package site.infinitescope.glance.llm;

import java.util.List;

public record LlmSummaryResult(
        String overview,
        List<ItemSummary> items
) {
    public record ItemSummary(
            int index,
            String summary,
            List<String> tags
    ) {
    }
}
