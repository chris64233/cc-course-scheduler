package com.coursescheduler.model;

public enum OperationType {
    CREATE,
    UPDATE,
    DELETE,
    BATCH_CREATE,
    BATCH_DELETE,
    BATCH_UPDATE_TIME_SLOT,
    RESCHEDULE,
    /** 教室停用事件生效（冻结受影响课程） */
    ROOM_OUTAGE_CREATE,
    /** 教室停用范围/原因调整 */
    ROOM_OUTAGE_ADJUST,
    /** 教室停用取消 */
    ROOM_OUTAGE_CANCEL,
    /** 停用后的成组修复确认 */
    REPAIR_CONFIRM,
    UNDO
}
