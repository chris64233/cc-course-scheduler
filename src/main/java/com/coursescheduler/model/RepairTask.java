package com.coursescheduler.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 教室停用生效时冻结的一份修复任务。
 *
 * <p>任务内固定包含全部与停用时段冲突的课程安排（冻结时的内容快照），
 * 修复方案必须覆盖全部尚未修复的课程；停用范围调整后新增冲突的课程会追加进同一任务。
 */
public class RepairTask {
    private Long id;
    private Long outageId;
    private String eventNo;
    private RepairTaskStatus status;
    private List<AffectedCourse> affectedCourses = new ArrayList<>();
    private LocalDateTime createdAt;
    private LocalDateTime resolvedAt;
    private Long confirmedRepairPlanId;

    public RepairTask() {}

    public RepairTask(Long id, Long outageId, String eventNo, RepairTaskStatus status,
                      List<AffectedCourse> affectedCourses, LocalDateTime createdAt) {
        this.id = id;
        this.outageId = outageId;
        this.eventNo = eventNo;
        this.status = status;
        this.affectedCourses = affectedCourses != null ? affectedCourses : new ArrayList<>();
        this.createdAt = createdAt;
    }

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

    public List<AffectedCourse> getAffectedCourses() {
        return affectedCourses;
    }

    public void setAffectedCourses(List<AffectedCourse> affectedCourses) {
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
