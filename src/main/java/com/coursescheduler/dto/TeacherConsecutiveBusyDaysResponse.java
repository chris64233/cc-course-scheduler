package com.coursescheduler.dto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TeacherConsecutiveBusyDaysResponse {

    private String teacherName;
    private List<String> busyDays;
    private int maxConsecutiveBusyDays;
    private List<String> longestBusyStreak;

    public TeacherConsecutiveBusyDaysResponse() {
        this.busyDays = Collections.emptyList();
        this.maxConsecutiveBusyDays = 0;
        this.longestBusyStreak = Collections.emptyList();
    }

    public TeacherConsecutiveBusyDaysResponse(String teacherName, List<String> busyDays,
                                               int maxConsecutiveBusyDays, List<String> longestBusyStreak) {
        this.teacherName = teacherName;
        this.busyDays = immutableCopy(busyDays);
        this.maxConsecutiveBusyDays = maxConsecutiveBusyDays;
        this.longestBusyStreak = immutableCopy(longestBusyStreak);
    }

    public String getTeacherName() {
        return teacherName;
    }

    public void setTeacherName(String teacherName) {
        this.teacherName = teacherName;
    }

    public List<String> getBusyDays() {
        return busyDays;
    }

    public void setBusyDays(List<String> busyDays) {
        this.busyDays = immutableCopy(busyDays);
    }

    public int getMaxConsecutiveBusyDays() {
        return maxConsecutiveBusyDays;
    }

    public void setMaxConsecutiveBusyDays(int maxConsecutiveBusyDays) {
        this.maxConsecutiveBusyDays = maxConsecutiveBusyDays;
    }

    public List<String> getLongestBusyStreak() {
        return longestBusyStreak;
    }

    public void setLongestBusyStreak(List<String> longestBusyStreak) {
        this.longestBusyStreak = immutableCopy(longestBusyStreak);
    }

    private static List<String> immutableCopy(List<String> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<>(source));
    }
}
