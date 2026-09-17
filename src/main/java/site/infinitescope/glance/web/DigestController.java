package site.infinitescope.glance.web;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import site.infinitescope.glance.model.Digest;
import site.infinitescope.glance.model.Period;
import site.infinitescope.glance.repo.DigestRepository;
import site.infinitescope.glance.service.RunStatus;
import site.infinitescope.glance.web.dto.DigestResponse;
import site.infinitescope.glance.web.dto.DigestSummaryResponse;
import site.infinitescope.glance.web.dto.PageResponse;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping({"/api/digest", "/api/v1/digest"})
public class DigestController {

    private final DigestRepository digestRepository;
    private final RunStatus runStatus;

    public DigestController(DigestRepository digestRepository, RunStatus runStatus) {
        this.digestRepository = digestRepository;
        this.runStatus = runStatus;
    }

    @GetMapping("/latest")
    public DigestResponse latest() {
        return digestRepository.findTop1ByOrderByGeneratedAtDesc()
                .flatMap(digest -> digestRepository.findWithItemsById(digest.getId()))
                .map(DigestResponse::from)
                .orElseThrow(() -> new NotFoundException("no digest generated yet"));
    }

    @GetMapping("/list")
    public PageResponse<DigestSummaryResponse> list(@RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("invalid page/size");
        }
        Page<Digest> result = digestRepository
                .findAllByOrderByGeneratedAtDesc(PageRequest.of(page, size));
        return PageResponse.from(result, DigestSummaryResponse::from);
    }

    @GetMapping("/{date}/{period}")
    public DigestResponse byDateAndPeriod(@PathVariable String date, @PathVariable String period) {
        LocalDate parsedDate;
        try {
            parsedDate = LocalDate.parse(date);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("invalid date, expected yyyy-MM-dd");
        }
        Period parsedPeriod;
        try {
            parsedPeriod = Period.fromValue(period);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("invalid period, expected morning|evening");
        }
        return digestRepository.findByDateAndPeriod(parsedDate, parsedPeriod)
                .map(DigestResponse::from)
                .orElseThrow(() -> new NotFoundException("digest not found: " + date + "/" + period));
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        RunStatus.Status status = runStatus.current();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "UP");
        body.put("lastRun", status.lastRunAt());
        body.put("lastRunStatus", status.lastRunStatus());
        body.put("message", status.message());
        return body;
    }
}
