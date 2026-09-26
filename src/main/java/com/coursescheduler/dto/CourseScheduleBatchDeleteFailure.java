package com.coursescheduler.dto;

public class CourseScheduleBatchDeleteFailure {
    private Long id;
    private String errorMessage;

    public CourseScheduleBatchDeleteFailure() {}

    public CourseScheduleBatchDeleteFailure(Long id, String errorMessage) {
        this.id = id;
        this.errorMessage = errorMessage;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
