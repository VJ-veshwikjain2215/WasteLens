package com.wastelens.web;

import com.wastelens.domain.WasteEntry;
import com.wastelens.security.SecurityUtils;
import com.wastelens.service.EntryService;
import com.wastelens.web.dto.CreateEntryRequest;
import com.wastelens.web.dto.CreateEntryResponse;
import com.wastelens.web.dto.EntryResponse;
import com.wastelens.web.exception.InvalidInputException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class EntryController {

    public static final LocalDate EARLIEST_DATE = LocalDate.of(2020, 1, 1);
    public static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

    private final EntryService entryService;
    private final Clock clock;

    public EntryController(EntryService entryService, Clock clock) {
        this.entryService = entryService;
        this.clock = clock;
    }

    @PostMapping("/entries")
    public ResponseEntity<CreateEntryResponse> createEntry(@RequestBody CreateEntryRequest request) {
        UUID ownerId = SecurityUtils.getCurrentOwnerId();

        // 1. Required field checks
        if (request.submissionId() == null) {
            throw new InvalidInputException("submissionId is required");
        }
        if (request.entryDate() == null) {
            throw new InvalidInputException("entryDate is required");
        }
        if (request.category() == null) {
            throw new InvalidInputException("category is required");
        }
        if (request.stage() == null) {
            throw new InvalidInputException("stage is required");
        }
        if (request.reason() == null) {
            throw new InvalidInputException("reason is required");
        }
        if (request.grams() == null) {
            throw new InvalidInputException("grams is required");
        }

        // 2. Validate grams: integer between 1 and 100,000 (T-VAL-01, T-VAL-02)
        int grams = request.grams();
        if (grams < 1 || grams > 100000) {
            throw new InvalidInputException("grams must be an integer between 1 and 100,000");
        }

        // 3. Validate entry date: between 2020-01-01 and today (Asia/Kolkata) (T-VAL-04, T-TIME-01)
        LocalDate todayKolkata = LocalDate.now(clock.withZone(ZONE));
        if (request.entryDate().isBefore(EARLIEST_DATE)) {
            throw new InvalidInputException("entryDate cannot be earlier than 2020-01-01");
        }
        if (request.entryDate().isAfter(todayKolkata)) {
            throw new InvalidInputException("entryDate cannot be in the future (today in Asia/Kolkata is " + todayKolkata + ")");
        }

        // 4. Create entry or handle replay
        EntryService.EntryResult result = entryService.createEntry(ownerId, request);
        EntryResponse dto = EntryResponse.fromDomain(result.entry());

        if (result.replayed()) {
            return ResponseEntity.status(HttpStatus.OK).body(new CreateEntryResponse(dto, true));
        } else {
            return ResponseEntity.status(HttpStatus.CREATED).body(new CreateEntryResponse(dto, false));
        }
    }
}
