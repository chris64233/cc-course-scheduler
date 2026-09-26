package com.coursescheduler.service;

import com.coursescheduler.dto.TeacherFreeTimeResponse;
import com.coursescheduler.exception.InvalidRequestParameterException;
import com.coursescheduler.exception.InvalidTimeSlotException;
import com.coursescheduler.model.CourseSchedule;
import com.coursescheduler.util.TimeSlotUtils;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TeacherFreeTimeSupportTest {

    @Test
    void validateRequest_教师名为null_抛出异常() {
        InvalidRequestParameterException ex = assertThrows(InvalidRequestParameterException.class,
                () -> TeacherFreeTimeSupport.validateRequest(null, "周一", "08:00", "16:00"));
        assertEquals("老师名不能为空", ex.getMessage());
    }

    @Test
    void validateRequest_教师名为空字符串_抛出异常() {
        InvalidRequestParameterException ex = assertThrows(InvalidRequestParameterException.class,
                () -> TeacherFreeTimeSupport.validateRequest("", "周一", "08:00", "16:00"));
        assertEquals("老师名不能为空", ex.getMessage());
    }

    @Test
    void validateRequest_教师名为空白_抛出异常() {
        InvalidRequestParameterException ex = assertThrows(InvalidRequestParameterException.class,
                () -> TeacherFreeTimeSupport.validateRequest("   ", "周一", "08:00", "16:00"));
        assertEquals("老师名不能为空", ex.getMessage());
    }

    @Test
    void validateRequest_周几为null_抛出异常() {
        InvalidRequestParameterException ex = assertThrows(InvalidRequestParameterException.class,
                () -> TeacherFreeTimeSupport.validateRequest("张老师", null, "08:00", "16:00"));
        assertEquals("周几不能为空", ex.getMessage());
    }

    @Test
    void validateRequest_周几为空字符串_抛出异常() {
        InvalidRequestParameterException ex = assertThrows(InvalidRequestParameterException.class,
                () -> TeacherFreeTimeSupport.validateRequest("张老师", "", "08:00", "16:00"));
        assertEquals("周几不能为空", ex.getMessage());
    }

    @Test
    void validateRequest_开始时间为null_抛出异常() {
        InvalidRequestParameterException ex = assertThrows(InvalidRequestParameterException.class,
                () -> TeacherFreeTimeSupport.validateRequest("张老师", "周一", null, "16:00"));
        assertEquals("开始时间不能为空", ex.getMessage());
    }

    @Test
    void validateRequest_开始时间为空字符串_抛出异常() {
        InvalidRequestParameterException ex = assertThrows(InvalidRequestParameterException.class,
                () -> TeacherFreeTimeSupport.validateRequest("张老师", "周一", "", "16:00"));
        assertEquals("开始时间不能为空", ex.getMessage());
    }

    @Test
    void validateRequest_结束时间为null_抛出异常() {
        InvalidRequestParameterException ex = assertThrows(InvalidRequestParameterException.class,
                () -> TeacherFreeTimeSupport.validateRequest("张老师", "周一", "08:00", null));
        assertEquals("结束时间不能为空", ex.getMessage());
    }

    @Test
    void validateRequest_结束时间为空字符串_抛出异常() {
        InvalidRequestParameterException ex = assertThrows(InvalidRequestParameterException.class,
                () -> TeacherFreeTimeSupport.validateRequest("张老师", "周一", "08:00", ""));
        assertEquals("结束时间不能为空", ex.getMessage());
    }

    @Test
    void validateRequest_合法参数_不抛出异常() {
        assertDoesNotThrow(() -> TeacherFreeTimeSupport.validateRequest("张老师", "周一", "08:00", "16:00"));
    }

    @Test
    void parseTimeRange_合法时间_返回分钟数数组() {
        int[] result = TeacherFreeTimeSupport.parseTimeRange("08:00", "10:30");

        assertEquals(2, result.length);
        assertEquals(480, result[0]);
        assertEquals(630, result[1]);
    }

    @Test
    void parseTimeRange_开始时间等于结束时间_抛出异常() {
        assertThrows(InvalidTimeSlotException.class,
                () -> TeacherFreeTimeSupport.parseTimeRange("08:00", "08:00"));
    }

    @Test
    void parseTimeRange_开始时间大于结束时间_抛出异常() {
        assertThrows(InvalidTimeSlotException.class,
                () -> TeacherFreeTimeSupport.parseTimeRange("10:00", "08:00"));
    }

    @Test
    void parseTimeRange_非法格式_抛出异常() {
        assertThrows(InvalidTimeSlotException.class,
                () -> TeacherFreeTimeSupport.parseTimeRange("abc", "10:00"));
    }

    @Test
    void parseTimeRange_结束时间非法格式_抛出异常() {
        assertThrows(InvalidTimeSlotException.class,
                () -> TeacherFreeTimeSupport.parseTimeRange("08:00", "xyz"));
    }

    @Test
    void calculate_该工作日无课程_完全空闲() {
        List<CourseSchedule> schedules = Collections.emptyList();

        TeacherFreeTimeResponse response = TeacherFreeTimeSupport.calculate(
                schedules, "张老师", TimeSlotUtils.MONDAY, 480, 960);

        assertTrue(response.isFullyAvailable());
        assertTrue(response.getOccupiedSlots().isEmpty());
        assertEquals(1, response.getFreeTimeWindows().size());
        assertEquals("08:00", response.getFreeTimeWindows().get(0).getStartTime());
        assertEquals("16:00", response.getFreeTimeWindows().get(0).getEndTime());
    }

    @Test
    void calculate_范围内有一节课_部分占用() {
        List<CourseSchedule> schedules = Arrays.asList(
                new CourseSchedule(1L, "数学", "张老师", "A101", "周一 10:00-12:00")
        );

        TeacherFreeTimeResponse response = TeacherFreeTimeSupport.calculate(
                schedules, "张老师", TimeSlotUtils.MONDAY, 480, 960);

        assertFalse(response.isFullyAvailable());
        assertEquals(1, response.getOccupiedSlots().size());
        assertEquals("数学", response.getOccupiedSlots().get(0).getCourseName());
        assertEquals("A101", response.getOccupiedSlots().get(0).getClassroom());
        assertEquals("周一 10:00-12:00", response.getOccupiedSlots().get(0).getTimeSlot());
        assertEquals(2, response.getFreeTimeWindows().size());
        assertEquals("08:00", response.getFreeTimeWindows().get(0).getStartTime());
        assertEquals("10:00", response.getFreeTimeWindows().get(0).getEndTime());
        assertEquals("12:00", response.getFreeTimeWindows().get(1).getStartTime());
        assertEquals("16:00", response.getFreeTimeWindows().get(1).getEndTime());
    }

    @Test
    void calculate_课程覆盖整个范围_完全占用() {
        List<CourseSchedule> schedules = Arrays.asList(
                new CourseSchedule(1L, "数学", "张老师", "A101", "周一 08:00-16:00")
        );

        TeacherFreeTimeResponse response = TeacherFreeTimeSupport.calculate(
                schedules, "张老师", TimeSlotUtils.MONDAY, 480, 960);

        assertFalse(response.isFullyAvailable());
        assertEquals(1, response.getOccupiedSlots().size());
        assertTrue(response.getFreeTimeWindows().isEmpty());
    }

    @Test
    void calculate_课程不在查询范围内_完全空闲() {
        List<CourseSchedule> schedules = Arrays.asList(
                new CourseSchedule(1L, "数学", "张老师", "A101", "周一 08:00-10:00")
        );

        TeacherFreeTimeResponse response = TeacherFreeTimeSupport.calculate(
                schedules, "张老师", TimeSlotUtils.MONDAY, 720, 960);

        assertTrue(response.isFullyAvailable());
        assertTrue(response.getOccupiedSlots().isEmpty());
        assertEquals(1, response.getFreeTimeWindows().size());
        assertEquals("12:00", response.getFreeTimeWindows().get(0).getStartTime());
        assertEquals("16:00", response.getFreeTimeWindows().get(0).getEndTime());
    }

    @Test
    void calculate_多节课之间有空隙_正确的空闲窗口() {
        List<CourseSchedule> schedules = Arrays.asList(
                new CourseSchedule(1L, "数学", "张老师", "A101", "周一 08:00-10:00"),
                new CourseSchedule(2L, "物理", "张老师", "B202", "周一 12:00-14:00")
        );

        TeacherFreeTimeResponse response = TeacherFreeTimeSupport.calculate(
                schedules, "张老师", TimeSlotUtils.MONDAY, 480, 960);

        assertFalse(response.isFullyAvailable());
        assertEquals(2, response.getOccupiedSlots().size());
        assertEquals(2, response.getFreeTimeWindows().size());
        assertEquals("10:00", response.getFreeTimeWindows().get(0).getStartTime());
        assertEquals("12:00", response.getFreeTimeWindows().get(0).getEndTime());
        assertEquals("14:00", response.getFreeTimeWindows().get(1).getStartTime());
        assertEquals("16:00", response.getFreeTimeWindows().get(1).getEndTime());
    }

    @Test
    void calculate_课程时间重叠_合并区间后计算空闲窗口() {
        List<CourseSchedule> schedules = Arrays.asList(
                new CourseSchedule(1L, "数学", "张老师", "A101", "周一 08:00-11:00"),
                new CourseSchedule(2L, "物理", "张老师", "B202", "周一 10:00-12:00")
        );

        TeacherFreeTimeResponse response = TeacherFreeTimeSupport.calculate(
                schedules, "张老师", TimeSlotUtils.MONDAY, 480, 960);

        assertFalse(response.isFullyAvailable());
        assertEquals(2, response.getOccupiedSlots().size());
        assertEquals(1, response.getFreeTimeWindows().size());
        assertEquals("12:00", response.getFreeTimeWindows().get(0).getStartTime());
        assertEquals("16:00", response.getFreeTimeWindows().get(0).getEndTime());
    }

    @Test
    void calculate_课程部分重叠范围开始_正确裁剪() {
        List<CourseSchedule> schedules = Arrays.asList(
                new CourseSchedule(1L, "数学", "张老师", "A101", "周一 07:00-09:00")
        );

        TeacherFreeTimeResponse response = TeacherFreeTimeSupport.calculate(
                schedules, "张老师", TimeSlotUtils.MONDAY, 480, 960);

        assertFalse(response.isFullyAvailable());
        assertEquals(1, response.getOccupiedSlots().size());
        assertEquals(1, response.getFreeTimeWindows().size());
        assertEquals("09:00", response.getFreeTimeWindows().get(0).getStartTime());
        assertEquals("16:00", response.getFreeTimeWindows().get(0).getEndTime());
    }

    @Test
    void calculate_课程部分重叠范围结束_正确裁剪() {
        List<CourseSchedule> schedules = Arrays.asList(
                new CourseSchedule(1L, "数学", "张老师", "A101", "周一 15:00-17:00")
        );

        TeacherFreeTimeResponse response = TeacherFreeTimeSupport.calculate(
                schedules, "张老师", TimeSlotUtils.MONDAY, 480, 960);

        assertFalse(response.isFullyAvailable());
        assertEquals(1, response.getOccupiedSlots().size());
        assertEquals(1, response.getFreeTimeWindows().size());
        assertEquals("08:00", response.getFreeTimeWindows().get(0).getStartTime());
        assertEquals("15:00", response.getFreeTimeWindows().get(0).getEndTime());
    }

    @Test
    void calculate_其他教师的课程被忽略() {
        List<CourseSchedule> schedules = Arrays.asList(
                new CourseSchedule(1L, "数学", "李老师", "A101", "周一 08:00-10:00"),
                new CourseSchedule(2L, "物理", "李老师", "B202", "周一 10:00-12:00")
        );

        TeacherFreeTimeResponse response = TeacherFreeTimeSupport.calculate(
                schedules, "张老师", TimeSlotUtils.MONDAY, 480, 960);

        assertTrue(response.isFullyAvailable());
        assertTrue(response.getOccupiedSlots().isEmpty());
        assertEquals(1, response.getFreeTimeWindows().size());
        assertEquals("08:00", response.getFreeTimeWindows().get(0).getStartTime());
        assertEquals("16:00", response.getFreeTimeWindows().get(0).getEndTime());
    }
}
