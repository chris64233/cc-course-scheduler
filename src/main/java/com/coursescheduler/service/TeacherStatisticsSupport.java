package com.coursescheduler.service;

import com.coursescheduler.dto.TeacherConsecutiveBusyDaysResponse;
import com.coursescheduler.dto.TeacherCourseStatisticsResponse;
import com.coursescheduler.dto.TeacherFreeDaySummaryResponse;
import com.coursescheduler.dto.TeacherWeeklySummaryResponse;
import com.coursescheduler.model.CourseSchedule;
import com.coursescheduler.util.TimeSlotUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class TeacherStatisticsSupport {

    private TeacherStatisticsSupport() {
    }

    public static List<TeacherCourseStatisticsResponse> calculateStatistics(List<CourseSchedule> schedules) {
        return ScheduleStatisticsSupport.calculateStatistics(
                schedules,
                CourseSchedule::getTeacherName,
                TeacherCourseStatisticsResponse::new
        );
    }

    public static TeacherWeeklySummaryResponse calculateWeeklySummary(String teacherName, List<CourseSchedule> allSchedules) {
        WeeklySummarySupport.WeeklySummaryResult result = WeeklySummarySupport.calculate(
                allSchedules,
                s -> teacherName.equals(s.getTeacherName()),
                CourseSchedule::getCourseName
        );

        return new TeacherWeeklySummaryResponse(
                teacherName,
                result.getTotalCourses(),
                result.getOccupiedDays(),
                result.getBusiestDay(),
                result.getNames()
        );
    }

    public static TeacherFreeDaySummaryResponse calculateFreeDaySummary(String teacherName, List<CourseSchedule> allSchedules) {
        return FreeDaySummarySupport.calculateFreeDaySummary(
                allSchedules,
                s -> teacherName.equals(s.getTeacherName()),
                (busyDays, freeDays) -> new TeacherFreeDaySummaryResponse(teacherName, busyDays, freeDays)
        );
    }

    public static TeacherConsecutiveBusyDaysResponse calculateConsecutiveBusyDays(String teacherName, List<CourseSchedule> allSchedules) {
        Set<Integer> busyIndices = WeeklySummarySupport.resolveBusyWeekdayIndices(
                allSchedules, s -> teacherName.equals(s.getTeacherName()));

        if (busyIndices.isEmpty()) {
            return new TeacherConsecutiveBusyDaysResponse(teacherName, Collections.emptyList(), 0, Collections.emptyList());
        }

        List<String> busyDays = new ArrayList<>();
        for (int i = 0; i < TimeSlotUtils.WEEKDAY_COUNT; i++) {
            if (busyIndices.contains(i)) {
                busyDays.add(TimeSlotUtils.getWeekdayName(i));
            }
        }

        int maxConsecutive = 0;
        int bestStart = -1;

        int i = 0;
        while (i < TimeSlotUtils.WEEKDAY_COUNT) {
            if (!busyIndices.contains(i)) {
                i++;
                continue;
            }
            int start = i;
            int length = 0;
            while (i < TimeSlotUtils.WEEKDAY_COUNT && busyIndices.contains(i)) {
                length++;
                i++;
            }
            if (length > maxConsecutive) {
                maxConsecutive = length;
                bestStart = start;
            }
        }

        List<String> longestBusyStreak = new ArrayList<>();
        if (maxConsecutive > 0 && bestStart >= 0) {
            for (int j = bestStart; j < bestStart + maxConsecutive; j++) {
                longestBusyStreak.add(TimeSlotUtils.getWeekdayName(j));
            }
        }

        return new TeacherConsecutiveBusyDaysResponse(teacherName, busyDays, maxConsecutive, longestBusyStreak);
    }
}
