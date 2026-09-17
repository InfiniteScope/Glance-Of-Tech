package site.infinitescope.glance.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import site.infinitescope.glance.config.GlanceProperties;
import site.infinitescope.glance.fetch.FetchedItem;
import site.infinitescope.glance.fetch.SourceFetcher;
import site.infinitescope.glance.llm.LlmService;
import site.infinitescope.glance.llm.LlmSummaryResult;
import site.infinitescope.glance.model.Digest;
import site.infinitescope.glance.model.DigestItem;
import site.infinitescope.glance.model.Period;
import site.infinitescope.glance.repo.DigestItemRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class DigestService {

    private static final Logger log = LoggerFactory.getLogger(DigestService.class);

    private static final int MAX_TITLE_LENGTH = 200;
    private static final int DEDUP_BATCH_SIZE = 500;

    private final GlanceProperties properties;
    private final List<SourceFetcher> fetchers;
    private final LlmService llmService;
    private final DigestStore digestStore;
    private final DigestItemRepository itemRepository;
    private final RunStatus runStatus;
    private final ObjectMapper objectMapper;

    public DigestService(GlanceProperties properties,
                         List<SourceFetcher> fetchers,
                         LlmService llmService,
                         DigestStore digestStore,
                         DigestItemRepository itemRepository,
                         RunStatus runStatus,
                         ObjectMapper objectMapper) {
        this.properties = properties;
        this.fetchers = fetchers;
        this.llmService = llmService;
        this.digestStore = digestStore;
        this.itemRepository = itemRepository;
        this.runStatus = runStatus;
        this.objectMapper = objectMapper;
    }

    public ZoneId zoneId() {
        return ZoneId.of(properties.timezone());
    }

    public boolean hasDigest(LocalDate date, Period period) {
        return digestStore.exists(date, period);
    }

    @Async
    public void generateAsync(LocalDate date, Period period, boolean force) {
        try {
            generate(date, period, force);
        } catch (Exception e) {
            log.error("async digest generation failed for {} {}", date, period.value(), e);
            runStatus.recordFailure(e.getMessage());
        }
    }

    public Optional<Digest> generate(LocalDate date, Period period, boolean force) {
        long started = System.currentTimeMillis();
        if (!force && digestStore.exists(date, period)) {
            log.info("digest {} {} already exists, skip", date, period.value());
            runStatus.recordSkipped("already exists: " + date + " " + period.value());
            return digestStore.find(date, period);
        }

        List<FetchedItem> fetched = fetchAll();
        List<FetchedItem> fresh = dedup(fetched, force);
        if (fresh.isEmpty()) {
            log.warn("all sources failed or nothing new, skip this period ({} {})", date, period.value());
            runStatus.recordFailure("no items fetched for " + date + " " + period.value());
            return Optional.empty();
        }

        Optional<LlmSummaryResult> llmResult = llmService.summarize(fresh);
        boolean degraded = llmResult.isEmpty();

        Digest digest = new Digest(
                date,
                period,
                buildTitle(date, period),
                llmResult.map(LlmSummaryResult::overview).orElse(null),
                degraded,
                OffsetDateTime.now(zoneId())
        );
        Map<Integer, LlmSummaryResult.ItemSummary> summaryByIndex = indexSummaries(llmResult);
        for (int i = 0; i < fresh.size(); i++) {
            FetchedItem item = fresh.get(i);
            DigestItem entity = new DigestItem(
                    item.source(), truncate(item.title()), item.url(), item.imageUrl(), item.publishedAt());
            LlmSummaryResult.ItemSummary itemSummary = summaryByIndex.get(i);
            if (itemSummary != null) {
                entity.setSummary(itemSummary.summary());
                if (itemSummary.tags() != null && !itemSummary.tags().isEmpty()) {
                    entity.setTags(writeTags(itemSummary.tags()));
                }
            }
            digest.addItem(entity);
        }

        try {
            Digest saved = digestStore.saveReplacing(digest, force);
            long elapsed = System.currentTimeMillis() - started;
            log.info("digest {} {} generated: {} items, degraded={}, {}ms",
                    date, period.value(), saved.getItems().size(), degraded, elapsed);
            runStatus.recordSuccess(saved.getItems().size() + " items, degraded=" + degraded + ", " + elapsed + "ms");
            return Optional.of(saved);
        } catch (DataIntegrityViolationException e) {
            log.info("digest {} {} concurrently created, returning existing", date, period.value());
            return digestStore.find(date, period);
        }
    }

    private List<FetchedItem> fetchAll() {
        List<FetchedItem> all = new ArrayList<>();
        for (GlanceProperties.Source source : properties.sources()) {
            if (!source.enabled()) {
                continue;
            }
            fetchers.stream()
                    .filter(f -> f.supports(source))
                    .findFirst()
                    .ifPresentOrElse(fetcher -> {
                        try {
                            List<FetchedItem> items = fetcher.fetch(source);
                            log.info("source {} fetched {} items", source.name(), items.size());
                            all.addAll(items);
                        } catch (Exception e) {
                            log.warn("source {} failed: {}", source.name(), e.getMessage());
                        }
                    }, () -> log.warn("no fetcher for source type {}", source.type()));
        }
        return all;
    }

    /**
     * 批内按 URL 去重；force=true 时跳过跨期去重（用于补跑历史缺期，
     * 否则历史 URL 会被全部滤掉导致补不出内容）。
     */
    private List<FetchedItem> dedup(List<FetchedItem> fetched, boolean ignoreHistory) {
        Map<String, FetchedItem> byUrl = new LinkedHashMap<>();
        for (FetchedItem item : fetched) {
            if (item.url() == null || item.url().isBlank()) {
                continue;
            }
            byUrl.putIfAbsent(item.url(), item);
        }
        Set<String> existing = new HashSet<>();
        if (!ignoreHistory) {
            List<String> urls = List.copyOf(byUrl.keySet());

            for (int from = 0; from < urls.size(); from += DEDUP_BATCH_SIZE) {
                existing.addAll(itemRepository.findExistingUrls(
                        urls.subList(from, Math.min(from + DEDUP_BATCH_SIZE, urls.size()))));
            }
        }
        List<FetchedItem> fresh = new ArrayList<>();
        for (FetchedItem item : byUrl.values()) {
            if (!existing.contains(item.url())) {
                fresh.add(item);
            }
        }
        return fresh;
    }

    private String truncate(String title) {
        if (title != null && title.length() > MAX_TITLE_LENGTH) {
            return title.substring(0, MAX_TITLE_LENGTH - 1) + "…";
        }
        return title;
    }

    private Map<Integer, LlmSummaryResult.ItemSummary> indexSummaries(Optional<LlmSummaryResult> result) {
        Map<Integer, LlmSummaryResult.ItemSummary> map = new LinkedHashMap<>();
        result.ifPresent(r -> r.items().forEach(item -> map.put(item.index(), item)));
        return map;
    }

    private String writeTags(List<String> tags) {
        try {
            return objectMapper.writeValueAsString(tags);
        } catch (Exception e) {
            return null;
        }
    }

    private String buildTitle(LocalDate date, Period period) {
        return properties.title() + " · " + date + (period == Period.MORNING ? " 早报" : " 晚报");
    }
}
