package com.coursescheduler.dto;

import java.util.List;

public class CourseScheduleBatchTimeSlotUpdateResponse {
    private int matchedCount;
    private int updatedCount;
    private String fromTimeSlot;
    private String toTimeSlot;
    private List<CourseScheduleResponse> updatedItems;

    public CourseScheduleBatchTimeSlotUpdateResponse() {}

    public CourseScheduleBatchTimeSlotUpdateResponse(int matchedCount, int updatedCount,
                                                      String fromTimeSlot, String toTimeSlot,
                                                      List<CourseScheduleResponse> updatedItems) {
        this.matchedCount = matchedCount;
        this.updatedCount = updatedCount;
        this.fromTimeSlot = fromTimeSlot;
        this.toTimeSlot = toTimeSlot;
        this.updatedItems = updatedItems;
    }

    public int getMatchedCount() {
        return matchedCount;
    }

    public void setMatchedCount(int matchedCount) {
        this.matchedCount = matchedCount;
    }

    public int getUpdatedCount() {
        return updatedCount;
    }

    public void setUpdatedCount(int updatedCount) {
        this.updatedCount = updatedCount;
    }

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

    public List<CourseScheduleResponse> getUpdatedItems() {
        return updatedItems;
    }

    public void setUpdatedItems(List<CourseScheduleResponse> updatedItems) {
        this.updatedItems = updatedItems;
    }
}
