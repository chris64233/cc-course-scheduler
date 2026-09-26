package com.coursescheduler.service;

import com.coursescheduler.exception.InvalidTimeSlotException;
import com.coursescheduler.model.CourseSchedule;
import com.coursescheduler.util.TimeSlotUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public final class WeeklySummarySupport {

    private WeeklySummarySupport() {
    }

    /**
     * 周摘要统计结果。
     *
     * <p>包含：有效课程数、有课天数、最忙星期、去重后的名单列表。
     */
    public static final class WeeklySummaryResult {
        private final int totalCourses;
        private final int occupiedDays;
        private final String busiestDay;
        private final List<String> names;

        WeeklySummaryResult(int totalCourses, int occupiedDays, String busiestDay, List<String> names) {
            this.totalCourses = totalCourses;
            this.occupiedDays = occupiedDays;
            this.busiestDay = busiestDay;
            this.names = (names == null || names.isEmpty())
                    ? Collections.emptyList()
                    : Collections.unmodifiableList(new ArrayList<>(names));
        }

        public int getTotalCourses() {
            return totalCourses;
        }

        public int getOccupiedDays() {
            return occupiedDays;
        }

        public String getBusiestDay() {
            return busiestDay;
        }

        public List<String> getNames() {
            return names;
        }
    }

    /**
     * 计算周摘要。
     *
     * <p>过滤流程与边界：
     * <ol>
     *   <li>列表中的 {@code null} 课程直接跳过，<b>matcher 不会接收到 null schedule</b></li>
     *   <li>然后调用 {@code matcher} 进行业务匹配（如匹配指定老师/教室）；
     *       注意：matcher 可能接收到 timeSlot 为 null 或格式非法的课程，需要自行处理边界</li>
     *   <li>匹配成功后，timeSlot 为 null 或解析失败的课程跳过，
     *       不计入 totalCourses / occupiedDays / busiestDay</li>
     * </ol>
     *
     * @param allSchedules 全部课程列表
     * @param matcher      课程匹配条件（如：匹配指定老师或指定教室）
     * @param nameExtractor 名单字段提取函数（如：CourseSchedule::getCourseName 或 CourseSchedule::getTeacherName）
     * @return 周摘要统计结果
     * @throws IllegalArgumentException 如果 matcher 或 nameExtractor 为 null
     */
    public static WeeklySummaryResult calculate(
            List<CourseSchedule> allSchedules,
            Predicate<CourseSchedule> matcher,
            Function<CourseSchedule, String> nameExtractor) {

        if (matcher == null) {
            throw new IllegalArgumentException("matcher 不能为空");
        }
        if (nameExtractor == null) {
            throw new IllegalArgumentException("nameExtractor 不能为空");
        }

        List<WeeklyEntry> entries = filterAndResolveWeekdays(allSchedules, matcher);

        int totalCourses = entries.size();

        Set<Integer> occupiedWeekdayIndices = new LinkedHashSet<>();
        int[] weekdayCounts = new int[TimeSlotUtils.WEEKDAY_COUNT];
        for (WeeklyEntry entry : entries) {
            int idx = entry.weekdayIndex;
            occupiedWeekdayIndices.add(idx);
            weekdayCounts[idx]++;
        }
        int occupiedDays = occupiedWeekdayIndices.size();

        String busiestDay = null;
        if (occupiedDays > 0) {
            int maxCount = -1;
            int busiestIdx = -1;
            for (int i = 0; i < TimeSlotUtils.WEEKDAY_COUNT; i++) {
                if (weekdayCounts[i] > maxCount) {
                    maxCount = weekdayCounts[i];
                    busiestIdx = i;
                }
            }
            busiestDay = TimeSlotUtils.getWeekdayName(busiestIdx);
        }

        List<String> names = entries.stream()
                .map(entry -> nameExtractor.apply(entry.schedule))
                .filter(name -> name != null)
                .distinct()
                .collect(Collectors.toList());

        return new WeeklySummaryResult(totalCourses, occupiedDays, busiestDay, names);
    }

    public static Set<Integer> resolveBusyWeekdayIndices(
            List<CourseSchedule> allSchedules, Predicate<CourseSchedule> matcher) {
        if (matcher == null) {
            throw new IllegalArgumentException("matcher 不能为空");
        }
        List<WeeklyEntry> entries = filterAndResolveWeekdays(allSchedules, matcher);
        Set<Integer> indices = new LinkedHashSet<>();
        for (WeeklyEntry entry : entries) {
            indices.add(entry.weekdayIndex);
        }
        if (indices.isEmpty()) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(indices);
    }

    private static List<WeeklyEntry> filterAndResolveWeekdays(
            List<CourseSchedule> allSchedules, Predicate<CourseSchedule> matcher) {
        if (allSchedules == null || allSchedules.isEmpty()) {
            return Collections.emptyList();
        }
        List<WeeklyEntry> result = new ArrayList<>();
        for (CourseSchedule s : allSchedules) {
            if (s == null) {
                continue;
            }
            if (!matcher.test(s)) {
                continue;
            }
            if (s.getTimeSlot() == null) {
                continue;
            }
            int weekdayIdx;
            try {
                weekdayIdx = TimeSlotUtils.extractWeekdayIndex(s.getTimeSlot());
            } catch (InvalidTimeSlotException e) {
                continue;
            }
            result.add(new WeeklyEntry(s, weekdayIdx));
        }
        return result;
    }

    private static final class WeeklyEntry {
        final CourseSchedule schedule;
        final int weekdayIndex;

        WeeklyEntry(CourseSchedule schedule, int weekdayIndex) {
            this.schedule = schedule;
            this.weekdayIndex = weekdayIndex;
        }
    }
}
