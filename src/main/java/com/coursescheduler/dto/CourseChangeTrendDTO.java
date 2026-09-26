package com.coursescheduler.dto;

import java.time.LocalDate;

public class CourseChangeTrendDTO {
    private LocalDate date;
    private int successCount;
    private int failureCount;
    private int totalCount;

    public CourseChangeTrendDTO() {}

    public CourseChangeTrendDTO(LocalDate date, int successCount, int failureCount, int totalCount) {
        this.date = date;
        this.successCount = successCount;
        this.failureCount = failureCount;
        this.totalCount = totalCount;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
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

    public int getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(int totalCount) {
        this.totalCount = totalCount;
    }
}
