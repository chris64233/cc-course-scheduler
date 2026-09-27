package com.coursescheduler.exception;

import com.coursescheduler.dto.RescheduleConflictDTO;

import java.util.List;

/**
 * 确认调课时发现冲突，整份方案未生效。携带整份方案的冲突明细。
 */
public class RescheduleConflictException extends RuntimeException {
    private final List<RescheduleConflictDTO> conflicts;

    public RescheduleConflictException(String message, List<RescheduleConflictDTO> conflicts) {
        super(message);
        this.conflicts = conflicts;
    }

    public List<RescheduleConflictDTO> getConflicts() {
        return conflicts;
    }
}
