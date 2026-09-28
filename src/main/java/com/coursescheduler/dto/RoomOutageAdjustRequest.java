package com.coursescheduler.dto;

import java.util.List;

/**
 * 教室停用范围/原因调整请求。
 */
public class RoomOutageAdjustRequest {
    private List<String> timeSlots;
    private String reason;
    private String operator;
    /** 乐观版本：传入时必须与当前停用事件版本一致 */
    private Long expectedVersion;

    public List<String> getTimeSlots() {
        return timeSlots;
    }

    public void setTimeSlots(List<String> timeSlots) {
        this.timeSlots = timeSlots;
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

    public Long getExpectedVersion() {
        return expectedVersion;
    }

    public void setExpectedVersion(Long expectedVersion) {
        this.expectedVersion = expectedVersion;
    }
}
