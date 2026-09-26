package com.coursescheduler.service;

import com.coursescheduler.dto.CourseChangeAbnormalDaysDTO;
import com.coursescheduler.dto.CourseChangeSummaryDTO;
import com.coursescheduler.dto.CourseChangeTrendDTO;
import com.coursescheduler.dto.FailureReasonSummaryDTO;
import com.coursescheduler.dto.OperationFailureSummaryDTO;
import com.coursescheduler.dto.OperationSummaryDTO;
import com.coursescheduler.dto.RecentActiveCourseDTO;
import com.coursescheduler.model.AuditLog;
import com.coursescheduler.model.OperationType;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class AuditLogStatisticsSupport {

    private AuditLogStatisticsSupport() {
    }

    public static List<OperationSummaryDTO> getChangeSummaryByOperation(List<AuditLog> logs) {
        if (logs == null || logs.isEmpty()) {
            return new ArrayList<>();
        }

        Map<OperationType, OperationStats> statsMap = new HashMap<>();
        for (AuditLog log : logs) {
            if (log.getOperationType() == null) {
                continue;
            }
            OperationStats stats = statsMap.computeIfAbsent(log.getOperationType(), k -> new OperationStats());
            stats.increment(log.isSuccess());
        }

        return statsMap.entrySet()
                .stream()
                .map(entry -> entry.getValue().toResponse(entry.getKey()))
                .sorted(Comparator.comparingInt(OperationSummaryDTO::getTotalCount).reversed()
                        .thenComparing(dto -> dto.getOperationType().name()))
                .collect(Collectors.toList());
    }

    private static class OperationStats {
        private int successCount;
        private int failureCount;

        void increment(boolean success) {
            if (success) {
                successCount++;
            } else {
                failureCount++;
            }
        }

        OperationSummaryDTO toResponse(OperationType operationType) {
            int total = successCount + failureCount;
            return new OperationSummaryDTO(operationType, successCount, failureCount, total);
        }
    }

    public static List<CourseChangeSummaryDTO> getChangeSummaryByCourse(List<AuditLog> logs) {
        if (logs == null || logs.isEmpty()) {
            return new ArrayList<>();
        }

        return logs.stream()
                .filter(AuditLog::isSuccess)
                .map(log -> normalizeCourseName(log.getCourseName()))
                .filter(name -> name != null)
                .collect(Collectors.groupingBy(
                        name -> name,
                        Collectors.summingInt(e -> 1)
                ))
                .entrySet()
                .stream()
                .map(entry -> new CourseChangeSummaryDTO(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingInt(CourseChangeSummaryDTO::getChangeCount).reversed()
                        .thenComparing(CourseChangeSummaryDTO::getCourseName))
                .collect(Collectors.toList());
    }

    private static String normalizeCourseName(String courseName) {
        if (courseName == null) {
            return null;
        }
        String trimmed = courseName.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 按课程名统计最近变更日志。
     * <p>仅校验 limit &gt; 0；上限约束由调用方（Service 层）负责。
     */
    public static List<RecentActiveCourseDTO> getRecentActiveCourses(List<AuditLog> logs, int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit 必须为正整数，当前值: " + limit);
        }
        if (logs == null || logs.isEmpty()) {
            return new ArrayList<>();
        }

        Map<String, CourseStats> statsMap = new HashMap<>();
        for (AuditLog log : logs) {
            String normalizedName = normalizeCourseName(log.getCourseName());
            if (normalizedName == null) {
                continue;
            }
            if (log.getTimestamp() == null) {
                continue;
            }
            CourseStats stats = statsMap.computeIfAbsent(normalizedName, k -> new CourseStats());
            stats.accumulate(log);
        }

        return statsMap.entrySet()
                .stream()
                .map(entry -> entry.getValue().toResponse(entry.getKey()))
                .sorted(Comparator.comparing(RecentActiveCourseDTO::getLastChangeTime, Comparator.reverseOrder())
                        .thenComparing(RecentActiveCourseDTO::getTotalCount, Comparator.reverseOrder())
                        .thenComparing(RecentActiveCourseDTO::getCourseName))
                .limit(limit)
                .collect(Collectors.toList());
    }

    private static class CourseStats {
        private LocalDateTime lastChangeTime;
        private int successCount;
        private int failureCount;

        void accumulate(AuditLog log) {
            if (lastChangeTime == null || log.getTimestamp().isAfter(lastChangeTime)) {
                lastChangeTime = log.getTimestamp();
            }
            if (log.isSuccess()) {
                successCount++;
            } else {
                failureCount++;
            }
        }

        RecentActiveCourseDTO toResponse(String courseName) {
            int total = successCount + failureCount;
            return new RecentActiveCourseDTO(courseName, lastChangeTime, successCount, failureCount, total);
        }
    }

    /**
     * 按日期汇总课程变更次数（成功/失败/总），按日期升序返回。
     *
     * <p>统计区间为 [clock 当日 - days + 1, clock 当日]；
     * clock 不可为 null，days 必须为正整数；
     * courseName 为 null/空白或 timestamp 为 null 的日志跳过不计。
     */
    public static List<CourseChangeTrendDTO> getCourseChangeTrend(List<AuditLog> logs, int days, Clock clock) {
        Map<LocalDate, DateStats> statsMap = buildDateStatsMap(logs, days, clock);

        return statsMap.entrySet()
                .stream()
                .map(entry -> entry.getValue().toTrendResponse(entry.getKey()))
                .sorted(Comparator.comparing(CourseChangeTrendDTO::getDate))
                .collect(Collectors.toList());
    }

    public static List<CourseChangeAbnormalDaysDTO> getCourseChangeAbnormalDays(List<AuditLog> logs, int days, Clock clock) {
        Map<LocalDate, DateStats> statsMap = buildDateStatsMap(logs, days, clock);

        return statsMap.entrySet()
                .stream()
                .map(entry -> entry.getValue().toAbnormalDaysResponse(entry.getKey()))
                .filter(dto -> dto.getFailureCount() > 0)
                .sorted(Comparator.comparingDouble(CourseChangeAbnormalDaysDTO::getFailureRate).reversed()
                        .thenComparing(CourseChangeAbnormalDaysDTO::getFailureCount, Comparator.reverseOrder())
                        .thenComparing(CourseChangeAbnormalDaysDTO::getDate))
                .collect(Collectors.toList());
    }

    private static Map<LocalDate, DateStats> buildDateStatsMap(List<AuditLog> logs, int days, Clock clock) {
        if (clock == null) {
            throw new IllegalArgumentException("clock 不能为 null");
        }
        if (days <= 0) {
            throw new IllegalArgumentException("days 必须为正整数，当前值: " + days);
        }
        if (logs == null || logs.isEmpty()) {
            return new HashMap<>();
        }

        LocalDate today = LocalDate.now(clock);
        LocalDate startDate = today.minusDays(days - 1);

        Map<LocalDate, DateStats> statsMap = new HashMap<>();
        for (AuditLog log : logs) {
            String normalizedName = normalizeCourseName(log.getCourseName());
            if (normalizedName == null) {
                continue;
            }
            if (log.getTimestamp() == null) {
                continue;
            }
            LocalDate logDate = log.getTimestamp().toLocalDate();
            if (logDate.isBefore(startDate) || logDate.isAfter(today)) {
                continue;
            }
            DateStats stats = statsMap.computeIfAbsent(logDate, k -> new DateStats());
            stats.increment(log.isSuccess());
        }
        return statsMap;
    }

    private static class DateStats {
        private int successCount;
        private int failureCount;

        void increment(boolean success) {
            if (success) {
                successCount++;
            } else {
                failureCount++;
            }
        }

        CourseChangeTrendDTO toTrendResponse(LocalDate date) {
            int total = successCount + failureCount;
            return new CourseChangeTrendDTO(date, successCount, failureCount, total);
        }

        CourseChangeAbnormalDaysDTO toAbnormalDaysResponse(LocalDate date) {
            int total = successCount + failureCount;
            double failureRate = total > 0 ? (double) failureCount / total : 0.0;
            return new CourseChangeAbnormalDaysDTO(date, successCount, failureCount, total, failureRate);
        }
    }

    private static class CumulativeStats {
        private int count;
        private LocalDateTime lastTime;

        void accumulate(LocalDateTime timestamp) {
            count++;
            if (lastTime == null || timestamp.isAfter(lastTime)) {
                lastTime = timestamp;
            }
        }

        int getCount() {
            return count;
        }

        LocalDateTime getLastTime() {
            return lastTime;
        }
    }

    private static <K> Map<K, CumulativeStats> buildFailureStatsMap(
            List<AuditLog> logs, Function<AuditLog, K> keyExtractor) {
        Map<K, CumulativeStats> statsMap = new HashMap<>();
        for (AuditLog log : logs) {
            if (log.isSuccess()) {
                continue;
            }
            if (log.getTimestamp() == null) {
                continue;
            }
            K key = keyExtractor.apply(log);
            if (key == null) {
                continue;
            }
            CumulativeStats stats = statsMap.computeIfAbsent(key, k -> new CumulativeStats());
            stats.accumulate(log.getTimestamp());
        }
        return statsMap;
    }

    /**
     * 按失败日志的 errorMessage 统计次数，返回失败原因、次数、最近一次出现时间。
     * <p>仅校验 limit &gt; 0；上限约束由调用方（Service 层）负责。
     * <p>只统计失败日志（success=false），errorMessage 为空或纯空白的跳过；
     * 排序：次数倒序、最近出现时间倒序、失败原因升序。
     */
    public static List<FailureReasonSummaryDTO> getFailureReasonSummary(List<AuditLog> logs, int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit 必须为正整数，当前值: " + limit);
        }
        if (logs == null || logs.isEmpty()) {
            return new ArrayList<>();
        }

        Map<String, CumulativeStats> statsMap = buildFailureStatsMap(
                logs, log -> normalizeErrorMessage(log.getErrorMessage()));

        return statsMap.entrySet()
                .stream()
                .map(entry -> new FailureReasonSummaryDTO(entry.getKey(), entry.getValue().getCount(), entry.getValue().getLastTime()))
                .sorted(Comparator.comparingInt(FailureReasonSummaryDTO::getCount).reversed()
                        .thenComparing(FailureReasonSummaryDTO::getLastOccurrenceTime, Comparator.reverseOrder())
                        .thenComparing(FailureReasonSummaryDTO::getFailureReason))
                .limit(limit)
                .collect(Collectors.toList());
    }

    private static String normalizeErrorMessage(String errorMessage) {
        if (errorMessage == null) {
            return null;
        }
        String trimmed = errorMessage.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 按失败日志的 operationType 统计次数，返回操作类型、失败次数、最近一次失败时间。
     * <p>仅校验 limit &gt; 0；上限约束由调用方（Service 层）负责。
     * <p>只统计失败日志（success=false），operationType 为 null 或 timestamp 为 null 的跳过；
     * 排序：失败次数倒序、最近失败时间倒序、操作类型名升序。
     */
    public static List<OperationFailureSummaryDTO> getOperationFailureSummary(List<AuditLog> logs, int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit 必须为正整数，当前值: " + limit);
        }
        if (logs == null || logs.isEmpty()) {
            return new ArrayList<>();
        }

        Map<OperationType, CumulativeStats> statsMap = buildFailureStatsMap(
                logs, AuditLog::getOperationType);

        return statsMap.entrySet()
                .stream()
                .map(entry -> new OperationFailureSummaryDTO(entry.getKey(), entry.getValue().getCount(), entry.getValue().getLastTime()))
                .sorted(Comparator.comparingInt(OperationFailureSummaryDTO::getFailureCount).reversed()
                        .thenComparing(OperationFailureSummaryDTO::getLastFailureTime, Comparator.reverseOrder())
                        .thenComparing(dto -> dto.getOperationType().name()))
                .limit(limit)
                .collect(Collectors.toList());
    }
}
