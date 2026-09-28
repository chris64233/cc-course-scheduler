package com.coursescheduler.exception;

/**
 * 课程安排落入生效中的教室停用时段。
 */
public class RoomOutageBlockedException extends RuntimeException {
    public RoomOutageBlockedException(String message) {
        super(message);
    }
}
