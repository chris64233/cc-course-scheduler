package com.coursescheduler.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 一门课程的完整变更链：新增、普通调课、停用冻结与成组修复等事件按时间排列。
 */
public class CourseChangeChainResponse {
    private Long scheduleId;
    private String courseName;
    private List<CourseChangeEventDTO> events = new ArrayList<>();

    public CourseChangeChainResponse() {}

    public CourseChangeChainResponse(Long scheduleId, String courseName, List<CourseChangeEventDTO> events) {
        this.scheduleId = scheduleId;
        this.courseName = courseName;
        this.events = events != null ? events : new ArrayList<>();
    }

    public Long getScheduleId() {
        return scheduleId;
    }

    public void setScheduleId(Long scheduleId) {
        this.scheduleId = scheduleId;
    }

    public String getCourseName() {
        return courseName;
    }

    public void setCourseName(String courseName) {
        this.courseName = courseName;
    }

    public List<CourseChangeEventDTO> getEvents() {
        return events;
    }

    public void setEvents(List<CourseChangeEventDTO> events) {
        this.events = events != null ? events : new ArrayList<>();
    }
}
