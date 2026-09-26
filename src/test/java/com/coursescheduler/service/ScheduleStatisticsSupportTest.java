package com.coursescheduler.service;

import com.coursescheduler.model.CourseSchedule;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ScheduleStatisticsSupportTest {

    private static final class NamedCount {
        private final String name;
        private final int count;

        NamedCount(String name, int count) {
            this.name = name;
            this.count = count;
        }

        String getName() { return name; }
        int getCount() { return count; }
    }

    private List<NamedCount> calc(List<CourseSchedule> schedules) {
        return ScheduleStatisticsSupport.calculateStatistics(
                schedules,
                CourseSchedule::getClassroom,
                NamedCount::new
        );
    }

    @Test
    void calculateStatistics_NullList_ReturnsEmptyList() {
        List<NamedCount> result = ScheduleStatisticsSupport.calculateStatistics(
                null,
                CourseSchedule::getClassroom,
                NamedCount::new
        );

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void calculateStatistics_EmptyList_ReturnsEmptyList() {
        List<CourseSchedule> schedules = Collections.emptyList();

        List<NamedCount> result = calc(schedules);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void calculateStatistics_SortedByCountDesc() {
        List<CourseSchedule> schedules = Arrays.asList(
                new CourseSchedule(1L, "数学", "张老师", "A101", "周一 08:00-10:00"),
                new CourseSchedule(2L, "物理", "李老师", "A101", "周二 08:00-10:00"),
                new CourseSchedule(3L, "化学", "王老师", "A101", "周三 08:00-10:00"),
                new CourseSchedule(4L, "英语", "赵老师", "B202", "周四 08:00-10:00"),
                new CourseSchedule(5L, "生物", "钱老师", "B202", "周五 08:00-10:00"),
                new CourseSchedule(6L, "历史", "孙老师", "C303", "周一 10:00-12:00")
        );

        List<NamedCount> result = calc(schedules);

        assertEquals(3, result.size());
        assertEquals("A101", result.get(0).getName());
        assertEquals(3, result.get(0).getCount());
        assertEquals("B202", result.get(1).getName());
        assertEquals(2, result.get(1).getCount());
        assertEquals("C303", result.get(2).getName());
        assertEquals(1, result.get(2).getCount());
    }

    @Test
    void calculateStatistics_SameCount_SortedByNameAsc() {
        List<CourseSchedule> schedules = Arrays.asList(
                new CourseSchedule(1L, "数学", "张老师", "C303", "周一 08:00-10:00"),
                new CourseSchedule(2L, "物理", "李老师", "A101", "周二 08:00-10:00"),
                new CourseSchedule(3L, "化学", "王老师", "B202", "周三 08:00-10:00")
        );

        List<NamedCount> result = calc(schedules);

        assertEquals(3, result.size());
        assertEquals("A101", result.get(0).getName());
        assertEquals(1, result.get(0).getCount());
        assertEquals("B202", result.get(1).getName());
        assertEquals(1, result.get(1).getCount());
        assertEquals("C303", result.get(2).getName());
        assertEquals(1, result.get(2).getCount());
    }

    @Test
    void calculateStatistics_DtoConstructor_UsedCorrectlyWithRightArguments() {
        List<CourseSchedule> schedules = Arrays.asList(
                new CourseSchedule(1L, "数学", "张老师", "A101", "周一 08:00-10:00"),
                new CourseSchedule(2L, "物理", "李老师", "A101", "周二 08:00-10:00"),
                new CourseSchedule(3L, "化学", "王老师", "B202", "周三 08:00-10:00")
        );

        List<String> constructorCalls = new ArrayList<>();
        AtomicInteger callCount = new AtomicInteger(0);

        List<NamedCount> result = ScheduleStatisticsSupport.calculateStatistics(
                schedules,
                CourseSchedule::getClassroom,
                (name, count) -> {
                    callCount.incrementAndGet();
                    constructorCalls.add(name + ":" + count);
                    return new NamedCount(name, count);
                }
        );

        assertEquals(2, callCount.get(), "不同分组数量应对应 DTO 构造调用次数");
        assertTrue(constructorCalls.contains("A101:2"), "A101 应被构造时传入数量 2");
        assertTrue(constructorCalls.contains("B202:1"), "B202 应被构造时传入数量 1");
        assertEquals(2, result.size());
    }

    @Test
    void calculateStatistics_MixedCountAndNameOrder() {
        List<CourseSchedule> schedules = Arrays.asList(
                new CourseSchedule(1L, "数学", "张老师", "D404", "周一 08:00-10:00"),
                new CourseSchedule(2L, "物理", "李老师", "B202", "周二 08:00-10:00"),
                new CourseSchedule(3L, "化学", "王老师", "A101", "周三 08:00-10:00"),
                new CourseSchedule(4L, "英语", "赵老师", "D404", "周四 08:00-10:00"),
                new CourseSchedule(5L, "生物", "钱老师", "A101", "周五 08:00-10:00"),
                new CourseSchedule(6L, "历史", "孙老师", "C303", "周一 10:00-12:00")
        );

        List<NamedCount> result = calc(schedules);

        assertEquals(4, result.size());
        assertEquals("A101", result.get(0).getName());
        assertEquals(2, result.get(0).getCount());
        assertEquals("D404", result.get(1).getName());
        assertEquals(2, result.get(1).getCount());
        assertEquals("B202", result.get(2).getName());
        assertEquals(1, result.get(2).getCount());
        assertEquals("C303", result.get(3).getName());
        assertEquals(1, result.get(3).getCount());
    }

    @Test
    void calculateStatistics_UsesSuppliedNameExtractor() {
        List<CourseSchedule> schedules = Arrays.asList(
                new CourseSchedule(1L, "数学", "张老师", "A101", "周一 08:00-10:00"),
                new CourseSchedule(2L, "数学", "李老师", "B202", "周二 08:00-10:00"),
                new CourseSchedule(3L, "物理", "王老师", "C303", "周三 08:00-10:00")
        );

        List<NamedCount> result = ScheduleStatisticsSupport.calculateStatistics(
                schedules,
                CourseSchedule::getCourseName,
                NamedCount::new
        );

        assertEquals(2, result.size());
        assertEquals("数学", result.get(0).getName());
        assertEquals(2, result.get(0).getCount());
        assertEquals("物理", result.get(1).getName());
        assertEquals(1, result.get(1).getCount());
    }
}
