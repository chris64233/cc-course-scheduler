package com.coursescheduler.service;

import com.coursescheduler.model.CourseSchedule;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ScheduleStatisticsSupport {

    private ScheduleStatisticsSupport() {
    }

    public static <T> List<T> calculateStatistics(
            List<CourseSchedule> schedules,
            Function<CourseSchedule, String> nameExtractor,
            BiFunction<String, Integer, T> dtoConstructor) {

        if (schedules == null || schedules.isEmpty()) {
            return new ArrayList<>();
        }

        Map<String, Long> countMap = schedules.stream()
                .collect(Collectors.groupingBy(nameExtractor, Collectors.counting()));

        return countMap.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry.comparingByKey()))
                .map(entry -> dtoConstructor.apply(entry.getKey(), entry.getValue().intValue()))
                .collect(Collectors.toList());
    }
}
