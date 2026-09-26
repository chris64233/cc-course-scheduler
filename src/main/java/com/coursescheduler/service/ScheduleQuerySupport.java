package com.coursescheduler.service;

import com.coursescheduler.model.CourseSchedule;
import com.coursescheduler.util.TimeSlotUtils;

import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class ScheduleQuerySupport {

    public enum QueryType {
        CLASSROOM,
        TEACHER
    }

    private ScheduleQuerySupport() {
    }

    public static List<CourseSchedule> findDailySchedules(
            List<CourseSchedule> schedules,
            QueryType queryType,
            String queryValue,
            int targetWeekdayIndex
    ) {
        return baseStream(schedules, queryType, queryValue)
                .filter(weekdayMatcher(targetWeekdayIndex))
                .sorted(dailyComparator())
                .collect(Collectors.toList());
    }

    public static List<CourseSchedule> findWeeklySchedules(
            List<CourseSchedule> schedules,
            QueryType queryType,
            String queryValue
    ) {
        return baseStream(schedules, queryType, queryValue)
                .sorted(weeklyComparator())
                .collect(Collectors.toList());
    }

    private static Stream<CourseSchedule> baseStream(
            List<CourseSchedule> schedules,
            QueryType queryType,
            String queryValue
    ) {
        if (schedules == null || schedules.isEmpty()) {
            return Stream.empty();
        }
        return schedules.stream().filter(fieldMatcher(queryType, queryValue));
    }

    private static Predicate<CourseSchedule> fieldMatcher(QueryType queryType, String queryValue) {
        switch (queryType) {
            case CLASSROOM:
                return s -> queryValue.equals(s.getClassroom());
            case TEACHER:
                return s -> queryValue.equals(s.getTeacherName());
            default:
                return s -> false;
        }
    }

    private static Predicate<CourseSchedule> weekdayMatcher(int targetWeekdayIndex) {
        return s -> TimeSlotUtils.extractWeekdayIndexFromNormalized(s.getTimeSlot()) == targetWeekdayIndex;
    }

    private static Comparator<CourseSchedule> dailyComparator() {
        return Comparator.comparingInt(
                s -> TimeSlotUtils.extractStartTimeMinutesFromNormalized(s.getTimeSlot())
        );
    }

    private static Comparator<CourseSchedule> weeklyComparator() {
        return Comparator
                .comparingInt((CourseSchedule s) ->
                        TimeSlotUtils.extractWeekdayIndexFromNormalized(s.getTimeSlot()))
                .thenComparingInt(s ->
                        TimeSlotUtils.extractStartTimeMinutesFromNormalized(s.getTimeSlot()));
    }
}
