package com.coursescheduler.dto;

import java.util.ArrayList;
import java.util.List;

public class BatchPreCheckResponse {

    private boolean canSchedule;
    private int totalConflictCount;
    private List<BatchPreCheckItemResponse> items;

    public BatchPreCheckResponse() {
        this.items = new ArrayList<>();
    }

    public BatchPreCheckResponse(boolean canSchedule, int totalConflictCount,
                                  List<BatchPreCheckItemResponse> items) {
        this.canSchedule = canSchedule;
        this.totalConflictCount = totalConflictCount;
        this.items = items != null ? items : new ArrayList<>();
    }

    public boolean isCanSchedule() {
        return canSchedule;
    }

    public void setCanSchedule(boolean canSchedule) {
        this.canSchedule = canSchedule;
    }

    public int getTotalConflictCount() {
        return totalConflictCount;
    }

    public void setTotalConflictCount(int totalConflictCount) {
        this.totalConflictCount = totalConflictCount;
    }

    public List<BatchPreCheckItemResponse> getItems() {
        return items;
    }

    public void setItems(List<BatchPreCheckItemResponse> items) {
        this.items = items;
    }
}
