package com.coursescheduler.exception;

/**
 * 教室停用事件不存在（按外部事件号或任务/方案 ID 查询不到）。
 */
public class OutageNotFoundException extends RuntimeException {
    public OutageNotFoundException(String message) {
        super(message);
    }
}
