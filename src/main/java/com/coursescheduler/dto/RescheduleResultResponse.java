package com.coursescheduler.dto;

import com.coursescheduler.model.ReschedulePlanStatus;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 调课方案处理结果（确认或拒绝）。
 *
 * <p>确认时：applied 为 true 表示全部变更已一次性生效；为 false 表示整份方案未生效，
 * issues 给出阻止生效的冲突明细，方案仍保持待处理状态。
 * 拒绝时：applied 为 false，status 为 REJECTED（终态）。
 */
public class RescheduleResultResponse {
    private String planId;
    private String businessId;
    private ReschedulePlanStatus status;
    private boolean applied;
    private int conflictCount;
    private List<RescheduleIssueDTO> issues;
    private List<RescheduleChangeDTO> changes;
    private LocalDateTime processedAt;

    public RescheduleResultResponse() {}

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

    public boolean isApplied() {
        return applied;
    }

    public void setApplied(boolean applied) {
        this.applied = applied;
    }

    public int getConflictCount() {
        return conflictCount;
    }

    public void setConflictCount(int conflictCount) {
        this.conflictCount = conflictCount;
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

    public LocalDateTime getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(LocalDateTime processedAt) {
        this.processedAt = processedAt;
    }
}
