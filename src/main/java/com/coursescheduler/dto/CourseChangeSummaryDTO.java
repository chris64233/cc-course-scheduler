package com.coursescheduler.dto;

public class CourseChangeSummaryDTO {
    private String courseName;
    private int changeCount;

    public CourseChangeSummaryDTO() {}

    public CourseChangeSummaryDTO(String courseName, int changeCount) {
        this.courseName = courseName;
        this.changeCount = changeCount;
    }

    public String getCourseName() {
        return courseName;
    }

    public void setCourseName(String courseName) {
        this.courseName = courseName;
    }

    public int getChangeCount() {
        return changeCount;
    }

    public void setChangeCount(int changeCount) {
        this.changeCount = changeCount;
    }
}
