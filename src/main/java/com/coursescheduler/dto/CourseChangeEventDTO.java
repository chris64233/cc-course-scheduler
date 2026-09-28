package com.coursescheduler.dto;

import com.coursescheduler.model.OperationType;

import java.time.LocalDateTime;

/**
 * 课程变更链上的一条事件（来源于审计日志）。
 */
public class CourseChangeEventDTO {
    private OperationType operationType;
    private LocalDateTime timestamp;
    private boolean success;
    private String previousClassroom;
    private String previousTimeSlot;
    private String classroom;
    private String timeSlot;
    /** 处理人员 */
    private String operator;
    /** 关联业务号：调课 bizKey、修复 bizKey 或停用外部事件号 */
    private String refNo;
    private String errorMessage;

    public CourseChangeEventDTO() {}

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

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
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

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public String getRefNo() {
        return refNo;
    }

    public void setRefNo(String refNo) {
        this.refNo = refNo;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
