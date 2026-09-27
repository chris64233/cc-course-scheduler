package com.coursescheduler.dto;

/**
 * 调课方案中一条冲突明细。
 */
public class RescheduleConflictDTO {

    public enum ConflictType {
        /** 与方案外课程的老师冲突 */
        TEACHER,
        /** 与方案外课程的教室冲突 */
        CLASSROOM,
        /** 方案内两条调整之间的老师冲突 */
        INTERNAL_TEACHER,
        /** 方案内两条调整之间的教室冲突 */
        INTERNAL_CLASSROOM,
        /** 课程在方案提交后已被他人修改 */
        SCHEDULE_CHANGED,
        /** 课程在方案提交后已被删除 */
        SCHEDULE_NOT_FOUND
    }

    private Long scheduleId;
    private ConflictType conflictType;
    private Long conflictingCourseId;
    private String conflictingCourseName;
    private String message;

    public RescheduleConflictDTO() {}

    public RescheduleConflictDTO(Long scheduleId, ConflictType conflictType,
                                 Long conflictingCourseId, String conflictingCourseName,
                                 String message) {
        this.scheduleId = scheduleId;
        this.conflictType = conflictType;
        this.conflictingCourseId = conflictingCourseId;
        this.conflictingCourseName = conflictingCourseName;
        this.message = message;
    }

    public Long getScheduleId() {
        return scheduleId;
    }

    public void setScheduleId(Long scheduleId) {
        this.scheduleId = scheduleId;
    }

    public ConflictType getConflictType() {
        return conflictType;
    }

    public void setConflictType(ConflictType conflictType) {
        this.conflictType = conflictType;
    }

    public Long getConflictingCourseId() {
        return conflictingCourseId;
    }

    public void setConflictingCourseId(Long conflictingCourseId) {
        this.conflictingCourseId = conflictingCourseId;
    }

    public String getConflictingCourseName() {
        return conflictingCourseName;
    }

    public void setConflictingCourseName(String conflictingCourseName) {
        this.conflictingCourseName = conflictingCourseName;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
