package com.wastelens.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wastelens.domain.Category;
import com.wastelens.domain.Reason;
import com.wastelens.domain.Stage;
import com.wastelens.service.EntryService;
import com.wastelens.service.ReportService;
import com.wastelens.web.dto.CategoryGramsDto;
import com.wastelens.web.dto.CompareResponse;
import com.wastelens.web.dto.CreateEntryRequest;
import com.wastelens.web.dto.WeekSummaryResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.hikari.maximum-pool-size=10",
    "spring.datasource.hikari.connection-timeout=15000"
})
class PostgresReportingIntegrationTest {

    static {
        loadLocalEnv();
    }

    @Autowired
    private EntryService entryService;

    @Autowired
    private ReportService reportService;

    @Autowired
    private ObjectMapper objectMapper;

    static void loadLocalEnv() {
        Path[] candidates = { Path.of("../.env.local"), Path.of(".env.local"), Path.of("../.env.neon"), Path.of(".env.neon") };
        for (Path p : candidates) {
            if (Files.exists(p)) {
                try {
                    List<String> lines = Files.readAllLines(p);
                    for (String line : lines) {
                        line = line.trim();
                        if (line.isEmpty() || line.startsWith("#")) continue;
                        int eq = line.indexOf('=');
                        if (eq > 0) {
                            String k = line.substring(0, eq).replace("\uFEFF", "").trim();
                            String v = line.substring(eq + 1).replace("\uFEFF", "").trim().replaceAll("^[\"']|[\"']$", "");
                            if (System.getProperty(k) == null && System.getenv(k) == null) {
                                System.setProperty(k, v);
                            }
                        }
                    }
                } catch (Exception ignored) {}
                break;
            }
        }
    }

    @Test
    @DisplayName("T-CMP-01: Four-entry worked example against real Postgres (2000g/2 vs 1000g/2 -> -1000g, -50.0%)")
    void tCmp01_fourEntryWorkedExample() {
        UUID owner = UUID.randomUUID();

        // Week A (2026-10-19..25): produce 1200g, grains 800g = 2000g, 2 entries
        LocalDate dateA1 = LocalDate.of(2026, 10, 20);
        LocalDate dateA2 = LocalDate.of(2026, 10, 22);
        entryService.createEntry(owner, new CreateEntryRequest(UUID.randomUUID(), dateA1, Category.PRODUCE, 1200, Stage.PREPARATION, Reason.TRIMMING));
        entryService.createEntry(owner, new CreateEntryRequest(UUID.randomUUID(), dateA2, Category.GRAINS, 800, Stage.PREPARATION, Reason.SPOILAGE));

        // Week B (2026-10-26..11-01): produce 600g, dairy 400g = 1000g, 2 entries
        LocalDate dateB1 = LocalDate.of(2026, 10, 27);
        LocalDate dateB2 = LocalDate.of(2026, 10, 29);
        entryService.createEntry(owner, new CreateEntryRequest(UUID.randomUUID(), dateB1, Category.PRODUCE, 600, Stage.SERVICE, Reason.PLATE_RETURN));
        entryService.createEntry(owner, new CreateEntryRequest(UUID.randomUUID(), dateB2, Category.DAIRY, 400, Stage.STORAGE, Reason.SPOILAGE));

        LocalDate weekA = LocalDate.of(2026, 10, 19);
        LocalDate weekB = LocalDate.of(2026, 10, 26);

        CompareResponse comparison = reportService.compareWeeks(owner, weekA, weekB);

        assertTrue(comparison.comparable());
        assertEquals(-1000L, comparison.differenceGrams());
        assertEquals(-50.0, comparison.percentChange());

        // Verify week A summary
        assertEquals(2000L, comparison.weekA().totalGrams());
        assertEquals(2L, comparison.weekA().entryCount());

        // Verify week B summary and top categories
        assertEquals(1000L, comparison.weekB().totalGrams());
        assertEquals(2L, comparison.weekB().entryCount());
        List<CategoryGramsDto> topB = comparison.weekB().topCategories();
        assertEquals(2, topB.size());
        assertEquals("produce", topB.get(0).category());
        assertEquals(600L, topB.get(0).grams());
        assertEquals("dairy", topB.get(1).category());
        assertEquals(400L, topB.get(1).grams());
    }

