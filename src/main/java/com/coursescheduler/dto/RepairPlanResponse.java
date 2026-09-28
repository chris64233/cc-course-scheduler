package com.coursescheduler.dto;

import com.coursescheduler.model.ReschedulePlanStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 停用成组修复方案详情：调整明细、冲突明细、覆盖情况与处理结果、处理人员。
 */
public class RepairPlanResponse {
    private Long id;
    private String bizKey;
    private Long taskId;
    private String eventNo;
    private ReschedulePlanStatus status;
    private List<ReschedulePlanItemResponse> items = new ArrayList<>();
    private int conflictCount;
    private List<RescheduleConflictDTO> conflicts = new ArrayList<>();
    /** 提交时任务中待修复课程总数（本方案应覆盖的课程数） */
    private int expectedCount;
    /** 本方案已覆盖的课程数 */
    private int coveredCount;
    /** 提交时未被本方案覆盖的课程 ID */
    private List<Long> missingScheduleIds = new ArrayList<>();
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

    public int getExpectedCount() {
        return expectedCount;
    }

    public void setExpectedCount(int expectedCount) {
        this.expectedCount = expectedCount;
    }

    public int getCoveredCount() {
        return coveredCount;
    }

    public void setCoveredCount(int coveredCount) {
        this.coveredCount = coveredCount;
    }

    public List<Long> getMissingScheduleIds() {
        return missingScheduleIds;
    }

    public void setMissingScheduleIds(List<Long> missingScheduleIds) {
        this.missingScheduleIds = missingScheduleIds != null ? missingScheduleIds : new ArrayList<>();
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
