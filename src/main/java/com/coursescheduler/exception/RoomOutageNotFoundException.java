package com.coursescheduler.exception;

public class RoomOutageNotFoundException extends RuntimeException {
    public RoomOutageNotFoundException(String message) {
        super(message);
    }
}
