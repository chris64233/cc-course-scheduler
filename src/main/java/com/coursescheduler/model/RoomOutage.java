package com.coursescheduler.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 教室临时停用事件。
 *
 * <p>一个事件指定教室、停用时间段（格式同课程时间段，如 {@code 周三 10:00-12:00}）、
 * 原因和外部事件号 {@code eventNo}。外部事件号用于幂等：相同事件号相同内容重复登记返回
 * 既有事件；相同事件号但教室/时间段/原因不同则视为冲突。
 *
 * <p>事件每次生效或调整停用范围时，都会冻结出一份修复任务（{@link RepairTask}）。
 */
public class RoomOutage {
    private Long id;
    private String eventNo;
    private String fingerprint;
    private String classroom;
    private String timeSlot;
    private String reason;
    private OutageStatus status;
    private String operator;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime cancelledAt;
    private List<Long> taskIds = new ArrayList<>();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEventNo() {
        return eventNo;
    }

    public void setEventNo(String eventNo) {
        this.eventNo = eventNo;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public void setFingerprint(String fingerprint) {
        this.fingerprint = fingerprint;
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

    public OutageStatus getStatus() {
        return status;
    }

    public void setStatus(OutageStatus status) {
        this.status = status;
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

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public LocalDateTime getCancelledAt() {
        return cancelledAt;
    }

    public void setCancelledAt(LocalDateTime cancelledAt) {
        this.cancelledAt = cancelledAt;
    }

    public List<Long> getTaskIds() {
        return taskIds;
    }

    public void setTaskIds(List<Long> taskIds) {
        this.taskIds = taskIds != null ? taskIds : new ArrayList<>();
    }
}
