package com.coursescheduler.model;

/**
 * 调课方案中的一条课程调整。
 *
 * <p>保存提交方案时看到的原始排课内容（课程名、老师、教室、时间段），
 * 用于确认调课时判断该课程是否已被他人修改。
 */
public class ReschedulePlanItem {
    private final Long scheduleId;
    private final String courseName;
    private final String teacherName;
    private final String originalClassroom;
    private final String originalTimeSlot;
    private final String newClassroom;
    private final String newTimeSlot;

    public ReschedulePlanItem(Long scheduleId, String courseName, String teacherName,
                              String originalClassroom, String originalTimeSlot,
                              String newClassroom, String newTimeSlot) {
        this.scheduleId = scheduleId;
        this.courseName = courseName;
        this.teacherName = teacherName;
        this.originalClassroom = originalClassroom;
        this.originalTimeSlot = originalTimeSlot;
        this.newClassroom = newClassroom;
        this.newTimeSlot = newTimeSlot;
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

    public String getNewClassroom() {
        return newClassroom;
    }

    public String getNewTimeSlot() {
        return newTimeSlot;
    }
}
