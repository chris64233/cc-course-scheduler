package com.coursescheduler.dto;

import java.util.List;

public class CourseScheduleBatchDeleteResponse {
    private int totalCount;
    private int successCount;
    private int failureCount;
    private List<CourseScheduleResponse> successItems;
    private List<CourseScheduleBatchDeleteFailure> failureItems;

    public CourseScheduleBatchDeleteResponse() {}

    public CourseScheduleBatchDeleteResponse(int totalCount, int successCount, int failureCount,
                                             List<CourseScheduleResponse> successItems,
                                             List<CourseScheduleBatchDeleteFailure> failureItems) {
        this.totalCount = totalCount;
        this.successCount = successCount;
        this.failureCount = failureCount;
        this.successItems = successItems;
        this.failureItems = failureItems;
    }

    public int getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(int totalCount) {
        this.totalCount = totalCount;
    }

    public int getSuccessCount() {
        return successCount;
    }

    public void setSuccessCount(int successCount) {
        this.successCount = successCount;
    }

    public int getFailureCount() {
        return failureCount;
    }

    public void setFailureCount(int failureCount) {
        this.failureCount = failureCount;
    }

    public List<CourseScheduleResponse> getSuccessItems() {
        return successItems;
    }

    public void setSuccessItems(List<CourseScheduleResponse> successItems) {
        this.successItems = successItems;
    }

    public List<CourseScheduleBatchDeleteFailure> getFailureItems() {
        return failureItems;
    }

    public void setFailureItems(List<CourseScheduleBatchDeleteFailure> failureItems) {
        this.failureItems = failureItems;
    }
}
