package com.coursescheduler.model;

/**
 * 成组调课方案（普通调课方案或停用修复方案）中一条课程调整的抽象。
 *
 * <p>实现类需保存提交方案时课程的原始排课内容与课程版本，
 * 供确认时判断课程是否已被他人修改。
 */
public interface PlanChangeItem {

    Long getScheduleId();

    String getCourseName();

    String getTeacherName();

    String getOriginalClassroom();

    String getOriginalTimeSlot();

    /** 提交方案时的课程版本；为 null 表示不校验版本（兼容旧方案）。 */
    Long getOriginalVersion();

    String getNewClassroom();

    String getNewTimeSlot();
}
