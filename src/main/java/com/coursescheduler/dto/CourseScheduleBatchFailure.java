package com.coursescheduler.dto;

public class CourseScheduleBatchFailure {
    private int index;
    private String courseName;
    private String errorMessage;

    public CourseScheduleBatchFailure() {}

    public CourseScheduleBatchFailure(int index, String courseName, String errorMessage) {
        this.index = index;
        this.courseName = courseName;
        this.errorMessage = errorMessage;
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public String getCourseName() {
        return courseName;
    }

    public void setCourseName(String courseName) {
        this.courseName = courseName;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
