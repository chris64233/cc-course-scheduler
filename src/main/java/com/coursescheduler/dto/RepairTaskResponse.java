package com.coursescheduler.dto;

import com.coursescheduler.model.RepairTaskStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 教室停用修复任务详情：冻结的受影响课程、已修复课程、关联修复方案。
 */
public class RepairTaskResponse {
    private Long id;
    private Long outageId;
    private String eventNo;
    private String classroom;
    private String timeSlot;
    private String reason;
    private RepairTaskStatus status;
    private long version;
    private List<AffectedCourseDTO> affectedCourses = new ArrayList<>();
    private int affectedCount;
    private int resolvedCount;
    private List<Long> planIds = new ArrayList<>();
    private LocalDateTime createdAt;
    private LocalDateTime processedAt;
    private LocalDateTime cancelledAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getOutageId() {
        return outageId;
    }

    public void setOutageId(Long outageId) {
        this.outageId = outageId;
    }

    public String getEventNo() {
        return eventNo;
    }

    public void setEventNo(String eventNo) {
        this.eventNo = eventNo;
    }

    public String getClassroom() {
        return classroom;
    }

    public void setClassroom(String classroom) {
        this.classroom = classroom;
    }

    public String getTimeSlot() {
        return timeSlot;
    }

    public void setTimeSlot(String timeSlot) {
        this.timeSlot = timeSlot;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public RepairTaskStatus getStatus() {
        return status;
    }

    public void setStatus(RepairTaskStatus status) {
        this.status = status;
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }

    public List<AffectedCourseDTO> getAffectedCourses() {
        return affectedCourses;
    }

    public void setAffectedCourses(List<AffectedCourseDTO> affectedCourses) {
        this.affectedCourses = affectedCourses != null ? affectedCourses : new ArrayList<>();
    }

    public int getAffectedCount() {
        return affectedCount;
    }

    public void setAffectedCount(int affectedCount) {
        this.affectedCount = affectedCount;
    }

    public int getResolvedCount() {
        return resolvedCount;
    }

    public void setResolvedCount(int resolvedCount) {
        this.resolvedCount = resolvedCount;
    }

    public List<Long> getPlanIds() {
        return planIds;
    }

    public void setPlanIds(List<Long> planIds) {
        this.planIds = planIds != null ? planIds : new ArrayList<>();
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

    public LocalDateTime getCancelledAt() {
        return cancelledAt;
    }

    public void setCancelledAt(LocalDateTime cancelledAt) {
        this.cancelledAt = cancelledAt;
    }
}
