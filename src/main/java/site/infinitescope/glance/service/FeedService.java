package site.infinitescope.glance.service;

import org.springframework.stereotype.Service;
import site.infinitescope.glance.config.GlanceProperties;
import site.infinitescope.glance.model.Digest;
import site.infinitescope.glance.model.DigestItem;
import site.infinitescope.glance.model.Period;
import site.infinitescope.glance.repo.DigestRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class FeedService {

    private static final String CDATA_END = "]]>";

    private final DigestRepository digestRepository;
    private final GlanceProperties properties;

    public FeedService(DigestRepository digestRepository, GlanceProperties properties) {
        this.digestRepository = digestRepository;
        this.properties = properties;
    }

    public String renderFeed() {
        ZoneId zone = ZoneId.of(properties.timezone());
        LocalDate today = LocalDate.now(zone);
        LocalDate from = today.minusDays(properties.feed().size());
        Map<LocalDate, Map<Period, Digest>> byDate = loadByDate(from.minusDays(1), today);

        List<java.time.Instant> buildTimes = new ArrayList<>();
        StringBuilder items = new StringBuilder();
        LocalDate cursor = today;
        while (!cursor.isBefore(from)) {
            Digest evening = find(byDate, cursor.minusDays(1), Period.EVENING);
            Digest morning = find(byDate, cursor, Period.MORNING);
            if (evening != null || morning != null) {
                items.append(renderItem(cursor, evening, morning, zone));
                if (morning != null) {
                    buildTimes.add(morning.getGeneratedAt().toInstant());
                }
                if (evening != null) {
                    buildTimes.add(evening.getGeneratedAt().toInstant());
                }
            }
            cursor = cursor.minusDays(1);
        }

        java.time.Instant lastBuild = buildTimes.stream().max(java.time.Instant::compareTo)
                .orElse(java.time.Instant.now());
        GlanceProperties.Feed feed = properties.feed();
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<rss version=\"2.0\" xmlns:content=\"http://purl.org/rss/1.0/modules/content/\" ")
                .append("xmlns:atom=\"http://www.w3.org/2005/Atom\">\n");
        xml.append("  <channel>\n");
        xml.append("    <title>").append(escapeXml(properties.title())).append("</title>\n");
        xml.append("    <link>").append(escapeXml(feed.link())).append("</link>\n");
        xml.append("    <description>").append(escapeXml(properties.title()))
                .append(" · 每日早 08:15 推送，包含前日晚报与今日早报</description>\n");
        xml.append("    <language>zh-CN</language>\n");
        xml.append("    <lastBuildDate>").append(rfc1123(lastBuild)).append("</lastBuildDate>\n");
        if (feed.selfUrl() != null && !feed.selfUrl().isBlank()) {
            xml.append("    <atom:link href=\"").append(escapeXml(feed.selfUrl()))
                    .append("\" rel=\"self\" type=\"application/rss+xml\"/>\n");
        }
        xml.append(items);
        xml.append("  </channel>\n");
        xml.append("</rss>\n");
        return xml.toString();
    }

    private Map<LocalDate, Map<Period, Digest>> loadByDate(LocalDate from, LocalDate to) {
        List<Digest> digests = digestRepository.findAllWithItemsByDateBetween(from, to);
        Map<LocalDate, Map<Period, Digest>> result = new LinkedHashMap<>();
        for (Digest digest : digests) {
            result.computeIfAbsent(digest.getDate(), d -> new LinkedHashMap<>())
                    .put(digest.getPeriod(), digest);
        }
        return result;
    }

    private Digest find(Map<LocalDate, Map<Period, Digest>> byDate, LocalDate date, Period period) {
        Map<Period, Digest> day = byDate.get(date);
        return day == null ? null : day.get(period);
    }

    private String renderItem(LocalDate date, Digest evening, Digest morning, ZoneId zone) {
        String link = properties.feed().link() + "?date=" + date;
        GlanceProperties.Feed feed = properties.feed();
        String title = properties.title() + " · 日报 " + date;
        StringBuilder description = new StringBuilder();
        int count = count(evening) + count(morning);
        description.append("本期日报包含前日晚报与今日早报，共 ").append(count).append(" 条资讯。");
        if (morning != null && morning.getSummary() != null) {
            description.append(" 今日看点：").append(morning.getSummary());
        } else if (evening != null && evening.getSummary() != null) {
            description.append(" 昨晚看点：").append(evening.getSummary());
        }

        StringBuilder html = new StringBuilder();
        if (evening != null) {
            html.append("<h2>").append(escapeXml(properties.title())).append(" · ").append(evening.getDate())
                    .append(" 晚报</h2>");
            appendDigest(html, evening);
        }
        if (morning != null) {
            html.append("<h2>").append(escapeXml(properties.title())).append(" · ").append(morning.getDate())
                    .append(" 早报</h2>");
            appendDigest(html, morning);
        }

        StringBuilder item = new StringBuilder();
        item.append("    <item>\n");
        item.append("      <title>").append(escapeXml(title)).append("</title>\n");
        item.append("      <link>").append(escapeXml(link)).append("</link>\n");
        item.append("      <guid isPermaLink=\"true\">").append(escapeXml(link)).append("</guid>\n");
        item.append("      <pubDate>").append(pubDate(date, zone)).append("</pubDate>\n");
        item.append("      <description>").append(escapeXml(description.toString())).append("</description>\n");
        item.append("      <content:encoded><![CDATA[").append(toCdata(html.toString()))
                .append("]]></content:encoded>\n");
        item.append("    </item>\n");
        return item.toString();
    }

    private void appendDigest(StringBuilder html, Digest digest) {
        int index = 1;
        for (DigestItem item : digest.getItems()) {
            html.append("<p>").append(index++).append(". <a href=\"").append(escapeXml(item.getUrl())).append("\">")
                    .append(escapeXml(item.getTitle())).append("</a>");
            if (item.getSummary() != null && !item.getSummary().isBlank()) {
                html.append("　").append(escapeXml(item.getSummary()));
            }
            if (item.getImageUrl() != null && !item.getImageUrl().isBlank()) {
                html.append("<br/><img src=\"").append(escapeXml(item.getImageUrl()))
                        .append("\" alt=\"\" style=\"max-width:100%\"/>");
            }
            html.append("</p>");
        }
    }

    private int count(Digest digest) {
        return digest == null ? 0 : digest.getItems().size();
    }

    private String pubDate(LocalDate date, ZoneId zone) {
        LocalTime time;
        try {
            time = LocalTime.parse(properties.feed().publishTime());
        } catch (Exception e) {
            time = LocalTime.of(8, 15);
        }
        ZonedDateTime zoned = ZonedDateTime.of(date, time, zone);
        return rfc1123(zoned.toInstant());
    }

    private String rfc1123(java.time.Instant instant) {
        return DateTimeFormatter.RFC_1123_DATE_TIME
                .format(ZonedDateTime.ofInstant(instant, ZoneOffset.UTC));
    }

    /** content:encoded 是任意 XML，用 CDATA 包 HTML；守护极少见的非法序列 ]]> （参考 InfBlog 踩坑） */
    static String toCdata(String html) {
        return html.replace(CDATA_END, "]]]]><![CDATA[>");
    }

    private String escapeXml(String str) {
        if (str == null) {
            return "";
        }
        return str
                .replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "")
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
