package com.coursescheduler.dto;

public class PendingConflictPairItemDTO {

    private int index;
    private String courseName;
    private String teacherName;
    private String classroom;
    private String timeSlot;

    public PendingConflictPairItemDTO() {
    }

    public PendingConflictPairItemDTO(int index, String courseName, String teacherName,
                                       String classroom, String timeSlot) {
        this.index = index;
        this.courseName = courseName;
        this.teacherName = teacherName;
        this.classroom = classroom;
        this.timeSlot = timeSlot;
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
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
}
