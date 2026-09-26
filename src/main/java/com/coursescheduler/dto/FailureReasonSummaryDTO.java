package com.coursescheduler.dto;

import java.time.LocalDateTime;

public class FailureReasonSummaryDTO {
    private String failureReason;
    private int count;
    private LocalDateTime lastOccurrenceTime;

    public FailureReasonSummaryDTO() {}

    public FailureReasonSummaryDTO(String failureReason, int count, LocalDateTime lastOccurrenceTime) {
        this.failureReason = failureReason;
        this.count = count;
        this.lastOccurrenceTime = lastOccurrenceTime;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public LocalDateTime getLastOccurrenceTime() {
        return lastOccurrenceTime;
    }

    public void setLastOccurrenceTime(LocalDateTime lastOccurrenceTime) {
        this.lastOccurrenceTime = lastOccurrenceTime;
    }
}
