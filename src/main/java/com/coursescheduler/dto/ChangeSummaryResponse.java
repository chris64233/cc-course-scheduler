package com.coursescheduler.dto;

import com.coursescheduler.model.OperationType;

import java.time.LocalDateTime;

public class ChangeSummaryResponse {
    private OperationType operationType;
    private LocalDateTime timestamp;
    private String courseName;
    private String teacherName;
    private String classroom;
    private String timeSlot;

    public ChangeSummaryResponse() {}

    public ChangeSummaryResponse(OperationType operationType, LocalDateTime timestamp,
                                 String courseName, String teacherName,
                                 String classroom, String timeSlot) {
        this.operationType = operationType;
        this.timestamp = timestamp;
        this.courseName = courseName;
        this.teacherName = teacherName;
        this.classroom = classroom;
        this.timeSlot = timeSlot;
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
}
