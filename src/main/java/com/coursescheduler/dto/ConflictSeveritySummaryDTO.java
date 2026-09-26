package com.coursescheduler.dto;

import java.util.ArrayList;
import java.util.List;

public class ConflictSeveritySummaryDTO {

    public enum Severity {
        BLOCKER,
        WARNING
    }

    private Severity severity;
    private int count;
    private String description;
    private int targetCount;
    private int conflictTypeCount;
    private int existingCourseTargetCount;
    private int pendingItemTargetCount;
    private List<String> courseNames;

    public ConflictSeveritySummaryDTO() {
        this.courseNames = new ArrayList<>();
    }

    public ConflictSeveritySummaryDTO(Severity severity, int count, String description) {
        this.severity = severity;
        this.count = count;
        this.description = description;
        this.courseNames = new ArrayList<>();
    }

    public Severity getSeverity() {
        return severity;
    }

    public void setSeverity(Severity severity) {
        this.severity = severity;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getTargetCount() {
        return targetCount;
    }

    public void setTargetCount(int targetCount) {
        this.targetCount = targetCount;
    }

    public int getConflictTypeCount() {
        return conflictTypeCount;
    }

    public void setConflictTypeCount(int conflictTypeCount) {
        this.conflictTypeCount = conflictTypeCount;
    }

    public int getExistingCourseTargetCount() {
        return existingCourseTargetCount;
    }

    public void setExistingCourseTargetCount(int existingCourseTargetCount) {
        this.existingCourseTargetCount = existingCourseTargetCount;
    }

    public int getPendingItemTargetCount() {
        return pendingItemTargetCount;
    }

    public void setPendingItemTargetCount(int pendingItemTargetCount) {
        this.pendingItemTargetCount = pendingItemTargetCount;
    }

    public List<String> getCourseNames() {
        return courseNames;
    }

    public void setCourseNames(List<String> courseNames) {
        this.courseNames = courseNames;
    }
}
