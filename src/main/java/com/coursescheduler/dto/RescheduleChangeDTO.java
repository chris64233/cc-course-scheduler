package com.coursescheduler.dto;

/**
 * 单条课程调课前后的变更明细。
 */
public class RescheduleChangeDTO {
    private int itemIndex;
    private Long scheduleId;
    private String courseName;
    private String teacherName;
    private String beforeClassroom;
    private String beforeTimeSlot;
    private String afterClassroom;
    private String afterTimeSlot;

    public RescheduleChangeDTO() {}

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

    public String getTeacherName() {
        return teacherName;
    }

    public void setTeacherName(String teacherName) {
        this.teacherName = teacherName;
    }

    public String getBeforeClassroom() {
        return beforeClassroom;
    }

    public void setBeforeClassroom(String beforeClassroom) {
        this.beforeClassroom = beforeClassroom;
    }

    public String getBeforeTimeSlot() {
        return beforeTimeSlot;
    }

    public void setBeforeTimeSlot(String beforeTimeSlot) {
        this.beforeTimeSlot = beforeTimeSlot;
    }

    public String getAfterClassroom() {
        return afterClassroom;
    }

    public void setAfterClassroom(String afterClassroom) {
        this.afterClassroom = afterClassroom;
    }

    public String getAfterTimeSlot() {
        return afterTimeSlot;
    }

    public void setAfterTimeSlot(String afterTimeSlot) {
        this.afterTimeSlot = afterTimeSlot;
    }
}
