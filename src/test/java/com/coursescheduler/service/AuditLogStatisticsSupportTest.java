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
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AuditLogStatisticsSupportTest {

    private static AuditLog createLog(boolean success, String courseName) {
        return new AuditLog(
                1L, OperationType.CREATE, LocalDateTime.now(),
                1L, courseName, "老师", "教室", "时段",
                success, null
        );
    }

    @Test
    void getChangeSummaryByCourse_NullList_ReturnsEmptyList() {
        List<CourseChangeSummaryDTO> result = AuditLogStatisticsSupport.getChangeSummaryByCourse(null);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void getChangeSummaryByCourse_EmptyList_ReturnsEmptyList() {
        List<AuditLog> logs = Collections.emptyList();

        List<CourseChangeSummaryDTO> result = AuditLogStatisticsSupport.getChangeSummaryByCourse(logs);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void getChangeSummaryByCourse_FailureLogs_Excluded() {
        List<AuditLog> logs = Arrays.asList(
                createLog(true, "数学"),
                createLog(false, "数学"),
                createLog(false, "物理"),
                createLog(true, "化学")
        );

        List<CourseChangeSummaryDTO> result = AuditLogStatisticsSupport.getChangeSummaryByCourse(logs);

        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(dto -> "数学".equals(dto.getCourseName()) && dto.getChangeCount() == 1));
        assertTrue(result.stream().anyMatch(dto -> "化学".equals(dto.getCourseName()) && dto.getChangeCount() == 1));
    }

    @Test
    void getChangeSummaryByCourse_OnlyFailureLogs_ReturnsEmptyList() {
        List<AuditLog> logs = Arrays.asList(
                createLog(false, "数学"),
                createLog(false, "物理")
        );

        List<CourseChangeSummaryDTO> result = AuditLogStatisticsSupport.getChangeSummaryByCourse(logs);

        assertTrue(result.isEmpty());
    }

    @Test
    void getChangeSummaryByCourse_NullCourseName_Skipped() {
        List<AuditLog> logs = Arrays.asList(
                createLog(true, "数学"),
                createLog(true, null),
                createLog(true, "物理")
        );

        List<CourseChangeSummaryDTO> result = AuditLogStatisticsSupport.getChangeSummaryByCourse(logs);

        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(dto -> "数学".equals(dto.getCourseName())));
        assertTrue(result.stream().anyMatch(dto -> "物理".equals(dto.getCourseName())));
    }

    @Test
    void getChangeSummaryByCourse_EmptyCourseName_Skipped() {
        List<AuditLog> logs = Arrays.asList(
                createLog(true, "数学"),
                createLog(true, ""),
                createLog(true, "物理")
        );

        List<CourseChangeSummaryDTO> result = AuditLogStatisticsSupport.getChangeSummaryByCourse(logs);

        assertEquals(2, result.size());
    }

    @Test
    void getChangeSummaryByCourse_WhitespaceOnlyCourseName_Skipped() {
        List<AuditLog> logs = Arrays.asList(
                createLog(true, "数学"),
                createLog(true, "   "),
                createLog(true, "  \t  "),
                createLog(true, "物理")
        );

        List<CourseChangeSummaryDTO> result = AuditLogStatisticsSupport.getChangeSummaryByCourse(logs);

        assertEquals(2, result.size());
    }

    @Test
    void getChangeSummaryByCourse_WhitespaceCourseName_MergedAfterTrim() {
        List<AuditLog> logs = Arrays.asList(
                createLog(true, "数学"),
                createLog(true, " 数学 "),
                createLog(true, "  数学  "),
                createLog(true, "\t数学\t"),
                createLog(true, "物理")
        );

        List<CourseChangeSummaryDTO> result = AuditLogStatisticsSupport.getChangeSummaryByCourse(logs);

        assertEquals(2, result.size());
        CourseChangeSummaryDTO mathDTO = result.stream()
                .filter(dto -> "数学".equals(dto.getCourseName()))
                .findFirst()
                .orElse(null);
        assertNotNull(mathDTO);
        assertEquals(4, mathDTO.getChangeCount());

        CourseChangeSummaryDTO physicsDTO = result.stream()
                .filter(dto -> "物理".equals(dto.getCourseName()))
                .findFirst()
                .orElse(null);
        assertNotNull(physicsDTO);
        assertEquals(1, physicsDTO.getChangeCount());
    }

    @Test
    void getChangeSummaryByCourse_SortedByCountDescending() {
        List<AuditLog> logs = Arrays.asList(
                createLog(true, "数学"),
                createLog(true, "数学"),
                createLog(true, "物理"),
                createLog(true, "物理"),
                createLog(true, "物理"),
                createLog(true, "化学"),
                createLog(true, "化学"),
                createLog(true, "化学"),
                createLog(true, "化学")
        );

        List<CourseChangeSummaryDTO> result = AuditLogStatisticsSupport.getChangeSummaryByCourse(logs);

        assertEquals(3, result.size());
        assertEquals("化学", result.get(0).getCourseName());
        assertEquals(4, result.get(0).getChangeCount());
        assertEquals("物理", result.get(1).getCourseName());
        assertEquals(3, result.get(1).getChangeCount());
        assertEquals("数学", result.get(2).getCourseName());
        assertEquals(2, result.get(2).getChangeCount());
    }

    @Test
    void getChangeSummaryByCourse_SameCount_SortedByCourseNameAscending() {
        List<AuditLog> logs = Arrays.asList(
                createLog(true, "物理"),
                createLog(true, "物理"),
                createLog(true, "数学"),
                createLog(true, "数学"),
                createLog(true, "化学"),
                createLog(true, "化学")
        );

        List<CourseChangeSummaryDTO> result = AuditLogStatisticsSupport.getChangeSummaryByCourse(logs);

        assertEquals(3, result.size());
        assertEquals("化学", result.get(0).getCourseName());
        assertEquals(2, result.get(0).getChangeCount());
        assertEquals("数学", result.get(1).getCourseName());
        assertEquals(2, result.get(1).getChangeCount());
        assertEquals("物理", result.get(2).getCourseName());
        assertEquals(2, result.get(2).getChangeCount());
    }

    @Test
    void getChangeSummaryByCourse_MixedScenario_CorrectResult() {
        List<AuditLog> logs = Arrays.asList(
                createLog(true, "数学"),
                createLog(true, " 数学 "),
                createLog(false, "数学"),
                createLog(true, null),
                createLog(true, "   "),
                createLog(true, "物理"),
                createLog(true, "化学"),
                createLog(true, "化学"),
                createLog(false, "化学"),
                createLog(true, "生物")
        );

        List<CourseChangeSummaryDTO> result = AuditLogStatisticsSupport.getChangeSummaryByCourse(logs);

        assertEquals(4, result.size());
        assertEquals("化学", result.get(0).getCourseName());
        assertEquals(2, result.get(0).getChangeCount());
        assertEquals("数学", result.get(1).getCourseName());
        assertEquals(2, result.get(1).getChangeCount());
        assertEquals("物理", result.get(2).getCourseName());
        assertEquals(1, result.get(2).getChangeCount());
        assertEquals("生物", result.get(3).getCourseName());
        assertEquals(1, result.get(3).getChangeCount());
    }

    private static AuditLog createLogWithOperation(OperationType operationType, boolean success) {
        return new AuditLog(
                1L, operationType, LocalDateTime.now(),
                1L, "课程", "老师", "教室", "时段",
                success, success ? null : "错误信息"
        );
    }

    @Test
    void getChangeSummaryByOperation_NullList_ReturnsEmptyList() {
        List<OperationSummaryDTO> result = AuditLogStatisticsSupport.getChangeSummaryByOperation(null);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void getChangeSummaryByOperation_EmptyList_ReturnsEmptyList() {
        List<AuditLog> logs = Collections.emptyList();

        List<OperationSummaryDTO> result = AuditLogStatisticsSupport.getChangeSummaryByOperation(logs);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void getChangeSummaryByOperation_MixedSuccessFailure_CorrectCounts() {
        List<AuditLog> logs = Arrays.asList(
                createLogWithOperation(OperationType.CREATE, true),
                createLogWithOperation(OperationType.CREATE, true),
                createLogWithOperation(OperationType.CREATE, false),
                createLogWithOperation(OperationType.UPDATE, true),
                createLogWithOperation(OperationType.UPDATE, false),
                createLogWithOperation(OperationType.UPDATE, false),
                createLogWithOperation(OperationType.DELETE, true)
        );

        List<OperationSummaryDTO> result = AuditLogStatisticsSupport.getChangeSummaryByOperation(logs);

        assertEquals(3, result.size());

        OperationSummaryDTO createDto = result.stream()
                .filter(dto -> dto.getOperationType() == OperationType.CREATE)
                .findFirst().orElse(null);
        assertNotNull(createDto);
        assertEquals(2, createDto.getSuccessCount());
        assertEquals(1, createDto.getFailureCount());
        assertEquals(3, createDto.getTotalCount());

        OperationSummaryDTO updateDto = result.stream()
                .filter(dto -> dto.getOperationType() == OperationType.UPDATE)
                .findFirst().orElse(null);
        assertNotNull(updateDto);
        assertEquals(1, updateDto.getSuccessCount());
        assertEquals(2, updateDto.getFailureCount());
        assertEquals(3, updateDto.getTotalCount());

        OperationSummaryDTO deleteDto = result.stream()
                .filter(dto -> dto.getOperationType() == OperationType.DELETE)
                .findFirst().orElse(null);
        assertNotNull(deleteDto);
        assertEquals(1, deleteDto.getSuccessCount());
        assertEquals(0, deleteDto.getFailureCount());
        assertEquals(1, deleteDto.getTotalCount());
    }

    @Test
    void getChangeSummaryByOperation_SortedByTotalCountDescending() {
        List<AuditLog> logs = Arrays.asList(
                createLogWithOperation(OperationType.CREATE, true),
                createLogWithOperation(OperationType.CREATE, true),
                createLogWithOperation(OperationType.CREATE, true),
                createLogWithOperation(OperationType.UPDATE, true),
                createLogWithOperation(OperationType.UPDATE, true),
                createLogWithOperation(OperationType.DELETE, true)
        );

        List<OperationSummaryDTO> result = AuditLogStatisticsSupport.getChangeSummaryByOperation(logs);

        assertEquals(3, result.size());
        assertEquals(OperationType.CREATE, result.get(0).getOperationType());
        assertEquals(3, result.get(0).getTotalCount());
        assertEquals(OperationType.UPDATE, result.get(1).getOperationType());
        assertEquals(2, result.get(1).getTotalCount());
        assertEquals(OperationType.DELETE, result.get(2).getOperationType());
        assertEquals(1, result.get(2).getTotalCount());
    }

    @Test
    void getChangeSummaryByOperation_SameTotalCount_SortedByOperationTypeNameAscending() {
        List<AuditLog> logs = Arrays.asList(
                createLogWithOperation(OperationType.UPDATE, true),
                createLogWithOperation(OperationType.DELETE, true),
                createLogWithOperation(OperationType.CREATE, true)
        );

        List<OperationSummaryDTO> result = AuditLogStatisticsSupport.getChangeSummaryByOperation(logs);

        assertEquals(3, result.size());
        assertEquals(OperationType.CREATE, result.get(0).getOperationType());
        assertEquals(OperationType.DELETE, result.get(1).getOperationType());
        assertEquals(OperationType.UPDATE, result.get(2).getOperationType());
    }

    @Test
    void getChangeSummaryByOperation_AllFailures_IncludedInCounts() {
        List<AuditLog> logs = Arrays.asList(
                createLogWithOperation(OperationType.CREATE, false),
                createLogWithOperation(OperationType.CREATE, false),
                createLogWithOperation(OperationType.UPDATE, false)
        );

        List<OperationSummaryDTO> result = AuditLogStatisticsSupport.getChangeSummaryByOperation(logs);

        assertEquals(2, result.size());
        OperationSummaryDTO createDto = result.stream()
                .filter(dto -> dto.getOperationType() == OperationType.CREATE)
                .findFirst().orElse(null);
        assertNotNull(createDto);
        assertEquals(0, createDto.getSuccessCount());
        assertEquals(2, createDto.getFailureCount());
        assertEquals(2, createDto.getTotalCount());
    }

    private static AuditLog createLogWithNullOperationType(boolean success) {
        return new AuditLog(
                1L, null, LocalDateTime.now(),
                1L, "课程", "老师", "教室", "时段",
                success, success ? null : "错误信息"
        );
    }

    @Test
    void getChangeSummaryByOperation_NullOperationType_Skipped() {
        List<AuditLog> logs = Arrays.asList(
                createLogWithOperation(OperationType.CREATE, true),
                createLogWithOperation(OperationType.CREATE, false),
                createLogWithNullOperationType(true),
                createLogWithNullOperationType(false),
                createLogWithOperation(OperationType.UPDATE, true),
                createLogWithOperation(OperationType.UPDATE, true),
                createLogWithOperation(OperationType.UPDATE, false)
        );

        List<OperationSummaryDTO> result = AuditLogStatisticsSupport.getChangeSummaryByOperation(logs);

        assertEquals(2, result.size());

        OperationSummaryDTO createDto = result.stream()
                .filter(dto -> dto.getOperationType() == OperationType.CREATE)
                .findFirst().orElse(null);
        assertNotNull(createDto);
        assertEquals(1, createDto.getSuccessCount());
        assertEquals(1, createDto.getFailureCount());
        assertEquals(2, createDto.getTotalCount());

        OperationSummaryDTO updateDto = result.stream()
                .filter(dto -> dto.getOperationType() == OperationType.UPDATE)
                .findFirst().orElse(null);
        assertNotNull(updateDto);
        assertEquals(2, updateDto.getSuccessCount());
        assertEquals(1, updateDto.getFailureCount());
        assertEquals(3, updateDto.getTotalCount());
    }

    @Test
    void getChangeSummaryByOperation_OnlyNullOperationType_ReturnsEmptyList() {
        List<AuditLog> logs = Arrays.asList(
                createLogWithNullOperationType(true),
                createLogWithNullOperationType(false)
        );

        List<OperationSummaryDTO> result = AuditLogStatisticsSupport.getChangeSummaryByOperation(logs);

        assertTrue(result.isEmpty());
    }

    @Test
    void getChangeSummaryByOperation_MixedScenario_CorrectSortAndCounts() {
        List<AuditLog> logs = Arrays.asList(
                createLogWithOperation(OperationType.BATCH_CREATE, true),
                createLogWithOperation(OperationType.BATCH_CREATE, true),
                createLogWithOperation(OperationType.BATCH_CREATE, false),
                createLogWithOperation(OperationType.CREATE, true),
                createLogWithOperation(OperationType.CREATE, true),
                createLogWithOperation(OperationType.CREATE, true),
                createLogWithOperation(OperationType.UPDATE, true),
                createLogWithOperation(OperationType.UPDATE, false),
                createLogWithOperation(OperationType.DELETE, false),
                createLogWithOperation(OperationType.UNDO, true)
        );

        List<OperationSummaryDTO> result = AuditLogStatisticsSupport.getChangeSummaryByOperation(logs);

        assertEquals(5, result.size());
        assertEquals(OperationType.BATCH_CREATE, result.get(0).getOperationType());
        assertEquals(3, result.get(0).getTotalCount());
        assertEquals(OperationType.CREATE, result.get(1).getOperationType());
        assertEquals(3, result.get(1).getTotalCount());
        assertEquals(OperationType.UPDATE, result.get(2).getOperationType());
        assertEquals(2, result.get(2).getTotalCount());
        assertEquals(OperationType.DELETE, result.get(3).getOperationType());
        assertEquals(1, result.get(3).getTotalCount());
        assertEquals(OperationType.UNDO, result.get(4).getOperationType());
        assertEquals(1, result.get(4).getTotalCount());
    }

    private static AuditLog createLogWithTimestampAndCourse(LocalDateTime timestamp, boolean success, String courseName) {
        return new AuditLog(
                1L, OperationType.CREATE, timestamp,
                1L, courseName, "老师", "教室", "时段",
                success, success ? null : "错误信息"
        );
    }

    @Test
    void getRecentActiveCourses_NullTimestamp_Skipped() {
        LocalDateTime t1 = LocalDateTime.of(2026, 6, 13, 10, 0);
        List<AuditLog> logs = Arrays.asList(
                createLogWithTimestampAndCourse(t1, true, "数学"),
                createLogWithTimestampAndCourse(null, true, "物理"),
                createLogWithTimestampAndCourse(t1.plusMinutes(1), true, "化学")
        );

        List<RecentActiveCourseDTO> result = AuditLogStatisticsSupport.getRecentActiveCourses(logs, 10);

        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(dto -> "数学".equals(dto.getCourseName())));
        assertTrue(result.stream().anyMatch(dto -> "化学".equals(dto.getCourseName())));
        assertFalse(result.stream().anyMatch(dto -> "物理".equals(dto.getCourseName())));
    }

    @Test
    void getRecentActiveCourses_AllNullTimestamp_ReturnsEmpty() {
        List<AuditLog> logs = Arrays.asList(
                createLogWithTimestampAndCourse(null, true, "数学"),
                createLogWithTimestampAndCourse(null, false, "物理")
        );

        List<RecentActiveCourseDTO> result = AuditLogStatisticsSupport.getRecentActiveCourses(logs, 10);

        assertTrue(result.isEmpty());
    }

    @Test
    void getRecentActiveCourses_NullTimestampDoesNotAffectOtherLogs() {
        LocalDateTime t1 = LocalDateTime.of(2026, 6, 13, 10, 0);
        List<AuditLog> logs = Arrays.asList(
                createLogWithTimestampAndCourse(t1, true, "数学"),
                createLogWithTimestampAndCourse(null, true, "数学"),
                createLogWithTimestampAndCourse(t1.plusMinutes(5), false, "数学"),
                createLogWithTimestampAndCourse(null, false, "物理"),
                createLogWithTimestampAndCourse(t1.plusMinutes(10), true, "物理")
        );

        List<RecentActiveCourseDTO> result = AuditLogStatisticsSupport.getRecentActiveCourses(logs, 10);

        assertEquals(2, result.size());

        RecentActiveCourseDTO math = result.stream()
                .filter(dto -> "数学".equals(dto.getCourseName()))
                .findFirst().orElse(null);
        assertNotNull(math);
        assertEquals(1, math.getSuccessCount());
        assertEquals(1, math.getFailureCount());
        assertEquals(2, math.getTotalCount());
        assertEquals(t1.plusMinutes(5), math.getLastChangeTime());

        RecentActiveCourseDTO physics = result.stream()
                .filter(dto -> "物理".equals(dto.getCourseName()))
                .findFirst().orElse(null);
        assertNotNull(physics);
        assertEquals(1, physics.getSuccessCount());
        assertEquals(0, physics.getFailureCount());
        assertEquals(1, physics.getTotalCount());
        assertEquals(t1.plusMinutes(10), physics.getLastChangeTime());
    }

    @Test
    void getRecentActiveCourses_ZeroLimit_ThrowsException() {
        LocalDateTime t1 = LocalDateTime.of(2026, 6, 13, 10, 0);
        List<AuditLog> logs = Arrays.asList(
                createLogWithTimestampAndCourse(t1, true, "数学")
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> AuditLogStatisticsSupport.getRecentActiveCourses(logs, 0));
        assertEquals("limit 必须为正整数，当前值: 0", ex.getMessage());
    }

    @Test
    void getRecentActiveCourses_NegativeLimit_ThrowsException() {
        LocalDateTime t1 = LocalDateTime.of(2026, 6, 13, 10, 0);
        List<AuditLog> logs = Arrays.asList(
                createLogWithTimestampAndCourse(t1, true, "数学")
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> AuditLogStatisticsSupport.getRecentActiveCourses(logs, -1));
        assertEquals("limit 必须为正整数，当前值: -1", ex.getMessage());
    }

    @Test
    void getRecentActiveCourses_LargeNegativeLimit_ThrowsException() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> AuditLogStatisticsSupport.getRecentActiveCourses(Collections.emptyList(), -100));
        assertEquals("limit 必须为正整数，当前值: -100", ex.getMessage());
    }

    @Test
    void getRecentActiveCourses_NullList_ReturnsEmptyList() {
        List<RecentActiveCourseDTO> result = AuditLogStatisticsSupport.getRecentActiveCourses(null, 10);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void getRecentActiveCourses_EmptyList_ReturnsEmptyList() {
        List<AuditLog> logs = Collections.emptyList();

        List<RecentActiveCourseDTO> result = AuditLogStatisticsSupport.getRecentActiveCourses(logs, 10);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void getRecentActiveCourses_MixedSuccessFailure_CorrectCounts() {
        LocalDateTime t1 = LocalDateTime.of(2026, 6, 13, 10, 0);
        List<AuditLog> logs = Arrays.asList(
                createLogWithTimestampAndCourse(t1, true, "数学"),
                createLogWithTimestampAndCourse(t1.plusMinutes(1), true, "数学"),
                createLogWithTimestampAndCourse(t1.plusMinutes(2), false, "数学"),
                createLogWithTimestampAndCourse(t1.plusMinutes(3), true, "物理"),
                createLogWithTimestampAndCourse(t1.plusMinutes(4), false, "物理"),
                createLogWithTimestampAndCourse(t1.plusMinutes(5), false, "物理"),
                createLogWithTimestampAndCourse(t1.plusMinutes(6), true, "化学")
        );

        List<RecentActiveCourseDTO> result = AuditLogStatisticsSupport.getRecentActiveCourses(logs, 10);

        assertEquals(3, result.size());

        RecentActiveCourseDTO math = result.stream()
                .filter(dto -> "数学".equals(dto.getCourseName()))
                .findFirst().orElse(null);
        assertNotNull(math);
        assertEquals(2, math.getSuccessCount());
        assertEquals(1, math.getFailureCount());
        assertEquals(3, math.getTotalCount());

        RecentActiveCourseDTO physics = result.stream()
                .filter(dto -> "物理".equals(dto.getCourseName()))
                .findFirst().orElse(null);
        assertNotNull(physics);
        assertEquals(1, physics.getSuccessCount());
        assertEquals(2, physics.getFailureCount());
        assertEquals(3, physics.getTotalCount());

        RecentActiveCourseDTO chemistry = result.stream()
                .filter(dto -> "化学".equals(dto.getCourseName()))
                .findFirst().orElse(null);
        assertNotNull(chemistry);
        assertEquals(1, chemistry.getSuccessCount());
        assertEquals(0, chemistry.getFailureCount());
        assertEquals(1, chemistry.getTotalCount());
    }

    @Test
    void getRecentActiveCourses_SortedByLastChangeTimeDescending() {
        LocalDateTime t1 = LocalDateTime.of(2026, 6, 13, 10, 0);
        List<AuditLog> logs = Arrays.asList(
                createLogWithTimestampAndCourse(t1, true, "数学"),
                createLogWithTimestampAndCourse(t1.plusMinutes(30), true, "物理"),
                createLogWithTimestampAndCourse(t1.plusMinutes(60), true, "化学"),
                createLogWithTimestampAndCourse(t1.plusMinutes(15), true, "生物")
        );

        List<RecentActiveCourseDTO> result = AuditLogStatisticsSupport.getRecentActiveCourses(logs, 10);

        assertEquals(4, result.size());
        assertEquals("化学", result.get(0).getCourseName());
        assertEquals("物理", result.get(1).getCourseName());
        assertEquals("生物", result.get(2).getCourseName());
        assertEquals("数学", result.get(3).getCourseName());
    }

    @Test
    void getRecentActiveCourses_SameLastChangeTime_SortedByTotalCountDescending() {
        LocalDateTime sameTime = LocalDateTime.of(2026, 6, 13, 10, 0);
        List<AuditLog> logs = Arrays.asList(
                createLogWithTimestampAndCourse(sameTime, true, "数学"),
                createLogWithTimestampAndCourse(sameTime, true, "数学"),
                createLogWithTimestampAndCourse(sameTime, true, "数学"),
                createLogWithTimestampAndCourse(sameTime, true, "物理"),
                createLogWithTimestampAndCourse(sameTime, true, "物理"),
                createLogWithTimestampAndCourse(sameTime, true, "化学"),
                createLogWithTimestampAndCourse(sameTime, true, "化学"),
                createLogWithTimestampAndCourse(sameTime, true, "化学"),
                createLogWithTimestampAndCourse(sameTime, true, "化学")
        );

        List<RecentActiveCourseDTO> result = AuditLogStatisticsSupport.getRecentActiveCourses(logs, 10);

        assertEquals(3, result.size());
        assertEquals("化学", result.get(0).getCourseName());
        assertEquals(4, result.get(0).getTotalCount());
        assertEquals("数学", result.get(1).getCourseName());
        assertEquals(3, result.get(1).getTotalCount());
        assertEquals("物理", result.get(2).getCourseName());
        assertEquals(2, result.get(2).getTotalCount());
    }

    @Test
    void getRecentActiveCourses_SameTimeAndCount_SortedByCourseNameAscending() {
        LocalDateTime sameTime = LocalDateTime.of(2026, 6, 13, 10, 0);
        List<AuditLog> logs = Arrays.asList(
                createLogWithTimestampAndCourse(sameTime, true, "物理"),
                createLogWithTimestampAndCourse(sameTime, true, "数学"),
                createLogWithTimestampAndCourse(sameTime, true, "化学"),
                createLogWithTimestampAndCourse(sameTime, true, "生物")
        );

        List<RecentActiveCourseDTO> result = AuditLogStatisticsSupport.getRecentActiveCourses(logs, 10);

        assertEquals(4, result.size());
        assertEquals("化学", result.get(0).getCourseName());
        assertEquals("数学", result.get(1).getCourseName());
        assertEquals("物理", result.get(2).getCourseName());
        assertEquals("生物", result.get(3).getCourseName());
    }

    @Test
    void getRecentActiveCourses_NullCourseName_Skipped() {
        LocalDateTime t1 = LocalDateTime.of(2026, 6, 13, 10, 0);
        List<AuditLog> logs = Arrays.asList(
                createLogWithTimestampAndCourse(t1, true, "数学"),
                createLogWithTimestampAndCourse(t1.plusMinutes(1), true, null),
                createLogWithTimestampAndCourse(t1.plusMinutes(2), true, "物理")
        );

        List<RecentActiveCourseDTO> result = AuditLogStatisticsSupport.getRecentActiveCourses(logs, 10);

        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(dto -> "数学".equals(dto.getCourseName())));
        assertTrue(result.stream().anyMatch(dto -> "物理".equals(dto.getCourseName())));
    }

    @Test
    void getRecentActiveCourses_EmptyCourseName_Skipped() {
        LocalDateTime t1 = LocalDateTime.of(2026, 6, 13, 10, 0);
        List<AuditLog> logs = Arrays.asList(
                createLogWithTimestampAndCourse(t1, true, "数学"),
                createLogWithTimestampAndCourse(t1.plusMinutes(1), true, ""),
                createLogWithTimestampAndCourse(t1.plusMinutes(2), true, "物理")
        );

        List<RecentActiveCourseDTO> result = AuditLogStatisticsSupport.getRecentActiveCourses(logs, 10);

        assertEquals(2, result.size());
    }

    @Test
    void getRecentActiveCourses_WhitespaceOnlyCourseName_Skipped() {
        LocalDateTime t1 = LocalDateTime.of(2026, 6, 13, 10, 0);
        List<AuditLog> logs = Arrays.asList(
                createLogWithTimestampAndCourse(t1, true, "数学"),
                createLogWithTimestampAndCourse(t1.plusMinutes(1), true, "   "),
                createLogWithTimestampAndCourse(t1.plusMinutes(2), true, "  \t  "),
                createLogWithTimestampAndCourse(t1.plusMinutes(3), true, "物理")
        );

        List<RecentActiveCourseDTO> result = AuditLogStatisticsSupport.getRecentActiveCourses(logs, 10);

        assertEquals(2, result.size());
    }

    @Test
    void getRecentActiveCourses_WhitespaceCourseName_MergedAfterTrim() {
        LocalDateTime t1 = LocalDateTime.of(2026, 6, 13, 10, 0);
        List<AuditLog> logs = Arrays.asList(
                createLogWithTimestampAndCourse(t1, true, "数学"),
                createLogWithTimestampAndCourse(t1.plusMinutes(1), true, " 数学 "),
                createLogWithTimestampAndCourse(t1.plusMinutes(2), false, "  数学  "),
                createLogWithTimestampAndCourse(t1.plusMinutes(3), true, "\t数学\t"),
                createLogWithTimestampAndCourse(t1.plusMinutes(4), true, "物理")
        );

        List<RecentActiveCourseDTO> result = AuditLogStatisticsSupport.getRecentActiveCourses(logs, 10);

        assertEquals(2, result.size());
        RecentActiveCourseDTO math = result.stream()
                .filter(dto -> "数学".equals(dto.getCourseName()))
                .findFirst().orElse(null);
        assertNotNull(math);
        assertEquals(3, math.getSuccessCount());
        assertEquals(1, math.getFailureCount());
        assertEquals(4, math.getTotalCount());
    }

    @Test
    void getRecentActiveCourses_LimitApplied_ReturnsOnlyLimitItems() {
        LocalDateTime t1 = LocalDateTime.of(2026, 6, 13, 10, 0);
        List<AuditLog> logs = new ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            logs.add(createLogWithTimestampAndCourse(
                    t1.plusMinutes(i), true, "课程" + i));
        }

        List<RecentActiveCourseDTO> result = AuditLogStatisticsSupport.getRecentActiveCourses(logs, 5);

        assertEquals(5, result.size());
        assertEquals("课程20", result.get(0).getCourseName());
        assertEquals("课程19", result.get(1).getCourseName());
        assertEquals("课程18", result.get(2).getCourseName());
        assertEquals("课程17", result.get(3).getCourseName());
        assertEquals("课程16", result.get(4).getCourseName());
    }

    @Test
    void getRecentActiveCourses_LimitExceedsAvailableCount_ReturnsAll_NoMaxValidationInSupport() {
        LocalDateTime t1 = LocalDateTime.of(2026, 6, 13, 10, 0);
        List<AuditLog> logs = Arrays.asList(
                createLogWithTimestampAndCourse(t1, true, "数学"),
                createLogWithTimestampAndCourse(t1.plusMinutes(1), true, "物理")
        );

        List<RecentActiveCourseDTO> result = AuditLogStatisticsSupport.getRecentActiveCourses(logs, 100);

        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(dto -> "数学".equals(dto.getCourseName())));
        assertTrue(result.stream().anyMatch(dto -> "物理".equals(dto.getCourseName())));
    }

    @Test
    void getRecentActiveCourses_AllUnparseableNames_ReturnsEmpty() {
        LocalDateTime t1 = LocalDateTime.of(2026, 6, 13, 10, 0);
        List<AuditLog> logs = Arrays.asList(
                createLogWithTimestampAndCourse(t1, true, null),
                createLogWithTimestampAndCourse(t1.plusMinutes(1), true, ""),
                createLogWithTimestampAndCourse(t1.plusMinutes(2), true, "   ")
        );

        List<RecentActiveCourseDTO> result = AuditLogStatisticsSupport.getRecentActiveCourses(logs, 10);

        assertTrue(result.isEmpty());
    }

    @Test
    void getRecentActiveCourses_LastChangeTime_IsLatestTimestamp() {
        LocalDateTime t1 = LocalDateTime.of(2026, 6, 13, 10, 0);
        LocalDateTime t2 = LocalDateTime.of(2026, 6, 13, 11, 30);
        LocalDateTime t3 = LocalDateTime.of(2026, 6, 13, 14, 0);
        List<AuditLog> logs = Arrays.asList(
                createLogWithTimestampAndCourse(t1, true, "数学"),
                createLogWithTimestampAndCourse(t2, false, "数学"),
                createLogWithTimestampAndCourse(t3, true, "数学")
        );

        List<RecentActiveCourseDTO> result = AuditLogStatisticsSupport.getRecentActiveCourses(logs, 10);

        assertEquals(1, result.size());
        assertEquals(t3, result.get(0).getLastChangeTime());
    }

    @Test
    void getRecentActiveCourses_MixedScenario_CorrectSortAndCounts() {
        LocalDateTime base = LocalDateTime.of(2026, 6, 13, 10, 0);
        List<AuditLog> logs = Arrays.asList(
                createLogWithTimestampAndCourse(base, true, "数学"),
                createLogWithTimestampAndCourse(base.plusMinutes(1), true, "数学"),
                createLogWithTimestampAndCourse(base.plusMinutes(2), false, "数学"),
                createLogWithTimestampAndCourse(base.plusMinutes(3), true, null),
                createLogWithTimestampAndCourse(base.plusMinutes(4), true, "   "),
                createLogWithTimestampAndCourse(base.plusMinutes(5), true, "物理"),
                createLogWithTimestampAndCourse(base.plusMinutes(6), false, "物理"),
                createLogWithTimestampAndCourse(base.plusMinutes(90), true, "化学"),
                createLogWithTimestampAndCourse(base.plusMinutes(91), true, "化学"),
                createLogWithTimestampAndCourse(base.plusMinutes(92), false, "化学"),
                createLogWithTimestampAndCourse(base.plusMinutes(93), false, "化学"),
                createLogWithTimestampAndCourse(base.plusMinutes(30), true, "生物"),
                createLogWithTimestampAndCourse(base.plusMinutes(60), true, " 生物 "),
                createLogWithTimestampAndCourse(base.plusMinutes(120), true, "历史")
        );

        List<RecentActiveCourseDTO> result = AuditLogStatisticsSupport.getRecentActiveCourses(logs, 10);

        assertEquals(5, result.size());
        assertEquals("历史", result.get(0).getCourseName());
        assertEquals(1, result.get(0).getTotalCount());
        assertEquals("化学", result.get(1).getCourseName());
        assertEquals(4, result.get(1).getTotalCount());
        assertEquals(2, result.get(1).getSuccessCount());
        assertEquals(2, result.get(1).getFailureCount());
        assertEquals("生物", result.get(2).getCourseName());
        assertEquals(2, result.get(2).getTotalCount());
        assertEquals("物理", result.get(3).getCourseName());
        assertEquals(2, result.get(3).getTotalCount());
        assertEquals(1, result.get(3).getSuccessCount());
        assertEquals(1, result.get(3).getFailureCount());
        assertEquals("数学", result.get(4).getCourseName());
        assertEquals(3, result.get(4).getTotalCount());
        assertEquals(2, result.get(4).getSuccessCount());
        assertEquals(1, result.get(4).getFailureCount());
    }

    private static AuditLog createLogWithDate(LocalDate date, boolean success, String courseName) {
        LocalDateTime timestamp = date.atTime(10, 0);
        return new AuditLog(
                1L, OperationType.CREATE, timestamp,
                1L, courseName, "老师", "教室", "时段",
                success, success ? null : "错误信息"
        );
    }

    private static Clock fixedClockAt(LocalDate date) {
        return Clock.fixed(date.atStartOfDay(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
    }

    @Test
    void getCourseChangeTrend_NullList_ReturnsEmptyList() {
        Clock clock = fixedClockAt(LocalDate.of(2026, 6, 16));
        List<CourseChangeTrendDTO> result = AuditLogStatisticsSupport.getCourseChangeTrend(null, 7, clock);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void getCourseChangeTrend_EmptyList_ReturnsEmptyList() {
        Clock clock = fixedClockAt(LocalDate.of(2026, 6, 16));
        List<AuditLog> logs = Collections.emptyList();

        List<CourseChangeTrendDTO> result = AuditLogStatisticsSupport.getCourseChangeTrend(logs, 7, clock);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void getCourseChangeTrend_ZeroDays_ThrowsException() {
        Clock clock = fixedClockAt(LocalDate.of(2026, 6, 16));
        List<AuditLog> logs = Collections.singletonList(
                createLogWithDate(LocalDate.of(2026, 6, 16), true, "数学"));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> AuditLogStatisticsSupport.getCourseChangeTrend(logs, 0, clock));
        assertEquals("days 必须为正整数，当前值: 0", ex.getMessage());
    }

    @Test
    void getCourseChangeTrend_NegativeDays_ThrowsException() {
        Clock clock = fixedClockAt(LocalDate.of(2026, 6, 16));
        List<AuditLog> logs = Collections.singletonList(
                createLogWithDate(LocalDate.of(2026, 6, 16), true, "数学"));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> AuditLogStatisticsSupport.getCourseChangeTrend(logs, -1, clock));
        assertEquals("days 必须为正整数，当前值: -1", ex.getMessage());
    }

    @Test
    void getCourseChangeTrend_NullClock_ThrowsException() {
        List<AuditLog> logs = Collections.singletonList(
                createLogWithDate(LocalDate.of(2026, 6, 16), true, "数学"));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> AuditLogStatisticsSupport.getCourseChangeTrend(logs, 7, null));
        assertEquals("clock 不能为 null", ex.getMessage());
    }

    @Test
    void getCourseChangeTrend_MixedSuccessFailure_CorrectCounts() {
        LocalDate today = LocalDate.of(2026, 6, 16);
        Clock clock = fixedClockAt(today);
        List<AuditLog> logs = Arrays.asList(
                createLogWithDate(today, true, "数学"),
                createLogWithDate(today, true, "物理"),
                createLogWithDate(today, false, "数学"),
                createLogWithDate(today, false, "化学"),
                createLogWithDate(today, false, "生物")
        );

        List<CourseChangeTrendDTO> result = AuditLogStatisticsSupport.getCourseChangeTrend(logs, 7, clock);

        assertEquals(1, result.size());
        CourseChangeTrendDTO dto = result.get(0);
        assertEquals(today, dto.getDate());
        assertEquals(2, dto.getSuccessCount());
        assertEquals(3, dto.getFailureCount());
        assertEquals(5, dto.getTotalCount());
    }

    @Test
    void getCourseChangeTrend_MultipleDates_SortedByDateAscending() {
        LocalDate today = LocalDate.of(2026, 6, 16);
        LocalDate day1 = today.minusDays(2);
        LocalDate day2 = today.minusDays(1);
        LocalDate day3 = today;
        Clock clock = fixedClockAt(today);

        List<AuditLog> logs = Arrays.asList(
                createLogWithDate(day3, true, "数学"),
                createLogWithDate(day1, true, "物理"),
                createLogWithDate(day2, true, "化学")
        );

        List<CourseChangeTrendDTO> result = AuditLogStatisticsSupport.getCourseChangeTrend(logs, 7, clock);

        assertEquals(3, result.size());
        assertEquals(day1, result.get(0).getDate());
        assertEquals(day2, result.get(1).getDate());
        assertEquals(day3, result.get(2).getDate());
    }

    @Test
    void getCourseChangeTrend_NullCourseName_Skipped() {
        LocalDate today = LocalDate.of(2026, 6, 16);
        Clock clock = fixedClockAt(today);
        List<AuditLog> logs = Arrays.asList(
                createLogWithDate(today, true, "数学"),
                createLogWithDate(today, true, null),
                createLogWithDate(today, true, "物理")
        );

        List<CourseChangeTrendDTO> result = AuditLogStatisticsSupport.getCourseChangeTrend(logs, 7, clock);

        assertEquals(1, result.size());
        assertEquals(2, result.get(0).getTotalCount());
    }

    @Test
    void getCourseChangeTrend_EmptyCourseName_Skipped() {
        LocalDate today = LocalDate.of(2026, 6, 16);
        Clock clock = fixedClockAt(today);
        List<AuditLog> logs = Arrays.asList(
                createLogWithDate(today, true, "数学"),
                createLogWithDate(today, true, ""),
                createLogWithDate(today, true, "物理")
        );

        List<CourseChangeTrendDTO> result = AuditLogStatisticsSupport.getCourseChangeTrend(logs, 7, clock);

        assertEquals(1, result.size());
        assertEquals(2, result.get(0).getTotalCount());
    }

    @Test
    void getCourseChangeTrend_WhitespaceOnlyCourseName_Skipped() {
        LocalDate today = LocalDate.of(2026, 6, 16);
        Clock clock = fixedClockAt(today);
        List<AuditLog> logs = Arrays.asList(
                createLogWithDate(today, true, "数学"),
                createLogWithDate(today, true, "   "),
                createLogWithDate(today, true, "  \t  "),
                createLogWithDate(today, true, "物理")
        );

        List<CourseChangeTrendDTO> result = AuditLogStatisticsSupport.getCourseChangeTrend(logs, 7, clock);

        assertEquals(1, result.size());
        assertEquals(2, result.get(0).getTotalCount());
    }

    @Test
    void getCourseChangeTrend_NullTimestamp_Skipped() {
        LocalDate today = LocalDate.of(2026, 6, 16);
        Clock clock = fixedClockAt(today);
        AuditLog logWithNullTimestamp = new AuditLog(
                1L, OperationType.CREATE, null,
                1L, "物理", "老师", "教室", "时段",
                true, null
        );
        List<AuditLog> logs = Arrays.asList(
                createLogWithDate(today, true, "数学"),
                logWithNullTimestamp
        );

        List<CourseChangeTrendDTO> result = AuditLogStatisticsSupport.getCourseChangeTrend(logs, 7, clock);

        assertEquals(1, result.size());
        assertEquals(1, result.get(0).getTotalCount());
    }

    @Test
    void getCourseChangeTrend_LogsOutsideDateRange_Skipped() {
        LocalDate today = LocalDate.of(2026, 6, 16);
        LocalDate inRange = today.minusDays(2);
        LocalDate outOfRangeOld = today.minusDays(10);
        LocalDate outOfRangeFuture = today.plusDays(1);
        Clock clock = fixedClockAt(today);

        List<AuditLog> logs = Arrays.asList(
                createLogWithDate(today, true, "数学"),
                createLogWithDate(inRange, true, "物理"),
                createLogWithDate(outOfRangeOld, true, "化学"),
                createLogWithDate(outOfRangeFuture, true, "生物")
        );

        List<CourseChangeTrendDTO> result = AuditLogStatisticsSupport.getCourseChangeTrend(logs, 7, clock);

        assertEquals(2, result.size());
        assertEquals(inRange, result.get(0).getDate());
        assertEquals(today, result.get(1).getDate());
    }

    @Test
    void getCourseChangeTrend_SpecifiedDays_OnlyWithinLastNDays() {
        LocalDate today = LocalDate.of(2026, 6, 16);
        LocalDate day0 = today;
        LocalDate day1 = today.minusDays(1);
        LocalDate day2 = today.minusDays(2);
        LocalDate day3 = today.minusDays(3);
        Clock clock = fixedClockAt(today);

        List<AuditLog> logs = Arrays.asList(
                createLogWithDate(day0, true, "数学"),
                createLogWithDate(day1, true, "物理"),
                createLogWithDate(day2, true, "化学"),
                createLogWithDate(day3, true, "生物")
        );

        List<CourseChangeTrendDTO> result = AuditLogStatisticsSupport.getCourseChangeTrend(logs, 3, clock);

        assertEquals(3, result.size());
        assertEquals(day2, result.get(0).getDate());
        assertEquals(day1, result.get(1).getDate());
        assertEquals(day0, result.get(2).getDate());
    }

    @Test
    void getCourseChangeTrend_DefaultDays7_IncludesLast7Days() {
        LocalDate today = LocalDate.of(2026, 6, 16);
        Clock clock = fixedClockAt(today);
        List<AuditLog> logs = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            logs.add(createLogWithDate(today.minusDays(i), true, "课程" + i));
        }

        List<CourseChangeTrendDTO> result = AuditLogStatisticsSupport.getCourseChangeTrend(logs, 7, clock);

        assertEquals(7, result.size());
        assertEquals(today.minusDays(6), result.get(0).getDate());
        assertEquals(today, result.get(6).getDate());
    }

    @Test
    void getCourseChangeTrend_AllUnparseableNames_ReturnsEmpty() {
        LocalDate today = LocalDate.of(2026, 6, 16);
        Clock clock = fixedClockAt(today);
        List<AuditLog> logs = Arrays.asList(
                createLogWithDate(today, true, null),
                createLogWithDate(today, true, ""),
                createLogWithDate(today, true, "   ")
        );

        List<CourseChangeTrendDTO> result = AuditLogStatisticsSupport.getCourseChangeTrend(logs, 7, clock);

        assertTrue(result.isEmpty());
    }

    @Test
    void getCourseChangeTrend_WhitespaceCourseName_MergedAfterTrim() {
        LocalDate today = LocalDate.of(2026, 6, 16);
        Clock clock = fixedClockAt(today);
        List<AuditLog> logs = Arrays.asList(
                createLogWithDate(today, true, "数学"),
                createLogWithDate(today, true, " 数学 "),
                createLogWithDate(today, false, "  数学  "),
                createLogWithDate(today, true, "\t数学\t")
        );

        List<CourseChangeTrendDTO> result = AuditLogStatisticsSupport.getCourseChangeTrend(logs, 7, clock);

        assertEquals(1, result.size());
        assertEquals(3, result.get(0).getSuccessCount());
        assertEquals(1, result.get(0).getFailureCount());
        assertEquals(4, result.get(0).getTotalCount());
    }

    @Test
    void getCourseChangeTrend_MixedScenario_CorrectSortAndCounts() {
        LocalDate today = LocalDate.of(2026, 6, 16);
        LocalDate day1 = today.minusDays(2);
        LocalDate day2 = today.minusDays(1);
        Clock clock = fixedClockAt(today);

        List<AuditLog> logs = Arrays.asList(
                createLogWithDate(day2, true, "数学"),
                createLogWithDate(day2, true, " 数学 "),
                createLogWithDate(day2, false, "数学"),
                createLogWithDate(day2, true, null),
                createLogWithDate(day2, true, "   "),
                createLogWithDate(day1, true, "物理"),
                createLogWithDate(day1, false, "物理"),
                createLogWithDate(today, true, "化学"),
                createLogWithDate(today, true, "化学"),
                createLogWithDate(today, false, "化学"),
                createLogWithDate(today, false, "化学"),
                createLogWithDate(today.minusDays(10), true, "历史"),
                createLogWithDate(today.plusDays(1), true, "未来")
        );

        List<CourseChangeTrendDTO> result = AuditLogStatisticsSupport.getCourseChangeTrend(logs, 7, clock);

        assertEquals(3, result.size());

        assertEquals(day1, result.get(0).getDate());
        assertEquals(1, result.get(0).getSuccessCount());
        assertEquals(1, result.get(0).getFailureCount());
        assertEquals(2, result.get(0).getTotalCount());

        assertEquals(day2, result.get(1).getDate());
        assertEquals(2, result.get(1).getSuccessCount());
        assertEquals(1, result.get(1).getFailureCount());
        assertEquals(3, result.get(1).getTotalCount());

        assertEquals(today, result.get(2).getDate());
        assertEquals(2, result.get(2).getSuccessCount());
        assertEquals(2, result.get(2).getFailureCount());
        assertEquals(4, result.get(2).getTotalCount());
    }

    @Test
    void getCourseChangeAbnormalDays_NullList_ReturnsEmptyList() {
        Clock clock = fixedClockAt(LocalDate.of(2026, 6, 16));
        List<CourseChangeAbnormalDaysDTO> result = AuditLogStatisticsSupport.getCourseChangeAbnormalDays(null, 7, clock);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void getCourseChangeAbnormalDays_EmptyList_ReturnsEmptyList() {
        Clock clock = fixedClockAt(LocalDate.of(2026, 6, 16));
        List<AuditLog> logs = Collections.emptyList();

        List<CourseChangeAbnormalDaysDTO> result = AuditLogStatisticsSupport.getCourseChangeAbnormalDays(logs, 7, clock);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void getCourseChangeAbnormalDays_ZeroDays_ThrowsException() {
        Clock clock = fixedClockAt(LocalDate.of(2026, 6, 16));
        List<AuditLog> logs = Collections.singletonList(
                createLogWithDate(LocalDate.of(2026, 6, 16), true, "数学"));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> AuditLogStatisticsSupport.getCourseChangeAbnormalDays(logs, 0, clock));
        assertEquals("days 必须为正整数，当前值: 0", ex.getMessage());
    }

    @Test
    void getCourseChangeAbnormalDays_NegativeDays_ThrowsException() {
        Clock clock = fixedClockAt(LocalDate.of(2026, 6, 16));
        List<AuditLog> logs = Collections.singletonList(
                createLogWithDate(LocalDate.of(2026, 6, 16), true, "数学"));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> AuditLogStatisticsSupport.getCourseChangeAbnormalDays(logs, -1, clock));
        assertEquals("days 必须为正整数，当前值: -1", ex.getMessage());
    }

    @Test
    void getCourseChangeAbnormalDays_NullClock_ThrowsException() {
        List<AuditLog> logs = Collections.singletonList(
                createLogWithDate(LocalDate.of(2026, 6, 16), true, "数学"));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> AuditLogStatisticsSupport.getCourseChangeAbnormalDays(logs, 7, null));
        assertEquals("clock 不能为 null", ex.getMessage());
    }

    @Test
    void getCourseChangeAbnormalDays_NoFailureLogs_ReturnsEmptyList() {
        LocalDate today = LocalDate.of(2026, 6, 16);
        Clock clock = fixedClockAt(today);
        List<AuditLog> logs = Arrays.asList(
                createLogWithDate(today, true, "数学"),
                createLogWithDate(today, true, "物理"),
                createLogWithDate(today, true, "化学")
        );

        List<CourseChangeAbnormalDaysDTO> result = AuditLogStatisticsSupport.getCourseChangeAbnormalDays(logs, 7, clock);

        assertTrue(result.isEmpty());
    }

    @Test
    void getCourseChangeAbnormalDays_MixedSuccessFailure_CorrectCounts() {
        LocalDate today = LocalDate.of(2026, 6, 16);
        Clock clock = fixedClockAt(today);
        List<AuditLog> logs = Arrays.asList(
                createLogWithDate(today, true, "数学"),
                createLogWithDate(today, true, "物理"),
                createLogWithDate(today, false, "数学"),
                createLogWithDate(today, false, "化学"),
                createLogWithDate(today, false, "生物")
        );

        List<CourseChangeAbnormalDaysDTO> result = AuditLogStatisticsSupport.getCourseChangeAbnormalDays(logs, 7, clock);

        assertEquals(1, result.size());
        CourseChangeAbnormalDaysDTO dto = result.get(0);
        assertEquals(today, dto.getDate());
        assertEquals(2, dto.getSuccessCount());
        assertEquals(3, dto.getFailureCount());
        assertEquals(5, dto.getTotalCount());
        assertEquals(0.6, dto.getFailureRate(), 0.0001);
    }

    @Test
    void getCourseChangeAbnormalDays_FailureRateCalculation() {
        LocalDate today = LocalDate.of(2026, 6, 16);
        Clock clock = fixedClockAt(today);
        List<AuditLog> logs = Arrays.asList(
                createLogWithDate(today, true, "数学"),
                createLogWithDate(today, false, "数学")
        );

        List<CourseChangeAbnormalDaysDTO> result = AuditLogStatisticsSupport.getCourseChangeAbnormalDays(logs, 7, clock);

        assertEquals(1, result.size());
        assertEquals(0.5, result.get(0).getFailureRate(), 0.0001);
    }

    @Test
    void getCourseChangeAbnormalDays_SortedByFailureRateDescending() {
        LocalDate today = LocalDate.of(2026, 6, 16);
        LocalDate day1 = today.minusDays(1);
        LocalDate day2 = today.minusDays(2);
        Clock clock = fixedClockAt(today);

        List<AuditLog> logs = Arrays.asList(
                createLogWithDate(today, true, "数学"),
                createLogWithDate(today, false, "数学"),
                createLogWithDate(day1, true, "物理"),
                createLogWithDate(day1, true, "物理"),
                createLogWithDate(day1, true, "物理"),
                createLogWithDate(day1, false, "物理"),
                createLogWithDate(day2, true, "化学"),
                createLogWithDate(day2, false, "化学")
        );

        List<CourseChangeAbnormalDaysDTO> result = AuditLogStatisticsSupport.getCourseChangeAbnormalDays(logs, 7, clock);

        assertEquals(3, result.size());
        assertEquals(day2, result.get(0).getDate());
        assertEquals(0.5, result.get(0).getFailureRate(), 0.0001);
        assertEquals(today, result.get(1).getDate());
        assertEquals(0.5, result.get(1).getFailureRate(), 0.0001);
        assertEquals(day1, result.get(2).getDate());
        assertEquals(0.25, result.get(2).getFailureRate(), 0.0001);
    }

    @Test
    void getCourseChangeAbnormalDays_SameFailureRate_SortedByFailureCountDescending() {
        LocalDate today = LocalDate.of(2026, 6, 16);
        LocalDate day1 = today.minusDays(1);
        Clock clock = fixedClockAt(today);

        List<AuditLog> logs = Arrays.asList(
                createLogWithDate(today, true, "数学"),
                createLogWithDate(today, false, "数学"),
                createLogWithDate(day1, true, "物理"),
                createLogWithDate(day1, true, "物理"),
                createLogWithDate(day1, false, "物理"),
                createLogWithDate(day1, false, "物理")
        );

        List<CourseChangeAbnormalDaysDTO> result = AuditLogStatisticsSupport.getCourseChangeAbnormalDays(logs, 7, clock);

        assertEquals(2, result.size());
        assertEquals(day1, result.get(0).getDate());
        assertEquals(2, result.get(0).getFailureCount());
        assertEquals(0.5, result.get(0).getFailureRate(), 0.0001);
        assertEquals(today, result.get(1).getDate());
        assertEquals(1, result.get(1).getFailureCount());
        assertEquals(0.5, result.get(1).getFailureRate(), 0.0001);
    }

    @Test
    void getCourseChangeAbnormalDays_SameFailureRateAndCount_SortedByDateAscending() {
        LocalDate today = LocalDate.of(2026, 6, 16);
        LocalDate day1 = today.minusDays(2);
        LocalDate day2 = today.minusDays(1);
        Clock clock = fixedClockAt(today);

        List<AuditLog> logs = Arrays.asList(
                createLogWithDate(today, true, "数学"),
                createLogWithDate(today, false, "数学"),
                createLogWithDate(day2, true, "物理"),
                createLogWithDate(day2, false, "物理"),
                createLogWithDate(day1, true, "化学"),
                createLogWithDate(day1, false, "化学")
        );

        List<CourseChangeAbnormalDaysDTO> result = AuditLogStatisticsSupport.getCourseChangeAbnormalDays(logs, 7, clock);

        assertEquals(3, result.size());
        assertEquals(day1, result.get(0).getDate());
        assertEquals(day2, result.get(1).getDate());
        assertEquals(today, result.get(2).getDate());
    }

    @Test
    void getCourseChangeAbnormalDays_SpecifiedDays_OnlyWithinLastNDays() {
        LocalDate today = LocalDate.of(2026, 6, 16);
        LocalDate day0 = today;
        LocalDate day1 = today.minusDays(1);
        LocalDate day2 = today.minusDays(2);
        LocalDate day3 = today.minusDays(3);
        Clock clock = fixedClockAt(today);

        List<AuditLog> logs = Arrays.asList(
                createLogWithDate(day0, false, "数学"),
                createLogWithDate(day1, false, "物理"),
                createLogWithDate(day2, false, "化学"),
                createLogWithDate(day3, false, "生物")
        );

        List<CourseChangeAbnormalDaysDTO> result = AuditLogStatisticsSupport.getCourseChangeAbnormalDays(logs, 3, clock);

        assertEquals(3, result.size());
        assertEquals(day2, result.get(0).getDate());
        assertEquals(day1, result.get(1).getDate());
        assertEquals(day0, result.get(2).getDate());
    }

    @Test
    void getCourseChangeAbnormalDays_DefaultDays7_IncludesLast7Days() {
        LocalDate today = LocalDate.of(2026, 6, 16);
        Clock clock = fixedClockAt(today);
        List<AuditLog> logs = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            logs.add(createLogWithDate(today.minusDays(i), false, "课程" + i));
        }

        List<CourseChangeAbnormalDaysDTO> result = AuditLogStatisticsSupport.getCourseChangeAbnormalDays(logs, 7, clock);

        assertEquals(7, result.size());
    }

    @Test
    void getCourseChangeAbnormalDays_NullCourseName_Skipped() {
        LocalDate today = LocalDate.of(2026, 6, 16);
        Clock clock = fixedClockAt(today);
        List<AuditLog> logs = Arrays.asList(
                createLogWithDate(today, false, "数学"),
                createLogWithDate(today, false, null)
        );

        List<CourseChangeAbnormalDaysDTO> result = AuditLogStatisticsSupport.getCourseChangeAbnormalDays(logs, 7, clock);

        assertEquals(1, result.size());
        assertEquals(1, result.get(0).getFailureCount());
    }

    @Test
    void getCourseChangeAbnormalDays_EmptyCourseName_Skipped() {
        LocalDate today = LocalDate.of(2026, 6, 16);
        Clock clock = fixedClockAt(today);
        List<AuditLog> logs = Arrays.asList(
                createLogWithDate(today, false, "数学"),
                createLogWithDate(today, false, "")
        );

        List<CourseChangeAbnormalDaysDTO> result = AuditLogStatisticsSupport.getCourseChangeAbnormalDays(logs, 7, clock);

        assertEquals(1, result.size());
        assertEquals(1, result.get(0).getFailureCount());
    }

    @Test
    void getCourseChangeAbnormalDays_WhitespaceOnlyCourseName_Skipped() {
        LocalDate today = LocalDate.of(2026, 6, 16);
        Clock clock = fixedClockAt(today);
        List<AuditLog> logs = Arrays.asList(
                createLogWithDate(today, false, "数学"),
                createLogWithDate(today, false, "   ")
        );

        List<CourseChangeAbnormalDaysDTO> result = AuditLogStatisticsSupport.getCourseChangeAbnormalDays(logs, 7, clock);

        assertEquals(1, result.size());
        assertEquals(1, result.get(0).getFailureCount());
    }

    @Test
    void getCourseChangeAbnormalDays_NullTimestamp_Skipped() {
        LocalDate today = LocalDate.of(2026, 6, 16);
        Clock clock = fixedClockAt(today);
        AuditLog logWithNullTimestamp = new AuditLog(
                1L, OperationType.CREATE, null,
                1L, "物理", "老师", "教室", "时段",
                false, "错误"
        );
        List<AuditLog> logs = Arrays.asList(
                createLogWithDate(today, false, "数学"),
                logWithNullTimestamp
        );

        List<CourseChangeAbnormalDaysDTO> result = AuditLogStatisticsSupport.getCourseChangeAbnormalDays(logs, 7, clock);

        assertEquals(1, result.size());
        assertEquals(1, result.get(0).getFailureCount());
    }

    @Test
    void getCourseChangeAbnormalDays_LogsOutsideDateRange_Skipped() {
        LocalDate today = LocalDate.of(2026, 6, 16);
        LocalDate inRange = today.minusDays(2);
        LocalDate outOfRangeOld = today.minusDays(10);
        LocalDate outOfRangeFuture = today.plusDays(1);
        Clock clock = fixedClockAt(today);

        List<AuditLog> logs = Arrays.asList(
                createLogWithDate(today, false, "数学"),
                createLogWithDate(inRange, false, "物理"),
                createLogWithDate(outOfRangeOld, false, "化学"),
                createLogWithDate(outOfRangeFuture, false, "生物")
        );

        List<CourseChangeAbnormalDaysDTO> result = AuditLogStatisticsSupport.getCourseChangeAbnormalDays(logs, 7, clock);

        assertEquals(2, result.size());
        assertEquals(inRange, result.get(0).getDate());
        assertEquals(today, result.get(1).getDate());
    }

    @Test
    void getCourseChangeAbnormalDays_AllUnparseableNames_ReturnsEmpty() {
        LocalDate today = LocalDate.of(2026, 6, 16);
        Clock clock = fixedClockAt(today);
        List<AuditLog> logs = Arrays.asList(
                createLogWithDate(today, false, null),
                createLogWithDate(today, false, ""),
                createLogWithDate(today, false, "   ")
        );

        List<CourseChangeAbnormalDaysDTO> result = AuditLogStatisticsSupport.getCourseChangeAbnormalDays(logs, 7, clock);

        assertTrue(result.isEmpty());
    }

    @Test
    void getCourseChangeAbnormalDays_MixedScenario_CorrectSortAndCounts() {
        LocalDate today = LocalDate.of(2026, 6, 16);
        LocalDate day1 = today.minusDays(2);
        LocalDate day2 = today.minusDays(1);
        Clock clock = fixedClockAt(today);

        List<AuditLog> logs = Arrays.asList(
                createLogWithDate(day2, true, "数学"),
                createLogWithDate(day2, true, " 数学 "),
                createLogWithDate(day2, false, "数学"),
                createLogWithDate(day2, true, null),
                createLogWithDate(day2, true, "   "),
                createLogWithDate(day1, true, "物理"),
                createLogWithDate(day1, false, "物理"),
                createLogWithDate(today, true, "化学"),
                createLogWithDate(today, true, "化学"),
                createLogWithDate(today, false, "化学"),
                createLogWithDate(today, false, "化学"),
                createLogWithDate(today.minusDays(10), false, "历史"),
                createLogWithDate(today.plusDays(1), false, "未来")
        );

        List<CourseChangeAbnormalDaysDTO> result = AuditLogStatisticsSupport.getCourseChangeAbnormalDays(logs, 7, clock);

        assertEquals(3, result.size());

        assertEquals(today, result.get(0).getDate());
        assertEquals(2, result.get(0).getSuccessCount());
        assertEquals(2, result.get(0).getFailureCount());
        assertEquals(4, result.get(0).getTotalCount());
        assertEquals(0.5, result.get(0).getFailureRate(), 0.0001);

        assertEquals(day1, result.get(1).getDate());
        assertEquals(1, result.get(1).getSuccessCount());
        assertEquals(1, result.get(1).getFailureCount());
        assertEquals(2, result.get(1).getTotalCount());
        assertEquals(0.5, result.get(1).getFailureRate(), 0.0001);

        assertEquals(day2, result.get(2).getDate());
        assertEquals(2, result.get(2).getSuccessCount());
        assertEquals(1, result.get(2).getFailureCount());
        assertEquals(3, result.get(2).getTotalCount());
    }

    private static AuditLog createFailureLogWithMessage(LocalDateTime timestamp, String errorMessage) {
        return new AuditLog(
                1L, OperationType.CREATE, timestamp,
                1L, "课程", "老师", "教室", "时段",
                false, errorMessage
        );
    }

    private static AuditLog createSuccessLogWithMessage(LocalDateTime timestamp, String errorMessage) {
        return new AuditLog(
                1L, OperationType.CREATE, timestamp,
                1L, "课程", "老师", "教室", "时段",
                true, errorMessage
        );
    }

    @Test
    void getFailureReasonSummary_NullList_ReturnsEmptyList() {
        List<FailureReasonSummaryDTO> result = AuditLogStatisticsSupport.getFailureReasonSummary(null, 10);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void getFailureReasonSummary_EmptyList_ReturnsEmptyList() {
        List<AuditLog> logs = Collections.emptyList();

        List<FailureReasonSummaryDTO> result = AuditLogStatisticsSupport.getFailureReasonSummary(logs, 10);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void getFailureReasonSummary_ZeroLimit_ThrowsException() {
        LocalDateTime t1 = LocalDateTime.of(2026, 6, 13, 10, 0);
        List<AuditLog> logs = Collections.singletonList(
                createFailureLogWithMessage(t1, "老师冲突"));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> AuditLogStatisticsSupport.getFailureReasonSummary(logs, 0));
        assertEquals("limit 必须为正整数，当前值: 0", ex.getMessage());
    }

    @Test
    void getFailureReasonSummary_NegativeLimit_ThrowsException() {
        LocalDateTime t1 = LocalDateTime.of(2026, 6, 13, 10, 0);
        List<AuditLog> logs = Collections.singletonList(
                createFailureLogWithMessage(t1, "老师冲突"));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> AuditLogStatisticsSupport.getFailureReasonSummary(logs, -1));
        assertEquals("limit 必须为正整数，当前值: -1", ex.getMessage());
    }

    @Test
    void getFailureReasonSummary_OnlySuccessLogs_ReturnsEmpty() {
        LocalDateTime t1 = LocalDateTime.of(2026, 6, 13, 10, 0);
        List<AuditLog> logs = Arrays.asList(
                createSuccessLogWithMessage(t1, null),
                createSuccessLogWithMessage(t1.plusMinutes(1), "不应该出现")
        );

        List<FailureReasonSummaryDTO> result = AuditLogStatisticsSupport.getFailureReasonSummary(logs, 10);

        assertTrue(result.isEmpty());
    }

    @Test
    void getFailureReasonSummary_NullErrorMessage_Skipped() {
        LocalDateTime t1 = LocalDateTime.of(2026, 6, 13, 10, 0);
        List<AuditLog> logs = Arrays.asList(
                createFailureLogWithMessage(t1, "老师冲突"),
                createFailureLogWithMessage(t1.plusMinutes(1), null),
                createFailureLogWithMessage(t1.plusMinutes(2), "教室冲突")
        );

        List<FailureReasonSummaryDTO> result = AuditLogStatisticsSupport.getFailureReasonSummary(logs, 10);

        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(dto -> "老师冲突".equals(dto.getFailureReason())));
        assertTrue(result.stream().anyMatch(dto -> "教室冲突".equals(dto.getFailureReason())));
    }

    @Test
    void getFailureReasonSummary_EmptyErrorMessage_Skipped() {
        LocalDateTime t1 = LocalDateTime.of(2026, 6, 13, 10, 0);
        List<AuditLog> logs = Arrays.asList(
                createFailureLogWithMessage(t1, "老师冲突"),
                createFailureLogWithMessage(t1.plusMinutes(1), ""),
                createFailureLogWithMessage(t1.plusMinutes(2), "教室冲突")
        );

        List<FailureReasonSummaryDTO> result = AuditLogStatisticsSupport.getFailureReasonSummary(logs, 10);

        assertEquals(2, result.size());
    }

    @Test
    void getFailureReasonSummary_WhitespaceOnlyErrorMessage_Skipped() {
        LocalDateTime t1 = LocalDateTime.of(2026, 6, 13, 10, 0);
        List<AuditLog> logs = Arrays.asList(
                createFailureLogWithMessage(t1, "老师冲突"),
                createFailureLogWithMessage(t1.plusMinutes(1), "   "),
                createFailureLogWithMessage(t1.plusMinutes(2), "  \t  "),
                createFailureLogWithMessage(t1.plusMinutes(3), "教室冲突")
        );

        List<FailureReasonSummaryDTO> result = AuditLogStatisticsSupport.getFailureReasonSummary(logs, 10);

        assertEquals(2, result.size());
    }

    @Test
    void getFailureReasonSummary_WhitespaceErrorMessage_MergedAfterTrim() {
        LocalDateTime t1 = LocalDateTime.of(2026, 6, 13, 10, 0);
        List<AuditLog> logs = Arrays.asList(
                createFailureLogWithMessage(t1, "老师冲突"),
                createFailureLogWithMessage(t1.plusMinutes(1), " 老师冲突 "),
                createFailureLogWithMessage(t1.plusMinutes(2), "  老师冲突  "),
                createFailureLogWithMessage(t1.plusMinutes(3), "\t老师冲突\t"),
                createFailureLogWithMessage(t1.plusMinutes(4), "教室冲突")
        );

        List<FailureReasonSummaryDTO> result = AuditLogStatisticsSupport.getFailureReasonSummary(logs, 10);

        assertEquals(2, result.size());
        FailureReasonSummaryDTO teacherConflict = result.stream()
                .filter(dto -> "老师冲突".equals(dto.getFailureReason()))
                .findFirst()
                .orElse(null);
        assertNotNull(teacherConflict);
        assertEquals(4, teacherConflict.getCount());

        FailureReasonSummaryDTO classroomConflict = result.stream()
                .filter(dto -> "教室冲突".equals(dto.getFailureReason()))
                .findFirst()
                .orElse(null);
        assertNotNull(classroomConflict);
        assertEquals(1, classroomConflict.getCount());
    }

    @Test
    void getFailureReasonSummary_SortedByCountDescending() {
        LocalDateTime t1 = LocalDateTime.of(2026, 6, 13, 10, 0);
        List<AuditLog> logs = Arrays.asList(
                createFailureLogWithMessage(t1, "老师冲突"),
                createFailureLogWithMessage(t1.plusMinutes(1), "教室冲突"),
                createFailureLogWithMessage(t1.plusMinutes(2), "教室冲突"),
                createFailureLogWithMessage(t1.plusMinutes(3), "时间冲突"),
                createFailureLogWithMessage(t1.plusMinutes(4), "时间冲突"),
                createFailureLogWithMessage(t1.plusMinutes(5), "时间冲突")
        );

        List<FailureReasonSummaryDTO> result = AuditLogStatisticsSupport.getFailureReasonSummary(logs, 10);

        assertEquals(3, result.size());
        assertEquals("时间冲突", result.get(0).getFailureReason());
        assertEquals(3, result.get(0).getCount());
        assertEquals("教室冲突", result.get(1).getFailureReason());
        assertEquals(2, result.get(1).getCount());
        assertEquals("老师冲突", result.get(2).getFailureReason());
        assertEquals(1, result.get(2).getCount());
    }

    @Test
    void getFailureReasonSummary_SameCount_SortedByLastOccurrenceTimeDescending() {
        LocalDateTime base = LocalDateTime.of(2026, 6, 13, 10, 0);
        List<AuditLog> logs = Arrays.asList(
                createFailureLogWithMessage(base, "老师冲突"),
                createFailureLogWithMessage(base.plusMinutes(30), "教室冲突"),
                createFailureLogWithMessage(base.plusMinutes(60), "老师冲突"),
                createFailureLogWithMessage(base.plusMinutes(90), "教室冲突")
        );

        List<FailureReasonSummaryDTO> result = AuditLogStatisticsSupport.getFailureReasonSummary(logs, 10);

        assertEquals(2, result.size());
        assertEquals("教室冲突", result.get(0).getFailureReason());
        assertEquals(base.plusMinutes(90), result.get(0).getLastOccurrenceTime());
        assertEquals("老师冲突", result.get(1).getFailureReason());
        assertEquals(base.plusMinutes(60), result.get(1).getLastOccurrenceTime());
    }

    @Test
    void getFailureReasonSummary_SameCountAndTime_SortedByFailureReasonAscending() {
        LocalDateTime sameTime = LocalDateTime.of(2026, 6, 13, 10, 0);
        List<AuditLog> logs = Arrays.asList(
                createFailureLogWithMessage(sameTime, "时间冲突"),
                createFailureLogWithMessage(sameTime, "教室冲突"),
                createFailureLogWithMessage(sameTime, "老师冲突")
        );

        List<FailureReasonSummaryDTO> result = AuditLogStatisticsSupport.getFailureReasonSummary(logs, 10);

        assertEquals(3, result.size());
        assertEquals("教室冲突", result.get(0).getFailureReason());
        assertEquals("时间冲突", result.get(1).getFailureReason());
        assertEquals("老师冲突", result.get(2).getFailureReason());
    }

    @Test
    void getFailureReasonSummary_LastOccurrenceTime_IsLatestTimestamp() {
        LocalDateTime t1 = LocalDateTime.of(2026, 6, 13, 10, 0);
        LocalDateTime t2 = LocalDateTime.of(2026, 6, 13, 11, 30);
        LocalDateTime t3 = LocalDateTime.of(2026, 6, 13, 14, 0);
        List<AuditLog> logs = Arrays.asList(
                createFailureLogWithMessage(t1, "老师冲突"),
                createFailureLogWithMessage(t2, "老师冲突"),
                createFailureLogWithMessage(t3, "老师冲突")
        );

        List<FailureReasonSummaryDTO> result = AuditLogStatisticsSupport.getFailureReasonSummary(logs, 10);

        assertEquals(1, result.size());
        assertEquals(t3, result.get(0).getLastOccurrenceTime());
        assertEquals(3, result.get(0).getCount());
    }

    @Test
    void getFailureReasonSummary_LimitApplied_ReturnsOnlyLimitItems() {
        LocalDateTime t1 = LocalDateTime.of(2026, 6, 13, 10, 0);
        List<AuditLog> logs = new ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            logs.add(createFailureLogWithMessage(t1.plusMinutes(i), "错误原因" + i));
        }

        List<FailureReasonSummaryDTO> result = AuditLogStatisticsSupport.getFailureReasonSummary(logs, 5);

        assertEquals(5, result.size());
    }

    @Test
    void getFailureReasonSummary_LimitExceedsAvailableCount_ReturnsAll() {
        LocalDateTime t1 = LocalDateTime.of(2026, 6, 13, 10, 0);
        List<AuditLog> logs = Arrays.asList(
                createFailureLogWithMessage(t1, "老师冲突"),
                createFailureLogWithMessage(t1.plusMinutes(1), "教室冲突")
        );

        List<FailureReasonSummaryDTO> result = AuditLogStatisticsSupport.getFailureReasonSummary(logs, 100);

        assertEquals(2, result.size());
    }

    @Test
    void getFailureReasonSummary_NullTimestamp_Skipped() {
        LocalDateTime t1 = LocalDateTime.of(2026, 6, 13, 10, 0);
        AuditLog logWithNullTimestamp = new AuditLog(
                1L, OperationType.CREATE, null,
                1L, "课程", "老师", "教室", "时段",
                false, "老师冲突"
        );
        List<AuditLog> logs = Arrays.asList(
                logWithNullTimestamp,
                createFailureLogWithMessage(t1, "老师冲突")
        );

        List<FailureReasonSummaryDTO> result = AuditLogStatisticsSupport.getFailureReasonSummary(logs, 10);

        assertEquals(1, result.size());
        FailureReasonSummaryDTO dto = result.get(0);
        assertEquals("老师冲突", dto.getFailureReason());
        assertEquals(1, dto.getCount());
        assertEquals(t1, dto.getLastOccurrenceTime());
    }

    @Test
    void getFailureReasonSummary_AllNullTimestamp_ReturnsEmpty() {
        AuditLog log1 = new AuditLog(
                1L, OperationType.CREATE, null,
                1L, "课程", "老师", "教室", "时段",
                false, "老师冲突"
        );
        AuditLog log2 = new AuditLog(
                2L, OperationType.UPDATE, null,
                2L, "课程", "老师", "教室", "时段",
                false, "教室冲突"
        );
        List<AuditLog> logs = Arrays.asList(log1, log2);

        List<FailureReasonSummaryDTO> result = AuditLogStatisticsSupport.getFailureReasonSummary(logs, 10);

        assertTrue(result.isEmpty());
    }

    @Test
    void getFailureReasonSummary_NullTimestampDoesNotAffectOtherLogs() {
        LocalDateTime t1 = LocalDateTime.of(2026, 6, 13, 10, 0);
        LocalDateTime t2 = LocalDateTime.of(2026, 6, 13, 11, 0);
        AuditLog logWithNullTimestamp = new AuditLog(
                1L, OperationType.CREATE, null,
                1L, "课程", "老师", "教室", "时段",
                false, "老师冲突"
        );
        List<AuditLog> logs = Arrays.asList(
                logWithNullTimestamp,
                createFailureLogWithMessage(t1, "老师冲突"),
                createFailureLogWithMessage(t2, "教室冲突")
        );

        List<FailureReasonSummaryDTO> result = AuditLogStatisticsSupport.getFailureReasonSummary(logs, 10);

        assertEquals(2, result.size());
        assertEquals("教室冲突", result.get(0).getFailureReason());
        assertEquals(1, result.get(0).getCount());
        assertEquals(t2, result.get(0).getLastOccurrenceTime());
        assertEquals("老师冲突", result.get(1).getFailureReason());
        assertEquals(1, result.get(1).getCount());
        assertEquals(t1, result.get(1).getLastOccurrenceTime());
    }

    @Test
    void getFailureReasonSummary_AllUnparseableReasons_ReturnsEmpty() {
        LocalDateTime t1 = LocalDateTime.of(2026, 6, 13, 10, 0);
        List<AuditLog> logs = Arrays.asList(
                createFailureLogWithMessage(t1, null),
                createFailureLogWithMessage(t1, ""),
                createFailureLogWithMessage(t1, "   ")
        );

        List<FailureReasonSummaryDTO> result = AuditLogStatisticsSupport.getFailureReasonSummary(logs, 10);

        assertTrue(result.isEmpty());
    }

    @Test
    void getFailureReasonSummary_MixedScenario_CorrectSortAndCounts() {
        LocalDateTime base = LocalDateTime.of(2026, 6, 13, 10, 0);
        List<AuditLog> logs = Arrays.asList(
                createFailureLogWithMessage(base, "老师冲突"),
                createFailureLogWithMessage(base.plusMinutes(1), " 老师冲突 "),
                createSuccessLogWithMessage(base.plusMinutes(2), "不应该统计成功的"),
                createFailureLogWithMessage(base.plusMinutes(3), null),
                createFailureLogWithMessage(base.plusMinutes(4), "   "),
                createFailureLogWithMessage(base.plusMinutes(5), "教室冲突"),
                createFailureLogWithMessage(base.plusMinutes(6), "教室冲突"),
                createFailureLogWithMessage(base.plusMinutes(90), "时间冲突"),
                createFailureLogWithMessage(base.plusMinutes(91), "时间冲突"),
                createFailureLogWithMessage(base.plusMinutes(92), "时间冲突"),
                createFailureLogWithMessage(base.plusMinutes(93), "时间冲突"),
                createFailureLogWithMessage(base.plusMinutes(30), "系统异常"),
                createFailureLogWithMessage(base.plusMinutes(60), " 系统异常 "),
                createFailureLogWithMessage(base.plusMinutes(120), "网络超时")
        );

        List<FailureReasonSummaryDTO> result = AuditLogStatisticsSupport.getFailureReasonSummary(logs, 10);

        assertEquals(5, result.size());

        assertEquals("时间冲突", result.get(0).getFailureReason());
        assertEquals(4, result.get(0).getCount());
        assertEquals(base.plusMinutes(93), result.get(0).getLastOccurrenceTime());

        assertEquals("系统异常", result.get(1).getFailureReason());
        assertEquals(2, result.get(1).getCount());
        assertEquals(base.plusMinutes(60), result.get(1).getLastOccurrenceTime());

        assertEquals("教室冲突", result.get(2).getFailureReason());
        assertEquals(2, result.get(2).getCount());

        assertEquals("老师冲突", result.get(3).getFailureReason());
        assertEquals(2, result.get(3).getCount());

        assertEquals("网络超时", result.get(4).getFailureReason());
        assertEquals(1, result.get(4).getCount());
    }

    private static AuditLog createFailureLogWithOperation(LocalDateTime timestamp, OperationType operationType) {
        return new AuditLog(
                1L, operationType, timestamp,
                1L, "课程", "老师", "教室", "时段",
                false, "错误信息"
        );
    }

    private static AuditLog createSuccessLogWithOperation(LocalDateTime timestamp, OperationType operationType) {
        return new AuditLog(
                1L, operationType, timestamp,
                1L, "课程", "老师", "教室", "时段",
                true, null
        );
    }

    @Test
    void getOperationFailureSummary_NullList_ReturnsEmptyList() {
        List<OperationFailureSummaryDTO> result = AuditLogStatisticsSupport.getOperationFailureSummary(null, 10);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void getOperationFailureSummary_EmptyList_ReturnsEmptyList() {
        List<AuditLog> logs = Collections.emptyList();

        List<OperationFailureSummaryDTO> result = AuditLogStatisticsSupport.getOperationFailureSummary(logs, 10);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void getOperationFailureSummary_ZeroLimit_ThrowsException() {
        LocalDateTime t1 = LocalDateTime.of(2026, 6, 13, 10, 0);
        List<AuditLog> logs = Collections.singletonList(
                createFailureLogWithOperation(t1, OperationType.CREATE));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> AuditLogStatisticsSupport.getOperationFailureSummary(logs, 0));
        assertEquals("limit 必须为正整数，当前值: 0", ex.getMessage());
    }

    @Test
    void getOperationFailureSummary_NegativeLimit_ThrowsException() {
        LocalDateTime t1 = LocalDateTime.of(2026, 6, 13, 10, 0);
        List<AuditLog> logs = Collections.singletonList(
                createFailureLogWithOperation(t1, OperationType.CREATE));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> AuditLogStatisticsSupport.getOperationFailureSummary(logs, -1));
        assertEquals("limit 必须为正整数，当前值: -1", ex.getMessage());
    }

    @Test
    void getOperationFailureSummary_LargeNegativeLimit_ThrowsException() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> AuditLogStatisticsSupport.getOperationFailureSummary(Collections.emptyList(), -100));
        assertEquals("limit 必须为正整数，当前值: -100", ex.getMessage());
    }

    @Test
    void getOperationFailureSummary_OnlySuccessLogs_ReturnsEmpty() {
        LocalDateTime t1 = LocalDateTime.of(2026, 6, 13, 10, 0);
        List<AuditLog> logs = Arrays.asList(
                createSuccessLogWithOperation(t1, OperationType.CREATE),
                createSuccessLogWithOperation(t1.plusMinutes(1), OperationType.UPDATE)
        );

        List<OperationFailureSummaryDTO> result = AuditLogStatisticsSupport.getOperationFailureSummary(logs, 10);

        assertTrue(result.isEmpty());
    }

    @Test
    void getOperationFailureSummary_NullOperationType_Skipped() {
        LocalDateTime t1 = LocalDateTime.of(2026, 6, 13, 10, 0);
        AuditLog logWithNullOperation = new AuditLog(
                1L, null, t1,
                1L, "课程", "老师", "教室", "时段",
                false, "错误信息"
        );
        List<AuditLog> logs = Arrays.asList(
                createFailureLogWithOperation(t1, OperationType.CREATE),
                logWithNullOperation,
                createFailureLogWithOperation(t1.plusMinutes(1), OperationType.UPDATE)
        );

        List<OperationFailureSummaryDTO> result = AuditLogStatisticsSupport.getOperationFailureSummary(logs, 10);

        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(dto -> dto.getOperationType() == OperationType.CREATE));
        assertTrue(result.stream().anyMatch(dto -> dto.getOperationType() == OperationType.UPDATE));
    }

    @Test
    void getOperationFailureSummary_OnlyNullOperationType_ReturnsEmpty() {
        LocalDateTime t1 = LocalDateTime.of(2026, 6, 13, 10, 0);
        AuditLog log1 = new AuditLog(
                1L, null, t1,
                1L, "课程", "老师", "教室", "时段",
                false, "错误信息"
        );
        AuditLog log2 = new AuditLog(
                2L, null, t1.plusMinutes(1),
                2L, "课程", "老师", "教室", "时段",
                false, "错误信息"
        );
        List<AuditLog> logs = Arrays.asList(log1, log2);

        List<OperationFailureSummaryDTO> result = AuditLogStatisticsSupport.getOperationFailureSummary(logs, 10);

        assertTrue(result.isEmpty());
    }

    @Test
    void getOperationFailureSummary_NullTimestamp_Skipped() {
        LocalDateTime t1 = LocalDateTime.of(2026, 6, 13, 10, 0);
        AuditLog logWithNullTimestamp = new AuditLog(
                1L, OperationType.CREATE, null,
                1L, "课程", "老师", "教室", "时段",
                false, "错误信息"
        );
        List<AuditLog> logs = Arrays.asList(
                logWithNullTimestamp,
                createFailureLogWithOperation(t1, OperationType.CREATE)
        );

        List<OperationFailureSummaryDTO> result = AuditLogStatisticsSupport.getOperationFailureSummary(logs, 10);

        assertEquals(1, result.size());
        OperationFailureSummaryDTO dto = result.get(0);
        assertEquals(OperationType.CREATE, dto.getOperationType());
        assertEquals(1, dto.getFailureCount());
        assertEquals(t1, dto.getLastFailureTime());
    }

    @Test
    void getOperationFailureSummary_AllNullTimestamp_ReturnsEmpty() {
        AuditLog log1 = new AuditLog(
                1L, OperationType.CREATE, null,
                1L, "课程", "老师", "教室", "时段",
                false, "错误信息"
        );
        AuditLog log2 = new AuditLog(
                2L, OperationType.UPDATE, null,
                2L, "课程", "老师", "教室", "时段",
                false, "错误信息"
        );
        List<AuditLog> logs = Arrays.asList(log1, log2);

        List<OperationFailureSummaryDTO> result = AuditLogStatisticsSupport.getOperationFailureSummary(logs, 10);

        assertTrue(result.isEmpty());
    }

    @Test
    void getOperationFailureSummary_NullOperationTypeAndTimestamp_SkippedTogether() {
        LocalDateTime t1 = LocalDateTime.of(2026, 6, 13, 10, 0);
        AuditLog nullOp = new AuditLog(
                1L, null, t1,
                1L, "课程", "老师", "教室", "时段",
                false, "错误"
        );
        AuditLog nullTs = new AuditLog(
                2L, OperationType.DELETE, null,
                2L, "课程", "老师", "教室", "时段",
                false, "错误"
        );
        AuditLog bothNull = new AuditLog(
                3L, null, null,
                3L, "课程", "老师", "教室", "时段",
                false, "错误"
        );
        List<AuditLog> logs = Arrays.asList(
                nullOp,
                nullTs,
                bothNull,
                createFailureLogWithOperation(t1, OperationType.UPDATE)
        );

        List<OperationFailureSummaryDTO> result = AuditLogStatisticsSupport.getOperationFailureSummary(logs, 10);

        assertEquals(1, result.size());
        assertEquals(OperationType.UPDATE, result.get(0).getOperationType());
    }

    @Test
    void getOperationFailureSummary_MixedSuccessFailure_CorrectCounts() {
        LocalDateTime t1 = LocalDateTime.of(2026, 6, 13, 10, 0);
        List<AuditLog> logs = Arrays.asList(
                createFailureLogWithOperation(t1, OperationType.CREATE),
                createSuccessLogWithOperation(t1.plusMinutes(1), OperationType.CREATE),
                createFailureLogWithOperation(t1.plusMinutes(2), OperationType.CREATE),
                createFailureLogWithOperation(t1.plusMinutes(3), OperationType.UPDATE),
                createSuccessLogWithOperation(t1.plusMinutes(4), OperationType.DELETE),
                createFailureLogWithOperation(t1.plusMinutes(5), OperationType.UPDATE)
        );

        List<OperationFailureSummaryDTO> result = AuditLogStatisticsSupport.getOperationFailureSummary(logs, 10);

        assertEquals(2, result.size());

        OperationFailureSummaryDTO createDto = result.stream()
                .filter(dto -> dto.getOperationType() == OperationType.CREATE)
                .findFirst().orElse(null);
        assertNotNull(createDto);
        assertEquals(2, createDto.getFailureCount());

        OperationFailureSummaryDTO updateDto = result.stream()
                .filter(dto -> dto.getOperationType() == OperationType.UPDATE)
                .findFirst().orElse(null);
        assertNotNull(updateDto);
        assertEquals(2, updateDto.getFailureCount());
    }

    @Test
    void getOperationFailureSummary_SortedByFailureCountDescending() {
        LocalDateTime t1 = LocalDateTime.of(2026, 6, 13, 10, 0);
        List<AuditLog> logs = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            logs.add(createFailureLogWithOperation(t1.plusMinutes(i), OperationType.CREATE));
        }
        for (int i = 0; i < 3; i++) {
            logs.add(createFailureLogWithOperation(t1.plusMinutes(10 + i), OperationType.UPDATE));
        }
        for (int i = 0; i < 1; i++) {
            logs.add(createFailureLogWithOperation(t1.plusMinutes(20 + i), OperationType.DELETE));
        }

        List<OperationFailureSummaryDTO> result = AuditLogStatisticsSupport.getOperationFailureSummary(logs, 10);

        assertEquals(3, result.size());
        assertEquals(OperationType.CREATE, result.get(0).getOperationType());
        assertEquals(5, result.get(0).getFailureCount());
        assertEquals(OperationType.UPDATE, result.get(1).getOperationType());
        assertEquals(3, result.get(1).getFailureCount());
        assertEquals(OperationType.DELETE, result.get(2).getOperationType());
        assertEquals(1, result.get(2).getFailureCount());
    }

    @Test
    void getOperationFailureSummary_SameCount_SortedByLastFailureTimeDescending() {
        LocalDateTime base = LocalDateTime.of(2026, 6, 13, 10, 0);
        List<AuditLog> logs = Arrays.asList(
                createFailureLogWithOperation(base, OperationType.CREATE),
                createFailureLogWithOperation(base.plusMinutes(30), OperationType.UPDATE),
                createFailureLogWithOperation(base.plusMinutes(60), OperationType.CREATE),
                createFailureLogWithOperation(base.plusMinutes(90), OperationType.UPDATE)
        );

        List<OperationFailureSummaryDTO> result = AuditLogStatisticsSupport.getOperationFailureSummary(logs, 10);

        assertEquals(2, result.size());
        assertEquals(OperationType.UPDATE, result.get(0).getOperationType());
        assertEquals(base.plusMinutes(90), result.get(0).getLastFailureTime());
        assertEquals(OperationType.CREATE, result.get(1).getOperationType());
        assertEquals(base.plusMinutes(60), result.get(1).getLastFailureTime());
    }

    @Test
    void getOperationFailureSummary_SameCountAndTime_SortedByOperationTypeNameAscending() {
        LocalDateTime sameTime = LocalDateTime.of(2026, 6, 13, 10, 0);
        List<AuditLog> logs = Arrays.asList(
                createFailureLogWithOperation(sameTime, OperationType.UPDATE),
                createFailureLogWithOperation(sameTime, OperationType.DELETE),
                createFailureLogWithOperation(sameTime, OperationType.CREATE)
        );

        List<OperationFailureSummaryDTO> result = AuditLogStatisticsSupport.getOperationFailureSummary(logs, 10);

        assertEquals(3, result.size());
        assertEquals(OperationType.CREATE, result.get(0).getOperationType());
        assertEquals(OperationType.DELETE, result.get(1).getOperationType());
        assertEquals(OperationType.UPDATE, result.get(2).getOperationType());
    }

    @Test
    void getOperationFailureSummary_LastFailureTime_IsLatestTimestamp() {
        LocalDateTime t1 = LocalDateTime.of(2026, 6, 13, 10, 0);
        LocalDateTime t2 = LocalDateTime.of(2026, 6, 13, 11, 30);
        LocalDateTime t3 = LocalDateTime.of(2026, 6, 13, 14, 0);
        List<AuditLog> logs = Arrays.asList(
                createFailureLogWithOperation(t1, OperationType.CREATE),
                createFailureLogWithOperation(t2, OperationType.CREATE),
                createFailureLogWithOperation(t3, OperationType.CREATE)
        );

        List<OperationFailureSummaryDTO> result = AuditLogStatisticsSupport.getOperationFailureSummary(logs, 10);

        assertEquals(1, result.size());
        assertEquals(t3, result.get(0).getLastFailureTime());
        assertEquals(3, result.get(0).getFailureCount());
    }

    @Test
    void getOperationFailureSummary_LimitApplied_ReturnsOnlyLimitItems() {
        LocalDateTime t1 = LocalDateTime.of(2026, 6, 13, 10, 0);
        OperationType[] types = OperationType.values();
        List<AuditLog> logs = new ArrayList<>();
        for (int i = 0; i < types.length; i++) {
            logs.add(createFailureLogWithOperation(t1.plusMinutes(i), types[i]));
        }

        List<OperationFailureSummaryDTO> result = AuditLogStatisticsSupport.getOperationFailureSummary(logs, 3);

        assertEquals(3, result.size());
    }

    @Test
    void getOperationFailureSummary_LimitExceedsAvailableCount_ReturnsAll() {
        LocalDateTime t1 = LocalDateTime.of(2026, 6, 13, 10, 0);
        List<AuditLog> logs = Arrays.asList(
                createFailureLogWithOperation(t1, OperationType.CREATE),
                createFailureLogWithOperation(t1.plusMinutes(1), OperationType.UPDATE)
        );

        List<OperationFailureSummaryDTO> result = AuditLogStatisticsSupport.getOperationFailureSummary(logs, 100);

        assertEquals(2, result.size());
    }

    @Test
    void getOperationFailureSummary_AllOperationTypes_Included() {
        LocalDateTime t1 = LocalDateTime.of(2026, 6, 13, 10, 0);
        OperationType[] types = OperationType.values();
        List<AuditLog> logs = new ArrayList<>();
        for (int i = 0; i < types.length; i++) {
            logs.add(createFailureLogWithOperation(t1.plusMinutes(i), types[i]));
        }

        List<OperationFailureSummaryDTO> result = AuditLogStatisticsSupport.getOperationFailureSummary(logs, 50);

        assertEquals(types.length, result.size());
    }

    @Test
    void getOperationFailureSummary_MixedScenario_CorrectSortAndCounts() {
        LocalDateTime base = LocalDateTime.of(2026, 6, 13, 10, 0);
        AuditLog nullOpLog = new AuditLog(
                1L, null, base.plusMinutes(3),
                1L, "课程", "老师", "教室", "时段",
                false, "错误信息"
        );
        AuditLog nullTsLog = new AuditLog(
                2L, OperationType.UPDATE, null,
                2L, "课程", "老师", "教室", "时段",
                false, "错误信息"
        );
        List<AuditLog> logs = Arrays.asList(
                createFailureLogWithOperation(base, OperationType.CREATE),
                createFailureLogWithOperation(base.plusMinutes(1), OperationType.CREATE),
                createSuccessLogWithOperation(base.plusMinutes(2), OperationType.CREATE),
                nullOpLog,
                nullTsLog,
                createFailureLogWithOperation(base.plusMinutes(5), OperationType.UPDATE),
                createFailureLogWithOperation(base.plusMinutes(6), OperationType.UPDATE),
                createFailureLogWithOperation(base.plusMinutes(90), OperationType.DELETE),
                createFailureLogWithOperation(base.plusMinutes(91), OperationType.DELETE),
                createFailureLogWithOperation(base.plusMinutes(92), OperationType.DELETE),
                createFailureLogWithOperation(base.plusMinutes(93), OperationType.DELETE),
                createFailureLogWithOperation(base.plusMinutes(30), OperationType.BATCH_CREATE),
                createFailureLogWithOperation(base.plusMinutes(60), OperationType.BATCH_CREATE),
                createSuccessLogWithOperation(base.plusMinutes(120), OperationType.UNDO)
        );

        List<OperationFailureSummaryDTO> result = AuditLogStatisticsSupport.getOperationFailureSummary(logs, 10);

        assertEquals(4, result.size());

        assertEquals(OperationType.DELETE, result.get(0).getOperationType());
        assertEquals(4, result.get(0).getFailureCount());
        assertEquals(base.plusMinutes(93), result.get(0).getLastFailureTime());

        assertEquals(OperationType.BATCH_CREATE, result.get(1).getOperationType());
        assertEquals(2, result.get(1).getFailureCount());
        assertEquals(base.plusMinutes(60), result.get(1).getLastFailureTime());

        assertEquals(OperationType.UPDATE, result.get(2).getOperationType());
        assertEquals(2, result.get(2).getFailureCount());
        assertEquals(base.plusMinutes(6), result.get(2).getLastFailureTime());

        assertEquals(OperationType.CREATE, result.get(3).getOperationType());
        assertEquals(2, result.get(3).getFailureCount());
        assertEquals(base.plusMinutes(1), result.get(3).getLastFailureTime());
    }
}
