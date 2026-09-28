package com.coursescheduler.dto;

import com.coursescheduler.model.RepairTaskStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 停用修复任务详情：冻结的受影响课程范围及其修复状态。
 */
public class RepairTaskResponse {
    private Long id;
    private Long outageId;
    private String eventNo;
    private RepairTaskStatus status;
    private int affectedCount;
    private int resolvedCount;
    private List<AffectedCourseDTO> affectedCourses = new ArrayList<>();
    private LocalDateTime createdAt;
    private LocalDateTime resolvedAt;
    private Long confirmedRepairPlanId;

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

    public RepairTaskStatus getStatus() {
        return status;
    }

    public void setStatus(RepairTaskStatus status) {
        this.status = status;
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

    public List<AffectedCourseDTO> getAffectedCourses() {
        return affectedCourses;
    }

    public void setAffectedCourses(List<AffectedCourseDTO> affectedCourses) {
        this.affectedCourses = affectedCourses != null ? affectedCourses : new ArrayList<>();
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(LocalDateTime resolvedAt) {
        this.resolvedAt = resolvedAt;
    }

    public Long getConfirmedRepairPlanId() {
        return confirmedRepairPlanId;
    }

    public void setConfirmedRepairPlanId(Long confirmedRepairPlanId) {
        this.confirmedRepairPlanId = confirmedRepairPlanId;
    }
}
