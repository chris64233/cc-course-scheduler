package com.coursescheduler.exception;

public class ReschedulePlanNotFoundException extends RuntimeException {
    public ReschedulePlanNotFoundException(String message) {
        super(message);
    }
}
