package site.infinitescope.glance.web.dto;

import site.infinitescope.glance.model.Digest;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record DigestSummaryResponse(
        LocalDate date,
        String period,
        String title,
        OffsetDateTime generatedAt
) {
    public static DigestSummaryResponse from(Digest digest) {
        return new DigestSummaryResponse(
                digest.getDate(),
                digest.getPeriod().value(),
                digest.getTitle(),
                digest.getGeneratedAt()
        );
    }
}
