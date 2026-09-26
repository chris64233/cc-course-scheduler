package com.coursescheduler.dto;

import com.coursescheduler.model.OperationType;

public class AuditLogFilterRequest {
    private OperationType operationType;
    private Boolean success;

    public AuditLogFilterRequest() {}

    public OperationType getOperationType() {
        return operationType;
    }

    public void setOperationType(OperationType operationType) {
        this.operationType = operationType;
    }

    public Boolean getSuccess() {
        return success;
    }

    public void setSuccess(Boolean success) {
        this.success = success;
    }
}
