# glance-of-tech 架构与数据流

> 本文讲解每个文件的作用、项目执行逻辑，以及数据在代码文件之间的流动与处理。
> 技术选型与部署细节见 [TECH.md](./TECH.md)。

## 一、文件清单与职责

### 入口与配置

| 文件 | 作用 |
|---|---|
| `GlanceApplication.java` | Spring Boot 入口。`@EnableScheduling` 开启定时任务；启动前先创建 SQLite 数据库文件的上级目录 |
| `config/GlanceProperties.java` | 把 `application.yml` 里 `glance.*` 绑定成强类型记录：`sources[]`（数据源列表）、`llm`（base-url/api-key/model/超时等）、`timezone` |
| `resources/application.yml` | 全部配置：端口、SQLite 路径（WAL 模式）、LLM 参数（key 走环境变量占位符）、数据源清单 |

### 数据层（`model/` + `repo/`）

| 文件 | 作用 |
|---|---|
| `model/Period.java` | 枚举 `MORNING/EVENING`，`@JsonValue` 保证序列化成 `"morning"/"evening"` 小写 |
| `model/Digest.java` | 简报实体（一期）：date、period、title、summary（总览）、degraded、generatedAt，与 DigestItem 一对多 |
| `model/DigestItem.java` | 条目实体（一条资讯）：source、title、url、**imageUrl**、summary、tags（JSON 字符串）、publishedAt |
| `repo/DigestRepository.java` | 简报查询：`existsByDateAndPeriod`（幂等）、`findTopByOrderByGeneratedAtDesc`（最新一期，EntityGraph 连带 items） |
| `repo/DigestItemRepository.java` | `existsByUrl` 用于跨期去重 |

### 抓取层（`fetch/`）

| 文件 | 作用 |
|---|---|
| `fetch/FetchedItem.java` | 抓取结果的统一中间格式（record）：source/title/url/imageUrl/publishedAt |
| `fetch/SourceFetcher.java` | 策略接口：`supports()` 判断能否处理某 source + `fetch()` 执行 |
| `fetch/RssSourceFetcher.java` | 处理 `type: rss`。JDK HttpClient 拉 XML → 字节级清洗（剥 DOCTYPE、修复裸 `&`）→ ROME 解析 → 转 FetchedItem |
| `fetch/AlgoliaSourceFetcher.java` | 处理 `type: algolia`（HN）。拉 JSON → Jackson 解析 `hits[]` → 转 FetchedItem（imageUrl 恒 null） |
| `fetch/ImageExtractor.java` | RSS 图片提取：`media:content`/thumbnail → image enclosure → description HTML 第一个 `<img>`（过滤 1x1 tracking pixel、相对路径转绝对） |

### LLM 层（`llm/`）

