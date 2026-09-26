package com.coursescheduler.dto;

import java.util.List;

public class ClassroomBatchPreCheckRequest {

    private String classroom;
    private List<ClassroomBatchPreCheckItemRequest> items;

    public String getClassroom() {
        return classroom;
    }

    public void setClassroom(String classroom) {
        this.classroom = classroom;
    }

    public List<ClassroomBatchPreCheckItemRequest> getItems() {
        return items;
    }

    public void setItems(List<ClassroomBatchPreCheckItemRequest> items) {
        this.items = items;
    }
}
