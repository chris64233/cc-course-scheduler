package com.coursescheduler.dto;

/**
 * 教室停用范围调整请求。停用时间段必填；教室缺省沿用原教室；原因缺省沿用原原因。
 */
public class RoomOutageAdjustRequest {
    private String classroom;
    private String timeSlot;
    private String reason;
    private String operator;

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

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }
}
