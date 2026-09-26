package com.coursescheduler.service;

import com.coursescheduler.dto.CourseScheduleFilterRequest;
import com.coursescheduler.exception.InvalidTimeSlotException;
import com.coursescheduler.model.CourseSchedule;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ScheduleFilterSupportTest {

    private CourseSchedule createSampleSchedule() {
        return new CourseSchedule(1L, "高等数学", "张老师", "A101", "周一 08:00-10:00");
    }

    private CourseScheduleFilterRequest createFilterRequest(
            String courseName, String teacherName, String classroom, String timeSlot,
            String weekday, String startTimeFrom, String startTimeTo,
            String sortBy, String sortDirection) {
        CourseScheduleFilterRequest filter = new CourseScheduleFilterRequest();
        filter.setCourseName(courseName);
        filter.setTeacherName(teacherName);
        filter.setClassroom(classroom);
        filter.setTimeSlot(timeSlot);
        filter.setWeekday(weekday);
        filter.setStartTimeFrom(startTimeFrom);
        filter.setStartTimeTo(startTimeTo);
        filter.setSortBy(sortBy);
        filter.setSortDirection(sortDirection);
        return filter;
    }

    private CourseScheduleFilterRequest createFilterRequest(
            String courseName, String teacherName, String classroom, String timeSlot,
            String sortBy, String sortDirection) {
        return createFilterRequest(courseName, teacherName, classroom, timeSlot,
                null, null, null, sortBy, sortDirection);
    }

    @Test
    void testNormalize_NullFilter_ReturnsAllNull() {
        ScheduleFilterSupport.NormalizedFilter result = ScheduleFilterSupport.normalize(null);

        assertNull(result.getCourseName());
        assertNull(result.getTeacherName());
        assertNull(result.getClassroom());
        assertNull(result.getTimeSlot());
        assertNull(result.getSortBy());
        assertNull(result.getSortDirection());
    }

    @Test
    void testNormalize_TextFields_TrimmedAndEmptyToNull() {
        CourseScheduleFilterRequest request = createFilterRequest(
                "  数学  ", "  张  ", "  A101  ", null,
                "  courseName  ", "  asc  ");

        ScheduleFilterSupport.NormalizedFilter result = ScheduleFilterSupport.normalize(request);

        assertEquals("数学", result.getCourseName());
        assertEquals("张", result.getTeacherName());
        assertEquals("A101", result.getClassroom());
        assertEquals("courseName", result.getSortBy());
        assertEquals("asc", result.getSortDirection());
    }

    @Test
    void testNormalize_EmptyStringTextFields_ConvertedToNull() {
        CourseScheduleFilterRequest request = createFilterRequest(
                "", "", "", "",
                "", "");

        ScheduleFilterSupport.NormalizedFilter result = ScheduleFilterSupport.normalize(request);

        assertNull(result.getCourseName());
        assertNull(result.getTeacherName());
        assertNull(result.getClassroom());
        assertNull(result.getTimeSlot());
        assertNull(result.getSortBy());
        assertNull(result.getSortDirection());
    }

    @Test
    void testNormalize_BlankTextFields_ConvertedToNull() {
        CourseScheduleFilterRequest request = createFilterRequest(
                "   ", "   ", "   ", "   ",
                "   ", "   ");

        ScheduleFilterSupport.NormalizedFilter result = ScheduleFilterSupport.normalize(request);

        assertNull(result.getCourseName());
        assertNull(result.getTeacherName());
        assertNull(result.getClassroom());
        assertNull(result.getTimeSlot());
        assertNull(result.getSortBy());
        assertNull(result.getSortDirection());
    }

    @Test
    void testNormalize_NullTextFields_StayNull() {
        CourseScheduleFilterRequest request = createFilterRequest(
                null, null, null, null,
                null, null);

        ScheduleFilterSupport.NormalizedFilter result = ScheduleFilterSupport.normalize(request);

        assertNull(result.getCourseName());
        assertNull(result.getTeacherName());
        assertNull(result.getClassroom());
        assertNull(result.getTimeSlot());
        assertNull(result.getSortBy());
        assertNull(result.getSortDirection());
    }

    @Test
    void testNormalize_TimeSlot_Normalized() {
        CourseScheduleFilterRequest request = createFilterRequest(
                null, null, null, "  周一 8:00-10:00  ",
                null, null);

        ScheduleFilterSupport.NormalizedFilter result = ScheduleFilterSupport.normalize(request);

        assertEquals("周一 08:00-10:00", result.getTimeSlot());
    }

    @Test
    void testNormalize_TimeSlot_Null_StayNull() {
        CourseScheduleFilterRequest request = createFilterRequest(
                null, null, null, null,
                null, null);

        ScheduleFilterSupport.NormalizedFilter result = ScheduleFilterSupport.normalize(request);

        assertNull(result.getTimeSlot());
    }

    @Test
    void testMatches_CourseName_PartialMatch_ReturnsTrue() {
        CourseSchedule schedule = createSampleSchedule();
        ScheduleFilterSupport.NormalizedFilter filter = ScheduleFilterSupport.normalize(
                createFilterRequest("数学", null, null, null, null, null));

        assertTrue(ScheduleFilterSupport.matches(schedule, filter));
    }

    @Test
    void testMatches_CourseName_FullMatch_ReturnsTrue() {
        CourseSchedule schedule = createSampleSchedule();
        ScheduleFilterSupport.NormalizedFilter filter = ScheduleFilterSupport.normalize(
                createFilterRequest("高等数学", null, null, null, null, null));

        assertTrue(ScheduleFilterSupport.matches(schedule, filter));
    }

    @Test
    void testMatches_CourseName_NoMatch_ReturnsFalse() {
        CourseSchedule schedule = createSampleSchedule();
        ScheduleFilterSupport.NormalizedFilter filter = ScheduleFilterSupport.normalize(
                createFilterRequest("物理", null, null, null, null, null));

        assertFalse(ScheduleFilterSupport.matches(schedule, filter));
    }

    @Test
    void testMatches_TeacherName_PartialMatch_ReturnsTrue() {
        CourseSchedule schedule = createSampleSchedule();
        ScheduleFilterSupport.NormalizedFilter filter = ScheduleFilterSupport.normalize(
                createFilterRequest(null, "张", null, null, null, null));

        assertTrue(ScheduleFilterSupport.matches(schedule, filter));
    }

    @Test
    void testMatches_TeacherName_NoMatch_ReturnsFalse() {
        CourseSchedule schedule = createSampleSchedule();
        ScheduleFilterSupport.NormalizedFilter filter = ScheduleFilterSupport.normalize(
                createFilterRequest(null, "李", null, null, null, null));

        assertFalse(ScheduleFilterSupport.matches(schedule, filter));
    }

    @Test
    void testMatches_Classroom_PartialMatch_ReturnsTrue() {
        CourseSchedule schedule = createSampleSchedule();
        ScheduleFilterSupport.NormalizedFilter filter = ScheduleFilterSupport.normalize(
                createFilterRequest(null, null, "A10", null, null, null));

        assertTrue(ScheduleFilterSupport.matches(schedule, filter));
    }

    @Test
    void testMatches_Classroom_NoMatch_ReturnsFalse() {
        CourseSchedule schedule = createSampleSchedule();
        ScheduleFilterSupport.NormalizedFilter filter = ScheduleFilterSupport.normalize(
                createFilterRequest(null, null, "B202", null, null, null));

        assertFalse(ScheduleFilterSupport.matches(schedule, filter));
    }

    @Test
    void testMatches_TimeSlot_ExactMatch_ReturnsTrue() {
        CourseSchedule schedule = createSampleSchedule();
        ScheduleFilterSupport.NormalizedFilter filter = ScheduleFilterSupport.normalize(
                createFilterRequest(null, null, null, "周一 08:00-10:00", null, null));

        assertTrue(ScheduleFilterSupport.matches(schedule, filter));
    }

    @Test
    void testMatches_TimeSlot_DifferentButValid_ReturnsFalse() {
        CourseSchedule schedule = createSampleSchedule();
        ScheduleFilterSupport.NormalizedFilter filter = ScheduleFilterSupport.normalize(
                createFilterRequest(null, null, null, "周二 08:00-10:00", null, null));

        assertFalse(ScheduleFilterSupport.matches(schedule, filter));
    }

    @Test
    void testMatches_TimeSlot_NoMatch_ReturnsFalse() {
        CourseSchedule schedule = createSampleSchedule();
        ScheduleFilterSupport.NormalizedFilter filter = ScheduleFilterSupport.normalize(
                createFilterRequest(null, null, null, "周二 14:00-16:00", null, null));

        assertFalse(ScheduleFilterSupport.matches(schedule, filter));
    }

    @Test
    void testMatches_NoFilterConditions_ReturnsTrue() {
        CourseSchedule schedule = createSampleSchedule();
        ScheduleFilterSupport.NormalizedFilter filter = ScheduleFilterSupport.normalize(null);

        assertTrue(ScheduleFilterSupport.matches(schedule, filter));
    }

    @Test
    void testMatches_AllConditionsMatch_ReturnsTrue() {
        CourseSchedule schedule = createSampleSchedule();
        ScheduleFilterSupport.NormalizedFilter filter = ScheduleFilterSupport.normalize(
                createFilterRequest("数学", "张", "A101", "周一 08:00-10:00", null, null));

        assertTrue(ScheduleFilterSupport.matches(schedule, filter));
    }

    @Test
    void testMatches_OneConditionFails_ReturnsFalse() {
        CourseSchedule schedule = createSampleSchedule();
        ScheduleFilterSupport.NormalizedFilter filter = ScheduleFilterSupport.normalize(
                createFilterRequest("数学", "李", "A101", "周一 08:00-10:00", null, null));

        assertFalse(ScheduleFilterSupport.matches(schedule, filter));
    }

    @Test
    void testNormalize_TimeRangeFields_TrimmedAndParsed() {
        CourseScheduleFilterRequest request = createFilterRequest(
                null, null, null, null,
                "  周一  ", "  08:00  ", "  12:00  ",
                null, null);

        ScheduleFilterSupport.NormalizedFilter result = ScheduleFilterSupport.normalize(request);

        assertEquals(Integer.valueOf(0), result.getWeekdayIndex());
        assertEquals(Integer.valueOf(480), result.getStartTimeFromMinutes());
        assertEquals(Integer.valueOf(720), result.getStartTimeToMinutes());
    }

    @Test
    void testNormalize_TimeRangeFields_Null_StayNull() {
        CourseScheduleFilterRequest request = createFilterRequest(
                null, null, null, null,
                null, null, null,
                null, null);

        ScheduleFilterSupport.NormalizedFilter result = ScheduleFilterSupport.normalize(request);

        assertNull(result.getWeekdayIndex());
        assertNull(result.getStartTimeFromMinutes());
        assertNull(result.getStartTimeToMinutes());
    }

    @Test
    void testNormalize_TimeRangeFields_EmptyString_ConvertedToNull() {
        CourseScheduleFilterRequest request = createFilterRequest(
                null, null, null, null,
                "", "", "",
                null, null);

        ScheduleFilterSupport.NormalizedFilter result = ScheduleFilterSupport.normalize(request);

        assertNull(result.getWeekdayIndex());
        assertNull(result.getStartTimeFromMinutes());
        assertNull(result.getStartTimeToMinutes());
    }

    @Test
    void testMatches_WeekdayOnly_MatchingWeekday_ReturnsTrue() {
        CourseSchedule schedule = createSampleSchedule();
        ScheduleFilterSupport.NormalizedFilter filter = ScheduleFilterSupport.normalize(
                createFilterRequest(null, null, null, null,
                        "周一", null, null, null, null));

        assertTrue(ScheduleFilterSupport.matches(schedule, filter));
    }

    @Test
    void testMatches_WeekdayOnly_NonMatchingWeekday_ReturnsFalse() {
        CourseSchedule schedule = createSampleSchedule();
        ScheduleFilterSupport.NormalizedFilter filter = ScheduleFilterSupport.normalize(
                createFilterRequest(null, null, null, null,
                        "周二", null, null, null, null));

        assertFalse(ScheduleFilterSupport.matches(schedule, filter));
    }

    @Test
    void testMatches_FullTimeRange_WithinRange_ReturnsTrue() {
        CourseSchedule schedule = createSampleSchedule();
        ScheduleFilterSupport.NormalizedFilter filter = ScheduleFilterSupport.normalize(
                createFilterRequest(null, null, null, null,
                        "周一", "08:00", "12:00", null, null));

        assertTrue(ScheduleFilterSupport.matches(schedule, filter));
    }

    @Test
    void testMatches_FullTimeRange_StartTimeEqualsFrom_ReturnsTrue() {
        CourseSchedule schedule = createSampleSchedule();
        ScheduleFilterSupport.NormalizedFilter filter = ScheduleFilterSupport.normalize(
                createFilterRequest(null, null, null, null,
                        "周一", "08:00", "08:00", null, null));

        assertTrue(ScheduleFilterSupport.matches(schedule, filter));
    }

    @Test
    void testMatches_FullTimeRange_StartTimeBeforeFrom_ReturnsFalse() {
        CourseSchedule schedule = new CourseSchedule(1L, "高等数学", "张老师", "A101", "周一 07:30-09:30");
        ScheduleFilterSupport.NormalizedFilter filter = ScheduleFilterSupport.normalize(
                createFilterRequest(null, null, null, null,
                        "周一", "08:00", "12:00", null, null));

        assertFalse(ScheduleFilterSupport.matches(schedule, filter));
    }

    @Test
    void testMatches_FullTimeRange_StartTimeAfterTo_ReturnsFalse() {
        CourseSchedule schedule = new CourseSchedule(1L, "高等数学", "张老师", "A101", "周一 13:00-15:00");
        ScheduleFilterSupport.NormalizedFilter filter = ScheduleFilterSupport.normalize(
                createFilterRequest(null, null, null, null,
                        "周一", "08:00", "12:00", null, null));

        assertFalse(ScheduleFilterSupport.matches(schedule, filter));
    }

    @Test
    void testMatches_StartTimeFromOnly_WithinRange_ReturnsTrue() {
        CourseSchedule schedule = createSampleSchedule();
        ScheduleFilterSupport.NormalizedFilter filter = ScheduleFilterSupport.normalize(
                createFilterRequest(null, null, null, null,
                        "周一", "08:00", null, null, null));

        assertTrue(ScheduleFilterSupport.matches(schedule, filter));
    }

    @Test
    void testMatches_StartTimeFromOnly_BeforeFrom_ReturnsFalse() {
        CourseSchedule schedule = new CourseSchedule(1L, "高等数学", "张老师", "A101", "周一 07:00-09:00");
        ScheduleFilterSupport.NormalizedFilter filter = ScheduleFilterSupport.normalize(
                createFilterRequest(null, null, null, null,
                        "周一", "08:00", null, null, null));

        assertFalse(ScheduleFilterSupport.matches(schedule, filter));
    }

    @Test
    void testMatches_StartTimeToOnly_WithinRange_ReturnsTrue() {
        CourseSchedule schedule = createSampleSchedule();
        ScheduleFilterSupport.NormalizedFilter filter = ScheduleFilterSupport.normalize(
                createFilterRequest(null, null, null, null,
                        "周一", null, "12:00", null, null));

        assertTrue(ScheduleFilterSupport.matches(schedule, filter));
    }

    @Test
    void testMatches_StartTimeToOnly_AfterTo_ReturnsFalse() {
        CourseSchedule schedule = new CourseSchedule(1L, "高等数学", "张老师", "A101", "周一 13:00-15:00");
        ScheduleFilterSupport.NormalizedFilter filter = ScheduleFilterSupport.normalize(
                createFilterRequest(null, null, null, null,
                        "周一", null, "12:00", null, null));

        assertFalse(ScheduleFilterSupport.matches(schedule, filter));
    }

    @Test
    void testMatches_TimeRangeWithWeekdayNumber_WorksCorrectly() {
        CourseSchedule schedule = createSampleSchedule();
        ScheduleFilterSupport.NormalizedFilter filter = ScheduleFilterSupport.normalize(
                createFilterRequest(null, null, null, null,
                        "1", "08:00", "12:00", null, null));

        assertTrue(ScheduleFilterSupport.matches(schedule, filter));
    }

    @Test
    void testMatches_TimeRangeCombinedWithOtherFilters_AllMatch_ReturnsTrue() {
        CourseSchedule schedule = createSampleSchedule();
        ScheduleFilterSupport.NormalizedFilter filter = ScheduleFilterSupport.normalize(
                createFilterRequest("数学", "张", "A101", null,
                        "周一", "08:00", "12:00", null, null));

        assertTrue(ScheduleFilterSupport.matches(schedule, filter));
    }

    @Test
    void testMatches_TimeRangeCombinedWithOtherFilters_OneFails_ReturnsFalse() {
        CourseSchedule schedule = createSampleSchedule();
        ScheduleFilterSupport.NormalizedFilter filter = ScheduleFilterSupport.normalize(
                createFilterRequest("物理", "张", "A101", null,
                        "周一", "08:00", "12:00", null, null));

        assertFalse(ScheduleFilterSupport.matches(schedule, filter));
    }

    @Test
    void testMatches_DifferentWeekdaySameTimeRange_ReturnsFalse() {
        CourseSchedule schedule = new CourseSchedule(1L, "高等数学", "张老师", "A101", "周二 08:00-10:00");
        ScheduleFilterSupport.NormalizedFilter filter = ScheduleFilterSupport.normalize(
                createFilterRequest(null, null, null, null,
                        "周一", "08:00", "12:00", null, null));

        assertFalse(ScheduleFilterSupport.matches(schedule, filter));
    }

    @Test
    void testMatches_NoTimeRangeConditions_ReturnsTrue() {
        CourseSchedule schedule = createSampleSchedule();
        ScheduleFilterSupport.NormalizedFilter filter = ScheduleFilterSupport.normalize(
                createFilterRequest(null, null, null, null,
                        null, null, null, null, null));

        assertTrue(ScheduleFilterSupport.matches(schedule, filter));
    }

    @Test
    void testNormalize_StartTimeFromGreaterThanTo_ThrowsInvalidTimeSlotException() {
        CourseScheduleFilterRequest request = createFilterRequest(
                null, null, null, null,
                "周一", "12:00", "08:00",
                null, null);

        InvalidTimeSlotException exception = assertThrows(InvalidTimeSlotException.class, () -> {
            ScheduleFilterSupport.normalize(request);
        });

        assertTrue(exception.getMessage().contains("startTimeFrom 必须早于或等于 startTimeTo"));
        assertTrue(exception.getMessage().contains("12:00"));
        assertTrue(exception.getMessage().contains("08:00"));
    }

    @Test
    void testNormalize_StartTimeFromEqualsTo_NoException() {
        CourseScheduleFilterRequest request = createFilterRequest(
                null, null, null, null,
                "周一", "08:00", "08:00",
                null, null);

        ScheduleFilterSupport.NormalizedFilter result = ScheduleFilterSupport.normalize(request);

        assertEquals(Integer.valueOf(480), result.getStartTimeFromMinutes());
        assertEquals(Integer.valueOf(480), result.getStartTimeToMinutes());
    }

    @Test
    void testNormalize_OnlyStartTimeFrom_NoValidation() {
        CourseScheduleFilterRequest request = createFilterRequest(
                null, null, null, null,
                "周一", "12:00", null,
                null, null);

        ScheduleFilterSupport.NormalizedFilter result = ScheduleFilterSupport.normalize(request);

        assertEquals(Integer.valueOf(720), result.getStartTimeFromMinutes());
        assertNull(result.getStartTimeToMinutes());
    }

    @Test
    void testNormalize_OnlyStartTimeTo_NoValidation() {
        CourseScheduleFilterRequest request = createFilterRequest(
                null, null, null, null,
                "周一", null, "08:00",
                null, null);

        ScheduleFilterSupport.NormalizedFilter result = ScheduleFilterSupport.normalize(request);

        assertNull(result.getStartTimeFromMinutes());
        assertEquals(Integer.valueOf(480), result.getStartTimeToMinutes());
    }
}
