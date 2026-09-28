package com.coursescheduler.dto;

/**
 * 教室停用事件登记请求。
 *
 * <p>{@code eventNo} 为外部事件号，用于幂等；{@code timeSlot} 为停用时间段，
 * 格式同课程时间段（如 {@code 周三 10:00-12:00}）。
 */
public class RoomOutageRequest {
    private String eventNo;
    private String classroom;
    private String timeSlot;
    private String reason;
    private String operator;

    public String getEventNo() {
        return eventNo;
    }

    public void setEventNo(String eventNo) {
        this.eventNo = eventNo;
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

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }
}
