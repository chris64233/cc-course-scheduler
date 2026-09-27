package com.coursescheduler.dto;

import com.coursescheduler.model.ReschedulePlanStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 调课方案详情：包含调整明细、最近一次预检/确认的冲突明细和处理结果。
 */
public class ReschedulePlanResponse {
    private Long id;
    private String bizKey;
    private ReschedulePlanStatus status;
    private List<ReschedulePlanItemResponse> items = new ArrayList<>();
    private int conflictCount;
    private List<RescheduleConflictDTO> conflicts = new ArrayList<>();
    private String resultMessage;
    private LocalDateTime createdAt;
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

    public ReschedulePlanStatus getStatus() {
        return status;
    }

    public void setStatus(ReschedulePlanStatus status) {
        this.status = status;
    }

    public List<ReschedulePlanItemResponse> getItems() {
        return items;
    }

    public void setItems(List<ReschedulePlanItemResponse> items) {
        this.items = items != null ? items : new ArrayList<>();
    }

    public int getConflictCount() {
        return conflictCount;
    }

    public void setConflictCount(int conflictCount) {
        this.conflictCount = conflictCount;
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
}
