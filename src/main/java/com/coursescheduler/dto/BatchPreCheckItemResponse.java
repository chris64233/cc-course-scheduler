package com.coursescheduler.dto;

import java.util.ArrayList;
import java.util.List;

public class BatchPreCheckItemResponse {

    private int originalIndex;
    private String courseName;
    private boolean canSchedule;
    private int conflictCount;
    private List<ConflictDetailDTO> conflictDetails;

    public BatchPreCheckItemResponse() {
        this.conflictDetails = new ArrayList<>();
    }

    public BatchPreCheckItemResponse(int originalIndex, String courseName, boolean canSchedule,
                                      int conflictCount, List<ConflictDetailDTO> conflictDetails) {
        this.originalIndex = originalIndex;
        this.courseName = courseName;
        this.canSchedule = canSchedule;
        this.conflictCount = conflictCount;
        this.conflictDetails = conflictDetails != null ? conflictDetails : new ArrayList<>();
    }

    public int getOriginalIndex() {
        return originalIndex;
    }

    public void setOriginalIndex(int originalIndex) {
        this.originalIndex = originalIndex;
    }

    public String getCourseName() {
        return courseName;
    }

    public void setCourseName(String courseName) {
        this.courseName = courseName;
    }

    public boolean isCanSchedule() {
        return canSchedule;
    }

    public void setCanSchedule(boolean canSchedule) {
        this.canSchedule = canSchedule;
    }

    public int getConflictCount() {
        return conflictCount;
    }

    public void setConflictCount(int conflictCount) {
        this.conflictCount = conflictCount;
    }

    public List<ConflictDetailDTO> getConflictDetails() {
        return conflictDetails;
    }

    public void setConflictDetails(List<ConflictDetailDTO> conflictDetails) {
        this.conflictDetails = conflictDetails;
    }
}