| 文件 | 作用 |
|---|---|
| `llm/LlmService.java` | 拼 prompt → POST `{base-url}/chat/completions`（OpenAI 兼容）→ 剥掉 ` ```json ` 包裹 → Jackson 解析校验 → 失败重试 1 次 → 返回 `Optional`（空 = 降级） |
| `llm/LlmSummaryResult.java` | LLM 输出的映射：`overview` + `items[{index, summary, tags}]` |

### 编排层（`service/` + `scheduler/`）

| 文件 | 作用 |
|---|---|
| `service/DigestService.java` | **核心编排器**。`generate(date, period, force)` 串联整个流水线 |
| `scheduler/DigestScheduler.java` | 两个 `@Scheduled` cron 方法（8:00 / 20:00），算出今天日期后调 `DigestService.generate(..., force=false)` |
| `service/RunStatus.java` | 内存中的最近运行状态（AtomicReference），供 `/health` 读取 |

### API 层（`web/`）

| 文件 | 作用 |
|---|---|
| `web/DigestController.java` | `/latest` `/list` `/{date}/{period}` `/health`，同时映射 `/api/digest` 和 `/api/v1/digest` 两套前缀 |
| `web/AdminController.java` | `POST /regenerate?period=&date=&force=` 手动补跑（内网用，nginx 不暴露） |
| `web/GlobalExceptionHandler.java` | 统一错误格式 `{error, message, path}`：404 语义化、400 参数错误、500 兜底 |
| `web/dto/*.java` | 响应对象。`DigestItemResponse.from()` 里把 tags 的 JSON 字符串反序列化成数组 |

## 二、执行逻辑（以一次定时任务为例）

```
DigestScheduler.morning()                    ← cron 触发，ZoneId 算今天日期
  └─ DigestService.generate(2026-09-16, MORNING, false)
       ├─ digestRepository.existsByDateAndPeriod()   ← 幂等闸门，存在即返回
       ├─ fetchAll()                                  ← 遍历 properties.sources()
       │    └─ 对每个 enabled 源：
       │         fetchers.stream().filter(supports).findFirst()   ← 策略匹配
       │         → RssSourceFetcher / AlgoliaSourceFetcher.fetch()
       │         → List<FetchedItem>（单源异常只记日志，不中断）
       ├─ dedup()                                     ← 批内 LinkedHashMap 按 URL 去重
       │                                               + itemRepository.existsByUrl() 跨期去重
       ├─ llmService.summarize(fresh)                 ← Optional<LlmSummaryResult>
       ├─ new Digest(...)                             ← degraded = llmResult.isEmpty()
       │    for i in fresh: new DigestItem(...)       ← FetchedItem → 实体
       │    按 index 匹配 LLM 的 summary/tags 填回对应条目
       └─ digestRepository.save(digest)               ← 级联存 items（unique 冲突兜底并发）
```

API 请求路径（如 `GET /api/digest/latest`）：

```
DigestController.latest()
  → digestRepository.findTopByOrderByGeneratedAtDesc()   ← EntityGraph 一次带出 items
  → DigestResponse.from(digest)                          ← 实体 → DTO
     └─ DigestItemResponse.from(item)                    ← tags 字符串 → List<String>
  → Jackson 序列化为 JSON
```

## 三、数据在文件间的流动

关键在**三次形态转换**，每一层只认自己的类型，互不泄露。

### ① 外部世界 → `FetchedItem`（抓取层归一化）

不同源的异构数据（HN 的 JSON hits、RSS 的 XML entry）被各自的 Fetcher 抹平成同一个 record。图片提取也在这里完成，下游不关心图是从 `media:content` 还是 HTML 里抠出来的。

### ② `FetchedItem` → `DigestItem` 实体（编排层组装）

`DigestService` 拿着 `List<FetchedItem>` 去调 LLM。LLM 不认识实体，只收到编号 + 标题 + URL 的纯文本；返回的 JSON 通过 `index` 与列表位置对齐，把 summary/tags 填回对应 `DigestItem`。这就是 prompt 里要求 `index 与输入编号一致` 的原因——**index 是 LLM 输出和内存列表之间的 join key**。

### ③ 实体 → DTO（API 层展示化）

Controller 从不直接返回 JPA 实体（避免懒加载、字段泄露、格式耦合）。`DigestResponse.from()` 做最后一道转换：Period 枚举 → 小写字符串、tags JSON 字符串 → 数组。数据库里 tags 存 `["ai","infra"]` 字符串是因为 SQLite 没有数组类型，到 API 才还原。

### 降级路径的数据流

LLM 失败时 `Optional.empty()` 流经编排器：`degraded=true`、`summary=null`、条目 summary/tags 留空，但 title/url/imageUrl/publishedAt 照常入库——**降级只是少了 LLM 一步的产物，整条管道不中断**。

### 持久化与状态

- 持久事实只在 SQLite：`digest` 表（一期一行）+ `digest_item` 表（外键级联删除，force 重新生成时先删旧期）
- 易失状态只在内存：`RunStatus` 记录最近一次任务的成败，重启清零（`/health` 显示 `NEVER_RUN`）

## 四、设计原则

- **宁可发纯清单，不可静默失败**：LLM 挂了简报照发（degraded），某源挂了其他源照常。
- **配置驱动**：加数据源只改 yml 的 `glance.sources[]`，不动代码。
- **幂等**：`(date, period)` 唯一约束 + 生成前先查，重跑/并发安全。
