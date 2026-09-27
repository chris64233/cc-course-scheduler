package com.coursescheduler.dto;

/**
 * 调课方案条目视图：包含提交时冻结的原始排课内容与目标排课内容。
 */
public class RescheduleItemDTO {
    private int itemIndex;
    private Long scheduleId;
    private String courseName;
    private String originalTeacherName;
    private String originalClassroom;
    private String originalTimeSlot;
    private String targetClassroom;
    private String targetTimeSlot;

    public RescheduleItemDTO() {}

    public int getItemIndex() {
        return itemIndex;
    }

    public void setItemIndex(int itemIndex) {
        this.itemIndex = itemIndex;
    }

    public Long getScheduleId() {
        return scheduleId;
    }

    public void setScheduleId(Long scheduleId) {
        this.scheduleId = scheduleId;
    }

    public String getCourseName() {
        return courseName;
    }

    public void setCourseName(String courseName) {
        this.courseName = courseName;
    }

    public String getOriginalTeacherName() {
        return originalTeacherName;
    }

    public void setOriginalTeacherName(String originalTeacherName) {
        this.originalTeacherName = originalTeacherName;
    }

    public String getOriginalClassroom() {
        return originalClassroom;
    }

    public void setOriginalClassroom(String originalClassroom) {
        this.originalClassroom = originalClassroom;
    }

    public String getOriginalTimeSlot() {
        return originalTimeSlot;
    }

    public void setOriginalTimeSlot(String originalTimeSlot) {
        this.originalTimeSlot = originalTimeSlot;
    }

    public String getTargetClassroom() {
        return targetClassroom;
    }

    public void setTargetClassroom(String targetClassroom) {
        this.targetClassroom = targetClassroom;
    }

    public String getTargetTimeSlot() {
        return targetTimeSlot;
    }

    public void setTargetTimeSlot(String targetTimeSlot) {
        this.targetTimeSlot = targetTimeSlot;
    }
}
