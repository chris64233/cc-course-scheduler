package com.coursescheduler.dto;

import java.util.List;

public class TeacherBatchPreCheckRequest {

    private String teacherName;
    private List<TeacherBatchPreCheckItemRequest> items;

    public String getTeacherName() {
        return teacherName;
    }

    public void setTeacherName(String teacherName) {
        this.teacherName = teacherName;
    }

    public List<TeacherBatchPreCheckItemRequest> getItems() {
        return items;
    }

    public void setItems(List<TeacherBatchPreCheckItemRequest> items) {
        this.items = items;
    }
}
