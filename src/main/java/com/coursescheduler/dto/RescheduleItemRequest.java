package com.coursescheduler.dto;

/**
 * 调课方案中的一条请求：引用现有课程安排 ID，并指定新的教室与时段。
 * 新的星期包含在 timeSlot 中（如：周三 10:00-12:00）。
 */
public class RescheduleItemRequest {
    private Long scheduleId;
    private String classroom;
    private String timeSlot;

    public RescheduleItemRequest() {}

    public RescheduleItemRequest(Long scheduleId, String classroom, String timeSlot) {
        this.scheduleId = scheduleId;
        this.classroom = classroom;
        this.timeSlot = timeSlot;
    }

    public Long getScheduleId() {
        return scheduleId;
    }

    public void setScheduleId(Long scheduleId) {
        this.scheduleId = scheduleId;
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
