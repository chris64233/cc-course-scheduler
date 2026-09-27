package com.coursescheduler.dto;

/**
 * 调课方案中一条课程调整的明细（调整前后内容）。
 */
public class ReschedulePlanItemResponse {
    private Long scheduleId;
    private String courseName;
    private String teacherName;
    private String originalClassroom;
    private String originalTimeSlot;
    private String newClassroom;
    private String newTimeSlot;

    public ReschedulePlanItemResponse() {}

    public ReschedulePlanItemResponse(Long scheduleId, String courseName, String teacherName,
                                      String originalClassroom, String originalTimeSlot,
                                      String newClassroom, String newTimeSlot) {
        this.scheduleId = scheduleId;
        this.courseName = courseName;
        this.teacherName = teacherName;
        this.originalClassroom = originalClassroom;
        this.originalTimeSlot = originalTimeSlot;
        this.newClassroom = newClassroom;
        this.newTimeSlot = newTimeSlot;
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

    public String getNewClassroom() {
        return newClassroom;
    }

    public void setNewClassroom(String newClassroom) {
        this.newClassroom = newClassroom;
    }

    public String getNewTimeSlot() {
        return newTimeSlot;
    }

    public void setNewTimeSlot(String newTimeSlot) {
        this.newTimeSlot = newTimeSlot;
    }
}
