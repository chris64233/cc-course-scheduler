package com.coursescheduler.dto;

public class PreCheckSummaryResponse {

    public enum PreCheckType {
        SINGLE,
        TEACHER_BATCH,
        CLASSROOM_BATCH
    }

    private PreCheckType preCheckType;
    private int totalItems;
    private int schedulableItems;
    private int unschedulableItems;
    private int totalConflicts;
    private int teacherConflicts;
    private int classroomConflicts;
    private int existingCourseConflicts;
    private int pendingItemConflicts;

    public PreCheckSummaryResponse() {}

    public PreCheckSummaryResponse(
            PreCheckType preCheckType,
            int totalItems,
            int schedulableItems,
            int unschedulableItems,
            int totalConflicts,
            int teacherConflicts,
            int classroomConflicts,
            int existingCourseConflicts,
            int pendingItemConflicts
    ) {
        this.preCheckType = preCheckType;
        this.totalItems = totalItems;
        this.schedulableItems = schedulableItems;
        this.unschedulableItems = unschedulableItems;
        this.totalConflicts = totalConflicts;
        this.teacherConflicts = teacherConflicts;
        this.classroomConflicts = classroomConflicts;
        this.existingCourseConflicts = existingCourseConflicts;
        this.pendingItemConflicts = pendingItemConflicts;
    }

    public PreCheckType getPreCheckType() {
        return preCheckType;
    }

    public void setPreCheckType(PreCheckType preCheckType) {
        this.preCheckType = preCheckType;
    }

    public int getTotalItems() {
        return totalItems;
    }

    public void setTotalItems(int totalItems) {
        this.totalItems = totalItems;
    }

    public int getSchedulableItems() {
        return schedulableItems;
    }

    public void setSchedulableItems(int schedulableItems) {
        this.schedulableItems = schedulableItems;
    }

    public int getUnschedulableItems() {
        return unschedulableItems;
    }

    public void setUnschedulableItems(int unschedulableItems) {
        this.unschedulableItems = unschedulableItems;
    }

    public int getTotalConflicts() {
        return totalConflicts;
    }

    public void setTotalConflicts(int totalConflicts) {
        this.totalConflicts = totalConflicts;
    }

    public int getTeacherConflicts() {
        return teacherConflicts;
    }

    public void setTeacherConflicts(int teacherConflicts) {
        this.teacherConflicts = teacherConflicts;
    }

    public int getClassroomConflicts() {
        return classroomConflicts;
    }

    public void setClassroomConflicts(int classroomConflicts) {
        this.classroomConflicts = classroomConflicts;
    }

    public int getExistingCourseConflicts() {
        return existingCourseConflicts;
    }

    public void setExistingCourseConflicts(int existingCourseConflicts) {
        this.existingCourseConflicts = existingCourseConflicts;
    }

    public int getPendingItemConflicts() {
        return pendingItemConflicts;
    }

    public void setPendingItemConflicts(int pendingItemConflicts) {
        this.pendingItemConflicts = pendingItemConflicts;
    }
}
