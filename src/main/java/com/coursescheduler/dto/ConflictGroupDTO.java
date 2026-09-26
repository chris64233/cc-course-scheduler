package com.coursescheduler.dto;

import java.util.ArrayList;
import java.util.List;

public class ConflictGroupDTO {

    private ConflictDetailDTO.SourceType sourceType;
    private int conflictCount;
    private int teacherConflictCount;
    private int classroomConflictCount;
    private List<String> courseNames;

    public ConflictGroupDTO() {
        this.courseNames = new ArrayList<>();
    }

    public ConflictGroupDTO(ConflictDetailDTO.SourceType sourceType, int conflictCount,
                            int teacherConflictCount, int classroomConflictCount,
                            List<String> courseNames) {
        this.sourceType = sourceType;
        this.conflictCount = conflictCount;
        this.teacherConflictCount = teacherConflictCount;
        this.classroomConflictCount = classroomConflictCount;
        this.courseNames = courseNames != null ? courseNames : new ArrayList<>();
    }

    public ConflictDetailDTO.SourceType getSourceType() {
        return sourceType;
    }

    public void setSourceType(ConflictDetailDTO.SourceType sourceType) {
        this.sourceType = sourceType;
    }

    public int getConflictCount() {
        return conflictCount;
    }

    public void setConflictCount(int conflictCount) {
        this.conflictCount = conflictCount;
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

    public List<String> getCourseNames() {
        return courseNames;
    }

    public void setCourseNames(List<String> courseNames) {
        this.courseNames = courseNames;
    }
}
