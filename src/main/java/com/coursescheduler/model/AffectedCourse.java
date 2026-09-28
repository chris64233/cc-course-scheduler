package com.coursescheduler.model;

/**
 * 修复任务中被冻结的一门受影响课程。
 *
 * <p>冻结时（停用生效或停用范围调整时）保存课程当时的排课内容作为快照，
 * 后续修复方案确认时据此判断课程版本是否已变化（乐观版本控制）。
 * 课程在任务生命周期中的处置通过 {@link State} 跟踪，冻结记录始终保留，
 * 以保证停用事件、修复前后安排和处理人员的记录完整。
 */
public class AffectedCourse {

    /**
     * 受影响课程的处置状态。
     */
    public enum State {
        /** 仍与停用范围冲突，等待修复方案覆盖 */
        ACTIVE,
        /** 已由确认的修复方案安排到停用范围之外 */
        RESOLVED,
        /** 冻结后被普通调课等方式移出了停用范围，不再需要修复 */
        MOVED_AWAY,
        /** 冻结后课程已被删除 */
        DELETED
    }

    private final Long scheduleId;
    private final String courseName;
    private final String teacherName;
    private final String originalClassroom;
    private final String originalTimeSlot;
    private final long frozenRevision;
    private State state = State.ACTIVE;

    public AffectedCourse(Long scheduleId, String courseName, String teacherName,
                          String originalClassroom, String originalTimeSlot, long frozenRevision) {
        this.scheduleId = scheduleId;
        this.courseName = courseName;
        this.teacherName = teacherName;
        this.originalClassroom = originalClassroom;
        this.originalTimeSlot = originalTimeSlot;
        this.frozenRevision = frozenRevision;
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

    public String getOriginalClassroom() {
        return originalClassroom;
    }

    public String getOriginalTimeSlot() {
        return originalTimeSlot;
    }

    public long getFrozenRevision() {
        return frozenRevision;
    }

    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
    }
}
