package site.infinitescope.glance.service;

import org.springframework.stereotype.Component;
import site.infinitescope.glance.config.GlanceProperties;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class RunStatus {

    public record Status(OffsetDateTime lastRunAt, String lastRunStatus, String message) {
    }

    private final ZoneId zoneId;
    private final AtomicReference<Status> current = new AtomicReference<>(new Status(null, "NEVER_RUN", null));

    public RunStatus(GlanceProperties properties) {
        this.zoneId = ZoneId.of(properties.timezone());
    }

    public void recordSuccess(String message) {
        current.set(new Status(OffsetDateTime.now(zoneId), "SUCCESS", message));
    }

    public void recordSkipped(String message) {
        current.set(new Status(OffsetDateTime.now(zoneId), "SKIPPED", message));
    }

    public void recordFailure(String message) {
        current.set(new Status(OffsetDateTime.now(zoneId), "FAILED", message));
    }

    public Status current() {
        return current.get();
    }
}
