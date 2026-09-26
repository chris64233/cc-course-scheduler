package com.coursescheduler.dto;

public class TeacherCourseStatisticsResponse {
    private String teacherName;
    private int courseCount;

    public TeacherCourseStatisticsResponse() {}

    public TeacherCourseStatisticsResponse(String teacherName, int courseCount) {
        this.teacherName = teacherName;
        this.courseCount = courseCount;
    }

    public String getTeacherName() {
        return teacherName;
    }

    public void setTeacherName(String teacherName) {
        this.teacherName = teacherName;
    }

    public int getCourseCount() {
        return courseCount;
    }

    public void setCourseCount(int courseCount) {
        this.courseCount = courseCount;
    }
}
