package com.coursescheduler.model;

/**
 * 单条课程安排调课前后的审计快照。
 * 仅在方案确认成功后生成并永久保留在方案上。
 */
public class RescheduleChange {
    private int itemIndex;
    private Long scheduleId;
    private String courseName;
    private String teacherName;

    private String beforeClassroom;
    private String beforeTimeSlot;
    private String afterClassroom;
    private String afterTimeSlot;

    public RescheduleChange() {}

    public RescheduleChange(int itemIndex, Long scheduleId, String courseName, String teacherName,
                            String beforeClassroom, String beforeTimeSlot,
                            String afterClassroom, String afterTimeSlot) {
        this.itemIndex = itemIndex;
        this.scheduleId = scheduleId;
        this.courseName = courseName;
        this.teacherName = teacherName;
        this.beforeClassroom = beforeClassroom;
        this.beforeTimeSlot = beforeTimeSlot;
        this.afterClassroom = afterClassroom;
        this.afterTimeSlot = afterTimeSlot;
    }

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
