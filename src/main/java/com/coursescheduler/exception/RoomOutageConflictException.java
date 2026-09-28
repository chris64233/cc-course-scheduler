package com.coursescheduler.exception;

import java.util.List;

/**
 * 教室停用事件冲突：相同外部事件号对应不同内容、或对已取消事件做范围调整等。
 */
public class RoomOutageConflictException extends RuntimeException {
    private final List<Long> missingScheduleIds;

    public RoomOutageConflictException(String message) {
        this(message, null);
    }

    public RoomOutageConflictException(String message, List<Long> missingScheduleIds) {
        super(message);
        this.missingScheduleIds = missingScheduleIds;
    }

    public List<Long> getMissingScheduleIds() {
        return missingScheduleIds;
    }
}
