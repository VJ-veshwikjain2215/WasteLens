package com.wastelens.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum Reason {
    OVERPRODUCTION("overproduction"),
    SPOILAGE("spoilage"),
    TRIMMING("trimming"),
    PLATE_RETURN("plate_return"),
    OTHER("other");

    private final String value;

    Reason(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static Reason fromValue(String text) {
        if (text != null) {
            for (Reason r : Reason.values()) {
                if (r.value.equals(text)) {
                    return r;
                }
            }
        }
        throw new IllegalArgumentException("Invalid reason: " + text);
    }
}
