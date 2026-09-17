package site.infinitescope.glance.web.dto;

import site.infinitescope.glance.model.Digest;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record DigestResponse(
        LocalDate date,
        String period,
        String title,
        String summary,
        boolean degraded,
        OffsetDateTime generatedAt,
        List<DigestItemResponse> items
) {
    public static DigestResponse from(Digest digest) {
        return new DigestResponse(
                digest.getDate(),
                digest.getPeriod().value(),
                digest.getTitle(),
                digest.getSummary(),
                digest.isDegraded(),
                digest.getGeneratedAt(),
                digest.getItems().stream().map(DigestItemResponse::from).toList()
        );
    }
}
