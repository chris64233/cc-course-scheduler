package com.coursescheduler.dto;

import java.util.ArrayList;
import java.util.List;

public class ConflictTypeGroupDTO {

    private ConflictDetailDTO.ConflictType conflictType;
    private int count;
    private List<ConflictDetailDTO> conflicts;
    private List<String> courseNames;
    private int conflictCount;
    private int existingCourseConflictCount;
    private int pendingItemConflictCount;

    public ConflictTypeGroupDTO() {
        this.conflicts = new ArrayList<>();
        this.courseNames = new ArrayList<>();
    }

    public ConflictTypeGroupDTO(ConflictDetailDTO.ConflictType conflictType, int count,
                                List<ConflictDetailDTO> conflicts, List<String> courseNames) {
        this.conflictType = conflictType;
        this.count = count;
        this.conflicts = conflicts != null ? conflicts : new ArrayList<>();
        this.courseNames = courseNames != null ? courseNames : new ArrayList<>();
    }

    public ConflictDetailDTO.ConflictType getConflictType() {
        return conflictType;
    }

    public void setConflictType(ConflictDetailDTO.ConflictType conflictType) {
        this.conflictType = conflictType;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public List<ConflictDetailDTO> getConflicts() {
        return conflicts;
    }

    public void setConflicts(List<ConflictDetailDTO> conflicts) {
        this.conflicts = conflicts;
    }

    public List<String> getCourseNames() {
        return courseNames;
    }

    public void setCourseNames(List<String> courseNames) {
        this.courseNames = courseNames;
    }

    public int getConflictCount() {
        return conflictCount;
    }

    public void setConflictCount(int conflictCount) {
        this.conflictCount = conflictCount;
    }

    public int getExistingCourseConflictCount() {
        return existingCourseConflictCount;
    }

    public void setExistingCourseConflictCount(int existingCourseConflictCount) {
        this.existingCourseConflictCount = existingCourseConflictCount;
    }

    public int getPendingItemConflictCount() {
        return pendingItemConflictCount;
    }

    public void setPendingItemConflictCount(int pendingItemConflictCount) {
        this.pendingItemConflictCount = pendingItemConflictCount;
    }
}
