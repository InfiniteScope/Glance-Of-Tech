package site.infinitescope.glance.llm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LlmServiceTest {

    @Test
    void stripPlainJsonUnchanged() {
        String json = "{\"summary\":\"s\",\"items\":[]}";
        assertEquals(json, LlmService.stripCodeFence(json));
    }

    @Test
    void stripJsonFence() {
        String fenced = "```json\n{\"summary\":\"s\",\"items\":[]}\n```";
        assertEquals("{\"summary\":\"s\",\"items\":[]}", LlmService.stripCodeFence(fenced));
    }

    @Test
    void stripBareFence() {
        String fenced = "```\n{\"summary\":\"s\"}\n```";
        assertEquals("{\"summary\":\"s\"}", LlmService.stripCodeFence(fenced));
    }

    @Test
    void stripProseAroundJson() {
        String messy = "好的，以下是结果：\n{\"summary\":\"s\",\"items\":[]}\n希望对你有帮助";
        assertEquals("{\"summary\":\"s\",\"items\":[]}", LlmService.stripCodeFence(messy));
    }

    @Test
    void parseFullResult() throws Exception {
        String json = """
                {"summary":"总览内容","items":[
                  {"index":0,"summary":"第一条摘要","tags":["ai","infra"]},
                  {"index":2,"summary":"第三条摘要","tags":[]}
                ]}""";
        LlmSummaryResult result = LlmService.parseResult(json);
        assertEquals("总览内容", result.overview());
        assertEquals(2, result.items().size());
        assertEquals(0, result.items().get(0).index());
        assertEquals("第一条摘要", result.items().get(0).summary());
        assertEquals(java.util.List.of("ai", "infra"), result.items().get(0).tags());
        assertEquals(2, result.items().get(1).index());
        assertTrue(result.items().get(1).tags().isEmpty());
    }

    @Test
    void parseSkipsInvalidIndexAndNonTextTags() throws Exception {
        String json = """
                {"items":[
                  {"index":-1,"summary":"无效","tags":[]},
                  {"index":1,"summary":"有效","tags":["ok",42,null]}
                ]}""";
        LlmSummaryResult result = LlmService.parseResult(json);
        assertNull(result.overview());
        assertEquals(1, result.items().size());
        assertEquals(1, result.items().get(0).index());
        assertEquals(java.util.List.of("ok"), result.items().get(0).tags());
    }

    @Test
    void parseMalformedJsonThrows() {
        assertThrows(Exception.class, () -> LlmService.parseResult("{\"summary\": 截断了"));
    }

    @Test
    void parseEmptyObjectTolerated() throws Exception {
        LlmSummaryResult result = LlmService.parseResult("{}");
        assertNull(result.overview());
        assertTrue(result.items().isEmpty());
    }
}
