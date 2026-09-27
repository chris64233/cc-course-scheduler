package com.coursescheduler.model;

public enum OperationType {
    CREATE,
    UPDATE,
    DELETE,
    BATCH_CREATE,
    BATCH_DELETE,
    BATCH_UPDATE_TIME_SLOT,
    RESCHEDULE,
    UNDO
}
