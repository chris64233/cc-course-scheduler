package com.coursescheduler.exception;

/**
 * 停用修复任务/方案状态不合法：任务已修复/已取消/已被范围调整取代，
 * 或修复方案已确认/拒绝后再次处理。
 */
public class RepairStateException extends RuntimeException {
    public RepairStateException(String message) {
        super(message);
    }
}
