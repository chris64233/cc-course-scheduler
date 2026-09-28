package com.coursescheduler.model;

public class CourseSchedule {
    private Long id;
    private String courseName;
    private String teacherName;
    private String classroom;
    private String timeSlot;
    /**
     * 课程版本号：课程每次排课内容（教室/时间段/老师/课程名）被修改时递增。
     * 停用修复方案冻结受影响课程、确认修复时据此判断课程是否已被较新的安排覆盖。
     */
    private long revision = 1;

    public CourseSchedule() {}

    public CourseSchedule(Long id, String courseName, String teacherName, String classroom, String timeSlot) {
        this(id, courseName, teacherName, classroom, timeSlot, 1);
    }

    public CourseSchedule(Long id, String courseName, String teacherName, String classroom, String timeSlot,
                          long revision) {
        this.id = id;
        this.courseName = courseName;
        this.teacherName = teacherName;
        this.classroom = classroom;
        this.timeSlot = timeSlot;
        this.revision = revision;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public String getClassroom() {
        return classroom;
    }

    public void setClassroom(String classroom) {
        this.classroom = classroom;
    }

    public String getTimeSlot() {
        return timeSlot;
    }

    public void setTimeSlot(String timeSlot) {
        this.timeSlot = timeSlot;
    }

    public long getRevision() {
        return revision;
    }

    /**
     * 排课内容发生变化时递增版本号。
     */
    public void incrementRevision() {
        this.revision++;
    }
}
