package com.coursescheduler.service;

import com.coursescheduler.dto.AuditLogFilterRequest;
import com.coursescheduler.dto.AuditLogResponse;
import com.coursescheduler.dto.ChangeSummaryResponse;
import com.coursescheduler.dto.CourseChangeAbnormalDaysDTO;
import com.coursescheduler.dto.CourseChangeSummaryDTO;
import com.coursescheduler.dto.CourseChangeTrendDTO;
import com.coursescheduler.dto.FailureReasonSummaryDTO;
import com.coursescheduler.dto.OperationFailureSummaryDTO;
import com.coursescheduler.dto.OperationSummaryDTO;
import com.coursescheduler.dto.RecentActiveCourseDTO;
import com.coursescheduler.exception.InvalidRequestParameterException;
import com.coursescheduler.model.AuditLog;
import com.coursescheduler.model.CourseSchedule;
import com.coursescheduler.model.OperationType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class AuditLogService {

    private static final int MAX_LIMIT = 50;
    private static final int MAX_DAYS = 365;

    private final List<AuditLog> logs = new ArrayList<>();
    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();
    private final Clock clock;
    private long idGenerator = 1;

    @Autowired
    public AuditLogService(Clock clock) {
        this.clock = clock;
    }

    public void recordLog(OperationType operationType, Long courseId, String courseName,
                          String teacherName, String classroom, String timeSlot,
                          boolean success, String errorMessage) {
        rwLock.writeLock().lock();
        try {
            AuditLog log = new AuditLog(
                    idGenerator++,
                    operationType,
                    LocalDateTime.now(clock),
                    courseId,
                    courseName,
                    teacherName,
                    classroom,
                    timeSlot,
                    success,
                    errorMessage
            );
            logs.add(log);
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    public void recordLog(OperationType operationType, CourseSchedule schedule,
                          boolean success, String errorMessage) {
        if (schedule != null) {
            recordLog(operationType, schedule.getId(), schedule.getCourseName(),
                    schedule.getTeacherName(), schedule.getClassroom(), schedule.getTimeSlot(),
                    success, errorMessage);
        } else {
            recordLog(operationType, null, null, null, null, null, success, errorMessage);
        }
    }

    public List<AuditLogResponse> queryLogs(AuditLogFilterRequest filter) {
        rwLock.readLock().lock();
        try {
            Stream<AuditLog> stream = logs.stream();

            if (filter != null) {
                if (filter.getOperationType() != null) {
                    stream = stream.filter(log -> log.getOperationType() == filter.getOperationType());
                }
                if (filter.getSuccess() != null) {
                    stream = stream.filter(log -> log.isSuccess() == filter.getSuccess());
                }
            }

            return stream
                    .sorted(Comparator.comparing(AuditLog::getId).reversed())
                    .map(this::toResponse)
                    .collect(Collectors.toList());
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public List<AuditLogResponse> getAllLogs() {
        return queryLogs(null);
    }

    public List<ChangeSummaryResponse> getChangeSummary(int limit) {
        validateLimit(limit);
        rwLock.readLock().lock();
        try {
            return logs.stream()
                    .filter(log -> log.isSuccess())
                    .sorted(Comparator.comparing(AuditLog::getTimestamp, Comparator.reverseOrder())
                            .thenComparing(AuditLog::getId, Comparator.reverseOrder()))
                    .limit(limit)
                    .map(this::toSummaryResponse)
                    .collect(Collectors.toList());
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public List<CourseChangeSummaryDTO> getChangeSummaryByCourse() {
        rwLock.readLock().lock();
        try {
            return AuditLogStatisticsSupport.getChangeSummaryByCourse(logs);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public List<OperationSummaryDTO> getChangeSummaryByOperation() {
        rwLock.readLock().lock();
        try {
            return AuditLogStatisticsSupport.getChangeSummaryByOperation(logs);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public List<RecentActiveCourseDTO> getRecentActiveCourses(int limit) {
        validateLimit(limit);
        rwLock.readLock().lock();
        try {
            return AuditLogStatisticsSupport.getRecentActiveCourses(logs, limit);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public List<CourseChangeTrendDTO> getCourseChangeTrend(int days) {
        validateDays(days);
        rwLock.readLock().lock();
        try {
            return AuditLogStatisticsSupport.getCourseChangeTrend(logs, days, clock);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public List<CourseChangeAbnormalDaysDTO> getCourseChangeAbnormalDays(int days) {
        validateDays(days);
        rwLock.readLock().lock();
        try {
            return AuditLogStatisticsSupport.getCourseChangeAbnormalDays(logs, days, clock);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public List<FailureReasonSummaryDTO> getFailureReasonSummary(int limit) {
        validateLimit(limit);
        rwLock.readLock().lock();
        try {
            return AuditLogStatisticsSupport.getFailureReasonSummary(logs, limit);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public List<OperationFailureSummaryDTO> getOperationFailureSummary(int limit) {
        validateLimit(limit);
        rwLock.readLock().lock();
        try {
            return AuditLogStatisticsSupport.getOperationFailureSummary(logs, limit);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public void clearAllLogs() {
        rwLock.writeLock().lock();
        try {
            logs.clear();
            idGenerator = 1;
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    private void validateLimit(int limit) {
        if (limit <= 0) {
            throw new InvalidRequestParameterException("limit 必须为正整数，当前值: " + limit);
        }
        if (limit > MAX_LIMIT) {
            throw new InvalidRequestParameterException("limit 最大值为 " + MAX_LIMIT + "，当前值: " + limit);
        }
    }

    private void validateDays(int days) {
        if (days <= 0) {
            throw new InvalidRequestParameterException("days 必须为正整数，当前值: " + days);
        }
        if (days > MAX_DAYS) {
            throw new InvalidRequestParameterException("days 最大值为 " + MAX_DAYS + "，当前值: " + days);
        }
    }

    private AuditLogResponse toResponse(AuditLog log) {
        return new AuditLogResponse(
                log.getId(),
                log.getOperationType(),
                log.getTimestamp(),
                log.getCourseId(),
                log.getCourseName(),
                log.getTeacherName(),
                log.getClassroom(),
                log.getTimeSlot(),
                log.isSuccess(),
                log.getErrorMessage()
        );
    }

    private ChangeSummaryResponse toSummaryResponse(AuditLog log) {
        return new ChangeSummaryResponse(
                log.getOperationType(),
                log.getTimestamp(),
                log.getCourseName(),
                log.getTeacherName(),
                log.getClassroom(),
                log.getTimeSlot()
        );
    }
}
