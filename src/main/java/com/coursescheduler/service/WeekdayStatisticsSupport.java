package com.coursescheduler.service;

import com.coursescheduler.dto.CourseScheduleWeekdayStatisticsResponse;
import com.coursescheduler.model.CourseSchedule;
import com.coursescheduler.util.TimeSlotUtils;

import java.util.List;

public class WeekdayStatisticsSupport {

    private WeekdayStatisticsSupport() {
    }

    public static CourseScheduleWeekdayStatisticsResponse calculateStatistics(List<CourseSchedule> schedules) {
        int[] counts = new int[TimeSlotUtils.WEEKDAY_COUNT];
        if (schedules != null) {
            for (CourseSchedule schedule : schedules) {
                int weekdayIndex = TimeSlotUtils.extractWeekdayIndex(schedule.getTimeSlot());
                counts[weekdayIndex]++;
            }
        }
        return buildResponse(counts);
    }

    private static CourseScheduleWeekdayStatisticsResponse buildResponse(int[] counts) {
        if (counts == null || counts.length != TimeSlotUtils.WEEKDAY_COUNT) {
            throw new IllegalArgumentException("counts 数组长度必须为 " + TimeSlotUtils.WEEKDAY_COUNT);
        }
        return new CourseScheduleWeekdayStatisticsResponse(
                counts[TimeSlotUtils.MONDAY],
                counts[TimeSlotUtils.TUESDAY],
                counts[TimeSlotUtils.WEDNESDAY],
                counts[TimeSlotUtils.THURSDAY],
                counts[TimeSlotUtils.FRIDAY],
                counts[TimeSlotUtils.SATURDAY],
                counts[TimeSlotUtils.SUNDAY]
        );
    }
}
