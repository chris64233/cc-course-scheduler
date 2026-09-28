package com.coursescheduler.model;

/**
 * 修复任务状态。
 */
public enum RepairTaskStatus {
    /** 待修复：冻结的全部或部分课程尚未完成修复 */
    OPEN,
    /** 已完成：全部受影响课程已由一份确认的修复方案安排妥当 */
    RESOLVED,
    /** 已取消：停用事件在修复完成前取消，未修复课程保持原状，已完成的修复不回退 */
    CANCELLED
}
