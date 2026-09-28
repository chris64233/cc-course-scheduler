package com.coursescheduler.model;

import com.coursescheduler.dto.RescheduleConflictDTO;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 教室停用修复方案：覆盖一份修复任务中全部受影响课程的成组排课调整。
 *
 * <p>方案提交时冻结任务版本、受影响课程集合和每门课程的原始排课快照；
 * 确认时重新校验课程版本、教师时间和目标教室停用状态，全部通过才在一次
 * 事务中应用所有变更。任何一门课程缺少可行安排都不能确认部分方案。
 */
public class RepairPlan {
    private Long id;
    private Long taskId;
    private String eventNo;
    private String bizKey;
    private String fingerprint;
    private ReschedulePlanStatus status;
    private long taskVersion;
    private List<ReschedulePlanItem> items = new ArrayList<>();
    private Set<Long> targetScheduleIds = new LinkedHashSet<>();
    /** 方案提交时每条课程的版本号，确认时据此判断课程是否被较新的安排覆盖。 */
    private Map<Long, Long> submitRevisions = new LinkedHashMap<>();
    private List<RescheduleConflictDTO> conflicts = new ArrayList<>();
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

    public long getTaskVersion() {
        return taskVersion;
    }

    public void setTaskVersion(long taskVersion) {
        this.taskVersion = taskVersion;
    }

    public List<ReschedulePlanItem> getItems() {
        return items;
    }

    public void setItems(List<ReschedulePlanItem> items) {
        this.items = items != null ? items : new ArrayList<>();
    }

    public Set<Long> getTargetScheduleIds() {
        return targetScheduleIds;
    }

    public void setTargetScheduleIds(Set<Long> targetScheduleIds) {
        this.targetScheduleIds = targetScheduleIds != null ? targetScheduleIds : new LinkedHashSet<>();
    }

    public Map<Long, Long> getSubmitRevisions() {
        return submitRevisions;
    }

    public void setSubmitRevisions(Map<Long, Long> submitRevisions) {
        this.submitRevisions = submitRevisions != null ? submitRevisions : new LinkedHashMap<>();
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
