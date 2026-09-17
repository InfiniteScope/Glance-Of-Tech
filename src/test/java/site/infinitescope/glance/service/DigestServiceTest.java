package site.infinitescope.glance.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import site.infinitescope.glance.config.GlanceProperties;
import site.infinitescope.glance.fetch.FetchedItem;
import site.infinitescope.glance.fetch.SourceFetcher;
import site.infinitescope.glance.llm.LlmService;
import site.infinitescope.glance.llm.LlmSummaryResult;
import site.infinitescope.glance.model.Digest;
import site.infinitescope.glance.model.Period;
import site.infinitescope.glance.repo.DigestItemRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DigestServiceTest {

    private static final LocalDate DATE = LocalDate.of(2026, 9, 16);
    private static final GlanceProperties.Source HN = new GlanceProperties.Source(
            "HackerNews", GlanceProperties.SourceType.ALGOLIA, "http://x", 10, true);

    private SourceFetcher fetcher;
    private LlmService llmService;
    private DigestStore digestStore;
    private DigestItemRepository itemRepository;
    private RunStatus runStatus;
    private DigestService service;

    @BeforeEach
    void setUp() {
        fetcher = mock(SourceFetcher.class);
        llmService = mock(LlmService.class);
        digestStore = mock(DigestStore.class);
        itemRepository = mock(DigestItemRepository.class);
        runStatus = new RunStatus(new GlanceProperties("Asia/Shanghai", null, null, List.of(), null, null));
        GlanceProperties properties = new GlanceProperties("Asia/Shanghai", null, null, List.of(HN), null, null);
        service = new DigestService(properties, List.of(fetcher), llmService, digestStore,
                itemRepository, runStatus, new ObjectMapper());
        when(fetcher.supports(any())).thenReturn(true);
    }

    @Test
    void skipsWhenDigestAlreadyExists() {
        Digest existing = digest(false);
        when(digestStore.exists(DATE, Period.MORNING)).thenReturn(true);
        when(digestStore.find(DATE, Period.MORNING)).thenReturn(Optional.of(existing));

        Optional<Digest> result = service.generate(DATE, Period.MORNING, false);

        assertTrue(result.isPresent());
        verify(fetcher, never()).fetch(any());
        verify(llmService, never()).summarize(anyList());
        verify(digestStore, never()).saveReplacing(any(), anyBoolean());
        assertEquals("SKIPPED", runStatus.current().lastRunStatus());
    }

    @Test
    void degradedWhenLlmFailsButStillPersistsItems() {
        when(digestStore.exists(DATE, Period.MORNING)).thenReturn(false);
        when(fetcher.fetch(HN)).thenReturn(List.of(
                new FetchedItem("HackerNews", "Title A", "https://a.example", null, Instant.now())));
        when(itemRepository.findExistingUrls(anyCollection())).thenReturn(Set.of());
        when(llmService.summarize(anyList())).thenReturn(Optional.empty());
        when(digestStore.saveReplacing(any(), eq(false))).thenAnswer(inv -> inv.getArgument(0));

        Optional<Digest> result = service.generate(DATE, Period.MORNING, false);

        assertTrue(result.isPresent());
        Digest digest = result.get();
        assertTrue(digest.isDegraded());
        assertNull(digest.getSummary());
        assertEquals(1, digest.getItems().size());
        assertEquals("Title A", digest.getItems().get(0).getTitle());
        assertNull(digest.getItems().get(0).getSummary());
        assertEquals("SUCCESS", runStatus.current().lastRunStatus());
    }

    @Test
    void appliesLlmSummariesByIndex() {
        when(digestStore.exists(DATE, Period.EVENING)).thenReturn(false);
        when(fetcher.fetch(HN)).thenReturn(List.of(
                new FetchedItem("HackerNews", "A", "https://a.example", null, null),
                new FetchedItem("HackerNews", "B", "https://b.example", null, null)));
        when(itemRepository.findExistingUrls(anyCollection())).thenReturn(Set.of());
        when(llmService.summarize(anyList())).thenReturn(Optional.of(new LlmSummaryResult(
                "overview text", List.of(new LlmSummaryResult.ItemSummary(1, "summary of B", List.of("ai"))))));
        when(digestStore.saveReplacing(any(), eq(false))).thenAnswer(inv -> inv.getArgument(0));

        Optional<Digest> result = service.generate(DATE, Period.EVENING, false);

        assertTrue(result.isPresent());
        Digest digest = result.get();
        assertFalse(digest.isDegraded());
        assertEquals("overview text", digest.getSummary());
        assertNull(digest.getItems().get(0).getSummary());
        assertEquals("summary of B", digest.getItems().get(1).getSummary());
        assertEquals("[\"ai\"]", digest.getItems().get(1).getTags());
    }

    @Test
    void dedupsWithinBatchAndAgainstDatabase() {
        when(digestStore.exists(DATE, Period.MORNING)).thenReturn(false);
        FetchedItem a1 = new FetchedItem("HackerNews", "A1", "https://dup.example", null, null);
        FetchedItem a2 = new FetchedItem("HackerNews", "A2", "https://dup.example", null, null);
        FetchedItem old = new FetchedItem("HackerNews", "Old", "https://old.example", null, null);
        when(fetcher.fetch(HN)).thenReturn(List.of(a1, a2, old));
        when(itemRepository.findExistingUrls(anyCollection())).thenReturn(Set.of("https://old.example"));
        when(llmService.summarize(anyList())).thenReturn(Optional.empty());
        when(digestStore.saveReplacing(any(), eq(false))).thenAnswer(inv -> inv.getArgument(0));

        Optional<Digest> result = service.generate(DATE, Period.MORNING, false);

        assertTrue(result.isPresent());
        assertEquals(1, result.get().getItems().size());
        assertEquals("A1", result.get().getItems().get(0).getTitle());
    }

    @Test
    void skipsPeriodWhenAllSourcesFail() {
        when(digestStore.exists(DATE, Period.MORNING)).thenReturn(false);
        when(fetcher.fetch(HN)).thenThrow(new RuntimeException("network down"));

        Optional<Digest> result = service.generate(DATE, Period.MORNING, false);

        assertTrue(result.isEmpty());
        verify(llmService, never()).summarize(anyList());
        verify(digestStore, never()).saveReplacing(any(), anyBoolean());
        assertEquals("FAILED", runStatus.current().lastRunStatus());
    }

    @Test
    void truncatesOverlongTitles() {
        when(digestStore.exists(DATE, Period.MORNING)).thenReturn(false);
        String longTitle = "x".repeat(300);
        when(fetcher.fetch(HN)).thenReturn(List.of(
                new FetchedItem("HackerNews", longTitle, "https://a.example", null, null)));
        when(itemRepository.findExistingUrls(anyCollection())).thenReturn(Set.of());
        when(llmService.summarize(anyList())).thenReturn(Optional.empty());

        ArgumentCaptor<Digest> captor = ArgumentCaptor.forClass(Digest.class);
        when(digestStore.saveReplacing(captor.capture(), eq(false))).thenAnswer(inv -> inv.getArgument(0));

        service.generate(DATE, Period.MORNING, false);

        String saved = captor.getValue().getItems().get(0).getTitle();
        assertEquals(200, saved.length());
        assertTrue(saved.endsWith("…"));
    }

    private Digest digest(boolean degraded) {
        return new Digest(DATE, Period.MORNING, "t", null, degraded, OffsetDateTime.now());
    }
}
