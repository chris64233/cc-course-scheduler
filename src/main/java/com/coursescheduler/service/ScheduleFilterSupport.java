package com.coursescheduler.service;

import com.coursescheduler.dto.CourseScheduleFilterRequest;
import com.coursescheduler.exception.InvalidTimeSlotException;
import com.coursescheduler.model.CourseSchedule;
import com.coursescheduler.util.TimeSlotUtils;
import org.springframework.util.StringUtils;

public class ScheduleFilterSupport {

    public static class NormalizedFilter {
        private final String courseName;
        private final String teacherName;
        private final String classroom;
        private final String timeSlot;
        private final Integer weekdayIndex;
        private final Integer startTimeFromMinutes;
        private final Integer startTimeToMinutes;
        private final String sortBy;
        private final String sortDirection;

        NormalizedFilter(String courseName, String teacherName, String classroom,
                         String timeSlot, Integer weekdayIndex,
                         Integer startTimeFromMinutes, Integer startTimeToMinutes,
                         String sortBy, String sortDirection) {
            this.courseName = courseName;
            this.teacherName = teacherName;
            this.classroom = classroom;
            this.timeSlot = timeSlot;
            this.weekdayIndex = weekdayIndex;
            this.startTimeFromMinutes = startTimeFromMinutes;
            this.startTimeToMinutes = startTimeToMinutes;
            this.sortBy = sortBy;
            this.sortDirection = sortDirection;
        }

        public String getCourseName() {
            return courseName;
        }

        public String getTeacherName() {
            return teacherName;
        }

        public String getClassroom() {
            return classroom;
        }

        public String getTimeSlot() {
            return timeSlot;
        }

        public Integer getWeekdayIndex() {
            return weekdayIndex;
        }

        public Integer getStartTimeFromMinutes() {
            return startTimeFromMinutes;
        }

        public Integer getStartTimeToMinutes() {
            return startTimeToMinutes;
        }

        public String getSortBy() {
            return sortBy;
        }

        public String getSortDirection() {
            return sortDirection;
        }
    }

    private ScheduleFilterSupport() {
    }

    public static NormalizedFilter normalize(CourseScheduleFilterRequest filter) {
        if (filter == null) {
            return new NormalizedFilter(null, null, null, null, null, null, null, null, null);
        }
        String normalizedWeekday = normalizeText(filter.getWeekday());
        String normalizedStartTimeFrom = normalizeText(filter.getStartTimeFrom());
        String normalizedStartTimeTo = normalizeText(filter.getStartTimeTo());

        Integer weekdayIndex = null;
        Integer startTimeFromMinutes = null;
        Integer startTimeToMinutes = null;

        if (normalizedWeekday != null) {
            weekdayIndex = TimeSlotUtils.parseWeekdayInput(normalizedWeekday);
        }
        if (normalizedStartTimeFrom != null) {
            startTimeFromMinutes = TimeSlotUtils.parseTimePoint(normalizedStartTimeFrom);
        }
        if (normalizedStartTimeTo != null) {
            startTimeToMinutes = TimeSlotUtils.parseTimePoint(normalizedStartTimeTo);
        }

        if (startTimeFromMinutes != null && startTimeToMinutes != null
                && startTimeFromMinutes > startTimeToMinutes) {
            throw new InvalidTimeSlotException(
                    "时间范围不合法，startTimeFrom 必须早于或等于 startTimeTo，实际输入："
                            + normalizedStartTimeFrom + " - " + normalizedStartTimeTo);
        }

        return new NormalizedFilter(
                normalizeText(filter.getCourseName()),
                normalizeText(filter.getTeacherName()),
                normalizeText(filter.getClassroom()),
                normalizeTimeSlot(filter.getTimeSlot()),
                weekdayIndex,
                startTimeFromMinutes,
                startTimeToMinutes,
                normalizeText(filter.getSortBy()),
                normalizeText(filter.getSortDirection())
        );
    }

    public static boolean matches(CourseSchedule schedule, NormalizedFilter normalizedFilter) {
        return matchesCourseName(schedule, normalizedFilter.getCourseName())
                && matchesTeacherName(schedule, normalizedFilter.getTeacherName())
                && matchesClassroom(schedule, normalizedFilter.getClassroom())
                && matchesTimeSlot(schedule, normalizedFilter.getTimeSlot())
                && matchesTimeRange(schedule, normalizedFilter.getWeekdayIndex(),
                normalizedFilter.getStartTimeFromMinutes(),
                normalizedFilter.getStartTimeToMinutes());
    }

    private static String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String normalizeTimeSlot(String rawTimeSlot) {
        String trimmed = normalizeText(rawTimeSlot);
        return trimmed != null ? TimeSlotUtils.normalize(trimmed) : null;
    }

    private static boolean matchesCourseName(CourseSchedule schedule, String courseNameKeyword) {
        return !StringUtils.hasText(courseNameKeyword)
                || schedule.getCourseName().contains(courseNameKeyword);
    }

    private static boolean matchesTeacherName(CourseSchedule schedule, String teacherNameKeyword) {
        return !StringUtils.hasText(teacherNameKeyword)
                || schedule.getTeacherName().contains(teacherNameKeyword);
    }

    private static boolean matchesClassroom(CourseSchedule schedule, String classroomKeyword) {
        return !StringUtils.hasText(classroomKeyword)
                || schedule.getClassroom().contains(classroomKeyword);
    }

    private static boolean matchesTimeSlot(CourseSchedule schedule, String timeSlot) {
        return !StringUtils.hasText(timeSlot)
                || schedule.getTimeSlot().equals(timeSlot);
    }

    private static boolean matchesTimeRange(CourseSchedule schedule, Integer weekdayIndex,
                                            Integer startTimeFromMinutes, Integer startTimeToMinutes) {
        if (weekdayIndex == null && startTimeFromMinutes == null && startTimeToMinutes == null) {
            return true;
        }

        String normalizedTimeSlot = schedule.getTimeSlot();
        int scheduleWeekdayIndex = TimeSlotUtils.extractWeekdayIndexFromNormalized(normalizedTimeSlot);
        int scheduleStartTimeMinutes = TimeSlotUtils.extractStartTimeMinutesFromNormalized(normalizedTimeSlot);

        if (weekdayIndex != null && scheduleWeekdayIndex != weekdayIndex) {
            return false;
        }

        if (startTimeFromMinutes != null && scheduleStartTimeMinutes < startTimeFromMinutes) {
            return false;
        }

        if (startTimeToMinutes != null && scheduleStartTimeMinutes > startTimeToMinutes) {
            return false;
        }

        return true;
    }
}
