package com.coursescheduler.dto;

/**
 * 修复任务中一门受影响课程的冻结快照。
 */
public class AffectedCourseDTO {
    private Long scheduleId;
    private String courseName;
    private String teacherName;
    private String originalClassroom;
    private String originalTimeSlot;
    private long frozenRevision;
    private boolean resolved;
    /** 处置状态：ACTIVE 待修复 / RESOLVED 已修复 / MOVED_AWAY 已被其他调课移出 / DELETED 已删除 */
    private String state;

    public AffectedCourseDTO() {}

    public AffectedCourseDTO(Long scheduleId, String courseName, String teacherName,
                             String originalClassroom, String originalTimeSlot,
                             long frozenRevision, boolean resolved, String state) {
        this.scheduleId = scheduleId;
        this.courseName = courseName;
        this.teacherName = teacherName;
        this.originalClassroom = originalClassroom;
        this.originalTimeSlot = originalTimeSlot;
        this.frozenRevision = frozenRevision;
        this.resolved = resolved;
        this.state = state;
    }

    public Long getScheduleId() {
        return scheduleId;
    }

    public void setScheduleId(Long scheduleId) {
        this.scheduleId = scheduleId;
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

    public String getOriginalClassroom() {
        return originalClassroom;
    }

    public void setOriginalClassroom(String originalClassroom) {
        this.originalClassroom = originalClassroom;
    }

    public String getOriginalTimeSlot() {
        return originalTimeSlot;
    }

    public void setOriginalTimeSlot(String originalTimeSlot) {
        this.originalTimeSlot = originalTimeSlot;
    }

    public long getFrozenRevision() {
        return frozenRevision;
    }

    public void setFrozenRevision(long frozenRevision) {
        this.frozenRevision = frozenRevision;
    }

    public boolean isResolved() {
        return resolved;
    }

    public void setResolved(boolean resolved) {
        this.resolved = resolved;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }
}
