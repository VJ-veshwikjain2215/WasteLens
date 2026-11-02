package com.wastelens.repository;

import com.wastelens.domain.Category;
import com.wastelens.domain.Reason;
import com.wastelens.domain.Stage;
import com.wastelens.domain.WasteEntry;
import com.wastelens.service.EntryService;
import com.wastelens.web.dto.CreateEntryRequest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import org.springframework.test.context.TestPropertySource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.hikari.maximum-pool-size=25",
    "spring.datasource.hikari.connection-timeout=15000"
})
class PostgresEntryIntegrationTest {

    static {
        loadLocalEnv();
    }

    @Autowired
    private EntryService entryService;

    @Autowired
    private WasteEntryRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
    @DisplayName("T-IDEM-01: 20 concurrent identical POSTs yield exactly 1 row, one 201 and 19 200s")
    void tIdem01_concurrentIdenticalSubmissions() throws Exception {
        UUID ownerA = UUID.randomUUID();
        UUID submissionId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 11, 1);
        CreateEntryRequest request = new CreateEntryRequest(
            submissionId, date, Category.PRODUCE, 500, Stage.PREPARATION, Reason.TRIMMING
        );

        int concurrency = 20;
        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch finishGate = new CountDownLatch(concurrency);
        List<EntryService.EntryResult> results = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < concurrency; i++) {
            executor.submit(() -> {
                try {
                    startGate.await();
                    EntryService.EntryResult res = entryService.createEntry(ownerA, request);
                    results.add(res);
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    finishGate.countDown();
                }
            });
        }

        startGate.countDown(); // Release all threads simultaneously
        finishGate.await();
        executor.shutdown();

        assertEquals(concurrency, results.size());

        long createdCount = results.stream().filter(r -> !r.replayed()).count();
        long replayedCount = results.stream().filter(EntryService.EntryResult::replayed).count();

        // Exactly one new insert, rest replayed
        assertEquals(1, createdCount, "Exactly 1 concurrent request should result in replayed=false");
        assertEquals(concurrency - 1, replayedCount, "Remaining requests should result in replayed=true");

        // Verify identical entry ID across all results
        UUID firstId = results.get(0).entry().id();
        for (EntryService.EntryResult res : results) {
            assertEquals(firstId, res.entry().id());
            assertEquals(500, res.entry().grams());
        }

        // Verify database actually has exactly 1 row
        assertEquals(1, repository.countByOwnerId(ownerA));
    }

    @Test
    @DisplayName("T-IDEM-03: Same submissionId by Owner B creates an independent row")
    void tIdem03_sameSubmissionIdDifferentOwner() {
        UUID ownerA = UUID.randomUUID();
        UUID ownerB = UUID.randomUUID();
        UUID sharedSubmissionId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 11, 1);

        CreateEntryRequest reqA = new CreateEntryRequest(sharedSubmissionId, date, Category.PRODUCE, 200, Stage.PREPARATION, Reason.TRIMMING);
        CreateEntryRequest reqB = new CreateEntryRequest(sharedSubmissionId, date, Category.GRAINS, 400, Stage.SERVICE, Reason.PLATE_RETURN);

        EntryService.EntryResult resA = entryService.createEntry(ownerA, reqA);
        EntryService.EntryResult resB = entryService.createEntry(ownerB, reqB);

        assertNotNull(resA);
        assertNotNull(resB);
        assertNotEquals(resA.entry().id(), resB.entry().id());
        assertEquals(ownerA, resA.entry().ownerId());
        assertEquals(ownerB, resB.entry().ownerId());

        // Verify owner row counts are isolated
        assertEquals(1, repository.countByOwnerId(ownerA));
        assertEquals(1, repository.countByOwnerId(ownerB));
    }
}
