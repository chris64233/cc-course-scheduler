package com.coursescheduler.dto;

import java.util.List;

/**
 * 教室停用事件创建/生效请求。
 *
 * <p>eventNo 为外部事件号：相同事件号相同内容重复提交幂等，同号异内容冲突。
 * timeSlots 为停用的周期性时间段列表，格式同课程时间段（如 周三 10:00-12:00）。
 */
public class RoomOutageCreateRequest {
    private String eventNo;
    private String classroom;
    private String reason;
    private List<String> timeSlots;
    /** 处理人员（可选） */
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

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public List<String> getTimeSlots() {
        return timeSlots;
    }

    public void setTimeSlots(List<String> timeSlots) {
        this.timeSlots = timeSlots;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }
}
