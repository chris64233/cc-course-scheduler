package com.coursescheduler.dto;

import com.coursescheduler.model.OperationType;

import java.time.LocalDateTime;

public class AuditLogResponse {
    private Long id;
    private OperationType operationType;
    private LocalDateTime timestamp;
    private Long courseId;
    private String courseName;
    private String teacherName;
    private String classroom;
    private String timeSlot;
    private boolean success;
    private String errorMessage;
    private String previousClassroom;
    private String previousTimeSlot;
    private String operator;
    private String referenceNo;

    public AuditLogResponse() {}

    public AuditLogResponse(Long id, OperationType operationType, LocalDateTime timestamp,
                            Long courseId, String courseName, String teacherName,
                            String classroom, String timeSlot, boolean success, String errorMessage) {
        this.id = id;
        this.operationType = operationType;
        this.timestamp = timestamp;
        this.courseId = courseId;
        this.courseName = courseName;
        this.teacherName = teacherName;
        this.classroom = classroom;
        this.timeSlot = timeSlot;
        this.success = success;
        this.errorMessage = errorMessage;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public OperationType getOperationType() {
        return operationType;
    }

    public void setOperationType(OperationType operationType) {
        this.operationType = operationType;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public Long getCourseId() {
        return courseId;
    }

    public void setCourseId(Long courseId) {
        this.courseId = courseId;
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

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getPreviousClassroom() {
        return previousClassroom;
    }

    public void setPreviousClassroom(String previousClassroom) {
        this.previousClassroom = previousClassroom;
    }

    public String getPreviousTimeSlot() {
        return previousTimeSlot;
    }

    public void setPreviousTimeSlot(String previousTimeSlot) {
        this.previousTimeSlot = previousTimeSlot;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public String getReferenceNo() {
        return referenceNo;
    }

    public void setReferenceNo(String referenceNo) {
        this.referenceNo = referenceNo;
    }
}
