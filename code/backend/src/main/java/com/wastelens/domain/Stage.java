package com.wastelens.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum Stage {
    PREPARATION("preparation"),
    SERVICE("service"),
    STORAGE("storage");

    private final String value;

    Stage(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static Stage fromValue(String text) {
        if (text != null) {
            for (Stage s : Stage.values()) {
                if (s.value.equals(text)) {
                    return s;
                }
            }
        }
        throw new IllegalArgumentException("Invalid stage: " + text);
    }
}
