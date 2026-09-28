package com.coursescheduler.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 教室临时停用事件。
 *
 * <p>指定教室、停用的周期性时间段（格式同课程时间段，如 周三 10:00-12:00）、
 * 原因与外部事件号。生效后落入停用时段的课程安排被冻结到一份修复任务中。
 */
public class RoomOutage {
    private Long id;
    /** 外部事件号：相同事件号相同内容幂等，同号异内容冲突 */
    private String eventNo;
    private String classroom;
    private String reason;
    /** 规范化后的停用时间段，可包含多个 */
    private List<String> timeSlots = new ArrayList<>();
    private RoomOutageStatus status;
    /** 停用事件自身版本：创建为 1，每次范围/原因调整递增 */
    private long version = 1;
    private String createFingerprint;
    private String createdBy;
    private LocalDateTime createdAt;
    private String updatedBy;
    private LocalDateTime updatedAt;
    private String cancelledBy;
    private LocalDateTime cancelledAt;
    /** 停用范围变更历史（创建时含一条初始记录） */
    private List<OutageRangeHistory> rangeHistory = new ArrayList<>();
    private Long repairTaskId;

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

    public String getClassroom() {
        return classroom;
    }

    public void setClassroom(String classroom) {
        this.classroom = classroom;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public List<String> getTimeSlots() {
        return timeSlots;
    }

    public void setTimeSlots(List<String> timeSlots) {
        this.timeSlots = timeSlots != null ? timeSlots : new ArrayList<>();
    }

    public RoomOutageStatus getStatus() {
        return status;
    }

    public void setStatus(RoomOutageStatus status) {
        this.status = status;
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }

    public String getCreateFingerprint() {
        return createFingerprint;
    }

    public void setCreateFingerprint(String createFingerprint) {
        this.createFingerprint = createFingerprint;
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

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getCancelledBy() {
        return cancelledBy;
    }

    public void setCancelledBy(String cancelledBy) {
        this.cancelledBy = cancelledBy;
    }

    public LocalDateTime getCancelledAt() {
        return cancelledAt;
    }

    public void setCancelledAt(LocalDateTime cancelledAt) {
        this.cancelledAt = cancelledAt;
    }

    public List<OutageRangeHistory> getRangeHistory() {
        return rangeHistory;
    }

    public void setRangeHistory(List<OutageRangeHistory> rangeHistory) {
        this.rangeHistory = rangeHistory != null ? rangeHistory : new ArrayList<>();
    }

    public Long getRepairTaskId() {
        return repairTaskId;
    }

    public void setRepairTaskId(Long repairTaskId) {
        this.repairTaskId = repairTaskId;
    }
}
