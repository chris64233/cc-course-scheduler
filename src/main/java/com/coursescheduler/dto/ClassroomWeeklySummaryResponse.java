package com.coursescheduler.dto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ClassroomWeeklySummaryResponse {
    private String classroom;
    private int totalCourses;
    private int occupiedDays;
    private String busiestDay;
    private List<String> teacherNames;

    public ClassroomWeeklySummaryResponse() {
        this.teacherNames = Collections.emptyList();
    }

    public ClassroomWeeklySummaryResponse(String classroom, int totalCourses, int occupiedDays,
                                          String busiestDay, List<String> teacherNames) {
        this.classroom = classroom;
        this.totalCourses = totalCourses;
        this.occupiedDays = occupiedDays;
        this.busiestDay = busiestDay;
        this.teacherNames = immutableCopy(teacherNames);
    }

    public String getClassroom() {
        return classroom;
    }

    public void setClassroom(String classroom) {
        this.classroom = classroom;
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

    public List<String> getTeacherNames() {
        return teacherNames;
    }

    public void setTeacherNames(List<String> teacherNames) {
        this.teacherNames = immutableCopy(teacherNames);
    }

    private static List<String> immutableCopy(List<String> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<>(source));
    }
}
