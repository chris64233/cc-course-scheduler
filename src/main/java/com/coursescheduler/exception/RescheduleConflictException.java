package com.coursescheduler.exception;

import com.coursescheduler.model.RescheduleIssue;

import java.util.ArrayList;
import java.util.List;

/**
 * 调课方案确认时因冲突或快照过期而整份无法生效。
 * 携带整份方案的冲突明细。
 */
public class RescheduleConflictException extends RuntimeException {

    private final List<RescheduleIssue> issues;

    public RescheduleConflictException(String message) {
        super(message);
        this.issues = new ArrayList<>();
    }

    public RescheduleConflictException(String message, List<RescheduleIssue> issues) {
        super(message);
        this.issues = issues != null ? new ArrayList<>(issues) : new ArrayList<>();
    }

    public List<RescheduleIssue> getIssues() {
        return issues;
    }
}
