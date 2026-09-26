package com.coursescheduler.dto;

import java.time.LocalDate;

public class CourseChangeAbnormalDaysDTO {
    private LocalDate date;
    private int successCount;
    private int failureCount;
    private int totalCount;
    private double failureRate;

    public CourseChangeAbnormalDaysDTO() {}

    public CourseChangeAbnormalDaysDTO(LocalDate date, int successCount, int failureCount, int totalCount, double failureRate) {
        this.date = date;
        this.successCount = successCount;
        this.failureCount = failureCount;
        this.totalCount = totalCount;
        this.failureRate = failureRate;
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

    public double getFailureRate() {
        return failureRate;
    }

    public void setFailureRate(double failureRate) {
        this.failureRate = failureRate;
    }
}
