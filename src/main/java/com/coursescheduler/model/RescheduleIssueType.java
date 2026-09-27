package com.coursescheduler.model;

/**
 * 调课冲突的类别。
 *
 * <ul>
 *   <li>{@link #SCHEDULE_DELETED}：方案引用的课程安排已被删除。</li>
 *   <li>{@link #SCHEDULE_CHANGED}：方案引用的课程安排在提交后被他人修改，原始快照过期。</li>
 *   <li>{@link #TEACHER}：目标时段与其他课程发生教师冲突。</li>
 *   <li>{@link #CLASSROOM}：目标时段与其他课程发生教室冲突。</li>
 * </ul>
 */
public enum RescheduleIssueType {
    SCHEDULE_DELETED,
    SCHEDULE_CHANGED,
    TEACHER,
    CLASSROOM
}
