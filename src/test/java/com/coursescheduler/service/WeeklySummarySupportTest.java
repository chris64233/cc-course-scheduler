package com.coursescheduler.service;

import com.coursescheduler.model.CourseSchedule;
import com.coursescheduler.util.TimeSlotUtils;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

class WeeklySummarySupportTest {

    private static final Predicate<CourseSchedule> MATCH_ALL = s -> true;
    private static final Function<CourseSchedule, String> EXTRACT_COURSE_NAME = CourseSchedule::getCourseName;

    @Test
    void calculate_NullMatcher_ThrowsClearException() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                WeeklySummarySupport.calculate(Collections.emptyList(), null, EXTRACT_COURSE_NAME)
        );
        assertEquals("matcher 不能为空", ex.getMessage());
    }

    @Test
    void calculate_NullNameExtractor_ThrowsClearException() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                WeeklySummarySupport.calculate(Collections.emptyList(), MATCH_ALL, null)
        );
        assertEquals("nameExtractor 不能为空", ex.getMessage());
    }

    @Test
    void calculate_NullAll_ThrowsOnMatcherFirst() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                WeeklySummarySupport.calculate(null, null, null)
        );
        assertEquals("matcher 不能为空", ex.getMessage());
    }

    @Test
    void calculate_EmptyList_ReturnsZeroSummary() {
        WeeklySummarySupport.WeeklySummaryResult result = WeeklySummarySupport.calculate(
                Collections.emptyList(), MATCH_ALL, EXTRACT_COURSE_NAME);

        assertEquals(0, result.getTotalCourses());
        assertEquals(0, result.getOccupiedDays());
        assertNull(result.getBusiestDay());
        assertNotNull(result.getNames());
        assertTrue(result.getNames().isEmpty());
    }

    @Test
    void calculate_NullList_ReturnsZeroSummary() {
        WeeklySummarySupport.WeeklySummaryResult result = WeeklySummarySupport.calculate(
                null, MATCH_ALL, EXTRACT_COURSE_NAME);

        assertEquals(0, result.getTotalCourses());
        assertEquals(0, result.getOccupiedDays());
        assertNull(result.getBusiestDay());
        assertTrue(result.getNames().isEmpty());
    }

    @Test
    void calculate_ValidCourses_CorrectStats() {
        List<CourseSchedule> schedules = Arrays.asList(
                new CourseSchedule(1L, "数学", "张老师", "A101", "周一 08:00-10:00"),
                new CourseSchedule(2L, "物理", "李老师", "A101", "周二 08:00-10:00"),
                new CourseSchedule(3L, "化学", "王老师", "B202", "周三 08:00-10:00"),
                new CourseSchedule(4L, "英语", "赵老师", "A101", "周一 14:00-16:00"),
                new CourseSchedule(5L, "生物", "钱老师", "B202", "周四 08:00-10:00")
        );

        WeeklySummarySupport.WeeklySummaryResult result = WeeklySummarySupport.calculate(
                schedules, s -> "A101".equals(s.getClassroom()), CourseSchedule::getCourseName);

        assertEquals(3, result.getTotalCourses());
        assertEquals(2, result.getOccupiedDays());
        assertEquals("周一", result.getBusiestDay());
        assertEquals(Arrays.asList("数学", "物理", "英语"), result.getNames());
    }

    @Test
    void calculate_InvalidTimeSlot_Skipped() {
        List<CourseSchedule> schedules = Arrays.asList(
                new CourseSchedule(1L, "数学", "张老师", "A101", null),
                new CourseSchedule(2L, "物理", "李老师", "A101", "周八 08:00-10:00"),
                new CourseSchedule(3L, "化学", "王老师", "A101", "周一 08:00-10:00"),
                new CourseSchedule(4L, "英语", "赵老师", "A101", "完全不合法"),
                new CourseSchedule(5L, "生物", "钱老师", "A101", "周二 09:00-11:00")
        );

        WeeklySummarySupport.WeeklySummaryResult result = WeeklySummarySupport.calculate(
                schedules, MATCH_ALL, EXTRACT_COURSE_NAME);

        assertEquals(2, result.getTotalCourses());
        assertEquals(2, result.getOccupiedDays());
        assertEquals("周一", result.getBusiestDay());
        assertEquals(Arrays.asList("化学", "生物"), result.getNames());
    }

    @Test
    void calculate_NullTimeSlot_EntireCourseSkipped() {
        List<CourseSchedule> schedules = Arrays.asList(
                new CourseSchedule(1L, "数学", "张老师", "A101", null),
                new CourseSchedule(2L, "物理", "李老师", "A101", null)
        );

        WeeklySummarySupport.WeeklySummaryResult result = WeeklySummarySupport.calculate(
                schedules, MATCH_ALL, EXTRACT_COURSE_NAME);

        assertEquals(0, result.getTotalCourses());
        assertEquals(0, result.getOccupiedDays());
        assertNull(result.getBusiestDay());
        assertTrue(result.getNames().isEmpty());
    }

    @Test
    void calculate_NullCoursesInList_Skipped() {
        List<CourseSchedule> schedules = Arrays.asList(
                new CourseSchedule(1L, "数学", "张老师", "A101", "周一 08:00-10:00"),
                null,
                new CourseSchedule(2L, "物理", "李老师", "A101", "周二 08:00-10:00"),
                null
        );

        WeeklySummarySupport.WeeklySummaryResult result = WeeklySummarySupport.calculate(
                schedules, MATCH_ALL, EXTRACT_COURSE_NAME);

        assertEquals(2, result.getTotalCourses());
        assertEquals(2, result.getOccupiedDays());
        assertEquals(Arrays.asList("数学", "物理"), result.getNames());
    }

    @Test
    void calculate_TieBusiestDay_PicksEarlierWeekday() {
        List<CourseSchedule> schedules = Arrays.asList(
                new CourseSchedule(1L, "数学", "张老师", "A101", "周三 08:00-10:00"),
                new CourseSchedule(2L, "物理", "李老师", "A101", "周三 10:00-12:00"),
                new CourseSchedule(3L, "化学", "王老师", "A101", "周一 08:00-10:00"),
                new CourseSchedule(4L, "英语", "赵老师", "A101", "周一 10:00-12:00"),
                new CourseSchedule(5L, "生物", "钱老师", "A101", "周五 08:00-10:00"),
                new CourseSchedule(6L, "历史", "孙老师", "A101", "周五 10:00-12:00")
        );

        WeeklySummarySupport.WeeklySummaryResult result = WeeklySummarySupport.calculate(
                schedules, MATCH_ALL, EXTRACT_COURSE_NAME);

        assertEquals(6, result.getTotalCourses());
        assertEquals(3, result.getOccupiedDays());
        assertEquals("周一", result.getBusiestDay());
    }

    @Test
    void calculate_TieOnSundayAndMonday_MondayWins() {
        List<CourseSchedule> schedules = Arrays.asList(
                new CourseSchedule(1L, "数学", "张老师", "A101", "周日 08:00-10:00"),
                new CourseSchedule(2L, "物理", "李老师", "A101", "周日 10:00-12:00"),
                new CourseSchedule(3L, "化学", "王老师", "A101", "周一 08:00-10:00"),
                new CourseSchedule(4L, "英语", "赵老师", "A101", "周一 10:00-12:00")
        );

        WeeklySummarySupport.WeeklySummaryResult result = WeeklySummarySupport.calculate(
                schedules, MATCH_ALL, EXTRACT_COURSE_NAME);

        assertEquals("周一", result.getBusiestDay());
    }

    @Test
    void calculate_NamesDeduped_PreservesCreationOrder() {
        List<CourseSchedule> schedules = Arrays.asList(
                new CourseSchedule(1L, "数学", "张老师", "A101", "周一 08:00-10:00"),
                new CourseSchedule(2L, "物理", "李老师", "A101", "周二 08:00-10:00"),
                new CourseSchedule(3L, "数学", "王老师", "A101", "周三 08:00-10:00"),
                new CourseSchedule(4L, "化学", "赵老师", "A101", "周四 08:00-10:00"),
                new CourseSchedule(5L, "物理", "钱老师", "A101", "周五 08:00-10:00"),
                new CourseSchedule(6L, "英语", "孙老师", "A101", "周六 08:00-10:00")
        );

        WeeklySummarySupport.WeeklySummaryResult result = WeeklySummarySupport.calculate(
                schedules, MATCH_ALL, EXTRACT_COURSE_NAME);

        assertEquals(6, result.getTotalCourses());
        assertEquals(Arrays.asList("数学", "物理", "化学", "英语"), result.getNames());
    }

    @Test
    void calculate_NullExtractedNames_FilteredOut() {
        List<CourseSchedule> schedules = Arrays.asList(
                new CourseSchedule(1L, null, "张老师", "A101", "周一 08:00-10:00"),
                new CourseSchedule(2L, "物理", "李老师", "A101", "周二 08:00-10:00"),
                new CourseSchedule(3L, null, "王老师", "A101", "周三 08:00-10:00")
        );

        WeeklySummarySupport.WeeklySummaryResult result = WeeklySummarySupport.calculate(
                schedules, MATCH_ALL, EXTRACT_COURSE_NAME);

        assertEquals(3, result.getTotalCourses());
        assertEquals(3, result.getOccupiedDays());
        assertEquals(Arrays.asList("物理"), result.getNames());
    }

    @Test
    void calculate_NamesImmutable_ThrowsOnModification() {
        List<CourseSchedule> schedules = Arrays.asList(
                new CourseSchedule(1L, "数学", "张老师", "A101", "周一 08:00-10:00")
        );

        WeeklySummarySupport.WeeklySummaryResult result = WeeklySummarySupport.calculate(
                schedules, MATCH_ALL, EXTRACT_COURSE_NAME);

        assertThrows(UnsupportedOperationException.class, () -> result.getNames().add("物理"));
        assertThrows(UnsupportedOperationException.class, () -> result.getNames().remove(0));
    }

    @Test
    void calculate_FilterOrder_MatcherReceivesTimeSlotDirtyData() {
        List<CourseSchedule> schedules = Arrays.asList(
                new CourseSchedule(1L, "数学", "张老师", "A101", "周一 08:00-10:00"),
                new CourseSchedule(2L, "物理", "李老师", "A101", null),
                new CourseSchedule(3L, "化学", "王老师", "A101", "完全不合法的时间段")
        );

        final boolean[] matcherCalledOnNullTimeSlot = {false};
        final boolean[] matcherCalledOnInvalidTimeSlot = {false};
        Predicate<CourseSchedule> observingMatcher = s -> {
            if (s.getTimeSlot() == null) {
                matcherCalledOnNullTimeSlot[0] = true;
            }
            if (s.getTimeSlot() != null && s.getTimeSlot().contains("完全不合法")) {
                matcherCalledOnInvalidTimeSlot[0] = true;
            }
            return true;
        };

        WeeklySummarySupport.calculate(schedules, observingMatcher, EXTRACT_COURSE_NAME);

        assertTrue(matcherCalledOnNullTimeSlot[0], "matcher 应该能接收到 timeSlot 为 null 的课程（边界承诺：只保证不会收到 null schedule）");
        assertTrue(matcherCalledOnInvalidTimeSlot[0], "matcher 应该能接收到 timeSlot 格式非法的课程（边界承诺：只保证不会收到 null schedule）");
    }

    @Test
    void calculate_MatcherNeverRunsOnNullSchedules() {
        List<CourseSchedule> schedules = Arrays.asList(
                null,
                new CourseSchedule(1L, "数学", "张老师", "A101", "周一 08:00-10:00"),
                null
        );

        final boolean[] matcherCalledOnNull = {false};
        Predicate<CourseSchedule> safeMatcher = s -> {
            if (s == null) {
                matcherCalledOnNull[0] = true;
            }
            return true;
        };

        WeeklySummarySupport.calculate(schedules, safeMatcher, EXTRACT_COURSE_NAME);

        assertFalse(matcherCalledOnNull[0], "matcher 不应该在 null 课程上被调用");
    }

    @Test
    void calculate_SundayCourses_CountedCorrectly() {
        List<CourseSchedule> schedules = Arrays.asList(
                new CourseSchedule(1L, "数学", "张老师", "A101", "周六 08:00-10:00"),
                new CourseSchedule(2L, "物理", "李老师", "A101", "周日 08:00-10:00"),
                new CourseSchedule(3L, "化学", "王老师", "A101", "周日 10:00-12:00")
        );

        WeeklySummarySupport.WeeklySummaryResult result = WeeklySummarySupport.calculate(
                schedules, MATCH_ALL, EXTRACT_COURSE_NAME);

        assertEquals(3, result.getTotalCourses());
        assertEquals(2, result.getOccupiedDays());
        assertEquals("周日", result.getBusiestDay());
    }

    @Test
    void calculate_CustomExtractor_ExtractsTeacherNames() {
        List<CourseSchedule> schedules = Arrays.asList(
                new CourseSchedule(1L, "数学", "张老师", "A101", "周一 08:00-10:00"),
                new CourseSchedule(2L, "物理", "李老师", "A101", "周二 08:00-10:00"),
                new CourseSchedule(3L, "化学", "张老师", "A101", "周三 08:00-10:00")
        );

        WeeklySummarySupport.WeeklySummaryResult result = WeeklySummarySupport.calculate(
                schedules, MATCH_ALL, CourseSchedule::getTeacherName);

        assertEquals(3, result.getTotalCourses());
        assertEquals(3, result.getOccupiedDays());
        assertEquals(Arrays.asList("张老师", "李老师"), result.getNames());
    }

    @Test
    void calculate_CustomMatcher_OnlyCountsMatching() {
        List<CourseSchedule> schedules = Arrays.asList(
                new CourseSchedule(1L, "数学", "张老师", "A101", "周一 08:00-10:00"),
                new CourseSchedule(2L, "物理", "李老师", "B202", "周二 08:00-10:00"),
                new CourseSchedule(3L, "化学", "王老师", "A101", "周三 08:00-10:00")
        );

        WeeklySummarySupport.WeeklySummaryResult result = WeeklySummarySupport.calculate(
                schedules, s -> "B202".equals(s.getClassroom()), EXTRACT_COURSE_NAME);

        assertEquals(1, result.getTotalCourses());
        assertEquals(1, result.getOccupiedDays());
        assertEquals("周二", result.getBusiestDay());
        assertEquals(Arrays.asList("物理"), result.getNames());
    }

    @Test
    void calculate_NoMatchingCourses_ReturnsZeroSummary() {
        List<CourseSchedule> schedules = Arrays.asList(
                new CourseSchedule(1L, "数学", "张老师", "A101", "周一 08:00-10:00"),
                new CourseSchedule(2L, "物理", "李老师", "A101", "周二 08:00-10:00")
        );

        WeeklySummarySupport.WeeklySummaryResult result = WeeklySummarySupport.calculate(
                schedules, s -> false, EXTRACT_COURSE_NAME);

        assertEquals(0, result.getTotalCourses());
        assertEquals(0, result.getOccupiedDays());
        assertNull(result.getBusiestDay());
        assertTrue(result.getNames().isEmpty());
    }

    @Test
    void calculate_AllDaysOccupied_CorrectCount() {
        List<CourseSchedule> schedules = Arrays.asList(
                new CourseSchedule(1L, "周一", "张", "A101", "周一 08:00-10:00"),
                new CourseSchedule(2L, "周二", "李", "A101", "周二 08:00-10:00"),
                new CourseSchedule(3L, "周三", "王", "A101", "周三 08:00-10:00"),
                new CourseSchedule(4L, "周四", "赵", "A101", "周四 08:00-10:00"),
                new CourseSchedule(5L, "周五", "钱", "A101", "周五 08:00-10:00"),
                new CourseSchedule(6L, "周六", "孙", "A101", "周六 08:00-10:00"),
                new CourseSchedule(7L, "周日", "周", "A101", "周日 08:00-10:00")
        );

        WeeklySummarySupport.WeeklySummaryResult result = WeeklySummarySupport.calculate(
                schedules, MATCH_ALL, EXTRACT_COURSE_NAME);

        assertEquals(7, result.getTotalCourses());
        assertEquals(7, result.getOccupiedDays());
        assertEquals("周一", result.getBusiestDay());
        assertEquals(7, result.getNames().size());
    }

    @Test
    void calculate_NonNormalizedTimeSlot_ParsedCorrectly() {
        List<CourseSchedule> schedules = Arrays.asList(
                new CourseSchedule(1L, "数学", "张", "A101", "星期一 8:00-10:00"),
                new CourseSchedule(2L, "物理", "李", "A101", " 周2  09:00-11:00  ")
        );

        WeeklySummarySupport.WeeklySummaryResult result = WeeklySummarySupport.calculate(
                schedules, MATCH_ALL, EXTRACT_COURSE_NAME);

        assertEquals(2, result.getTotalCourses());
        assertEquals(2, result.getOccupiedDays());
        assertEquals("周一", result.getBusiestDay());
        assertEquals(Arrays.asList("数学", "物理"), result.getNames());
    }

    @Test
    void calculate_MixedDirtyAndClean_OnlyCleanCounted() {
        List<CourseSchedule> schedules = Arrays.asList(
                null,
                new CourseSchedule(1L, "数学", "张", "A101", null),
                new CourseSchedule(2L, "物理", "李", "A101", "周一 08:00-10:00"),
                new CourseSchedule(3L, "化学", "王", "B202", "周一 08:00-10:00"),
                null,
                new CourseSchedule(4L, "英语", "赵", "A101", "乱码时间"),
                new CourseSchedule(5L, "生物", "钱", "A101", "周二 09:00-11:00")
        );

        WeeklySummarySupport.WeeklySummaryResult result = WeeklySummarySupport.calculate(
                schedules, s -> "A101".equals(s.getClassroom()), EXTRACT_COURSE_NAME);

        assertEquals(2, result.getTotalCourses());
        assertEquals(2, result.getOccupiedDays());
        assertEquals("周一", result.getBusiestDay());
        assertEquals(Arrays.asList("物理", "生物"), result.getNames());
    }

    @Test
    void weeklySummaryResult_NullNames_FallsBackToEmptyList() {
        WeeklySummarySupport.WeeklySummaryResult result =
                new WeeklySummarySupport.WeeklySummaryResult(0, 0, null, null);

        assertNotNull(result.getNames());
        assertTrue(result.getNames().isEmpty());
    }

    @Test
    void resolveBusyWeekdayIndices_NullMatcher_Throws() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                WeeklySummarySupport.resolveBusyWeekdayIndices(Collections.emptyList(), null)
        );
        assertEquals("matcher 不能为空", ex.getMessage());
    }

    @Test
    void resolveBusyWeekdayIndices_EmptyList_ReturnsEmptySet() {
        Set<Integer> result = WeeklySummarySupport.resolveBusyWeekdayIndices(
                Collections.emptyList(), MATCH_ALL);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void resolveBusyWeekdayIndices_NullList_ReturnsEmptySet() {
        Set<Integer> result = WeeklySummarySupport.resolveBusyWeekdayIndices(null, MATCH_ALL);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void resolveBusyWeekdayIndices_MultipleDays_CorrectIndices() {
        List<CourseSchedule> schedules = Arrays.asList(
                new CourseSchedule(1L, "数学", "张老师", "A101", "周一 08:00-10:00"),
                new CourseSchedule(2L, "物理", "张老师", "B202", "周一 10:00-12:00"),
                new CourseSchedule(3L, "化学", "张老师", "C303", "周三 08:00-10:00"),
                new CourseSchedule(4L, "英语", "张老师", "D404", "周日 14:00-16:00")
        );

        Set<Integer> result = WeeklySummarySupport.resolveBusyWeekdayIndices(
                schedules, s -> "张老师".equals(s.getTeacherName()));

        assertEquals(3, result.size());
        assertTrue(result.contains(TimeSlotUtils.MONDAY));
        assertTrue(result.contains(TimeSlotUtils.WEDNESDAY));
        assertTrue(result.contains(TimeSlotUtils.SUNDAY));
        assertFalse(result.contains(TimeSlotUtils.TUESDAY));
        assertFalse(result.contains(TimeSlotUtils.THURSDAY));
        assertFalse(result.contains(TimeSlotUtils.FRIDAY));
        assertFalse(result.contains(TimeSlotUtils.SATURDAY));
    }

    @Test
    void resolveBusyWeekdayIndices_InvalidTimeSlot_Skipped() {
        List<CourseSchedule> schedules = Arrays.asList(
                new CourseSchedule(1L, "数学", "张老师", "A101", null),
                new CourseSchedule(2L, "物理", "张老师", "B202", "周八 08:00-10:00"),
                new CourseSchedule(3L, "化学", "张老师", "C303", "周二 08:00-10:00"),
                new CourseSchedule(4L, "英语", "张老师", "D404", "完全不合法"),
                new CourseSchedule(5L, "生物", "张老师", "E505", "周四 09:00-11:00")
        );

        Set<Integer> result = WeeklySummarySupport.resolveBusyWeekdayIndices(
                schedules, s -> "张老师".equals(s.getTeacherName()));

        assertEquals(2, result.size());
        assertTrue(result.contains(TimeSlotUtils.TUESDAY));
        assertTrue(result.contains(TimeSlotUtils.THURSDAY));
    }

    @Test
    void resolveBusyWeekdayIndices_NoMatch_ReturnsEmpty() {
        List<CourseSchedule> schedules = Arrays.asList(
                new CourseSchedule(1L, "数学", "李老师", "A101", "周一 08:00-10:00"),
                new CourseSchedule(2L, "物理", "李老师", "B202", "周二 08:00-10:00")
        );

        Set<Integer> result = WeeklySummarySupport.resolveBusyWeekdayIndices(
                schedules, s -> "张老师".equals(s.getTeacherName()));

        assertTrue(result.isEmpty());
    }

    @Test
    void resolveBusyWeekdayIndices_AllSevenDays_AllIndices() {
        List<CourseSchedule> schedules = Arrays.asList(
                new CourseSchedule(1L, "周一", "张", "A101", "周一 08:00-10:00"),
                new CourseSchedule(2L, "周二", "张", "A101", "周二 08:00-10:00"),
                new CourseSchedule(3L, "周三", "张", "A101", "周三 08:00-10:00"),
                new CourseSchedule(4L, "周四", "张", "A101", "周四 08:00-10:00"),
                new CourseSchedule(5L, "周五", "张", "A101", "周五 08:00-10:00"),
                new CourseSchedule(6L, "周六", "张", "A101", "周六 08:00-10:00"),
                new CourseSchedule(7L, "周日", "张", "A101", "周日 08:00-10:00")
        );

        Set<Integer> result = WeeklySummarySupport.resolveBusyWeekdayIndices(
                schedules, MATCH_ALL);

        assertEquals(7, result.size());
        for (int i = 0; i < 7; i++) {
            assertTrue(result.contains(i));
        }
    }

    @Test
    void resolveBusyWeekdayIndices_NullCoursesInList_Skipped() {
        List<CourseSchedule> schedules = Arrays.asList(
                new CourseSchedule(1L, "数学", "张老师", "A101", "周一 08:00-10:00"),
                null,
                new CourseSchedule(2L, "物理", "张老师", "B202", "周三 08:00-10:00"),
                null
        );

        Set<Integer> result = WeeklySummarySupport.resolveBusyWeekdayIndices(
                schedules, s -> "张老师".equals(s.getTeacherName()));

        assertEquals(2, result.size());
        assertTrue(result.contains(TimeSlotUtils.MONDAY));
        assertTrue(result.contains(TimeSlotUtils.WEDNESDAY));
    }

    @Test
    void resolveBusyWeekdayIndices_ReturnsImmutableSet_CantModify() {
        List<CourseSchedule> schedules = Arrays.asList(
                new CourseSchedule(1L, "数学", "张老师", "A101", "周一 08:00-10:00"),
                new CourseSchedule(2L, "物理", "张老师", "B202", "周三 08:00-10:00")
        );

        Set<Integer> result = WeeklySummarySupport.resolveBusyWeekdayIndices(
                schedules, s -> "张老师".equals(s.getTeacherName()));

        assertThrows(UnsupportedOperationException.class, () -> result.add(TimeSlotUtils.FRIDAY));
        assertThrows(UnsupportedOperationException.class, () -> result.remove(TimeSlotUtils.MONDAY));
        assertThrows(UnsupportedOperationException.class, () -> result.clear());
    }

    @Test
    void resolveBusyWeekdayIndices_EmptyResult_AlsoImmutable() {
        Set<Integer> result = WeeklySummarySupport.resolveBusyWeekdayIndices(
                Collections.emptyList(), MATCH_ALL);

        assertThrows(UnsupportedOperationException.class, () -> result.add(TimeSlotUtils.MONDAY));
    }
}
