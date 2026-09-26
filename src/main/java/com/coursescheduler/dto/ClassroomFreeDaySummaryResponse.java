package com.coursescheduler.dto;

import java.util.List;

public class ClassroomFreeDaySummaryResponse extends AbstractFreeDaySummaryResponse {

    private String classroom;

    public ClassroomFreeDaySummaryResponse() {
        super();
    }

    public ClassroomFreeDaySummaryResponse(String classroom, List<String> busyDays, List<String> freeDays) {
        super(busyDays, freeDays);
        this.classroom = classroom;
    }

    public String getClassroom() {
        return classroom;
    }

    public void setClassroom(String classroom) {
        this.classroom = classroom;
    }
}
