package com.wastelens.web.exception;

public class CapacityLimitException extends RuntimeException {
    public CapacityLimitException(String message) {
        super(message);
    }
}
