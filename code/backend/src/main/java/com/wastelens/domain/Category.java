package com.wastelens.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum Category {
    PRODUCE("produce"),
    GRAINS("grains"),
    DAIRY("dairy"),
    PROTEIN("protein"),
    PREPARED("prepared"),
    OTHER("other");

    private final String value;

    Category(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static Category fromValue(String text) {
        if (text != null) {
            for (Category c : Category.values()) {
                if (c.value.equals(text)) {
                    return c;
                }
            }
        }
        throw new IllegalArgumentException("Invalid category: " + text);
    }
}
