package site.infinitescope.glance.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import site.infinitescope.glance.config.GlanceProperties;
import site.infinitescope.glance.fetch.FetchedItem;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class LlmService {

    private static final Logger log = LoggerFactory.getLogger(LlmService.class);

    private static final String SYSTEM_PROMPT = """
            你是一名科技资讯编辑。根据给定的资讯条目列表，输出严格 JSON（不要输出任何额外文字、不要用 markdown 代码块包裹）：
            {
              "summary": "本期总览，中文，2-4 句话，概括整体趋势与亮点",
              "items": [
                { "index": 0, "summary": "该条一句话中文摘要", "tags": ["标签1", "标签2"] }
              ]
            }
            要求：items 必须覆盖输入的每一条，index 与输入编号一致；tags 每条 1-3 个，用小写英文短词（如 ai、infra、web、security、hardware）。
            """;

    private final GlanceProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public LlmService(GlanceProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(properties.llm().timeoutSeconds() * 1000);
        this.restClient = RestClient.builder()
                .requestFactory(factory)
                .build();
    }

    public Optional<LlmSummaryResult> summarize(List<FetchedItem> items) {
        if (!properties.llm().configured()) {
            log.warn("LLM not configured (missing api-key/base-url/model), degraded mode");
            return Optional.empty();
        }
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                return Optional.of(doSummarize(items));
            } catch (Exception e) {
                log.warn("LLM summarize attempt {} failed: {}", attempt, e.getMessage());
            }
        }
        return Optional.empty();
    }

    private LlmSummaryResult doSummarize(List<FetchedItem> items) throws Exception {
        StringBuilder userContent = new StringBuilder("资讯条目列表：\n");
        for (int i = 0; i < items.size(); i++) {
            FetchedItem item = items.get(i);
            userContent.append(i).append(". [").append(item.source()).append("] ")
                    .append(item.title()).append(" — ").append(item.url()).append('\n');
        }

        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("model", properties.llm().model());
        body.put("temperature", properties.llm().temperature());
        body.put("max_tokens", properties.llm().maxTokens());
        if (properties.llm().jsonMode()) {
            body.put("response_format", Map.of("type", "json_object"));
        }
        body.put("messages", List.of(
                Map.of("role", "system", "content", SYSTEM_PROMPT),
                Map.of("role", "user", "content", userContent.toString())
        ));

        String endpoint = properties.llm().baseUrl().replaceAll("/+$", "") + "/chat/completions";
        String responseBody = restClient.post()
                .uri(endpoint)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + properties.llm().apiKey())
                .body(body)
                .retrieve()
                .body(String.class);

        JsonNode root = objectMapper.readTree(responseBody);
        String content = root.path("choices").path(0).path("message").path("content").asText();
        if (content == null || content.isBlank()) {
            throw new IllegalStateException("empty LLM content");
        }
        return parseResult(stripCodeFence(content));
    }

    static String stripCodeFence(String content) {
        String trimmed = content.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            if (firstNewline > 0) {
                trimmed = trimmed.substring(firstNewline + 1);
            }
            if (trimmed.endsWith("```")) {
                trimmed = trimmed.substring(0, trimmed.length() - 3);
            }
        }
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return trimmed;
    }

    static LlmSummaryResult parseResult(String json) throws Exception {
        JsonNode node = new ObjectMapper().readTree(json);
        String overview = node.path("summary").asText(null);
        List<LlmSummaryResult.ItemSummary> items = new java.util.ArrayList<>();
        for (JsonNode itemNode : node.path("items")) {
            int index = itemNode.path("index").asInt(-1);
            if (index < 0) {
                continue;
            }
            String summary = itemNode.path("summary").asText(null);
            List<String> tags = new java.util.ArrayList<>();
            for (JsonNode tag : itemNode.path("tags")) {
                if (tag.isTextual()) {
                    tags.add(tag.asText());
                }
            }
            items.add(new LlmSummaryResult.ItemSummary(index, summary, tags));
        }
        return new LlmSummaryResult(overview, items);
    }
}
