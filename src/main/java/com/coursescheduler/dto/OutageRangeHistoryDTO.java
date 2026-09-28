package com.coursescheduler.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 停用范围/原因变更历史明细。
 */
public class OutageRangeHistoryDTO {
    private long version;
    private List<String> timeSlots;
    private String reason;
    private LocalDateTime changedAt;
    private String changedBy;

    public OutageRangeHistoryDTO() {}

    public OutageRangeHistoryDTO(long version, List<String> timeSlots, String reason,
                                 LocalDateTime changedAt, String changedBy) {
        this.version = version;
        this.timeSlots = timeSlots;
        this.reason = reason;
        this.changedAt = changedAt;
        this.changedBy = changedBy;
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }

    public List<String> getTimeSlots() {
        return timeSlots;
    }

    public void setTimeSlots(List<String> timeSlots) {
        this.timeSlots = timeSlots;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public LocalDateTime getChangedAt() {
        return changedAt;
    }

    public void setChangedAt(LocalDateTime changedAt) {
        this.changedAt = changedAt;
    }

    public String getChangedBy() {
        return changedBy;
    }

    public void setChangedBy(String changedBy) {
        this.changedBy = changedBy;
    }
}
