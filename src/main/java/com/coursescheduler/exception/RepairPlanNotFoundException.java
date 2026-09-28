package com.coursescheduler.exception;

public class RepairPlanNotFoundException extends RuntimeException {
    public RepairPlanNotFoundException(String message) {
        super(message);
    }
}
