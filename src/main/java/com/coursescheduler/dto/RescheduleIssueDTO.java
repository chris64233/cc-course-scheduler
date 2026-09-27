package com.coursescheduler.dto;

import com.coursescheduler.model.RescheduleIssue;
import com.coursescheduler.model.RescheduleIssueSource;
import com.coursescheduler.model.RescheduleIssueType;

/**
 * 调课冲突明细。
 */
public class RescheduleIssueDTO {
    private int itemIndex;
    private Long scheduleId;
    private RescheduleIssueType issueType;
    private RescheduleIssueSource source;
    private String courseName;
    private String teacherName;
    private String classroom;
    private String timeSlot;
    private Integer otherItemIndex;
    private String reason;

    public RescheduleIssueDTO() {}

    public static RescheduleIssueDTO from(RescheduleIssue issue) {
        RescheduleIssueDTO dto = new RescheduleIssueDTO();
        dto.itemIndex = issue.getItemIndex();
        dto.scheduleId = issue.getScheduleId();
        dto.issueType = issue.getIssueType();
        dto.source = issue.getSource();
        dto.courseName = issue.getCourseName();
        dto.teacherName = issue.getTeacherName();
        dto.classroom = issue.getClassroom();
        dto.timeSlot = issue.getTimeSlot();
        dto.otherItemIndex = issue.getOtherItemIndex();
        dto.reason = issue.getReason();
        return dto;
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
