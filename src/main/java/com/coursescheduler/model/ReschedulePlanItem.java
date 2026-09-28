package com.coursescheduler.model;

/**
 * 调课方案中的一条课程调整。
 *
 * <p>保存提交方案时看到的原始排课内容（课程名、老师、教室、时间段）与课程版本，
 * 用于确认调课时判断该课程是否已被他人修改。
 */
public class ReschedulePlanItem implements PlanChangeItem {
    private final Long scheduleId;
    private final String courseName;
    private final String teacherName;
    private final String originalClassroom;
    private final String originalTimeSlot;
    private final String newClassroom;
    private final String newTimeSlot;
    private final Long originalVersion;

    public ReschedulePlanItem(Long scheduleId, String courseName, String teacherName,
                              String originalClassroom, String originalTimeSlot,
                              String newClassroom, String newTimeSlot) {
        this(scheduleId, courseName, teacherName, originalClassroom, originalTimeSlot,
                newClassroom, newTimeSlot, null);
    }

    public ReschedulePlanItem(Long scheduleId, String courseName, String teacherName,
                              String originalClassroom, String originalTimeSlot,
                              String newClassroom, String newTimeSlot,
                              Long originalVersion) {
        this.scheduleId = scheduleId;
        this.courseName = courseName;
        this.teacherName = teacherName;
        this.originalClassroom = originalClassroom;
        this.originalTimeSlot = originalTimeSlot;
        this.newClassroom = newClassroom;
        this.newTimeSlot = newTimeSlot;
        this.originalVersion = originalVersion;
    }

    @Override
    public Long getScheduleId() {
        return scheduleId;
    }

    @Override
    public String getCourseName() {
        return courseName;
    }

    @Override
    public String getTeacherName() {
        return teacherName;
    }

    @Override
    public String getOriginalClassroom() {
        return originalClassroom;
    }

    @Override
    public String getOriginalTimeSlot() {
        return originalTimeSlot;
    }

    @Override
    public Long getOriginalVersion() {
        return originalVersion;
    }

    @Override
    public String getNewClassroom() {
        return newClassroom;
    }

    @Override
    public String getNewTimeSlot() {
        return newTimeSlot;
    }
}
