package com.coursescheduler.dto;

import java.util.List;

public class TeacherFreeTimeResponse {
    private String teacherName;
    private String weekday;
    private String queryStartTimeFrom;
    private String queryStartTimeTo;
    private boolean fullyAvailable;
    private List<OccupiedSlot> occupiedSlots;
    private List<FreeTimeWindow> freeTimeWindows;

    public TeacherFreeTimeResponse() {}

    public TeacherFreeTimeResponse(String teacherName, String weekday,
                                   String queryStartTimeFrom, String queryStartTimeTo,
                                   boolean fullyAvailable,
                                   List<OccupiedSlot> occupiedSlots,
                                   List<FreeTimeWindow> freeTimeWindows) {
        this.teacherName = teacherName;
        this.weekday = weekday;
        this.queryStartTimeFrom = queryStartTimeFrom;
        this.queryStartTimeTo = queryStartTimeTo;
        this.fullyAvailable = fullyAvailable;
        this.occupiedSlots = occupiedSlots;
        this.freeTimeWindows = freeTimeWindows;
    }

    public String getTeacherName() {
        return teacherName;
    }

    public void setTeacherName(String teacherName) {
        this.teacherName = teacherName;
    }

    public String getWeekday() {
        return weekday;
    }

    public void setWeekday(String weekday) {
        this.weekday = weekday;
    }

    public String getQueryStartTimeFrom() {
        return queryStartTimeFrom;
    }

    public void setQueryStartTimeFrom(String queryStartTimeFrom) {
        this.queryStartTimeFrom = queryStartTimeFrom;
    }

    public String getQueryStartTimeTo() {
        return queryStartTimeTo;
    }

    public void setQueryStartTimeTo(String queryStartTimeTo) {
        this.queryStartTimeTo = queryStartTimeTo;
    }

    public boolean isFullyAvailable() {
        return fullyAvailable;
    }

    public void setFullyAvailable(boolean fullyAvailable) {
        this.fullyAvailable = fullyAvailable;
    }

    public List<OccupiedSlot> getOccupiedSlots() {
        return occupiedSlots;
    }

    public void setOccupiedSlots(List<OccupiedSlot> occupiedSlots) {
        this.occupiedSlots = occupiedSlots;
    }

    public List<FreeTimeWindow> getFreeTimeWindows() {
        return freeTimeWindows;
    }

    public void setFreeTimeWindows(List<FreeTimeWindow> freeTimeWindows) {
        this.freeTimeWindows = freeTimeWindows;
    }

    public static class OccupiedSlot {
        private String timeSlot;
        private String courseName;
        private String classroom;

        public OccupiedSlot() {}

        public OccupiedSlot(String timeSlot, String courseName, String classroom) {
            this.timeSlot = timeSlot;
            this.courseName = courseName;
            this.classroom = classroom;
        }

        public String getTimeSlot() {
            return timeSlot;
        }

        public void setTimeSlot(String timeSlot) {
            this.timeSlot = timeSlot;
        }

        public String getCourseName() {
            return courseName;
        }

        public void setCourseName(String courseName) {
            this.courseName = courseName;
        }

        public String getClassroom() {
            return classroom;
        }

        public void setClassroom(String classroom) {
            this.classroom = classroom;
        }
    }

    public static class FreeTimeWindow {
        private String startTime;
        private String endTime;

        public FreeTimeWindow() {}

        public FreeTimeWindow(String startTime, String endTime) {
            this.startTime = startTime;
            this.endTime = endTime;
        }

        public String getStartTime() {
            return startTime;
        }

        public void setStartTime(String startTime) {
            this.startTime = startTime;
        }

        public String getEndTime() {
            return endTime;
        }

        public void setEndTime(String endTime) {
            this.endTime = endTime;
        }
    }
}
