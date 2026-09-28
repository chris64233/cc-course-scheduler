package com.coursescheduler.model;

public enum OperationType {
    CREATE,
    UPDATE,
    DELETE,
    BATCH_CREATE,
    BATCH_DELETE,
    BATCH_UPDATE_TIME_SLOT,
    RESCHEDULE,
    /** 教室停用事件登记生效 */
    OUTAGE_REGISTER,
    /** 教室停用范围（时间段/原因）调整 */
    OUTAGE_UPDATE,
    /** 教室停用取消 */
    OUTAGE_CANCEL,
    /** 停用修复方案确认产生的课程调整 */
    REPAIR_RESCHEDULE,
    UNDO
}
