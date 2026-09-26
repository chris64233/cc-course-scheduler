package com.coursescheduler.service;

import com.coursescheduler.model.CourseSchedule;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ScheduleQuerySupportTest {

    private CourseSchedule s(Long id, String course, String teacher, String classroom, String timeSlot) {
        return new CourseSchedule(id, course, teacher, classroom, timeSlot);
    }

    @Test
    void testFindDailySchedules_ClassroomMatch_FilteredAndSortedByStartTime() {
        List<CourseSchedule> schedules = Arrays.asList(
                s(1L, "数学", "张老师", "A101", "周一 10:00-12:00"),
                s(2L, "物理", "李老师", "A101", "周一 08:00-10:00"),
                s(3L, "化学", "王老师", "A101", "周二 08:00-10:00"),
                s(4L, "英语", "赵老师", "B202", "周一 08:00-10:00")
        );

        List<CourseSchedule> result = ScheduleQuerySupport.findDailySchedules(
                schedules, ScheduleQuerySupport.QueryType.CLASSROOM, "A101", 0
        );

        assertEquals(2, result.size());
        assertEquals("物理", result.get(0).getCourseName());
        assertEquals("周一 08:00-10:00", result.get(0).getTimeSlot());
        assertEquals("数学", result.get(1).getCourseName());
        assertEquals("周一 10:00-12:00", result.get(1).getTimeSlot());
    }

    @Test
    void testFindDailySchedules_TeacherMatch() {
        List<CourseSchedule> schedules = Arrays.asList(
                s(1L, "数学", "张老师", "A101", "周一 10:00-12:00"),
                s(2L, "物理", "张老师", "A102", "周一 08:00-10:00"),
                s(3L, "化学", "李老师", "A101", "周一 08:00-10:00")
        );

        List<CourseSchedule> result = ScheduleQuerySupport.findDailySchedules(
                schedules, ScheduleQuerySupport.QueryType.TEACHER, "张老师", 0
        );

        assertEquals(2, result.size());
        assertEquals("物理", result.get(0).getCourseName());
        assertEquals("数学", result.get(1).getCourseName());
    }

    @Test
    void testFindDailySchedules_EmptyInputList_ReturnsEmpty() {
        List<CourseSchedule> result = ScheduleQuerySupport.findDailySchedules(
                Collections.emptyList(), ScheduleQuerySupport.QueryType.CLASSROOM, "A101", 0
        );

        assertTrue(result.isEmpty());
    }

    @Test
    void testFindDailySchedules_NullInputList_ReturnsEmpty() {
        List<CourseSchedule> result = ScheduleQuerySupport.findDailySchedules(
                null, ScheduleQuerySupport.QueryType.CLASSROOM, "A101", 0
        );

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testFindDailySchedules_NoMatch_ReturnsEmpty() {
        List<CourseSchedule> schedules = Arrays.asList(
                s(1L, "数学", "张老师", "A101", "周一 10:00-12:00"),
                s(2L, "物理", "李老师", "A101", "周二 08:00-10:00")
        );

        List<CourseSchedule> result = ScheduleQuerySupport.findDailySchedules(
                schedules, ScheduleQuerySupport.QueryType.CLASSROOM, "B202", 0
        );

        assertTrue(result.isEmpty());
    }

    @Test
    void testFindDailySchedules_ExactMatchNotContains() {
        List<CourseSchedule> schedules = Collections.singletonList(
                s(1L, "数学", "张老师", "A101", "周一 10:00-12:00")
        );

        List<CourseSchedule> result = ScheduleQuerySupport.findDailySchedules(
                schedules, ScheduleQuerySupport.QueryType.CLASSROOM, "A10", 0
        );

        assertTrue(result.isEmpty());
    }

    @Test
    void testFindWeeklySchedules_ClassroomMatch_SortedByWeekdayThenStartTime() {
        List<CourseSchedule> schedules = Arrays.asList(
                s(1L, "英语", "赵老师", "A101", "周三 14:00-16:00"),
                s(2L, "数学", "张老师", "A101", "周一 10:00-12:00"),
                s(3L, "物理", "李老师", "A101", "周一 08:00-10:00"),
                s(4L, "化学", "王老师", "A101", "周二 08:00-10:00"),
                s(5L, "自习", "刘老师", "B202", "周一 08:00-10:00")
        );

        List<CourseSchedule> result = ScheduleQuerySupport.findWeeklySchedules(
                schedules, ScheduleQuerySupport.QueryType.CLASSROOM, "A101"
        );

        assertEquals(4, result.size());
        assertEquals("物理", result.get(0).getCourseName());
        assertEquals("周一 08:00-10:00", result.get(0).getTimeSlot());
        assertEquals("数学", result.get(1).getCourseName());
        assertEquals("周一 10:00-12:00", result.get(1).getTimeSlot());
        assertEquals("化学", result.get(2).getCourseName());
        assertEquals("周二 08:00-10:00", result.get(2).getTimeSlot());
        assertEquals("英语", result.get(3).getCourseName());
        assertEquals("周三 14:00-16:00", result.get(3).getTimeSlot());
    }

    @Test
    void testFindWeeklySchedules_TeacherMatch() {
        List<CourseSchedule> schedules = Arrays.asList(
                s(1L, "数学", "张老师", "A101", "周三 10:00-12:00"),
                s(2L, "物理", "张老师", "A102", "周一 08:00-10:00"),
                s(3L, "化学", "李老师", "A101", "周一 08:00-10:00")
        );

        List<CourseSchedule> result = ScheduleQuerySupport.findWeeklySchedules(
                schedules, ScheduleQuerySupport.QueryType.TEACHER, "张老师"
        );

        assertEquals(2, result.size());
        assertEquals("物理", result.get(0).getCourseName());
        assertEquals("数学", result.get(1).getCourseName());
    }

    @Test
    void testFindWeeklySchedules_SundayLast() {
        List<CourseSchedule> schedules = Arrays.asList(
                s(1L, "周日课", "赵老师", "A101", "周日 08:00-10:00"),
                s(2L, "周一课", "张老师", "A101", "周一 08:00-10:00"),
                s(3L, "周五课", "李老师", "A101", "周五 08:00-10:00")
        );

        List<CourseSchedule> result = ScheduleQuerySupport.findWeeklySchedules(
                schedules, ScheduleQuerySupport.QueryType.CLASSROOM, "A101"
        );

        assertEquals(3, result.size());
        assertEquals("周一课", result.get(0).getCourseName());
        assertEquals("周五课", result.get(1).getCourseName());
        assertEquals("周日课", result.get(2).getCourseName());
    }

    @Test
    void testFindWeeklySchedules_EmptyInputList_ReturnsEmpty() {
        List<CourseSchedule> result = ScheduleQuerySupport.findWeeklySchedules(
                Collections.emptyList(), ScheduleQuerySupport.QueryType.CLASSROOM, "A101"
        );

        assertTrue(result.isEmpty());
    }

    @Test
    void testFindWeeklySchedules_NullInputList_ReturnsEmpty() {
        List<CourseSchedule> result = ScheduleQuerySupport.findWeeklySchedules(
                null, ScheduleQuerySupport.QueryType.CLASSROOM, "A101"
        );

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testFindWeeklySchedules_NoMatch_ReturnsEmpty() {
        List<CourseSchedule> schedules = Collections.singletonList(
                s(1L, "数学", "张老师", "A101", "周一 10:00-12:00")
        );

        List<CourseSchedule> result = ScheduleQuerySupport.findWeeklySchedules(
                schedules, ScheduleQuerySupport.QueryType.CLASSROOM, "B202"
        );

        assertTrue(result.isEmpty());
    }

    @Test
    void testFindWeeklySchedules_SameDaySortedByStartTime() {
        List<CourseSchedule> schedules = Arrays.asList(
                s(1L, "第三课", "张老师", "A101", "周一 14:00-16:00"),
                s(2L, "第一课", "李老师", "A101", "周一 08:00-10:00"),
                s(3L, "第二课", "王老师", "A101", "周一 10:00-12:00")
        );

        List<CourseSchedule> result = ScheduleQuerySupport.findWeeklySchedules(
                schedules, ScheduleQuerySupport.QueryType.CLASSROOM, "A101"
        );

        assertEquals(3, result.size());
        assertEquals("第一课", result.get(0).getCourseName());
        assertEquals("第二课", result.get(1).getCourseName());
        assertEquals("第三课", result.get(2).getCourseName());
    }

    @Test
    void testFindDailySchedules_DoesNotMutateInputList() {
        List<CourseSchedule> schedules = new ArrayList<>(Arrays.asList(
                s(2L, "数学", "张老师", "A101", "周一 10:00-12:00"),
                s(1L, "物理", "李老师", "A101", "周一 08:00-10:00")
        ));

        ScheduleQuerySupport.findDailySchedules(
                schedules, ScheduleQuerySupport.QueryType.CLASSROOM, "A101", 0
        );

        assertEquals(2, schedules.size());
        assertEquals("数学", schedules.get(0).getCourseName());
        assertEquals("物理", schedules.get(1).getCourseName());
    }

    @Test
    void testFindWeeklySchedules_DoesNotMutateInputList() {
        List<CourseSchedule> schedules = new ArrayList<>(Arrays.asList(
                s(2L, "数学", "张老师", "A101", "周二 10:00-12:00"),
                s(1L, "物理", "李老师", "A101", "周一 08:00-10:00")
        ));

        ScheduleQuerySupport.findWeeklySchedules(
                schedules, ScheduleQuerySupport.QueryType.CLASSROOM, "A101"
        );

        assertEquals(2, schedules.size());
        assertEquals("数学", schedules.get(0).getCourseName());
        assertEquals("物理", schedules.get(1).getCourseName());
    }
}
