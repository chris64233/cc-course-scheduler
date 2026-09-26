package com.coursescheduler.service;

import com.coursescheduler.dto.CourseScheduleResponse;

import java.nio.charset.StandardCharsets;
import java.util.List;

public final class CourseScheduleCsvSupport {

    private static final String BOM = "\uFEFF";
    private static final String COURSE_LIST_HEADER = "课程名称,老师,教室,时间段\n";

    private CourseScheduleCsvSupport() {
    }

    public static byte[] buildCourseListCsv(List<CourseScheduleResponse> schedules) {
        StringBuilder csv = new StringBuilder();
        csv.append(BOM);
        csv.append(COURSE_LIST_HEADER);

        if (schedules != null) {
            for (CourseScheduleResponse row : schedules) {
                csv.append(CsvSupport.escapeField(row.getCourseName())).append(',');
                csv.append(CsvSupport.escapeField(row.getTeacherName())).append(',');
                csv.append(CsvSupport.escapeField(row.getClassroom())).append(',');
                csv.append(CsvSupport.escapeField(row.getTimeSlot())).append('\n');
            }
        }

        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }
}
