package com.coursescheduler.dto;

import java.util.ArrayList;
import java.util.List;

public class CourseScheduleConflictPreCheckResponse {

    private boolean canSchedule;
    private int conflictCount;
    private List<ConflictDetailDTO> conflictDetails;

    public CourseScheduleConflictPreCheckResponse() {
        this.conflictDetails = new ArrayList<>();
    }

    public CourseScheduleConflictPreCheckResponse(boolean canSchedule, int conflictCount,
                                                  List<ConflictDetailDTO> conflictDetails) {
        this.canSchedule = canSchedule;
        this.conflictCount = conflictCount;
        this.conflictDetails = conflictDetails != null ? conflictDetails : new ArrayList<>();
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
