package com.coursescheduler.exception;

public class NoUndoAvailableException extends RuntimeException {
    public NoUndoAvailableException(String message) {
        super(message);
    }
}
