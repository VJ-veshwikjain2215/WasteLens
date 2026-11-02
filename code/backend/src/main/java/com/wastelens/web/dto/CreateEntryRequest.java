package com.wastelens.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.wastelens.domain.Category;
import com.wastelens.domain.Reason;
import com.wastelens.domain.Stage;

import java.time.LocalDate;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = false)
public record CreateEntryRequest(
    UUID submissionId,
    @JsonDeserialize(using = StrictLocalDateDeserializer.class)
    LocalDate entryDate,
    Category category,
    @JsonDeserialize(using = StrictIntegerDeserializer.class)
    Integer grams,
    Stage stage,
    Reason reason
) {}
