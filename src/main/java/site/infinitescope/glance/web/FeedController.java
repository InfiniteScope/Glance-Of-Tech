package site.infinitescope.glance.web;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import site.infinitescope.glance.service.FeedService;

@RestController
@RequestMapping({"/api/digest", "/api/v1/digest"})
public class FeedController {

    private static final MediaType RSS = MediaType.parseMediaType("application/rss+xml;charset=UTF-8");

    private final FeedService feedService;

    public FeedController(FeedService feedService) {
        this.feedService = feedService;
    }

    @GetMapping(value = "/feed.xml", produces = "application/rss+xml;charset=UTF-8")
    public ResponseEntity<String> feed() {
        return ResponseEntity.ok()
                .contentType(RSS)
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=1800")
                .body(feedService.renderFeed());
    }
}
