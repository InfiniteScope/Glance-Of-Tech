package site.infinitescope.glance.fetch;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import site.infinitescope.glance.config.GlanceProperties;

import java.util.List;

@Disabled("manual smoke test, requires external network")
class ImageExtractionSmokeTest {

    @Test
    void feedsImageExtraction() {
        RssSourceFetcher fetcher = new RssSourceFetcher(new ImageExtractor(),
                new GlanceProperties(null, null, null, null, null, null));
        String[] feeds = {
                "https://sspai.com/feed",
                "https://www.ifanr.com/feed",
                "https://www.solidot.org/index.rss",
                "https://www.ithome.com/rss/",
                "https://www.qbitai.com/feed",
                "https://www.jiqizhixin.com/rss",
                "https://www.infoq.cn/feed",
                "https://feeds.arstechnica.com/arstechnica/index",
                "https://www.technologyreview.com/feed/",
                "https://blog.google/technology/ai/rss/"
        };
        for (String feedUrl : feeds) {
            try {
                GlanceProperties.Source source = new GlanceProperties.Source(
                        feedUrl, GlanceProperties.SourceType.RSS, feedUrl, 5, true);
                List<FetchedItem> items = fetcher.fetch(source);
                long withImage = items.stream().filter(i -> i.imageUrl() != null).count();
                System.out.println("== " + feedUrl + " withImage=" + withImage + "/" + items.size());
                items.stream().filter(i -> i.imageUrl() != null).findFirst()
                        .ifPresent(i -> System.out.println("   sample: " + i.imageUrl()));
            } catch (Exception e) {
                System.out.println("== " + feedUrl + " FAILED: " + e.getMessage());
            }
        }
    }
}
