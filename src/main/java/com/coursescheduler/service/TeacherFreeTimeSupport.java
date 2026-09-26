package com.coursescheduler.service;

import com.coursescheduler.dto.TeacherFreeTimeResponse;
import com.coursescheduler.dto.TeacherFreeTimeResponse.FreeTimeWindow;
import com.coursescheduler.dto.TeacherFreeTimeResponse.OccupiedSlot;
import com.coursescheduler.exception.InvalidRequestParameterException;
import com.coursescheduler.exception.InvalidTimeSlotException;
import com.coursescheduler.model.CourseSchedule;
import com.coursescheduler.util.TimeSlotUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class TeacherFreeTimeSupport {

    private TeacherFreeTimeSupport() {
    }

    public static void validateRequest(String teacherName, String weekday,
                                        String startTimeFrom, String startTimeTo) {
        if (teacherName == null || teacherName.trim().isEmpty()) {
            throw new InvalidRequestParameterException("老师名不能为空");
        }
        if (weekday == null || weekday.trim().isEmpty()) {
            throw new InvalidRequestParameterException("周几不能为空");
        }
        if (startTimeFrom == null || startTimeFrom.trim().isEmpty()) {
            throw new InvalidRequestParameterException("开始时间不能为空");
        }
        if (startTimeTo == null || startTimeTo.trim().isEmpty()) {
            throw new InvalidRequestParameterException("结束时间不能为空");
        }
    }

    public static int[] parseTimeRange(String startTimeFrom, String startTimeTo) {
        int fromMinutes = TimeSlotUtils.parseTimePoint(startTimeFrom);
        int toMinutes = TimeSlotUtils.parseTimePoint(startTimeTo);
        if (fromMinutes >= toMinutes) {
            throw new InvalidTimeSlotException(
                    "时间范围不合法，开始时间必须早于结束时间，实际输入："
                            + startTimeFrom + " - " + startTimeTo);
        }
        return new int[]{fromMinutes, toMinutes};
    }

    public static TeacherFreeTimeResponse calculate(
            List<CourseSchedule> allSchedules,
            String normalizedTeacherName,
            int weekdayIndex,
            int startTimeFromMinutes,
            int startTimeToMinutes) {

        List<CourseSchedule> teacherCoursesOnWeekday = allSchedules.stream()
                .filter(s -> s.getTeacherName().equals(normalizedTeacherName))
                .filter(s -> TimeSlotUtils.extractWeekdayIndexFromNormalized(s.getTimeSlot()) == weekdayIndex)
                .collect(java.util.stream.Collectors.toList());

        List<OccupiedSlot> occupiedSlots = new ArrayList<>();
        List<int[]> occupiedIntervals = new ArrayList<>();

        for (CourseSchedule course : teacherCoursesOnWeekday) {
            int courseStart = TimeSlotUtils.extractStartTimeMinutesFromNormalized(course.getTimeSlot());
            int courseEnd = TimeSlotUtils.extractEndTimeMinutesFromNormalized(course.getTimeSlot());

            if (courseStart < startTimeToMinutes && courseEnd > startTimeFromMinutes) {
                occupiedSlots.add(new OccupiedSlot(
                        course.getTimeSlot(),
                        course.getCourseName(),
                        course.getClassroom()
                ));
                int overlapStart = Math.max(courseStart, startTimeFromMinutes);
                int overlapEnd = Math.min(courseEnd, startTimeToMinutes);
                occupiedIntervals.add(new int[]{overlapStart, overlapEnd});
            }
        }

        occupiedIntervals.sort(Comparator.comparingInt(a -> a[0]));
        List<int[]> merged = mergeIntervals(occupiedIntervals);

        List<FreeTimeWindow> freeTimeWindows = new ArrayList<>();
        int currentStart = startTimeFromMinutes;
        for (int[] interval : merged) {
            if (currentStart < interval[0]) {
                freeTimeWindows.add(new FreeTimeWindow(
                        TimeSlotUtils.minutesToTimePoint(currentStart),
                        TimeSlotUtils.minutesToTimePoint(interval[0])
                ));
            }
            currentStart = Math.max(currentStart, interval[1]);
        }
        if (currentStart < startTimeToMinutes) {
            freeTimeWindows.add(new FreeTimeWindow(
                    TimeSlotUtils.minutesToTimePoint(currentStart),
                    TimeSlotUtils.minutesToTimePoint(startTimeToMinutes)
            ));
        }

        occupiedSlots.sort(Comparator.comparingInt(s ->
                TimeSlotUtils.extractStartTimeMinutesFromNormalized(s.getTimeSlot())));

        String weekdayName = TimeSlotUtils.getWeekdayName(weekdayIndex);
        boolean fullyAvailable = occupiedSlots.isEmpty();

        return new TeacherFreeTimeResponse(
                normalizedTeacherName,
                weekdayName,
                TimeSlotUtils.minutesToTimePoint(startTimeFromMinutes),
                TimeSlotUtils.minutesToTimePoint(startTimeToMinutes),
                fullyAvailable,
                occupiedSlots,
                freeTimeWindows
        );
    }

    private static List<int[]> mergeIntervals(List<int[]> intervals) {
        if (intervals.isEmpty()) {
            return intervals;
        }
        List<int[]> merged = new ArrayList<>();
        int[] current = intervals.get(0).clone();
        for (int i = 1; i < intervals.size(); i++) {
            int[] next = intervals.get(i);
            if (next[0] <= current[1]) {
                current[1] = Math.max(current[1], next[1]);
            } else {
                merged.add(current);
                current = next.clone();
            }
        }
        merged.add(current);
        return merged;
    }
}
