package site.infinitescope.glance.fetch;

import java.time.Instant;

public record FetchedItem(
        String source,
        String title,
        String url,
        String imageUrl,
        Instant publishedAt
) {
}
