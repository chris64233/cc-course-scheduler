package com.coursescheduler.dto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ConflictRiskPreviewResponse {

    public enum RiskLevel {
        LOW,
        MEDIUM,
        HIGH
    }

    private int teacherConflictCount;
    private int classroomConflictCount;
    private List<String> conflictCourseNames;
    private RiskLevel riskLevel;

    public ConflictRiskPreviewResponse() {
        this.conflictCourseNames = Collections.emptyList();
    }

    public ConflictRiskPreviewResponse(int teacherConflictCount, int classroomConflictCount,
                                       List<String> conflictCourseNames, RiskLevel riskLevel) {
        this.teacherConflictCount = teacherConflictCount;
        this.classroomConflictCount = classroomConflictCount;
        this.conflictCourseNames = conflictCourseNames != null
                ? Collections.unmodifiableList(new ArrayList<>(conflictCourseNames))
                : Collections.<String>emptyList();
        this.riskLevel = riskLevel;
    }

    public int getTeacherConflictCount() {
        return teacherConflictCount;
    }

    public void setTeacherConflictCount(int teacherConflictCount) {
        this.teacherConflictCount = teacherConflictCount;
    }

    public int getClassroomConflictCount() {
        return classroomConflictCount;
    }

    public void setClassroomConflictCount(int classroomConflictCount) {
        this.classroomConflictCount = classroomConflictCount;
    }

    public List<String> getConflictCourseNames() {
        return conflictCourseNames;
    }

    public void setConflictCourseNames(List<String> conflictCourseNames) {
        this.conflictCourseNames = conflictCourseNames != null
                ? Collections.unmodifiableList(new ArrayList<>(conflictCourseNames))
                : Collections.<String>emptyList();
    }

    public RiskLevel getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(RiskLevel riskLevel) {
        this.riskLevel = riskLevel;
    }
}
