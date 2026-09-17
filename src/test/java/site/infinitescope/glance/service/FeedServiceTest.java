package site.infinitescope.glance.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import site.infinitescope.glance.config.GlanceProperties;
import site.infinitescope.glance.model.Digest;
import site.infinitescope.glance.model.DigestItem;
import site.infinitescope.glance.model.Period;
import site.infinitescope.glance.repo.DigestRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FeedServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private DigestRepository digestRepository;
    private FeedService feedService;

    @BeforeEach
    void setUp() {
        digestRepository = mock(DigestRepository.class);
        GlanceProperties properties = new GlanceProperties(
                "Asia/Shanghai", "科技资讯快报", "ua",
                List.of(), null, null,
                new GlanceProperties.Feed("https://infinitescope.site/digest", 20, "08:15", ""));
        feedService = new FeedService(digestRepository, properties);
    }

    @Test
    void combinesYesterdayEveningAndTodayMorningIntoOneItem() {
        LocalDate today = LocalDate.now(ZONE);
        LocalDate yesterday = today.minusDays(1);
        when(digestRepository.findAllWithItemsByDateBetween(any(), any()))
                .thenReturn(List.of(
                        digest(yesterday, Period.EVENING, "晚报总览", item("晚报条目", "https://e.example")),
                        digest(today, Period.MORNING, "早报总览", item("早报条目", "https://m.example"))));

        String xml = feedService.renderFeed();

        assertTrue(xml.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"));
        assertTrue(xml.contains("<title>科技资讯快报 · 日报 " + today + "</title>"));
        assertEquals(1, countOccurrences(xml, "<item>"));
        assertTrue(xml.contains("晚报条目"));
        assertTrue(xml.contains("早报条目"));
        assertTrue(xml.contains("<guid isPermaLink=\"true\">https://infinitescope.site/digest?date=" + today));
    }

    @Test
    void itemPubDateIsRfc1123UtcAtConfiguredTime() {
        LocalDate today = LocalDate.now(ZONE);
        when(digestRepository.findAllWithItemsByDateBetween(any(), any()))
                .thenReturn(List.of(digest(today, Period.MORNING, null, item("条目", "https://m.example"))));

        String xml = feedService.renderFeed();

        String expected = java.time.ZonedDateTime.of(today, java.time.LocalTime.of(8, 15), ZONE)
                .toInstant().toString().replace("T", " ").substring(0, 10);
        assertTrue(xml.contains("<pubDate>"));
        assertTrue(xml.matches("(?s).*<pubDate>[A-Z][a-z]{2}, \\d{2} [A-Z][a-z]{2} \\d{4} .* GMT</pubDate>.*"));
        assertTrue(xml.contains(expected.substring(0, 4)));
    }

    @Test
    void escapesXmlInTitlesAndUrls() {
        LocalDate today = LocalDate.now(ZONE);
        when(digestRepository.findAllWithItemsByDateBetween(any(), any()))
                .thenReturn(List.of(digest(today, Period.MORNING, null,
                        item("A & B <script> ]]> 注入", "https://x.example?a=1&b=2"))));

        String xml = feedService.renderFeed();

        assertTrue(xml.contains("A &amp; B &lt;script&gt;"));
        assertTrue(xml.contains("]]&gt; 注入"), "转义后不得残留裸 ]]>");
        assertEquals(1, countOccurrences(xml, "]]>"), "输出中只应有一处 ]]>（content:encoded 闭合）");
        assertTrue(xml.contains("href=\"https://x.example?a=1&amp;b=2\""));
    }

    @Test
    void toCdataSplitsIllegalSequence() {
        assertEquals("a ]]]]><![CDATA[> b", FeedService.toCdata("a ]]> b"));
    }

    @Test
    void skipsDaysWithoutAnyDigest() {
        when(digestRepository.findAllWithItemsByDateBetween(any(), any())).thenReturn(List.of());

        String xml = feedService.renderFeed();

        assertEquals(0, countOccurrences(xml, "<item>"));
        assertTrue(xml.contains("</channel>"));
    }

    @Test
    void dailyReportShowsOnlyMorningWhenEveningMissing() {
        LocalDate today = LocalDate.now(ZONE);
        when(digestRepository.findAllWithItemsByDateBetween(any(), any()))
                .thenReturn(List.of(digest(today, Period.MORNING, "早报总览", item("条目", "https://m.example"))));

        String xml = feedService.renderFeed();

        assertEquals(1, countOccurrences(xml, "<item>"));
        assertTrue(xml.contains("早报"));
    }

    private Digest digest(LocalDate date, Period period, String summary, DigestItem item) {
        Digest digest = new Digest(date, period, "t", summary, false, OffsetDateTime.now(ZONE));
        digest.addItem(item);
        return digest;
    }

    private DigestItem item(String title, String url) {
        return new DigestItem("src", title, url, null, Instant.now());
    }

    private int countOccurrences(String haystack, String needle) {
        int count = 0;
        int index = 0;
        while ((index = haystack.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }
}
