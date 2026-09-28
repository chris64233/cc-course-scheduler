package com.coursescheduler.dto;

/**
 * 停用影响范围内的一门受影响课程（冻结时的内容快照与修复状态）。
 */
public class AffectedCourseDTO {
    private Long scheduleId;
    private String courseName;
    private String teacherName;
    private String classroom;
    private String timeSlot;
    private Long version;
    private boolean resolved;
    private String resolvedReason;

    public AffectedCourseDTO() {}

    public AffectedCourseDTO(Long scheduleId, String courseName, String teacherName,
                             String classroom, String timeSlot, Long version,
                             boolean resolved, String resolvedReason) {
        this.scheduleId = scheduleId;
        this.courseName = courseName;
        this.teacherName = teacherName;
        this.classroom = classroom;
        this.timeSlot = timeSlot;
        this.version = version;
        this.resolved = resolved;
        this.resolvedReason = resolvedReason;
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

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public boolean isResolved() {
        return resolved;
    }

    public void setResolved(boolean resolved) {
        this.resolved = resolved;
    }

    public String getResolvedReason() {
        return resolvedReason;
    }

    public void setResolvedReason(String resolvedReason) {
        this.resolvedReason = resolvedReason;
    }
}
