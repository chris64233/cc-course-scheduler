package com.coursescheduler.service;

import com.coursescheduler.dto.ClassroomCourseStatisticsResponse;
import com.coursescheduler.dto.ClassroomFreeDaySummaryResponse;
import com.coursescheduler.dto.ClassroomWeeklySummaryResponse;
import com.coursescheduler.model.CourseSchedule;

import java.util.List;

public class ClassroomStatisticsSupport {

    private ClassroomStatisticsSupport() {
    }

    public static List<ClassroomCourseStatisticsResponse> calculateStatistics(List<CourseSchedule> schedules) {
        return ScheduleStatisticsSupport.calculateStatistics(
                schedules,
                CourseSchedule::getClassroom,
                ClassroomCourseStatisticsResponse::new
        );
    }

    public static ClassroomWeeklySummaryResponse calculateWeeklySummary(String classroom, List<CourseSchedule> allSchedules) {
        WeeklySummarySupport.WeeklySummaryResult result = WeeklySummarySupport.calculate(
                allSchedules,
                s -> classroom.equals(s.getClassroom()),
                CourseSchedule::getTeacherName
        );

        return new ClassroomWeeklySummaryResponse(
                classroom,
                result.getTotalCourses(),
                result.getOccupiedDays(),
                result.getBusiestDay(),
                result.getNames()
        );
    }

    public static ClassroomFreeDaySummaryResponse calculateFreeDaySummary(String classroom, List<CourseSchedule> allSchedules) {
        return FreeDaySummarySupport.calculateFreeDaySummary(
                allSchedules,
                s -> classroom.equals(s.getClassroom()),
                (busyDays, freeDays) -> new ClassroomFreeDaySummaryResponse(classroom, busyDays, freeDays)
        );
    }
}
