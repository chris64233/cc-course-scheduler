package com.coursescheduler.model;

/**
 * 教室停用事件状态。
 *
 * <ul>
 *   <li>{@link #ACTIVE} 生效中：停用时段对新增/普通调课生效，修复任务可处理；</li>
 *   <li>{@link #CANCELLED} 已取消：停用不再生效，但不自动回退已经完成的修复。</li>
 * </ul>
 */
public enum OutageStatus {
    ACTIVE,
    CANCELLED
}
