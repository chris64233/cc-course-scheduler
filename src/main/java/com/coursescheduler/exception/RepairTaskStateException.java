package com.coursescheduler.exception;

/**
 * 停用修复任务状态不允许当前操作（如任务已完成/已取消仍提交或确认方案）。
 */
public class RepairTaskStateException extends RuntimeException {
    public RepairTaskStateException(String message) {
        super(message);
    }
}
