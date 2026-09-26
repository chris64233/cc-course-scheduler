package com.coursescheduler.dto;

public class CourseScheduleStatisticsResponse {
    private long totalCourses;
    private long teacherCount;
    private long classroomCount;

    public CourseScheduleStatisticsResponse() {}

    public CourseScheduleStatisticsResponse(long totalCourses, long teacherCount, long classroomCount) {
        this.totalCourses = totalCourses;
        this.teacherCount = teacherCount;
        this.classroomCount = classroomCount;
    }

    public long getTotalCourses() {
        return totalCourses;
    }

    public void setTotalCourses(long totalCourses) {
        this.totalCourses = totalCourses;
    }

    public long getTeacherCount() {
        return teacherCount;
    }

    public void setTeacherCount(long teacherCount) {
        this.teacherCount = teacherCount;
    }

    public long getClassroomCount() {
        return classroomCount;
    }

    public void setClassroomCount(long classroomCount) {
        this.classroomCount = classroomCount;
    }
}
