package com.wastelens.web.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.regex.Pattern;

public class StrictLocalDateDeserializer extends JsonDeserializer<LocalDate> {

    private static final Pattern STRICT_DATE_PATTERN = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");
    private static final DateTimeFormatter STRICT_FORMATTER = DateTimeFormatter.ofPattern("uuuu-MM-dd")
            .withResolverStyle(ResolverStyle.STRICT);

    @Override
    public LocalDate deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        if (p.currentToken() != JsonToken.VALUE_STRING) {
            throw new IllegalArgumentException("entryDate must be a string in YYYY-MM-DD format");
        }
        String text = p.getText().trim();
        if (!STRICT_DATE_PATTERN.matcher(text).matches()) {
            throw new IllegalArgumentException("entryDate must be strictly in YYYY-MM-DD format");
        }
        try {
            return LocalDate.parse(text, STRICT_FORMATTER);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid calendar date: " + text, e);
        }
    }
}
