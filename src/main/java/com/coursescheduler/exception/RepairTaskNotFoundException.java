package com.coursescheduler.exception;

public class RepairTaskNotFoundException extends RuntimeException {
    public RepairTaskNotFoundException(String message) {
        super(message);
    }
}
