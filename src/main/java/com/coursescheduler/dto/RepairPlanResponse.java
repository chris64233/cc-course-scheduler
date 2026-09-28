package com.coursescheduler.dto;

import com.coursescheduler.model.ReschedulePlanStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 停用修复方案详情：覆盖的课程调整、最近一次预检/确认的冲突明细和处理结果。
 */
public class RepairPlanResponse {
    private Long id;
    private Long taskId;
    private String eventNo;
    private String bizKey;
    private ReschedulePlanStatus status;
    private long taskVersion;
    private List<ReschedulePlanItemResponse> items = new ArrayList<>();
    private List<Long> affectedScheduleIds = new ArrayList<>();
    private List<RescheduleConflictDTO> conflicts = new ArrayList<>();
    private int conflictCount;
    private String resultMessage;
    private String operator;
    private LocalDateTime createdAt;
    private LocalDateTime processedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public String getEventNo() {
        return eventNo;
    }

    public void setEventNo(String eventNo) {
        this.eventNo = eventNo;
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

    public long getTaskVersion() {
        return taskVersion;
    }

    public void setTaskVersion(long taskVersion) {
        this.taskVersion = taskVersion;
    }

    public List<ReschedulePlanItemResponse> getItems() {
        return items;
    }

    public void setItems(List<ReschedulePlanItemResponse> items) {
        this.items = items != null ? items : new ArrayList<>();
    }

    public List<Long> getAffectedScheduleIds() {
        return affectedScheduleIds;
    }

    public void setAffectedScheduleIds(List<Long> affectedScheduleIds) {
        this.affectedScheduleIds = affectedScheduleIds != null ? affectedScheduleIds : new ArrayList<>();
    }

    public List<RescheduleConflictDTO> getConflicts() {
        return conflicts;
    }

    public void setConflicts(List<RescheduleConflictDTO> conflicts) {
        this.conflicts = conflicts != null ? conflicts : new ArrayList<>();
    }

    public int getConflictCount() {
        return conflictCount;
    }

    public void setConflictCount(int conflictCount) {
        this.conflictCount = conflictCount;
    }

    public String getResultMessage() {
        return resultMessage;
    }

    public void setResultMessage(String resultMessage) {
        this.resultMessage = resultMessage;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
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
