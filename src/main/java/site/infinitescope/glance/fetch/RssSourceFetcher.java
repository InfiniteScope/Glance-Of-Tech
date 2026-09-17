package site.infinitescope.glance.fetch;

import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import site.infinitescope.glance.config.GlanceProperties;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;

@Component
public class RssSourceFetcher implements SourceFetcher {

    private static final Logger log = LoggerFactory.getLogger(RssSourceFetcher.class);

    private final HttpClient httpClient;
    private final ImageExtractor imageExtractor;
    private final GlanceProperties properties;

    public RssSourceFetcher(ImageExtractor imageExtractor, GlanceProperties properties) {
        this.imageExtractor = imageExtractor;
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public boolean supports(GlanceProperties.Source source) {
        return source.type() == GlanceProperties.SourceType.RSS;
    }

    @Override
    public List<FetchedItem> fetch(GlanceProperties.Source source) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(source.url()))
                    .timeout(Duration.ofSeconds(10))
                    .header("User-Agent", properties.userAgent())
                    .GET()
                    .build();
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() >= 400) {
                throw new SourceFetchException("HTTP " + response.statusCode() + " from " + source.url(), null);
            }
            SyndFeed feed;
            try (InputStream in = response.body()) {
                byte[] body = sanitizeAmpersands(stripDoctype(in.readAllBytes()));
                feed = new SyndFeedInput().build(new XmlReader(new java.io.ByteArrayInputStream(body)));
            }
            return feed.getEntries().stream()
                    .limit(source.maxItems() > 0 ? source.maxItems() : 10)
                    .map(entry -> toItem(source.name(), entry))
                    .toList();
        } catch (SourceFetchException e) {
            throw e;
        } catch (Exception e) {
            throw new SourceFetchException("Failed to fetch RSS source " + source.name() + ": " + e.getMessage(), e);
        }
    }

    // 字节级扫描安全性说明：所有 needle（"<!DOCTYPE"、"&"、">" 等）均为纯 ASCII。
    // UTF-8 多字节字符的每个字节都 >= 0x80，永远不会与 ASCII 字节发生碰撞，
    // 因此在未解码的原始字节上扫描不会误判多字节字符内部。UTF-16 编码的 feed
    // 不在此保护范围内，但主流 RSS/Atom 均为 UTF-8（且 XML 声明会标明编码）。
    private byte[] stripDoctype(byte[] body) {
        int scanLimit = Math.min(body.length, 4096);
        int start = indexOf(body, "<!DOCTYPE", scanLimit);
        if (start < 0) {
            return body;
        }
        int internalSubset = indexOf(body, "[", start, Math.min(body.length, start + 4096));
        int close = indexOf(body, ">", start, Math.min(body.length, start + 8192));
        if (internalSubset >= 0 && (close < 0 || internalSubset < close)) {
            close = indexOf(body, "]>", start, Math.min(body.length, start + 8192));
            if (close < 0) {
                return body;
            }
            close += 2;
        } else if (close >= 0) {
            close += 1;
        } else {
            return body;
        }
        byte[] stripped = new byte[body.length - (close - start)];
        System.arraycopy(body, 0, stripped, 0, start);
        System.arraycopy(body, close, stripped, start, body.length - close);
        return stripped;
    }

    // 修复不规范 feed 中的裸 '&'：合法实体引用（&amp; &#123; &#x1F; 等）原样保留，
    // 非法的替换为 &amp;。同样是字节级 ASCII 扫描，UTF-8 安全（见 stripDoctype 注释）。
    private byte[] sanitizeAmpersands(byte[] body) {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(body.length + 64);
        boolean changed = false;
        for (int i = 0; i < body.length; i++) {
            byte b = body[i];
            if (b != '&') {
                out.write(b);
                continue;
            }
            if (isValidEntityRef(body, i)) {
                out.write(b);
            } else {
                out.write('&');
                out.write('a');
                out.write('m');
                out.write('p');
                out.write(';');
                changed = true;
            }
        }
        return changed ? out.toByteArray() : body;
    }

    private boolean isValidEntityRef(byte[] body, int ampIndex) {
        int i = ampIndex + 1;
        int end = Math.min(body.length, i + 12);
        if (i >= end) {
            return false;
        }
        if (body[i] == '#') {
            i++;
            if (i < end && (body[i] == 'x' || body[i] == 'X')) {
                i++;
                int digitStart = i;
                while (i < end && isHexDigit(body[i])) {
                    i++;
                }
                return i > digitStart && i < body.length && body[i] == ';';
            }
            int digitStart = i;
            while (i < end && body[i] >= '0' && body[i] <= '9') {
                i++;
            }
            return i > digitStart && i < body.length && body[i] == ';';
        }
        int nameStart = i;
        while (i < end && isAsciiLetter(body[i])) {
            i++;
        }
        return i > nameStart && i < body.length && body[i] == ';';
    }

    private boolean isHexDigit(byte b) {
        return (b >= '0' && b <= '9') || (b >= 'a' && b <= 'f') || (b >= 'A' && b <= 'F');
    }

    private boolean isAsciiLetter(byte b) {
        return (b >= 'a' && b <= 'z') || (b >= 'A' && b <= 'Z');
    }

    private int indexOf(byte[] haystack, String needle, int limit) {
        return indexOf(haystack, needle, 0, limit);
    }

    private int indexOf(byte[] haystack, String needle, int from, int limit) {
        byte[] n = needle.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        outer:
        for (int i = from; i <= limit - n.length; i++) {
            for (int j = 0; j < n.length; j++) {
                if (haystack[i + j] != n[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    private FetchedItem toItem(String sourceName, SyndEntry entry) {
        String title = entry.getTitle() == null ? "(untitled)" : entry.getTitle().trim();
        String url = entry.getLink();
        String imageUrl = imageExtractor.extract(entry);
        Instant publishedAt = toInstant(entry.getPublishedDate() != null ? entry.getPublishedDate() : entry.getUpdatedDate());
        return new FetchedItem(sourceName, title, url, imageUrl, publishedAt);
    }

    private Instant toInstant(Date date) {
        return date == null ? null : date.toInstant();
    }
}
