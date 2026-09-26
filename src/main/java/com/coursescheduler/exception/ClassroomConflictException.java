package com.coursescheduler.exception;

public class ClassroomConflictException extends RuntimeException {
    public ClassroomConflictException(String message) {
        super(message);
    }
}
