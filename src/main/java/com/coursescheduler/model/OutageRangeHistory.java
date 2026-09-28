package com.coursescheduler.model;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 停用范围/原因变更历史的一条记录。
 */
public class OutageRangeHistory {
    private final List<String> timeSlots;
    private final String reason;
    private final long version;
    private final LocalDateTime changedAt;
    private final String changedBy;

    public OutageRangeHistory(List<String> timeSlots, String reason, long version,
                              LocalDateTime changedAt, String changedBy) {
        this.timeSlots = timeSlots;
        this.reason = reason;
        this.version = version;
        this.changedAt = changedAt;
        this.changedBy = changedBy;
    }

    public List<String> getTimeSlots() {
        return timeSlots;
    }

    public String getReason() {
        return reason;
    }

    public long getVersion() {
        return version;
    }

    public LocalDateTime getChangedAt() {
        return changedAt;
    }

    public String getChangedBy() {
        return changedBy;
    }
}
