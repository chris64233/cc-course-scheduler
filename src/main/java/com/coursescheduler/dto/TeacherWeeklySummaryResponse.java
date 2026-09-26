package com.coursescheduler.dto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TeacherWeeklySummaryResponse {
    private String teacherName;
    private int totalCourses;
    private int occupiedDays;
    private String busiestDay;
    private List<String> courseNames;

    public TeacherWeeklySummaryResponse() {
        this.courseNames = Collections.emptyList();
    }

    public TeacherWeeklySummaryResponse(String teacherName, int totalCourses, int occupiedDays,
                                        String busiestDay, List<String> courseNames) {
        this.teacherName = teacherName;
        this.totalCourses = totalCourses;
        this.occupiedDays = occupiedDays;
        this.busiestDay = busiestDay;
        this.courseNames = immutableCopy(courseNames);
    }

    public String getTeacherName() {
        return teacherName;
    }

    public void setTeacherName(String teacherName) {
        this.teacherName = teacherName;
    }

    public int getTotalCourses() {
        return totalCourses;
    }

    public void setTotalCourses(int totalCourses) {
        this.totalCourses = totalCourses;
    }

    public int getOccupiedDays() {
        return occupiedDays;
    }

    public void setOccupiedDays(int occupiedDays) {
        this.occupiedDays = occupiedDays;
    }

    public String getBusiestDay() {
        return busiestDay;
    }

    public void setBusiestDay(String busiestDay) {
        this.busiestDay = busiestDay;
    }

    public List<String> getCourseNames() {
        return courseNames;
    }

    public void setCourseNames(List<String> courseNames) {
        this.courseNames = immutableCopy(courseNames);
    }

    private static List<String> immutableCopy(List<String> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<>(source));
    }
}
