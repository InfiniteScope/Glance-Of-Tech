package site.infinitescope.glance.web.dto;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import site.infinitescope.glance.model.DigestItem;

import java.time.Instant;
import java.util.List;

public record DigestItemResponse(
        String source,
        String title,
        String url,
        String imageUrl,
        String summary,
        List<String> tags,
        Instant publishedAt
) {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static DigestItemResponse from(DigestItem item) {
        return new DigestItemResponse(
                item.getSource(),
                item.getTitle(),
                item.getUrl(),
                item.getImageUrl(),
                item.getSummary(),
                parseTags(item.getTags()),
                item.getPublishedAt()
        );
    }

    private static List<String> parseTags(String tags) {
        if (tags == null || tags.isBlank()) {
            return List.of();
        }
        try {
            return MAPPER.readValue(tags, new TypeReference<>() {
            });
        } catch (Exception e) {
            return List.of();
        }
    }
}
