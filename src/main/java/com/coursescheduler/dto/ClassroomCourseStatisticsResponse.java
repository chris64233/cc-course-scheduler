package com.coursescheduler.dto;

public class ClassroomCourseStatisticsResponse {
    private String classroom;
    private int courseCount;

    public ClassroomCourseStatisticsResponse() {}

    public ClassroomCourseStatisticsResponse(String classroom, int courseCount) {
        this.classroom = classroom;
        this.courseCount = courseCount;
    }

    public String getClassroom() {
        return classroom;
    }

    public void setClassroom(String classroom) {
        this.classroom = classroom;
    }

    public int getCourseCount() {
        return courseCount;
    }

    public void setCourseCount(int courseCount) {
        this.courseCount = courseCount;
    }
}