    @Test
    @DisplayName("T-AGG-02: Category ties resolved lexicographically by category code ascending")
    void tAgg02_categoryTiesSortedAlphabetically() {
        UUID owner = UUID.randomUUID();
        LocalDate monday = LocalDate.of(2026, 10, 19);

        // Insert identical grams for DAIRY, GRAINS, and PRODUCE (500g each)
        // Alphabetical order: dairy, grains, produce
        entryService.createEntry(owner, new CreateEntryRequest(UUID.randomUUID(), monday, Category.PRODUCE, 500, Stage.PREPARATION, Reason.TRIMMING));
        entryService.createEntry(owner, new CreateEntryRequest(UUID.randomUUID(), monday, Category.DAIRY, 500, Stage.STORAGE, Reason.SPOILAGE));
        entryService.createEntry(owner, new CreateEntryRequest(UUID.randomUUID(), monday, Category.GRAINS, 500, Stage.SERVICE, Reason.PLATE_RETURN));

        WeekSummaryResponse summary = reportService.getWeekSummary(owner, monday);

        assertEquals(1500L, summary.totalGrams());
        assertEquals(3L, summary.entryCount());

        List<CategoryGramsDto> top = summary.topCategories();
        assertEquals(3, top.size());
        assertEquals("dairy", top.get(0).category());
        assertEquals("grains", top.get(1).category());
        assertEquals("produce", top.get(2).category());
    }

    @Test
    @DisplayName("T-AGG-03: Identical entries with different submissionId are counted twice")
    void tAgg03_identicalEntriesWithDifferentSubmissionIdsCounted() {
        UUID owner = UUID.randomUUID();
        LocalDate monday = LocalDate.of(2026, 10, 19);

        entryService.createEntry(owner, new CreateEntryRequest(UUID.randomUUID(), monday, Category.PRODUCE, 300, Stage.PREPARATION, Reason.TRIMMING));
        entryService.createEntry(owner, new CreateEntryRequest(UUID.randomUUID(), monday, Category.PRODUCE, 300, Stage.PREPARATION, Reason.TRIMMING));

        WeekSummaryResponse summary = reportService.getWeekSummary(owner, monday);
        assertEquals(600L, summary.totalGrams());
        assertEquals(2L, summary.entryCount());
    }

    @Test
    @DisplayName("T-EXP-01: Full export is parseable JSON, sorted by entryDate and id, and strictly isolated")
    void tExp01_exportSortedAndIsolated() throws Exception {
        UUID ownerA = UUID.randomUUID();
        UUID ownerB = UUID.randomUUID();

        LocalDate d1 = LocalDate.of(2026, 10, 10);
        LocalDate d2 = LocalDate.of(2026, 10, 20);

        // Owner A entries
        entryService.createEntry(ownerA, new CreateEntryRequest(UUID.randomUUID(), d2, Category.PRODUCE, 500, Stage.PREPARATION, Reason.TRIMMING));
        entryService.createEntry(ownerA, new CreateEntryRequest(UUID.randomUUID(), d1, Category.DAIRY, 250, Stage.STORAGE, Reason.SPOILAGE));

        // Owner B entry (must not leak into A's export)
        entryService.createEntry(ownerB, new CreateEntryRequest(UUID.randomUUID(), d1, Category.PROTEIN, 9999, Stage.SERVICE, Reason.OVERPRODUCTION));

        byte[] exportBytes = reportService.exportEntries(ownerA);
        JsonNode root = objectMapper.readTree(exportBytes);

        assertEquals(1, root.get("schemaVersion").asInt());
        assertEquals("Asia/Kolkata", root.get("timezone").asText());

        JsonNode entries = root.get("entries");
        assertEquals(2, entries.size());

        // Sorted by entryDate ASC: d1 (2026-10-10) must be first, then d2 (2026-10-20)
        assertEquals("2026-10-10", entries.get(0).get("entryDate").asText());
        assertEquals("dairy", entries.get(0).get("category").asText());
        assertEquals(250, entries.get(0).get("grams").asInt());

        assertEquals("2026-10-20", entries.get(1).get("entryDate").asText());
        assertEquals("produce", entries.get(1).get("category").asText());
        assertEquals(500, entries.get(1).get("grams").asInt());

        // Confirm zero trace of Owner B
        assertFalse(new String(exportBytes).contains("9999"));
        assertFalse(new String(exportBytes).contains("protein"));
    }
}
