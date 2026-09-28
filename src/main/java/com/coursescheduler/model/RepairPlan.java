package com.coursescheduler.model;

import com.coursescheduler.dto.RescheduleConflictDTO;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 教室停用后的成组修复方案。
 *
 * <p>方案必须覆盖对应修复任务中全部待修复课程；确认时一次性应用全部调整，
 * 任一课程无法安排则整份方案不生效。
 */
public class RepairPlan {
    private Long id;
    private String bizKey;
    private String fingerprint;
    private Long repairTaskId;
    private ReschedulePlanStatus status;
    private List<ReschedulePlanItem> items = new ArrayList<>();
    private List<RescheduleConflictDTO> conflicts = new ArrayList<>();
    private String resultMessage;
    private String createdBy;
    private LocalDateTime createdAt;
    private String processedBy;
    private LocalDateTime processedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getBizKey() {
        return bizKey;
    }

    public void setBizKey(String bizKey) {
        this.bizKey = bizKey;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public void setFingerprint(String fingerprint) {
        this.fingerprint = fingerprint;
    }

    public Long getRepairTaskId() {
        return repairTaskId;
    }

    public void setRepairTaskId(Long repairTaskId) {
        this.repairTaskId = repairTaskId;
    }

    public ReschedulePlanStatus getStatus() {
        return status;
    }

    public void setStatus(ReschedulePlanStatus status) {
        this.status = status;
    }

    public List<ReschedulePlanItem> getItems() {
        return items;
    }

    public void setItems(List<ReschedulePlanItem> items) {
        this.items = items;
    }

    public List<RescheduleConflictDTO> getConflicts() {
        return conflicts;
    }

    public void setConflicts(List<RescheduleConflictDTO> conflicts) {
        this.conflicts = conflicts != null ? conflicts : new ArrayList<>();
    }

    public String getResultMessage() {
        return resultMessage;
    }

    public void setResultMessage(String resultMessage) {
        this.resultMessage = resultMessage;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getProcessedBy() {
        return processedBy;
    }

    public void setProcessedBy(String processedBy) {
        this.processedBy = processedBy;
    }

    public LocalDateTime getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(LocalDateTime processedAt) {
        this.processedAt = processedAt;
    }
}
