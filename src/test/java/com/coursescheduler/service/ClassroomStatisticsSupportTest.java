package com.coursescheduler.service;

import com.coursescheduler.dto.ClassroomCourseStatisticsResponse;
import com.coursescheduler.dto.ClassroomFreeDaySummaryResponse;
import com.coursescheduler.dto.ClassroomWeeklySummaryResponse;
import com.coursescheduler.model.CourseSchedule;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ClassroomStatisticsSupportTest {

    @Test
    void calculateStatistics_NullList_ReturnsEmptyList() {
        List<ClassroomCourseStatisticsResponse> stats = ClassroomStatisticsSupport.calculateStatistics(null);

        assertNotNull(stats);
        assertTrue(stats.isEmpty());
    }

    @Test
    void calculateStatistics_EmptyList_ReturnsEmptyList() {
        List<CourseSchedule> schedules = Collections.emptyList();

        List<ClassroomCourseStatisticsResponse> stats = ClassroomStatisticsSupport.calculateStatistics(schedules);

        assertNotNull(stats);
        assertTrue(stats.isEmpty());
    }

    @Test
    void calculateStatistics_MultipleClassrooms_SortedByCountDesc() {
        List<CourseSchedule> schedules = Arrays.asList(
                new CourseSchedule(1L, "数学", "张老师", "A101", "周一 08:00-10:00"),
                new CourseSchedule(2L, "物理", "李老师", "A101", "周一 10:00-12:00"),
                new CourseSchedule(3L, "化学", "王老师", "A101", "周二 08:00-10:00"),
                new CourseSchedule(4L, "英语", "赵老师", "B202", "周三 14:00-16:00"),
                new CourseSchedule(5L, "生物", "钱老师", "B202", "周四 09:00-11:00"),
                new CourseSchedule(6L, "历史", "孙老师", "C303", "周五 08:00-10:00")
        );

        List<ClassroomCourseStatisticsResponse> stats = ClassroomStatisticsSupport.calculateStatistics(schedules);

        assertEquals(3, stats.size());
        assertEquals("A101", stats.get(0).getClassroom());
        assertEquals(3, stats.get(0).getCourseCount());
        assertEquals("B202", stats.get(1).getClassroom());
        assertEquals(2, stats.get(1).getCourseCount());
        assertEquals("C303", stats.get(2).getClassroom());
        assertEquals(1, stats.get(2).getCourseCount());
    }

    @Test
    void calculateStatistics_SameCount_SortedByClassroomNameAsc() {
        List<CourseSchedule> schedules = Arrays.asList(
                new CourseSchedule(1L, "数学", "张老师", "C303", "周一 08:00-10:00"),
                new CourseSchedule(2L, "物理", "李老师", "A101", "周二 08:00-10:00"),
                new CourseSchedule(3L, "化学", "王老师", "B202", "周三 08:00-10:00")
        );

        List<ClassroomCourseStatisticsResponse> stats = ClassroomStatisticsSupport.calculateStatistics(schedules);

        assertEquals(3, stats.size());
        assertEquals("A101", stats.get(0).getClassroom());
        assertEquals(1, stats.get(0).getCourseCount());
        assertEquals("B202", stats.get(1).getClassroom());
        assertEquals(1, stats.get(1).getCourseCount());
        assertEquals("C303", stats.get(2).getClassroom());
        assertEquals(1, stats.get(2).getCourseCount());
    }

    @Test
    void calculateStatistics_SameClassroom_CoursesMergedIntoOneEntry() {
        List<CourseSchedule> schedules = Arrays.asList(
                new CourseSchedule(1L, "数学", "张老师", "A101", "周一 08:00-10:00"),
                new CourseSchedule(2L, "物理", "李老师", "A101", "周二 08:00-10:00"),
                new CourseSchedule(3L, "化学", "王老师", "A101", "周三 08:00-10:00")
        );

        List<ClassroomCourseStatisticsResponse> stats = ClassroomStatisticsSupport.calculateStatistics(schedules);

        assertEquals(1, stats.size());
        assertEquals("A101", stats.get(0).getClassroom());
        assertEquals(3, stats.get(0).getCourseCount());
    }

    @Test
    void calculateStatistics_MixedCountAndSortOrder() {
        List<CourseSchedule> schedules = Arrays.asList(
                new CourseSchedule(1L, "数学", "张老师", "B202", "周一 08:00-10:00"),
                new CourseSchedule(2L, "物理", "李老师", "A101", "周二 08:00-10:00"),
                new CourseSchedule(3L, "化学", "王老师", "A101", "周三 08:00-10:00"),
                new CourseSchedule(4L, "英语", "赵老师", "C303", "周四 08:00-10:00"),
                new CourseSchedule(5L, "生物", "钱老师", "C303", "周五 08:00-10:00"),
                new CourseSchedule(6L, "历史", "孙老师", "D404", "周一 10:00-12:00"),
                new CourseSchedule(7L, "地理", "周老师", "D404", "周二 10:00-12:00")
        );

        List<ClassroomCourseStatisticsResponse> stats = ClassroomStatisticsSupport.calculateStatistics(schedules);

        assertEquals(4, stats.size());
        assertEquals("A101", stats.get(0).getClassroom());
        assertEquals(2, stats.get(0).getCourseCount());
        assertEquals("C303", stats.get(1).getClassroom());
        assertEquals(2, stats.get(1).getCourseCount());
        assertEquals("D404", stats.get(2).getClassroom());
        assertEquals(2, stats.get(2).getCourseCount());
        assertEquals("B202", stats.get(3).getClassroom());
        assertEquals(1, stats.get(3).getCourseCount());
    }

    @Test
    void calculateWeeklySummary_NoCourses_ReturnsZeroSummary() {
        List<CourseSchedule> schedules = Collections.emptyList();

        ClassroomWeeklySummaryResponse summary = ClassroomStatisticsSupport.calculateWeeklySummary("A101", schedules);

        assertEquals("A101", summary.getClassroom());
        assertEquals(0, summary.getTotalCourses());
        assertEquals(0, summary.getOccupiedDays());
        assertNull(summary.getBusiestDay());
        assertNotNull(summary.getTeacherNames());
        assertTrue(summary.getTeacherNames().isEmpty());
    }

    @Test
    void calculateWeeklySummary_NoCoursesForClassroom_ReturnsZeroSummary() {
        List<CourseSchedule> schedules = Arrays.asList(
                new CourseSchedule(1L, "数学", "张老师", "B202", "周一 08:00-10:00"),
                new CourseSchedule(2L, "物理", "李老师", "B202", "周二 08:00-10:00")
        );

        ClassroomWeeklySummaryResponse summary = ClassroomStatisticsSupport.calculateWeeklySummary("A101", schedules);

        assertEquals("A101", summary.getClassroom());
        assertEquals(0, summary.getTotalCourses());
        assertEquals(0, summary.getOccupiedDays());
        assertNull(summary.getBusiestDay());
        assertTrue(summary.getTeacherNames().isEmpty());
    }

    @Test
    void calculateWeeklySummary_MultipleDays_CorrectOccupiedDaysAndBusiestDay() {
        List<CourseSchedule> schedules = Arrays.asList(
                new CourseSchedule(1L, "数学", "张老师", "A101", "周一 08:00-10:00"),
                new CourseSchedule(2L, "物理", "李老师", "A101", "周一 10:00-12:00"),
                new CourseSchedule(3L, "化学", "王老师", "A101", "周二 08:00-10:00"),
                new CourseSchedule(4L, "英语", "赵老师", "A101", "周三 14:00-16:00"),
                new CourseSchedule(5L, "生物", "钱老师", "A101", "周三 16:00-18:00")
        );

        ClassroomWeeklySummaryResponse summary = ClassroomStatisticsSupport.calculateWeeklySummary("A101", schedules);

        assertEquals("A101", summary.getClassroom());
        assertEquals(5, summary.getTotalCourses());
        assertEquals(3, summary.getOccupiedDays());
        assertEquals("周一", summary.getBusiestDay());
        assertEquals(Arrays.asList("张老师", "李老师", "王老师", "赵老师", "钱老师"), summary.getTeacherNames());
    }

    @Test
    void calculateWeeklySummary_SingleBusiestDay_MaxOnWednesday() {
        List<CourseSchedule> schedules = Arrays.asList(
                new CourseSchedule(1L, "数学", "张老师", "A101", "周一 08:00-10:00"),
                new CourseSchedule(2L, "物理", "李老师", "A101", "周三 08:00-10:00"),
                new CourseSchedule(3L, "化学", "王老师", "A101", "周三 10:00-12:00"),
                new CourseSchedule(4L, "英语", "赵老师", "A101", "周三 14:00-16:00")
        );

        ClassroomWeeklySummaryResponse summary = ClassroomStatisticsSupport.calculateWeeklySummary("A101", schedules);

        assertEquals(4, summary.getTotalCourses());
        assertEquals(2, summary.getOccupiedDays());
        assertEquals("周三", summary.getBusiestDay());
    }

    @Test
    void calculateWeeklySummary_TieBusiestDay_PicksEarlierWeekday() {
        List<CourseSchedule> schedules = Arrays.asList(
                new CourseSchedule(1L, "数学", "张老师", "A101", "周一 08:00-10:00"),
                new CourseSchedule(2L, "物理", "李老师", "A101", "周一 10:00-12:00"),
                new CourseSchedule(3L, "化学", "王老师", "A101", "周三 08:00-10:00"),
                new CourseSchedule(4L, "英语", "赵老师", "A101", "周三 10:00-12:00"),
                new CourseSchedule(5L, "生物", "钱老师", "A101", "周五 08:00-10:00"),
                new CourseSchedule(6L, "历史", "孙老师", "A101", "周五 10:00-12:00")
        );

        ClassroomWeeklySummaryResponse summary = ClassroomStatisticsSupport.calculateWeeklySummary("A101", schedules);

        assertEquals(6, summary.getTotalCourses());
        assertEquals(3, summary.getOccupiedDays());
        assertEquals("周一", summary.getBusiestDay());
    }

    @Test
    void calculateWeeklySummary_TeacherNamesDeduped_PreservesCreationOrder() {
        List<CourseSchedule> schedules = Arrays.asList(
                new CourseSchedule(1L, "数学", "张老师", "A101", "周一 08:00-10:00"),
                new CourseSchedule(2L, "物理", "李老师", "A101", "周二 08:00-10:00"),
                new CourseSchedule(3L, "化学", "张老师", "A101", "周三 08:00-10:00"),
                new CourseSchedule(4L, "英语", "王老师", "A101", "周四 08:00-10:00"),
                new CourseSchedule(5L, "生物", "李老师", "A101", "周五 08:00-10:00")
        );

        ClassroomWeeklySummaryResponse summary = ClassroomStatisticsSupport.calculateWeeklySummary("A101", schedules);

        assertEquals(5, summary.getTotalCourses());
        assertEquals(Arrays.asList("张老师", "李老师", "王老师"), summary.getTeacherNames());
    }

    @Test
    void calculateWeeklySummary_SundayIncluded_CountedCorrectly() {
        List<CourseSchedule> schedules = Arrays.asList(
                new CourseSchedule(1L, "数学", "张老师", "A101", "周六 08:00-10:00"),
                new CourseSchedule(2L, "物理", "李老师", "A101", "周日 08:00-10:00"),
                new CourseSchedule(3L, "化学", "王老师", "A101", "周日 10:00-12:00")
        );

        ClassroomWeeklySummaryResponse summary = ClassroomStatisticsSupport.calculateWeeklySummary("A101", schedules);

        assertEquals(3, summary.getTotalCourses());
        assertEquals(2, summary.getOccupiedDays());
        assertEquals("周日", summary.getBusiestDay());
    }

    @Test
    void calculateWeeklySummary_ListContainsNullSchedules_SkippedGracefully() {
        List<CourseSchedule> schedules = Arrays.asList(
                new CourseSchedule(1L, "数学", "张老师", "A101", "周一 08:00-10:00"),
                null,
                new CourseSchedule(2L, "物理", "李老师", "A101", "周二 08:00-10:00"),
                null
        );

        ClassroomWeeklySummaryResponse summary = ClassroomStatisticsSupport.calculateWeeklySummary("A101", schedules);

        assertEquals(2, summary.getTotalCourses());
        assertEquals(2, summary.getOccupiedDays());
        assertEquals("周一", summary.getBusiestDay());
        assertEquals(Arrays.asList("张老师", "李老师"), summary.getTeacherNames());
    }

    @Test
    void calculateWeeklySummary_NullTeacherName_FilteredFromResult() {
        List<CourseSchedule> schedules = Arrays.asList(
                new CourseSchedule(1L, "数学", null, "A101", "周一 08:00-10:00"),
                new CourseSchedule(2L, "物理", "李老师", "A101", "周二 08:00-10:00"),
                new CourseSchedule(3L, "化学", null, "A101", "周三 08:00-10:00")
        );

        ClassroomWeeklySummaryResponse summary = ClassroomStatisticsSupport.calculateWeeklySummary("A101", schedules);

        assertEquals(3, summary.getTotalCourses());
        assertEquals(3, summary.getOccupiedDays());
        assertEquals(Arrays.asList("李老师"), summary.getTeacherNames());
    }

    @Test
    void calculateWeeklySummary_NullTimeSlot_EntireCourseSkipped() {
        List<CourseSchedule> schedules = Arrays.asList(
                new CourseSchedule(1L, "数学", "张老师", "A101", null),
                new CourseSchedule(2L, "物理", "李老师", "A101", "周一 08:00-10:00"),
                new CourseSchedule(3L, "化学", "王老师", "A101", "周二 08:00-10:00"),
                new CourseSchedule(4L, "英语", "赵老师", "A101", null)
        );

        ClassroomWeeklySummaryResponse summary = ClassroomStatisticsSupport.calculateWeeklySummary("A101", schedules);

        assertEquals(2, summary.getTotalCourses());
        assertEquals(2, summary.getOccupiedDays());
        assertEquals("周一", summary.getBusiestDay());
        assertEquals(Arrays.asList("李老师", "王老师"), summary.getTeacherNames());
    }

    @Test
    void calculateWeeklySummary_NonNormalizedTimeSlot_StillParsedCorrectly() {
        List<CourseSchedule> schedules = Arrays.asList(
                new CourseSchedule(1L, "数学", "张老师", "A101", "星期一 8:00-10:00"),
                new CourseSchedule(2L, "物理", "李老师", "A101", " 周2  09:00-11:00  "),
                new CourseSchedule(3L, "化学", "王老师", "A101", "周1  14:00-16:00")
        );

        ClassroomWeeklySummaryResponse summary = ClassroomStatisticsSupport.calculateWeeklySummary("A101", schedules);

        assertEquals(3, summary.getTotalCourses());
        assertEquals(2, summary.getOccupiedDays());
        assertEquals("周一", summary.getBusiestDay());
        assertEquals(Arrays.asList("张老师", "李老师", "王老师"), summary.getTeacherNames());
    }

    @Test
    void calculateWeeklySummary_AllNullTimeSlot_AllSkipped() {
        List<CourseSchedule> schedules = Arrays.asList(
                new CourseSchedule(1L, "数学", "张老师", "A101", null),
                new CourseSchedule(2L, "物理", "李老师", "A101", null)
        );

        ClassroomWeeklySummaryResponse summary = ClassroomStatisticsSupport.calculateWeeklySummary("A101", schedules);

        assertEquals(0, summary.getTotalCourses());
        assertEquals(0, summary.getOccupiedDays());
        assertNull(summary.getBusiestDay());
        assertTrue(summary.getTeacherNames().isEmpty());
    }

    @Test
    void calculateWeeklySummary_InvalidTimeSlotFormat_EntireCourseSkipped() {
        List<CourseSchedule> schedules = Arrays.asList(
                new CourseSchedule(1L, "数学", "张老师", "A101", "完全不合法的字符串"),
                new CourseSchedule(2L, "物理", "李老师", "A101", "周一 08:00-10:00"),
                new CourseSchedule(3L, "化学", "王老师", "A101", "周八 08:00-10:00"),
                new CourseSchedule(4L, "英语", "赵老师", "A101", "周一 25:00-26:00"),
                new CourseSchedule(5L, "生物", "钱老师", "A101", "周二 09:00-11:00")
        );

        ClassroomWeeklySummaryResponse summary = ClassroomStatisticsSupport.calculateWeeklySummary("A101", schedules);

        assertEquals(2, summary.getTotalCourses());
        assertEquals(2, summary.getOccupiedDays());
        assertEquals("周一", summary.getBusiestDay());
        assertEquals(Arrays.asList("李老师", "钱老师"), summary.getTeacherNames());
    }

    @Test
    void calculateWeeklySummary_MixedDirtyData_OnlyValidCoursesCounted() {
        List<CourseSchedule> schedules = Arrays.asList(
                null,
                new CourseSchedule(1L, "数学", "张老师", "A101", null),
                new CourseSchedule(2L, "物理", "李老师", "A101", "周一 08:00-10:00"),
                new CourseSchedule(3L, "化学", "王老师", "B202", "周一 08:00-10:00"),
                null,
                new CourseSchedule(4L, "英语", "赵老师", "A101", "乱码时间"),
                new CourseSchedule(5L, "生物", "钱老师", "A101", "周二 09:00-11:00"),
                new CourseSchedule(6L, "历史", null, "A101", "周三 14:00-16:00")
        );

        ClassroomWeeklySummaryResponse summary = ClassroomStatisticsSupport.calculateWeeklySummary("A101", schedules);

        assertEquals(3, summary.getTotalCourses());
        assertEquals(3, summary.getOccupiedDays());
        assertEquals("周一", summary.getBusiestDay());
        assertEquals(Arrays.asList("李老师", "钱老师"), summary.getTeacherNames());
    }

    @Test
    void noArgConstructor_GetTeacherNames_NoNPE() {
        ClassroomWeeklySummaryResponse response = new ClassroomWeeklySummaryResponse();

        assertNotNull(response.getTeacherNames());
        assertTrue(response.getTeacherNames().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> response.getTeacherNames().add("张老师"));
    }

    @Test
    void getTeacherNames_ReturnsDefensiveCopy_ModificationsNotAllowed() {
        List<CourseSchedule> schedules = Arrays.asList(
                new CourseSchedule(1L, "数学", "张老师", "A101", "周一 08:00-10:00"),
                new CourseSchedule(2L, "物理", "李老师", "A101", "周二 08:00-10:00")
        );

        ClassroomWeeklySummaryResponse summary = ClassroomStatisticsSupport.calculateWeeklySummary("A101", schedules);
        List<String> teacherNames = summary.getTeacherNames();

        assertThrows(UnsupportedOperationException.class, () -> teacherNames.add("王老师"));
        assertThrows(UnsupportedOperationException.class, () -> teacherNames.remove(0));
    }

    @Test
    void setTeacherNames_Null_BecomesEmptyList() {
        ClassroomWeeklySummaryResponse response = new ClassroomWeeklySummaryResponse();
        response.setTeacherNames(null);

        assertNotNull(response.getTeacherNames());
        assertTrue(response.getTeacherNames().isEmpty());
    }

    @Test
    void setTeacherNames_ExternalListMutated_InternalStateUnchanged() {
        ClassroomWeeklySummaryResponse response = new ClassroomWeeklySummaryResponse();
        List<String> externalList = new java.util.ArrayList<>();
        externalList.add("张老师");
        externalList.add("李老师");

        response.setTeacherNames(externalList);
        externalList.add("王老师");

        assertEquals(2, response.getTeacherNames().size());
        assertEquals(Arrays.asList("张老师", "李老师"), response.getTeacherNames());
    }

    @Test
    void setTeacherNames_GetterReturnsImmutable_UnsupportedOnMutation() {
        ClassroomWeeklySummaryResponse response = new ClassroomWeeklySummaryResponse();
        response.setTeacherNames(Arrays.asList("张老师", "李老师"));

        assertThrows(UnsupportedOperationException.class, () -> response.getTeacherNames().add("王老师"));
        assertThrows(UnsupportedOperationException.class, () -> response.getTeacherNames().remove(0));
    }

    @Test
    void constructor_TeacherNamesDefensivelyCopied_ExternalMutationDoesNotAffectResponse() {
        List<String> externalList = new java.util.ArrayList<>();
        externalList.add("张老师");
        externalList.add("李老师");

        ClassroomWeeklySummaryResponse response = new ClassroomWeeklySummaryResponse(
                "A101", 2, 1, "周一", externalList);

        externalList.add("王老师");

        assertEquals(2, response.getTeacherNames().size());
        assertEquals(Arrays.asList("张老师", "李老师"), response.getTeacherNames());
    }

    @Test
    void constructor_NullTeacherNames_BecomesEmptyList() {
        ClassroomWeeklySummaryResponse response = new ClassroomWeeklySummaryResponse(
                "A101", 0, 0, null, null);

        assertNotNull(response.getTeacherNames());
        assertTrue(response.getTeacherNames().isEmpty());
    }

    @Test
    void calculateFreeDaySummary_NullList_AllDaysFree() {
        ClassroomFreeDaySummaryResponse summary =
                ClassroomStatisticsSupport.calculateFreeDaySummary("A101", null);

        assertEquals("A101", summary.getClassroom());
        assertTrue(summary.getBusyDays().isEmpty());
        assertEquals(7, summary.getFreeDays().size());
        assertEquals(7, summary.getFreeDayCount());
        assertEquals(Arrays.asList("周一", "周二", "周三", "周四", "周五", "周六", "周日"),
                summary.getFreeDays());
    }

    @Test
    void calculateFreeDaySummary_EmptyList_AllDaysFree() {
        List<CourseSchedule> schedules = Collections.emptyList();

        ClassroomFreeDaySummaryResponse summary =
                ClassroomStatisticsSupport.calculateFreeDaySummary("A101", schedules);

        assertEquals("A101", summary.getClassroom());
        assertTrue(summary.getBusyDays().isEmpty());
        assertEquals(7, summary.getFreeDays().size());
        assertEquals(7, summary.getFreeDayCount());
    }

    @Test
    void calculateFreeDaySummary_NoCoursesForClassroom_AllDaysFree() {
        List<CourseSchedule> schedules = Arrays.asList(
                new CourseSchedule(1L, "数学", "张老师", "B202", "周一 08:00-10:00"),
                new CourseSchedule(2L, "物理", "李老师", "B202", "周二 08:00-10:00")
        );

        ClassroomFreeDaySummaryResponse summary =
                ClassroomStatisticsSupport.calculateFreeDaySummary("A101", schedules);

        assertEquals("A101", summary.getClassroom());
        assertTrue(summary.getBusyDays().isEmpty());
        assertEquals(7, summary.getFreeDayCount());
    }

    @Test
    void calculateFreeDaySummary_PartialDays_CorrectBusyAndFree() {
        List<CourseSchedule> schedules = Arrays.asList(
                new CourseSchedule(1L, "数学", "张老师", "A101", "周一 08:00-10:00"),
                new CourseSchedule(2L, "物理", "李老师", "A101", "周一 10:00-12:00"),
                new CourseSchedule(3L, "化学", "王老师", "A101", "周三 08:00-10:00"),
                new CourseSchedule(4L, "英语", "赵老师", "A101", "周五 14:00-16:00")
        );

        ClassroomFreeDaySummaryResponse summary =
                ClassroomStatisticsSupport.calculateFreeDaySummary("A101", schedules);

        assertEquals("A101", summary.getClassroom());
        assertEquals(Arrays.asList("周一", "周三", "周五"), summary.getBusyDays());
        assertEquals(Arrays.asList("周二", "周四", "周六", "周日"), summary.getFreeDays());
        assertEquals(4, summary.getFreeDayCount());
    }

    @Test
    void calculateFreeDaySummary_AllSevenDaysOccupied_NoFreeDays() {
        List<CourseSchedule> schedules = Arrays.asList(
                new CourseSchedule(1L, "周一", "张", "A101", "周一 08:00-10:00"),
                new CourseSchedule(2L, "周二", "李", "A101", "周二 08:00-10:00"),
                new CourseSchedule(3L, "周三", "王", "A101", "周三 08:00-10:00"),
                new CourseSchedule(4L, "周四", "赵", "A101", "周四 08:00-10:00"),
                new CourseSchedule(5L, "周五", "钱", "A101", "周五 08:00-10:00"),
                new CourseSchedule(6L, "周六", "孙", "A101", "周六 08:00-10:00"),
                new CourseSchedule(7L, "周日", "周", "A101", "周日 08:00-10:00")
        );

        ClassroomFreeDaySummaryResponse summary =
                ClassroomStatisticsSupport.calculateFreeDaySummary("A101", schedules);

        assertEquals("A101", summary.getClassroom());
        assertEquals(Arrays.asList("周一", "周二", "周三", "周四", "周五", "周六", "周日"),
                summary.getBusyDays());
        assertTrue(summary.getFreeDays().isEmpty());
        assertEquals(0, summary.getFreeDayCount());
    }

    @Test
    void calculateFreeDaySummary_InvalidTimeSlots_SkippedAndNotCounted() {
        List<CourseSchedule> schedules = Arrays.asList(
                new CourseSchedule(1L, "数学", "张老师", "A101", null),
                new CourseSchedule(2L, "物理", "李老师", "A101", "周八 08:00-10:00"),
                new CourseSchedule(3L, "化学", "王老师", "A101", "周二 08:00-10:00"),
                new CourseSchedule(4L, "英语", "赵老师", "A101", "完全不合法的时间段"),
                new CourseSchedule(5L, "生物", "钱老师", "A101", "周四 09:00-11:00"),
                new CourseSchedule(6L, "历史", "孙老师", "A101", "周一 25:00-26:00")
        );

        ClassroomFreeDaySummaryResponse summary =
                ClassroomStatisticsSupport.calculateFreeDaySummary("A101", schedules);

        assertEquals(Arrays.asList("周二", "周四"), summary.getBusyDays());
        assertEquals(Arrays.asList("周一", "周三", "周五", "周六", "周日"), summary.getFreeDays());
        assertEquals(5, summary.getFreeDayCount());
    }

    @Test
    void calculateFreeDaySummary_BusyDaysSorted_MondayToSundayOrder() {
        List<CourseSchedule> schedules = Arrays.asList(
                new CourseSchedule(1L, "周日", "张老师", "A101", "周日 08:00-10:00"),
                new CourseSchedule(2L, "周三", "李老师", "A101", "周三 08:00-10:00"),
                new CourseSchedule(3L, "周一", "王老师", "A101", "周一 08:00-10:00"),
                new CourseSchedule(4L, "周六", "赵老师", "A101", "周六 08:00-10:00")
        );

        ClassroomFreeDaySummaryResponse summary =
                ClassroomStatisticsSupport.calculateFreeDaySummary("A101", schedules);

        assertEquals(Arrays.asList("周一", "周三", "周六", "周日"), summary.getBusyDays());
        assertEquals(Arrays.asList("周二", "周四", "周五"), summary.getFreeDays());
    }

    @Test
    void calculateFreeDaySummary_FreeDaysSorted_MondayToSundayOrder() {
        List<CourseSchedule> schedules = Arrays.asList(
                new CourseSchedule(1L, "周一", "张老师", "A101", "周一 08:00-10:00"),
                new CourseSchedule(2L, "周三", "李老师", "A101", "周三 08:00-10:00"),
                new CourseSchedule(3L, "周日", "王老师", "A101", "周日 08:00-10:00")
        );

        ClassroomFreeDaySummaryResponse summary =
                ClassroomStatisticsSupport.calculateFreeDaySummary("A101", schedules);

        assertEquals(Arrays.asList("周二", "周四", "周五", "周六"), summary.getFreeDays());
        assertEquals(4, summary.getFreeDayCount());
    }

    @Test
    void calculateFreeDaySummary_OtherClassroomsCourses_NotIncluded() {
        List<CourseSchedule> schedules = Arrays.asList(
                new CourseSchedule(1L, "数学", "张老师", "A101", "周一 08:00-10:00"),
                new CourseSchedule(2L, "物理", "李老师", "B202", "周二 08:00-10:00"),
                new CourseSchedule(3L, "化学", "王老师", "C303", "周三 08:00-10:00"),
                new CourseSchedule(4L, "英语", "赵老师", "A101", "周四 08:00-10:00")
        );

        ClassroomFreeDaySummaryResponse summary =
                ClassroomStatisticsSupport.calculateFreeDaySummary("A101", schedules);

        assertEquals(Arrays.asList("周一", "周四"), summary.getBusyDays());
        assertEquals(Arrays.asList("周二", "周三", "周五", "周六", "周日"), summary.getFreeDays());
        assertEquals(5, summary.getFreeDayCount());
    }

    @Test
    void calculateFreeDaySummary_SundayIncluded_CountedAsLast() {
        List<CourseSchedule> schedules = Arrays.asList(
                new CourseSchedule(1L, "周六课", "张老师", "A101", "周六 08:00-10:00"),
                new CourseSchedule(2L, "周日课", "李老师", "A101", "周日 08:00-10:00")
        );

        ClassroomFreeDaySummaryResponse summary =
                ClassroomStatisticsSupport.calculateFreeDaySummary("A101", schedules);

        assertEquals(Arrays.asList("周六", "周日"), summary.getBusyDays());
        assertEquals(5, summary.getFreeDayCount());
        assertFalse(summary.getFreeDays().contains("周六"));
        assertFalse(summary.getFreeDays().contains("周日"));
    }

    @Test
    void calculateFreeDaySummary_BusyDaysImmutable_ThrowsOnModify() {
        List<CourseSchedule> schedules = Arrays.asList(
                new CourseSchedule(1L, "数学", "张老师", "A101", "周一 08:00-10:00")
        );

        ClassroomFreeDaySummaryResponse summary =
                ClassroomStatisticsSupport.calculateFreeDaySummary("A101", schedules);

        assertThrows(UnsupportedOperationException.class, () -> summary.getBusyDays().add("周二"));
        assertThrows(UnsupportedOperationException.class, () -> summary.getBusyDays().remove(0));
    }

    @Test
    void calculateFreeDaySummary_FreeDaysImmutable_ThrowsOnModify() {
        List<CourseSchedule> schedules = Arrays.asList(
                new CourseSchedule(1L, "数学", "张老师", "A101", "周一 08:00-10:00")
        );

        ClassroomFreeDaySummaryResponse summary =
                ClassroomStatisticsSupport.calculateFreeDaySummary("A101", schedules);

        assertThrows(UnsupportedOperationException.class, () -> summary.getFreeDays().add("周一"));
        assertThrows(UnsupportedOperationException.class, () -> summary.getFreeDays().remove(0));
    }

    @Test
    void calculateFreeDaySummary_MixedDirtyData_OnlyValidCounted() {
        List<CourseSchedule> schedules = Arrays.asList(
                null,
                new CourseSchedule(1L, "数学", "张老师", "A101", null),
                new CourseSchedule(2L, "物理", "李老师", "A101", "周一 08:00-10:00"),
                new CourseSchedule(3L, "化学", "王老师", "B202", "周二 08:00-10:00"),
                null,
                new CourseSchedule(4L, "英语", "赵老师", "A101", "乱码时间"),
                new CourseSchedule(5L, "生物", "钱老师", "A101", "周三 09:00-11:00")
        );

        ClassroomFreeDaySummaryResponse summary =
                ClassroomStatisticsSupport.calculateFreeDaySummary("A101", schedules);

        assertEquals(Arrays.asList("周一", "周三"), summary.getBusyDays());
        assertEquals(Arrays.asList("周二", "周四", "周五", "周六", "周日"), summary.getFreeDays());
        assertEquals(5, summary.getFreeDayCount());
    }

    @Test
    void freeDaySummary_noArgConstructor_NoNPE() {
        ClassroomFreeDaySummaryResponse response = new ClassroomFreeDaySummaryResponse();

        assertNotNull(response.getBusyDays());
        assertNotNull(response.getFreeDays());
        assertTrue(response.getBusyDays().isEmpty());
        assertTrue(response.getFreeDays().isEmpty());
        assertEquals(0, response.getFreeDayCount());
    }

    @Test
    void freeDaySummary_constructor_NullLists_BecomesEmpty() {
        ClassroomFreeDaySummaryResponse response = new ClassroomFreeDaySummaryResponse(
                "A101", null, null);

        assertNotNull(response.getBusyDays());
        assertNotNull(response.getFreeDays());
        assertTrue(response.getBusyDays().isEmpty());
        assertTrue(response.getFreeDays().isEmpty());
        assertEquals(0, response.getFreeDayCount());
    }

    @Test
    void freeDaySummary_setFreeDays_UpdatesFreeDayCount_ViaConstructor() {
        ClassroomFreeDaySummaryResponse response = new ClassroomFreeDaySummaryResponse(
                "A101", Collections.emptyList(), Arrays.asList("周一", "周二", "周三"));

        assertEquals(3, response.getFreeDayCount());
        assertEquals(Arrays.asList("周一", "周二", "周三"), response.getFreeDays());
    }

    @Test
    void freeDaySummary_setBusyDays_DoesNotAffectFreeDayCount_ViaConstructor() {
        ClassroomFreeDaySummaryResponse response = new ClassroomFreeDaySummaryResponse(
                "A101", Arrays.asList("周三", "周四", "周五"), Arrays.asList("周一", "周二"));

        assertEquals(2, response.getFreeDayCount());
        assertEquals(Arrays.asList("周三", "周四", "周五"), response.getBusyDays());
    }

    @Test
    void freeDaySummary_setFreeDays_Null_BecomesEmpty_ViaConstructor() {
        ClassroomFreeDaySummaryResponse response = new ClassroomFreeDaySummaryResponse(
                "A101", Arrays.asList("周一", "周二"), null);

        assertTrue(response.getFreeDays().isEmpty());
        assertEquals(0, response.getFreeDayCount());
    }

    @Test
    void freeDaySummary_setBusyDays_Null_BecomesEmpty_ViaConstructor() {
        ClassroomFreeDaySummaryResponse response = new ClassroomFreeDaySummaryResponse(
                "A101", null, Arrays.asList("周一"));

        assertTrue(response.getBusyDays().isEmpty());
    }
}
