package com.coursescheduler.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 教室停用修复任务：一次停用生效（或停用范围调整）后冻结出的受影响课程集合。
 *
 * <p>任务在冻结时保存受影响课程的快照；停用范围调整会重新冻结当前仍受影响的课程，
 * 并递增 {@code version}，此前提交的修复方案确认时会因版本不一致而失败。
 * 全部受影响课程都被已确认的修复方案处理后，任务标记为 {@link RepairTaskStatus#REPAIRED}。
 */
public class RepairTask {
    private Long id;
    private Long outageId;
    private String eventNo;
    private String classroom;
    private String timeSlot;
    private String reason;
    private RepairTaskStatus status;
    /** 任务版本：每次停用范围调整、重新冻结受影响课程时递增。 */
    private long version;
    private List<AffectedCourse> affectedCourses = new ArrayList<>();
    /** 已被已确认修复方案处理过的课程安排 ID。 */
    private List<Long> resolvedScheduleIds = new ArrayList<>();
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

    public List<AffectedCourse> getAffectedCourses() {
        return affectedCourses;
    }

    public void setAffectedCourses(List<AffectedCourse> affectedCourses) {
        this.affectedCourses = affectedCourses != null ? affectedCourses : new ArrayList<>();
    }

    public List<Long> getResolvedScheduleIds() {
        return resolvedScheduleIds;
    }

    public void setResolvedScheduleIds(List<Long> resolvedScheduleIds) {
        this.resolvedScheduleIds = resolvedScheduleIds != null ? resolvedScheduleIds : new ArrayList<>();
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
