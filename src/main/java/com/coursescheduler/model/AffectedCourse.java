package com.coursescheduler.model;

/**
 * 停用生效时冻结的一门受影响课程（冲突明细快照）。
 */
public class AffectedCourse {
    private final Long scheduleId;
    private final String courseName;
    private final String teacherName;
    private final String classroom;
    private final String timeSlot;
    /** 冻结时的课程版本 */
    private final long version;
    /** 是否已由确认的修复方案安排妥当 */
    private boolean resolved;
    /** 修复结果说明（如：修复方案 ID） */
    private String resolvedReason;

    public AffectedCourse(Long scheduleId, String courseName, String teacherName,
                          String classroom, String timeSlot, long version) {
        this.scheduleId = scheduleId;
        this.courseName = courseName;
        this.teacherName = teacherName;
        this.classroom = classroom;
        this.timeSlot = timeSlot;
        this.version = version;
        this.resolved = false;
    }

    public Long getScheduleId() {
        return scheduleId;
    }

    public String getCourseName() {
        return courseName;
    }

    public String getTeacherName() {
        return teacherName;
    }

    public String getClassroom() {
        return classroom;
    }

    public String getTimeSlot() {
        return timeSlot;
    }

    public long getVersion() {
        return version;
    }

    public boolean isResolved() {
        return resolved;
    }

    public void setResolved(boolean resolved) {
        this.resolved = resolved;
    }

    public String getResolvedReason() {
        return resolvedReason;
    }

    public void setResolvedReason(String resolvedReason) {
        this.resolvedReason = resolvedReason;
    }
}
