package com.coursescheduler.dto;

import com.coursescheduler.model.RoomOutageStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 教室停用事件详情。
 */
public class RoomOutageResponse {
    private Long id;
    private String eventNo;
    private String classroom;
    private String reason;
    private List<String> timeSlots = new ArrayList<>();
    private RoomOutageStatus status;
    private long version;
    private String createdBy;
    private LocalDateTime createdAt;
    private String updatedBy;
    private LocalDateTime updatedAt;
    private String cancelledBy;
    private LocalDateTime cancelledAt;
    private Long repairTaskId;
    private List<OutageRangeHistoryDTO> rangeHistory = new ArrayList<>();

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

    public Long getRepairTaskId() {
        return repairTaskId;
    }

    public void setRepairTaskId(Long repairTaskId) {
        this.repairTaskId = repairTaskId;
    }

    public List<OutageRangeHistoryDTO> getRangeHistory() {
        return rangeHistory;
    }

    public void setRangeHistory(List<OutageRangeHistoryDTO> rangeHistory) {
        this.rangeHistory = rangeHistory != null ? rangeHistory : new ArrayList<>();
    }
}
