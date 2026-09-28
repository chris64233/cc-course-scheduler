package com.coursescheduler.model;

/**
 * 教室停用修复任务状态。
 *
 * <ul>
 *   <li>{@link #PENDING} 待修复：任务中仍有受影响课程等待修复方案；</li>
 *   <li>{@link #REPAIRED} 已修复：全部受影响课程已通过修复方案安排；</li>
 *   <li>{@link #CANCELLED} 已取消：停用事件被取消，未完成的任务随之取消，已完成的修复保留。</li>
 * </ul>
 */
public enum RepairTaskStatus {
    PENDING,
    REPAIRED,
    CANCELLED
}
