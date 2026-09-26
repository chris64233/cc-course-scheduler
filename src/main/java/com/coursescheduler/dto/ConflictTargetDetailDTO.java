package com.coursescheduler.dto;

import java.util.ArrayList;
import java.util.List;

public class ConflictTargetDetailDTO {

    private String targetIdentifier;
    private String targetName;
    private ConflictDetailDTO.ConflictType conflictType;
    private String conflictingCourseName;
    private String conflictingTeacherName;
    private String conflictingClassroom;
    private String conflictingTimeSlot;
    private ConflictDetailDTO.SourceType sourceType;
    private String reason;
    private String courseName;
    private String teacherName;
    private String classroom;
    private String timeSlot;
    private List<Integer> relatedPendingIndexes;
    private List<String> relatedCourseNames;
    private int conflictCount;
    private List<ConflictDetailDTO.ConflictType> conflictTypes;
    private Integer pendingIndex;
    private Long courseId;

    public ConflictTargetDetailDTO() {
        this.relatedPendingIndexes = new ArrayList<>();
        this.relatedCourseNames = new ArrayList<>();
        this.conflictTypes = new ArrayList<>();
    }

    public String getTargetIdentifier() {
        return targetIdentifier;
    }

    public void setTargetIdentifier(String targetIdentifier) {
        this.targetIdentifier = targetIdentifier;
    }

    public String getTargetName() {
        return targetName;
    }

    public void setTargetName(String targetName) {
        this.targetName = targetName;
    }

    public ConflictDetailDTO.ConflictType getConflictType() {
        return conflictType;
    }

    public void setConflictType(ConflictDetailDTO.ConflictType conflictType) {
        this.conflictType = conflictType;
    }

    public String getConflictingCourseName() {
        return conflictingCourseName;
    }

    public void setConflictingCourseName(String conflictingCourseName) {
        this.conflictingCourseName = conflictingCourseName;
    }

    public String getConflictingTeacherName() {
        return conflictingTeacherName;
    }

    public void setConflictingTeacherName(String conflictingTeacherName) {
        this.conflictingTeacherName = conflictingTeacherName;
    }

    public String getConflictingClassroom() {
        return conflictingClassroom;
    }

    public void setConflictingClassroom(String conflictingClassroom) {
        this.conflictingClassroom = conflictingClassroom;
    }

    public String getConflictingTimeSlot() {
        return conflictingTimeSlot;
    }

    public void setConflictingTimeSlot(String conflictingTimeSlot) {
        this.conflictingTimeSlot = conflictingTimeSlot;
    }

    public ConflictDetailDTO.SourceType getSourceType() {
        return sourceType;
    }

    public void setSourceType(ConflictDetailDTO.SourceType sourceType) {
        this.sourceType = sourceType;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getCourseName() {
        return courseName;
    }

    public void setCourseName(String courseName) {
        this.courseName = courseName;
    }

    public String getTeacherName() {
        return teacherName;
    }

    public void setTeacherName(String teacherName) {
        this.teacherName = teacherName;
    }

    public String getClassroom() {
        return classroom;
    }

    public void setClassroom(String classroom) {
        this.classroom = classroom;
    }

    public String getTimeSlot() {
        return timeSlot;
    }

    public void setTimeSlot(String timeSlot) {
        this.timeSlot = timeSlot;
    }

    public List<Integer> getRelatedPendingIndexes() {
        return relatedPendingIndexes;
    }

    public void setRelatedPendingIndexes(List<Integer> relatedPendingIndexes) {
        this.relatedPendingIndexes = relatedPendingIndexes;
    }

    public List<String> getRelatedCourseNames() {
        return relatedCourseNames;
    }

    public void setRelatedCourseNames(List<String> relatedCourseNames) {
        this.relatedCourseNames = relatedCourseNames;
    }

    public int getConflictCount() {
        return conflictCount;
    }

    public void setConflictCount(int conflictCount) {
        this.conflictCount = conflictCount;
    }

    public List<ConflictDetailDTO.ConflictType> getConflictTypes() {
        return conflictTypes;
    }

    public void setConflictTypes(List<ConflictDetailDTO.ConflictType> conflictTypes) {
        this.conflictTypes = conflictTypes;
    }

    public Integer getPendingIndex() {
        return pendingIndex;
    }

    public void setPendingIndex(Integer pendingIndex) {
        this.pendingIndex = pendingIndex;
    }

    public Long getCourseId() {
        return courseId;
    }

    public void setCourseId(Long courseId) {
        this.courseId = courseId;
    }
}
