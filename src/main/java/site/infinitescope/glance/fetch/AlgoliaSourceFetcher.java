package site.infinitescope.glance.fetch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import site.infinitescope.glance.config.GlanceProperties;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
public class AlgoliaSourceFetcher implements SourceFetcher {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final GlanceProperties properties;

    public AlgoliaSourceFetcher(ObjectMapper objectMapper, GlanceProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public boolean supports(GlanceProperties.Source source) {
        return source.type() == GlanceProperties.SourceType.ALGOLIA;
    }

    @Override
    public List<FetchedItem> fetch(GlanceProperties.Source source) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(source.url()))
                    .timeout(Duration.ofSeconds(10))
                    .header("User-Agent", properties.userAgent())
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new SourceFetchException("HTTP " + response.statusCode() + " from " + source.url(), null);
            }
            JsonNode hits = objectMapper.readTree(response.body()).path("hits");
            int limit = source.maxItems() > 0 ? source.maxItems() : 10;
            List<FetchedItem> items = new ArrayList<>();
            for (JsonNode hit : hits) {
                if (items.size() >= limit) {
                    break;
                }
                String title = hit.path("title").asText(null);
                if (title == null || title.isBlank()) {
                    continue;
                }
                String url = hit.path("url").asText(null);
                if (url == null || url.isBlank()) {
                    url = "https://news.ycombinator.com/item?id=" + hit.path("objectID").asText();
                }
                long createdAt = hit.path("created_at_i").asLong(0);
                Instant publishedAt = createdAt > 0 ? Instant.ofEpochSecond(createdAt) : null;
                items.add(new FetchedItem(source.name(), title, url, null, publishedAt));
            }
            return items;
        } catch (SourceFetchException e) {
            throw e;
        } catch (Exception e) {
            throw new SourceFetchException("Failed to fetch Algolia source " + source.name() + ": " + e.getMessage(), e);
        }
    }
}
