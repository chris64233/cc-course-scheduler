package com.coursescheduler.dto;

/**
 * 调课方案中的一条调整请求：指定现有课程安排的新星期/时段（timeSlot 内含星期）和新教室。
 */
public class ReschedulePlanItemRequest {
    private Long scheduleId;
    private String newClassroom;
    private String newTimeSlot;

    public ReschedulePlanItemRequest() {}

    public ReschedulePlanItemRequest(Long scheduleId, String newClassroom, String newTimeSlot) {
        this.scheduleId = scheduleId;
        this.newClassroom = newClassroom;
        this.newTimeSlot = newTimeSlot;
    }

    public Long getScheduleId() {
        return scheduleId;
    }

    public void setScheduleId(Long scheduleId) {
        this.scheduleId = scheduleId;
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
