package com.coursescheduler.dto;

import com.coursescheduler.model.OperationType;

public class OperationSummaryDTO {
    private OperationType operationType;
    private int successCount;
    private int failureCount;
    private int totalCount;

    public OperationSummaryDTO() {}

    public OperationSummaryDTO(OperationType operationType, int successCount, int failureCount, int totalCount) {
        this.operationType = operationType;
        this.successCount = successCount;
        this.failureCount = failureCount;
        this.totalCount = totalCount;
    }

    public OperationType getOperationType() {
        return operationType;
    }

    public void setOperationType(OperationType operationType) {
        this.operationType = operationType;
    }

    public int getSuccessCount() {
        return successCount;
    }

    public void setSuccessCount(int successCount) {
        this.successCount = successCount;
    }

    public int getFailureCount() {
        return failureCount;
    }

    public void setFailureCount(int failureCount) {
        this.failureCount = failureCount;
    }

    public int getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(int totalCount) {
        this.totalCount = totalCount;
    }
}
