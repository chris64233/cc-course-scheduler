package com.coursescheduler.model;

import com.coursescheduler.dto.RescheduleConflictDTO;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 多门课程原子调课方案。
 */
public class ReschedulePlan {
    private Long id;
    private String bizKey;
    private String fingerprint;
    private ReschedulePlanStatus status;
    private List<ReschedulePlanItem> items = new ArrayList<>();
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

    public String getFingerprint() {
        return fingerprint;
    }

    public void setFingerprint(String fingerprint) {
        this.fingerprint = fingerprint;
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
