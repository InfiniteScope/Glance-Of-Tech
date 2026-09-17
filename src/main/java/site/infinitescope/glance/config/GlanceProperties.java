package site.infinitescope.glance.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "glance")
public record GlanceProperties(
        String timezone,
        String title,
        String userAgent,
        List<Source> sources,
        Llm llm,
        Admin admin,
        Feed feed
) {
    public GlanceProperties {
        if (timezone == null || timezone.isBlank()) {
            timezone = "Asia/Shanghai";
        }
        if (title == null || title.isBlank()) {
            title = "科技资讯快报";
        }
        if (userAgent == null || userAgent.isBlank()) {
            userAgent = "glance-of-tech/0.1";
        }
        if (sources == null) {
            sources = new ArrayList<>();
        }
        if (feed == null) {
            feed = new Feed(null, 0, null, null);
        }
    }

    public record Source(
            String name,
            SourceType type,
            String url,
            int maxItems,
            boolean enabled
    ) {
    }

    public enum SourceType {
        RSS, ALGOLIA
    }

    public record Admin(String token) {
        public boolean configured() {
            return token != null && !token.isBlank();
        }
    }

    public record Feed(
            String link,
            int size,
            String publishTime,
            String selfUrl
    ) {
        public Feed {
            if (link == null || link.isBlank()) {
                link = "http://127.0.0.1:8081/api/digest";
            }
            if (size <= 0) {
                size = 20;
            }
            if (publishTime == null || publishTime.isBlank()) {
                publishTime = "08:15";
            }
        }
    }

    public record Llm(
            String baseUrl,
            String apiKey,
            String model,
            int timeoutSeconds,
            int maxTokens,
            double temperature,
            boolean jsonMode
    ) {
        public Llm {
            if (timeoutSeconds <= 0) {
                timeoutSeconds = 30;
            }
            if (maxTokens <= 0) {
                maxTokens = 8000;
            }
        }

        public boolean configured() {
            return apiKey != null && !apiKey.isBlank()
                    && baseUrl != null && !baseUrl.isBlank()
                    && model != null && !model.isBlank();
        }
    }
}
