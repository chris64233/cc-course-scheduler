package com.coursescheduler.model;

/**
 * 一条调课冲突明细，预检与确认失败共用同一结构。
 */
public class RescheduleIssue {
    private int itemIndex;
    private Long scheduleId;
    private RescheduleIssueType issueType;
    private RescheduleIssueSource source;

    /** 冲突对象（现有课程或方案内其他条目）的课程名。 */
    private String courseName;
    private String teacherName;
    private String classroom;
    private String timeSlot;

    /** 当来源为方案内其他条目时，该条目在方案中的下标。 */
    private Integer otherItemIndex;

    private String reason;

    public RescheduleIssue() {}

    public RescheduleIssue(int itemIndex, Long scheduleId, RescheduleIssueType issueType,
                           RescheduleIssueSource source, String courseName, String teacherName,
                           String classroom, String timeSlot, Integer otherItemIndex, String reason) {
        this.itemIndex = itemIndex;
        this.scheduleId = scheduleId;
        this.issueType = issueType;
        this.source = source;
        this.courseName = courseName;
        this.teacherName = teacherName;
        this.classroom = classroom;
        this.timeSlot = timeSlot;
        this.otherItemIndex = otherItemIndex;
        this.reason = reason;
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

    public RescheduleIssueType getIssueType() {
        return issueType;
    }

    public void setIssueType(RescheduleIssueType issueType) {
        this.issueType = issueType;
    }

    public RescheduleIssueSource getSource() {
        return source;
    }

    public void setSource(RescheduleIssueSource source) {
        this.source = source;
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

    public Integer getOtherItemIndex() {
        return otherItemIndex;
    }

    public void setOtherItemIndex(Integer otherItemIndex) {
        this.otherItemIndex = otherItemIndex;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
