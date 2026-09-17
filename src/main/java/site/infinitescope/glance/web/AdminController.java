package site.infinitescope.glance.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import site.infinitescope.glance.config.GlanceProperties;
import site.infinitescope.glance.model.Period;
import site.infinitescope.glance.service.DigestService;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping({"/api/admin/digest", "/api/v1/admin/digest"})
public class AdminController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    private final DigestService digestService;
    private final GlanceProperties properties;

    public AdminController(DigestService digestService, GlanceProperties properties) {
        this.digestService = digestService;
        this.properties = properties;
    }

    @PostMapping("/regenerate")
    public ResponseEntity<Map<String, Object>> regenerate(
            @RequestHeader(value = "X-Admin-Token", required = false) String token,
            @RequestParam(required = false) String date,
            @RequestParam String period,
            @RequestParam(defaultValue = "true") boolean force) {
        if (!properties.admin().configured()) {
            return build(HttpStatus.FORBIDDEN, "ADMIN_DISABLED", "admin token not configured");
        }
        if (!properties.admin().token().equals(token)) {
            log.warn("admin request with bad token");
            return build(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "invalid admin token");
        }
        LocalDate targetDate = date == null || date.isBlank()
                ? LocalDate.now(digestService.zoneId())
                : LocalDate.parse(date);
        Period targetPeriod = Period.fromValue(period);
        digestService.generateAsync(targetDate, targetPeriod, force);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "ACCEPTED");
        body.put("date", targetDate.toString());
        body.put("period", targetPeriod.value());
        return ResponseEntity.accepted().body(body);
    }

    private ResponseEntity<Map<String, Object>> build(HttpStatus status, String code, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", code);
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }
}
