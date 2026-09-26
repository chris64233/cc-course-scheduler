package com.coursescheduler.service;

import com.coursescheduler.dto.CourseScheduleWeekdayStatisticsResponse;
import com.coursescheduler.model.CourseSchedule;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WeekdayStatisticsSupportTest {

    @Test
    void calculateStatistics_NullList_ReturnsAllZeros() {
        CourseScheduleWeekdayStatisticsResponse stats = WeekdayStatisticsSupport.calculateStatistics(null);

        assertEquals(0, stats.getMonday());
        assertEquals(0, stats.getTuesday());
        assertEquals(0, stats.getWednesday());
        assertEquals(0, stats.getThursday());
        assertEquals(0, stats.getFriday());
        assertEquals(0, stats.getSaturday());
        assertEquals(0, stats.getSunday());
    }

    @Test
    void calculateStatistics_EmptyList_ReturnsAllZeros() {
        List<CourseSchedule> schedules = Collections.emptyList();

        CourseScheduleWeekdayStatisticsResponse stats = WeekdayStatisticsSupport.calculateStatistics(schedules);

        assertEquals(0, stats.getMonday());
        assertEquals(0, stats.getTuesday());
        assertEquals(0, stats.getWednesday());
        assertEquals(0, stats.getThursday());
        assertEquals(0, stats.getFriday());
        assertEquals(0, stats.getSaturday());
        assertEquals(0, stats.getSunday());
    }

    @Test
    void calculateStatistics_MultipleWeekdays_CountsCorrectly() {
        List<CourseSchedule> schedules = Arrays.asList(
                new CourseSchedule(1L, "数学", "张老师", "A101", "周一 08:00-10:00"),
                new CourseSchedule(2L, "物理", "李老师", "B202", "周一 10:00-12:00"),
                new CourseSchedule(3L, "化学", "王老师", "C303", "周二 08:00-10:00"),
                new CourseSchedule(4L, "英语", "赵老师", "D404", "周三 14:00-16:00"),
                new CourseSchedule(5L, "生物", "孙老师", "E505", "周五 09:00-11:00")
        );

        CourseScheduleWeekdayStatisticsResponse stats = WeekdayStatisticsSupport.calculateStatistics(schedules);

        assertEquals(2, stats.getMonday());
        assertEquals(1, stats.getTuesday());
        assertEquals(1, stats.getWednesday());
        assertEquals(0, stats.getThursday());
        assertEquals(1, stats.getFriday());
        assertEquals(0, stats.getSaturday());
        assertEquals(0, stats.getSunday());
    }

    @Test
    void calculateStatistics_NonStandardFormat_NormalizedAndCounted() {
        List<CourseSchedule> schedules = Arrays.asList(
                new CourseSchedule(1L, "数学", "张老师", "A101", "星期一 8:00-10:00"),
                new CourseSchedule(2L, "物理", "李老师", "B202", "周1 08:00-10:00"),
                new CourseSchedule(3L, "化学", "王老师", "C303", "星期2 09:00-11:00"),
                new CourseSchedule(4L, "英语", "赵老师", "D404", "周2 14:00-16:00"),
                new CourseSchedule(5L, "生物", "孙老师", "E505", "星期日 10:00-12:00")
        );

        CourseScheduleWeekdayStatisticsResponse stats = WeekdayStatisticsSupport.calculateStatistics(schedules);

        assertEquals(2, stats.getMonday());
        assertEquals(2, stats.getTuesday());
        assertEquals(0, stats.getWednesday());
        assertEquals(0, stats.getThursday());
        assertEquals(0, stats.getFriday());
        assertEquals(0, stats.getSaturday());
        assertEquals(1, stats.getSunday());
    }

    @Test
    void calculateStatistics_OnePerDay_AllCountedCorrectly() {
        List<CourseSchedule> schedules = Arrays.asList(
                new CourseSchedule(1L, "周一课", "老师1", "A101", "周一 08:00-10:00"),
                new CourseSchedule(2L, "周二课", "老师2", "B202", "周二 08:00-10:00"),
                new CourseSchedule(3L, "周三课", "老师3", "C303", "周三 08:00-10:00"),
                new CourseSchedule(4L, "周四课", "老师4", "D404", "周四 08:00-10:00"),
                new CourseSchedule(5L, "周五课", "老师5", "E505", "周五 08:00-10:00"),
                new CourseSchedule(6L, "周六课", "老师6", "F606", "周六 08:00-10:00"),
                new CourseSchedule(7L, "周日课", "老师7", "G707", "周日 08:00-10:00")
        );

        CourseScheduleWeekdayStatisticsResponse stats = WeekdayStatisticsSupport.calculateStatistics(schedules);

        assertEquals(1, stats.getMonday());
        assertEquals(1, stats.getTuesday());
        assertEquals(1, stats.getWednesday());
        assertEquals(1, stats.getThursday());
        assertEquals(1, stats.getFriday());
        assertEquals(1, stats.getSaturday());
        assertEquals(1, stats.getSunday());
    }
}
