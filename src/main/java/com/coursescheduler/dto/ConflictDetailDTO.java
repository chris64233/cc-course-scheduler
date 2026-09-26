package com.coursescheduler.dto;

public class ConflictDetailDTO {

    public enum ConflictType {
        TEACHER,
        CLASSROOM
    }

    public enum SourceType {
        EXISTING_COURSE,
        PENDING_ITEM
    }

    private ConflictType conflictType;
    private SourceType sourceType;
    private Long courseId;
    private Integer pendingIndex;
    private String courseName;
    private String teacherName;
    private String classroom;
    private String timeSlot;
    private String reason;

    public ConflictDetailDTO() {}

    public ConflictDetailDTO(ConflictType conflictType, Long courseId, String courseName,
                         String teacherName, String classroom, String timeSlot, String reason) {
        this.conflictType = conflictType;
        this.sourceType = SourceType.EXISTING_COURSE;
        this.courseId = courseId;
        this.pendingIndex = null;
        this.courseName = courseName;
        this.teacherName = teacherName;
        this.classroom = classroom;
        this.timeSlot = timeSlot;
        this.reason = reason;
    }

    public ConflictDetailDTO(ConflictType conflictType, Integer pendingIndex, String courseName,
                         String teacherName, String classroom, String timeSlot, String reason) {
        this.conflictType = conflictType;
        this.sourceType = SourceType.PENDING_ITEM;
        this.courseId = null;
        this.pendingIndex = pendingIndex;
        this.courseName = courseName;
        this.teacherName = teacherName;
        this.classroom = classroom;
        this.timeSlot = timeSlot;
        this.reason = reason;
    }

    public ConflictType getConflictType() {
        return conflictType;
    }

    public void setConflictType(ConflictType conflictType) {
        this.conflictType = conflictType;
    }

    public SourceType getSourceType() {
        return sourceType;
    }

    public void setSourceType(SourceType sourceType) {
        this.sourceType = sourceType;
    }

    public Long getCourseId() {
        return courseId;
    }

    public void setCourseId(Long courseId) {
        this.courseId = courseId;
    }

    public Integer getPendingIndex() {
        return pendingIndex;
    }

    public void setPendingIndex(Integer pendingIndex) {
        this.pendingIndex = pendingIndex;
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

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
