package com.coursescheduler.service;

import com.coursescheduler.dto.CourseScheduleResponse;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class CourseScheduleCsvSupportTest {

    @Test
    void buildCourseListCsv_空列表_只返回BOM加表头() {
        byte[] csvBytes = CourseScheduleCsvSupport.buildCourseListCsv(Collections.emptyList());
        String csv = new String(csvBytes, StandardCharsets.UTF_8);

        assertEquals("\uFEFF课程名称,老师,教室,时间段\n", csv);
    }

    @Test
    void buildCourseListCsv_null列表_只返回BOM加表头() {
        byte[] csvBytes = CourseScheduleCsvSupport.buildCourseListCsv(null);
        String csv = new String(csvBytes, StandardCharsets.UTF_8);

        assertEquals("\uFEFF课程名称,老师,教室,时间段\n", csv);
    }

    @Test
    void buildCourseListCsv_单条普通记录_正确输出() {
        CourseScheduleResponse row = new CourseScheduleResponse(
                1L, "数学", "张老师", "A101", "周一 08:00-10:00");

        byte[] csvBytes = CourseScheduleCsvSupport.buildCourseListCsv(Collections.singletonList(row));
        String csv = new String(csvBytes, StandardCharsets.UTF_8);

        StringBuilder expected = new StringBuilder();
        expected.append("\uFEFF");
        expected.append("课程名称,老师,教室,时间段\n");
        expected.append("数学,张老师,A101,周一 08:00-10:00\n");

        assertEquals(expected.toString(), csv);
    }

    @Test
    void buildCourseListCsv_多条记录_按输入顺序输出() {
        CourseScheduleResponse row1 = new CourseScheduleResponse(
                1L, "数学", "张老师", "A101", "周一 08:00-10:00");
        CourseScheduleResponse row2 = new CourseScheduleResponse(
                2L, "英语", "李老师", "B202", "周二 14:00-16:00");
        CourseScheduleResponse row3 = new CourseScheduleResponse(
                3L, "物理", "王老师", "C303", "周三 10:00-12:00");

        byte[] csvBytes = CourseScheduleCsvSupport.buildCourseListCsv(Arrays.asList(row1, row2, row3));
        String csv = new String(csvBytes, StandardCharsets.UTF_8);

        StringBuilder expected = new StringBuilder();
        expected.append("\uFEFF");
        expected.append("课程名称,老师,教室,时间段\n");
        expected.append("数学,张老师,A101,周一 08:00-10:00\n");
        expected.append("英语,李老师,B202,周二 14:00-16:00\n");
        expected.append("物理,王老师,C303,周三 10:00-12:00\n");

        assertEquals(expected.toString(), csv);
    }

    @Test
    void buildCourseListCsv_含逗号和引号的字段_正确转义() {
        CourseScheduleResponse row = new CourseScheduleResponse(
                1L, "数学,高等", "张\"教授\"", "教,室 101", "周一 08:00-10:00");

        byte[] csvBytes = CourseScheduleCsvSupport.buildCourseListCsv(Collections.singletonList(row));
        String csv = new String(csvBytes, StandardCharsets.UTF_8);

        StringBuilder expected = new StringBuilder();
        expected.append("\uFEFF");
        expected.append("课程名称,老师,教室,时间段\n");
        expected.append("\"数学,高等\"").append(',');
        expected.append("\"张\"\"教授\"\"\"").append(',');
        expected.append("\"教,室 101\"").append(',');
        expected.append("周一 08:00-10:00").append('\n');

        assertEquals(expected.toString(), csv);
    }

    @Test
    void buildCourseListCsv_含换行回车的字段_正确转义() {
        CourseScheduleResponse row = new CourseScheduleResponse(
                1L, "数学\n高等数学", "张\r教授", "教室\n101", "周一 08:00-10:00");

        byte[] csvBytes = CourseScheduleCsvSupport.buildCourseListCsv(Collections.singletonList(row));
        String csv = new String(csvBytes, StandardCharsets.UTF_8);

        StringBuilder expected = new StringBuilder();
        expected.append("\uFEFF");
        expected.append("课程名称,老师,教室,时间段\n");
        expected.append("\"数学\n高等数学\"").append(',');
        expected.append("\"张\r教授\"").append(',');
        expected.append("\"教室\n101\"").append(',');
        expected.append("周一 08:00-10:00").append('\n');

        assertEquals(expected.toString(), csv);
    }

    @Test
    void buildCourseListCsv_null字段_转空字符串() {
        CourseScheduleResponse row = new CourseScheduleResponse(
                1L, null, null, null, null);

        byte[] csvBytes = CourseScheduleCsvSupport.buildCourseListCsv(Collections.singletonList(row));
        String csv = new String(csvBytes, StandardCharsets.UTF_8);

        StringBuilder expected = new StringBuilder();
        expected.append("\uFEFF");
        expected.append("课程名称,老师,教室,时间段\n");
        expected.append(",,,\n");

        assertEquals(expected.toString(), csv);
    }

    @Test
    void buildCourseListCsv_返回UTF_8编码字节数组() {
        CourseScheduleResponse row = new CourseScheduleResponse(
                1L, "数学", "张老师", "A101", "周一 08:00-10:00");

        byte[] csvBytes = CourseScheduleCsvSupport.buildCourseListCsv(Collections.singletonList(row));
        String csv = new String(csvBytes, StandardCharsets.UTF_8);

        assertTrue(csv.startsWith("\uFEFF"));
        assertTrue(csv.contains("数学"));
        assertTrue(csv.contains("张老师"));
    }
}
