package com.coursescheduler.dto;

import java.util.List;

public class TeacherFreeDaySummaryResponse extends AbstractFreeDaySummaryResponse {

    private String teacherName;

    public TeacherFreeDaySummaryResponse() {
        super();
    }

    public TeacherFreeDaySummaryResponse(String teacherName, List<String> busyDays, List<String> freeDays) {
        super(busyDays, freeDays);
        this.teacherName = teacherName;
    }

    public String getTeacherName() {
        return teacherName;
    }

    public void setTeacherName(String teacherName) {
        this.teacherName = teacherName;
    }
}
