package site.infinitescope.glance.fetch;

import com.rometools.modules.mediarss.MediaEntryModule;
import com.rometools.modules.mediarss.MediaModule;
import com.rometools.modules.mediarss.types.MediaContent;
import com.rometools.modules.mediarss.types.Thumbnail;
import com.rometools.rome.feed.synd.SyndContent;
import com.rometools.rome.feed.synd.SyndEnclosure;
import com.rometools.rome.feed.synd.SyndEntry;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
public class ImageExtractor {

    private static final Logger log = LoggerFactory.getLogger(ImageExtractor.class);

    private static final Set<String> TRACKING_HOST_HINTS = Set.of(
            "doubleclick.net", "googlesyndication.com", "google-analytics.com",
            "feedburner.com", "feedsportal.com", "piwik", "statcounter.com"
    );

    public String extract(SyndEntry entry) {
        String fromMedia = fromMediaModule(entry);
        if (fromMedia != null) {
            return fromMedia;
        }
        String fromEnclosure = fromEnclosures(entry);
        if (fromEnclosure != null) {
            return fromEnclosure;
        }
        return fromHtml(entry);
    }

    private String fromMediaModule(SyndEntry entry) {
        try {
            MediaModule module = (MediaModule) entry.getModule(MediaModule.URI);
            if (module == null) {
                return null;
            }
            if (module instanceof MediaEntryModule mediaEntry) {
                MediaContent[] contents = mediaEntry.getMediaContents();
                if (contents != null) {
                    for (MediaContent content : contents) {
                        if (content.getReference() != null && isImageContent(content)) {
                            return content.getReference().toString();
                        }
                    }
                }
                Thumbnail[] thumbnails = mediaEntry.getMetadata() == null
                        ? null : mediaEntry.getMetadata().getThumbnail();
                if (thumbnails != null && thumbnails.length > 0 && thumbnails[0].getUrl() != null) {
                    return thumbnails[0].getUrl().toString();
                }
            }
        } catch (Exception e) {
            log.debug("media module parse failed for {}: {}", entry.getLink(), e.getMessage());
        }
        return null;
    }

    private boolean isImageContent(MediaContent content) {
        if (content.getType() != null && content.getType().startsWith("image/")) {
            return true;
        }
        if (content.getMedium() != null && "image".equalsIgnoreCase(content.getMedium())) {
            return true;
        }
        String ref = content.getReference().toString().toLowerCase();
        return ref.matches(".*\\.(png|jpe?g|gif|webp|avif)(\\?.*)?$");
    }

    private String fromEnclosures(SyndEntry entry) {
        List<SyndEnclosure> enclosures = entry.getEnclosures();
        if (enclosures == null) {
            return null;
        }
        for (SyndEnclosure enclosure : enclosures) {
            if (enclosure.getType() != null && enclosure.getType().startsWith("image/")
                    && enclosure.getUrl() != null) {
                return enclosure.getUrl();
            }
        }
        return null;
    }

    private String fromHtml(SyndEntry entry) {
        String base = entry.getLink() == null ? "" : entry.getLink();
        String html = null;
        if (entry.getContents() != null && !entry.getContents().isEmpty()) {
            html = entry.getContents().get(0).getValue();
        }
        if ((html == null || html.isBlank())) {
            SyndContent desc = entry.getDescription();
            if (desc != null) {
                html = desc.getValue();
            }
        }
        if (html == null || html.isBlank()) {
            return null;
        }
        try {
            Document doc = Jsoup.parse(html, base);
            for (Element img : doc.select("img[src]")) {
                if (isTrackingPixel(img)) {
                    continue;
                }
                String src = img.absUrl("src");
                if (src.isBlank()) {
                    src = img.attr("src");
                }
                if (src.startsWith("http") && !isTrackingHost(src)) {
                    return src;
                }
            }
        } catch (Exception e) {
            log.debug("html image extraction failed for {}: {}", base, e.getMessage());
        }
        return null;
    }

    private boolean isTrackingPixel(Element img) {
        try {
            int w = parseDim(img.attr("width"));
            int h = parseDim(img.attr("height"));
            return (w > 0 && w <= 2) || (h > 0 && h <= 2);
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private int parseDim(String attr) {
        if (attr == null || attr.isBlank()) {
            return -1;
        }
        return Integer.parseInt(attr.replaceAll("[^0-9].*$", ""));
    }

    private boolean isTrackingHost(String url) {
        String lower = url.toLowerCase();
        return TRACKING_HOST_HINTS.stream().anyMatch(lower::contains);
    }
}
