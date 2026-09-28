package com.coursescheduler.model;

/**
 * 教室停用事件状态。
 */
public enum RoomOutageStatus {
    /** 生效中：目标时段视为不可用 */
    ACTIVE,
    /** 已取消：目标时段恢复可用，已完成的修复不自动回退 */
    CANCELLED
}
