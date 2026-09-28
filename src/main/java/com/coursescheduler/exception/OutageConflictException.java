package com.coursescheduler.exception;

/**
 * 教室停用业务冲突：相同外部事件号登记了不同内容、停用已取消后重复操作等。
 */
public class OutageConflictException extends RuntimeException {
    public OutageConflictException(String message) {
        super(message);
    }
}
