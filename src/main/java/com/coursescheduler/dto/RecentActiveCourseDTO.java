package com.coursescheduler.dto;

import java.time.LocalDateTime;

public class RecentActiveCourseDTO {
    private String courseName;
    private LocalDateTime lastChangeTime;
    private int successCount;
    private int failureCount;
    private int totalCount;

    public RecentActiveCourseDTO() {}

    public RecentActiveCourseDTO(String courseName, LocalDateTime lastChangeTime,
                                int successCount, int failureCount, int totalCount) {
        this.courseName = courseName;
        this.lastChangeTime = lastChangeTime;
        this.successCount = successCount;
        this.failureCount = failureCount;
        this.totalCount = totalCount;
    }

    public String getCourseName() {
        return courseName;
    }

    public void setCourseName(String courseName) {
        this.courseName = courseName;
    }

    public LocalDateTime getLastChangeTime() {
        return lastChangeTime;
    }

    public void setLastChangeTime(LocalDateTime lastChangeTime) {
        this.lastChangeTime = lastChangeTime;
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
