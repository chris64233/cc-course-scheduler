package com.coursescheduler.service;

import com.coursescheduler.model.CourseSchedule;
import com.coursescheduler.util.TimeSlotUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Predicate;

public final class FreeDaySummarySupport {

    private FreeDaySummarySupport() {
    }

    public static <R> R calculateFreeDaySummary(
            List<CourseSchedule> allSchedules,
            Predicate<CourseSchedule> matcher,
            BiFunction<List<String>, List<String>, R> responseFactory) {
        if (matcher == null) {
            throw new IllegalArgumentException("matcher 不能为空");
        }
        if (responseFactory == null) {
            throw new IllegalArgumentException("responseFactory 不能为空");
        }

        Set<Integer> busyIndices = WeeklySummarySupport.resolveBusyWeekdayIndices(allSchedules, matcher);
        WeekdayPartition partition = partitionWeekdays(busyIndices);

        return responseFactory.apply(partition.getBusyDays(), partition.getFreeDays());
    }

    public static WeekdayPartition partitionWeekdays(Set<Integer> busyIndices) {
        if (busyIndices == null) {
            throw new IllegalArgumentException("busyIndices 不能为 null");
        }
        validateWeekdayIndices(busyIndices);

        List<String> busyDays = new ArrayList<>();
        List<String> freeDays = new ArrayList<>();

        for (int i = 0; i < TimeSlotUtils.WEEKDAY_COUNT; i++) {
            String weekdayName = TimeSlotUtils.getWeekdayName(i);
            if (busyIndices.contains(i)) {
                busyDays.add(weekdayName);
            } else {
                freeDays.add(weekdayName);
            }
        }

        assertMutuallyExclusive(busyDays, freeDays);

        return new WeekdayPartition(busyDays, freeDays);
    }

    static void validateWeekdayIndices(Set<Integer> indices) {
        for (Integer idx : indices) {
            if (idx == null) {
                throw new IllegalArgumentException("weekday index 不能为 null");
            }
            if (idx < 0 || idx >= TimeSlotUtils.WEEKDAY_COUNT) {
                throw new IllegalArgumentException(
                        "非法的 weekday index：" + idx + "，应在 0~" + (TimeSlotUtils.WEEKDAY_COUNT - 1) + " 之间");
            }
        }
    }

    private static void assertMutuallyExclusive(List<String> busyDays, List<String> freeDays) {
        Set<String> busySet = new LinkedHashSet<>(busyDays);
        for (String freeDay : freeDays) {
            if (busySet.contains(freeDay)) {
                throw new IllegalStateException(
                        "同一天不能同时出现在 busyDays 和 freeDays 中：" + freeDay);
            }
        }
        if (busyDays.size() + freeDays.size() != TimeSlotUtils.WEEKDAY_COUNT) {
            throw new IllegalStateException(
                    "busyDays + freeDays 必须等于 7 天，实际：busy=" + busyDays.size()
                            + ", free=" + freeDays.size());
        }
    }

    private static Set<String> allValidWeekdayNames() {
        Set<String> names = new HashSet<>();
        for (int i = 0; i < TimeSlotUtils.WEEKDAY_COUNT; i++) {
            names.add(TimeSlotUtils.getWeekdayName(i));
        }
        return Collections.unmodifiableSet(names);
    }

    public static final class WeekdayPartition {
        private static final Set<String> VALID_WEEKDAY_NAMES = allValidWeekdayNames();

        private final List<String> busyDays;
        private final List<String> freeDays;

        WeekdayPartition(List<String> busyDays, List<String> freeDays) {
            if (busyDays == null) {
                throw new IllegalArgumentException("busyDays 不能为 null");
            }
            if (freeDays == null) {
                throw new IllegalArgumentException("freeDays 不能为 null");
            }
            this.busyDays = immutableCopy(busyDays);
            this.freeDays = immutableCopy(freeDays);
            validatePartition();
        }

        public List<String> getBusyDays() {
            return busyDays;
        }

        public List<String> getFreeDays() {
            return freeDays;
        }

        private static List<String> immutableCopy(List<String> source) {
            if (source == null || source.isEmpty()) {
                return Collections.emptyList();
            }
            return Collections.unmodifiableList(new ArrayList<>(source));
        }

        private void validatePartition() {
            for (String day : busyDays) {
                if (day == null) {
                    throw new IllegalArgumentException("busyDays 不能包含 null 元素");
                }
                if (!VALID_WEEKDAY_NAMES.contains(day)) {
                    throw new IllegalArgumentException(
                            "busyDays 包含非法的星期名：\"" + day + "\"，合法值为：" + VALID_WEEKDAY_NAMES);
                }
            }
            for (String day : freeDays) {
                if (day == null) {
                    throw new IllegalArgumentException("freeDays 不能包含 null 元素");
                }
                if (!VALID_WEEKDAY_NAMES.contains(day)) {
                    throw new IllegalArgumentException(
                            "freeDays 包含非法的星期名：\"" + day + "\"，合法值为：" + VALID_WEEKDAY_NAMES);
                }
            }
            Set<String> busySet = new HashSet<>(busyDays);
            for (String freeDay : freeDays) {
                if (busySet.contains(freeDay)) {
                    throw new IllegalArgumentException(
                            "同一天不能同时出现在 busyDays 和 freeDays 中：" + freeDay);
                }
            }
            if (busyDays.size() + freeDays.size() != TimeSlotUtils.WEEKDAY_COUNT) {
                throw new IllegalArgumentException(
                        "busyDays + freeDays 必须等于 " + TimeSlotUtils.WEEKDAY_COUNT
                                + " 天，实际：busy=" + busyDays.size() + ", free=" + freeDays.size());
            }
        }
    }
}
