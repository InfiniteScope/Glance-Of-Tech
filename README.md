# Glance-Of-Tech

科技资讯快报服务：定时抓取科技资讯源 → LLM 总结 → 一日两篇简报 → JSON API。

独立 Spring Boot 后端，可嵌入任意站点。首个客户端：[InfBlog](https://infinitescope.site/)。

## 特性

- 一日两篇（08:00 / 20:00，Asia/Shanghai），`(date, period)` 幂等不重复
- 配置驱动数据源：HackerNews（Algolia API）、arXiv（Atom）、任意 RSS（ROME）
- LLM 厂商无关（OpenAI 兼容接口，DeepSeek/豆包/Kimi 均可）；失败自动降级为纯清单，简报照发
- 自动提取原文主图（media:content → enclosure → HTML `<img>`）
- SQLite 持久化（WAL 模式），2GB 内存服务器可跑
- 只读 JSON API + 带 token 鉴权的管理端点（异步执行）

## 快速开始

```bash
# 需要 JDK 17+
export LLM_API_KEY=sk-xxx                      # 不配则降级为纯清单模式
export GLANCE_ADMIN_TOKEN=your-secret          # 管理端点口令
./mvnw package -DskipTests
java -Duser.timezone=Asia/Shanghai -jar target/glance-of-tech.jar
```

服务监听 `127.0.0.1:8081`。

## API

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/digest/latest` | 最新一期（含条目） |
| GET | `/api/digest/list?page=0&size=20` | 历史列表 |
| GET | `/api/digest/{date}/{period}` | 指定简报（period: morning/evening） |
| GET | `/api/digest/health` | 健康检查 |
| POST | `/api/admin/digest/regenerate?period=morning` | 手动补跑（需 `X-Admin-Token` header，异步返回 202） |

## 配置数据源

改 `application.yml` 的 `glance.sources`，加源不改代码：

```yaml
    - name: ifanr
      type: rss        # rss | algolia
      url: https://www.ifanr.com/feed
      max-items: 5
      enabled: true
```

## 文档

- [技术文档与部署](docs/TECH.md)（选型、systemd/nginx、坑清单、路线图）
- [架构与数据流](docs/ARCHITECTURE.md)

## License

BSD 3-Clause
