package com.coursescheduler.dto;

import javax.validation.constraints.NotBlank;

public class CourseScheduleBatchTimeSlotUpdateRequest {

    @NotBlank(message = "原时间段不能为空")
    private String fromTimeSlot;

    @NotBlank(message = "目标时间段不能为空")
    private String toTimeSlot;

    public String getFromTimeSlot() {
        return fromTimeSlot;
    }

    public void setFromTimeSlot(String fromTimeSlot) {
        this.fromTimeSlot = fromTimeSlot;
    }

    public String getToTimeSlot() {
        return toTimeSlot;
    }

    public void setToTimeSlot(String toTimeSlot) {
        this.toTimeSlot = toTimeSlot;
    }
}
