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
import com.coursescheduler.model.CourseSchedule;
import com.coursescheduler.model.OperationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AuditLogServiceTest {

    private AuditLogService auditLogService;
    private TestClock testClock;

    static class TestClock extends Clock {
        private Instant instant;
        private final ZoneId zone;

        TestClock(Instant instant) {
            this.instant = instant;
            this.zone = ZoneId.systemDefault();
        }

        void advanceSeconds(long seconds) {
            this.instant = this.instant.plusSeconds(seconds);
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    @BeforeEach
    void setUp() {
        testClock = new TestClock(Instant.parse("2026-06-13T10:00:00Z"));
        auditLogService = new AuditLogService(testClock);
    }

    @Test
    void testRecordLog_SuccessLog() {
        auditLogService.recordLog(
                OperationType.CREATE, 1L, "数学", "张老师", "A101", "周一 08:00-10:00",
                true, null
        );

        List<AuditLogResponse> logs = auditLogService.getAllLogs();
        assertEquals(1, logs.size());
        AuditLogResponse log = logs.get(0);
        assertEquals(OperationType.CREATE, log.getOperationType());
        assertEquals(1L, log.getCourseId());
        assertEquals("数学", log.getCourseName());
        assertEquals("张老师", log.getTeacherName());
        assertEquals("A101", log.getClassroom());
        assertEquals("周一 08:00-10:00", log.getTimeSlot());
        assertTrue(log.isSuccess());
        assertNull(log.getErrorMessage());
        assertNotNull(log.getTimestamp());
        assertNotNull(log.getId());
    }

    @Test
    void testRecordLog_FailureLog() {
        auditLogService.recordLog(
                OperationType.UPDATE, 2L, "物理", "李老师", "B202", "周二 14:00-16:00",
                false, "老师冲突"
        );

        List<AuditLogResponse> logs = auditLogService.getAllLogs();
        assertEquals(1, logs.size());
        AuditLogResponse log = logs.get(0);
        assertEquals(OperationType.UPDATE, log.getOperationType());
        assertFalse(log.isSuccess());
        assertEquals("老师冲突", log.getErrorMessage());
    }

    @Test
    void testRecordLog_WithCourseSchedule() {
        CourseSchedule schedule = new CourseSchedule(3L, "化学", "王老师", "C303", "周三 10:00-12:00");
        auditLogService.recordLog(OperationType.DELETE, schedule, true, null);

        List<AuditLogResponse> logs = auditLogService.getAllLogs();
        assertEquals(1, logs.size());
        AuditLogResponse log = logs.get(0);
        assertEquals(OperationType.DELETE, log.getOperationType());
        assertEquals(3L, log.getCourseId());
        assertEquals("化学", log.getCourseName());
        assertEquals("王老师", log.getTeacherName());
        assertEquals("C303", log.getClassroom());
        assertEquals("周三 10:00-12:00", log.getTimeSlot());
        assertTrue(log.isSuccess());
    }

    @Test
    void testQueryLogs_FilterByOperationType() {
        auditLogService.recordLog(OperationType.CREATE, 1L, "数学", "张老师", "A101", "周一 08:00-10:00", true, null);
        auditLogService.recordLog(OperationType.UPDATE, 2L, "物理", "李老师", "B202", "周二 14:00-16:00", true, null);
        auditLogService.recordLog(OperationType.DELETE, 1L, "数学", "张老师", "A101", "周一 08:00-10:00", true, null);

        AuditLogFilterRequest filter = new AuditLogFilterRequest();
        filter.setOperationType(OperationType.CREATE);

        List<AuditLogResponse> logs = auditLogService.queryLogs(filter);
        assertEquals(1, logs.size());
        assertEquals(OperationType.CREATE, logs.get(0).getOperationType());
    }

    @Test
    void testQueryLogs_FilterBySuccess() {
        auditLogService.recordLog(OperationType.CREATE, 1L, "数学", "张老师", "A101", "周一 08:00-10:00", true, null);
        auditLogService.recordLog(OperationType.CREATE, null, "物理", "张老师", "A101", "周一 08:00-10:00", false, "教室冲突");

        AuditLogFilterRequest successFilter = new AuditLogFilterRequest();
        successFilter.setSuccess(true);
        List<AuditLogResponse> successLogs = auditLogService.queryLogs(successFilter);
        assertEquals(1, successLogs.size());
        assertTrue(successLogs.get(0).isSuccess());

        AuditLogFilterRequest failureFilter = new AuditLogFilterRequest();
        failureFilter.setSuccess(false);
        List<AuditLogResponse> failureLogs = auditLogService.queryLogs(failureFilter);
        assertEquals(1, failureLogs.size());
        assertFalse(failureLogs.get(0).isSuccess());
    }

    @Test
    void testQueryLogs_FilterByOperationTypeAndSuccess() {
        auditLogService.recordLog(OperationType.CREATE, 1L, "数学", "张老师", "A101", "周一 08:00-10:00", true, null);
        auditLogService.recordLog(OperationType.CREATE, null, "物理", "张老师", "A101", "周一 08:00-10:00", false, "教室冲突");
        auditLogService.recordLog(OperationType.DELETE, 1L, "数学", "张老师", "A101", "周一 08:00-10:00", true, null);

        AuditLogFilterRequest filter = new AuditLogFilterRequest();
        filter.setOperationType(OperationType.CREATE);
        filter.setSuccess(false);

        List<AuditLogResponse> logs = auditLogService.queryLogs(filter);
        assertEquals(1, logs.size());
        assertEquals(OperationType.CREATE, logs.get(0).getOperationType());
        assertFalse(logs.get(0).isSuccess());
    }

    @Test
    void testQueryLogs_NoFilter_ReturnsAll() {
        auditLogService.recordLog(OperationType.CREATE, 1L, "数学", "张老师", "A101", "周一 08:00-10:00", true, null);
        auditLogService.recordLog(OperationType.UPDATE, 2L, "物理", "李老师", "B202", "周二 14:00-16:00", false, "冲突");

        List<AuditLogResponse> logs = auditLogService.getAllLogs();
        assertEquals(2, logs.size());
    }

    @Test
    void testQueryLogs_OrderedByTimestampDescending() {
        testClock.advanceSeconds(0);
        auditLogService.recordLog(OperationType.CREATE, 1L, "数学", "张老师", "A101", "周一 08:00-10:00", true, null);
        testClock.advanceSeconds(60);
        auditLogService.recordLog(OperationType.UPDATE, 1L, "高等数学", "张老师", "A101", "周一 08:00-10:00", true, null);
        testClock.advanceSeconds(60);
        auditLogService.recordLog(OperationType.DELETE, 1L, "高等数学", "张老师", "A101", "周一 08:00-10:00", true, null);

        List<AuditLogResponse> logs = auditLogService.getAllLogs();
        assertEquals(3, logs.size());
        assertEquals(OperationType.DELETE, logs.get(0).getOperationType());
        assertEquals(OperationType.UPDATE, logs.get(1).getOperationType());
        assertEquals(OperationType.CREATE, logs.get(2).getOperationType());
    }

    @Test
    void testClearAllLogs() {
        auditLogService.recordLog(OperationType.CREATE, 1L, "数学", "张老师", "A101", "周一 08:00-10:00", true, null);
        auditLogService.recordLog(OperationType.UPDATE, 2L, "物理", "李老师", "B202", "周二 14:00-16:00", true, null);

        assertEquals(2, auditLogService.getAllLogs().size());

        auditLogService.clearAllLogs();

        assertTrue(auditLogService.getAllLogs().isEmpty());
    }

    @Test
    void testRecordLog_IdAutoIncrement() {
        auditLogService.recordLog(OperationType.CREATE, 1L, "数学", "张老师", "A101", "周一 08:00-10:00", true, null);
        auditLogService.recordLog(OperationType.UPDATE, 2L, "物理", "李老师", "B202", "周二 14:00-16:00", true, null);

        List<AuditLogResponse> logs = auditLogService.getAllLogs();
        assertEquals(2, logs.size());

        auditLogService.clearAllLogs();

        auditLogService.recordLog(OperationType.DELETE, 3L, "化学", "王老师", "C303", "周三 10:00-12:00", true, null);
        List<AuditLogResponse> logsAfterClear = auditLogService.getAllLogs();
        assertEquals(1, logsAfterClear.size());
        assertEquals(1L, logsAfterClear.get(0).getId());
    }

    @Test
    void testRecordLog_UndoOperation_NullCourseFields() {
        auditLogService.recordLog(OperationType.UNDO, null, null, null, null, null, true, null);

        List<AuditLogResponse> logs = auditLogService.getAllLogs();
        assertEquals(1, logs.size());
        AuditLogResponse log = logs.get(0);
        assertEquals(OperationType.UNDO, log.getOperationType());
        assertNull(log.getCourseId());
        assertNull(log.getCourseName());
        assertNull(log.getTeacherName());
        assertNull(log.getClassroom());
        assertNull(log.getTimeSlot());
        assertTrue(log.isSuccess());
    }

    @Test
    void testGetChangeSummary_DefaultLimit10_MoreThan10Logs_ReturnsOnly10() {
        for (int i = 1; i <= 15; i++) {
            testClock.advanceSeconds(60);
            auditLogService.recordLog(OperationType.CREATE, (long) i, "课程" + i, "老师" + i, "教室" + i, "周一 08:00-10:00", true, null);
        }

        List<ChangeSummaryResponse> summary = auditLogService.getChangeSummary(10);
        assertEquals(10, summary.size());
        assertEquals("课程15", summary.get(0).getCourseName());
        assertEquals("课程14", summary.get(1).getCourseName());
        assertEquals("课程6", summary.get(9).getCourseName());
    }

    @Test
    void testGetChangeSummary_OrderedByTimestampDescending() {
        auditLogService.recordLog(OperationType.CREATE, 1L, "数学", "张老师", "A101", "周一 08:00-10:00", true, null);
        testClock.advanceSeconds(60);
        auditLogService.recordLog(OperationType.UPDATE, 1L, "高等数学", "张老师", "A101", "周一 08:00-10:00", true, null);
        testClock.advanceSeconds(60);
        auditLogService.recordLog(OperationType.DELETE, 1L, "高等数学", "张老师", "A101", "周一 08:00-10:00", true, null);

        List<ChangeSummaryResponse> summary = auditLogService.getChangeSummary(10);
        assertEquals(3, summary.size());
        assertEquals(OperationType.DELETE, summary.get(0).getOperationType());
        assertEquals(OperationType.UPDATE, summary.get(1).getOperationType());
        assertEquals(OperationType.CREATE, summary.get(2).getOperationType());

        assertTrue(summary.get(0).getTimestamp().isAfter(summary.get(1).getTimestamp()));
        assertTrue(summary.get(1).getTimestamp().isAfter(summary.get(2).getTimestamp()));
    }

    @Test
    void testGetChangeSummary_CustomLimit5_ReturnsTop5() {
        for (int i = 1; i <= 30; i++) {
            testClock.advanceSeconds(60);
            auditLogService.recordLog(OperationType.CREATE, (long) i, "课程" + i, "老师" + i, "教室" + i, "周一 08:00-10:00", true, null);
        }

        List<ChangeSummaryResponse> summary = auditLogService.getChangeSummary(5);
        assertEquals(5, summary.size());
        assertEquals("课程30", summary.get(0).getCourseName());
        assertEquals("课程29", summary.get(1).getCourseName());
        assertEquals("课程28", summary.get(2).getCourseName());
        assertEquals("课程27", summary.get(3).getCourseName());
        assertEquals("课程26", summary.get(4).getCourseName());
    }

    @Test
    void testGetChangeSummary_MixedWithFailures_ExcludesFailures() {
        auditLogService.recordLog(OperationType.CREATE, 1L, "数学", "张老师", "A101", "周一 08:00-10:00", true, null);
        auditLogService.recordLog(OperationType.UPDATE, 2L, "物理", "李老师", "B202", "周二 14:00-16:00", false, "老师冲突");
        auditLogService.recordLog(OperationType.DELETE, 1L, "数学", "张老师", "A101", "周一 08:00-10:00", true, null);

        List<ChangeSummaryResponse> summary = auditLogService.getChangeSummary(10);
        assertEquals(2, summary.size());
        assertTrue(summary.stream().anyMatch(s -> "数学".equals(s.getCourseName())
                && OperationType.CREATE == s.getOperationType()));
        assertTrue(summary.stream().anyMatch(s -> "数学".equals(s.getCourseName())
                && OperationType.DELETE == s.getOperationType()));
    }

    @Test
    void testGetChangeSummary_SummaryFields_OnlyRequiredFieldsPresent() {
        auditLogService.recordLog(
                OperationType.BATCH_UPDATE_TIME_SLOT,
                10L,
                "线性代数",
                "陈教授",
                "教学楼 501",
                "周五 14:00-16:00",
                true,
                null
        );

        List<ChangeSummaryResponse> summary = auditLogService.getChangeSummary(10);
        assertEquals(1, summary.size());
        ChangeSummaryResponse item = summary.get(0);
        assertEquals(OperationType.BATCH_UPDATE_TIME_SLOT, item.getOperationType());
        assertEquals("线性代数", item.getCourseName());
        assertEquals("陈教授", item.getTeacherName());
        assertEquals("教学楼 501", item.getClassroom());
        assertEquals("周五 14:00-16:00", item.getTimeSlot());
        assertNotNull(item.getTimestamp());
    }

    @Test
    void testGetChangeSummary_ZeroLimit_ThrowsException() {
        InvalidRequestParameterException ex = assertThrows(InvalidRequestParameterException.class,
                () -> auditLogService.getChangeSummary(0));
        assertEquals("limit 必须为正整数，当前值: 0", ex.getMessage());
    }

    @Test
    void testGetChangeSummary_NegativeLimit_ThrowsException() {
        InvalidRequestParameterException ex = assertThrows(InvalidRequestParameterException.class,
                () -> auditLogService.getChangeSummary(-1));
        assertEquals("limit 必须为正整数，当前值: -1", ex.getMessage());
    }

    @Test
    void testGetChangeSummary_LimitExceedsMax_ThrowsException() {
        InvalidRequestParameterException ex = assertThrows(InvalidRequestParameterException.class,
                () -> auditLogService.getChangeSummary(51));
        assertEquals("limit 最大值为 50，当前值: 51", ex.getMessage());
    }

    @Test
    void testGetChangeSummary_Limit50_IsAllowed() {
        for (int i = 1; i <= 60; i++) {
            testClock.advanceSeconds(60);
            auditLogService.recordLog(OperationType.CREATE, (long) i, "课程" + i, "老师" + i, "教室" + i, "周一 08:00-10:00", true, null);
        }

        List<ChangeSummaryResponse> summary = auditLogService.getChangeSummary(50);
        assertEquals(50, summary.size());
        assertEquals("课程60", summary.get(0).getCourseName());
    }

    @Test
    void testGetChangeSummary_EmptyLogs_ReturnsEmptyList() {
        List<ChangeSummaryResponse> summary = auditLogService.getChangeSummary(10);
        assertTrue(summary.isEmpty());
    }

    @Test
    void testGetChangeSummary_OnlyFailureLogs_ReturnsEmptyList() {
        auditLogService.recordLog(OperationType.CREATE, 1L, "数学", "张老师", "A101", "周一 08:00-10:00", false, "失败");
        auditLogService.recordLog(OperationType.UPDATE, 2L, "物理", "李老师", "B202", "周二 14:00-16:00", false, "失败");

        List<ChangeSummaryResponse> summary = auditLogService.getChangeSummary(10);
        assertTrue(summary.isEmpty());
    }

    @Test
    void testGetChangeSummary_AllOperationTypes_AllTypesIncluded() {
        auditLogService.recordLog(OperationType.CREATE, 1L, "课程1", "老师1", "教室1", "周一 08:00-10:00", true, null);
        testClock.advanceSeconds(60);
        auditLogService.recordLog(OperationType.UPDATE, 2L, "课程2", "老师2", "教室2", "周二 10:00-12:00", true, null);
        testClock.advanceSeconds(60);
        auditLogService.recordLog(OperationType.DELETE, 3L, "课程3", "老师3", "教室3", "周三 14:00-16:00", true, null);
        testClock.advanceSeconds(60);
        auditLogService.recordLog(OperationType.BATCH_CREATE, 4L, "课程4", "老师4", "教室4", "周四 09:00-11:00", true, null);
        testClock.advanceSeconds(60);
        auditLogService.recordLog(OperationType.BATCH_DELETE, 5L, "课程5", "老师5", "教室5", "周五 15:00-17:00", true, null);
        testClock.advanceSeconds(60);
        auditLogService.recordLog(OperationType.BATCH_UPDATE_TIME_SLOT, 6L, "课程6", "老师6", "教室6", "周六 08:00-10:00", true, null);

        List<ChangeSummaryResponse> summary = auditLogService.getChangeSummary(10);
        assertEquals(6, summary.size());
        assertEquals(OperationType.BATCH_UPDATE_TIME_SLOT, summary.get(0).getOperationType());
        assertEquals("课程6", summary.get(0).getCourseName());
        assertEquals(OperationType.BATCH_DELETE, summary.get(1).getOperationType());
        assertEquals(OperationType.BATCH_CREATE, summary.get(2).getOperationType());
        assertEquals(OperationType.DELETE, summary.get(3).getOperationType());
        assertEquals(OperationType.UPDATE, summary.get(4).getOperationType());
        assertEquals(OperationType.CREATE, summary.get(5).getOperationType());
        assertEquals("课程1", summary.get(5).getCourseName());
    }

    @Test
    void testGetChangeSummary_SameTimestamp_IdUsedAsTiebreaker() {
        auditLogService.recordLog(OperationType.CREATE, 1L, "先记录", "老师A", "教室A", "周一 08:00-10:00", true, null);
        auditLogService.recordLog(OperationType.CREATE, 2L, "后记录", "老师B", "教室B", "周二 10:00-12:00", true, null);

        List<ChangeSummaryResponse> summary = auditLogService.getChangeSummary(10);
        assertEquals(2, summary.size());
        assertEquals("后记录", summary.get(0).getCourseName());
        assertEquals("先记录", summary.get(1).getCourseName());
        assertEquals(summary.get(0).getTimestamp(), summary.get(1).getTimestamp());
    }

    @Test
    void testGetChangeSummary_TimestampOutOfInsertOrder_ReturnsByTimestampNotId() {
        auditLogService.recordLog(OperationType.CREATE, 1L, "最新", "老师A", "教室A", "周一 08:00-10:00", true, null);
        testClock.advanceSeconds(-120);
        auditLogService.recordLog(OperationType.CREATE, 2L, "最早", "老师B", "教室B", "周二 10:00-12:00", true, null);
        testClock.advanceSeconds(60);
        auditLogService.recordLog(OperationType.CREATE, 3L, "中间", "老师C", "教室C", "周三 14:00-16:00", true, null);

        List<ChangeSummaryResponse> summary = auditLogService.getChangeSummary(10);
        assertEquals(3, summary.size());
        assertEquals("最新", summary.get(0).getCourseName());
        assertEquals("中间", summary.get(1).getCourseName());
        assertEquals("最早", summary.get(2).getCourseName());
    }

    @Test
    void testGetChangeSummaryByCourse_EmptyLogs_ReturnsEmptyList() {
        List<CourseChangeSummaryDTO> result = auditLogService.getChangeSummaryByCourse();
        assertTrue(result.isEmpty());
    }

    @Test
    void testGetChangeSummaryByCourse_DelegatesToSupportAndReturnsResult() {
        auditLogService.recordLog(OperationType.CREATE, 1L, "数学", "张老师", "A101", "周一 08:00-10:00", true, null);
        auditLogService.recordLog(OperationType.UPDATE, 1L, " 数学 ", "张老师", "A101", "周一 08:00-10:00", true, null);
        auditLogService.recordLog(OperationType.CREATE, 2L, "物理", "李老师", "B202", "周二 14:00-16:00", false, "老师冲突");
        auditLogService.recordLog(OperationType.CREATE, 3L, "化学", "王老师", "C303", "周三 10:00-12:00", true, null);
        auditLogService.recordLog(OperationType.UNDO, null, "", null, null, null, true, null);

        List<CourseChangeSummaryDTO> result = auditLogService.getChangeSummaryByCourse();
        assertEquals(2, result.size());

        CourseChangeSummaryDTO math = result.stream().filter(r -> "数学".equals(r.getCourseName())).findFirst().orElse(null);
        assertNotNull(math);
        assertEquals(2, math.getChangeCount());

        CourseChangeSummaryDTO chemistry = result.stream().filter(r -> "化学".equals(r.getCourseName())).findFirst().orElse(null);
        assertNotNull(chemistry);
        assertEquals(1, chemistry.getChangeCount());
    }

    @Test
    void testGetChangeSummaryByOperation_EmptyLogs_ReturnsEmptyList() {
        List<OperationSummaryDTO> result = auditLogService.getChangeSummaryByOperation();
        assertTrue(result.isEmpty());
    }

    @Test
    void testGetChangeSummaryByOperation_DelegatesToSupport() {
        auditLogService.recordLog(OperationType.CREATE, 1L, "数学", "张老师", "A101", "周一 08:00-10:00", true, null);
        auditLogService.recordLog(OperationType.CREATE, 2L, "物理", "李老师", "B202", "周二 14:00-16:00", false, "失败");
        auditLogService.recordLog(OperationType.UPDATE, 1L, "高等数学", "张老师", "A101", "周一 08:00-10:00", true, null);

        List<OperationSummaryDTO> result = auditLogService.getChangeSummaryByOperation();
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
        assertEquals(1, updateDto.getSuccessCount());
        assertEquals(0, updateDto.getFailureCount());
        assertEquals(1, updateDto.getTotalCount());
    }

    @Test
    void testGetRecentActiveCourses_EmptyLogs_ReturnsEmptyList() {
        List<RecentActiveCourseDTO> result = auditLogService.getRecentActiveCourses(10);
        assertTrue(result.isEmpty());
    }

    @Test
    void testGetRecentActiveCourses_DefaultLimit10_MoreThan10Courses_ReturnsOnly10() {
        for (int i = 1; i <= 15; i++) {
            testClock.advanceSeconds(60);
            auditLogService.recordLog(OperationType.CREATE, (long) i, "课程" + i, "老师" + i, "教室" + i, "周一 08:00-10:00", true, null);
        }

        List<RecentActiveCourseDTO> result = auditLogService.getRecentActiveCourses(10);
        assertEquals(10, result.size());
        assertEquals("课程15", result.get(0).getCourseName());
        assertEquals("课程14", result.get(1).getCourseName());
        assertEquals("课程6", result.get(9).getCourseName());
    }

    @Test
    void testGetRecentActiveCourses_CustomLimit5_ReturnsTop5() {
        for (int i = 1; i <= 20; i++) {
            testClock.advanceSeconds(60);
            auditLogService.recordLog(OperationType.CREATE, (long) i, "课程" + i, "老师" + i, "教室" + i, "周一 08:00-10:00", true, null);
        }

        List<RecentActiveCourseDTO> result = auditLogService.getRecentActiveCourses(5);
        assertEquals(5, result.size());
        assertEquals("课程20", result.get(0).getCourseName());
        assertEquals("课程19", result.get(1).getCourseName());
        assertEquals("课程18", result.get(2).getCourseName());
        assertEquals("课程17", result.get(3).getCourseName());
        assertEquals("课程16", result.get(4).getCourseName());
    }

    @Test
    void testGetRecentActiveCourses_MixedSuccessFailure_CorrectCounts() {
        auditLogService.recordLog(OperationType.CREATE, 1L, "数学", "张老师", "A101", "周一 08:00-10:00", true, null);
        auditLogService.recordLog(OperationType.UPDATE, 1L, "数学", "张老师", "A101", "周一 08:00-10:00", true, null);
        auditLogService.recordLog(OperationType.DELETE, 1L, "数学", "张老师", "A101", "周一 08:00-10:00", false, "删除失败");

        auditLogService.recordLog(OperationType.CREATE, 2L, "物理", "李老师", "B202", "周二 14:00-16:00", false, "冲突");
        auditLogService.recordLog(OperationType.CREATE, 2L, "物理", "李老师", "B202", "周二 14:00-16:00", true, null);

        List<RecentActiveCourseDTO> result = auditLogService.getRecentActiveCourses(10);
        assertEquals(2, result.size());

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
        assertEquals(1, physics.getFailureCount());
        assertEquals(2, physics.getTotalCount());
    }

    @Test
    void testGetRecentActiveCourses_SortedByLastChangeTimeDescending() {
        auditLogService.recordLog(OperationType.CREATE, 1L, "数学", "张老师", "A101", "周一 08:00-10:00", true, null);
        testClock.advanceSeconds(60);
        auditLogService.recordLog(OperationType.UPDATE, 2L, "物理", "李老师", "B202", "周二 14:00-16:00", true, null);
        testClock.advanceSeconds(60);
        auditLogService.recordLog(OperationType.CREATE, 3L, "化学", "王老师", "C303", "周三 10:00-12:00", true, null);
        testClock.advanceSeconds(30);
        auditLogService.recordLog(OperationType.UPDATE, 1L, "数学", "张老师", "A101", "周一 08:00-10:00", true, null);

        List<RecentActiveCourseDTO> result = auditLogService.getRecentActiveCourses(10);
        assertEquals(3, result.size());
        assertEquals("数学", result.get(0).getCourseName());
        assertEquals("化学", result.get(1).getCourseName());
        assertEquals("物理", result.get(2).getCourseName());
    }

    @Test
    void testGetRecentActiveCourses_SameTime_SortedByTotalCountDescending() {
        auditLogService.recordLog(OperationType.CREATE, 1L, "数学", "张老师", "A101", "周一 08:00-10:00", true, null);
        auditLogService.recordLog(OperationType.CREATE, 1L, "数学", "张老师", "A101", "周一 08:00-10:00", true, null);
        auditLogService.recordLog(OperationType.CREATE, 1L, "数学", "张老师", "A101", "周一 08:00-10:00", true, null);

        auditLogService.recordLog(OperationType.CREATE, 2L, "物理", "李老师", "B202", "周二 14:00-16:00", true, null);
        auditLogService.recordLog(OperationType.CREATE, 2L, "物理", "李老师", "B202", "周二 14:00-16:00", true, null);

        auditLogService.recordLog(OperationType.CREATE, 3L, "化学", "王老师", "C303", "周三 10:00-12:00", true, null);
        auditLogService.recordLog(OperationType.CREATE, 3L, "化学", "王老师", "C303", "周三 10:00-12:00", true, null);
        auditLogService.recordLog(OperationType.CREATE, 3L, "化学", "王老师", "C303", "周三 10:00-12:00", true, null);
        auditLogService.recordLog(OperationType.CREATE, 3L, "化学", "王老师", "C303", "周三 10:00-12:00", true, null);

        List<RecentActiveCourseDTO> result = auditLogService.getRecentActiveCourses(10);
        assertEquals(3, result.size());
        assertEquals("化学", result.get(0).getCourseName());
        assertEquals(4, result.get(0).getTotalCount());
        assertEquals("数学", result.get(1).getCourseName());
        assertEquals(3, result.get(1).getTotalCount());
        assertEquals("物理", result.get(2).getCourseName());
        assertEquals(2, result.get(2).getTotalCount());
    }

    @Test
    void testGetRecentActiveCourses_UnparseableCourseNames_Skipped() {
        auditLogService.recordLog(OperationType.CREATE, 1L, "数学", "张老师", "A101", "周一 08:00-10:00", true, null);
        auditLogService.recordLog(OperationType.UNDO, null, null, null, null, null, true, null);
        auditLogService.recordLog(OperationType.UNDO, null, "", null, null, null, true, null);
        auditLogService.recordLog(OperationType.UNDO, null, "   ", null, null, null, true, null);
        auditLogService.recordLog(OperationType.CREATE, 2L, "物理", "李老师", "B202", "周二 14:00-16:00", true, null);

        List<RecentActiveCourseDTO> result = auditLogService.getRecentActiveCourses(10);
        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(dto -> "数学".equals(dto.getCourseName())));
        assertTrue(result.stream().anyMatch(dto -> "物理".equals(dto.getCourseName())));
    }

    @Test
    void testGetRecentActiveCourses_ZeroLimit_ThrowsException() {
        InvalidRequestParameterException ex = assertThrows(InvalidRequestParameterException.class,
                () -> auditLogService.getRecentActiveCourses(0));
        assertEquals("limit 必须为正整数，当前值: 0", ex.getMessage());
    }

    @Test
    void testGetRecentActiveCourses_NegativeLimit_ThrowsException() {
        InvalidRequestParameterException ex = assertThrows(InvalidRequestParameterException.class,
                () -> auditLogService.getRecentActiveCourses(-1));
        assertEquals("limit 必须为正整数，当前值: -1", ex.getMessage());
    }

    @Test
    void testGetRecentActiveCourses_LimitExceedsMax_ThrowsException() {
        InvalidRequestParameterException ex = assertThrows(InvalidRequestParameterException.class,
                () -> auditLogService.getRecentActiveCourses(51));
        assertEquals("limit 最大值为 50，当前值: 51", ex.getMessage());
    }

    @Test
    void testGetRecentActiveCourses_Limit50_IsAllowed() {
        for (int i = 1; i <= 60; i++) {
            testClock.advanceSeconds(60);
            auditLogService.recordLog(OperationType.CREATE, (long) i, "课程" + i, "老师" + i, "教室" + i, "周一 08:00-10:00", true, null);
        }

        List<RecentActiveCourseDTO> result = auditLogService.getRecentActiveCourses(50);
        assertEquals(50, result.size());
        assertEquals("课程60", result.get(0).getCourseName());
    }

    @Test
    void testGetRecentActiveCourses_AllOperationTypes_Included() {
        auditLogService.recordLog(OperationType.CREATE, 1L, "课程1", "老师1", "教室1", "周一 08:00-10:00", true, null);
        testClock.advanceSeconds(10);
        auditLogService.recordLog(OperationType.UPDATE, 1L, "课程1", "老师1", "教室1", "周一 08:00-10:00", true, null);
        testClock.advanceSeconds(10);
        auditLogService.recordLog(OperationType.DELETE, 1L, "课程1", "老师1", "教室1", "周一 08:00-10:00", true, null);
        testClock.advanceSeconds(10);
        auditLogService.recordLog(OperationType.BATCH_CREATE, 2L, "课程2", "老师2", "教室2", "周四 09:00-11:00", true, null);
        testClock.advanceSeconds(10);
        auditLogService.recordLog(OperationType.BATCH_DELETE, 2L, "课程2", "老师2", "教室2", "周五 15:00-17:00", false, "错误");
        testClock.advanceSeconds(10);
        auditLogService.recordLog(OperationType.BATCH_UPDATE_TIME_SLOT, 3L, "课程3", "老师3", "教室3", "周六 08:00-10:00", true, null);

        List<RecentActiveCourseDTO> result = auditLogService.getRecentActiveCourses(10);
        assertEquals(3, result.size());

        RecentActiveCourseDTO c1 = result.stream()
                .filter(dto -> "课程1".equals(dto.getCourseName()))
                .findFirst().orElse(null);
        assertNotNull(c1);
        assertEquals(3, c1.getTotalCount());
        assertEquals(3, c1.getSuccessCount());
        assertEquals(0, c1.getFailureCount());

        RecentActiveCourseDTO c2 = result.stream()
                .filter(dto -> "课程2".equals(dto.getCourseName()))
                .findFirst().orElse(null);
        assertNotNull(c2);
        assertEquals(2, c2.getTotalCount());
        assertEquals(1, c2.getSuccessCount());
        assertEquals(1, c2.getFailureCount());

        RecentActiveCourseDTO c3 = result.stream()
                .filter(dto -> "课程3".equals(dto.getCourseName()))
                .findFirst().orElse(null);
        assertNotNull(c3);
        assertEquals(1, c3.getTotalCount());
    }

    @Test
    void testGetCourseChangeTrend_EmptyLogs_ReturnsEmptyList() {
        List<CourseChangeTrendDTO> result = auditLogService.getCourseChangeTrend(7);
        assertTrue(result.isEmpty());
    }

    @Test
    void testGetCourseChangeTrend_DefaultDays7_MoreThan7Days_ReturnsOnlyLast7() {
        for (int i = 0; i < 10; i++) {
            testClock.advanceSeconds(60 * 60 * 24);
            auditLogService.recordLog(
                    OperationType.CREATE, (long) i, "课程" + i, "老师" + i, "教室" + i, "周一 08:00-10:00",
                    true, null
            );
        }

        List<CourseChangeTrendDTO> result = auditLogService.getCourseChangeTrend(7);
        assertEquals(7, result.size());
    }

    @Test
    void testGetCourseChangeTrend_CustomDays3_ReturnsOnlyLast3() {
        for (int i = 0; i < 5; i++) {
            testClock.advanceSeconds(60 * 60 * 24);
            auditLogService.recordLog(
                    OperationType.CREATE, (long) i, "课程" + i, "老师" + i, "教室" + i, "周一 08:00-10:00",
                    true, null
            );
        }

        List<CourseChangeTrendDTO> result = auditLogService.getCourseChangeTrend(3);
        assertEquals(3, result.size());
    }

    @Test
    void testGetCourseChangeTrend_MixedSuccessFailure_CorrectCounts() {
        auditLogService.recordLog(
                OperationType.CREATE, 1L, "数学", "张老师", "A101", "周一 08:00-10:00",
                true, null
        );
        auditLogService.recordLog(
                OperationType.CREATE, 2L, "物理", "李老师", "B202", "周二 14:00-16:00",
                true, null
        );
        auditLogService.recordLog(
                OperationType.UPDATE, 1L, "数学", "张老师", "A101", "周一 08:00-10:00",
                false, "冲突"
        );

        List<CourseChangeTrendDTO> result = auditLogService.getCourseChangeTrend(7);
        assertEquals(1, result.size());
        CourseChangeTrendDTO dto = result.get(0);
        assertEquals(2, dto.getSuccessCount());
        assertEquals(1, dto.getFailureCount());
        assertEquals(3, dto.getTotalCount());
    }

    @Test
    void testGetCourseChangeTrend_SortedByDateAscending() {
        for (int i = 0; i < 3; i++) {
            testClock.advanceSeconds(60 * 60 * 24);
            auditLogService.recordLog(
                    OperationType.CREATE, (long) i, "课程" + i, "老师" + i, "教室" + i, "周一 08:00-10:00",
                    true, null
            );
        }

        List<CourseChangeTrendDTO> result = auditLogService.getCourseChangeTrend(7);
        assertEquals(3, result.size());
        assertTrue(result.get(0).getDate().isBefore(result.get(1).getDate()));
        assertTrue(result.get(1).getDate().isBefore(result.get(2).getDate()));
    }

    @Test
    void testGetCourseChangeTrend_UnparseableCourseNames_Skipped() {
        auditLogService.recordLog(
                OperationType.CREATE, 1L, "数学", "张老师", "A101", "周一 08:00-10:00",
                true, null
        );
        auditLogService.recordLog(
                OperationType.UNDO, null, null, null, null, null,
                true, null
        );
        auditLogService.recordLog(
                OperationType.UNDO, null, "", null, null, null,
                true, null
        );
        auditLogService.recordLog(
                OperationType.UNDO, null, "   ", null, null, null,
                true, null
        );
        auditLogService.recordLog(
                OperationType.CREATE, 2L, "物理", "李老师", "B202", "周二 14:00-16:00",
                true, null
        );

        List<CourseChangeTrendDTO> result = auditLogService.getCourseChangeTrend(7);
        assertEquals(1, result.size());
        assertEquals(2, result.get(0).getTotalCount());
    }

    @Test
    void testGetCourseChangeTrend_ZeroDays_ThrowsException() {
        InvalidRequestParameterException ex = assertThrows(InvalidRequestParameterException.class,
                () -> auditLogService.getCourseChangeTrend(0));
        assertEquals("days 必须为正整数，当前值: 0", ex.getMessage());
    }

    @Test
    void testGetCourseChangeTrend_NegativeDays_ThrowsException() {
        InvalidRequestParameterException ex = assertThrows(InvalidRequestParameterException.class,
                () -> auditLogService.getCourseChangeTrend(-1));
        assertEquals("days 必须为正整数，当前值: -1", ex.getMessage());
    }

    @Test
    void testGetCourseChangeTrend_DaysExceedsMax_ThrowsException() {
        InvalidRequestParameterException ex = assertThrows(InvalidRequestParameterException.class,
                () -> auditLogService.getCourseChangeTrend(366));
        assertEquals("days 最大值为 365，当前值: 366", ex.getMessage());
    }

    @Test
    void testGetCourseChangeTrend_Days365_IsAllowed() {
        List<CourseChangeTrendDTO> result = auditLogService.getCourseChangeTrend(365);
        assertTrue(result.isEmpty());
    }

    @Test
    void testGetCourseChangeTrend_AllOperationTypes_Included() {
        auditLogService.recordLog(OperationType.CREATE, 1L, "课程1", "老师1", "教室1", "周一 08:00-10:00", true, null);
        testClock.advanceSeconds(10);
        auditLogService.recordLog(OperationType.UPDATE, 1L, "课程1", "老师1", "教室1", "周一 08:00-10:00", true, null);
        testClock.advanceSeconds(10);
        auditLogService.recordLog(OperationType.DELETE, 1L, "课程1", "老师1", "教室1", "周一 08:00-10:00", true, null);
        testClock.advanceSeconds(10);
        auditLogService.recordLog(OperationType.BATCH_CREATE, 2L, "课程2", "老师2", "教室2", "周四 09:00-11:00", true, null);
        testClock.advanceSeconds(10);
        auditLogService.recordLog(OperationType.BATCH_DELETE, 2L, "课程2", "老师2", "教室2", "周五 15:00-17:00", false, "错误");

        List<CourseChangeTrendDTO> result = auditLogService.getCourseChangeTrend(7);
        assertEquals(1, result.size());
        assertEquals(4, result.get(0).getSuccessCount());
        assertEquals(1, result.get(0).getFailureCount());
        assertEquals(5, result.get(0).getTotalCount());
    }

    @Test
    void testGetCourseChangeAbnormalDay_EmptyLogs_ReturnsEmptyList() {
        List<CourseChangeAbnormalDaysDTO> result = auditLogService.getCourseChangeAbnormalDays(7);
        assertTrue(result.isEmpty());
    }

    @Test
    void testGetCourseChangeAbnormalDay_DefaultDays7_NoFailures_ReturnsEmptyList() {
        auditLogService.recordLog(OperationType.CREATE, 1L, "数学", "张老师", "A101", "周一 08:00-10:00", true, null);
        auditLogService.recordLog(OperationType.UPDATE, 2L, "物理", "李老师", "B202", "周二 14:00-16:00", true, null);

        List<CourseChangeAbnormalDaysDTO> result = auditLogService.getCourseChangeAbnormalDays(7);
        assertTrue(result.isEmpty());
    }

    @Test
    void testGetCourseChangeAbnormalDay_MixedSuccessFailure_CorrectCounts() {
        auditLogService.recordLog(OperationType.CREATE, 1L, "数学", "张老师", "A101", "周一 08:00-10:00", true, null);
        auditLogService.recordLog(OperationType.CREATE, 2L, "物理", "李老师", "B202", "周二 14:00-16:00", true, null);
        auditLogService.recordLog(OperationType.UPDATE, 1L, "数学", "张老师", "A101", "周一 08:00-10:00", false, "冲突");

        List<CourseChangeAbnormalDaysDTO> result = auditLogService.getCourseChangeAbnormalDays(7);
        assertEquals(1, result.size());
        CourseChangeAbnormalDaysDTO dto = result.get(0);
        assertEquals(2, dto.getSuccessCount());
        assertEquals(1, dto.getFailureCount());
        assertEquals(3, dto.getTotalCount());
        assertEquals(1.0 / 3, dto.getFailureRate(), 0.0001);
    }

    @Test
    void testGetCourseChangeAbnormalDay_CustomDays3_ReturnsOnlyLast3() {
        for (int i = 0; i < 5; i++) {
            testClock.advanceSeconds(60 * 60 * 24);
            auditLogService.recordLog(
                    OperationType.CREATE, (long) i, "课程" + i, "老师" + i, "教室" + i, "周一 08:00-10:00",
                    false, "错误"
            );
        }

        List<CourseChangeAbnormalDaysDTO> result = auditLogService.getCourseChangeAbnormalDays(3);
        assertEquals(3, result.size());
    }

    @Test
    void testGetCourseChangeAbnormalDay_SortedByFailureRateDescending() {
        auditLogService.recordLog(OperationType.CREATE, 1L, "数学", "张老师", "A101", "周一 08:00-10:00", true, null);
        auditLogService.recordLog(OperationType.CREATE, 1L, "数学", "张老师", "A101", "周一 08:00-10:00", false, "冲突");
        testClock.advanceSeconds(60 * 60 * 24);
        auditLogService.recordLog(OperationType.CREATE, 2L, "物理", "李老师", "B202", "周二 14:00-16:00", true, null);
        auditLogService.recordLog(OperationType.CREATE, 2L, "物理", "李老师", "B202", "周二 14:00-16:00", true, null);
        auditLogService.recordLog(OperationType.CREATE, 2L, "物理", "李老师", "B202", "周二 14:00-16:00", false, "冲突");

        List<CourseChangeAbnormalDaysDTO> result = auditLogService.getCourseChangeAbnormalDays(7);
        assertEquals(2, result.size());
        assertTrue(result.get(0).getFailureRate() >= result.get(1).getFailureRate());
    }

    @Test
    void testGetCourseChangeAbnormalDay_UnparseableCourseNames_Skipped() {
        auditLogService.recordLog(OperationType.CREATE, 1L, "数学", "张老师", "A101", "周一 08:00-10:00", false, "冲突");
        auditLogService.recordLog(OperationType.UNDO, null, null, null, null, null, false, "错误");
        auditLogService.recordLog(OperationType.UNDO, null, "", null, null, null, false, "错误");
        auditLogService.recordLog(OperationType.UNDO, null, "   ", null, null, null, false, "错误");

        List<CourseChangeAbnormalDaysDTO> result = auditLogService.getCourseChangeAbnormalDays(7);
        assertEquals(1, result.size());
        assertEquals(1, result.get(0).getFailureCount());
    }

    @Test
    void testGetCourseChangeAbnormalDay_ZeroDays_ThrowsException() {
        InvalidRequestParameterException ex = assertThrows(InvalidRequestParameterException.class,
                () -> auditLogService.getCourseChangeAbnormalDays(0));
        assertEquals("days 必须为正整数，当前值: 0", ex.getMessage());
    }

    @Test
    void testGetCourseChangeAbnormalDay_NegativeDays_ThrowsException() {
        InvalidRequestParameterException ex = assertThrows(InvalidRequestParameterException.class,
                () -> auditLogService.getCourseChangeAbnormalDays(-1));
        assertEquals("days 必须为正整数，当前值: -1", ex.getMessage());
    }

    @Test
    void testGetCourseChangeAbnormalDay_DaysExceedsMax_ThrowsException() {
        InvalidRequestParameterException ex = assertThrows(InvalidRequestParameterException.class,
                () -> auditLogService.getCourseChangeAbnormalDays(366));
        assertEquals("days 最大值为 365，当前值: 366", ex.getMessage());
    }

    @Test
    void testGetCourseChangeAbnormalDay_Days365_IsAllowed() {
        List<CourseChangeAbnormalDaysDTO> result = auditLogService.getCourseChangeAbnormalDays(365);
        assertTrue(result.isEmpty());
    }

    @Test
    void testGetFailureReasonSummary_EmptyLogs_ReturnsEmptyList() {
        List<FailureReasonSummaryDTO> result = auditLogService.getFailureReasonSummary(10);
        assertTrue(result.isEmpty());
    }

    @Test
    void testGetFailureReasonSummary_DefaultLimit10_MoreThan10Reasons_ReturnsOnly10() {
        for (int i = 1; i <= 15; i++) {
            testClock.advanceSeconds(60);
            auditLogService.recordLog(
                    OperationType.CREATE, (long) i, "课程" + i, "老师" + i, "教室" + i, "周一 08:00-10:00",
                    false, "错误原因" + i
            );
        }

        List<FailureReasonSummaryDTO> result = auditLogService.getFailureReasonSummary(10);
        assertEquals(10, result.size());
    }

    @Test
    void testGetFailureReasonSummary_CustomLimit5_ReturnsTop5() {
        for (int i = 1; i <= 20; i++) {
            testClock.advanceSeconds(60);
            auditLogService.recordLog(
                    OperationType.CREATE, (long) i, "课程" + i, "老师" + i, "教室" + i, "周一 08:00-10:00",
                    false, "错误原因" + i
            );
        }

        List<FailureReasonSummaryDTO> result = auditLogService.getFailureReasonSummary(5);
        assertEquals(5, result.size());
    }

    @Test
    void testGetFailureReasonSummary_SortedByCountDescending() {
        auditLogService.recordLog(OperationType.CREATE, 1L, "数学", "张老师", "A101", "周一 08:00-10:00", false, "老师冲突");
        auditLogService.recordLog(OperationType.UPDATE, 2L, "物理", "李老师", "B202", "周二 14:00-16:00", false, "教室冲突");
        auditLogService.recordLog(OperationType.DELETE, 3L, "化学", "王老师", "C303", "周三 10:00-12:00", false, "教室冲突");
        auditLogService.recordLog(OperationType.CREATE, 4L, "生物", "赵老师", "D404", "周四 09:00-11:00", false, "时间冲突");
        auditLogService.recordLog(OperationType.UPDATE, 5L, "历史", "刘老师", "E505", "周五 14:00-16:00", false, "时间冲突");
        auditLogService.recordLog(OperationType.DELETE, 6L, "地理", "孙老师", "F606", "周六 08:00-10:00", false, "时间冲突");

        List<FailureReasonSummaryDTO> result = auditLogService.getFailureReasonSummary(10);

        assertEquals(3, result.size());
        assertEquals("时间冲突", result.get(0).getFailureReason());
        assertEquals(3, result.get(0).getCount());
        assertEquals("教室冲突", result.get(1).getFailureReason());
        assertEquals(2, result.get(1).getCount());
        assertEquals("老师冲突", result.get(2).getFailureReason());
        assertEquals(1, result.get(2).getCount());
    }

    @Test
    void testGetFailureReasonSummary_SameCount_SortedByLastOccurrenceTimeDescending() {
        auditLogService.recordLog(OperationType.CREATE, 1L, "数学", "张老师", "A101", "周一 08:00-10:00", false, "老师冲突");
        testClock.advanceSeconds(1800);
        auditLogService.recordLog(OperationType.UPDATE, 2L, "物理", "李老师", "B202", "周二 14:00-16:00", false, "教室冲突");
        testClock.advanceSeconds(1800);
        auditLogService.recordLog(OperationType.DELETE, 3L, "化学", "王老师", "C303", "周三 10:00-12:00", false, "老师冲突");
        testClock.advanceSeconds(1800);
        auditLogService.recordLog(OperationType.CREATE, 4L, "生物", "赵老师", "D404", "周四 09:00-11:00", false, "教室冲突");

        List<FailureReasonSummaryDTO> result = auditLogService.getFailureReasonSummary(10);

        assertEquals(2, result.size());
        assertEquals("教室冲突", result.get(0).getFailureReason());
        assertEquals("老师冲突", result.get(1).getFailureReason());
    }

    @Test
    void testGetFailureReasonSummary_SameCountAndTime_SortedByFailureReasonAscending() {
        auditLogService.recordLog(OperationType.CREATE, 1L, "数学", "张老师", "A101", "周一 08:00-10:00", false, "时间冲突");
        auditLogService.recordLog(OperationType.UPDATE, 2L, "物理", "李老师", "B202", "周二 14:00-16:00", false, "教室冲突");
        auditLogService.recordLog(OperationType.DELETE, 3L, "化学", "王老师", "C303", "周三 10:00-12:00", false, "老师冲突");

        List<FailureReasonSummaryDTO> result = auditLogService.getFailureReasonSummary(10);

        assertEquals(3, result.size());
        assertEquals("教室冲突", result.get(0).getFailureReason());
        assertEquals("时间冲突", result.get(1).getFailureReason());
        assertEquals("老师冲突", result.get(2).getFailureReason());
    }

    @Test
    void testGetFailureReasonSummary_OnlySuccessLogs_ReturnsEmpty() {
        auditLogService.recordLog(OperationType.CREATE, 1L, "数学", "张老师", "A101", "周一 08:00-10:00", true, null);
        auditLogService.recordLog(OperationType.UPDATE, 2L, "物理", "李老师", "B202", "周二 14:00-16:00", true, null);

        List<FailureReasonSummaryDTO> result = auditLogService.getFailureReasonSummary(10);
        assertTrue(result.isEmpty());
    }

    @Test
    void testGetFailureReasonSummary_NullErrorMessage_Skipped() {
        auditLogService.recordLog(OperationType.CREATE, 1L, "数学", "张老师", "A101", "周一 08:00-10:00", false, "老师冲突");
        auditLogService.recordLog(OperationType.UPDATE, 2L, "物理", "李老师", "B202", "周二 14:00-16:00", false, null);
        auditLogService.recordLog(OperationType.DELETE, 3L, "化学", "王老师", "C303", "周三 10:00-12:00", false, "教室冲突");

        List<FailureReasonSummaryDTO> result = auditLogService.getFailureReasonSummary(10);

        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(dto -> "老师冲突".equals(dto.getFailureReason())));
        assertTrue(result.stream().anyMatch(dto -> "教室冲突".equals(dto.getFailureReason())));
    }

    @Test
    void testGetFailureReasonSummary_EmptyErrorMessage_Skipped() {
        auditLogService.recordLog(OperationType.CREATE, 1L, "数学", "张老师", "A101", "周一 08:00-10:00", false, "老师冲突");
        auditLogService.recordLog(OperationType.UPDATE, 2L, "物理", "李老师", "B202", "周二 14:00-16:00", false, "");
        auditLogService.recordLog(OperationType.DELETE, 3L, "化学", "王老师", "C303", "周三 10:00-12:00", false, "教室冲突");

        List<FailureReasonSummaryDTO> result = auditLogService.getFailureReasonSummary(10);

        assertEquals(2, result.size());
    }

    @Test
    void testGetFailureReasonSummary_WhitespaceOnlyErrorMessage_Skipped() {
        auditLogService.recordLog(OperationType.CREATE, 1L, "数学", "张老师", "A101", "周一 08:00-10:00", false, "老师冲突");
        auditLogService.recordLog(OperationType.UPDATE, 2L, "物理", "李老师", "B202", "周二 14:00-16:00", false, "   ");
        auditLogService.recordLog(OperationType.DELETE, 3L, "化学", "王老师", "C303", "周三 10:00-12:00", false, "  \t  ");
        auditLogService.recordLog(OperationType.CREATE, 4L, "生物", "赵老师", "D404", "周四 09:00-11:00", false, "教室冲突");

        List<FailureReasonSummaryDTO> result = auditLogService.getFailureReasonSummary(10);

        assertEquals(2, result.size());
    }

    @Test
    void testGetFailureReasonSummary_ZeroLimit_ThrowsException() {
        InvalidRequestParameterException ex = assertThrows(InvalidRequestParameterException.class,
                () -> auditLogService.getFailureReasonSummary(0));
        assertEquals("limit 必须为正整数，当前值: 0", ex.getMessage());
    }

    @Test
    void testGetFailureReasonSummary_NegativeLimit_ThrowsException() {
        InvalidRequestParameterException ex = assertThrows(InvalidRequestParameterException.class,
                () -> auditLogService.getFailureReasonSummary(-1));
        assertEquals("limit 必须为正整数，当前值: -1", ex.getMessage());
    }

    @Test
    void testGetFailureReasonSummary_LimitExceedsMax_ThrowsException() {
        InvalidRequestParameterException ex = assertThrows(InvalidRequestParameterException.class,
                () -> auditLogService.getFailureReasonSummary(51));
        assertEquals("limit 最大值为 50，当前值: 51", ex.getMessage());
    }

    @Test
    void testGetFailureReasonSummary_Limit50_IsAllowed() {
        for (int i = 1; i <= 60; i++) {
            testClock.advanceSeconds(60);
            auditLogService.recordLog(
                    OperationType.CREATE, (long) i, "课程" + i, "老师" + i, "教室" + i, "周一 08:00-10:00",
                    false, "错误原因" + i
            );
        }

        List<FailureReasonSummaryDTO> result = auditLogService.getFailureReasonSummary(50);
        assertEquals(50, result.size());
    }

    @Test
    void testGetFailureReasonSummary_MixedSuccessFailure_CorrectCounts() {
        auditLogService.recordLog(OperationType.CREATE, 1L, "数学", "张老师", "A101", "周一 08:00-10:00", true, null);
        auditLogService.recordLog(OperationType.CREATE, 2L, "物理", "李老师", "B202", "周二 14:00-16:00", false, "老师冲突");
        auditLogService.recordLog(OperationType.UPDATE, 1L, "数学", "张老师", "A101", "周一 08:00-10:00", false, "老师冲突");
        auditLogService.recordLog(OperationType.DELETE, 2L, "物理", "李老师", "B202", "周二 14:00-16:00", true, null);
        auditLogService.recordLog(OperationType.CREATE, 3L, "化学", "王老师", "C303", "周三 10:00-12:00", false, "教室冲突");

        List<FailureReasonSummaryDTO> result = auditLogService.getFailureReasonSummary(10);

        assertEquals(2, result.size());
        FailureReasonSummaryDTO teacherConflict = result.stream()
                .filter(dto -> "老师冲突".equals(dto.getFailureReason()))
                .findFirst()
                .orElse(null);
        assertNotNull(teacherConflict);
        assertEquals(2, teacherConflict.getCount());

        FailureReasonSummaryDTO classroomConflict = result.stream()
                .filter(dto -> "教室冲突".equals(dto.getFailureReason()))
                .findFirst()
                .orElse(null);
        assertNotNull(classroomConflict);
        assertEquals(1, classroomConflict.getCount());
    }

    @Test
    void testGetFailureReasonSummary_WhitespaceErrorMessage_MergedAfterTrim() {
        auditLogService.recordLog(OperationType.CREATE, 1L, "数学", "张老师", "A101", "周一 08:00-10:00", false, "老师冲突");
        auditLogService.recordLog(OperationType.UPDATE, 2L, "物理", "李老师", "B202", "周二 14:00-16:00", false, " 老师冲突 ");
        auditLogService.recordLog(OperationType.DELETE, 3L, "化学", "王老师", "C303", "周三 10:00-12:00", false, "  老师冲突  ");
        auditLogService.recordLog(OperationType.CREATE, 4L, "生物", "赵老师", "D404", "周四 09:00-11:00", false, "\t老师冲突\t");
        auditLogService.recordLog(OperationType.UPDATE, 5L, "历史", "刘老师", "E505", "周五 14:00-16:00", false, "教室冲突");

        List<FailureReasonSummaryDTO> result = auditLogService.getFailureReasonSummary(10);

        assertEquals(2, result.size());
        FailureReasonSummaryDTO teacherConflict = result.stream()
                .filter(dto -> "老师冲突".equals(dto.getFailureReason()))
                .findFirst()
                .orElse(null);
        assertNotNull(teacherConflict);
        assertEquals(4, teacherConflict.getCount());
    }

    @Test
    void testGetOperationFailureSummary_EmptyLogs_ReturnsEmptyList() {
        List<OperationFailureSummaryDTO> result = auditLogService.getOperationFailureSummary(10);
        assertTrue(result.isEmpty());
    }

    @Test
    void testGetOperationFailureSummary_DefaultLimit10_MoreThan10Types_ReturnsOnly10() {
        OperationType[] types = OperationType.values();
        for (int i = 0; i < types.length; i++) {
            testClock.advanceSeconds(60);
            auditLogService.recordLog(
                    types[i], (long) i, "课程" + i, "老师" + i, "教室" + i, "周一 08:00-10:00",
                    false, "错误" + i
            );
        }

        List<OperationFailureSummaryDTO> result = auditLogService.getOperationFailureSummary(10);
        assertEquals(Math.min(10, types.length), result.size());
    }

    @Test
    void testGetOperationFailureSummary_CustomLimit5_ReturnsTop5() {
        OperationType[] types = OperationType.values();
        for (int i = 0; i < types.length; i++) {
            testClock.advanceSeconds(60);
            auditLogService.recordLog(
                    types[i], (long) i, "课程" + i, "老师" + i, "教室" + i, "周一 08:00-10:00",
                    false, "错误" + i
            );
        }

        List<OperationFailureSummaryDTO> result = auditLogService.getOperationFailureSummary(5);
        assertEquals(5, result.size());
    }

    @Test
    void testGetOperationFailureSummary_SortedByFailureCountDescending() {
        for (int i = 0; i < 5; i++) {
            testClock.advanceSeconds(10);
            auditLogService.recordLog(OperationType.CREATE, 1L, "数学", "张老师", "A101", "周一 08:00-10:00", false, "错误");
        }
        for (int i = 0; i < 3; i++) {
            testClock.advanceSeconds(10);
            auditLogService.recordLog(OperationType.UPDATE, 2L, "物理", "李老师", "B202", "周二 14:00-16:00", false, "错误");
        }
        for (int i = 0; i < 1; i++) {
            testClock.advanceSeconds(10);
            auditLogService.recordLog(OperationType.DELETE, 3L, "化学", "王老师", "C303", "周三 10:00-12:00", false, "错误");
        }

        List<OperationFailureSummaryDTO> result = auditLogService.getOperationFailureSummary(10);

        assertEquals(3, result.size());
        assertEquals(OperationType.CREATE, result.get(0).getOperationType());
        assertEquals(5, result.get(0).getFailureCount());
        assertEquals(OperationType.UPDATE, result.get(1).getOperationType());
        assertEquals(3, result.get(1).getFailureCount());
        assertEquals(OperationType.DELETE, result.get(2).getOperationType());
        assertEquals(1, result.get(2).getFailureCount());
    }

    @Test
    void testGetOperationFailureSummary_SameCount_SortedByLastFailureTimeDescending() {
        auditLogService.recordLog(OperationType.CREATE, 1L, "数学", "张老师", "A101", "周一 08:00-10:00", false, "错误");
        testClock.advanceSeconds(1800);
        auditLogService.recordLog(OperationType.UPDATE, 2L, "物理", "李老师", "B202", "周二 14:00-16:00", false, "错误");
        testClock.advanceSeconds(1800);
        auditLogService.recordLog(OperationType.CREATE, 1L, "数学", "张老师", "A101", "周一 08:00-10:00", false, "错误");
        testClock.advanceSeconds(1800);
        auditLogService.recordLog(OperationType.UPDATE, 2L, "物理", "李老师", "B202", "周二 14:00-16:00", false, "错误");

        List<OperationFailureSummaryDTO> result = auditLogService.getOperationFailureSummary(10);

        assertEquals(2, result.size());
        assertEquals(OperationType.UPDATE, result.get(0).getOperationType());
        assertEquals(OperationType.CREATE, result.get(1).getOperationType());
    }

    @Test
    void testGetOperationFailureSummary_SameCountAndTime_SortedByOperationTypeNameAscending() {
        auditLogService.recordLog(OperationType.UPDATE, 1L, "数学", "张老师", "A101", "周一 08:00-10:00", false, "错误");
        auditLogService.recordLog(OperationType.DELETE, 2L, "物理", "李老师", "B202", "周二 14:00-16:00", false, "错误");
        auditLogService.recordLog(OperationType.CREATE, 3L, "化学", "王老师", "C303", "周三 10:00-12:00", false, "错误");

        List<OperationFailureSummaryDTO> result = auditLogService.getOperationFailureSummary(10);

        assertEquals(3, result.size());
        assertEquals(OperationType.CREATE, result.get(0).getOperationType());
        assertEquals(OperationType.DELETE, result.get(1).getOperationType());
        assertEquals(OperationType.UPDATE, result.get(2).getOperationType());
    }

    @Test
    void testGetOperationFailureSummary_OnlySuccessLogs_ReturnsEmpty() {
        auditLogService.recordLog(OperationType.CREATE, 1L, "数学", "张老师", "A101", "周一 08:00-10:00", true, null);
        auditLogService.recordLog(OperationType.UPDATE, 2L, "物理", "李老师", "B202", "周二 14:00-16:00", true, null);

        List<OperationFailureSummaryDTO> result = auditLogService.getOperationFailureSummary(10);
        assertTrue(result.isEmpty());
    }

    @Test
    void testGetOperationFailureSummary_ZeroLimit_ThrowsException() {
        InvalidRequestParameterException ex = assertThrows(InvalidRequestParameterException.class,
                () -> auditLogService.getOperationFailureSummary(0));
        assertEquals("limit 必须为正整数，当前值: 0", ex.getMessage());
    }

    @Test
    void testGetOperationFailureSummary_NegativeLimit_ThrowsException() {
        InvalidRequestParameterException ex = assertThrows(InvalidRequestParameterException.class,
                () -> auditLogService.getOperationFailureSummary(-1));
        assertEquals("limit 必须为正整数，当前值: -1", ex.getMessage());
    }

    @Test
    void testGetOperationFailureSummary_LimitExceedsMax_ThrowsException() {
        InvalidRequestParameterException ex = assertThrows(InvalidRequestParameterException.class,
                () -> auditLogService.getOperationFailureSummary(51));
        assertEquals("limit 最大值为 50，当前值: 51", ex.getMessage());
    }

    @Test
    void testGetOperationFailureSummary_Limit50_IsAllowed() {
        OperationType[] types = OperationType.values();
        for (int i = 0; i < types.length; i++) {
            for (int j = 0; j < 10; j++) {
                testClock.advanceSeconds(10);
                auditLogService.recordLog(
                        types[i], (long) (i * 10 + j), "课程" + i + j, "老师" + i, "教室" + i, "周一 08:00-10:00",
                        false, "错误"
                );
            }
        }

        List<OperationFailureSummaryDTO> result = auditLogService.getOperationFailureSummary(50);
        assertEquals(types.length, result.size());
    }

    @Test
    void testGetOperationFailureSummary_DelegatesToSupport_CorrectResult() {
        auditLogService.recordLog(OperationType.CREATE, 1L, "数学", "张老师", "A101", "周一 08:00-10:00", true, null);
        auditLogService.recordLog(OperationType.CREATE, 2L, "物理", "李老师", "B202", "周二 14:00-16:00", false, "失败1");
        auditLogService.recordLog(OperationType.UPDATE, 1L, "数学", "张老师", "A101", "周一 08:00-10:00", false, "失败2");
        auditLogService.recordLog(OperationType.CREATE, 3L, "化学", "王老师", "C303", "周三 10:00-12:00", false, "失败3");

        List<OperationFailureSummaryDTO> result = auditLogService.getOperationFailureSummary(10);
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
        assertEquals(1, updateDto.getFailureCount());
    }
}
