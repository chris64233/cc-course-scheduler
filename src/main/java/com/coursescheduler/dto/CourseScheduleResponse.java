package com.coursescheduler.dto;

public class CourseScheduleResponse {
    private Long id;
    private String courseName;
    private String teacherName;
    private String classroom;
    private String timeSlot;
    private long version = 1;

    public CourseScheduleResponse() {}

    public CourseScheduleResponse(Long id, String courseName, String teacherName, String classroom, String timeSlot) {
        this(id, courseName, teacherName, classroom, timeSlot, 1L);
    }

    public CourseScheduleResponse(Long id, String courseName, String teacherName,
                                  String classroom, String timeSlot, long version) {
        this.id = id;
        this.courseName = courseName;
        this.teacherName = teacherName;
        this.classroom = classroom;
        this.timeSlot = timeSlot;
        this.version = version;
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

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }
}
