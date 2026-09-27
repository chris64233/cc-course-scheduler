package com.coursescheduler.dto;

import com.coursescheduler.model.ReschedulePlanStatus;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 调课方案视图：方案基本信息、条目、状态、最近冲突明细与确认后的变更明细。
 */
public class ReschedulePlanResponse {
    private String planId;
    private String businessId;
    private ReschedulePlanStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime processedAt;
    private List<RescheduleItemDTO> items;
    private List<RescheduleIssueDTO> issues;
    private List<RescheduleChangeDTO> changes;

    public ReschedulePlanResponse() {}

    public String getPlanId() {
        return planId;
    }

    public void setPlanId(String planId) {
        this.planId = planId;
    }

    public String getBusinessId() {
        return businessId;
    }

    public void setBusinessId(String businessId) {
        this.businessId = businessId;
    }

    public ReschedulePlanStatus getStatus() {
        return status;
    }

    public void setStatus(ReschedulePlanStatus status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(LocalDateTime processedAt) {
        this.processedAt = processedAt;
    }

    public List<RescheduleItemDTO> getItems() {
        return items;
    }

    public void setItems(List<RescheduleItemDTO> items) {
        this.items = items;
    }

    public List<RescheduleIssueDTO> getIssues() {
        return issues;
    }

    public void setIssues(List<RescheduleIssueDTO> issues) {
        this.issues = issues;
    }

    public List<RescheduleChangeDTO> getChanges() {
        return changes;
    }

    public void setChanges(List<RescheduleChangeDTO> changes) {
        this.changes = changes;
    }
}
