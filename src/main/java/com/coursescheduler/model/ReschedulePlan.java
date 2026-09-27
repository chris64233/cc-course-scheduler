package com.coursescheduler.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 原子调课方案聚合根。
 *
 * <p>持有业务号（幂等键）、互不重复的调课条目（含提交时的原始快照）、状态、
 * 最近一次预检/确认失败的冲突明细，以及确认成功后每条课程的前后变更明细。
 */
public class ReschedulePlan {
    private final String planId;
    private final String businessId;
    private final LocalDateTime createdAt;
    private final List<RescheduleItem> items;

    private ReschedulePlanStatus status;
    private LocalDateTime processedAt;

    /** 最近一次预检或确认失败得到的整份方案冲突明细。 */
    private List<RescheduleIssue> lastIssues = new ArrayList<>();

    /** 确认成功后每条课程调整前后的审计记录。 */
    private List<RescheduleChange> changes = new ArrayList<>();

    public ReschedulePlan(String planId, String businessId, LocalDateTime createdAt,
                          List<RescheduleItem> items) {
        this.planId = planId;
        this.businessId = businessId;
        this.createdAt = createdAt;
        this.items = new ArrayList<>(items);
        this.status = ReschedulePlanStatus.PENDING;
    }

    public String getPlanId() {
        return planId;
    }

    public String getBusinessId() {
        return businessId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public List<RescheduleItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    public ReschedulePlanStatus getStatus() {
        return status;
    }

    public void setStatus(ReschedulePlanStatus status) {
        this.status = status;
    }

    public LocalDateTime getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(LocalDateTime processedAt) {
        this.processedAt = processedAt;
    }

    public List<RescheduleIssue> getLastIssues() {
        return Collections.unmodifiableList(lastIssues);
    }

    public void setLastIssues(List<RescheduleIssue> issues) {
        this.lastIssues = new ArrayList<>(issues);
    }

    public List<RescheduleChange> getChanges() {
        return Collections.unmodifiableList(changes);
    }

    public void setChanges(List<RescheduleChange> newChanges) {
        this.changes = new ArrayList<>(newChanges);
    }
}
