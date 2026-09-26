package com.coursescheduler.dto;

import com.coursescheduler.model.OperationType;

import java.time.LocalDateTime;

public class OperationFailureSummaryDTO {
    private OperationType operationType;
    private int failureCount;
    private LocalDateTime lastFailureTime;

    public OperationFailureSummaryDTO() {}

    public OperationFailureSummaryDTO(OperationType operationType, int failureCount, LocalDateTime lastFailureTime) {
        this.operationType = operationType;
        this.failureCount = failureCount;
        this.lastFailureTime = lastFailureTime;
    }

    public OperationType getOperationType() {
        return operationType;
    }

    public void setOperationType(OperationType operationType) {
        this.operationType = operationType;
    }

    public int getFailureCount() {
        return failureCount;
    }

    public void setFailureCount(int failureCount) {
        this.failureCount = failureCount;
    }

    public LocalDateTime getLastFailureTime() {
        return lastFailureTime;
    }

    public void setLastFailureTime(LocalDateTime lastFailureTime) {
        this.lastFailureTime = lastFailureTime;
    }
}
