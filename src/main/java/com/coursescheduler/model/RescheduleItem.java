package com.coursescheduler.model;

/**
 * 调课方案中的一条安排：引用一条现有课程安排及其提交时看到的原始排课内容，
 * 并指定新的星期/时段和教室。
 *
 * <p>原始快照（originalTeacherName/originalClassroom/originalTimeSlot）在提交时冻结，
 * 用于确认时判断该课程是否已被别人修改或删除。
 */
public class RescheduleItem {
    private int itemIndex;
    private Long scheduleId;

    private String originalCourseName;
    private String originalTeacherName;
    private String originalClassroom;
    private String originalTimeSlot;

    private String targetClassroom;
    private String targetTimeSlot;

    public RescheduleItem() {}

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

    public String getOriginalCourseName() {
        return originalCourseName;
    }

    public void setOriginalCourseName(String originalCourseName) {
        this.originalCourseName = originalCourseName;
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
