package com.coursescheduler.service;

import com.coursescheduler.dto.TeacherConsecutiveBusyDaysResponse;
import com.coursescheduler.dto.TeacherCourseStatisticsResponse;
import com.coursescheduler.dto.TeacherFreeDaySummaryResponse;
import com.coursescheduler.dto.TeacherWeeklySummaryResponse;
import com.coursescheduler.model.CourseSchedule;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TeacherStatisticsSupportTest {

    @Test
    void calculateStatistics_NullList_ReturnsEmptyList() {
        List<TeacherCourseStatisticsResponse> stats = TeacherStatisticsSupport.calculateStatistics(null);

        assertNotNull(stats);
        assertTrue(stats.isEmpty());
    }

    @Test
    void calculateStatistics_EmptyList_ReturnsEmptyList() {
        List<CourseSchedule> schedules = Collections.emptyList();

        List<TeacherCourseStatisticsResponse> stats = TeacherStatisticsSupport.calculateStatistics(schedules);

        assertNotNull(stats);
        assertTrue(stats.isEmpty());
    }

    @Test
    void calculateStatistics_MultipleTeachers_SortedByCountDesc() {
        List<CourseSchedule> schedules = Arrays.asList(
                new CourseSchedule(1L, "数学", "张老师", "A101", "周一 08:00-10:00"),
                new CourseSchedule(2L, "物理", "张老师", "B202", "周一 10:00-12:00"),
                new CourseSchedule(3L, "化学", "张老师", "C303", "周二 08:00-10:00"),
                new CourseSchedule(4L, "英语", "李老师", "D404", "周三 14:00-16:00"),
                new CourseSchedule(5L, "生物", "李老师", "E505", "周四 09:00-11:00"),
                new CourseSchedule(6L, "历史", "王老师", "F606", "周五 08:00-10:00")
        );

        List<TeacherCourseStatisticsResponse> stats = TeacherStatisticsSupport.calculateStatistics(schedules);

        assertEquals(3, stats.size());
        assertEquals("张老师", stats.get(0).getTeacherName());
        assertEquals(3, stats.get(0).getCourseCount());
        assertEquals("李老师", stats.get(1).getTeacherName());
        assertEquals(2, stats.get(1).getCourseCount());
        assertEquals("王老师", stats.get(2).getTeacherName());
        assertEquals(1, stats.get(2).getCourseCount());
    }

    @Test
    void calculateStatistics_SameCount_SortedByNameAsc() {
        List<CourseSchedule> schedules = Arrays.asList(
                new CourseSchedule(1L, "数学", "张老师", "A101", "周一 08:00-10:00"),
                new CourseSchedule(2L, "物理", "李老师", "B202", "周二 08:00-10:00"),
                new CourseSchedule(3L, "化学", "王老师", "C303", "周三 08:00-10:00")
        );

        List<TeacherCourseStatisticsResponse> stats = TeacherStatisticsSupport.calculateStatistics(schedules);

        assertEquals(3, stats.size());
        assertEquals("张老师", stats.get(0).getTeacherName());
        assertEquals(1, stats.get(0).getCourseCount());
        assertEquals("李老师", stats.get(1).getTeacherName());
        assertEquals(1, stats.get(1).getCourseCount());
        assertEquals("王老师", stats.get(2).getTeacherName());
        assertEquals(1, stats.get(2).getCourseCount());
    }

    @Test
    void calculateStatistics_SameTeacher_CoursesMergedIntoOneEntry() {
        List<CourseSchedule> schedules = Arrays.asList(
                new CourseSchedule(1L, "数学", "张老师", "A101", "周一 08:00-10:00"),
                new CourseSchedule(2L, "物理", "张老师", "B202", "周二 08:00-10:00"),
                new CourseSchedule(3L, "化学", "张老师", "C303", "周三 08:00-10:00")
        );

        List<TeacherCourseStatisticsResponse> stats = TeacherStatisticsSupport.calculateStatistics(schedules);

        assertEquals(1, stats.size());
        assertEquals("张老师", stats.get(0).getTeacherName());
        assertEquals(3, stats.get(0).getCourseCount());
    }

    @Test
    void calculateWeeklySummary_NoCourses_ReturnsZeroSummary() {
        List<CourseSchedule> schedules = Collections.emptyList();

        TeacherWeeklySummaryResponse summary = TeacherStatisticsSupport.calculateWeeklySummary("张老师", schedules);

        assertEquals("张老师", summary.getTeacherName());
        assertEquals(0, summary.getTotalCourses());
        assertEquals(0, summary.getOccupiedDays());
        assertNull(summary.getBusiestDay());
        assertNotNull(summary.getCourseNames());
        assertTrue(summary.getCourseNames().isEmpty());
    }

    @Test
    void calculateWeeklySummary_NoCoursesForTeacher_ReturnsZeroSummary() {
        List<CourseSchedule> schedules = Arrays.asList(
                new CourseSchedule(1L, "数学", "李老师", "A101", "周一 08:00-10:00"),
                new CourseSchedule(2L, "物理", "李老师", "B202", "周二 08:00-10:00")
        );

        TeacherWeeklySummaryResponse summary = TeacherStatisticsSupport.calculateWeeklySummary("张老师", schedules);

        assertEquals("张老师", summary.getTeacherName());
        assertEquals(0, summary.getTotalCourses());
        assertEquals(0, summary.getOccupiedDays());
        assertNull(summary.getBusiestDay());
        assertTrue(summary.getCourseNames().isEmpty());
    }

    @Test
    void calculateWeeklySummary_MultipleDays_CorrectOccupiedDaysAndBusiestDay() {
        List<CourseSchedule> schedules = Arrays.asList(
                new CourseSchedule(1L, "数学", "张老师", "A101", "周一 08:00-10:00"),
                new CourseSchedule(2L, "物理", "张老师", "B202", "周一 10:00-12:00"),
                new CourseSchedule(3L, "化学", "张老师", "C303", "周二 08:00-10:00"),
                new CourseSchedule(4L, "英语", "张老师", "D404", "周三 14:00-16:00"),
                new CourseSchedule(5L, "生物", "张老师", "E505", "周三 16:00-18:00")
        );

        TeacherWeeklySummaryResponse summary = TeacherStatisticsSupport.calculateWeeklySummary("张老师", schedules);

        assertEquals("张老师", summary.getTeacherName());
        assertEquals(5, summary.getTotalCourses());
        assertEquals(3, summary.getOccupiedDays());
        assertEquals("周一", summary.getBusiestDay());
        assertEquals(Arrays.asList("数学", "物理", "化学", "英语", "生物"), summary.getCourseNames());
    }

    @Test
    void calculateWeeklySummary_SingleBusiestDay_MaxOnWednesday() {
        List<CourseSchedule> schedules = Arrays.asList(
                new CourseSchedule(1L, "数学", "张老师", "A101", "周一 08:00-10:00"),
                new CourseSchedule(2L, "物理", "张老师", "B202", "周三 08:00-10:00"),
                new CourseSchedule(3L, "化学", "张老师", "C303", "周三 10:00-12:00"),
                new CourseSchedule(4L, "英语", "张老师", "D404", "周三 14:00-16:00")
        );

        TeacherWeeklySummaryResponse summary = TeacherStatisticsSupport.calculateWeeklySummary("张老师", schedules);

        assertEquals(4, summary.getTotalCourses());
        assertEquals(2, summary.getOccupiedDays());
        assertEquals("周三", summary.getBusiestDay());
    }

    @Test
    void calculateWeeklySummary_TieBusiestDay_PicksEarlierWeekday() {
        List<CourseSchedule> schedules = Arrays.asList(
                new CourseSchedule(1L, "数学", "张老师", "A101", "周一 08:00-10:00"),
                new CourseSchedule(2L, "物理", "张老师", "B202", "周一 10:00-12:00"),
                new CourseSchedule(3L, "化学", "张老师", "C303", "周三 08:00-10:00"),
                new CourseSchedule(4L, "英语", "张老师", "D404", "周三 10:00-12:00"),
                new CourseSchedule(5L, "生物", "张老师", "E505", "周五 08:00-10:00"),
                new CourseSchedule(6L, "历史", "张老师", "F606", "周五 10:00-12:00")
        );

        TeacherWeeklySummaryResponse summary = TeacherStatisticsSupport.calculateWeeklySummary("张老师", schedules);

        assertEquals(6, summary.getTotalCourses());
        assertEquals(3, summary.getOccupiedDays());
        assertEquals("周一", summary.getBusiestDay());
    }

    @Test
    void calculateWeeklySummary_CourseNamesDeduped_PreservesCreationOrder() {
        List<CourseSchedule> schedules = Arrays.asList(
                new CourseSchedule(1L, "数学", "张老师", "A101", "周一 08:00-10:00"),
                new CourseSchedule(2L, "物理", "张老师", "B202", "周二 08:00-10:00"),
                new CourseSchedule(3L, "数学", "张老师", "A101", "周三 08:00-10:00"),
                new CourseSchedule(4L, "化学", "张老师", "C303", "周四 08:00-10:00"),
                new CourseSchedule(5L, "物理", "张老师", "B202", "周五 08:00-10:00")
        );

        TeacherWeeklySummaryResponse summary = TeacherStatisticsSupport.calculateWeeklySummary("张老师", schedules);

        assertEquals(5, summary.getTotalCourses());
        assertEquals(Arrays.asList("数学", "物理", "化学"), summary.getCourseNames());
    }

    @Test
    void calculateWeeklySummary_SundayIncluded_CountedCorrectly() {
        List<CourseSchedule> schedules = Arrays.asList(
                new CourseSchedule(1L, "数学", "张老师", "A101", "周六 08:00-10:00"),
                new CourseSchedule(2L, "物理", "张老师", "B202", "周日 08:00-10:00"),
                new CourseSchedule(3L, "化学", "张老师", "C303", "周日 10:00-12:00")
        );

        TeacherWeeklySummaryResponse summary = TeacherStatisticsSupport.calculateWeeklySummary("张老师", schedules);

        assertEquals(3, summary.getTotalCourses());
        assertEquals(2, summary.getOccupiedDays());
        assertEquals("周日", summary.getBusiestDay());
    }

    @Test
    void calculateWeeklySummary_ListContainsNullSchedules_SkippedGracefully() {
        List<CourseSchedule> schedules = Arrays.asList(
                new CourseSchedule(1L, "数学", "张老师", "A101", "周一 08:00-10:00"),
                null,
                new CourseSchedule(2L, "物理", "张老师", "B202", "周二 08:00-10:00"),
                null
        );

        TeacherWeeklySummaryResponse summary = TeacherStatisticsSupport.calculateWeeklySummary("张老师", schedules);

        assertEquals(2, summary.getTotalCourses());
        assertEquals(2, summary.getOccupiedDays());
        assertEquals("周一", summary.getBusiestDay());
        assertEquals(Arrays.asList("数学", "物理"), summary.getCourseNames());
    }

    @Test
    void calculateWeeklySummary_NullCourseName_FilteredFromResult() {
        List<CourseSchedule> schedules = Arrays.asList(
                new CourseSchedule(1L, null, "张老师", "A101", "周一 08:00-10:00"),
                new CourseSchedule(2L, "物理", "张老师", "B202", "周二 08:00-10:00"),
                new CourseSchedule(3L, null, "张老师", "C303", "周三 08:00-10:00")
        );

        TeacherWeeklySummaryResponse summary = TeacherStatisticsSupport.calculateWeeklySummary("张老师", schedules);

        assertEquals(3, summary.getTotalCourses());
        assertEquals(3, summary.getOccupiedDays());
        assertEquals(Arrays.asList("物理"), summary.getCourseNames());
    }

    @Test
    void calculateWeeklySummary_NullTimeSlot_EntireCourseSkipped() {
        List<CourseSchedule> schedules = Arrays.asList(
                new CourseSchedule(1L, "数学", "张老师", "A101", null),
                new CourseSchedule(2L, "物理", "张老师", "B202", "周一 08:00-10:00"),
                new CourseSchedule(3L, "化学", "张老师", "C303", "周二 08:00-10:00"),
                new CourseSchedule(4L, "英语", "张老师", "D404", null)
        );

        TeacherWeeklySummaryResponse summary = TeacherStatisticsSupport.calculateWeeklySummary("张老师", schedules);

        assertEquals(2, summary.getTotalCourses());
        assertEquals(2, summary.getOccupiedDays());
        assertEquals("周一", summary.getBusiestDay());
        assertEquals(Arrays.asList("物理", "化学"), summary.getCourseNames());
    }

    @Test
    void calculateWeeklySummary_NonNormalizedTimeSlot_StillParsedCorrectly() {
        List<CourseSchedule> schedules = Arrays.asList(
                new CourseSchedule(1L, "数学", "张老师", "A101", "星期一 8:00-10:00"),
                new CourseSchedule(2L, "物理", "张老师", "B202", " 周2  09:00-11:00  "),
                new CourseSchedule(3L, "化学", "张老师", "C303", "周1  14:00-16:00")
        );

        TeacherWeeklySummaryResponse summary = TeacherStatisticsSupport.calculateWeeklySummary("张老师", schedules);

        assertEquals(3, summary.getTotalCourses());
        assertEquals(2, summary.getOccupiedDays());
        assertEquals("周一", summary.getBusiestDay());
        assertEquals(Arrays.asList("数学", "物理", "化学"), summary.getCourseNames());
    }

    @Test
    void calculateWeeklySummary_AllNullTimeSlot_AllSkipped() {
        List<CourseSchedule> schedules = Arrays.asList(
                new CourseSchedule(1L, "数学", "张老师", "A101", null),
                new CourseSchedule(2L, "物理", "张老师", "B202", null)
        );

        TeacherWeeklySummaryResponse summary = TeacherStatisticsSupport.calculateWeeklySummary("张老师", schedules);

        assertEquals(0, summary.getTotalCourses());
        assertEquals(0, summary.getOccupiedDays());
        assertNull(summary.getBusiestDay());
        assertTrue(summary.getCourseNames().isEmpty());
    }

    @Test
    void calculateWeeklySummary_InvalidTimeSlotFormat_EntireCourseSkipped() {
        List<CourseSchedule> schedules = Arrays.asList(
                new CourseSchedule(1L, "数学", "张老师", "A101", "完全不合法的字符串"),
                new CourseSchedule(2L, "物理", "张老师", "B202", "周一 08:00-10:00"),
                new CourseSchedule(3L, "化学", "张老师", "C303", "周八 08:00-10:00"),
                new CourseSchedule(4L, "英语", "张老师", "D404", "周一 25:00-26:00"),
                new CourseSchedule(5L, "生物", "张老师", "E505", "周二 09:00-11:00")
        );

        TeacherWeeklySummaryResponse summary = TeacherStatisticsSupport.calculateWeeklySummary("张老师", schedules);

        assertEquals(2, summary.getTotalCourses());
        assertEquals(2, summary.getOccupiedDays());
        assertEquals("周一", summary.getBusiestDay());
        assertEquals(Arrays.asList("物理", "生物"), summary.getCourseNames());
    }

    @Test
    void calculateWeeklySummary_MixedDirtyData_OnlyValidCoursesCounted() {
        List<CourseSchedule> schedules = Arrays.asList(
                null,
                new CourseSchedule(1L, "数学", "张老师", "A101", null),
                new CourseSchedule(2L, "物理", "张老师", "B202", "周一 08:00-10:00"),
                new CourseSchedule(3L, "化学", "李老师", "C303", "周一 08:00-10:00"),
                null,
                new CourseSchedule(4L, "英语", "张老师", "D404", "乱码时间"),
                new CourseSchedule(5L, "生物", "张老师", "E505", "周二 09:00-11:00"),
                new CourseSchedule(6L, null, "张老师", "F606", "周三 14:00-16:00")
        );

        TeacherWeeklySummaryResponse summary = TeacherStatisticsSupport.calculateWeeklySummary("张老师", schedules);

        assertEquals(3, summary.getTotalCourses());
        assertEquals(3, summary.getOccupiedDays());
        assertEquals("周一", summary.getBusiestDay());
        assertEquals(Arrays.asList("物理", "生物"), summary.getCourseNames());
    }

    @Test
    void noArgConstructor_GetCourseNames_NoNPE() {
        TeacherWeeklySummaryResponse response = new TeacherWeeklySummaryResponse();

        assertNotNull(response.getCourseNames());
        assertTrue(response.getCourseNames().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> response.getCourseNames().add("数学"));
    }

    @Test
    void getCourseNames_ReturnsDefensiveCopy_ModificationsNotAllowed() {
        List<CourseSchedule> schedules = Arrays.asList(
                new CourseSchedule(1L, "数学", "张老师", "A101", "周一 08:00-10:00"),
                new CourseSchedule(2L, "物理", "张老师", "B202", "周二 08:00-10:00")
        );

        TeacherWeeklySummaryResponse summary = TeacherStatisticsSupport.calculateWeeklySummary("张老师", schedules);
        List<String> courseNames = summary.getCourseNames();

        assertThrows(UnsupportedOperationException.class, () -> courseNames.add("化学"));
        assertThrows(UnsupportedOperationException.class, () -> courseNames.remove(0));
    }

    @Test
    void setCourseNames_Null_BecomesEmptyList() {
        TeacherWeeklySummaryResponse response = new TeacherWeeklySummaryResponse();
        response.setCourseNames(null);

        assertNotNull(response.getCourseNames());
        assertTrue(response.getCourseNames().isEmpty());
    }

    @Test
    void setCourseNames_ExternalListMutated_InternalStateUnchanged() {
        TeacherWeeklySummaryResponse response = new TeacherWeeklySummaryResponse();
        List<String> externalList = new java.util.ArrayList<>();
        externalList.add("数学");
        externalList.add("物理");

        response.setCourseNames(externalList);
        externalList.add("化学");

        assertEquals(2, response.getCourseNames().size());
        assertEquals(Arrays.asList("数学", "物理"), response.getCourseNames());
    }

    @Test
    void setCourseNames_GetterReturnsImmutable_UnsupportedOnMutation() {
        TeacherWeeklySummaryResponse response = new TeacherWeeklySummaryResponse();
        response.setCourseNames(Arrays.asList("数学", "物理"));

        assertThrows(UnsupportedOperationException.class, () -> response.getCourseNames().add("化学"));
        assertThrows(UnsupportedOperationException.class, () -> response.getCourseNames().remove(0));
    }

    @Test
    void constructor_CourseNamesDefensivelyCopied_ExternalMutationDoesNotAffectResponse() {
        List<String> externalList = new java.util.ArrayList<>();
        externalList.add("数学");
        externalList.add("物理");

        TeacherWeeklySummaryResponse response = new TeacherWeeklySummaryResponse(
                "张老师", 2, 1, "周一", externalList);

        externalList.add("化学");

        assertEquals(2, response.getCourseNames().size());
        assertEquals(Arrays.asList("数学", "物理"), response.getCourseNames());
    }

    @Test
    void constructor_NullCourseNames_BecomesEmptyList() {
        TeacherWeeklySummaryResponse response = new TeacherWeeklySummaryResponse(
                "张老师", 0, 0, null, null);

        assertNotNull(response.getCourseNames());
        assertTrue(response.getCourseNames().isEmpty());
    }

    @Test
    void calculateFreeDaySummary_NullList_AllDaysFree() {
        TeacherFreeDaySummaryResponse summary =
                TeacherStatisticsSupport.calculateFreeDaySummary("张老师", null);

        assertEquals("张老师", summary.getTeacherName());
        assertTrue(summary.getBusyDays().isEmpty());
        assertEquals(7, summary.getFreeDays().size());
        assertEquals(7, summary.getFreeDayCount());
        assertEquals(Arrays.asList("周一", "周二", "周三", "周四", "周五", "周六", "周日"),
                summary.getFreeDays());
    }

    @Test
    void calculateFreeDaySummary_EmptyList_AllDaysFree() {
        List<CourseSchedule> schedules = Collections.emptyList();

        TeacherFreeDaySummaryResponse summary =
                TeacherStatisticsSupport.calculateFreeDaySummary("张老师", schedules);

        assertEquals("张老师", summary.getTeacherName());
        assertTrue(summary.getBusyDays().isEmpty());
        assertEquals(7, summary.getFreeDays().size());
        assertEquals(7, summary.getFreeDayCount());
    }

    @Test
    void calculateFreeDaySummary_NoCoursesForTeacher_AllDaysFree() {
        List<CourseSchedule> schedules = Arrays.asList(
                new CourseSchedule(1L, "数学", "李老师", "A101", "周一 08:00-10:00"),
                new CourseSchedule(2L, "物理", "李老师", "B202", "周二 08:00-10:00")
        );

        TeacherFreeDaySummaryResponse summary =
                TeacherStatisticsSupport.calculateFreeDaySummary("张老师", schedules);

        assertEquals("张老师", summary.getTeacherName());
        assertTrue(summary.getBusyDays().isEmpty());
        assertEquals(7, summary.getFreeDayCount());
    }

    @Test
    void calculateFreeDaySummary_PartialDays_CorrectBusyAndFree() {
        List<CourseSchedule> schedules = Arrays.asList(
                new CourseSchedule(1L, "数学", "张老师", "A101", "周一 08:00-10:00"),
                new CourseSchedule(2L, "物理", "张老师", "B202", "周一 10:00-12:00"),
                new CourseSchedule(3L, "化学", "张老师", "C303", "周三 08:00-10:00"),
                new CourseSchedule(4L, "英语", "张老师", "D404", "周五 14:00-16:00")
        );

        TeacherFreeDaySummaryResponse summary =
                TeacherStatisticsSupport.calculateFreeDaySummary("张老师", schedules);

        assertEquals("张老师", summary.getTeacherName());
        assertEquals(Arrays.asList("周一", "周三", "周五"), summary.getBusyDays());
        assertEquals(Arrays.asList("周二", "周四", "周六", "周日"), summary.getFreeDays());
        assertEquals(4, summary.getFreeDayCount());
    }

    @Test
    void calculateFreeDaySummary_AllSevenDaysOccupied_NoFreeDays() {
        List<CourseSchedule> schedules = Arrays.asList(
                new CourseSchedule(1L, "周一", "张", "A101", "周一 08:00-10:00"),
                new CourseSchedule(2L, "周二", "张", "A101", "周二 08:00-10:00"),
                new CourseSchedule(3L, "周三", "张", "A101", "周三 08:00-10:00"),
                new CourseSchedule(4L, "周四", "张", "A101", "周四 08:00-10:00"),
                new CourseSchedule(5L, "周五", "张", "A101", "周五 08:00-10:00"),
                new CourseSchedule(6L, "周六", "张", "A101", "周六 08:00-10:00"),
                new CourseSchedule(7L, "周日", "张", "A101", "周日 08:00-10:00")
        );

        TeacherFreeDaySummaryResponse summary =
                TeacherStatisticsSupport.calculateFreeDaySummary("张", schedules);

        assertEquals("张", summary.getTeacherName());
        assertEquals(Arrays.asList("周一", "周二", "周三", "周四", "周五", "周六", "周日"),
                summary.getBusyDays());
        assertTrue(summary.getFreeDays().isEmpty());
        assertEquals(0, summary.getFreeDayCount());
    }

    @Test
    void calculateFreeDaySummary_InvalidTimeSlots_SkippedAndNotCounted() {
        List<CourseSchedule> schedules = Arrays.asList(
                new CourseSchedule(1L, "数学", "张老师", "A101", null),
                new CourseSchedule(2L, "物理", "张老师", "B202", "周八 08:00-10:00"),
                new CourseSchedule(3L, "化学", "张老师", "C303", "周二 08:00-10:00"),
                new CourseSchedule(4L, "英语", "张老师", "D404", "完全不合法的时间段"),
                new CourseSchedule(5L, "生物", "张老师", "E505", "周四 09:00-11:00"),
                new CourseSchedule(6L, "历史", "张老师", "F606", "周一 25:00-26:00")
        );

        TeacherFreeDaySummaryResponse summary =
                TeacherStatisticsSupport.calculateFreeDaySummary("张老师", schedules);

        assertEquals(Arrays.asList("周二", "周四"), summary.getBusyDays());
        assertEquals(Arrays.asList("周一", "周三", "周五", "周六", "周日"), summary.getFreeDays());
        assertEquals(5, summary.getFreeDayCount());
    }

    @Test
    void calculateFreeDaySummary_BusyDaysSorted_MondayToSundayOrder() {
        List<CourseSchedule> schedules = Arrays.asList(
                new CourseSchedule(1L, "周日", "张老师", "A101", "周日 08:00-10:00"),
                new CourseSchedule(2L, "周三", "张老师", "B202", "周三 08:00-10:00"),
                new CourseSchedule(3L, "周一", "张老师", "C303", "周一 08:00-10:00"),
                new CourseSchedule(4L, "周六", "张老师", "D404", "周六 08:00-10:00")
        );

        TeacherFreeDaySummaryResponse summary =
                TeacherStatisticsSupport.calculateFreeDaySummary("张老师", schedules);

        assertEquals(Arrays.asList("周一", "周三", "周六", "周日"), summary.getBusyDays());
        assertEquals(Arrays.asList("周二", "周四", "周五"), summary.getFreeDays());
    }

    @Test
    void calculateFreeDaySummary_FreeDaysSorted_MondayToSundayOrder() {
        List<CourseSchedule> schedules = Arrays.asList(
                new CourseSchedule(1L, "周一", "张老师", "A101", "周一 08:00-10:00"),
                new CourseSchedule(2L, "周三", "张老师", "B202", "周三 08:00-10:00"),
                new CourseSchedule(3L, "周日", "张老师", "C303", "周日 08:00-10:00")
        );

        TeacherFreeDaySummaryResponse summary =
                TeacherStatisticsSupport.calculateFreeDaySummary("张老师", schedules);

        assertEquals(Arrays.asList("周二", "周四", "周五", "周六"), summary.getFreeDays());
        assertEquals(4, summary.getFreeDayCount());
    }

    @Test
    void calculateFreeDaySummary_OtherTeachersCourses_NotIncluded() {
        List<CourseSchedule> schedules = Arrays.asList(
                new CourseSchedule(1L, "数学", "张老师", "A101", "周一 08:00-10:00"),
                new CourseSchedule(2L, "物理", "李老师", "B202", "周二 08:00-10:00"),
                new CourseSchedule(3L, "化学", "王老师", "C303", "周三 08:00-10:00"),
                new CourseSchedule(4L, "英语", "张老师", "D404", "周四 08:00-10:00")
        );

        TeacherFreeDaySummaryResponse summary =
                TeacherStatisticsSupport.calculateFreeDaySummary("张老师", schedules);

        assertEquals(Arrays.asList("周一", "周四"), summary.getBusyDays());
        assertEquals(Arrays.asList("周二", "周三", "周五", "周六", "周日"), summary.getFreeDays());
        assertEquals(5, summary.getFreeDayCount());
    }

    @Test
    void calculateFreeDaySummary_SundayIncluded_CountedAsLast() {
        List<CourseSchedule> schedules = Arrays.asList(
                new CourseSchedule(1L, "周六课", "张老师", "A101", "周六 08:00-10:00"),
                new CourseSchedule(2L, "周日课", "张老师", "B202", "周日 08:00-10:00")
        );

        TeacherFreeDaySummaryResponse summary =
                TeacherStatisticsSupport.calculateFreeDaySummary("张老师", schedules);

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

        TeacherFreeDaySummaryResponse summary =
                TeacherStatisticsSupport.calculateFreeDaySummary("张老师", schedules);

        assertThrows(UnsupportedOperationException.class, () -> summary.getBusyDays().add("周二"));
        assertThrows(UnsupportedOperationException.class, () -> summary.getBusyDays().remove(0));
    }

    @Test
    void calculateFreeDaySummary_FreeDaysImmutable_ThrowsOnModify() {
        List<CourseSchedule> schedules = Arrays.asList(
                new CourseSchedule(1L, "数学", "张老师", "A101", "周一 08:00-10:00")
        );

        TeacherFreeDaySummaryResponse summary =
                TeacherStatisticsSupport.calculateFreeDaySummary("张老师", schedules);

        assertThrows(UnsupportedOperationException.class, () -> summary.getFreeDays().add("周一"));
        assertThrows(UnsupportedOperationException.class, () -> summary.getFreeDays().remove(0));
    }

    @Test
    void calculateFreeDaySummary_MixedDirtyData_OnlyValidCounted() {
        List<CourseSchedule> schedules = Arrays.asList(
                null,
                new CourseSchedule(1L, "数学", "张老师", "A101", null),
                new CourseSchedule(2L, "物理", "张老师", "B202", "周一 08:00-10:00"),
                new CourseSchedule(3L, "化学", "李老师", "C303", "周二 08:00-10:00"),
                null,
                new CourseSchedule(4L, "英语", "张老师", "D404", "乱码时间"),
                new CourseSchedule(5L, "生物", "张老师", "E505", "周三 09:00-11:00")
        );

        TeacherFreeDaySummaryResponse summary =
                TeacherStatisticsSupport.calculateFreeDaySummary("张老师", schedules);

        assertEquals(Arrays.asList("周一", "周三"), summary.getBusyDays());
        assertEquals(Arrays.asList("周二", "周四", "周五", "周六", "周日"), summary.getFreeDays());
        assertEquals(5, summary.getFreeDayCount());
    }

    @Test
    void noArgConstructor_FreeDaySummary_NoNPE() {
        TeacherFreeDaySummaryResponse response = new TeacherFreeDaySummaryResponse();

        assertNotNull(response.getBusyDays());
        assertNotNull(response.getFreeDays());
        assertTrue(response.getBusyDays().isEmpty());
        assertTrue(response.getFreeDays().isEmpty());
        assertEquals(0, response.getFreeDayCount());
    }

    @Test
    void constructor_NullLists_BecomesEmpty() {
        TeacherFreeDaySummaryResponse response = new TeacherFreeDaySummaryResponse(
                "张老师", null, null);

        assertNotNull(response.getBusyDays());
        assertNotNull(response.getFreeDays());
        assertTrue(response.getBusyDays().isEmpty());
        assertTrue(response.getFreeDays().isEmpty());
        assertEquals(0, response.getFreeDayCount());
    }

    @Test
    void constructor_AllArg_FreeDayCountMatches() {
        TeacherFreeDaySummaryResponse response = new TeacherFreeDaySummaryResponse(
                "张",
                Arrays.asList("周一", "周三", "周五"),
                Arrays.asList("周二", "周四", "周六", "周日")
        );

        assertEquals(4, response.getFreeDayCount());
        assertEquals(response.getFreeDays().size(), response.getFreeDayCount());
        assertEquals(Arrays.asList("周一", "周三", "周五"), response.getBusyDays());
        assertEquals(Arrays.asList("周二", "周四", "周六", "周日"), response.getFreeDays());
    }

    @Test
    void constructor_NoArg_DefaultEmpty() {
        TeacherFreeDaySummaryResponse response = new TeacherFreeDaySummaryResponse();

        assertEquals(0, response.getFreeDayCount());
        assertTrue(response.getBusyDays().isEmpty());
        assertTrue(response.getFreeDays().isEmpty());
    }

    @Test
    void dtoImmutableCopy_SimpleCopyPreservesAllElements() {
        TeacherFreeDaySummaryResponse response = new TeacherFreeDaySummaryResponse(
                "张",
                Arrays.asList("周一", "周三", "周一", "周二"),
                Arrays.asList("周四", "周五", "周四")
        );

        assertEquals(Arrays.asList("周一", "周三", "周一", "周二"), response.getBusyDays());
        assertEquals(Arrays.asList("周四", "周五", "周四"), response.getFreeDays());
        assertEquals(3, response.getFreeDayCount());
    }

    @Test
    void dtoImmutableCopy_PreservesNullAndBlank() {
        TeacherFreeDaySummaryResponse response = new TeacherFreeDaySummaryResponse(
                "张",
                Arrays.asList("周一", null, "", "  ", "\t", "周三"),
                Arrays.asList(null, "   ", "周二", "周四")
        );

        assertEquals(Arrays.asList("周一", null, "", "  ", "\t", "周三"), response.getBusyDays());
        assertEquals(Arrays.asList(null, "   ", "周二", "周四"), response.getFreeDays());
        assertEquals(4, response.getFreeDayCount());
    }

    @Test
    void dtoImmutableCopy_NullBecomesEmptyList() {
        TeacherFreeDaySummaryResponse response = new TeacherFreeDaySummaryResponse(
                "张",
                null,
                null
        );

        assertTrue(response.getBusyDays().isEmpty());
        assertTrue(response.getFreeDays().isEmpty());
        assertEquals(0, response.getFreeDayCount());
    }

    @Test
    void dtoImmutableCopy_EmptyBecomesEmptyList() {
        TeacherFreeDaySummaryResponse response = new TeacherFreeDaySummaryResponse(
                "张",
                Collections.emptyList(),
                Collections.emptyList()
        );

        assertTrue(response.getBusyDays().isEmpty());
        assertTrue(response.getFreeDays().isEmpty());
        assertEquals(0, response.getFreeDayCount());
    }

    @Test
    void dtoImmutableCopy_PreservesOrder() {
        TeacherFreeDaySummaryResponse response = new TeacherFreeDaySummaryResponse(
                "张",
                Arrays.asList("周日", "周三", "周一", "周六"),
                Arrays.asList("周五", "周二", "周四")
        );

        assertEquals(Arrays.asList("周日", "周三", "周一", "周六"), response.getBusyDays());
        assertEquals(Arrays.asList("周五", "周二", "周四"), response.getFreeDays());
    }

    @Test
    void dtoImmutableCopy_ReturnsImmutableList() {
        TeacherFreeDaySummaryResponse response = new TeacherFreeDaySummaryResponse(
                "张",
                Arrays.asList("周一", "周三"),
                Arrays.asList("周二")
        );

        assertThrows(UnsupportedOperationException.class, () -> response.getBusyDays().add("周四"));
        assertThrows(UnsupportedOperationException.class, () -> response.getFreeDays().add("周五"));
        assertThrows(UnsupportedOperationException.class, () -> response.getBusyDays().remove(0));
        assertThrows(UnsupportedOperationException.class, () -> response.getFreeDays().remove(0));
        assertThrows(UnsupportedOperationException.class, () -> response.getBusyDays().set(0, "周二"));
        assertThrows(UnsupportedOperationException.class, () -> response.getFreeDays().clear());
    }

    @Test
    void constructorWithVariousSizes_FreeDayCountAlwaysMatches() {
        TeacherFreeDaySummaryResponse r0 = new TeacherFreeDaySummaryResponse(
                "张", null, null);
        assertEquals(0, r0.getFreeDayCount());
        assertEquals(r0.getFreeDays().size(), r0.getFreeDayCount());

        TeacherFreeDaySummaryResponse r1 = new TeacherFreeDaySummaryResponse(
                "张", Collections.emptyList(), Collections.emptyList());
        assertEquals(0, r1.getFreeDayCount());

        TeacherFreeDaySummaryResponse r2 = new TeacherFreeDaySummaryResponse(
                "张",
                Arrays.asList("周一"),
                Arrays.asList("周二", "周三", "周四", "周五", "周六", "周日"));
        assertEquals(6, r2.getFreeDayCount());
        assertEquals(r2.getFreeDays().size(), r2.getFreeDayCount());

        TeacherFreeDaySummaryResponse r3 = new TeacherFreeDaySummaryResponse(
                "张",
                Arrays.asList("周一", "周二", "周三", "周四", "周五", "周六", "周日"),
                Collections.emptyList());
        assertEquals(0, r3.getFreeDayCount());
    }

    @Test
    void partitionWeekdays_InvalidNegativeIndex_Throws() {
        Set<Integer> invalid = Collections.singleton(-1);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                FreeDaySummarySupport.partitionWeekdays(invalid)
        );
        assertTrue(ex.getMessage().contains("-1"));
        assertTrue(ex.getMessage().contains("0~6"));
    }

    @Test
    void partitionWeekdays_InvalidTooLargeIndex_Throws() {
        Set<Integer> invalid = Collections.singleton(7);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                FreeDaySummarySupport.partitionWeekdays(invalid)
        );
        assertTrue(ex.getMessage().contains("7"));
        assertTrue(ex.getMessage().contains("0~6"));
    }

    @Test
    void partitionWeekdays_InvalidIndexPlusValid_ThrowsOnFirstInvalid() {
        Set<Integer> mixed = new HashSet<>(Arrays.asList(0, 1, 100, 2));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                FreeDaySummarySupport.partitionWeekdays(mixed)
        );
        assertTrue(ex.getMessage().contains("100"));
    }

    @Test
    void partitionWeekdays_NullElementInSet_Throws() {
        Set<Integer> hasNull = new HashSet<>(Arrays.asList(0, 1, null, 2));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                FreeDaySummarySupport.partitionWeekdays(hasNull)
        );
        assertEquals("weekday index 不能为 null", ex.getMessage());
    }

    @Test
    void partitionWeekdays_NullSet_Throws() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                FreeDaySummarySupport.partitionWeekdays(null)
        );
        assertTrue(ex.getMessage().contains("busyIndices"));
        assertTrue(ex.getMessage().contains("null"));
    }

    @Test
    void freeDaySummaryCalculate_NullMatcher_Throws() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                FreeDaySummarySupport.calculateFreeDaySummary(
                        Collections.emptyList(), null, (b, f) -> new TeacherFreeDaySummaryResponse())
        );
        assertTrue(ex.getMessage().contains("matcher"));
    }

    @Test
    void freeDaySummaryCalculate_NullFactory_Throws() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                FreeDaySummarySupport.calculateFreeDaySummary(
                        Collections.emptyList(), s -> true, null)
        );
        assertTrue(ex.getMessage().contains("responseFactory"));
    }

    @Test
    void partitionWeekdays_ReturnsImmutableLists() {
        Set<Integer> busy = new HashSet<>(Arrays.asList(0, 2, 4));

        FreeDaySummarySupport.WeekdayPartition partition =
                FreeDaySummarySupport.partitionWeekdays(busy);

        assertThrows(UnsupportedOperationException.class, () -> partition.getBusyDays().add("周二"));
        assertThrows(UnsupportedOperationException.class, () -> partition.getFreeDays().add("周一"));
        assertThrows(UnsupportedOperationException.class, () -> partition.getBusyDays().remove(0));
        assertThrows(UnsupportedOperationException.class, () -> partition.getFreeDays().clear());
        assertThrows(UnsupportedOperationException.class, () -> partition.getBusyDays().set(0, "周日"));
    }

    @Test
    void partitionWeekdays_EmptyBusyIndices_AllDaysFree() {
        Set<Integer> empty = Collections.emptySet();

        FreeDaySummarySupport.WeekdayPartition partition =
                FreeDaySummarySupport.partitionWeekdays(empty);

        assertTrue(partition.getBusyDays().isEmpty());
        assertEquals(7, partition.getFreeDays().size());
        assertEquals(Arrays.asList("周一", "周二", "周三", "周四", "周五", "周六", "周日"),
                partition.getFreeDays());
    }

    @Test
    void partitionWeekdays_AllIndicesBusy_NoFreeDays() {
        Set<Integer> all = new HashSet<>(Arrays.asList(0, 1, 2, 3, 4, 5, 6));

        FreeDaySummarySupport.WeekdayPartition partition =
                FreeDaySummarySupport.partitionWeekdays(all);

        assertEquals(7, partition.getBusyDays().size());
        assertTrue(partition.getFreeDays().isEmpty());
        assertEquals(Arrays.asList("周一", "周二", "周三", "周四", "周五", "周六", "周日"),
                partition.getBusyDays());
    }

    @Test
    void partitionWeekdays_PartialDays_CorrectPartition() {
        Set<Integer> busy = new HashSet<>(Arrays.asList(0, 2, 4));

        FreeDaySummarySupport.WeekdayPartition partition =
                FreeDaySummarySupport.partitionWeekdays(busy);

        assertEquals(Arrays.asList("周一", "周三", "周五"), partition.getBusyDays());
        assertEquals(Arrays.asList("周二", "周四", "周六", "周日"), partition.getFreeDays());
    }

    @Test
    void partitionWeekdays_SaturdaySundayIncluded_CorrectlySorted() {
        Set<Integer> busy = new HashSet<>(Arrays.asList(5, 6, 0));

        FreeDaySummarySupport.WeekdayPartition partition =
                FreeDaySummarySupport.partitionWeekdays(busy);

        assertEquals(Arrays.asList("周一", "周六", "周日"), partition.getBusyDays());
        assertEquals(Arrays.asList("周二", "周三", "周四", "周五"), partition.getFreeDays());
    }

    @Test
    void partitionWeekdays_ResultAlwaysSortedMondayToSunday() {
        Set<Integer> busy = new HashSet<>(Arrays.asList(6, 2, 0, 5));

        FreeDaySummarySupport.WeekdayPartition partition =
                FreeDaySummarySupport.partitionWeekdays(busy);

        assertEquals(Arrays.asList("周一", "周三", "周六", "周日"), partition.getBusyDays());
        assertEquals(Arrays.asList("周二", "周四", "周五"), partition.getFreeDays());
    }

    @Test
    void weekdayPartition_InvalidWeekdayNameInBusy_Throws() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                new FreeDaySummarySupport.WeekdayPartition(
                        Arrays.asList("周一", "星期八"),
                        Arrays.asList("周二", "周三", "周四", "周五", "周六"))
        );
        assertTrue(ex.getMessage().contains("星期八"));
        assertTrue(ex.getMessage().contains("busyDays"));
    }

    @Test
    void weekdayPartition_InvalidWeekdayNameInFree_Throws() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                new FreeDaySummarySupport.WeekdayPartition(
                        Arrays.asList("周一"),
                        Arrays.asList("周二", "holiday", "周四", "周五", "周六", "周日"))
        );
        assertTrue(ex.getMessage().contains("holiday"));
        assertTrue(ex.getMessage().contains("freeDays"));
    }

    @Test
    void weekdayPartition_NullElementInBusy_Throws() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                new FreeDaySummarySupport.WeekdayPartition(
                        Arrays.asList("周一", null, "周三"),
                        Arrays.asList("周二", "周四", "周五", "周六"))
        );
        assertTrue(ex.getMessage().contains("null"));
        assertTrue(ex.getMessage().contains("busyDays"));
    }

    @Test
    void weekdayPartition_NullElementInFree_Throws() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                new FreeDaySummarySupport.WeekdayPartition(
                        Arrays.asList("周一"),
                        Arrays.asList("周二", null, "周四", "周五", "周六", "周日"))
        );
        assertTrue(ex.getMessage().contains("null"));
        assertTrue(ex.getMessage().contains("freeDays"));
    }

    @Test
    void weekdayPartition_OverlapDay_Throws() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                new FreeDaySummarySupport.WeekdayPartition(
                        Arrays.asList("周一", "周三", "周五"),
                        Arrays.asList("周二", "周三", "周四", "周六", "周日"))
        );
        assertTrue(ex.getMessage().contains("周三"));
        assertTrue(ex.getMessage().contains("同时出现"));
    }

    @Test
    void weekdayPartition_TotalCountNotSeven_Throws() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                new FreeDaySummarySupport.WeekdayPartition(
                        Arrays.asList("周一", "周三"),
                        Arrays.asList("周二", "周四", "周五"))
        );
        assertTrue(ex.getMessage().contains("等于 7 天"));
    }

    @Test
    void weekdayPartition_NullBusyList_Throws() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                new FreeDaySummarySupport.WeekdayPartition(
                        null,
                        Arrays.asList("周一", "周二", "周三", "周四", "周五", "周六", "周日"))
        );
        assertTrue(ex.getMessage().contains("busyDays"));
        assertTrue(ex.getMessage().contains("null"));
    }

    @Test
    void weekdayPartition_NullFreeList_Throws() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                new FreeDaySummarySupport.WeekdayPartition(
                        Arrays.asList("周一", "周二", "周三", "周四", "周五", "周六", "周日"),
                        null)
        );
        assertTrue(ex.getMessage().contains("freeDays"));
        assertTrue(ex.getMessage().contains("null"));
    }

    @Test
    void weekdayPartition_ValidInput_ReturnsImmutable() {
        FreeDaySummarySupport.WeekdayPartition partition =
                new FreeDaySummarySupport.WeekdayPartition(
                        Arrays.asList("周一", "周三", "周五"),
                        Arrays.asList("周二", "周四", "周六", "周日"));

        assertEquals(Arrays.asList("周一", "周三", "周五"), partition.getBusyDays());
        assertEquals(Arrays.asList("周二", "周四", "周六", "周日"), partition.getFreeDays());
        assertThrows(UnsupportedOperationException.class, () -> partition.getBusyDays().add("周二"));
        assertThrows(UnsupportedOperationException.class, () -> partition.getFreeDays().remove(0));
    }

    @Test
    void partitionWeekdays_BusyAndFreeAreMutuallyExclusive() {
        Set<Integer> busy = new HashSet<>(Arrays.asList(0, 2, 4, 6));

        FreeDaySummarySupport.WeekdayPartition partition =
                FreeDaySummarySupport.partitionWeekdays(busy);

        Set<String> busySet = new HashSet<>(partition.getBusyDays());
        for (String freeDay : partition.getFreeDays()) {
            assertFalse(busySet.contains(freeDay),
                    freeDay + " 不应该同时出现在 busyDays 和 freeDays 中");
        }
        assertEquals(7, partition.getBusyDays().size() + partition.getFreeDays().size());
    }

    @Test
    void partitionWeekdays_SingleDayBusy_Correct() {
        Set<Integer> busy = Collections.singleton(3);

        FreeDaySummarySupport.WeekdayPartition partition =
                FreeDaySummarySupport.partitionWeekdays(busy);

        assertEquals(Collections.singletonList("周四"), partition.getBusyDays());
        assertEquals(Arrays.asList("周一", "周二", "周三", "周五", "周六", "周日"),
                partition.getFreeDays());
    }

    @Test
    void calculateFreeDaySummary_UsesPartitionWeekdays() {
        List<CourseSchedule> schedules = Arrays.asList(
                new CourseSchedule(1L, "数学", "张老师", "A101", "周一 08:00-10:00"),
                new CourseSchedule(2L, "物理", "张老师", "B202", "周三 08:00-10:00"),
                new CourseSchedule(3L, "化学", "李老师", "C303", "周二 08:00-10:00"),
                new CourseSchedule(4L, "英语", "张老师", "D404", "周五 14:00-16:00")
        );

        TeacherFreeDaySummaryResponse summary =
                TeacherStatisticsSupport.calculateFreeDaySummary("张老师", schedules);

        assertEquals(Arrays.asList("周一", "周三", "周五"), summary.getBusyDays());
        assertEquals(Arrays.asList("周二", "周四", "周六", "周日"), summary.getFreeDays());
        assertEquals(4, summary.getFreeDayCount());
    }

    @Test
    void calculateFreeDaySummary_BusyDaysSortedMondayToSunday() {
        List<CourseSchedule> schedules = Arrays.asList(
                new CourseSchedule(1L, "周日", "张老师", "A101", "周日 08:00-10:00"),
                new CourseSchedule(2L, "周三", "张老师", "B202", "周三 08:00-10:00"),
                new CourseSchedule(3L, "周一", "张老师", "C303", "周一 08:00-10:00")
        );

        TeacherFreeDaySummaryResponse summary =
                TeacherStatisticsSupport.calculateFreeDaySummary("张老师", schedules);

        assertEquals(Arrays.asList("周一", "周三", "周日"), summary.getBusyDays());
    }

    @Test
    void calculateConsecutiveBusyDays_NullList_ReturnsZeroResult() {
        TeacherConsecutiveBusyDaysResponse result =
                TeacherStatisticsSupport.calculateConsecutiveBusyDays("张老师", null);

        assertEquals("张老师", result.getTeacherName());
        assertTrue(result.getBusyDays().isEmpty());
        assertEquals(0, result.getMaxConsecutiveBusyDays());
        assertTrue(result.getLongestBusyStreak().isEmpty());
    }

    @Test
    void calculateConsecutiveBusyDays_EmptyList_ReturnsZeroResult() {
        TeacherConsecutiveBusyDaysResponse result =
                TeacherStatisticsSupport.calculateConsecutiveBusyDays("张老师", Collections.emptyList());

        assertEquals("张老师", result.getTeacherName());
        assertTrue(result.getBusyDays().isEmpty());
        assertEquals(0, result.getMaxConsecutiveBusyDays());
        assertTrue(result.getLongestBusyStreak().isEmpty());
    }

    @Test
    void calculateConsecutiveBusyDays_NoCoursesForTeacher_ReturnsZeroResult() {
        List<CourseSchedule> schedules = Arrays.asList(
                new CourseSchedule(1L, "数学", "李老师", "A101", "周一 08:00-10:00")
        );

        TeacherConsecutiveBusyDaysResponse result =
                TeacherStatisticsSupport.calculateConsecutiveBusyDays("张老师", schedules);

        assertEquals("张老师", result.getTeacherName());
        assertTrue(result.getBusyDays().isEmpty());
        assertEquals(0, result.getMaxConsecutiveBusyDays());
        assertTrue(result.getLongestBusyStreak().isEmpty());
    }

    @Test
    void calculateConsecutiveBusyDays_ThreeConsecutiveDays_ReturnsCorrectStreak() {
        List<CourseSchedule> schedules = Arrays.asList(
                new CourseSchedule(1L, "数学", "张老师", "A101", "周一 08:00-10:00"),
                new CourseSchedule(2L, "物理", "张老师", "B202", "周二 08:00-10:00"),
                new CourseSchedule(3L, "化学", "张老师", "C303", "周三 08:00-10:00"),
                new CourseSchedule(4L, "英语", "张老师", "D404", "周五 14:00-16:00")
        );

        TeacherConsecutiveBusyDaysResponse result =
                TeacherStatisticsSupport.calculateConsecutiveBusyDays("张老师", schedules);

        assertEquals("张老师", result.getTeacherName());
        assertEquals(Arrays.asList("周一", "周二", "周三", "周五"), result.getBusyDays());
        assertEquals(3, result.getMaxConsecutiveBusyDays());
        assertEquals(Arrays.asList("周一", "周二", "周三"), result.getLongestBusyStreak());
    }

    @Test
    void calculateConsecutiveBusyDays_TwoEqualStreaks_PicksEarlier() {
        List<CourseSchedule> schedules = Arrays.asList(
                new CourseSchedule(1L, "数学", "张老师", "A101", "周一 08:00-10:00"),
                new CourseSchedule(2L, "物理", "张老师", "B202", "周二 08:00-10:00"),
                new CourseSchedule(3L, "化学", "张老师", "C303", "周四 08:00-10:00"),
                new CourseSchedule(4L, "英语", "张老师", "D404", "周五 14:00-16:00")
        );

        TeacherConsecutiveBusyDaysResponse result =
                TeacherStatisticsSupport.calculateConsecutiveBusyDays("张老师", schedules);

        assertEquals(Arrays.asList("周一", "周二", "周四", "周五"), result.getBusyDays());
        assertEquals(2, result.getMaxConsecutiveBusyDays());
        assertEquals(Arrays.asList("周一", "周二"), result.getLongestBusyStreak());
    }

    @Test
    void calculateConsecutiveBusyDays_AllSevenDays_ReturnsSevenConsecutive() {
        List<CourseSchedule> schedules = Arrays.asList(
                new CourseSchedule(1L, "周一", "张老师", "A101", "周一 08:00-10:00"),
                new CourseSchedule(2L, "周二", "张老师", "A101", "周二 08:00-10:00"),
                new CourseSchedule(3L, "周三", "张老师", "A101", "周三 08:00-10:00"),
                new CourseSchedule(4L, "周四", "张老师", "A101", "周四 08:00-10:00"),
                new CourseSchedule(5L, "周五", "张老师", "A101", "周五 08:00-10:00"),
                new CourseSchedule(6L, "周六", "张老师", "A101", "周六 08:00-10:00"),
                new CourseSchedule(7L, "周日", "张老师", "A101", "周日 08:00-10:00")
        );

        TeacherConsecutiveBusyDaysResponse result =
                TeacherStatisticsSupport.calculateConsecutiveBusyDays("张老师", schedules);

        assertEquals(Arrays.asList("周一", "周二", "周三", "周四", "周五", "周六", "周日"), result.getBusyDays());
        assertEquals(7, result.getMaxConsecutiveBusyDays());
        assertEquals(Arrays.asList("周一", "周二", "周三", "周四", "周五", "周六", "周日"), result.getLongestBusyStreak());
    }

    @Test
    void calculateConsecutiveBusyDays_SingleDay_ReturnsOne() {
        List<CourseSchedule> schedules = Arrays.asList(
                new CourseSchedule(1L, "数学", "张老师", "A101", "周三 08:00-10:00")
        );

        TeacherConsecutiveBusyDaysResponse result =
                TeacherStatisticsSupport.calculateConsecutiveBusyDays("张老师", schedules);

        assertEquals(Collections.singletonList("周三"), result.getBusyDays());
        assertEquals(1, result.getMaxConsecutiveBusyDays());
        assertEquals(Collections.singletonList("周三"), result.getLongestBusyStreak());
    }

    @Test
    void calculateConsecutiveBusyDays_InvalidTimeSlots_Ignored() {
        List<CourseSchedule> schedules = Arrays.asList(
                new CourseSchedule(1L, "数学", "张老师", "A101", null),
                new CourseSchedule(2L, "物理", "张老师", "B202", "周八 08:00-10:00"),
                new CourseSchedule(3L, "化学", "张老师", "C303", "周二 08:00-10:00"),
                new CourseSchedule(4L, "英语", "张老师", "D404", "完全不合法的时间段"),
                new CourseSchedule(5L, "生物", "张老师", "E505", "周三 09:00-11:00"),
                new CourseSchedule(6L, "历史", "张老师", "F606", "周一 25:00-26:00")
        );

        TeacherConsecutiveBusyDaysResponse result =
                TeacherStatisticsSupport.calculateConsecutiveBusyDays("张老师", schedules);

        assertEquals(Arrays.asList("周二", "周三"), result.getBusyDays());
        assertEquals(2, result.getMaxConsecutiveBusyDays());
        assertEquals(Arrays.asList("周二", "周三"), result.getLongestBusyStreak());
    }

    @Test
    void calculateConsecutiveBusyDays_OtherTeachersCourses_NotIncluded() {
        List<CourseSchedule> schedules = Arrays.asList(
                new CourseSchedule(1L, "数学", "张老师", "A101", "周一 08:00-10:00"),
                new CourseSchedule(2L, "物理", "李老师", "B202", "周二 08:00-10:00"),
                new CourseSchedule(3L, "化学", "张老师", "C303", "周三 08:00-10:00"),
                new CourseSchedule(4L, "英语", "李老师", "D404", "周四 08:00-10:00")
        );

        TeacherConsecutiveBusyDaysResponse result =
                TeacherStatisticsSupport.calculateConsecutiveBusyDays("张老师", schedules);

        assertEquals(Arrays.asList("周一", "周三"), result.getBusyDays());
        assertEquals(1, result.getMaxConsecutiveBusyDays());
        assertEquals(Collections.singletonList("周一"), result.getLongestBusyStreak());
    }

    @Test
    void calculateConsecutiveBusyDays_BusyDaysSortedMondayToSunday() {
        List<CourseSchedule> schedules = Arrays.asList(
                new CourseSchedule(1L, "周日", "张老师", "A101", "周日 08:00-10:00"),
                new CourseSchedule(2L, "周三", "张老师", "B202", "周三 08:00-10:00"),
                new CourseSchedule(3L, "周一", "张老师", "C303", "周一 08:00-10:00")
        );

        TeacherConsecutiveBusyDaysResponse result =
                TeacherStatisticsSupport.calculateConsecutiveBusyDays("张老师", schedules);

        assertEquals(Arrays.asList("周一", "周三", "周日"), result.getBusyDays());
    }

    @Test
    void calculateConsecutiveBusyDays_SaturdaySundayConsecutive() {
        List<CourseSchedule> schedules = Arrays.asList(
                new CourseSchedule(1L, "周六课", "张老师", "A101", "周六 08:00-10:00"),
                new CourseSchedule(2L, "周日课", "张老师", "B202", "周日 08:00-10:00")
        );

        TeacherConsecutiveBusyDaysResponse result =
                TeacherStatisticsSupport.calculateConsecutiveBusyDays("张老师", schedules);

        assertEquals(Arrays.asList("周六", "周日"), result.getBusyDays());
        assertEquals(2, result.getMaxConsecutiveBusyDays());
        assertEquals(Arrays.asList("周六", "周日"), result.getLongestBusyStreak());
    }

    @Test
    void calculateConsecutiveBusyDays_SundayNotWrappingToMonday() {
        List<CourseSchedule> schedules = Arrays.asList(
                new CourseSchedule(1L, "周日课", "张老师", "A101", "周日 08:00-10:00"),
                new CourseSchedule(2L, "周一课", "张老师", "B202", "周一 08:00-10:00")
        );

        TeacherConsecutiveBusyDaysResponse result =
                TeacherStatisticsSupport.calculateConsecutiveBusyDays("张老师", schedules);

        assertEquals(Arrays.asList("周一", "周日"), result.getBusyDays());
        assertEquals(1, result.getMaxConsecutiveBusyDays());
        assertEquals(Collections.singletonList("周一"), result.getLongestBusyStreak());
    }

    @Test
    void calculateConsecutiveBusyDays_DuplicateCoursesOnSameDay_NoDuplicateBusyDay() {
        List<CourseSchedule> schedules = Arrays.asList(
                new CourseSchedule(1L, "数学", "张老师", "A101", "周一 08:00-10:00"),
                new CourseSchedule(2L, "物理", "张老师", "B202", "周一 10:00-12:00"),
                new CourseSchedule(3L, "化学", "张老师", "C303", "周二 08:00-10:00")
        );

        TeacherConsecutiveBusyDaysResponse result =
                TeacherStatisticsSupport.calculateConsecutiveBusyDays("张老师", schedules);

        assertEquals(Arrays.asList("周一", "周二"), result.getBusyDays());
        assertEquals(2, result.getMaxConsecutiveBusyDays());
        assertEquals(Arrays.asList("周一", "周二"), result.getLongestBusyStreak());
    }

    @Test
    void calculateConsecutiveBusyDays_MixedDirtyData_OnlyValidCounted() {
        List<CourseSchedule> schedules = Arrays.asList(
                null,
                new CourseSchedule(1L, "数学", "张老师", "A101", null),
                new CourseSchedule(2L, "物理", "张老师", "B202", "周一 08:00-10:00"),
                new CourseSchedule(3L, "化学", "李老师", "C303", "周二 08:00-10:00"),
                null,
                new CourseSchedule(4L, "英语", "张老师", "D404", "乱码时间"),
                new CourseSchedule(5L, "生物", "张老师", "E505", "周二 09:00-11:00"),
                new CourseSchedule(6L, null, "张老师", "F606", "周三 14:00-16:00")
        );

        TeacherConsecutiveBusyDaysResponse result =
                TeacherStatisticsSupport.calculateConsecutiveBusyDays("张老师", schedules);

        assertEquals(Arrays.asList("周一", "周二", "周三"), result.getBusyDays());
        assertEquals(3, result.getMaxConsecutiveBusyDays());
        assertEquals(Arrays.asList("周一", "周二", "周三"), result.getLongestBusyStreak());
    }

    @Test
    void calculateConsecutiveBusyDays_ThreeEqualStreaksOfLengthTwo_PicksFirst() {
        List<CourseSchedule> schedules = Arrays.asList(
                new CourseSchedule(1L, "数学", "张老师", "A101", "周一 08:00-10:00"),
                new CourseSchedule(2L, "物理", "张老师", "B202", "周二 08:00-10:00"),
                new CourseSchedule(3L, "化学", "张老师", "C303", "周四 08:00-10:00"),
                new CourseSchedule(4L, "英语", "张老师", "D404", "周五 14:00-16:00"),
                new CourseSchedule(5L, "生物", "张老师", "E505", "周日 09:00-11:00")
        );

        TeacherConsecutiveBusyDaysResponse result =
                TeacherStatisticsSupport.calculateConsecutiveBusyDays("张老师", schedules);

        assertEquals(Arrays.asList("周一", "周二", "周四", "周五", "周日"), result.getBusyDays());
        assertEquals(2, result.getMaxConsecutiveBusyDays());
        assertEquals(Arrays.asList("周一", "周二"), result.getLongestBusyStreak());
    }

    @Test
    void consecutiveBusyDays_NoArgConstructor_Defaults() {
        TeacherConsecutiveBusyDaysResponse response = new TeacherConsecutiveBusyDaysResponse();

        assertNull(response.getTeacherName());
        assertTrue(response.getBusyDays().isEmpty());
        assertEquals(0, response.getMaxConsecutiveBusyDays());
        assertTrue(response.getLongestBusyStreak().isEmpty());
    }

    @Test
    void consecutiveBusyDays_BusyDaysImmutable_ThrowsOnModify() {
        List<CourseSchedule> schedules = Arrays.asList(
                new CourseSchedule(1L, "数学", "张老师", "A101", "周一 08:00-10:00")
        );

        TeacherConsecutiveBusyDaysResponse result =
                TeacherStatisticsSupport.calculateConsecutiveBusyDays("张老师", schedules);

        assertThrows(UnsupportedOperationException.class, () -> result.getBusyDays().add("周二"));
        assertThrows(UnsupportedOperationException.class, () -> result.getLongestBusyStreak().add("周二"));
    }

    @Test
    void consecutiveBusyDays_SetNullBusyDays_BecomesEmpty() {
        TeacherConsecutiveBusyDaysResponse response = new TeacherConsecutiveBusyDaysResponse();
        response.setBusyDays(null);

        assertNotNull(response.getBusyDays());
        assertTrue(response.getBusyDays().isEmpty());
    }

    @Test
    void consecutiveBusyDays_SetNullLongestBusyStreak_BecomesEmpty() {
        TeacherConsecutiveBusyDaysResponse response = new TeacherConsecutiveBusyDaysResponse();
        response.setLongestBusyStreak(null);

        assertNotNull(response.getLongestBusyStreak());
        assertTrue(response.getLongestBusyStreak().isEmpty());
    }

    @Test
    void consecutiveBusyDays_ConstructorDefensiveCopy() {
        List<String> externalBusyDays = new java.util.ArrayList<>();
        externalBusyDays.add("周一");
        externalBusyDays.add("周二");
        List<String> externalStreak = new java.util.ArrayList<>();
        externalStreak.add("周一");
        externalStreak.add("周二");

        TeacherConsecutiveBusyDaysResponse response = new TeacherConsecutiveBusyDaysResponse(
                "张老师", externalBusyDays, 2, externalStreak);

        externalBusyDays.add("周三");
        externalStreak.add("周三");

        assertEquals(2, response.getBusyDays().size());
        assertEquals(2, response.getLongestBusyStreak().size());
    }

    @Test
    void consecutiveBusyDays_FiveDayStreak_MondayToFriday() {
        List<CourseSchedule> schedules = Arrays.asList(
                new CourseSchedule(1L, "周一", "张老师", "A101", "周一 08:00-10:00"),
                new CourseSchedule(2L, "周二", "张老师", "B202", "周二 08:00-10:00"),
                new CourseSchedule(3L, "周三", "张老师", "C303", "周三 08:00-10:00"),
                new CourseSchedule(4L, "周四", "张老师", "D404", "周四 08:00-10:00"),
                new CourseSchedule(5L, "周五", "张老师", "E505", "周五 08:00-10:00")
        );

        TeacherConsecutiveBusyDaysResponse result =
                TeacherStatisticsSupport.calculateConsecutiveBusyDays("张老师", schedules);

        assertEquals(Arrays.asList("周一", "周二", "周三", "周四", "周五"), result.getBusyDays());
        assertEquals(5, result.getMaxConsecutiveBusyDays());
        assertEquals(Arrays.asList("周一", "周二", "周三", "周四", "周五"), result.getLongestBusyStreak());
    }

}
