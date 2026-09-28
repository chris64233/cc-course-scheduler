package com.coursescheduler.service;

import com.coursescheduler.dto.AuditLogResponse;
import com.coursescheduler.dto.ClassroomCourseStatisticsResponse;
import com.coursescheduler.dto.CourseScheduleBatchDeleteFailure;
import com.coursescheduler.dto.CourseScheduleBatchDeleteResponse;
import com.coursescheduler.dto.CourseScheduleBatchFailure;
import com.coursescheduler.dto.CourseScheduleBatchResponse;
import com.coursescheduler.dto.CourseScheduleBatchTimeSlotUpdateRequest;
import com.coursescheduler.dto.CourseScheduleBatchTimeSlotUpdateResponse;
import com.coursescheduler.dto.CourseScheduleCreateRequest;
import com.coursescheduler.dto.CourseScheduleFilterRequest;
import com.coursescheduler.dto.CourseScheduleDeleteResponse;
import com.coursescheduler.dto.CourseScheduleResponse;
import com.coursescheduler.dto.CourseScheduleStatisticsResponse;
import com.coursescheduler.dto.CourseScheduleWeekdayStatisticsResponse;
import com.coursescheduler.dto.CourseScheduleUpdateRequest;
import com.coursescheduler.dto.TeacherCourseStatisticsResponse;
import com.coursescheduler.dto.TeacherFreeDaySummaryResponse;
import com.coursescheduler.dto.ConflictDetailDTO;
import com.coursescheduler.dto.ConflictTargetDetailDTO;
import com.coursescheduler.dto.ConflictRiskPreviewRequest;
import com.coursescheduler.dto.ConflictRiskPreviewResponse;
import com.coursescheduler.dto.CourseScheduleConflictPreCheckRequest;
import com.coursescheduler.dto.CourseScheduleConflictPreCheckResponse;
import com.coursescheduler.dto.BatchPreCheckItemResponse;
import com.coursescheduler.dto.BatchPreCheckResponse;
import com.coursescheduler.dto.ClassroomBatchPreCheckItemRequest;
import com.coursescheduler.dto.ClassroomBatchPreCheckRequest;
import com.coursescheduler.dto.TeacherBatchPreCheckItemRequest;
import com.coursescheduler.dto.TeacherBatchPreCheckRequest;
import com.coursescheduler.dto.PreCheckSummaryResponse;
import com.coursescheduler.dto.TeacherFreeTimeRequest;
import com.coursescheduler.dto.TeacherFreeTimeResponse;
import com.coursescheduler.dto.ConflictSeveritySummaryDTO;
import com.coursescheduler.dto.PendingConflictPairDTO;
import com.coursescheduler.dto.PendingConflictPairItemDTO;
import com.coursescheduler.exception.ClassroomConflictException;
import com.coursescheduler.exception.InvalidRequestParameterException;
import com.coursescheduler.exception.InvalidSortParameterException;
import com.coursescheduler.exception.InvalidTimeSlotException;
import com.coursescheduler.exception.NoUndoAvailableException;
import com.coursescheduler.exception.ScheduleNotFoundException;
import com.coursescheduler.exception.TeacherConflictException;
import com.coursescheduler.model.OperationType;
import org.junit.jupiter.api.BeforeEach;

import java.time.Clock;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class CourseScheduleServiceTest {

    private static final int LATCH_TIMEOUT_SECONDS = 10;

    private CourseScheduleService scheduleService;
    private AuditLogService auditLogService;

    @BeforeEach
    void setUp() {
        auditLogService = new AuditLogService(Clock.systemDefaultZone());
        scheduleService = new CourseScheduleService(auditLogService,
                new RoomOutageRegistry(), new SchedulingLocks());
    }

    private CourseScheduleCreateRequest createRequest(String courseName, String teacherName, String classroom, String timeSlot) {
        CourseScheduleCreateRequest request = new CourseScheduleCreateRequest();
        request.setCourseName(courseName);
        request.setTeacherName(teacherName);
        request.setClassroom(classroom);
        request.setTimeSlot(timeSlot);
        return request;
    }

    private void shutdownExecutorSafely(ExecutorService executor) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                executor.awaitTermination(3, TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    @Test
    void testAddSchedule_Success() {
        CourseScheduleCreateRequest request = createRequest("数学", "张老师", "A101", "周一 08:00-10:00");

        CourseScheduleResponse result = scheduleService.addSchedule(request);

        assertNotNull(result.getId());
        assertEquals(1L, result.getId());
        assertEquals("数学", result.getCourseName());
        assertEquals("张老师", result.getTeacherName());
        assertEquals("A101", result.getClassroom());
        assertEquals("周一 08:00-10:00", result.getTimeSlot());

        List<CourseScheduleResponse> allSchedules = scheduleService.findSchedules(null);
        assertEquals(1, allSchedules.size());
    }

    @Test
    void testAddSchedule_TeacherConflict_ThrowsException() {
        CourseScheduleCreateRequest request1 = createRequest("数学", "张老师", "A101", "周一 08:00-10:00");
        scheduleService.addSchedule(request1);

        CourseScheduleCreateRequest request2 = createRequest("物理", "张老师", "B202", "周一 08:00-10:00");

        TeacherConflictException exception = assertThrows(TeacherConflictException.class, () -> {
            scheduleService.addSchedule(request2);
        });

        assertTrue(exception.getMessage().contains("张老师"));
        assertTrue(exception.getMessage().contains("周一 08:00-10:00"));

        List<CourseScheduleResponse> allSchedules = scheduleService.findSchedules(null);
        assertEquals(1, allSchedules.size());
    }

    @Test
    void testAddSchedule_DifferentTeacherSameTimeSlot_Success() {
        CourseScheduleCreateRequest request1 = createRequest("数学", "张老师", "A101", "周一 08:00-10:00");
        scheduleService.addSchedule(request1);

        CourseScheduleCreateRequest request2 = createRequest("物理", "李老师", "B202", "周一 08:00-10:00");

        CourseScheduleResponse result = scheduleService.addSchedule(request2);

        assertNotNull(result.getId());
        assertEquals(2L, result.getId());

        List<CourseScheduleResponse> allSchedules = scheduleService.findSchedules(null);
        assertEquals(2, allSchedules.size());
    }

    @Test
    void testAddSchedule_SameTeacherDifferentTimeSlot_Success() {
        CourseScheduleCreateRequest request1 = createRequest("数学", "张老师", "A101", "周一 08:00-10:00");
        scheduleService.addSchedule(request1);

        CourseScheduleCreateRequest request2 = createRequest("物理", "张老师", "A101", "周一 10:00-12:00");

        CourseScheduleResponse result = scheduleService.addSchedule(request2);

        assertNotNull(result.getId());
        assertEquals(2L, result.getId());

        List<CourseScheduleResponse> allSchedules = scheduleService.findSchedules(null);
        assertEquals(2, allSchedules.size());
    }

    @Test
    void testGetAllSchedules_EmptyList() {
        List<CourseScheduleResponse> schedules = scheduleService.findSchedules(null);
        assertTrue(schedules.isEmpty());
    }

    @Test
    void testGetAllSchedules_ReturnsCopy() {
        CourseScheduleCreateRequest request = createRequest("数学", "张老师", "A101", "周一 08:00-10:00");
        scheduleService.addSchedule(request);

        List<CourseScheduleResponse> schedules = scheduleService.findSchedules(null);
        schedules.clear();

        List<CourseScheduleResponse> actualSchedules = scheduleService.findSchedules(null);
        assertEquals(1, actualSchedules.size());
    }

    @Test
    void testConcurrentAddSchedule_SameTeacherSameTimeSlot_OnlyOneSucceeds() throws InterruptedException {
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        try {
            for (int i = 0; i < threadCount; i++) {
                final int index = i;
                executor.submit(() -> {
                    try {
                        CourseScheduleCreateRequest request = createRequest(
                                "课程" + index,
                                "王老师",
                                "教室" + index,
                                "周五 14:00-16:00"
                        );
                        scheduleService.addSchedule(request);
                        successCount.incrementAndGet();
                    } catch (TeacherConflictException e) {
                        conflictCount.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            boolean completed = latch.await(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            assertTrue(completed, "测试超时，线程未在规定时间内完成");

            assertEquals(1, successCount.get(), "应该只有 1 个成功新增");
            assertEquals(threadCount - 1, conflictCount.get(), "其余应该都是冲突异常");
            assertEquals(1, scheduleService.findSchedules(null).size(), "最终只有 1 条记录");
        } finally {
            shutdownExecutorSafely(executor);
        }
    }

    @Test
    void testConcurrentAddSchedule_DifferentTeachers_NoConflicts() throws InterruptedException {
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        List<Exception> exceptions = new ArrayList<>();

        try {
            for (int i = 0; i < threadCount; i++) {
                final int index = i;
                executor.submit(() -> {
                    try {
                        CourseScheduleCreateRequest request = createRequest(
                                "课程" + index,
                                "老师" + index,
                                "教室" + index,
                                "周一 08:00-10:00"
                        );
                        scheduleService.addSchedule(request);
                    } catch (Exception e) {
                        synchronized (exceptions) {
                            exceptions.add(e);
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            boolean completed = latch.await(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            assertTrue(completed, "测试超时，线程未在规定时间内完成");

            assertEquals(0, exceptions.size(), "不应该有任何异常");
            assertEquals(threadCount, scheduleService.findSchedules(null).size(), "应该全部新增成功");
        } finally {
            shutdownExecutorSafely(executor);
        }
    }

    @Test
    void testConcurrentAddSchedule_MixedScenarios_CorrectConflictDetection() throws InterruptedException {
        int teacherCount = 5;
        int requestsPerTeacher = 4;
        int totalRequests = teacherCount * requestsPerTeacher;

        ExecutorService executor = Executors.newFixedThreadPool(totalRequests);
        CountDownLatch latch = new CountDownLatch(totalRequests);
        AtomicInteger totalSuccess = new AtomicInteger(0);
        AtomicInteger totalConflicts = new AtomicInteger(0);

        try {
            for (int t = 0; t < teacherCount; t++) {
                final String teacherName = "老师" + t;
                final String classroomPrefix = "老师" + t + "教室";
                for (int r = 0; r < requestsPerTeacher; r++) {
                    final int requestIndex = r;
                    executor.submit(() -> {
                        try {
                            CourseScheduleCreateRequest request = createRequest(
                                    teacherName + "-课程" + requestIndex,
                                    teacherName,
                                    classroomPrefix + requestIndex,
                                    "周三 09:00-11:00"
                            );
                            scheduleService.addSchedule(request);
                            totalSuccess.incrementAndGet();
                        } catch (TeacherConflictException | ClassroomConflictException e) {
                            totalConflicts.incrementAndGet();
                        } finally {
                            latch.countDown();
                        }
                    });
                }
            }

            boolean completed = latch.await(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            assertTrue(completed, "测试超时，线程未在规定时间内完成");

            assertEquals(teacherCount, totalSuccess.get(), "每个老师应该只有 1 个成功");
            assertEquals(totalRequests - teacherCount, totalConflicts.get(), "其余应该都是冲突");
            assertEquals(teacherCount, scheduleService.findSchedules(null).size(), "最终记录数应等于老师数");
        } finally {
            shutdownExecutorSafely(executor);
        }
    }

    @Test
    void testClearAllSchedules_ThreadSafe() throws InterruptedException {
        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        try {
            for (int i = 0; i < threadCount / 2; i++) {
                final int index = i;
                executor.submit(() -> {
                    try {
                        CourseScheduleCreateRequest request = createRequest(
                                "课程" + index,
                                "老师" + index,
                                "教室" + index,
                                "周一 10:00-12:00"
                        );
                        scheduleService.addSchedule(request);
                    } finally {
                        latch.countDown();
                    }
                });
            }

            for (int i = 0; i < threadCount / 2; i++) {
                executor.submit(() -> {
                    try {
                        scheduleService.clearAllSchedules();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            boolean completed = latch.await(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            assertTrue(completed, "测试超时，线程未在规定时间内完成");

            List<CourseScheduleResponse> schedules = scheduleService.findSchedules(null);
            long uniqueIds = schedules.stream().map(CourseScheduleResponse::getId).distinct().count();
            assertEquals(schedules.size(), uniqueIds, "ID 不应该有重复");
        } finally {
            shutdownExecutorSafely(executor);
        }
    }

    private CourseScheduleFilterRequest createFilter(String teacherName, String classroom, String timeSlot) {
        CourseScheduleFilterRequest filter = new CourseScheduleFilterRequest();
        filter.setTeacherName(teacherName);
        filter.setClassroom(classroom);
        filter.setTimeSlot(timeSlot);
        return filter;
    }

    private void addSampleData() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));
        scheduleService.addSchedule(createRequest("物理", "张教授", "B202", "周一 10:00-12:00"));
        scheduleService.addSchedule(createRequest("化学", "李副教授", "教学楼A101", "周二 08:00-10:00"));
        scheduleService.addSchedule(createRequest("英语", "王老师", "教学楼C303", "周二 14:00-16:00"));
    }

    @Test
    void testFilterSchedules_NoCondition_ReturnsAll() {
        addSampleData();

        CourseScheduleFilterRequest filter = createFilter(null, null, null);
        List<CourseScheduleResponse> result = scheduleService.findSchedules(filter);

        assertEquals(4, result.size());
    }

    @Test
    void testFilterSchedules_ByTeacher() {
        addSampleData();

        CourseScheduleFilterRequest filter = createFilter("张", null, null);
        List<CourseScheduleResponse> result = scheduleService.findSchedules(filter);

        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(r -> r.getTeacherName().contains("张")));
    }

    @Test
    void testFilterSchedules_ByClassroom() {
        addSampleData();

        CourseScheduleFilterRequest filter = createFilter(null, "A101", null);
        List<CourseScheduleResponse> result = scheduleService.findSchedules(filter);

        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(r -> r.getClassroom().contains("A101")));
    }

    @Test
    void testFilterSchedules_ByClassroom_KeywordBuilding() {
        addSampleData();

        CourseScheduleFilterRequest filter = createFilter(null, "教学楼", null);
        List<CourseScheduleResponse> result = scheduleService.findSchedules(filter);

        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(r -> r.getClassroom().contains("教学楼")));
    }

    @Test
    void testFilterSchedules_ByTimeSlot() {
        addSampleData();

        CourseScheduleFilterRequest filter = createFilter(null, null, "周一 08:00-10:00");
        List<CourseScheduleResponse> result = scheduleService.findSchedules(filter);

        assertEquals(1, result.size());
        assertEquals("数学", result.get(0).getCourseName());
    }

    @Test
    void testFilterSchedules_MultipleConditions() {
        addSampleData();

        CourseScheduleFilterRequest filter = createFilter("张", "A101", "周一 08:00-10:00");
        List<CourseScheduleResponse> result = scheduleService.findSchedules(filter);

        assertEquals(1, result.size());
        assertEquals("数学", result.get(0).getCourseName());
    }

    @Test
    void testFilterSchedules_MultipleConditionsPartialMatch() {
        addSampleData();

        CourseScheduleFilterRequest filter = createFilter("张", "B202", null);
        List<CourseScheduleResponse> result = scheduleService.findSchedules(filter);

        assertEquals(1, result.size());
        assertEquals("物理", result.get(0).getCourseName());
    }

    @Test
    void testFilterSchedules_NoMatch_ReturnsEmpty() {
        addSampleData();

        CourseScheduleFilterRequest filter = createFilter("赵", null, null);
        List<CourseScheduleResponse> result = scheduleService.findSchedules(filter);

        assertTrue(result.isEmpty());
    }

    @Test
    void testFilterSchedules_TrimmedParameter() {
        addSampleData();

        CourseScheduleFilterRequest filter = createFilter("  张  ", "  A101  ", null);
        List<CourseScheduleResponse> result = scheduleService.findSchedules(filter);

        assertEquals(1, result.size());
        assertEquals("数学", result.get(0).getCourseName());
    }

    @Test
    void testFilterSchedules_OnlyWhitespace_NoFilter() {
        addSampleData();

        CourseScheduleFilterRequest filter = createFilter("   ", "   ", "   ");
        List<CourseScheduleResponse> result = scheduleService.findSchedules(filter);

        assertEquals(4, result.size());
    }

    @Test
    void testFilterSchedules_AssociateProfessor_Keyword() {
        addSampleData();

        CourseScheduleFilterRequest filter = createFilter("副教授", null, null);
        List<CourseScheduleResponse> result = scheduleService.findSchedules(filter);

        assertEquals(1, result.size());
        assertEquals("李副教授", result.get(0).getTeacherName());
    }

    @Test
    void testFilterSchedules_EmptyData_NoCondition_ReturnsEmpty() {
        CourseScheduleFilterRequest filter = createFilter(null, null, null);
        List<CourseScheduleResponse> result = scheduleService.findSchedules(filter);

        assertTrue(result.isEmpty());
    }

    @Test
    void testGetStatistics_WithData() {
        addSampleData();

        CourseScheduleStatisticsResponse stats = scheduleService.getStatistics();

        assertEquals(4, stats.getTotalCourses());
        assertEquals(4, stats.getTeacherCount());
        assertEquals(4, stats.getClassroomCount());
    }

    @Test
    void testGetStatistics_EmptyData() {
        CourseScheduleStatisticsResponse stats = scheduleService.getStatistics();

        assertEquals(0, stats.getTotalCourses());
        assertEquals(0, stats.getTeacherCount());
        assertEquals(0, stats.getClassroomCount());
    }

    @Test
    void testGetStatistics_SameTeacherMultipleCourses() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));
        scheduleService.addSchedule(createRequest("物理", "张老师", "B202", "周一 10:00-12:00"));
        scheduleService.addSchedule(createRequest("化学", "李老师", "A101", "周二 08:00-10:00"));

        CourseScheduleStatisticsResponse stats = scheduleService.getStatistics();

        assertEquals(3, stats.getTotalCourses());
        assertEquals(2, stats.getTeacherCount());
        assertEquals(2, stats.getClassroomCount());
    }

    @Test
    void testDeleteSchedule_Success() {
        CourseScheduleResponse added = scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));

        CourseScheduleDeleteResponse deleted = scheduleService.deleteSchedule(added.getId());

        assertEquals(added.getId(), deleted.getId());
        assertEquals("数学", deleted.getCourseName());
        assertEquals("张老师", deleted.getTeacherName());
        assertEquals("A101", deleted.getClassroom());
        assertEquals("周一 08:00-10:00", deleted.getTimeSlot());
        assertEquals(0, scheduleService.findSchedules(null).size());
    }

    @Test
    void testDeleteSchedule_NotFound_ThrowsException() {
        ScheduleNotFoundException exception = assertThrows(ScheduleNotFoundException.class, () -> {
            scheduleService.deleteSchedule(999L);
        });
        assertTrue(exception.getMessage().contains("999"));
    }

    @Test
    void testDeleteSchedule_ZeroId_ThrowsException() {
        ScheduleNotFoundException exception = assertThrows(ScheduleNotFoundException.class, () -> {
            scheduleService.deleteSchedule(0L);
        });
        assertTrue(exception.getMessage().contains("0"));
    }

    @Test
    void testDeleteSchedule_NegativeId_ThrowsException() {
        ScheduleNotFoundException exception = assertThrows(ScheduleNotFoundException.class, () -> {
            scheduleService.deleteSchedule(-1L);
        });
        assertTrue(exception.getMessage().contains("-1"));
    }

    @Test
    void testDeleteSchedule_ListAndStatisticsUpdated() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));
        CourseScheduleResponse added2 = scheduleService.addSchedule(createRequest("物理", "李老师", "B202", "周二 08:00-10:00"));

        scheduleService.deleteSchedule(added2.getId());

        List<CourseScheduleResponse> schedules = scheduleService.findSchedules(null);
        assertEquals(1, schedules.size());
        assertEquals("数学", schedules.get(0).getCourseName());

        CourseScheduleStatisticsResponse stats = scheduleService.getStatistics();
        assertEquals(1, stats.getTotalCourses());
        assertEquals(1, stats.getTeacherCount());
        assertEquals(1, stats.getClassroomCount());
    }

    @Test
    void testDeleteSchedule_CanReAddSameTeacherSameTimeSlot() {
        CourseScheduleResponse added = scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));
        scheduleService.deleteSchedule(added.getId());

        CourseScheduleResponse reAdded = scheduleService.addSchedule(createRequest("物理", "张老师", "B202", "周一 08:00-10:00"));

        assertNotNull(reAdded.getId());
        assertEquals("物理", reAdded.getCourseName());
        assertEquals(1, scheduleService.findSchedules(null).size());
    }

    @Test
    void testDeleteSchedule_DeleteSameIdTwice_ThrowsOnSecond() {
        CourseScheduleResponse added = scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));
        scheduleService.deleteSchedule(added.getId());

        assertThrows(ScheduleNotFoundException.class, () -> {
            scheduleService.deleteSchedule(added.getId());
        });
    }

    @Test
    void testDeleteSchedule_FilterStillWorks() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));
        CourseScheduleResponse added2 = scheduleService.addSchedule(createRequest("物理", "李老师", "B202", "周二 08:00-10:00"));
        scheduleService.deleteSchedule(added2.getId());

        CourseScheduleFilterRequest filter = createFilter("张", null, null);
        List<CourseScheduleResponse> result = scheduleService.findSchedules(filter);

        assertEquals(1, result.size());
        assertEquals("数学", result.get(0).getCourseName());
    }

    private CourseScheduleUpdateRequest createUpdateRequest(String courseName, String teacherName, String classroom, String timeSlot) {
        CourseScheduleUpdateRequest request = new CourseScheduleUpdateRequest();
        request.setCourseName(courseName);
        request.setTeacherName(teacherName);
        request.setClassroom(classroom);
        request.setTimeSlot(timeSlot);
        return request;
    }

    @Test
    void testUpdateSchedule_Success() {
        CourseScheduleResponse added = scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));

        CourseScheduleUpdateRequest updateRequest = createUpdateRequest("高等数学", "李老师", "B202", "周二 14:00-16:00");
        CourseScheduleResponse result = scheduleService.updateSchedule(added.getId(), updateRequest);

        assertEquals(added.getId(), result.getId());
        assertEquals("高等数学", result.getCourseName());
        assertEquals("李老师", result.getTeacherName());
        assertEquals("B202", result.getClassroom());
        assertEquals("周二 14:00-16:00", result.getTimeSlot());

        List<CourseScheduleResponse> allSchedules = scheduleService.findSchedules(null);
        assertEquals(1, allSchedules.size());
        assertEquals("高等数学", allSchedules.get(0).getCourseName());
    }

    @Test
    void testUpdateSchedule_NotFound_ThrowsException() {
        CourseScheduleUpdateRequest updateRequest = createUpdateRequest("高等数学", "李老师", "B202", "周二 14:00-16:00");

        ScheduleNotFoundException exception = assertThrows(ScheduleNotFoundException.class, () -> {
            scheduleService.updateSchedule(999L, updateRequest);
        });
        assertTrue(exception.getMessage().contains("999"));
    }

    @Test
    void testUpdateSchedule_FilterWorksAfterUpdate() {
        CourseScheduleResponse added = scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));

        CourseScheduleUpdateRequest updateRequest = createUpdateRequest("物理", "李老师", "B202", "周二 14:00-16:00");
        scheduleService.updateSchedule(added.getId(), updateRequest);

        CourseScheduleFilterRequest filterByTeacher = createFilter("李", null, null);
        List<CourseScheduleResponse> resultByTeacher = scheduleService.findSchedules(filterByTeacher);
        assertEquals(1, resultByTeacher.size());
        assertEquals("物理", resultByTeacher.get(0).getCourseName());

        CourseScheduleFilterRequest filterByClassroom = createFilter(null, "B202", null);
        List<CourseScheduleResponse> resultByClassroom = scheduleService.findSchedules(filterByClassroom);
        assertEquals(1, resultByClassroom.size());
        assertEquals("物理", resultByClassroom.get(0).getCourseName());

        CourseScheduleFilterRequest filterByTimeSlot = createFilter(null, null, "周二 14:00-16:00");
        List<CourseScheduleResponse> resultByTimeSlot = scheduleService.findSchedules(filterByTimeSlot);
        assertEquals(1, resultByTimeSlot.size());
        assertEquals("物理", resultByTimeSlot.get(0).getCourseName());

        CourseScheduleStatisticsResponse stats = scheduleService.getStatistics();
        assertEquals(1, stats.getTotalCourses());
        assertEquals(1, stats.getTeacherCount());
        assertEquals(1, stats.getClassroomCount());
    }

    @Test
    void testUpdateSchedule_TeacherConflictWithOther_ThrowsException() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));
        CourseScheduleResponse added2 = scheduleService.addSchedule(createRequest("物理", "李老师", "B202", "周二 14:00-16:00"));

        CourseScheduleUpdateRequest updateRequest = createUpdateRequest("化学", "张老师", "C303", "周一 08:00-10:00");

        TeacherConflictException exception = assertThrows(TeacherConflictException.class, () -> {
            scheduleService.updateSchedule(added2.getId(), updateRequest);
        });
        assertTrue(exception.getMessage().contains("张老师"));
        assertTrue(exception.getMessage().contains("周一 08:00-10:00"));

        List<CourseScheduleResponse> allSchedules = scheduleService.findSchedules(null);
        assertEquals(2, allSchedules.size());
        assertEquals("物理", allSchedules.get(1).getCourseName());
    }

    @Test
    void testUpdateSchedule_OnlyChangeCourseName_NoSelfConflict() {
        CourseScheduleResponse added = scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));

        CourseScheduleUpdateRequest updateRequest = createUpdateRequest("高等数学", "张老师", "A101", "周一 08:00-10:00");
        CourseScheduleResponse result = scheduleService.updateSchedule(added.getId(), updateRequest);

        assertEquals(added.getId(), result.getId());
        assertEquals("高等数学", result.getCourseName());
        assertEquals("张老师", result.getTeacherName());
        assertEquals("A101", result.getClassroom());
        assertEquals("周一 08:00-10:00", result.getTimeSlot());

        List<CourseScheduleResponse> allSchedules = scheduleService.findSchedules(null);
        assertEquals(1, allSchedules.size());
    }

    private CourseScheduleFilterRequest createSortFilter(String sortBy, String sortDirection) {
        CourseScheduleFilterRequest filter = new CourseScheduleFilterRequest();
        filter.setSortBy(sortBy);
        filter.setSortDirection(sortDirection);
        return filter;
    }

    private CourseScheduleFilterRequest createFilterWithSort(
            String teacherName, String classroom, String timeSlot,
            String sortBy, String sortDirection) {
        CourseScheduleFilterRequest filter = new CourseScheduleFilterRequest();
        filter.setTeacherName(teacherName);
        filter.setClassroom(classroom);
        filter.setTimeSlot(timeSlot);
        filter.setSortBy(sortBy);
        filter.setSortDirection(sortDirection);
        return filter;
    }

    private void addSortSampleData() {
        scheduleService.addSchedule(createRequest("物理", "张教授", "B202", "周二 08:00-10:00"));
        scheduleService.addSchedule(createRequest("数学", "李副教授", "A101", "周一 08:00-10:00"));
        scheduleService.addSchedule(createRequest("化学", "王教授", "C303", "周三 09:00-11:00"));
    }

    @Test
    void testFindSchedules_DefaultOrder_ByCreation() {
        addSortSampleData();

        List<CourseScheduleResponse> result = scheduleService.findSchedules(createSortFilter(null, null));

        assertEquals(3, result.size());
        assertEquals("物理", result.get(0).getCourseName());
        assertEquals("数学", result.get(1).getCourseName());
        assertEquals("化学", result.get(2).getCourseName());
    }

    @Test
    void testFindSchedules_SortByCourseName_Ascending() {
        addSortSampleData();

        List<CourseScheduleResponse> result = scheduleService.findSchedules(
                createSortFilter("courseName", "asc"));

        assertEquals(3, result.size());
        assertEquals("化学", result.get(0).getCourseName());
        assertEquals("数学", result.get(1).getCourseName());
        assertEquals("物理", result.get(2).getCourseName());
    }

    @Test
    void testFindSchedules_SortByCourseName_Descending() {
        addSortSampleData();

        List<CourseScheduleResponse> result = scheduleService.findSchedules(
                createSortFilter("courseName", "desc"));

        assertEquals(3, result.size());
        assertEquals("物理", result.get(0).getCourseName());
        assertEquals("数学", result.get(1).getCourseName());
        assertEquals("化学", result.get(2).getCourseName());
    }

    @Test
    void testFindSchedules_FilterThenSort_ByTeacherFilterSortByCourseName() {
        scheduleService.addSchedule(createRequest("物理", "张教授", "B202", "周二 08:00-10:00"));
        scheduleService.addSchedule(createRequest("数学", "张教授", "A101", "周一 08:00-10:00"));
        scheduleService.addSchedule(createRequest("化学", "李副教授", "C303", "周三 09:00-11:00"));
        scheduleService.addSchedule(createRequest("生物", "张教授", "D404", "周四 10:00-12:00"));

        List<CourseScheduleResponse> result = scheduleService.findSchedules(
                createFilterWithSort("张", null, null, "courseName", "asc"));

        assertEquals(3, result.size());
        assertTrue(result.stream().allMatch(r -> r.getTeacherName().contains("张")));
        assertEquals("数学", result.get(0).getCourseName());
        assertEquals("物理", result.get(1).getCourseName());
        assertEquals("生物", result.get(2).getCourseName());
    }

    @Test
    void testFindSchedules_InvalidSortField_ThrowsInvalidSortParameterException() {
        InvalidSortParameterException exception = assertThrows(InvalidSortParameterException.class, () -> {
            scheduleService.findSchedules(createSortFilter("invalidField", "asc"));
        });
        assertTrue(exception.getMessage().contains("invalidField"));
        assertTrue(exception.getMessage().contains("不支持的排序字段"));
    }

    @Test
    void testFindSchedules_InvalidSortDirection_ThrowsInvalidSortParameterException() {
        InvalidSortParameterException exception = assertThrows(InvalidSortParameterException.class, () -> {
            scheduleService.findSchedules(createSortFilter("courseName", "invalid"));
        });
        assertTrue(exception.getMessage().contains("invalid"));
        assertTrue(exception.getMessage().contains("不支持的排序方向"));
    }

    @Test
    void testAddSchedule_ClassroomConflict_ThrowsException() {
        CourseScheduleCreateRequest request1 = createRequest("数学", "张老师", "A101", "周一 08:00-10:00");
        scheduleService.addSchedule(request1);

        CourseScheduleCreateRequest request2 = createRequest("物理", "李老师", "A101", "周一 08:00-10:00");

        ClassroomConflictException exception = assertThrows(ClassroomConflictException.class, () -> {
            scheduleService.addSchedule(request2);
        });

        assertTrue(exception.getMessage().contains("A101"));
        assertTrue(exception.getMessage().contains("周一 08:00-10:00"));

        List<CourseScheduleResponse> allSchedules = scheduleService.findSchedules(null);
        assertEquals(1, allSchedules.size());
    }

    @Test
    void testAddSchedule_DifferentClassroomSameTimeSlot_Success() {
        CourseScheduleCreateRequest request1 = createRequest("数学", "张老师", "A101", "周一 08:00-10:00");
        scheduleService.addSchedule(request1);

        CourseScheduleCreateRequest request2 = createRequest("物理", "李老师", "B202", "周一 08:00-10:00");

        CourseScheduleResponse result = scheduleService.addSchedule(request2);

        assertNotNull(result.getId());
        assertEquals(2L, result.getId());

        List<CourseScheduleResponse> allSchedules = scheduleService.findSchedules(null);
        assertEquals(2, allSchedules.size());
    }

    @Test
    void testAddSchedule_SameClassroomDifferentTimeSlot_Success() {
        CourseScheduleCreateRequest request1 = createRequest("数学", "张老师", "A101", "周一 08:00-10:00");
        scheduleService.addSchedule(request1);

        CourseScheduleCreateRequest request2 = createRequest("物理", "李老师", "A101", "周一 10:00-12:00");

        CourseScheduleResponse result = scheduleService.addSchedule(request2);

        assertNotNull(result.getId());
        assertEquals(2L, result.getId());

        List<CourseScheduleResponse> allSchedules = scheduleService.findSchedules(null);
        assertEquals(2, allSchedules.size());
    }

    @Test
    void testUpdateSchedule_ClassroomConflictWithOther_ThrowsException() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));
        CourseScheduleResponse added2 = scheduleService.addSchedule(createRequest("物理", "李老师", "B202", "周二 14:00-16:00"));

        CourseScheduleUpdateRequest updateRequest = createUpdateRequest("化学", "王老师", "A101", "周一 08:00-10:00");

        ClassroomConflictException exception = assertThrows(ClassroomConflictException.class, () -> {
            scheduleService.updateSchedule(added2.getId(), updateRequest);
        });
        assertTrue(exception.getMessage().contains("A101"));
        assertTrue(exception.getMessage().contains("周一 08:00-10:00"));

        List<CourseScheduleResponse> allSchedules = scheduleService.findSchedules(null);
        assertEquals(2, allSchedules.size());
        assertEquals("物理", allSchedules.get(1).getCourseName());
    }

    @Test
    void testUpdateSchedule_OnlyChangeCourseName_NoClassroomSelfConflict() {
        CourseScheduleResponse added = scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));

        CourseScheduleUpdateRequest updateRequest = createUpdateRequest("高等数学", "张老师", "A101", "周一 08:00-10:00");
        CourseScheduleResponse result = scheduleService.updateSchedule(added.getId(), updateRequest);

        assertEquals(added.getId(), result.getId());
        assertEquals("高等数学", result.getCourseName());
        assertEquals("A101", result.getClassroom());
        assertEquals("周一 08:00-10:00", result.getTimeSlot());

        List<CourseScheduleResponse> allSchedules = scheduleService.findSchedules(null);
        assertEquals(1, allSchedules.size());
    }

    @Test
    void testDeleteSchedule_CanReAddSameClassroomSameTimeSlot() {
        CourseScheduleResponse added = scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));
        scheduleService.deleteSchedule(added.getId());

        CourseScheduleResponse reAdded = scheduleService.addSchedule(createRequest("物理", "李老师", "A101", "周一 08:00-10:00"));

        assertNotNull(reAdded.getId());
        assertEquals("物理", reAdded.getCourseName());
        assertEquals(1, scheduleService.findSchedules(null).size());
    }

    @Test
    void testAddSchedule_TeacherConflictStillWorks_AfterAddingClassroomCheck() {
        CourseScheduleCreateRequest request1 = createRequest("数学", "张老师", "A101", "周一 08:00-10:00");
        scheduleService.addSchedule(request1);

        CourseScheduleCreateRequest request2 = createRequest("物理", "张老师", "B202", "周一 08:00-10:00");

        TeacherConflictException exception = assertThrows(TeacherConflictException.class, () -> {
            scheduleService.addSchedule(request2);
        });

        assertTrue(exception.getMessage().contains("张老师"));
        assertTrue(exception.getMessage().contains("周一 08:00-10:00"));

        List<CourseScheduleResponse> allSchedules = scheduleService.findSchedules(null);
        assertEquals(1, allSchedules.size());
    }

    @Test
    void testUpdateSchedule_TeacherConflictStillWorks_AfterAddingClassroomCheck() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));
        CourseScheduleResponse added2 = scheduleService.addSchedule(createRequest("物理", "李老师", "B202", "周二 14:00-16:00"));

        CourseScheduleUpdateRequest updateRequest = createUpdateRequest("化学", "张老师", "C303", "周一 08:00-10:00");

        TeacherConflictException exception = assertThrows(TeacherConflictException.class, () -> {
            scheduleService.updateSchedule(added2.getId(), updateRequest);
        });
        assertTrue(exception.getMessage().contains("张老师"));
        assertTrue(exception.getMessage().contains("周一 08:00-10:00"));

        List<CourseScheduleResponse> allSchedules = scheduleService.findSchedules(null);
        assertEquals(2, allSchedules.size());
        assertEquals("物理", allSchedules.get(1).getCourseName());
    }

    @Test
    void testConcurrentAddSchedule_SameClassroomSameTimeSlot_OnlyOneSucceeds() throws InterruptedException {
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        try {
            for (int i = 0; i < threadCount; i++) {
                final int index = i;
                executor.submit(() -> {
                    try {
                        CourseScheduleCreateRequest request = createRequest(
                                "课程" + index,
                                "老师" + index,
                                "A101",
                                "周一 08:00-10:00"
                        );
                        scheduleService.addSchedule(request);
                        successCount.incrementAndGet();
                    } catch (ClassroomConflictException | TeacherConflictException e) {
                        conflictCount.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            boolean completed = latch.await(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            assertTrue(completed, "测试超时，线程未在规定时间内完成");

            assertEquals(1, successCount.get(), "应该只有 1 个成功新增");
            assertEquals(threadCount - 1, conflictCount.get(), "其余应该都是冲突异常");
            assertEquals(1, scheduleService.findSchedules(null).size(), "最终只有 1 条记录");
        } finally {
            shutdownExecutorSafely(executor);
        }
    }

    @Test
    void testAddSchedule_TeacherConflictAndClassroomConflict_ThrowsTeacherConflictFirst() {
        CourseScheduleCreateRequest request1 = createRequest("数学", "张老师", "A101", "周一 08:00-10:00");
        scheduleService.addSchedule(request1);

        CourseScheduleCreateRequest request2 = createRequest("物理", "张老师", "A101", "周一 08:00-10:00");

        TeacherConflictException exception = assertThrows(TeacherConflictException.class, () -> {
            scheduleService.addSchedule(request2);
        });

        assertTrue(exception.getMessage().contains("张老师"));
        assertTrue(exception.getMessage().contains("周一 08:00-10:00"));

        List<CourseScheduleResponse> allSchedules = scheduleService.findSchedules(null);
        assertEquals(1, allSchedules.size());
    }

    @Test
    void testUpdateSchedule_TeacherConflictAndClassroomConflict_ThrowsTeacherConflictFirst() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));
        CourseScheduleResponse added2 = scheduleService.addSchedule(createRequest("物理", "李老师", "B202", "周二 14:00-16:00"));

        CourseScheduleUpdateRequest updateRequest = createUpdateRequest("化学", "张老师", "A101", "周一 08:00-10:00");

        TeacherConflictException exception = assertThrows(TeacherConflictException.class, () -> {
            scheduleService.updateSchedule(added2.getId(), updateRequest);
        });
        assertTrue(exception.getMessage().contains("张老师"));
        assertTrue(exception.getMessage().contains("周一 08:00-10:00"));

        List<CourseScheduleResponse> allSchedules = scheduleService.findSchedules(null);
        assertEquals(2, allSchedules.size());
        assertEquals("物理", allSchedules.get(1).getCourseName());
    }

    @Test
    void testAddSchedule_TimeSlotNormalization_SingleDigitHour() {
        CourseScheduleCreateRequest request1 = createRequest("数学", "张老师", "A101", "周一 8:00-10:00");
        CourseScheduleResponse result1 = scheduleService.addSchedule(request1);

        assertEquals("周一 08:00-10:00", result1.getTimeSlot());

        CourseScheduleCreateRequest request2 = createRequest("物理", "李老师", "A101", "周一 08:00-10:00");

        ClassroomConflictException exception = assertThrows(ClassroomConflictException.class, () -> {
            scheduleService.addSchedule(request2);
        });
        assertTrue(exception.getMessage().contains("A101"));
        assertTrue(exception.getMessage().contains("周一 08:00-10:00"));
    }

    @Test
    void testAddSchedule_TimeSlotNormalization_BothSingleDigitHours() {
        CourseScheduleCreateRequest request1 = createRequest("数学", "张老师", "A101", "周一 8:00-9:30");
        CourseScheduleResponse result1 = scheduleService.addSchedule(request1);

        assertEquals("周一 08:00-09:30", result1.getTimeSlot());

        CourseScheduleCreateRequest request2 = createRequest("物理", "李老师", "A101", "周一 08:00-09:30");

        ClassroomConflictException exception = assertThrows(ClassroomConflictException.class, () -> {
            scheduleService.addSchedule(request2);
        });
        assertTrue(exception.getMessage().contains("周一 08:00-09:30"));
    }

    @Test
    void testAddSchedule_TimeSlotNormalization_ExtraSpaces() {
        CourseScheduleCreateRequest request1 = createRequest("数学", "张老师", "A101", "  周一   08:00-10:00  ");
        CourseScheduleResponse result1 = scheduleService.addSchedule(request1);

        assertEquals("周一 08:00-10:00", result1.getTimeSlot());

        CourseScheduleCreateRequest request2 = createRequest("物理", "李老师", "A101", "周一 08:00-10:00");

        ClassroomConflictException exception = assertThrows(ClassroomConflictException.class, () -> {
            scheduleService.addSchedule(request2);
        });
        assertTrue(exception.getMessage().contains("周一 08:00-10:00"));
    }

    @Test
    void testUpdateSchedule_TimeSlotNormalization() {
        CourseScheduleResponse added = scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));

        CourseScheduleUpdateRequest updateRequest = createUpdateRequest("高等数学", "张老师", "A101", "周一 8:00-10:00");
        CourseScheduleResponse result = scheduleService.updateSchedule(added.getId(), updateRequest);

        assertEquals("周一 08:00-10:00", result.getTimeSlot());
    }

    @Test
    void testFilterSchedules_TimeSlotNormalization() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));

        CourseScheduleFilterRequest filter = createFilter(null, null, "周一 8:00-10:00");
        List<CourseScheduleResponse> result = scheduleService.findSchedules(filter);

        assertEquals(1, result.size());
        assertEquals("数学", result.get(0).getCourseName());
    }

    @Test
    void testDeleteSchedule_CanReAddWithDifferentTimeSlotFormat() {
        CourseScheduleResponse added = scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));
        scheduleService.deleteSchedule(added.getId());

        CourseScheduleResponse reAdded = scheduleService.addSchedule(createRequest("物理", "李老师", "A101", "周一 8:00-10:00"));

        assertNotNull(reAdded.getId());
        assertEquals("物理", reAdded.getCourseName());
        assertEquals("周一 08:00-10:00", reAdded.getTimeSlot());
        assertEquals(1, scheduleService.findSchedules(null).size());
    }

    @Test
    void testAddSchedule_TeacherConflictWithDifferentTimeSlotFormat() {
        CourseScheduleCreateRequest request1 = createRequest("数学", "张老师", "A101", "周一 08:00-10:00");
        scheduleService.addSchedule(request1);

        CourseScheduleCreateRequest request2 = createRequest("物理", "张老师", "B202", "周一 8:00-10:00");

        TeacherConflictException exception = assertThrows(TeacherConflictException.class, () -> {
            scheduleService.addSchedule(request2);
        });

        assertTrue(exception.getMessage().contains("张老师"));
        assertTrue(exception.getMessage().contains("周一 08:00-10:00"));
    }

    @Test
    void testTimeSlotNormalization_NoChangeForAlreadyNormalized() {
        CourseScheduleCreateRequest request = createRequest("数学", "张老师", "A101", "周一 08:00-10:00");
        CourseScheduleResponse result = scheduleService.addSchedule(request);

        assertEquals("周一 08:00-10:00", result.getTimeSlot());
    }

    @Test
    void testTimeSlotNormalization_AfterNoonSingleDigit() {
        CourseScheduleCreateRequest request = createRequest("数学", "张老师", "A101", "周一 1:00-3:30");
        CourseScheduleResponse result = scheduleService.addSchedule(request);

        assertEquals("周一 01:00-03:30", result.getTimeSlot());
    }

    @Test
    void testOriginalScenarios_AddScheduleStillWorks() {
        CourseScheduleCreateRequest request = createRequest("数学", "张老师", "A101", "周一 08:00-10:00");

        CourseScheduleResponse result = scheduleService.addSchedule(request);

        assertNotNull(result.getId());
        assertEquals(1L, result.getId());
        assertEquals("数学", result.getCourseName());
        assertEquals("张老师", result.getTeacherName());
        assertEquals("A101", result.getClassroom());
        assertEquals("周一 08:00-10:00", result.getTimeSlot());
    }

    @Test
    void testOriginalScenarios_UpdateScheduleStillWorks() {
        CourseScheduleResponse added = scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));

        CourseScheduleUpdateRequest updateRequest = createUpdateRequest("高等数学", "李老师", "B202", "周二 14:00-16:00");
        CourseScheduleResponse result = scheduleService.updateSchedule(added.getId(), updateRequest);

        assertEquals(added.getId(), result.getId());
        assertEquals("高等数学", result.getCourseName());
        assertEquals("李老师", result.getTeacherName());
        assertEquals("B202", result.getClassroom());
        assertEquals("周二 14:00-16:00", result.getTimeSlot());
    }

    @Test
    void testOriginalScenarios_DeleteAndReuseStillWorks() {
        CourseScheduleResponse added = scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));
        scheduleService.deleteSchedule(added.getId());

        CourseScheduleResponse reAdded = scheduleService.addSchedule(createRequest("物理", "张老师", "A101", "周一 08:00-10:00"));

        assertNotNull(reAdded.getId());
        assertEquals("物理", reAdded.getCourseName());
        assertEquals(1, scheduleService.findSchedules(null).size());
    }

    @Test
    void testOriginalScenarios_TeacherConflictStillWorks() {
        CourseScheduleCreateRequest request1 = createRequest("数学", "张老师", "A101", "周一 08:00-10:00");
        scheduleService.addSchedule(request1);

        CourseScheduleCreateRequest request2 = createRequest("物理", "张老师", "B202", "周一 08:00-10:00");

        TeacherConflictException exception = assertThrows(TeacherConflictException.class, () -> {
            scheduleService.addSchedule(request2);
        });

        assertTrue(exception.getMessage().contains("张老师"));
        assertTrue(exception.getMessage().contains("周一 08:00-10:00"));

        List<CourseScheduleResponse> allSchedules = scheduleService.findSchedules(null);
        assertEquals(1, allSchedules.size());
    }

    @Test
    void testApiInput_AddSchedule_NonStandardWeekdayFormat() {
        CourseScheduleCreateRequest request = createRequest("数学", "张老师", "A101", "星期一 8:00-10:00");
        CourseScheduleResponse result = scheduleService.addSchedule(request);

        assertEquals("周一 08:00-10:00", result.getTimeSlot());
    }

    @Test
    void testApiInput_AddSchedule_NumericWeekday() {
        CourseScheduleCreateRequest request = createRequest("数学", "张老师", "A101", "周1 08:00-10:00");
        CourseScheduleResponse result = scheduleService.addSchedule(request);

        assertEquals("周一 08:00-10:00", result.getTimeSlot());
    }

    @Test
    void testApiInput_AddSchedule_ExtraSpaces() {
        CourseScheduleCreateRequest request = createRequest("数学", "张老师", "A101", "  周一   08:00 - 10:00  ");
        CourseScheduleResponse result = scheduleService.addSchedule(request);

        assertEquals("周一 08:00-10:00", result.getTimeSlot());
    }

    @Test
    void testApiInput_AddSchedule_InvalidFormat_ThrowsException() {
        CourseScheduleCreateRequest request = createRequest("数学", "张老师", "A101", "明天上午");

        InvalidTimeSlotException exception = assertThrows(InvalidTimeSlotException.class, () -> {
            scheduleService.addSchedule(request);
        });
        assertTrue(exception.getMessage().contains("格式不合法"));
    }

    @Test
    void testApiInput_AddSchedule_StartAfterEnd_ThrowsException() {
        CourseScheduleCreateRequest request = createRequest("数学", "张老师", "A101", "周一 10:00-08:00");

        InvalidTimeSlotException exception = assertThrows(InvalidTimeSlotException.class, () -> {
            scheduleService.addSchedule(request);
        });
        assertTrue(exception.getMessage().contains("必须早于"));
    }

    @Test
    void testApiInput_AddSchedule_InvalidHour_ThrowsException() {
        CourseScheduleCreateRequest request = createRequest("数学", "张老师", "A101", "周一 25:00-26:00");

        InvalidTimeSlotException exception = assertThrows(InvalidTimeSlotException.class, () -> {
            scheduleService.addSchedule(request);
        });
        assertTrue(exception.getMessage().contains("不合法"));
    }

    @Test
    void testApiInput_UpdateSchedule_NonStandardFormat() {
        CourseScheduleResponse added = scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));

        CourseScheduleUpdateRequest updateRequest = createUpdateRequest("高等数学", "张老师", "A101", "星期1 9:00-11:00");
        CourseScheduleResponse result = scheduleService.updateSchedule(added.getId(), updateRequest);

        assertEquals("周一 09:00-11:00", result.getTimeSlot());
    }

    @Test
    void testApiInput_UpdateSchedule_InvalidFormat_ThrowsException() {
        CourseScheduleResponse added = scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));

        CourseScheduleUpdateRequest updateRequest = createUpdateRequest("高等数学", "张老师", "A101", "随便写");

        InvalidTimeSlotException exception = assertThrows(InvalidTimeSlotException.class, () -> {
            scheduleService.updateSchedule(added.getId(), updateRequest);
        });
        assertTrue(exception.getMessage().contains("格式不合法"));
    }

    @Test
    void testApiInput_FilterSchedules_NonStandardFormat() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));
        scheduleService.addSchedule(createRequest("物理", "李老师", "B202", "周二 14:00-16:00"));

        CourseScheduleFilterRequest filter = createFilter(null, null, "星期一 8:00-10:00");
        List<CourseScheduleResponse> result = scheduleService.findSchedules(filter);

        assertEquals(1, result.size());
        assertEquals("数学", result.get(0).getCourseName());
    }

    @Test
    void testApiInput_FilterSchedules_InvalidFormat_ThrowsException() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));

        CourseScheduleFilterRequest filter = createFilter(null, null, "不是合法时间段");

        InvalidTimeSlotException exception = assertThrows(InvalidTimeSlotException.class, () -> {
            scheduleService.findSchedules(filter);
        });
        assertTrue(exception.getMessage().contains("格式不合法"));
    }

    @Test
    void testApiInput_DifferentFormatsSameTimeSlot_ConflictDetected() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));

        CourseScheduleCreateRequest request1 = createRequest("物理", "李老师", "A101", "周一 8:00-10:00");
        assertThrows(ClassroomConflictException.class, () -> scheduleService.addSchedule(request1));

        CourseScheduleCreateRequest request2 = createRequest("化学", "王老师", "A101", "星期一 08:00-10:00");
        assertThrows(ClassroomConflictException.class, () -> scheduleService.addSchedule(request2));

        CourseScheduleCreateRequest request3 = createRequest("生物", "赵老师", "A101", "周1 08:00-10:00");
        assertThrows(ClassroomConflictException.class, () -> scheduleService.addSchedule(request3));
    }

    private CourseScheduleFilterRequest createFilterWithCourseName(
            String courseName, String teacherName, String classroom, String timeSlot) {
        CourseScheduleFilterRequest filter = new CourseScheduleFilterRequest();
        filter.setCourseName(courseName);
        filter.setTeacherName(teacherName);
        filter.setClassroom(classroom);
        filter.setTimeSlot(timeSlot);
        return filter;
    }

    private CourseScheduleFilterRequest createFilterWithCourseNameAndSort(
            String courseName, String teacherName, String classroom, String timeSlot,
            String sortBy, String sortDirection) {
        CourseScheduleFilterRequest filter = new CourseScheduleFilterRequest();
        filter.setCourseName(courseName);
        filter.setTeacherName(teacherName);
        filter.setClassroom(classroom);
        filter.setTimeSlot(timeSlot);
        filter.setSortBy(sortBy);
        filter.setSortDirection(sortDirection);
        return filter;
    }

    private void addCourseNameSampleData() {
        scheduleService.addSchedule(createRequest("高等数学", "张老师", "A101", "周一 08:00-10:00"));
        scheduleService.addSchedule(createRequest("线性代数", "李老师", "B202", "周一 10:00-12:00"));
        scheduleService.addSchedule(createRequest("数学分析", "张老师", "A102", "周二 08:00-10:00"));
        scheduleService.addSchedule(createRequest("大学物理", "王教授", "C303", "周二 14:00-16:00"));
        scheduleService.addSchedule(createRequest("物理实验", "王教授", "实验室1", "周三 09:00-11:00"));
    }

    @Test
    void testFilterSchedules_ByCourseName_Only() {
        addCourseNameSampleData();

        CourseScheduleFilterRequest filter = createFilterWithCourseName("数学", null, null, null);
        List<CourseScheduleResponse> result = scheduleService.findSchedules(filter);

        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(r -> r.getCourseName().contains("数学")));
    }

    @Test
    void testFilterSchedules_ByCourseNameAndTeacher() {
        addCourseNameSampleData();

        CourseScheduleFilterRequest filter = createFilterWithCourseName("数学", "张", null, null);
        List<CourseScheduleResponse> result = scheduleService.findSchedules(filter);

        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(r -> r.getCourseName().contains("数学")));
        assertTrue(result.stream().allMatch(r -> r.getTeacherName().contains("张")));
    }

    @Test
    void testFilterSchedules_ByCourseName_WithSort() {
        addCourseNameSampleData();

        CourseScheduleFilterRequest filter = createFilterWithCourseNameAndSort(
                "物", null, null, null, "courseName", "asc");
        List<CourseScheduleResponse> result = scheduleService.findSchedules(filter);

        assertEquals(2, result.size());
        assertEquals("大学物理", result.get(0).getCourseName());
        assertEquals("物理实验", result.get(1).getCourseName());
    }

    @Test
    void testFilterSchedules_BlankCourseName_NoEffect() {
        addCourseNameSampleData();

        CourseScheduleFilterRequest filter = createFilterWithCourseName("   ", null, null, null);
        List<CourseScheduleResponse> result = scheduleService.findSchedules(filter);

        assertEquals(5, result.size());
    }

    @Test
    void testFilterSchedules_EmptyCourseName_NoEffect() {
        addCourseNameSampleData();

        CourseScheduleFilterRequest filter = createFilterWithCourseName("", null, null, null);
        List<CourseScheduleResponse> result = scheduleService.findSchedules(filter);

        assertEquals(5, result.size());
    }

    @Test
    void testFilterSchedules_ByCourseName_NoMatch_ReturnsEmpty() {
        addCourseNameSampleData();

        CourseScheduleFilterRequest filter = createFilterWithCourseName("化学", null, null, null);
        List<CourseScheduleResponse> result = scheduleService.findSchedules(filter);

        assertTrue(result.isEmpty());
    }

    @Test
    void testFilterSchedules_ByCourseName_TrimmedKeyword() {
        addCourseNameSampleData();

        CourseScheduleFilterRequest filter = createFilterWithCourseName("  代数  ", null, null, null);
        List<CourseScheduleResponse> result = scheduleService.findSchedules(filter);

        assertEquals(1, result.size());
        assertEquals("线性代数", result.get(0).getCourseName());
    }

    @Test
    void testFilterSchedules_ByCourseNameAndClassroom() {
        addCourseNameSampleData();

        CourseScheduleFilterRequest filter = createFilterWithCourseName("数学", null, "A101", null);
        List<CourseScheduleResponse> result = scheduleService.findSchedules(filter);

        assertEquals(1, result.size());
        assertEquals("高等数学", result.get(0).getCourseName());
        assertEquals("A101", result.get(0).getClassroom());
    }

    @Test
    void testFilterSchedules_ByCourseNameAndTimeSlot() {
        addCourseNameSampleData();

        CourseScheduleFilterRequest filter = createFilterWithCourseName("物", null, null, "周二 14:00-16:00");
        List<CourseScheduleResponse> result = scheduleService.findSchedules(filter);

        assertEquals(1, result.size());
        assertEquals("大学物理", result.get(0).getCourseName());
        assertEquals("周二 14:00-16:00", result.get(0).getTimeSlot());
    }

    @Test
    void testBatchAddSchedules_AllSuccess() {
        List<CourseScheduleCreateRequest> requests = new ArrayList<>();
        requests.add(createRequest("高等数学", "张教授", "A101", "周一 08:00-10:00"));
        requests.add(createRequest("大学物理", "李教授", "B202", "周二 14:00-16:00"));
        requests.add(createRequest("线性代数", "王教授", "C303", "周三 09:00-11:00"));

        CourseScheduleBatchResponse response = scheduleService.addSchedulesBatch(requests);

        assertEquals(3, response.getTotalCount());
        assertEquals(3, response.getSuccessCount());
        assertEquals(0, response.getFailureCount());
        assertEquals(3, response.getSuccessItems().size());
        assertEquals(0, response.getFailureItems().size());

        List<CourseScheduleResponse> all = scheduleService.findSchedules(null);
        assertEquals(3, all.size());
    }

    @Test
    void testBatchAddSchedules_PartialFailure() {
        List<CourseScheduleCreateRequest> requests = new ArrayList<>();
        requests.add(createRequest("高等数学", "张教授", "A101", "周一 08:00-10:00"));
        requests.add(createRequest("", "李教授", "B202", "周二 14:00-16:00"));
        requests.add(createRequest("线性代数", "王教授", "C303", "周三 09:00-11:00"));

        CourseScheduleBatchResponse response = scheduleService.addSchedulesBatch(requests);

        assertEquals(3, response.getTotalCount());
        assertEquals(2, response.getSuccessCount());
        assertEquals(1, response.getFailureCount());
        assertEquals(1, response.getFailureItems().size());
        assertEquals(1, response.getFailureItems().get(0).getIndex());
        assertEquals("课程名不能为空", response.getFailureItems().get(0).getErrorMessage());

        List<CourseScheduleResponse> all = scheduleService.findSchedules(null);
        assertEquals(2, all.size());
    }

    @Test
    void testBatchAddSchedules_InternalTeacherConflict() {
        List<CourseScheduleCreateRequest> requests = new ArrayList<>();
        requests.add(createRequest("高等数学", "张教授", "A101", "周一 08:00-10:00"));
        requests.add(createRequest("线性代数", "张教授", "B202", "周一 08:00-10:00"));

        CourseScheduleBatchResponse response = scheduleService.addSchedulesBatch(requests);

        assertEquals(2, response.getTotalCount());
        assertEquals(1, response.getSuccessCount());
        assertEquals(1, response.getFailureCount());
        assertEquals(1, response.getFailureItems().get(0).getIndex());
        assertTrue(response.getFailureItems().get(0).getErrorMessage().contains("与本次批量新增的其他课程冲突"));

        List<CourseScheduleResponse> all = scheduleService.findSchedules(null);
        assertEquals(1, all.size());
    }

    @Test
    void testBatchAddSchedules_InternalClassroomConflict() {
        List<CourseScheduleCreateRequest> requests = new ArrayList<>();
        requests.add(createRequest("高等数学", "张教授", "A101", "周一 08:00-10:00"));
        requests.add(createRequest("线性代数", "李教授", "A101", "周一 08:00-10:00"));

        CourseScheduleBatchResponse response = scheduleService.addSchedulesBatch(requests);

        assertEquals(2, response.getTotalCount());
        assertEquals(1, response.getSuccessCount());
        assertEquals(1, response.getFailureCount());
        assertTrue(response.getFailureItems().get(0).getErrorMessage().contains("与本次批量新增的其他课程冲突"));
    }

    @Test
    void testBatchAddSchedules_ConflictWithExisting() {
        scheduleService.addSchedule(createRequest("已有课程", "张教授", "A101", "周一 08:00-10:00"));

        List<CourseScheduleCreateRequest> requests = new ArrayList<>();
        requests.add(createRequest("高等数学", "张教授", "B202", "周二 14:00-16:00"));
        requests.add(createRequest("线性代数", "张教授", "C303", "周一 08:00-10:00"));

        CourseScheduleBatchResponse response = scheduleService.addSchedulesBatch(requests);

        assertEquals(2, response.getTotalCount());
        assertEquals(1, response.getSuccessCount());
        assertEquals(1, response.getFailureCount());
        assertTrue(response.getFailureItems().get(0).getErrorMessage().contains("已有课程安排"));

        List<CourseScheduleResponse> all = scheduleService.findSchedules(null);
        assertEquals(2, all.size());
    }

    @Test
    void testBatchAddSchedules_InvalidTimeSlot() {
        List<CourseScheduleCreateRequest> requests = new ArrayList<>();
        requests.add(createRequest("高等数学", "张教授", "A101", "周一 08:00-10:00"));
        requests.add(createRequest("线性代数", "李教授", "B202", "明天上午"));

        CourseScheduleBatchResponse response = scheduleService.addSchedulesBatch(requests);

        assertEquals(2, response.getTotalCount());
        assertEquals(1, response.getSuccessCount());
        assertEquals(1, response.getFailureCount());
        assertTrue(response.getFailureItems().get(0).getErrorMessage().contains("格式不合法"));
    }

    @Test
    void testBatchAddSchedules_ListWithNullElement() {
        List<CourseScheduleCreateRequest> requests = new ArrayList<>();
        requests.add(createRequest("高等数学", "张教授", "A101", "周一 08:00-10:00"));
        requests.add(null);
        requests.add(createRequest("线性代数", "李教授", "B202", "周二 14:00-16:00"));

        CourseScheduleBatchResponse response = scheduleService.addSchedulesBatch(requests);

        assertEquals(3, response.getTotalCount());
        assertEquals(2, response.getSuccessCount());
        assertEquals(1, response.getFailureCount());
        assertEquals(1, response.getFailureItems().get(0).getIndex());
        assertEquals("课程请求不能为空", response.getFailureItems().get(0).getErrorMessage());
    }

    @Test
    void testBatchAddSchedules_EmptyList() {
        List<CourseScheduleCreateRequest> requests = new ArrayList<>();

        CourseScheduleBatchResponse response = scheduleService.addSchedulesBatch(requests);

        assertEquals(0, response.getTotalCount());
        assertEquals(0, response.getSuccessCount());
        assertEquals(0, response.getFailureCount());
        assertEquals(0, response.getSuccessItems().size());
        assertEquals(0, response.getFailureItems().size());
    }

    @Test
    void testBatchAddSchedules_NullList_ThrowsException() {
        assertThrows(InvalidRequestParameterException.class, () -> {
            scheduleService.addSchedulesBatch(null);
        });
    }

    @Test
    void testBatchDeleteSchedules_AllSuccess() {
        CourseScheduleResponse s1 = scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));
        CourseScheduleResponse s2 = scheduleService.addSchedule(createRequest("物理", "李老师", "B202", "周二 14:00-16:00"));
        CourseScheduleResponse s3 = scheduleService.addSchedule(createRequest("化学", "王老师", "C303", "周三 09:00-11:00"));

        List<Long> ids = new ArrayList<>();
        ids.add(s1.getId());
        ids.add(s2.getId());
        ids.add(s3.getId());

        CourseScheduleBatchDeleteResponse response = scheduleService.deleteSchedulesBatch(ids);

        assertEquals(3, response.getTotalCount());
        assertEquals(3, response.getSuccessCount());
        assertEquals(0, response.getFailureCount());
        assertEquals(3, response.getSuccessItems().size());
        assertEquals(0, response.getFailureItems().size());

        List<CourseScheduleResponse> all = scheduleService.findSchedules(null);
        assertEquals(0, all.size());

        CourseScheduleStatisticsResponse stats = scheduleService.getStatistics();
        assertEquals(0, stats.getTotalCourses());
    }

    @Test
    void testBatchDeleteSchedules_PartialNotFound() {
        CourseScheduleResponse s1 = scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));
        CourseScheduleResponse s2 = scheduleService.addSchedule(createRequest("物理", "李老师", "B202", "周二 14:00-16:00"));

        List<Long> ids = new ArrayList<>();
        ids.add(s1.getId());
        ids.add(999L);
        ids.add(s2.getId());
        ids.add(888L);

        CourseScheduleBatchDeleteResponse response = scheduleService.deleteSchedulesBatch(ids);

        assertEquals(4, response.getTotalCount());
        assertEquals(2, response.getSuccessCount());
        assertEquals(2, response.getFailureCount());
        assertEquals(2, response.getSuccessItems().size());
        assertEquals(2, response.getFailureItems().size());

        assertEquals(999L, response.getFailureItems().get(0).getId());
        assertEquals("课程安排不存在", response.getFailureItems().get(0).getErrorMessage());
        assertEquals(888L, response.getFailureItems().get(1).getId());
        assertEquals("课程安排不存在", response.getFailureItems().get(1).getErrorMessage());

        List<CourseScheduleResponse> all = scheduleService.findSchedules(null);
        assertEquals(0, all.size());
    }

    @Test
    void testBatchDeleteSchedules_AllFailure() {
        List<Long> ids = new ArrayList<>();
        ids.add(999L);
        ids.add(888L);
        ids.add(777L);

        CourseScheduleBatchDeleteResponse response = scheduleService.deleteSchedulesBatch(ids);

        assertEquals(3, response.getTotalCount());
        assertEquals(0, response.getSuccessCount());
        assertEquals(3, response.getFailureCount());
        assertEquals(0, response.getSuccessItems().size());
        assertEquals(3, response.getFailureItems().size());
    }

    @Test
    void testBatchDeleteSchedules_DuplicateIds() {
        CourseScheduleResponse s1 = scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));
        CourseScheduleResponse s2 = scheduleService.addSchedule(createRequest("物理", "李老师", "B202", "周二 14:00-16:00"));

        List<Long> ids = new ArrayList<>();
        ids.add(s1.getId());
        ids.add(s2.getId());
        ids.add(s1.getId());
        ids.add(s2.getId());
        ids.add(s1.getId());

        CourseScheduleBatchDeleteResponse response = scheduleService.deleteSchedulesBatch(ids);

        assertEquals(5, response.getTotalCount());
        assertEquals(2, response.getSuccessCount());
        assertEquals(3, response.getFailureCount());
        assertEquals(2, response.getSuccessItems().size());
        assertEquals(3, response.getFailureItems().size());
        assertEquals(s1.getId(), response.getFailureItems().get(0).getId());
        assertEquals("重复 id 已忽略", response.getFailureItems().get(0).getErrorMessage());
        assertEquals(s2.getId(), response.getFailureItems().get(1).getId());
        assertEquals("重复 id 已忽略", response.getFailureItems().get(1).getErrorMessage());
        assertEquals(s1.getId(), response.getFailureItems().get(2).getId());
        assertEquals("重复 id 已忽略", response.getFailureItems().get(2).getErrorMessage());
        assertEquals(response.getTotalCount(), response.getSuccessCount() + response.getFailureCount());

        List<CourseScheduleResponse> all = scheduleService.findSchedules(null);
        assertEquals(0, all.size());

        CourseScheduleStatisticsResponse stats = scheduleService.getStatistics();
        assertEquals(0, stats.getTotalCourses());
    }

    @Test
    void testBatchDeleteSchedules_EmptyList() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));

        List<Long> ids = new ArrayList<>();

        CourseScheduleBatchDeleteResponse response = scheduleService.deleteSchedulesBatch(ids);

        assertEquals(0, response.getTotalCount());
        assertEquals(0, response.getSuccessCount());
        assertEquals(0, response.getFailureCount());
        assertEquals(0, response.getSuccessItems().size());
        assertEquals(0, response.getFailureItems().size());

        List<CourseScheduleResponse> all = scheduleService.findSchedules(null);
        assertEquals(1, all.size());
    }

    @Test
    void testBatchDeleteSchedules_NullList() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));

        assertThrows(InvalidRequestParameterException.class, () -> {
            scheduleService.deleteSchedulesBatch(null);
        });

        List<AuditLogResponse> logs = auditLogService.getAllLogs();
        assertEquals(2, logs.size());
        AuditLogResponse failureLog = logs.get(0);
        assertEquals(OperationType.BATCH_DELETE, failureLog.getOperationType());
        assertFalse(failureLog.isSuccess());
        assertEquals("请求列表不能为空", failureLog.getErrorMessage());

        List<CourseScheduleResponse> all = scheduleService.findSchedules(null);
        assertEquals(1, all.size());
    }

    @Test
    void testBatchDeleteSchedules_NegativeIds() {
        CourseScheduleResponse s1 = scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));

        List<Long> ids = new ArrayList<>();
        ids.add(s1.getId());
        ids.add(-1L);
        ids.add(-5L);
        ids.add(0L);

        CourseScheduleBatchDeleteResponse response = scheduleService.deleteSchedulesBatch(ids);

        assertEquals(4, response.getTotalCount());
        assertEquals(1, response.getSuccessCount());
        assertEquals(3, response.getFailureCount());
        assertEquals(1, response.getSuccessItems().size());
        assertEquals(3, response.getFailureItems().size());

        assertEquals(-1L, response.getFailureItems().get(0).getId());
        assertEquals("无效的课程 ID", response.getFailureItems().get(0).getErrorMessage());
        assertEquals(-5L, response.getFailureItems().get(1).getId());
        assertEquals("无效的课程 ID", response.getFailureItems().get(1).getErrorMessage());
        assertEquals(0L, response.getFailureItems().get(2).getId());
        assertEquals("无效的课程 ID", response.getFailureItems().get(2).getErrorMessage());

        List<CourseScheduleResponse> all = scheduleService.findSchedules(null);
        assertEquals(0, all.size());
    }

    @Test
    void testBatchDeleteSchedules_ListAndStatisticsUpdated() {
        CourseScheduleResponse s1 = scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));
        CourseScheduleResponse s2 = scheduleService.addSchedule(createRequest("物理", "李老师", "B202", "周二 14:00-16:00"));
        CourseScheduleResponse s3 = scheduleService.addSchedule(createRequest("化学", "张老师", "C303", "周三 09:00-11:00"));

        List<Long> ids = new ArrayList<>();
        ids.add(s2.getId());

        CourseScheduleBatchDeleteResponse response = scheduleService.deleteSchedulesBatch(ids);

        assertEquals(1, response.getSuccessCount());

        List<CourseScheduleResponse> all = scheduleService.findSchedules(null);
        assertEquals(2, all.size());
        assertEquals("数学", all.get(0).getCourseName());
        assertEquals("化学", all.get(1).getCourseName());

        CourseScheduleStatisticsResponse stats = scheduleService.getStatistics();
        assertEquals(2, stats.getTotalCourses());
        assertEquals(1, stats.getTeacherCount());
        assertEquals(2, stats.getClassroomCount());
    }

    @Test
    void testBatchDeleteSchedules_MixedScenarios() {
        CourseScheduleResponse s1 = scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));
        CourseScheduleResponse s2 = scheduleService.addSchedule(createRequest("物理", "李老师", "B202", "周二 14:00-16:00"));

        List<Long> ids = new ArrayList<>();
        ids.add(s1.getId());
        ids.add(-1L);
        ids.add(999L);
        ids.add(s2.getId());
        ids.add(s1.getId());
        ids.add(0L);

        CourseScheduleBatchDeleteResponse response = scheduleService.deleteSchedulesBatch(ids);

        assertEquals(6, response.getTotalCount());
        assertEquals(2, response.getSuccessCount());
        assertEquals(4, response.getFailureCount());
        assertEquals(2, response.getSuccessItems().size());
        assertEquals(4, response.getFailureItems().size());
        assertEquals(-1L, response.getFailureItems().get(0).getId());
        assertEquals("无效的课程 ID", response.getFailureItems().get(0).getErrorMessage());
        assertEquals(999L, response.getFailureItems().get(1).getId());
        assertEquals("课程安排不存在", response.getFailureItems().get(1).getErrorMessage());
        assertEquals(s1.getId(), response.getFailureItems().get(2).getId());
        assertEquals("重复 id 已忽略", response.getFailureItems().get(2).getErrorMessage());
        assertEquals(0L, response.getFailureItems().get(3).getId());
        assertEquals("无效的课程 ID", response.getFailureItems().get(3).getErrorMessage());
        assertEquals(response.getTotalCount(), response.getSuccessCount() + response.getFailureCount());

        List<CourseScheduleResponse> all = scheduleService.findSchedules(null);
        assertEquals(0, all.size());
    }

    @Test
    void testBatchDeleteSchedules_DuplicateNegativeIds() {
        List<Long> ids = new ArrayList<>();
        ids.add(-1L);
        ids.add(-1L);
        ids.add(-5L);
        ids.add(-5L);
        ids.add(-1L);

        CourseScheduleBatchDeleteResponse response = scheduleService.deleteSchedulesBatch(ids);

        assertEquals(5, response.getTotalCount());
        assertEquals(0, response.getSuccessCount());
        assertEquals(5, response.getFailureCount());
        assertEquals(0, response.getSuccessItems().size());
        assertEquals(5, response.getFailureItems().size());

        for (CourseScheduleBatchDeleteFailure failure : response.getFailureItems()) {
            assertEquals("无效的课程 ID", failure.getErrorMessage());
        }
        assertEquals(-1L, response.getFailureItems().get(0).getId());
        assertEquals(-1L, response.getFailureItems().get(1).getId());
        assertEquals(-5L, response.getFailureItems().get(2).getId());
        assertEquals(-5L, response.getFailureItems().get(3).getId());
        assertEquals(-1L, response.getFailureItems().get(4).getId());
        assertEquals(response.getTotalCount(), response.getSuccessCount() + response.getFailureCount());
    }

    @Test
    void testBatchDeleteSchedules_DuplicateZeroIds() {
        List<Long> ids = new ArrayList<>();
        ids.add(0L);
        ids.add(0L);
        ids.add(0L);

        CourseScheduleBatchDeleteResponse response = scheduleService.deleteSchedulesBatch(ids);

        assertEquals(3, response.getTotalCount());
        assertEquals(0, response.getSuccessCount());
        assertEquals(3, response.getFailureCount());
        assertEquals(0, response.getSuccessItems().size());
        assertEquals(3, response.getFailureItems().size());

        for (CourseScheduleBatchDeleteFailure failure : response.getFailureItems()) {
            assertEquals(0L, failure.getId());
            assertEquals("无效的课程 ID", failure.getErrorMessage());
        }
        assertEquals(response.getTotalCount(), response.getSuccessCount() + response.getFailureCount());
    }

    @Test
    void testBatchDeleteSchedules_MixedInvalidDuplicates() {
        List<Long> ids = new ArrayList<>();
        ids.add(-1L);
        ids.add(0L);
        ids.add(-1L);
        ids.add(0L);
        ids.add(-10L);
        ids.add(0L);

        CourseScheduleBatchDeleteResponse response = scheduleService.deleteSchedulesBatch(ids);

        assertEquals(6, response.getTotalCount());
        assertEquals(0, response.getSuccessCount());
        assertEquals(6, response.getFailureCount());
        assertEquals(0, response.getSuccessItems().size());
        assertEquals(6, response.getFailureItems().size());

        for (CourseScheduleBatchDeleteFailure failure : response.getFailureItems()) {
            assertEquals("无效的课程 ID", failure.getErrorMessage());
        }
        assertEquals(response.getTotalCount(), response.getSuccessCount() + response.getFailureCount());
    }

    @Test
    void testBatchDeleteSchedules_InvalidDuplicatesPlusValidDuplicates() {
        CourseScheduleResponse s1 = scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));

        List<Long> ids = new ArrayList<>();
        ids.add(-1L);
        ids.add(s1.getId());
        ids.add(-1L);
        ids.add(0L);
        ids.add(s1.getId());
        ids.add(0L);
        ids.add(s1.getId());

        CourseScheduleBatchDeleteResponse response = scheduleService.deleteSchedulesBatch(ids);

        assertEquals(7, response.getTotalCount());
        assertEquals(1, response.getSuccessCount());
        assertEquals(6, response.getFailureCount());
        assertEquals(1, response.getSuccessItems().size());
        assertEquals(6, response.getFailureItems().size());

        assertEquals(-1L, response.getFailureItems().get(0).getId());
        assertEquals("无效的课程 ID", response.getFailureItems().get(0).getErrorMessage());
        assertEquals(-1L, response.getFailureItems().get(1).getId());
        assertEquals("无效的课程 ID", response.getFailureItems().get(1).getErrorMessage());
        assertEquals(0L, response.getFailureItems().get(2).getId());
        assertEquals("无效的课程 ID", response.getFailureItems().get(2).getErrorMessage());
        assertEquals(s1.getId(), response.getFailureItems().get(3).getId());
        assertEquals("重复 id 已忽略", response.getFailureItems().get(3).getErrorMessage());
        assertEquals(0L, response.getFailureItems().get(4).getId());
        assertEquals("无效的课程 ID", response.getFailureItems().get(4).getErrorMessage());
        assertEquals(s1.getId(), response.getFailureItems().get(5).getId());
        assertEquals("重复 id 已忽略", response.getFailureItems().get(5).getErrorMessage());
        assertEquals(response.getTotalCount(), response.getSuccessCount() + response.getFailureCount());

        List<CourseScheduleResponse> all = scheduleService.findSchedules(null);
        assertEquals(0, all.size());
    }

    @Test
    void testGetWeekdayStatistics_EmptyData() {
        CourseScheduleWeekdayStatisticsResponse stats = scheduleService.getWeekdayStatistics();

        assertEquals(0, stats.getMonday());
        assertEquals(0, stats.getTuesday());
        assertEquals(0, stats.getWednesday());
        assertEquals(0, stats.getThursday());
        assertEquals(0, stats.getFriday());
        assertEquals(0, stats.getSaturday());
        assertEquals(0, stats.getSunday());
    }

    @Test
    void testGetWeekdayStatistics_MultipleWeekdays() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));
        scheduleService.addSchedule(createRequest("物理", "李老师", "B202", "周一 10:00-12:00"));
        scheduleService.addSchedule(createRequest("化学", "王老师", "C303", "周二 08:00-10:00"));
        scheduleService.addSchedule(createRequest("英语", "赵老师", "D404", "周三 14:00-16:00"));
        scheduleService.addSchedule(createRequest("生物", "孙老师", "E505", "周五 09:00-11:00"));

        CourseScheduleWeekdayStatisticsResponse stats = scheduleService.getWeekdayStatistics();

        assertEquals(2, stats.getMonday());
        assertEquals(1, stats.getTuesday());
        assertEquals(1, stats.getWednesday());
        assertEquals(0, stats.getThursday());
        assertEquals(1, stats.getFriday());
        assertEquals(0, stats.getSaturday());
        assertEquals(0, stats.getSunday());
    }

    @Test
    void testGetWeekdayStatistics_AfterUpdate_ChangesAccordingly() {
        CourseScheduleResponse added = scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));

        CourseScheduleWeekdayStatisticsResponse statsBefore = scheduleService.getWeekdayStatistics();
        assertEquals(1, statsBefore.getMonday());
        assertEquals(0, statsBefore.getTuesday());

        CourseScheduleUpdateRequest updateRequest = createUpdateRequest("高等数学", "张老师", "A101", "周二 08:00-10:00");
        scheduleService.updateSchedule(added.getId(), updateRequest);

        CourseScheduleWeekdayStatisticsResponse statsAfter = scheduleService.getWeekdayStatistics();
        assertEquals(0, statsAfter.getMonday());
        assertEquals(1, statsAfter.getTuesday());
    }

    @Test
    void testGetWeekdayStatistics_AfterDelete_ChangesAccordingly() {
        CourseScheduleResponse s1 = scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));
        scheduleService.addSchedule(createRequest("物理", "李老师", "B202", "周一 10:00-12:00"));

        CourseScheduleWeekdayStatisticsResponse statsBefore = scheduleService.getWeekdayStatistics();
        assertEquals(2, statsBefore.getMonday());

        scheduleService.deleteSchedule(s1.getId());

        CourseScheduleWeekdayStatisticsResponse statsAfter = scheduleService.getWeekdayStatistics();
        assertEquals(1, statsAfter.getMonday());
    }

    @Test
    void testGetWeekdayStatistics_AfterBatchDelete_ChangesAccordingly() {
        CourseScheduleResponse s1 = scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));
        CourseScheduleResponse s2 = scheduleService.addSchedule(createRequest("物理", "李老师", "B202", "周二 14:00-16:00"));
        CourseScheduleResponse s3 = scheduleService.addSchedule(createRequest("化学", "王老师", "C303", "周一 10:00-12:00"));
        scheduleService.addSchedule(createRequest("英语", "赵老师", "D404", "周三 09:00-11:00"));

        CourseScheduleWeekdayStatisticsResponse statsBefore = scheduleService.getWeekdayStatistics();
        assertEquals(2, statsBefore.getMonday());
        assertEquals(1, statsBefore.getTuesday());
        assertEquals(1, statsBefore.getWednesday());

        List<Long> ids = new ArrayList<>();
        ids.add(s1.getId());
        ids.add(s2.getId());
        ids.add(s3.getId());
        scheduleService.deleteSchedulesBatch(ids);

        CourseScheduleWeekdayStatisticsResponse statsAfter = scheduleService.getWeekdayStatistics();
        assertEquals(0, statsAfter.getMonday());
        assertEquals(0, statsAfter.getTuesday());
        assertEquals(1, statsAfter.getWednesday());
    }

    @Test
    void testGetTeacherCourseStatistics_EmptyData() {
        List<TeacherCourseStatisticsResponse> stats = scheduleService.getTeacherCourseStatistics();
        assertNotNull(stats);
        assertTrue(stats.isEmpty());
    }

    @Test
    void testGetTeacherCourseStatistics_MultipleTeachers_SortedByCountDesc() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));
        scheduleService.addSchedule(createRequest("物理", "张老师", "B202", "周一 10:00-12:00"));
        scheduleService.addSchedule(createRequest("化学", "张老师", "C303", "周二 08:00-10:00"));
        scheduleService.addSchedule(createRequest("英语", "李老师", "D404", "周二 10:00-12:00"));
        scheduleService.addSchedule(createRequest("生物", "李老师", "E505", "周三 08:00-10:00"));
        scheduleService.addSchedule(createRequest("历史", "王老师", "F606", "周三 10:00-12:00"));

        List<TeacherCourseStatisticsResponse> stats = scheduleService.getTeacherCourseStatistics();

        assertEquals(3, stats.size());
        assertEquals("张老师", stats.get(0).getTeacherName());
        assertEquals(3, stats.get(0).getCourseCount());
        assertEquals("李老师", stats.get(1).getTeacherName());
        assertEquals(2, stats.get(1).getCourseCount());
        assertEquals("王老师", stats.get(2).getTeacherName());
        assertEquals(1, stats.get(2).getCourseCount());
    }

    @Test
    void testGetTeacherCourseStatistics_SameCount_SortedByNameAsc() {
        scheduleService.addSchedule(createRequest("数学", "李老师", "A101", "周一 08:00-10:00"));
        scheduleService.addSchedule(createRequest("物理", "王老师", "B202", "周一 10:00-12:00"));
        scheduleService.addSchedule(createRequest("化学", "张老师", "C303", "周二 08:00-10:00"));

        List<TeacherCourseStatisticsResponse> stats = scheduleService.getTeacherCourseStatistics();

        assertEquals(3, stats.size());
        assertEquals(1, stats.get(0).getCourseCount());
        assertEquals(1, stats.get(1).getCourseCount());
        assertEquals(1, stats.get(2).getCourseCount());
        assertEquals("张老师", stats.get(0).getTeacherName());
        assertEquals("李老师", stats.get(1).getTeacherName());
        assertEquals("王老师", stats.get(2).getTeacherName());
    }

    @Test
    void testGetTeacherCourseStatistics_AfterUpdateTeacher_ChangesAccordingly() {
        CourseScheduleResponse added = scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));
        scheduleService.addSchedule(createRequest("物理", "李老师", "B202", "周二 08:00-10:00"));

        List<TeacherCourseStatisticsResponse> statsBefore = scheduleService.getTeacherCourseStatistics();
        assertEquals(2, statsBefore.size());
        assertEquals("张老师", statsBefore.get(0).getTeacherName());
        assertEquals(1, statsBefore.get(0).getCourseCount());

        CourseScheduleUpdateRequest updateRequest = createUpdateRequest("数学", "李老师", "A101", "周一 08:00-10:00");
        scheduleService.updateSchedule(added.getId(), updateRequest);

        List<TeacherCourseStatisticsResponse> statsAfter = scheduleService.getTeacherCourseStatistics();
        assertEquals(1, statsAfter.size());
        assertEquals("李老师", statsAfter.get(0).getTeacherName());
        assertEquals(2, statsAfter.get(0).getCourseCount());
    }

    @Test
    void testGetTeacherCourseStatistics_AfterDelete_ChangesAccordingly() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));
        CourseScheduleResponse toDelete = scheduleService.addSchedule(createRequest("物理", "李老师", "B202", "周二 08:00-10:00"));
        scheduleService.addSchedule(createRequest("化学", "张老师", "C303", "周三 08:00-10:00"));

        List<TeacherCourseStatisticsResponse> statsBefore = scheduleService.getTeacherCourseStatistics();
        assertEquals(2, statsBefore.size());
        assertEquals("张老师", statsBefore.get(0).getTeacherName());
        assertEquals(2, statsBefore.get(0).getCourseCount());
        assertEquals("李老师", statsBefore.get(1).getTeacherName());
        assertEquals(1, statsBefore.get(1).getCourseCount());

        scheduleService.deleteSchedule(toDelete.getId());

        List<TeacherCourseStatisticsResponse> statsAfter = scheduleService.getTeacherCourseStatistics();
        assertEquals(1, statsAfter.size());
        assertEquals("张老师", statsAfter.get(0).getTeacherName());
        assertEquals(2, statsAfter.get(0).getCourseCount());
    }

    @Test
    void testGetTeacherCourseStatistics_AfterBatchDelete_ChangesAccordingly() {
        CourseScheduleResponse s1 = scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));
        CourseScheduleResponse s2 = scheduleService.addSchedule(createRequest("物理", "李老师", "B202", "周二 08:00-10:00"));
        scheduleService.addSchedule(createRequest("化学", "张老师", "C303", "周三 08:00-10:00"));
        CourseScheduleResponse s4 = scheduleService.addSchedule(createRequest("英语", "王老师", "D404", "周四 08:00-10:00"));

        List<TeacherCourseStatisticsResponse> statsBefore = scheduleService.getTeacherCourseStatistics();
        assertEquals(3, statsBefore.size());

        List<Long> ids = new ArrayList<>();
        ids.add(s1.getId());
        ids.add(s2.getId());
        ids.add(s4.getId());
        scheduleService.deleteSchedulesBatch(ids);

        List<TeacherCourseStatisticsResponse> statsAfter = scheduleService.getTeacherCourseStatistics();
        assertEquals(1, statsAfter.size());
        assertEquals("张老师", statsAfter.get(0).getTeacherName());
        assertEquals(1, statsAfter.get(0).getCourseCount());
    }

    @Test
    void testAddSchedule_WithWhitespace_FieldsTrimmed() {
        CourseScheduleCreateRequest request = createRequest("  高等数学  ", "  张教授  ", "  A101  ", "周一 08:00-10:00");

        CourseScheduleResponse result = scheduleService.addSchedule(request);

        assertEquals("高等数学", result.getCourseName());
        assertEquals("张教授", result.getTeacherName());
        assertEquals("A101", result.getClassroom());
    }

    @Test
    void testUpdateSchedule_WithWhitespace_FieldsTrimmed() {
        CourseScheduleResponse added = scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));

        CourseScheduleUpdateRequest updateRequest = createUpdateRequest("  线性代数  ", "  李教授  ", "  B202  ", "周二 14:00-16:00");
        CourseScheduleResponse result = scheduleService.updateSchedule(added.getId(), updateRequest);

        assertEquals("线性代数", result.getCourseName());
        assertEquals("李教授", result.getTeacherName());
        assertEquals("B202", result.getClassroom());
    }

    @Test
    void testAddSchedule_TeacherNameWithWhitespace_StatisticsMerged() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));
        scheduleService.addSchedule(createRequest("物理", "  张老师  ", "B202", "周一 10:00-12:00"));
        scheduleService.addSchedule(createRequest("化学", "  张老师", "C303", "周二 08:00-10:00"));

        List<TeacherCourseStatisticsResponse> stats = scheduleService.getTeacherCourseStatistics();

        assertEquals(1, stats.size());
        assertEquals("张老师", stats.get(0).getTeacherName());
        assertEquals(3, stats.get(0).getCourseCount());
    }

    @Test
    void testUpdateSchedule_TeacherNameWithWhitespace_StatisticsMerged() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));
        CourseScheduleResponse toUpdate = scheduleService.addSchedule(createRequest("物理", "李老师", "B202", "周二 08:00-10:00"));

        List<TeacherCourseStatisticsResponse> statsBefore = scheduleService.getTeacherCourseStatistics();
        assertEquals(2, statsBefore.size());

        CourseScheduleUpdateRequest updateRequest = createUpdateRequest("物理", "  张老师  ", "B202", "周二 08:00-10:00");
        scheduleService.updateSchedule(toUpdate.getId(), updateRequest);

        List<TeacherCourseStatisticsResponse> statsAfter = scheduleService.getTeacherCourseStatistics();
        assertEquals(1, statsAfter.size());
        assertEquals("张老师", statsAfter.get(0).getTeacherName());
        assertEquals(2, statsAfter.get(0).getCourseCount());
    }

    @Test
    void testBatchAddSchedules_WithWhitespace_FieldsTrimmed() {
        List<CourseScheduleCreateRequest> requests = new ArrayList<>();
        requests.add(createRequest("  数学  ", "  张老师  ", "  A101  ", "周一 08:00-10:00"));
        requests.add(createRequest("  物理  ", "  李老师  ", "  B202  ", "周二 14:00-16:00"));

        CourseScheduleBatchResponse response = scheduleService.addSchedulesBatch(requests);

        assertEquals(2, response.getSuccessCount());
        assertEquals("数学", response.getSuccessItems().get(0).getCourseName());
        assertEquals("张老师", response.getSuccessItems().get(0).getTeacherName());
        assertEquals("A101", response.getSuccessItems().get(0).getClassroom());
        assertEquals("物理", response.getSuccessItems().get(1).getCourseName());
        assertEquals("李老师", response.getSuccessItems().get(1).getTeacherName());
        assertEquals("B202", response.getSuccessItems().get(1).getClassroom());

        List<TeacherCourseStatisticsResponse> stats = scheduleService.getTeacherCourseStatistics();
        assertEquals(2, stats.size());
    }

    @Test
    void testFindSchedules_AfterAddWithWhitespace_CanFindByTrimmedName() {
        scheduleService.addSchedule(createRequest("  高等数学  ", "  张教授  ", "  教学楼A101  ", "周一 08:00-10:00"));

        CourseScheduleFilterRequest filter = createFilter("张", "教学楼", null);
        List<CourseScheduleResponse> result = scheduleService.findSchedules(filter);

        assertEquals(1, result.size());
        assertEquals("高等数学", result.get(0).getCourseName());
        assertEquals("张教授", result.get(0).getTeacherName());
        assertEquals("教学楼A101", result.get(0).getClassroom());
    }

    @Test
    void testGetClassroomCourseStatistics_EmptyData() {
        List<ClassroomCourseStatisticsResponse> stats = scheduleService.getClassroomCourseStatistics();
        assertNotNull(stats);
        assertTrue(stats.isEmpty());
    }

    @Test
    void testGetClassroomCourseStatistics_MultipleClassrooms_SortedByCountDesc() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));
        scheduleService.addSchedule(createRequest("物理", "李老师", "A101", "周一 10:00-12:00"));
        scheduleService.addSchedule(createRequest("化学", "王老师", "A101", "周二 08:00-10:00"));
        scheduleService.addSchedule(createRequest("英语", "赵老师", "B202", "周三 14:00-16:00"));
        scheduleService.addSchedule(createRequest("生物", "钱老师", "B202", "周四 09:00-11:00"));
        scheduleService.addSchedule(createRequest("历史", "孙老师", "C303", "周五 08:00-10:00"));

        List<ClassroomCourseStatisticsResponse> stats = scheduleService.getClassroomCourseStatistics();

        assertEquals(3, stats.size());
        assertEquals("A101", stats.get(0).getClassroom());
        assertEquals(3, stats.get(0).getCourseCount());
        assertEquals("B202", stats.get(1).getClassroom());
        assertEquals(2, stats.get(1).getCourseCount());
        assertEquals("C303", stats.get(2).getClassroom());
        assertEquals(1, stats.get(2).getCourseCount());
    }

    @Test
    void testGetClassroomCourseStatistics_SameCount_SortedByClassroomNameAsc() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "C303", "周一 08:00-10:00"));
        scheduleService.addSchedule(createRequest("物理", "李老师", "A101", "周二 08:00-10:00"));
        scheduleService.addSchedule(createRequest("化学", "王老师", "B202", "周三 08:00-10:00"));

        List<ClassroomCourseStatisticsResponse> stats = scheduleService.getClassroomCourseStatistics();

        assertEquals(3, stats.size());
        assertEquals("A101", stats.get(0).getClassroom());
        assertEquals(1, stats.get(0).getCourseCount());
        assertEquals("B202", stats.get(1).getClassroom());
        assertEquals(1, stats.get(1).getCourseCount());
        assertEquals("C303", stats.get(2).getClassroom());
        assertEquals(1, stats.get(2).getCourseCount());
    }

    @Test
    void testGetClassroomCourseStatistics_AfterUpdateClassroom_ChangesAccordingly() {
        CourseScheduleResponse added = scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));
        scheduleService.addSchedule(createRequest("物理", "李老师", "B202", "周二 08:00-10:00"));

        List<ClassroomCourseStatisticsResponse> statsBefore = scheduleService.getClassroomCourseStatistics();
        assertEquals(2, statsBefore.size());
        assertEquals("A101", statsBefore.get(0).getClassroom());
        assertEquals(1, statsBefore.get(0).getCourseCount());

        CourseScheduleUpdateRequest updateRequest = createUpdateRequest("数学", "张老师", "B202", "周一 08:00-10:00");
        scheduleService.updateSchedule(added.getId(), updateRequest);

        List<ClassroomCourseStatisticsResponse> statsAfter = scheduleService.getClassroomCourseStatistics();
        assertEquals(1, statsAfter.size());
        assertEquals("B202", statsAfter.get(0).getClassroom());
        assertEquals(2, statsAfter.get(0).getCourseCount());
    }

    @Test
    void testGetClassroomCourseStatistics_AfterDelete_ChangesAccordingly() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));
        CourseScheduleResponse toDelete = scheduleService.addSchedule(createRequest("物理", "李老师", "B202", "周二 08:00-10:00"));
        scheduleService.addSchedule(createRequest("化学", "王老师", "A101", "周三 08:00-10:00"));

        List<ClassroomCourseStatisticsResponse> statsBefore = scheduleService.getClassroomCourseStatistics();
        assertEquals(2, statsBefore.size());
        assertEquals("A101", statsBefore.get(0).getClassroom());
        assertEquals(2, statsBefore.get(0).getCourseCount());
        assertEquals("B202", statsBefore.get(1).getClassroom());
        assertEquals(1, statsBefore.get(1).getCourseCount());

        scheduleService.deleteSchedule(toDelete.getId());

        List<ClassroomCourseStatisticsResponse> statsAfter = scheduleService.getClassroomCourseStatistics();
        assertEquals(1, statsAfter.size());
        assertEquals("A101", statsAfter.get(0).getClassroom());
        assertEquals(2, statsAfter.get(0).getCourseCount());
    }

    @Test
    void testGetClassroomCourseStatistics_AfterBatchDelete_ChangesAccordingly() {
        CourseScheduleResponse s1 = scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));
        CourseScheduleResponse s2 = scheduleService.addSchedule(createRequest("物理", "李老师", "B202", "周二 08:00-10:00"));
        scheduleService.addSchedule(createRequest("化学", "张老师", "A101", "周三 08:00-10:00"));
        CourseScheduleResponse s4 = scheduleService.addSchedule(createRequest("英语", "王老师", "C303", "周四 08:00-10:00"));

        List<ClassroomCourseStatisticsResponse> statsBefore = scheduleService.getClassroomCourseStatistics();
        assertEquals(3, statsBefore.size());

        List<Long> ids = new ArrayList<>();
        ids.add(s1.getId());
        ids.add(s2.getId());
        ids.add(s4.getId());
        scheduleService.deleteSchedulesBatch(ids);

        List<ClassroomCourseStatisticsResponse> statsAfter = scheduleService.getClassroomCourseStatistics();
        assertEquals(1, statsAfter.size());
        assertEquals("A101", statsAfter.get(0).getClassroom());
        assertEquals(1, statsAfter.get(0).getCourseCount());
    }

    @Test
    void testGetClassroomCourseStatistics_WithWhitespaceClassroom_Merged() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));
        scheduleService.addSchedule(createRequest("物理", "李老师", "  A101  ", "周一 10:00-12:00"));
        scheduleService.addSchedule(createRequest("化学", "王老师", "  A101", "周二 08:00-10:00"));

        List<ClassroomCourseStatisticsResponse> stats = scheduleService.getClassroomCourseStatistics();

        assertEquals(1, stats.size());
        assertEquals("A101", stats.get(0).getClassroom());
        assertEquals(3, stats.get(0).getCourseCount());
    }

    @Test
    void testGetClassroomCourseStatistics_UpdateWithWhitespaceClassroom_Merged() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));
        CourseScheduleResponse toUpdate = scheduleService.addSchedule(createRequest("物理", "李老师", "B202", "周二 08:00-10:00"));

        List<ClassroomCourseStatisticsResponse> statsBefore = scheduleService.getClassroomCourseStatistics();
        assertEquals(2, statsBefore.size());

        CourseScheduleUpdateRequest updateRequest = createUpdateRequest("物理", "李老师", "  A101  ", "周二 08:00-10:00");
        scheduleService.updateSchedule(toUpdate.getId(), updateRequest);

        List<ClassroomCourseStatisticsResponse> statsAfter = scheduleService.getClassroomCourseStatistics();
        assertEquals(1, statsAfter.size());
        assertEquals("A101", statsAfter.get(0).getClassroom());
        assertEquals(2, statsAfter.get(0).getCourseCount());
    }

    private CourseScheduleBatchTimeSlotUpdateRequest createTimeSlotUpdateRequest(String fromTimeSlot, String toTimeSlot) {
        CourseScheduleBatchTimeSlotUpdateRequest request = new CourseScheduleBatchTimeSlotUpdateRequest();
        request.setFromTimeSlot(fromTimeSlot);
        request.setToTimeSlot(toTimeSlot);
        return request;
    }

    @Test
    void testBatchUpdateTimeSlot_SingleCourse_Success() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));

        CourseScheduleBatchTimeSlotUpdateRequest request = createTimeSlotUpdateRequest(
                "周一 08:00-10:00", "周二 14:00-16:00");
        CourseScheduleBatchTimeSlotUpdateResponse response = scheduleService.batchUpdateTimeSlot(request);

        assertEquals(1, response.getMatchedCount());
        assertEquals(1, response.getUpdatedCount());
        assertEquals("周一 08:00-10:00", response.getFromTimeSlot());
        assertEquals("周二 14:00-16:00", response.getToTimeSlot());
        assertEquals(1, response.getUpdatedItems().size());
        assertEquals("周二 14:00-16:00", response.getUpdatedItems().get(0).getTimeSlot());

        List<CourseScheduleResponse> all = scheduleService.findSchedules(null);
        assertEquals(1, all.size());
        assertEquals("周二 14:00-16:00", all.get(0).getTimeSlot());
    }

    @Test
    void testBatchUpdateTimeSlot_MultipleCourses_Success() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));
        scheduleService.addSchedule(createRequest("物理", "李老师", "B202", "周一 08:00-10:00"));
        scheduleService.addSchedule(createRequest("化学", "王老师", "C303", "周三 09:00-11:00"));

        CourseScheduleBatchTimeSlotUpdateRequest request = createTimeSlotUpdateRequest(
                "周一 08:00-10:00", "周五 14:00-16:00");
        CourseScheduleBatchTimeSlotUpdateResponse response = scheduleService.batchUpdateTimeSlot(request);

        assertEquals(2, response.getMatchedCount());
        assertEquals(2, response.getUpdatedCount());
        assertEquals(2, response.getUpdatedItems().size());

        List<CourseScheduleResponse> all = scheduleService.findSchedules(null);
        assertEquals(3, all.size());
        for (CourseScheduleResponse s : all) {
            if (!s.getCourseName().equals("化学")) {
                assertEquals("周五 14:00-16:00", s.getTimeSlot());
            } else {
                assertEquals("周三 09:00-11:00", s.getTimeSlot());
            }
        }

        CourseScheduleFilterRequest filter = createFilter(null, null, "周五 14:00-16:00");
        List<CourseScheduleResponse> filtered = scheduleService.findSchedules(filter);
        assertEquals(2, filtered.size());
    }

    @Test
    void testBatchUpdateTimeSlot_NoMatch_ReturnsZero() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));

        CourseScheduleBatchTimeSlotUpdateRequest request = createTimeSlotUpdateRequest(
                "周二 14:00-16:00", "周三 09:00-11:00");
        CourseScheduleBatchTimeSlotUpdateResponse response = scheduleService.batchUpdateTimeSlot(request);

        assertEquals(0, response.getMatchedCount());
        assertEquals(0, response.getUpdatedCount());
        assertEquals(0, response.getUpdatedItems().size());

        List<CourseScheduleResponse> all = scheduleService.findSchedules(null);
        assertEquals(1, all.size());
        assertEquals("周一 08:00-10:00", all.get(0).getTimeSlot());
    }

    @Test
    void testBatchUpdateTimeSlot_EmptyData_ReturnsZero() {
        CourseScheduleBatchTimeSlotUpdateRequest request = createTimeSlotUpdateRequest(
                "周一 08:00-10:00", "周二 14:00-16:00");
        CourseScheduleBatchTimeSlotUpdateResponse response = scheduleService.batchUpdateTimeSlot(request);

        assertEquals(0, response.getMatchedCount());
        assertEquals(0, response.getUpdatedCount());
    }

    @Test
    void testBatchUpdateTimeSlot_SameTimeSlot_NoChange() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));
        scheduleService.addSchedule(createRequest("物理", "李老师", "B202", "周一 08:00-10:00"));

        CourseScheduleBatchTimeSlotUpdateRequest request = createTimeSlotUpdateRequest(
                "周一 08:00-10:00", "周一 08:00-10:00");
        CourseScheduleBatchTimeSlotUpdateResponse response = scheduleService.batchUpdateTimeSlot(request);

        assertEquals(0, response.getMatchedCount());
        assertEquals(0, response.getUpdatedCount());

        List<CourseScheduleResponse> all = scheduleService.findSchedules(null);
        assertEquals(2, all.size());
        assertEquals("周一 08:00-10:00", all.get(0).getTimeSlot());
        assertEquals("周一 08:00-10:00", all.get(1).getTimeSlot());
    }

    @Test
    void testBatchUpdateTimeSlot_NonStandardTimeFormat_Normalized() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));

        CourseScheduleBatchTimeSlotUpdateRequest request = createTimeSlotUpdateRequest(
                "星期一 8:00-10:00", "星期2 14:00-16:00");
        CourseScheduleBatchTimeSlotUpdateResponse response = scheduleService.batchUpdateTimeSlot(request);

        assertEquals(1, response.getMatchedCount());
        assertEquals(1, response.getUpdatedCount());
        assertEquals("周一 08:00-10:00", response.getFromTimeSlot());
        assertEquals("周二 14:00-16:00", response.getToTimeSlot());
        assertEquals("周二 14:00-16:00", response.getUpdatedItems().get(0).getTimeSlot());
    }

    @Test
    void testBatchUpdateTimeSlot_NonStandardFromTimeSlot_WithExtraSpaces() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));

        CourseScheduleBatchTimeSlotUpdateRequest request = createTimeSlotUpdateRequest(
                "  周一   08:00 - 10:00  ", "  周二  14:00-16:00  ");
        CourseScheduleBatchTimeSlotUpdateResponse response = scheduleService.batchUpdateTimeSlot(request);

        assertEquals(1, response.getMatchedCount());
        assertEquals(1, response.getUpdatedCount());
    }

    @Test
    void testBatchUpdateTimeSlot_NumericWeekday_Normalized() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));

        CourseScheduleBatchTimeSlotUpdateRequest request = createTimeSlotUpdateRequest(
                "周1 08:00-10:00", "周5 14:00-16:00");
        CourseScheduleBatchTimeSlotUpdateResponse response = scheduleService.batchUpdateTimeSlot(request);

        assertEquals(1, response.getMatchedCount());
        assertEquals(1, response.getUpdatedCount());
        assertEquals("周五 14:00-16:00", response.getToTimeSlot());
    }

    @Test
    void testBatchUpdateTimeSlot_TeacherConflict_ThrowsException() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));
        scheduleService.addSchedule(createRequest("物理", "张老师", "B202", "周二 14:00-16:00"));

        CourseScheduleBatchTimeSlotUpdateRequest request = createTimeSlotUpdateRequest(
                "周一 08:00-10:00", "周二 14:00-16:00");

        TeacherConflictException exception = assertThrows(TeacherConflictException.class, () -> {
            scheduleService.batchUpdateTimeSlot(request);
        });
        assertTrue(exception.getMessage().contains("张老师"));
        assertTrue(exception.getMessage().contains("周二 14:00-16:00"));

        List<CourseScheduleResponse> all = scheduleService.findSchedules(null);
        assertEquals(2, all.size());
        assertEquals("周一 08:00-10:00", all.get(0).getTimeSlot());
        assertEquals("周二 14:00-16:00", all.get(1).getTimeSlot());
    }

    @Test
    void testBatchUpdateTimeSlot_ClassroomConflict_ThrowsException() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));
        scheduleService.addSchedule(createRequest("物理", "李老师", "A101", "周三 09:00-11:00"));

        CourseScheduleBatchTimeSlotUpdateRequest request = createTimeSlotUpdateRequest(
                "周一 08:00-10:00", "周三 09:00-11:00");

        ClassroomConflictException exception = assertThrows(ClassroomConflictException.class, () -> {
            scheduleService.batchUpdateTimeSlot(request);
        });
        assertTrue(exception.getMessage().contains("A101"));
        assertTrue(exception.getMessage().contains("周三 09:00-11:00"));

        List<CourseScheduleResponse> all = scheduleService.findSchedules(null);
        assertEquals(2, all.size());
    }

    @Test
    void testBatchUpdateTimeSlot_TeacherConflictCheckedFirst() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));
        scheduleService.addSchedule(createRequest("物理", "张老师", "A101", "周四 10:00-12:00"));

        CourseScheduleBatchTimeSlotUpdateRequest request = createTimeSlotUpdateRequest(
                "周一 08:00-10:00", "周四 10:00-12:00");

        TeacherConflictException exception = assertThrows(TeacherConflictException.class, () -> {
            scheduleService.batchUpdateTimeSlot(request);
        });
        assertTrue(exception.getMessage().contains("张老师"));
    }

    @Test
    void testBatchUpdateTimeSlot_InternalTeacherConflict_ThrowsException() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));
        scheduleService.addSchedule(createRequest("物理", "李老师", "B202", "周一 08:00-10:00"));
        scheduleService.addSchedule(createRequest("化学", "张老师", "C303", "周二 14:00-16:00"));

        CourseScheduleBatchTimeSlotUpdateRequest request = createTimeSlotUpdateRequest(
                "周一 08:00-10:00", "周二 14:00-16:00");

        TeacherConflictException exception = assertThrows(TeacherConflictException.class, () -> {
            scheduleService.batchUpdateTimeSlot(request);
        });
        assertTrue(exception.getMessage().contains("张老师"));

        List<CourseScheduleResponse> all = scheduleService.findSchedules(null);
        assertEquals(3, all.size());
        for (CourseScheduleResponse s : all) {
            if (s.getCourseName().equals("化学")) {
                assertEquals("周二 14:00-16:00", s.getTimeSlot());
            }
        }
    }

    @Test
    void testBatchUpdateTimeSlot_InternalClassroomConflict_ThrowsException() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));
        scheduleService.addSchedule(createRequest("物理", "李老师", "B202", "周一 08:00-10:00"));
        scheduleService.addSchedule(createRequest("化学", "王老师", "B202", "周四 10:00-12:00"));

        CourseScheduleBatchTimeSlotUpdateRequest request = createTimeSlotUpdateRequest(
                "周一 08:00-10:00", "周四 10:00-12:00");

        ClassroomConflictException exception = assertThrows(ClassroomConflictException.class, () -> {
            scheduleService.batchUpdateTimeSlot(request);
        });
        assertTrue(exception.getMessage().contains("B202"));
    }

    @Test
    void testBatchUpdateTimeSlot_InvalidFromTimeSlot_ThrowsException() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));

        CourseScheduleBatchTimeSlotUpdateRequest request = createTimeSlotUpdateRequest(
                "不是合法时间段", "周二 14:00-16:00");

        InvalidTimeSlotException exception = assertThrows(InvalidTimeSlotException.class, () -> {
            scheduleService.batchUpdateTimeSlot(request);
        });
        assertTrue(exception.getMessage().contains("格式不合法"));
    }

    @Test
    void testBatchUpdateTimeSlot_InvalidToTimeSlot_ThrowsException() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));

        CourseScheduleBatchTimeSlotUpdateRequest request = createTimeSlotUpdateRequest(
                "周一 08:00-10:00", "随便写");

        InvalidTimeSlotException exception = assertThrows(InvalidTimeSlotException.class, () -> {
            scheduleService.batchUpdateTimeSlot(request);
        });
        assertTrue(exception.getMessage().contains("格式不合法"));
    }

    @Test
    void testBatchUpdateTimeSlot_StartAfterEnd_ThrowsException() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));

        CourseScheduleBatchTimeSlotUpdateRequest request = createTimeSlotUpdateRequest(
                "周一 08:00-10:00", "周二 16:00-14:00");

        InvalidTimeSlotException exception = assertThrows(InvalidTimeSlotException.class, () -> {
            scheduleService.batchUpdateTimeSlot(request);
        });
        assertTrue(exception.getMessage().contains("必须早于"));
    }

    @Test
    void testBatchUpdateTimeSlot_NullRequest_ThrowsException() {
        assertThrows(InvalidRequestParameterException.class, () -> {
            scheduleService.batchUpdateTimeSlot(null);
        });
    }

    @Test
    void testBatchUpdateTimeSlot_EmptyFromTimeSlot_ThrowsException() {
        CourseScheduleBatchTimeSlotUpdateRequest request = createTimeSlotUpdateRequest(
                "", "周二 14:00-16:00");

        InvalidRequestParameterException exception = assertThrows(InvalidRequestParameterException.class, () -> {
            scheduleService.batchUpdateTimeSlot(request);
        });
        assertTrue(exception.getMessage().contains("原时间段"));
    }

    @Test
    void testBatchUpdateTimeSlot_EmptyToTimeSlot_ThrowsException() {
        CourseScheduleBatchTimeSlotUpdateRequest request = createTimeSlotUpdateRequest(
                "周一 08:00-10:00", "");

        InvalidRequestParameterException exception = assertThrows(InvalidRequestParameterException.class, () -> {
            scheduleService.batchUpdateTimeSlot(request);
        });
        assertTrue(exception.getMessage().contains("目标时间段"));
    }

    @Test
    void testBatchUpdateTimeSlot_WhitespaceFromTimeSlot_ThrowsException() {
        CourseScheduleBatchTimeSlotUpdateRequest request = createTimeSlotUpdateRequest(
                "   ", "周二 14:00-16:00");

        InvalidRequestParameterException exception = assertThrows(InvalidRequestParameterException.class, () -> {
            scheduleService.batchUpdateTimeSlot(request);
        });
        assertTrue(exception.getMessage().contains("原时间段"));
    }

    @Test
    void testBatchUpdateTimeSlot_WhitespaceToTimeSlot_ThrowsException() {
        CourseScheduleBatchTimeSlotUpdateRequest request = createTimeSlotUpdateRequest(
                "周一 08:00-10:00", "   ");

        InvalidRequestParameterException exception = assertThrows(InvalidRequestParameterException.class, () -> {
            scheduleService.batchUpdateTimeSlot(request);
        });
        assertTrue(exception.getMessage().contains("目标时间段"));
    }

    @Test
    void testBatchUpdateTimeSlot_StatisticsUpdatedAfterSuccess() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));
        scheduleService.addSchedule(createRequest("物理", "李老师", "B202", "周一 08:00-10:00"));

        CourseScheduleWeekdayStatisticsResponse statsBefore = scheduleService.getWeekdayStatistics();
        assertEquals(2, statsBefore.getMonday());
        assertEquals(0, statsBefore.getFriday());

        CourseScheduleBatchTimeSlotUpdateRequest request = createTimeSlotUpdateRequest(
                "周一 08:00-10:00", "周五 14:00-16:00");
        scheduleService.batchUpdateTimeSlot(request);

        CourseScheduleWeekdayStatisticsResponse statsAfter = scheduleService.getWeekdayStatistics();
        assertEquals(0, statsAfter.getMonday());
        assertEquals(2, statsAfter.getFriday());
    }

    @Test
    void testBatchUpdateTimeSlot_AllOrNothing_OnConflict() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));
        scheduleService.addSchedule(createRequest("物理", "李老师", "B202", "周一 08:00-10:00"));
        scheduleService.addSchedule(createRequest("化学", "李老师", "C303", "周三 09:00-11:00"));

        CourseScheduleBatchTimeSlotUpdateRequest request = createTimeSlotUpdateRequest(
                "周一 08:00-10:00", "周三 09:00-11:00");

        assertThrows(TeacherConflictException.class, () -> {
            scheduleService.batchUpdateTimeSlot(request);
        });

        List<CourseScheduleResponse> all = scheduleService.findSchedules(null);
        assertEquals(3, all.size());
        for (CourseScheduleResponse s : all) {
            if (s.getCourseName().equals("数学") || s.getCourseName().equals("物理")) {
                assertEquals("周一 08:00-10:00", s.getTimeSlot());
            }
        }
    }

    @Test
    void testBatchUpdateTimeSlot_CanFindAfterUpdate_WithNonStandardFormat() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));

        CourseScheduleBatchTimeSlotUpdateRequest request = createTimeSlotUpdateRequest(
                "周一 08:00-10:00", "星期二 2:00-4:00");
        scheduleService.batchUpdateTimeSlot(request);

        CourseScheduleFilterRequest filter = createFilter(null, null, "周二 02:00-04:00");
        List<CourseScheduleResponse> filtered = scheduleService.findSchedules(filter);
        assertEquals(1, filtered.size());
        assertEquals("数学", filtered.get(0).getCourseName());
    }

    @Test
    void testBatchUpdateTimeSlot_DifferentFormatsSameTimeSlot_ConflictDetected() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));
        scheduleService.addSchedule(createRequest("物理", "李老师", "A101", "周一 14:00-16:00"));

        CourseScheduleBatchTimeSlotUpdateRequest request = createTimeSlotUpdateRequest(
                "星期一 8:00-10:00", "周1 14:00-16:00");

        ClassroomConflictException exception = assertThrows(ClassroomConflictException.class, () -> {
            scheduleService.batchUpdateTimeSlot(request);
        });
        assertTrue(exception.getMessage().contains("A101"));
    }

    @Test
    void testUndo_AfterAdd_RestoresPreviousState() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));

        List<CourseScheduleResponse> result = scheduleService.undo();

        assertTrue(result.isEmpty());
        assertEquals(0, scheduleService.findSchedules(null).size());
    }

    @Test
    void testUndo_AfterAddWithExistingData_RestoresPreviousState() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));
        scheduleService.addSchedule(createRequest("物理", "李老师", "B202", "周二 14:00-16:00"));

        List<CourseScheduleResponse> result = scheduleService.undo();

        assertEquals(1, result.size());
        assertEquals("数学", result.get(0).getCourseName());

        List<CourseScheduleResponse> all = scheduleService.findSchedules(null);
        assertEquals(1, all.size());
        assertEquals("数学", all.get(0).getCourseName());
    }

    @Test
    void testUndo_AfterUpdate_RestoresPreviousState() {
        CourseScheduleResponse added = scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));

        CourseScheduleUpdateRequest updateRequest = createUpdateRequest("高等数学", "李老师", "B202", "周二 14:00-16:00");
        scheduleService.updateSchedule(added.getId(), updateRequest);

        List<CourseScheduleResponse> result = scheduleService.undo();

        assertEquals(1, result.size());
        assertEquals("数学", result.get(0).getCourseName());
        assertEquals("张老师", result.get(0).getTeacherName());
        assertEquals("A101", result.get(0).getClassroom());
        assertEquals("周一 08:00-10:00", result.get(0).getTimeSlot());

        List<CourseScheduleResponse> all = scheduleService.findSchedules(null);
        assertEquals(1, all.size());
        assertEquals("数学", all.get(0).getCourseName());
    }

    @Test
    void testUndo_AfterDelete_RestoresPreviousState() {
        CourseScheduleResponse added = scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));
        scheduleService.deleteSchedule(added.getId());

        List<CourseScheduleResponse> result = scheduleService.undo();

        assertEquals(1, result.size());
        assertEquals("数学", result.get(0).getCourseName());
        assertEquals("张老师", result.get(0).getTeacherName());

        List<CourseScheduleResponse> all = scheduleService.findSchedules(null);
        assertEquals(1, all.size());
        assertEquals("数学", all.get(0).getCourseName());
    }

    @Test
    void testUndo_AfterBatchAdd_RestoresPreviousState() {
        List<CourseScheduleCreateRequest> requests = new ArrayList<>();
        requests.add(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));
        requests.add(createRequest("物理", "李老师", "B202", "周二 14:00-16:00"));
        scheduleService.addSchedulesBatch(requests);

        List<CourseScheduleResponse> result = scheduleService.undo();

        assertTrue(result.isEmpty());
        assertEquals(0, scheduleService.findSchedules(null).size());
    }

    @Test
    void testUndo_AfterBatchAddWithExistingData_RestoresPreviousState() {
        scheduleService.addSchedule(createRequest("已有课程", "王老师", "C303", "周三 09:00-11:00"));

        List<CourseScheduleCreateRequest> requests = new ArrayList<>();
        requests.add(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));
        requests.add(createRequest("物理", "李老师", "B202", "周二 14:00-16:00"));
        scheduleService.addSchedulesBatch(requests);

        List<CourseScheduleResponse> result = scheduleService.undo();

        assertEquals(1, result.size());
        assertEquals("已有课程", result.get(0).getCourseName());

        List<CourseScheduleResponse> all = scheduleService.findSchedules(null);
        assertEquals(1, all.size());
        assertEquals("已有课程", all.get(0).getCourseName());
    }

    @Test
    void testUndo_AfterBatchDelete_RestoresPreviousState() {
        CourseScheduleResponse s1 = scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));
        CourseScheduleResponse s2 = scheduleService.addSchedule(createRequest("物理", "李老师", "B202", "周二 14:00-16:00"));

        List<Long> ids = new ArrayList<>();
        ids.add(s1.getId());
        ids.add(s2.getId());
        scheduleService.deleteSchedulesBatch(ids);

        List<CourseScheduleResponse> result = scheduleService.undo();

        assertEquals(2, result.size());

        List<CourseScheduleResponse> all = scheduleService.findSchedules(null);
        assertEquals(2, all.size());
    }

    @Test
    void testUndo_AfterBatchUpdateTimeSlot_RestoresPreviousState() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));
        scheduleService.addSchedule(createRequest("物理", "李老师", "B202", "周一 08:00-10:00"));

        CourseScheduleBatchTimeSlotUpdateRequest request = createTimeSlotUpdateRequest(
                "周一 08:00-10:00", "周五 14:00-16:00");
        scheduleService.batchUpdateTimeSlot(request);

        List<CourseScheduleResponse> result = scheduleService.undo();

        assertEquals(2, result.size());
        assertEquals("周一 08:00-10:00", result.get(0).getTimeSlot());
        assertEquals("周一 08:00-10:00", result.get(1).getTimeSlot());

        List<CourseScheduleResponse> all = scheduleService.findSchedules(null);
        assertEquals(2, all.size());
        assertEquals("周一 08:00-10:00", all.get(0).getTimeSlot());
        assertEquals("周一 08:00-10:00", all.get(1).getTimeSlot());
    }

    @Test
    void testUndo_NoOperationAvailable_ThrowsException() {
        NoUndoAvailableException exception = assertThrows(NoUndoAvailableException.class, () -> {
            scheduleService.undo();
        });
        assertEquals("没有可撤销的操作", exception.getMessage());
    }

    @Test
    void testUndo_DoubleUndo_ThrowsExceptionOnSecond() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));

        scheduleService.undo();

        NoUndoAvailableException exception = assertThrows(NoUndoAvailableException.class, () -> {
            scheduleService.undo();
        });
        assertEquals("没有可撤销的操作", exception.getMessage());
    }

    @Test
    void testUndo_DoesNotCreateNewUndoRecord() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));

        scheduleService.undo();

        assertThrows(NoUndoAvailableException.class, () -> {
            scheduleService.undo();
        });
    }

    @Test
    void testUndo_RestoresIdGenerator() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));
        scheduleService.addSchedule(createRequest("物理", "李老师", "B202", "周二 14:00-16:00"));

        scheduleService.undo();

        CourseScheduleResponse newSchedule = scheduleService.addSchedule(createRequest("化学", "王老师", "C303", "周三 09:00-11:00"));
        assertEquals(2L, newSchedule.getId());
    }

    @Test
    void testUndo_ClearAllSchedulesClearsUndoSnapshot() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));

        scheduleService.clearAllSchedules();

        NoUndoAvailableException exception = assertThrows(NoUndoAvailableException.class, () -> {
            scheduleService.undo();
        });
        assertEquals("没有可撤销的操作", exception.getMessage());
    }

    @Test
    void testUndo_FailedAdd_DoesNotOverwritePreviousUndoRecord() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));

        assertThrows(TeacherConflictException.class, () -> {
            scheduleService.addSchedule(createRequest("物理", "张老师", "B202", "周一 08:00-10:00"));
        });

        List<CourseScheduleResponse> result = scheduleService.undo();
        assertEquals(0, result.size());
    }

    @Test
    void testUndo_FailedAddWithInvalidParam_DoesNotOverwritePreviousUndoRecord() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));

        assertThrows(InvalidRequestParameterException.class, () -> {
            scheduleService.addSchedule(createRequest("", "李老师", "B202", "周二 10:00-12:00"));
        });

        List<CourseScheduleResponse> result = scheduleService.undo();
        assertEquals(0, result.size());
    }

    @Test
    void testUndo_FailedUpdateNotFound_DoesNotOverwritePreviousUndoRecord() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));

        assertThrows(ScheduleNotFoundException.class, () -> {
            scheduleService.updateSchedule(999L, createUpdateRequest("物理", "李老师", "B202", "周二 10:00-12:00"));
        });

        List<CourseScheduleResponse> result = scheduleService.undo();
        assertEquals(0, result.size());
    }

    @Test
    void testUndo_FailedUpdateConflict_DoesNotOverwritePreviousUndoRecord() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));
        scheduleService.addSchedule(createRequest("物理", "李老师", "B202", "周二 10:00-12:00"));

        assertThrows(TeacherConflictException.class, () -> {
            scheduleService.updateSchedule(2L, createUpdateRequest("化学", "张老师", "C303", "周一 08:00-10:00"));
        });

        List<CourseScheduleResponse> result = scheduleService.undo();
        assertEquals(1, result.size());
        assertEquals("数学", result.get(0).getCourseName());
    }

    @Test
    void testUndo_FailedDeleteNotFound_DoesNotOverwritePreviousUndoRecord() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));

        assertThrows(ScheduleNotFoundException.class, () -> {
            scheduleService.deleteSchedule(999L);
        });

        List<CourseScheduleResponse> result = scheduleService.undo();
        assertEquals(0, result.size());
    }

    @Test
    void testUndo_FailedBatchAddAllFailures_DoesNotOverwritePreviousUndoRecord() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));

        List<CourseScheduleCreateRequest> requests = new ArrayList<>();
        requests.add(createRequest("", "李老师", "B202", "周二 10:00-12:00"));
        requests.add(createRequest("物理", "", "C303", "周三 14:00-16:00"));
        CourseScheduleBatchResponse response = scheduleService.addSchedulesBatch(requests);

        assertEquals(2, response.getTotalCount());
        assertEquals(0, response.getSuccessCount());
        assertEquals(2, response.getFailureCount());

        List<CourseScheduleResponse> result = scheduleService.undo();
        assertEquals(0, result.size());
    }

    @Test
    void testUndo_BatchAddPartialSuccess_SavesUndoRecord() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));

        List<CourseScheduleCreateRequest> requests = new ArrayList<>();
        requests.add(createRequest("物理", "李老师", "B202", "周二 10:00-12:00"));
        requests.add(createRequest("", "王老师", "C303", "周三 14:00-16:00"));
        CourseScheduleBatchResponse response = scheduleService.addSchedulesBatch(requests);

        assertEquals(2, response.getTotalCount());
        assertEquals(1, response.getSuccessCount());
        assertEquals(1, response.getFailureCount());

        List<CourseScheduleResponse> result = scheduleService.undo();
        assertEquals(1, result.size());
        assertEquals("数学", result.get(0).getCourseName());
    }

    @Test
    void testUndo_FailedBatchDeleteAllFailures_DoesNotOverwritePreviousUndoRecord() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));

        List<Long> ids = new ArrayList<>();
        ids.add(999L);
        ids.add(-1L);
        CourseScheduleBatchDeleteResponse response = scheduleService.deleteSchedulesBatch(ids);

        assertEquals(2, response.getTotalCount());
        assertEquals(0, response.getSuccessCount());
        assertEquals(2, response.getFailureCount());

        List<CourseScheduleResponse> result = scheduleService.undo();
        assertEquals(0, result.size());
    }

    @Test
    void testUndo_FailedBatchDeleteEmptyList_DoesNotOverwritePreviousUndoRecord() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));

        CourseScheduleBatchDeleteResponse response = scheduleService.deleteSchedulesBatch(new ArrayList<>());

        assertEquals(0, response.getTotalCount());

        List<CourseScheduleResponse> result = scheduleService.undo();
        assertEquals(0, result.size());
    }

    @Test
    void testUndo_BatchDeletePartialSuccess_SavesUndoRecord() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));
        scheduleService.addSchedule(createRequest("物理", "李老师", "B202", "周二 10:00-12:00"));

        List<Long> ids = new ArrayList<>();
        ids.add(1L);
        ids.add(999L);
        CourseScheduleBatchDeleteResponse response = scheduleService.deleteSchedulesBatch(ids);

        assertEquals(2, response.getTotalCount());
        assertEquals(1, response.getSuccessCount());
        assertEquals(1, response.getFailureCount());

        List<CourseScheduleResponse> result = scheduleService.undo();
        assertEquals(2, result.size());
    }

    @Test
    void testUndo_FailedBatchTimeSlotUpdateNoMatch_DoesNotOverwritePreviousUndoRecord() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));

        CourseScheduleBatchTimeSlotUpdateRequest request = new CourseScheduleBatchTimeSlotUpdateRequest();
        request.setFromTimeSlot("周二 10:00-12:00");
        request.setToTimeSlot("周三 14:00-16:00");
        CourseScheduleBatchTimeSlotUpdateResponse response = scheduleService.batchUpdateTimeSlot(request);

        assertEquals(0, response.getMatchedCount());

        List<CourseScheduleResponse> result = scheduleService.undo();
        assertEquals(0, result.size());
    }

    @Test
    void testUndo_FailedBatchTimeSlotUpdateSameSlot_DoesNotOverwritePreviousUndoRecord() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));

        CourseScheduleBatchTimeSlotUpdateRequest request = new CourseScheduleBatchTimeSlotUpdateRequest();
        request.setFromTimeSlot("周一 08:00-10:00");
        request.setToTimeSlot("周一 08:00-10:00");
        CourseScheduleBatchTimeSlotUpdateResponse response = scheduleService.batchUpdateTimeSlot(request);

        assertEquals(0, response.getUpdatedCount());

        List<CourseScheduleResponse> result = scheduleService.undo();
        assertEquals(0, result.size());
    }

    @Test
    void testUndo_FailedBatchTimeSlotUpdateConflict_DoesNotOverwritePreviousUndoRecord() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));
        scheduleService.addSchedule(createRequest("物理", "张老师", "B202", "周二 10:00-12:00"));

        CourseScheduleBatchTimeSlotUpdateRequest request = new CourseScheduleBatchTimeSlotUpdateRequest();
        request.setFromTimeSlot("周二 10:00-12:00");
        request.setToTimeSlot("周一 08:00-10:00");

        assertThrows(TeacherConflictException.class, () -> {
            scheduleService.batchUpdateTimeSlot(request);
        });

        List<CourseScheduleResponse> result = scheduleService.undo();
        assertEquals(1, result.size());
        assertEquals("数学", result.get(0).getCourseName());
    }

    @Test
    void testUndo_FailedBatchTimeSlotUpdateInvalidParam_DoesNotOverwritePreviousUndoRecord() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));

        CourseScheduleBatchTimeSlotUpdateRequest request = new CourseScheduleBatchTimeSlotUpdateRequest();
        request.setFromTimeSlot("");
        request.setToTimeSlot("周三 14:00-16:00");

        assertThrows(InvalidRequestParameterException.class, () -> {
            scheduleService.batchUpdateTimeSlot(request);
        });

        List<CourseScheduleResponse> result = scheduleService.undo();
        assertEquals(0, result.size());
    }

    @Test
    void testGetClassroomDailySchedule_NormalQuery_ReturnsSortedCourses() {
        scheduleService.addSchedule(createRequest("高等数学", "王教授", "A101", "周一 14:00-16:00"));
        scheduleService.addSchedule(createRequest("线性代数", "李老师", "A101", "周一 08:00-10:00"));
        scheduleService.addSchedule(createRequest("概率论", "张教授", "A101", "周一 10:00-12:00"));

        List<CourseScheduleResponse> result = scheduleService.getClassroomDailySchedule("A101", "周一");

        assertEquals(3, result.size());
        assertEquals("08:00", result.get(0).getTimeSlot().substring(3, 8));
        assertEquals("10:00", result.get(1).getTimeSlot().substring(3, 8));
        assertEquals("14:00", result.get(2).getTimeSlot().substring(3, 8));
        assertEquals("线性代数", result.get(0).getCourseName());
        assertEquals("概率论", result.get(1).getCourseName());
        assertEquals("高等数学", result.get(2).getCourseName());
    }

    @Test
    void testGetClassroomDailySchedule_ClassroomWithSpaces_TrimmedAndMatched() {
        scheduleService.addSchedule(createRequest("大学英语", "刘老师", "教学楼 301", "周二 09:00-11:00"));

        List<CourseScheduleResponse> result = scheduleService.getClassroomDailySchedule("  教学楼 301  ", "周二");

        assertEquals(1, result.size());
        assertEquals("教学楼 301", result.get(0).getClassroom());
        assertEquals("大学英语", result.get(0).getCourseName());
    }

    @Test
    void testGetClassroomDailySchedule_WeekdayZhouYi_ReturnsCorrect() {
        scheduleService.addSchedule(createRequest("物理", "赵老师", "B202", "周一 08:00-10:00"));

        List<CourseScheduleResponse> result = scheduleService.getClassroomDailySchedule("B202", "周一");

        assertEquals(1, result.size());
        assertEquals("物理", result.get(0).getCourseName());
    }

    @Test
    void testGetClassroomDailySchedule_WeekdayXingQiYi_ReturnsSameAsZhouYi() {
        scheduleService.addSchedule(createRequest("化学", "孙老师", "C303", "周一 14:00-16:00"));

        List<CourseScheduleResponse> result1 = scheduleService.getClassroomDailySchedule("C303", "周一");
        List<CourseScheduleResponse> result2 = scheduleService.getClassroomDailySchedule("C303", "星期一");

        assertEquals(1, result1.size());
        assertEquals(1, result2.size());
        assertEquals("化学", result1.get(0).getCourseName());
        assertEquals("化学", result2.get(0).getCourseName());
    }

    @Test
    void testGetClassroomDailySchedule_WeekdayNumber1_ReturnsSameAsZhouYi() {
        scheduleService.addSchedule(createRequest("生物", "周老师", "D404", "周一 10:00-12:00"));

        List<CourseScheduleResponse> result1 = scheduleService.getClassroomDailySchedule("D404", "周一");
        List<CourseScheduleResponse> result2 = scheduleService.getClassroomDailySchedule("D404", "1");

        assertEquals(1, result1.size());
        assertEquals(1, result2.size());
        assertEquals("生物", result1.get(0).getCourseName());
        assertEquals("生物", result2.get(0).getCourseName());
    }

    @Test
    void testGetClassroomDailySchedule_AllWeekdayFormats_ReturnSameResult() {
        scheduleService.addSchedule(createRequest("计算机", "吴老师", "E505", "周三 14:00-16:00"));

        List<CourseScheduleResponse> result1 = scheduleService.getClassroomDailySchedule("E505", "周三");
        List<CourseScheduleResponse> result2 = scheduleService.getClassroomDailySchedule("E505", "星期三");
        List<CourseScheduleResponse> result3 = scheduleService.getClassroomDailySchedule("E505", "3");
        List<CourseScheduleResponse> result4 = scheduleService.getClassroomDailySchedule("E505", "周3");
        List<CourseScheduleResponse> result5 = scheduleService.getClassroomDailySchedule("E505", "星期3");

        assertEquals(1, result1.size());
        assertEquals(1, result2.size());
        assertEquals(1, result3.size());
        assertEquals(1, result4.size());
        assertEquals(1, result5.size());
        assertEquals("计算机", result1.get(0).getCourseName());
        assertEquals("计算机", result2.get(0).getCourseName());
        assertEquals("计算机", result3.get(0).getCourseName());
        assertEquals("计算机", result4.get(0).getCourseName());
        assertEquals("计算机", result5.get(0).getCourseName());
    }

    @Test
    void testGetClassroomDailySchedule_SortedByStartTime_EarlyToLate() {
        scheduleService.addSchedule(createRequest("课程4", "老师4", "F606", "周五 18:00-20:00"));
        scheduleService.addSchedule(createRequest("课程1", "老师1", "F606", "周五 08:00-10:00"));
        scheduleService.addSchedule(createRequest("课程3", "老师3", "F606", "周五 14:00-16:00"));
        scheduleService.addSchedule(createRequest("课程2", "老师2", "F606", "周五 10:00-12:00"));

        List<CourseScheduleResponse> result = scheduleService.getClassroomDailySchedule("F606", "周五");

        assertEquals(4, result.size());
        assertEquals("课程1", result.get(0).getCourseName());
        assertEquals("课程2", result.get(1).getCourseName());
        assertEquals("课程3", result.get(2).getCourseName());
        assertEquals("课程4", result.get(3).getCourseName());
    }

    @Test
    void testGetClassroomDailySchedule_NoResult_ReturnsEmptyList() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));

        List<CourseScheduleResponse> result = scheduleService.getClassroomDailySchedule("A101", "周二");

        assertTrue(result.isEmpty());
    }

    @Test
    void testGetClassroomDailySchedule_WrongClassroom_ReturnsEmptyList() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));

        List<CourseScheduleResponse> result = scheduleService.getClassroomDailySchedule("B202", "周一");

        assertTrue(result.isEmpty());
    }

    @Test
    void testGetClassroomDailySchedule_EmptyData_ReturnsEmptyList() {
        List<CourseScheduleResponse> result = scheduleService.getClassroomDailySchedule("A101", "周一");

        assertTrue(result.isEmpty());
    }

    @Test
    void testGetClassroomDailySchedule_OnlyFiltersCorrectClassroomAndWeekday() {
        scheduleService.addSchedule(createRequest("课程1", "老师1", "A101", "周一 08:00-10:00"));
        scheduleService.addSchedule(createRequest("课程2", "老师2", "A101", "周二 08:00-10:00"));
        scheduleService.addSchedule(createRequest("课程3", "老师3", "B202", "周一 08:00-10:00"));
        scheduleService.addSchedule(createRequest("课程4", "老师4", "A101", "周一 14:00-16:00"));

        List<CourseScheduleResponse> result = scheduleService.getClassroomDailySchedule("A101", "周一");

        assertEquals(2, result.size());
        assertEquals("课程1", result.get(0).getCourseName());
        assertEquals("课程4", result.get(1).getCourseName());
    }

    @Test
    void testGetClassroomDailySchedule_NullClassroom_ThrowsException() {
        InvalidRequestParameterException exception = assertThrows(InvalidRequestParameterException.class, () -> {
            scheduleService.getClassroomDailySchedule(null, "周一");
        });
        assertTrue(exception.getMessage().contains("教室名不能为空"));
    }

    @Test
    void testGetClassroomDailySchedule_EmptyClassroom_ThrowsException() {
        InvalidRequestParameterException exception = assertThrows(InvalidRequestParameterException.class, () -> {
            scheduleService.getClassroomDailySchedule("", "周一");
        });
        assertTrue(exception.getMessage().contains("教室名不能为空"));
    }

    @Test
    void testGetClassroomDailySchedule_WhitespaceClassroom_ThrowsException() {
        InvalidRequestParameterException exception = assertThrows(InvalidRequestParameterException.class, () -> {
            scheduleService.getClassroomDailySchedule("   ", "周一");
        });
        assertTrue(exception.getMessage().contains("教室名不能为空"));
    }

    @Test
    void testGetClassroomDailySchedule_NullWeekday_ThrowsException() {
        InvalidTimeSlotException exception = assertThrows(InvalidTimeSlotException.class, () -> {
            scheduleService.getClassroomDailySchedule("A101", null);
        });
        assertTrue(exception.getMessage().contains("不能为空"));
    }

    @Test
    void testGetClassroomDailySchedule_InvalidWeekday_ThrowsException() {
        InvalidTimeSlotException exception = assertThrows(InvalidTimeSlotException.class, () -> {
            scheduleService.getClassroomDailySchedule("A101", "星期一 08:00");
        });
        assertTrue(exception.getMessage().contains("格式不合法"));
    }

    @Test
    void testGetClassroomDailySchedule_ClassroomNameCaseSensitive_ExactMatch() {
        scheduleService.addSchedule(createRequest("英语", "刘老师", "a101", "周三 08:00-10:00"));

        List<CourseScheduleResponse> result1 = scheduleService.getClassroomDailySchedule("a101", "周三");
        List<CourseScheduleResponse> result2 = scheduleService.getClassroomDailySchedule("A101", "周三");

        assertEquals(1, result1.size());
        assertTrue(result2.isEmpty());
    }

    @Test
    void testGetClassroomDailySchedule_Sunday_Weekday7_WorksCorrectly() {
        scheduleService.addSchedule(createRequest("选修", "钱老师", "G707", "周日 10:00-12:00"));

        List<CourseScheduleResponse> result1 = scheduleService.getClassroomDailySchedule("G707", "周日");
        List<CourseScheduleResponse> result2 = scheduleService.getClassroomDailySchedule("G707", "星期日");
        List<CourseScheduleResponse> result3 = scheduleService.getClassroomDailySchedule("G707", "7");

        assertEquals(1, result1.size());
        assertEquals(1, result2.size());
        assertEquals(1, result3.size());
        assertEquals("选修", result1.get(0).getCourseName());
        assertEquals("选修", result2.get(0).getCourseName());
        assertEquals("选修", result3.get(0).getCourseName());
    }

    @Test
    void testGetTeacherDailySchedule_NormalQuery_ReturnsSortedCourses() {
        scheduleService.addSchedule(createRequest("高等数学", "王教授", "A101", "周一 14:00-16:00"));
        scheduleService.addSchedule(createRequest("线性代数", "王教授", "B202", "周一 08:00-10:00"));
        scheduleService.addSchedule(createRequest("概率论", "王教授", "C303", "周一 10:00-12:00"));

        List<CourseScheduleResponse> result = scheduleService.getTeacherDailySchedule("王教授", "周一");

        assertEquals(3, result.size());
        assertEquals("08:00", result.get(0).getTimeSlot().substring(3, 8));
        assertEquals("10:00", result.get(1).getTimeSlot().substring(3, 8));
        assertEquals("14:00", result.get(2).getTimeSlot().substring(3, 8));
        assertEquals("线性代数", result.get(0).getCourseName());
        assertEquals("概率论", result.get(1).getCourseName());
        assertEquals("高等数学", result.get(2).getCourseName());
    }

    @Test
    void testGetTeacherDailySchedule_TeacherNameWithSpaces_TrimmedAndMatched() {
        scheduleService.addSchedule(createRequest("大学英语", "刘老师", "教学楼 301", "周二 09:00-11:00"));

        List<CourseScheduleResponse> result = scheduleService.getTeacherDailySchedule("  刘老师  ", "周二");

        assertEquals(1, result.size());
        assertEquals("刘老师", result.get(0).getTeacherName());
        assertEquals("大学英语", result.get(0).getCourseName());
    }

    @Test
    void testGetTeacherDailySchedule_WeekdayZhouYi_ReturnsCorrect() {
        scheduleService.addSchedule(createRequest("物理", "赵老师", "B202", "周一 08:00-10:00"));

        List<CourseScheduleResponse> result = scheduleService.getTeacherDailySchedule("赵老师", "周一");

        assertEquals(1, result.size());
        assertEquals("物理", result.get(0).getCourseName());
    }

    @Test
    void testGetTeacherDailySchedule_WeekdayXingQiYi_ReturnsSameAsZhouYi() {
        scheduleService.addSchedule(createRequest("化学", "孙老师", "C303", "周一 14:00-16:00"));

        List<CourseScheduleResponse> result1 = scheduleService.getTeacherDailySchedule("孙老师", "周一");
        List<CourseScheduleResponse> result2 = scheduleService.getTeacherDailySchedule("孙老师", "星期一");

        assertEquals(1, result1.size());
        assertEquals(1, result2.size());
        assertEquals("化学", result1.get(0).getCourseName());
        assertEquals("化学", result2.get(0).getCourseName());
    }

    @Test
    void testGetTeacherDailySchedule_WeekdayNumber1_ReturnsSameAsZhouYi() {
        scheduleService.addSchedule(createRequest("生物", "周老师", "D404", "周一 10:00-12:00"));

        List<CourseScheduleResponse> result1 = scheduleService.getTeacherDailySchedule("周老师", "周一");
        List<CourseScheduleResponse> result2 = scheduleService.getTeacherDailySchedule("周老师", "1");

        assertEquals(1, result1.size());
        assertEquals(1, result2.size());
        assertEquals("生物", result1.get(0).getCourseName());
        assertEquals("生物", result2.get(0).getCourseName());
    }

    @Test
    void testGetTeacherDailySchedule_AllWeekdayFormats_ReturnSameResult() {
        scheduleService.addSchedule(createRequest("计算机", "吴老师", "E505", "周三 14:00-16:00"));

        List<CourseScheduleResponse> result1 = scheduleService.getTeacherDailySchedule("吴老师", "周三");
        List<CourseScheduleResponse> result2 = scheduleService.getTeacherDailySchedule("吴老师", "星期三");
        List<CourseScheduleResponse> result3 = scheduleService.getTeacherDailySchedule("吴老师", "3");
        List<CourseScheduleResponse> result4 = scheduleService.getTeacherDailySchedule("吴老师", "周3");
        List<CourseScheduleResponse> result5 = scheduleService.getTeacherDailySchedule("吴老师", "星期3");

        assertEquals(1, result1.size());
        assertEquals(1, result2.size());
        assertEquals(1, result3.size());
        assertEquals(1, result4.size());
        assertEquals(1, result5.size());
        assertEquals("计算机", result1.get(0).getCourseName());
        assertEquals("计算机", result2.get(0).getCourseName());
        assertEquals("计算机", result3.get(0).getCourseName());
        assertEquals("计算机", result4.get(0).getCourseName());
        assertEquals("计算机", result5.get(0).getCourseName());
    }

    @Test
    void testGetTeacherDailySchedule_SortedByStartTime_EarlyToLate() {
        scheduleService.addSchedule(createRequest("课程4", "陈教授", "F606", "周五 18:00-20:00"));
        scheduleService.addSchedule(createRequest("课程1", "陈教授", "A101", "周五 08:00-10:00"));
        scheduleService.addSchedule(createRequest("课程3", "陈教授", "B202", "周五 14:00-16:00"));
        scheduleService.addSchedule(createRequest("课程2", "陈教授", "C303", "周五 10:00-12:00"));

        List<CourseScheduleResponse> result = scheduleService.getTeacherDailySchedule("陈教授", "周五");

        assertEquals(4, result.size());
        assertEquals("课程1", result.get(0).getCourseName());
        assertEquals("课程2", result.get(1).getCourseName());
        assertEquals("课程3", result.get(2).getCourseName());
        assertEquals("课程4", result.get(3).getCourseName());
    }

    @Test
    void testGetTeacherDailySchedule_NoResult_ReturnsEmptyList() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));

        List<CourseScheduleResponse> result = scheduleService.getTeacherDailySchedule("张老师", "周二");

        assertTrue(result.isEmpty());
    }

    @Test
    void testGetTeacherDailySchedule_WrongTeacher_ReturnsEmptyList() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));

        List<CourseScheduleResponse> result = scheduleService.getTeacherDailySchedule("李老师", "周一");

        assertTrue(result.isEmpty());
    }

    @Test
    void testGetTeacherDailySchedule_EmptyData_ReturnsEmptyList() {
        List<CourseScheduleResponse> result = scheduleService.getTeacherDailySchedule("王教授", "周一");

        assertTrue(result.isEmpty());
    }

    @Test
    void testGetTeacherDailySchedule_OnlyFiltersCorrectTeacherAndWeekday() {
        scheduleService.addSchedule(createRequest("课程1", "老师1", "A101", "周一 08:00-10:00"));
        scheduleService.addSchedule(createRequest("课程2", "老师1", "A102", "周二 08:00-10:00"));
        scheduleService.addSchedule(createRequest("课程3", "老师2", "B202", "周一 08:00-10:00"));
        scheduleService.addSchedule(createRequest("课程4", "老师1", "C303", "周一 14:00-16:00"));

        List<CourseScheduleResponse> result = scheduleService.getTeacherDailySchedule("老师1", "周一");

        assertEquals(2, result.size());
        assertEquals("课程1", result.get(0).getCourseName());
        assertEquals("课程4", result.get(1).getCourseName());
    }

    @Test
    void testGetTeacherDailySchedule_NullTeacherName_ThrowsException() {
        InvalidRequestParameterException exception = assertThrows(InvalidRequestParameterException.class, () -> {
            scheduleService.getTeacherDailySchedule(null, "周一");
        });
        assertTrue(exception.getMessage().contains("老师名不能为空"));
    }

    @Test
    void testGetTeacherDailySchedule_EmptyTeacherName_ThrowsException() {
        InvalidRequestParameterException exception = assertThrows(InvalidRequestParameterException.class, () -> {
            scheduleService.getTeacherDailySchedule("", "周一");
        });
        assertTrue(exception.getMessage().contains("老师名不能为空"));
    }

    @Test
    void testGetTeacherDailySchedule_WhitespaceTeacherName_ThrowsException() {
        InvalidRequestParameterException exception = assertThrows(InvalidRequestParameterException.class, () -> {
            scheduleService.getTeacherDailySchedule("   ", "周一");
        });
        assertTrue(exception.getMessage().contains("老师名不能为空"));
    }

    @Test
    void testGetTeacherDailySchedule_NullWeekday_ThrowsException() {
        InvalidTimeSlotException exception = assertThrows(InvalidTimeSlotException.class, () -> {
            scheduleService.getTeacherDailySchedule("张老师", null);
        });
        assertTrue(exception.getMessage().contains("不能为空"));
    }

    @Test
    void testGetTeacherDailySchedule_InvalidWeekday_ThrowsException() {
        InvalidTimeSlotException exception = assertThrows(InvalidTimeSlotException.class, () -> {
            scheduleService.getTeacherDailySchedule("张老师", "星期一 08:00");
        });
        assertTrue(exception.getMessage().contains("格式不合法"));
    }

    @Test
    void testGetTeacherDailySchedule_TeacherNameCaseSensitive_ExactMatch() {
        scheduleService.addSchedule(createRequest("英语", "zhangsan", "A101", "周三 08:00-10:00"));

        List<CourseScheduleResponse> result1 = scheduleService.getTeacherDailySchedule("zhangsan", "周三");
        List<CourseScheduleResponse> result2 = scheduleService.getTeacherDailySchedule("Zhangsan", "周三");

        assertEquals(1, result1.size());
        assertTrue(result2.isEmpty());
    }

    @Test
    void testGetTeacherDailySchedule_Sunday_Weekday7_WorksCorrectly() {
        scheduleService.addSchedule(createRequest("选修", "钱老师", "G707", "周日 10:00-12:00"));

        List<CourseScheduleResponse> result1 = scheduleService.getTeacherDailySchedule("钱老师", "周日");
        List<CourseScheduleResponse> result2 = scheduleService.getTeacherDailySchedule("钱老师", "星期日");
        List<CourseScheduleResponse> result3 = scheduleService.getTeacherDailySchedule("钱老师", "7");

        assertEquals(1, result1.size());
        assertEquals(1, result2.size());
        assertEquals(1, result3.size());
        assertEquals("选修", result1.get(0).getCourseName());
        assertEquals("选修", result2.get(0).getCourseName());
        assertEquals("选修", result3.get(0).getCourseName());
    }

    private void addTimeRangeSampleData() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));
        scheduleService.addSchedule(createRequest("物理", "李老师", "A102", "周一 10:00-12:00"));
        scheduleService.addSchedule(createRequest("化学", "王老师", "B201", "周一 14:00-16:00"));
        scheduleService.addSchedule(createRequest("英语", "赵老师", "B202", "周二 08:00-10:00"));
        scheduleService.addSchedule(createRequest("生物", "孙老师", "C301", "周二 10:00-12:00"));
    }

    private CourseScheduleFilterRequest createTimeRangeFilter(
            String weekday, String startTimeFrom, String startTimeTo) {
        CourseScheduleFilterRequest filter = new CourseScheduleFilterRequest();
        filter.setWeekday(weekday);
        filter.setStartTimeFrom(startTimeFrom);
        filter.setStartTimeTo(startTimeTo);
        return filter;
    }

    @Test
    void testFindSchedules_ByWeekdayOnly_Monday_ReturnsMondayCourses() {
        addTimeRangeSampleData();

        CourseScheduleFilterRequest filter = createTimeRangeFilter("周一", null, null);
        List<CourseScheduleResponse> result = scheduleService.findSchedules(filter);

        assertEquals(3, result.size());
        assertTrue(result.stream().allMatch(r -> r.getTimeSlot().startsWith("周一")));
    }

    @Test
    void testFindSchedules_ByWeekdayOnly_Tuesday_ReturnsTuesdayCourses() {
        addTimeRangeSampleData();

        CourseScheduleFilterRequest filter = createTimeRangeFilter("周二", null, null);
        List<CourseScheduleResponse> result = scheduleService.findSchedules(filter);

        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(r -> r.getTimeSlot().startsWith("周二")));
    }

    @Test
    void testFindSchedules_FullTimeRange_MondayMorning_ReturnsMorningCourses() {
        addTimeRangeSampleData();

        CourseScheduleFilterRequest filter = createTimeRangeFilter("周一", "08:00", "12:00");
        List<CourseScheduleResponse> result = scheduleService.findSchedules(filter);

        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(r -> r.getTimeSlot().startsWith("周一")));
        assertTrue(result.stream().anyMatch(r -> r.getCourseName().equals("数学")));
        assertTrue(result.stream().anyMatch(r -> r.getCourseName().equals("物理")));
    }

    @Test
    void testFindSchedules_FullTimeRange_MondayAfternoon_ReturnsAfternoonCourses() {
        addTimeRangeSampleData();

        CourseScheduleFilterRequest filter = createTimeRangeFilter("周一", "12:00", "18:00");
        List<CourseScheduleResponse> result = scheduleService.findSchedules(filter);

        assertEquals(1, result.size());
        assertEquals("化学", result.get(0).getCourseName());
    }

    @Test
    void testFindSchedules_StartTimeFromOnly_ReturnsCoursesFromThatTime() {
        addTimeRangeSampleData();

        CourseScheduleFilterRequest filter = createTimeRangeFilter("周一", "10:00", null);
        List<CourseScheduleResponse> result = scheduleService.findSchedules(filter);

        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(r -> r.getCourseName().equals("物理")));
        assertTrue(result.stream().anyMatch(r -> r.getCourseName().equals("化学")));
    }

    @Test
    void testFindSchedules_StartTimeToOnly_ReturnsCoursesUpToThatTime() {
        addTimeRangeSampleData();

        CourseScheduleFilterRequest filter = createTimeRangeFilter("周一", null, "10:00");
        List<CourseScheduleResponse> result = scheduleService.findSchedules(filter);

        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(r -> r.getCourseName().equals("数学")));
        assertTrue(result.stream().anyMatch(r -> r.getCourseName().equals("物理")));
    }

    @Test
    void testFindSchedules_TimeRangeWithOtherFilters_CombinedCorrectly() {
        addTimeRangeSampleData();

        CourseScheduleFilterRequest filter = createTimeRangeFilter("周一", "08:00", "12:00");
        filter.setTeacherName("张");
        List<CourseScheduleResponse> result = scheduleService.findSchedules(filter);

        assertEquals(1, result.size());
        assertEquals("数学", result.get(0).getCourseName());
    }

    @Test
    void testFindSchedules_TimeRangeNoMatch_ReturnsEmpty() {
        addTimeRangeSampleData();

        CourseScheduleFilterRequest filter = createTimeRangeFilter("周一", "18:00", "20:00");
        List<CourseScheduleResponse> result = scheduleService.findSchedules(filter);

        assertTrue(result.isEmpty());
    }

    @Test
    void testFindSchedules_StartTimeFromGreaterThanTo_ThrowsInvalidTimeSlotException() {
        addTimeRangeSampleData();

        CourseScheduleFilterRequest filter = createTimeRangeFilter("周一", "12:00", "08:00");

        InvalidTimeSlotException exception = assertThrows(InvalidTimeSlotException.class, () -> {
            scheduleService.findSchedules(filter);
        });

        assertTrue(exception.getMessage().contains("startTimeFrom 必须早于或等于 startTimeTo"));
    }

    @Test
    void testFindSchedules_WeekdayNumber_WorksCorrectly() {
        addTimeRangeSampleData();

        CourseScheduleFilterRequest filter = createTimeRangeFilter("1", "08:00", "12:00");
        List<CourseScheduleResponse> result = scheduleService.findSchedules(filter);

        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(r -> r.getTimeSlot().startsWith("周一")));
    }

    @Test
    void testFindSchedules_TrimmedTimeRangeParams_WorksCorrectly() {
        addTimeRangeSampleData();

        CourseScheduleFilterRequest filter = createTimeRangeFilter("  周一  ", "  08:00  ", "  12:00  ");
        List<CourseScheduleResponse> result = scheduleService.findSchedules(filter);

        assertEquals(2, result.size());
    }

    @Test
    void testFindSchedules_InvalidTimePointFormat_ThrowsException() {
        addTimeRangeSampleData();

        CourseScheduleFilterRequest filter = createTimeRangeFilter("周一", "8点", null);

        InvalidTimeSlotException exception = assertThrows(InvalidTimeSlotException.class, () -> {
            scheduleService.findSchedules(filter);
        });

        assertTrue(exception.getMessage().contains("格式不合法"));
    }

    @Test
    void testExportSchedulesAsCsv_ByTimeRange_ContainsOnlyFilteredCourses() {
        addTimeRangeSampleData();

        CourseScheduleFilterRequest filter = createTimeRangeFilter("周一", "08:00", "12:00");
        byte[] csvBytes = scheduleService.exportSchedulesAsCsv(filter);
        String csvContent = new String(csvBytes, java.nio.charset.StandardCharsets.UTF_8);

        assertTrue(csvContent.contains("数学"));
        assertTrue(csvContent.contains("物理"));
        assertFalse(csvContent.contains("化学"));
        assertFalse(csvContent.contains("英语"));
        assertFalse(csvContent.contains("生物"));
    }

    @Test
    void testExportSchedulesAsCsv_ByWeekdayOnly_ContainsCorrectCourses() {
        addTimeRangeSampleData();

        CourseScheduleFilterRequest filter = createTimeRangeFilter("周二", null, null);
        byte[] csvBytes = scheduleService.exportSchedulesAsCsv(filter);
        String csvContent = new String(csvBytes, java.nio.charset.StandardCharsets.UTF_8);

        assertTrue(csvContent.contains("英语"));
        assertTrue(csvContent.contains("生物"));
        assertFalse(csvContent.contains("数学"));
        assertFalse(csvContent.contains("物理"));
        assertFalse(csvContent.contains("化学"));
    }

    @Test
    void testExportSchedulesAsCsv_StartTimeFromGreaterThanTo_ThrowsInvalidTimeSlotException() {
        addTimeRangeSampleData();

        CourseScheduleFilterRequest filter = createTimeRangeFilter("周一", "12:00", "08:00");

        InvalidTimeSlotException exception = assertThrows(InvalidTimeSlotException.class, () -> {
            scheduleService.exportSchedulesAsCsv(filter);
        });

        assertTrue(exception.getMessage().contains("startTimeFrom 必须早于或等于 startTimeTo"));
    }

    @Test
    void testExportSchedulesAsCsv_NoTimeRange_ContainsAllCourses() {
        addTimeRangeSampleData();

        CourseScheduleFilterRequest filter = new CourseScheduleFilterRequest();
        byte[] csvBytes = scheduleService.exportSchedulesAsCsv(filter);
        String csvContent = new String(csvBytes, java.nio.charset.StandardCharsets.UTF_8);

        assertTrue(csvContent.contains("数学"));
        assertTrue(csvContent.contains("物理"));
        assertTrue(csvContent.contains("化学"));
        assertTrue(csvContent.contains("英语"));
        assertTrue(csvContent.contains("生物"));
    }

    @Test
    void testExportSchedulesAsCsv_TimeRangeWithOtherFilters_CombinedCorrectly() {
        addTimeRangeSampleData();

        CourseScheduleFilterRequest filter = createTimeRangeFilter("周一", "08:00", "12:00");
        filter.setClassroom("A101");
        byte[] csvBytes = scheduleService.exportSchedulesAsCsv(filter);
        String csvContent = new String(csvBytes, java.nio.charset.StandardCharsets.UTF_8);

        assertTrue(csvContent.contains("数学"));
        assertFalse(csvContent.contains("物理"));
        assertFalse(csvContent.contains("化学"));
    }

    private TeacherFreeTimeRequest createFreeTimeRequest(String teacherName, String weekday,
                                                          String startTimeFrom, String startTimeTo) {
        TeacherFreeTimeRequest request = new TeacherFreeTimeRequest();
        request.setTeacherName(teacherName);
        request.setWeekday(weekday);
        request.setStartTimeFrom(startTimeFrom);
        request.setStartTimeTo(startTimeTo);
        return request;
    }

    @Test
    void testGetTeacherFreeTime_TeacherHasCourses_PartiallyAvailable() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));
        scheduleService.addSchedule(createRequest("物理", "张老师", "B202", "周一 14:00-16:00"));
        scheduleService.addSchedule(createRequest("化学", "李老师", "C303", "周一 10:00-12:00"));

        TeacherFreeTimeRequest request = createFreeTimeRequest("张老师", "周一", "08:00", "18:00");
        TeacherFreeTimeResponse response = scheduleService.getTeacherFreeTime(request);

        assertEquals("张老师", response.getTeacherName());
        assertEquals("周一", response.getWeekday());
        assertEquals("08:00", response.getQueryStartTimeFrom());
        assertEquals("18:00", response.getQueryStartTimeTo());
        assertFalse(response.isFullyAvailable());
        assertEquals(2, response.getOccupiedSlots().size());
        assertEquals("数学", response.getOccupiedSlots().get(0).getCourseName());
        assertEquals("物理", response.getOccupiedSlots().get(1).getCourseName());
        assertEquals(2, response.getFreeTimeWindows().size());
        assertEquals("10:00", response.getFreeTimeWindows().get(0).getStartTime());
        assertEquals("14:00", response.getFreeTimeWindows().get(0).getEndTime());
        assertEquals("16:00", response.getFreeTimeWindows().get(1).getStartTime());
        assertEquals("18:00", response.getFreeTimeWindows().get(1).getEndTime());
    }

    @Test
    void testGetTeacherFreeTime_TeacherNoCourses_FullyAvailable() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));

        TeacherFreeTimeRequest request = createFreeTimeRequest("李老师", "周一", "08:00", "16:00");
        TeacherFreeTimeResponse response = scheduleService.getTeacherFreeTime(request);

        assertTrue(response.isFullyAvailable());
        assertTrue(response.getOccupiedSlots().isEmpty());
        assertEquals(1, response.getFreeTimeWindows().size());
        assertEquals("08:00", response.getFreeTimeWindows().get(0).getStartTime());
        assertEquals("16:00", response.getFreeTimeWindows().get(0).getEndTime());
    }

    @Test
    void testGetTeacherFreeTime_NoCoursesAtAll_FullyAvailable() {
        TeacherFreeTimeRequest request = createFreeTimeRequest("张老师", "周一", "08:00", "16:00");
        TeacherFreeTimeResponse response = scheduleService.getTeacherFreeTime(request);

        assertTrue(response.isFullyAvailable());
        assertTrue(response.getOccupiedSlots().isEmpty());
        assertEquals(1, response.getFreeTimeWindows().size());
        assertEquals("08:00", response.getFreeTimeWindows().get(0).getStartTime());
        assertEquals("16:00", response.getFreeTimeWindows().get(0).getEndTime());
    }

    @Test
    void testGetTeacherFreeTime_DifferentWeekday_FullyAvailable() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));

        TeacherFreeTimeRequest request = createFreeTimeRequest("张老师", "周二", "08:00", "16:00");
        TeacherFreeTimeResponse response = scheduleService.getTeacherFreeTime(request);

        assertTrue(response.isFullyAvailable());
        assertEquals("周二", response.getWeekday());
    }

    @Test
    void testGetTeacherFreeTime_MissingTeacherName_ThrowsException() {
        TeacherFreeTimeRequest request = createFreeTimeRequest(null, "周一", "08:00", "16:00");

        InvalidRequestParameterException ex = assertThrows(InvalidRequestParameterException.class,
                () -> scheduleService.getTeacherFreeTime(request));
        assertEquals("老师名不能为空", ex.getMessage());
    }

    @Test
    void testGetTeacherFreeTime_EmptyTeacherName_ThrowsException() {
        TeacherFreeTimeRequest request = createFreeTimeRequest("", "周一", "08:00", "16:00");

        InvalidRequestParameterException ex = assertThrows(InvalidRequestParameterException.class,
                () -> scheduleService.getTeacherFreeTime(request));
        assertEquals("老师名不能为空", ex.getMessage());
    }

    @Test
    void testGetTeacherFreeTime_MissingWeekday_ThrowsException() {
        TeacherFreeTimeRequest request = createFreeTimeRequest("张老师", null, "08:00", "16:00");

        InvalidRequestParameterException ex = assertThrows(InvalidRequestParameterException.class,
                () -> scheduleService.getTeacherFreeTime(request));
        assertEquals("周几不能为空", ex.getMessage());
    }

    @Test
    void testGetTeacherFreeTime_MissingStartTimeFrom_ThrowsException() {
        TeacherFreeTimeRequest request = createFreeTimeRequest("张老师", "周一", null, "16:00");

        InvalidRequestParameterException ex = assertThrows(InvalidRequestParameterException.class,
                () -> scheduleService.getTeacherFreeTime(request));
        assertEquals("开始时间不能为空", ex.getMessage());
    }

    @Test
    void testGetTeacherFreeTime_MissingStartTimeTo_ThrowsException() {
        TeacherFreeTimeRequest request = createFreeTimeRequest("张老师", "周一", "08:00", null);

        InvalidRequestParameterException ex = assertThrows(InvalidRequestParameterException.class,
                () -> scheduleService.getTeacherFreeTime(request));
        assertEquals("结束时间不能为空", ex.getMessage());
    }

    @Test
    void testGetTeacherFreeTime_InvalidWeekday_ThrowsException() {
        TeacherFreeTimeRequest request = createFreeTimeRequest("张老师", "周八", "08:00", "16:00");

        assertThrows(InvalidTimeSlotException.class, () -> scheduleService.getTeacherFreeTime(request));
    }

    @Test
    void testGetTeacherFreeTime_InvalidTimeFormat_ThrowsException() {
        TeacherFreeTimeRequest request = createFreeTimeRequest("张老师", "周一", "abc", "16:00");

        assertThrows(InvalidTimeSlotException.class, () -> scheduleService.getTeacherFreeTime(request));
    }

    @Test
    void testGetTeacherFreeTime_StartAfterEnd_ThrowsException() {
        TeacherFreeTimeRequest request = createFreeTimeRequest("张老师", "周一", "16:00", "08:00");

        assertThrows(InvalidTimeSlotException.class, () -> scheduleService.getTeacherFreeTime(request));
    }

    @Test
    void testGetTeacherFreeTime_NormalizedWeekdayInput() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));

        TeacherFreeTimeRequest request = createFreeTimeRequest("张老师", "星期一", "08:00", "16:00");
        TeacherFreeTimeResponse response = scheduleService.getTeacherFreeTime(request);

        assertEquals("周一", response.getWeekday());
        assertEquals(1, response.getOccupiedSlots().size());
    }

    @Test
    void testGetTeacherFreeTime_NumericWeekdayInput() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));

        TeacherFreeTimeRequest request = createFreeTimeRequest("张老师", "1", "08:00", "16:00");
        TeacherFreeTimeResponse response = scheduleService.getTeacherFreeTime(request);

        assertEquals("周一", response.getWeekday());
        assertEquals(1, response.getOccupiedSlots().size());
    }

    @Test
    void testExportTeacherFreeTimeAsCsv_HasCourses() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 10:00-12:00"));

        TeacherFreeTimeRequest request = createFreeTimeRequest("张老师", "周一", "08:00", "16:00");
        byte[] csvBytes = scheduleService.exportTeacherFreeTimeAsCsv(request);
        String csvContent = new String(csvBytes, java.nio.charset.StandardCharsets.UTF_8);

        assertTrue(csvContent.contains("\uFEFF"));
        assertTrue(csvContent.contains("老师"));
        assertTrue(csvContent.contains("有课"));
        assertTrue(csvContent.contains("空闲"));
        assertTrue(csvContent.contains("张老师"));
        assertTrue(csvContent.contains("数学"));
        assertTrue(csvContent.contains("A101"));
    }

    @Test
    void testExportTeacherFreeTimeAsCsv_FullyAvailable() {
        TeacherFreeTimeRequest request = createFreeTimeRequest("张老师", "周一", "08:00", "16:00");
        byte[] csvBytes = scheduleService.exportTeacherFreeTimeAsCsv(request);
        String csvContent = new String(csvBytes, java.nio.charset.StandardCharsets.UTF_8);

        assertFalse(csvContent.contains("有课"));
        assertTrue(csvContent.contains("空闲"));
        assertTrue(csvContent.contains("张老师"));
    }

    @Test
    void testExportTeacherFreeTimeAsCsv_InvalidParams_ThrowsException() {
        TeacherFreeTimeRequest request = createFreeTimeRequest(null, "周一", "08:00", "16:00");

        assertThrows(InvalidRequestParameterException.class,
                () -> scheduleService.exportTeacherFreeTimeAsCsv(request));
    }

    @Test
    void testExportTeacherFreeTimeAsCsv_ChronologicalOrder() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));
        scheduleService.addSchedule(createRequest("物理", "张老师", "B202", "周一 14:00-16:00"));

        TeacherFreeTimeRequest request = createFreeTimeRequest("张老师", "周一", "08:00", "18:00");
        byte[] csvBytes = scheduleService.exportTeacherFreeTimeAsCsv(request);
        String csvContent = new String(csvBytes, java.nio.charset.StandardCharsets.UTF_8);

        String[] lines = csvContent.split("\n");
        int dataStart = 0;
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].contains("时间段类型")) {
                dataStart = i + 1;
                break;
            }
        }

        java.util.List<String> dataLines = new java.util.ArrayList<>();
        for (int i = dataStart; i < lines.length; i++) {
            if (!lines[i].trim().isEmpty()) {
                dataLines.add(lines[i]);
            }
        }

        assertEquals(4, dataLines.size());

        assertTrue(dataLines.get(0).contains("有课"));
        assertTrue(dataLines.get(0).contains("08:00"));
        assertTrue(dataLines.get(0).contains("10:00"));
        assertTrue(dataLines.get(0).contains("数学"));

        assertTrue(dataLines.get(1).contains("空闲"));
        assertTrue(dataLines.get(1).contains("10:00"));
        assertTrue(dataLines.get(1).contains("14:00"));

        assertTrue(dataLines.get(2).contains("有课"));
        assertTrue(dataLines.get(2).contains("14:00"));
        assertTrue(dataLines.get(2).contains("16:00"));
        assertTrue(dataLines.get(2).contains("物理"));

        assertTrue(dataLines.get(3).contains("空闲"));
        assertTrue(dataLines.get(3).contains("16:00"));
        assertTrue(dataLines.get(3).contains("18:00"));
    }

    @Test
    void testExportTeacherFreeTimeAsCsv_ChronologicalOrder_FreeFirst() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 10:00-12:00"));

        TeacherFreeTimeRequest request = createFreeTimeRequest("张老师", "周一", "08:00", "16:00");
        byte[] csvBytes = scheduleService.exportTeacherFreeTimeAsCsv(request);
        String csvContent = new String(csvBytes, java.nio.charset.StandardCharsets.UTF_8);

        String[] lines = csvContent.split("\n");
        int dataStart = 0;
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].contains("时间段类型")) {
                dataStart = i + 1;
                break;
            }
        }

        java.util.List<String> dataLines = new java.util.ArrayList<>();
        for (int i = dataStart; i < lines.length; i++) {
            if (!lines[i].trim().isEmpty()) {
                dataLines.add(lines[i]);
            }
        }

        assertEquals(3, dataLines.size());

        assertTrue(dataLines.get(0).contains("空闲"));
        assertTrue(dataLines.get(0).contains("08:00"));
        assertTrue(dataLines.get(0).contains("10:00"));

        assertTrue(dataLines.get(1).contains("有课"));
        assertTrue(dataLines.get(1).contains("10:00"));
        assertTrue(dataLines.get(1).contains("12:00"));

        assertTrue(dataLines.get(2).contains("空闲"));
        assertTrue(dataLines.get(2).contains("12:00"));
        assertTrue(dataLines.get(2).contains("16:00"));
    }

    @Test
    void testAuditLog_CreateSchedule_Success() {
        CourseScheduleCreateRequest request = createRequest("数学", "张老师", "A101", "周一 08:00-10:00");
        scheduleService.addSchedule(request);

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
    }

    @Test
    void testAuditLog_CreateSchedule_Failure_TeacherConflict() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));

        CourseScheduleCreateRequest request2 = createRequest("物理", "张老师", "B202", "周一 08:00-10:00");
        assertThrows(TeacherConflictException.class, () -> {
            scheduleService.addSchedule(request2);
        });

        List<AuditLogResponse> logs = auditLogService.getAllLogs();
        assertEquals(2, logs.size());

        AuditLogResponse failureLog = logs.get(0);
        assertEquals(OperationType.CREATE, failureLog.getOperationType());
        assertFalse(failureLog.isSuccess());
        assertTrue(failureLog.getErrorMessage().contains("张老师"));
        assertTrue(failureLog.getErrorMessage().contains("周一 08:00-10:00"));
    }

    @Test
    void testAuditLog_CreateSchedule_Failure_ValidationError() {
        CourseScheduleCreateRequest request = createRequest("", "张老师", "A101", "周一 08:00-10:00");
        assertThrows(InvalidRequestParameterException.class, () -> {
            scheduleService.addSchedule(request);
        });

        List<AuditLogResponse> logs = auditLogService.getAllLogs();
        assertEquals(1, logs.size());
        AuditLogResponse log = logs.get(0);
        assertEquals(OperationType.CREATE, log.getOperationType());
        assertFalse(log.isSuccess());
        assertEquals("课程名不能为空", log.getErrorMessage());
    }

    @Test
    void testAuditLog_UpdateSchedule_Success() {
        CourseScheduleResponse added = scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));

        CourseScheduleUpdateRequest updateRequest = createUpdateRequest("高等数学", "李老师", "B202", "周二 14:00-16:00");
        scheduleService.updateSchedule(added.getId(), updateRequest);

        List<AuditLogResponse> logs = auditLogService.getAllLogs();
        assertEquals(2, logs.size());

        AuditLogResponse updateLog = logs.get(0);
        assertEquals(OperationType.UPDATE, updateLog.getOperationType());
        assertEquals(added.getId(), updateLog.getCourseId());
        assertEquals("高等数学", updateLog.getCourseName());
        assertEquals("李老师", updateLog.getTeacherName());
        assertEquals("B202", updateLog.getClassroom());
        assertEquals("周二 14:00-16:00", updateLog.getTimeSlot());
        assertTrue(updateLog.isSuccess());
    }

    @Test
    void testAuditLog_UpdateSchedule_Failure_NotFound() {
        CourseScheduleUpdateRequest updateRequest = createUpdateRequest("高等数学", "李老师", "B202", "周二 14:00-16:00");
        assertThrows(ScheduleNotFoundException.class, () -> {
            scheduleService.updateSchedule(999L, updateRequest);
        });

        List<AuditLogResponse> logs = auditLogService.getAllLogs();
        assertEquals(1, logs.size());
        AuditLogResponse log = logs.get(0);
        assertEquals(OperationType.UPDATE, log.getOperationType());
        assertEquals(999L, log.getCourseId());
        assertFalse(log.isSuccess());
        assertTrue(log.getErrorMessage().contains("999"));
    }

    @Test
    void testAuditLog_DeleteSchedule_Success() {
        CourseScheduleResponse added = scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));
        scheduleService.deleteSchedule(added.getId());

        List<AuditLogResponse> logs = auditLogService.getAllLogs();
        assertEquals(2, logs.size());

        AuditLogResponse deleteLog = logs.get(0);
        assertEquals(OperationType.DELETE, deleteLog.getOperationType());
        assertEquals(added.getId(), deleteLog.getCourseId());
        assertEquals("数学", deleteLog.getCourseName());
        assertEquals("张老师", deleteLog.getTeacherName());
        assertEquals("A101", deleteLog.getClassroom());
        assertEquals("周一 08:00-10:00", deleteLog.getTimeSlot());
        assertTrue(deleteLog.isSuccess());
    }

    @Test
    void testAuditLog_DeleteSchedule_Failure() {
        assertThrows(ScheduleNotFoundException.class, () -> {
            scheduleService.deleteSchedule(999L);
        });

        List<AuditLogResponse> logs = auditLogService.getAllLogs();
        assertEquals(1, logs.size());
        AuditLogResponse log = logs.get(0);
        assertEquals(OperationType.DELETE, log.getOperationType());
        assertEquals(999L, log.getCourseId());
        assertFalse(log.isSuccess());
    }

    @Test
    void testAuditLog_BatchCreate_SuccessAndFailure() {
        List<CourseScheduleCreateRequest> requests = new ArrayList<>();
        requests.add(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));
        requests.add(createRequest("", "李老师", "B202", "周二 14:00-16:00"));
        requests.add(createRequest("化学", "张老师", "A101", "周一 08:00-10:00"));

        CourseScheduleBatchResponse response = scheduleService.addSchedulesBatch(requests);

        assertEquals(1, response.getSuccessCount());
        assertEquals(2, response.getFailureCount());

        List<AuditLogResponse> logs = auditLogService.getAllLogs();
        assertEquals(3, logs.size());

        long successCount = logs.stream().filter(AuditLogResponse::isSuccess).count();
        long failureCount = logs.stream().filter(l -> !l.isSuccess()).count();
        assertEquals(1, successCount);
        assertEquals(2, failureCount);

        assertTrue(logs.stream().allMatch(l -> l.getOperationType() == OperationType.BATCH_CREATE));
    }

    @Test
    void testAuditLog_BatchDelete_SuccessAndFailure() {
        CourseScheduleResponse s1 = scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));
        CourseScheduleResponse s2 = scheduleService.addSchedule(createRequest("物理", "李老师", "B202", "周二 14:00-16:00"));

        List<Long> ids = new ArrayList<>();
        ids.add(s1.getId());
        ids.add(999L);
        ids.add(s2.getId());
        ids.add(-1L);

        CourseScheduleBatchDeleteResponse response = scheduleService.deleteSchedulesBatch(ids);

        assertEquals(2, response.getSuccessCount());
        assertEquals(2, response.getFailureCount());

        List<AuditLogResponse> logs = auditLogService.getAllLogs();
        assertEquals(6, logs.size());

        long batchDeleteSuccess = logs.stream()
                .filter(l -> l.getOperationType() == OperationType.BATCH_DELETE)
                .filter(AuditLogResponse::isSuccess)
                .count();
        long batchDeleteFailure = logs.stream()
                .filter(l -> l.getOperationType() == OperationType.BATCH_DELETE)
                .filter(l -> !l.isSuccess())
                .count();
        assertEquals(2, batchDeleteSuccess);
        assertEquals(2, batchDeleteFailure);
    }

    @Test
    void testAuditLog_BatchUpdateTimeSlot_Success() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));
        scheduleService.addSchedule(createRequest("物理", "李老师", "B202", "周一 08:00-10:00"));

        CourseScheduleBatchTimeSlotUpdateRequest request = createTimeSlotUpdateRequest(
                "周一 08:00-10:00", "周二 14:00-16:00");
        scheduleService.batchUpdateTimeSlot(request);

        List<AuditLogResponse> logs = auditLogService.getAllLogs();
        assertEquals(4, logs.size());

        long batchUpdateCount = logs.stream()
                .filter(l -> l.getOperationType() == OperationType.BATCH_UPDATE_TIME_SLOT)
                .filter(AuditLogResponse::isSuccess)
                .count();
        assertEquals(2, batchUpdateCount);
    }

    @Test
    void testAuditLog_BatchUpdateTimeSlot_Failure() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));
        scheduleService.addSchedule(createRequest("物理", "张老师", "B202", "周二 14:00-16:00"));

        CourseScheduleBatchTimeSlotUpdateRequest request = createTimeSlotUpdateRequest(
                "周一 08:00-10:00", "周二 14:00-16:00");

        assertThrows(TeacherConflictException.class, () -> {
            scheduleService.batchUpdateTimeSlot(request);
        });

        List<AuditLogResponse> logs = auditLogService.getAllLogs();
        assertEquals(3, logs.size());

        AuditLogResponse failureLog = logs.get(0);
        assertEquals(OperationType.BATCH_UPDATE_TIME_SLOT, failureLog.getOperationType());
        assertFalse(failureLog.isSuccess());
        assertTrue(failureLog.getErrorMessage().contains("张老师"));
    }

    @Test
    void testAuditLog_Undo_Success() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));
        scheduleService.addSchedule(createRequest("物理", "李老师", "B202", "周二 14:00-16:00"));

        scheduleService.undo();

        List<AuditLogResponse> logs = auditLogService.getAllLogs();
        assertEquals(3, logs.size());

        AuditLogResponse undoLog = logs.get(0);
        assertEquals(OperationType.UNDO, undoLog.getOperationType());
        assertTrue(undoLog.isSuccess());
        assertNull(undoLog.getCourseId());
        assertNull(undoLog.getCourseName());
    }

    @Test
    void testAuditLog_Undo_Failure() {
        assertThrows(NoUndoAvailableException.class, () -> {
            scheduleService.undo();
        });

        List<AuditLogResponse> logs = auditLogService.getAllLogs();
        assertEquals(1, logs.size());
        AuditLogResponse log = logs.get(0);
        assertEquals(OperationType.UNDO, log.getOperationType());
        assertFalse(log.isSuccess());
        assertEquals("没有可撤销的操作", log.getErrorMessage());
    }

    @Test
    void testAuditLog_ClearAllSchedules_RecordsBatchDeleteLogs() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));
        scheduleService.addSchedule(createRequest("物理", "李老师", "B202", "周二 14:00-16:00"));

        assertEquals(2, auditLogService.getAllLogs().size());

        scheduleService.clearAllSchedules();

        assertTrue(scheduleService.findSchedules(null).isEmpty());

        List<AuditLogResponse> allLogs = auditLogService.getAllLogs();
        assertEquals(4, allLogs.size());

        com.coursescheduler.dto.AuditLogFilterRequest deleteFilter = new com.coursescheduler.dto.AuditLogFilterRequest();
        deleteFilter.setOperationType(OperationType.BATCH_DELETE);
        List<AuditLogResponse> deleteLogs = auditLogService.queryLogs(deleteFilter);
        assertEquals(2, deleteLogs.size());
        for (AuditLogResponse log : deleteLogs) {
            assertTrue(log.isSuccess());
            assertNull(log.getErrorMessage());
        }
    }

    @Test
    void testAuditLog_ResetForTesting_ClearsEverything() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));
        scheduleService.addSchedule(createRequest("物理", "李老师", "B202", "周二 14:00-16:00"));

        assertEquals(2, auditLogService.getAllLogs().size());
        assertEquals(2, scheduleService.findSchedules(null).size());

        scheduleService.resetForTesting();

        assertTrue(auditLogService.getAllLogs().isEmpty());
        assertTrue(scheduleService.findSchedules(null).isEmpty());

        CourseScheduleResponse newOne = scheduleService.addSchedule(createRequest("新课", "新老师", "C303", "周三 09:00-11:00"));
        assertEquals(1L, newOne.getId());
    }

    @Test
    void testAuditLog_ClearAllLogs_ClearsLogs() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));
        scheduleService.addSchedule(createRequest("物理", "李老师", "B202", "周二 14:00-16:00"));

        assertEquals(2, auditLogService.getAllLogs().size());

        auditLogService.clearAllLogs();

        assertTrue(auditLogService.getAllLogs().isEmpty());
    }

    @Test
    void testAuditLog_FilterByOperationType() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));
        CourseScheduleResponse added = scheduleService.addSchedule(createRequest("物理", "李老师", "B202", "周二 14:00-16:00"));
        scheduleService.deleteSchedule(added.getId());

        com.coursescheduler.dto.AuditLogFilterRequest filter = new com.coursescheduler.dto.AuditLogFilterRequest();
        filter.setOperationType(OperationType.DELETE);
        List<AuditLogResponse> deleteLogs = auditLogService.queryLogs(filter);

        assertEquals(1, deleteLogs.size());
        assertEquals(OperationType.DELETE, deleteLogs.get(0).getOperationType());
    }

    @Test
    void testAuditLog_FilterBySuccess() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));
        CourseScheduleCreateRequest badRequest = createRequest("", "李老师", "B202", "周二 14:00-16:00");
        assertThrows(InvalidRequestParameterException.class, () -> {
            scheduleService.addSchedule(badRequest);
        });

        com.coursescheduler.dto.AuditLogFilterRequest successFilter = new com.coursescheduler.dto.AuditLogFilterRequest();
        successFilter.setSuccess(true);
        assertEquals(1, auditLogService.queryLogs(successFilter).size());

        com.coursescheduler.dto.AuditLogFilterRequest failureFilter = new com.coursescheduler.dto.AuditLogFilterRequest();
        failureFilter.setSuccess(false);
        assertEquals(1, auditLogService.queryLogs(failureFilter).size());
    }

    @Test
    void testAuditLog_BatchCreate_NullList() {
        assertThrows(InvalidRequestParameterException.class, () -> {
            scheduleService.addSchedulesBatch(null);
        });

        List<AuditLogResponse> logs = auditLogService.getAllLogs();
        assertEquals(1, logs.size());
        AuditLogResponse log = logs.get(0);
        assertEquals(OperationType.BATCH_CREATE, log.getOperationType());
        assertFalse(log.isSuccess());
        assertEquals("请求列表不能为空", log.getErrorMessage());
        assertNull(log.getCourseId());
        assertNull(log.getCourseName());
    }

    @Test
    void testAuditLog_BatchUpdateTimeSlot_NullRequest() {
        assertThrows(InvalidRequestParameterException.class, () -> {
            scheduleService.batchUpdateTimeSlot(null);
        });

        List<AuditLogResponse> logs = auditLogService.getAllLogs();
        assertEquals(1, logs.size());
        AuditLogResponse log = logs.get(0);
        assertEquals(OperationType.BATCH_UPDATE_TIME_SLOT, log.getOperationType());
        assertFalse(log.isSuccess());
        assertEquals("请求不能为空", log.getErrorMessage());
    }

    @Test
    void testAuditLog_BatchUpdateTimeSlot_EmptyFromTimeSlot() {
        CourseScheduleBatchTimeSlotUpdateRequest request = createTimeSlotUpdateRequest("", "周二 14:00-16:00");

        assertThrows(InvalidRequestParameterException.class, () -> {
            scheduleService.batchUpdateTimeSlot(request);
        });

        List<AuditLogResponse> logs = auditLogService.getAllLogs();
        assertEquals(1, logs.size());
        AuditLogResponse log = logs.get(0);
        assertEquals(OperationType.BATCH_UPDATE_TIME_SLOT, log.getOperationType());
        assertFalse(log.isSuccess());
        assertEquals("原时间段不能为空", log.getErrorMessage());
    }

    @Test
    void testAuditLog_BatchUpdateTimeSlot_EmptyToTimeSlot() {
        CourseScheduleBatchTimeSlotUpdateRequest request = createTimeSlotUpdateRequest("周一 08:00-10:00", "");

        assertThrows(InvalidRequestParameterException.class, () -> {
            scheduleService.batchUpdateTimeSlot(request);
        });

        List<AuditLogResponse> logs = auditLogService.getAllLogs();
        assertEquals(1, logs.size());
        AuditLogResponse log = logs.get(0);
        assertEquals(OperationType.BATCH_UPDATE_TIME_SLOT, log.getOperationType());
        assertFalse(log.isSuccess());
        assertEquals("目标时间段不能为空", log.getErrorMessage());
        assertEquals("周一 08:00-10:00", log.getTimeSlot());
    }

    @Test
    void testAuditLog_BatchUpdateTimeSlot_InvalidFromTimeSlot() {
        CourseScheduleBatchTimeSlotUpdateRequest request = createTimeSlotUpdateRequest("非法时间段", "周二 14:00-16:00");

        assertThrows(InvalidTimeSlotException.class, () -> {
            scheduleService.batchUpdateTimeSlot(request);
        });

        List<AuditLogResponse> logs = auditLogService.getAllLogs();
        assertEquals(1, logs.size());
        AuditLogResponse log = logs.get(0);
        assertEquals(OperationType.BATCH_UPDATE_TIME_SLOT, log.getOperationType());
        assertFalse(log.isSuccess());
        assertTrue(log.getErrorMessage().contains("格式不合法"));
        assertEquals("非法时间段", log.getTimeSlot());
    }

    @Test
    void testAuditLog_BatchUpdateTimeSlot_InvalidToTimeSlot() {
        CourseScheduleBatchTimeSlotUpdateRequest request = createTimeSlotUpdateRequest("周一 08:00-10:00", "随便写");

        assertThrows(InvalidTimeSlotException.class, () -> {
            scheduleService.batchUpdateTimeSlot(request);
        });

        List<AuditLogResponse> logs = auditLogService.getAllLogs();
        assertEquals(1, logs.size());
        AuditLogResponse log = logs.get(0);
        assertEquals(OperationType.BATCH_UPDATE_TIME_SLOT, log.getOperationType());
        assertFalse(log.isSuccess());
        assertTrue(log.getErrorMessage().contains("格式不合法"));
        assertEquals("随便写", log.getTimeSlot());
    }

    @Test
    void testAuditLog_BatchUpdateTimeSlot_SameFromAndTo() {
        CourseScheduleBatchTimeSlotUpdateRequest request = createTimeSlotUpdateRequest(
                "周一 08:00-10:00", "周一 08:00-10:00");

        CourseScheduleBatchTimeSlotUpdateResponse response = scheduleService.batchUpdateTimeSlot(request);

        assertEquals(0, response.getUpdatedCount());

        List<AuditLogResponse> logs = auditLogService.getAllLogs();
        assertEquals(1, logs.size());
        AuditLogResponse log = logs.get(0);
        assertEquals(OperationType.BATCH_UPDATE_TIME_SLOT, log.getOperationType());
        assertFalse(log.isSuccess());
        assertEquals("原时间段与目标时间段相同", log.getErrorMessage());
        assertEquals("周一 08:00-10:00", log.getTimeSlot());
    }

    @Test
    void testAuditLog_BatchUpdateTimeSlot_NoMatch() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));

        CourseScheduleBatchTimeSlotUpdateRequest request = createTimeSlotUpdateRequest(
                "周二 10:00-12:00", "周三 14:00-16:00");

        CourseScheduleBatchTimeSlotUpdateResponse response = scheduleService.batchUpdateTimeSlot(request);

        assertEquals(0, response.getMatchedCount());

        List<AuditLogResponse> logs = auditLogService.getAllLogs();
        assertEquals(2, logs.size());

        AuditLogResponse failureLog = logs.get(0);
        assertEquals(OperationType.BATCH_UPDATE_TIME_SLOT, failureLog.getOperationType());
        assertFalse(failureLog.isSuccess());
        assertEquals("没有匹配的课程", failureLog.getErrorMessage());
        assertEquals("周二 10:00-12:00", failureLog.getTimeSlot());
    }

    @Test
    void testGetClassroomWeeklySchedule_SortedByWeekdayAndStartTime() {
        scheduleService.addSchedule(createRequest("英语", "赵老师", "A101", "周三 14:00-16:00"));
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 10:00-12:00"));
        scheduleService.addSchedule(createRequest("物理", "李老师", "A101", "周一 08:00-10:00"));
        scheduleService.addSchedule(createRequest("化学", "王老师", "A101", "周二 08:00-10:00"));
        scheduleService.addSchedule(createRequest("生物", "孙老师", "B202", "周一 08:00-10:00"));

        List<CourseScheduleResponse> result = scheduleService.getClassroomWeeklySchedule("A101");

        assertEquals(4, result.size());
        assertEquals("物理", result.get(0).getCourseName());
        assertEquals("周一 08:00-10:00", result.get(0).getTimeSlot());
        assertEquals("数学", result.get(1).getCourseName());
        assertEquals("周一 10:00-12:00", result.get(1).getTimeSlot());
        assertEquals("化学", result.get(2).getCourseName());
        assertEquals("周二 08:00-10:00", result.get(2).getTimeSlot());
        assertEquals("英语", result.get(3).getCourseName());
        assertEquals("周三 14:00-16:00", result.get(3).getTimeSlot());
    }

    @Test
    void testGetClassroomWeeklySchedule_SpansFullWeek_SundayLast() {
        scheduleService.addSchedule(createRequest("周日课", "赵老师", "A101", "周日 08:00-10:00"));
        scheduleService.addSchedule(createRequest("周一课", "张老师", "A101", "周一 08:00-10:00"));
        scheduleService.addSchedule(createRequest("周五课", "李老师", "A101", "周五 08:00-10:00"));
        scheduleService.addSchedule(createRequest("周三课", "王老师", "A101", "周三 08:00-10:00"));

        List<CourseScheduleResponse> result = scheduleService.getClassroomWeeklySchedule("A101");

        assertEquals(4, result.size());
        assertEquals("周一课", result.get(0).getCourseName());
        assertEquals("周三课", result.get(1).getCourseName());
        assertEquals("周五课", result.get(2).getCourseName());
        assertEquals("周日课", result.get(3).getCourseName());
    }

    @Test
    void testGetClassroomWeeklySchedule_EmptyResult() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));

        List<CourseScheduleResponse> result = scheduleService.getClassroomWeeklySchedule("B202");

        assertTrue(result.isEmpty());
    }

    @Test
    void testGetClassroomWeeklySchedule_NoSchedulesAtAll() {
        List<CourseScheduleResponse> result = scheduleService.getClassroomWeeklySchedule("A101");

        assertTrue(result.isEmpty());
    }

    @Test
    void testGetClassroomWeeklySchedule_NullClassroom_ThrowsException() {
        InvalidRequestParameterException exception = assertThrows(InvalidRequestParameterException.class, () -> {
            scheduleService.getClassroomWeeklySchedule(null);
        });
        assertEquals("教室名不能为空", exception.getMessage());
    }

    @Test
    void testGetClassroomWeeklySchedule_EmptyClassroom_ThrowsException() {
        InvalidRequestParameterException exception = assertThrows(InvalidRequestParameterException.class, () -> {
            scheduleService.getClassroomWeeklySchedule("");
        });
        assertEquals("教室名不能为空", exception.getMessage());
    }

    @Test
    void testGetClassroomWeeklySchedule_BlankClassroom_ThrowsException() {
        InvalidRequestParameterException exception = assertThrows(InvalidRequestParameterException.class, () -> {
            scheduleService.getClassroomWeeklySchedule("   ");
        });
        assertEquals("教室名不能为空", exception.getMessage());
    }

    @Test
    void testGetClassroomWeeklySchedule_TrimmedClassroom() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));

        List<CourseScheduleResponse> result = scheduleService.getClassroomWeeklySchedule("  A101  ");

        assertEquals(1, result.size());
        assertEquals("数学", result.get(0).getCourseName());
    }

    @Test
    void testGetClassroomWeeklySchedule_ResponseStructure() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));

        List<CourseScheduleResponse> result = scheduleService.getClassroomWeeklySchedule("A101");

        assertEquals(1, result.size());
        CourseScheduleResponse response = result.get(0);
        assertNotNull(response.getId());
        assertEquals("数学", response.getCourseName());
        assertEquals("张老师", response.getTeacherName());
        assertEquals("A101", response.getClassroom());
        assertEquals("周一 08:00-10:00", response.getTimeSlot());
    }

    @Test
    void testGetClassroomWeeklySchedule_OnlyMatchesExactClassroom() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));
        scheduleService.addSchedule(createRequest("物理", "李老师", "A1011", "周一 08:00-10:00"));
        scheduleService.addSchedule(createRequest("化学", "王老师", "教学楼A101", "周一 08:00-10:00"));

        List<CourseScheduleResponse> result = scheduleService.getClassroomWeeklySchedule("A101");

        assertEquals(1, result.size());
        assertEquals("数学", result.get(0).getCourseName());
    }

    @Test
    void testGetClassroomWeeklySchedule_SameDaySortedByStartTime() {
        scheduleService.addSchedule(createRequest("英语", "赵老师", "A101", "周一 14:00-16:00"));
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));
        scheduleService.addSchedule(createRequest("物理", "李老师", "A101", "周一 10:00-12:00"));

        List<CourseScheduleResponse> result = scheduleService.getClassroomWeeklySchedule("A101");

        assertEquals(3, result.size());
        assertEquals("数学", result.get(0).getCourseName());
        assertEquals("物理", result.get(1).getCourseName());
        assertEquals("英语", result.get(2).getCourseName());
    }

    @Test
    void testGetTeacherWeeklySchedule_SortedByWeekdayAndStartTime() {
        scheduleService.addSchedule(createRequest("英语", "张老师", "C303", "周三 14:00-16:00"));
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 10:00-12:00"));
        scheduleService.addSchedule(createRequest("物理", "张老师", "B202", "周一 08:00-10:00"));
        scheduleService.addSchedule(createRequest("化学", "张老师", "D404", "周二 08:00-10:00"));
        scheduleService.addSchedule(createRequest("生物", "李老师", "A101", "周一 08:00-10:00"));

        List<CourseScheduleResponse> result = scheduleService.getTeacherWeeklySchedule("张老师");

        assertEquals(4, result.size());
        assertEquals("物理", result.get(0).getCourseName());
        assertEquals("周一 08:00-10:00", result.get(0).getTimeSlot());
        assertEquals("数学", result.get(1).getCourseName());
        assertEquals("周一 10:00-12:00", result.get(1).getTimeSlot());
        assertEquals("化学", result.get(2).getCourseName());
        assertEquals("周二 08:00-10:00", result.get(2).getTimeSlot());
        assertEquals("英语", result.get(3).getCourseName());
        assertEquals("周三 14:00-16:00", result.get(3).getTimeSlot());
    }

    @Test
    void testGetTeacherWeeklySchedule_SpansFullWeek_SundayLast() {
        scheduleService.addSchedule(createRequest("周日课", "王老师", "A101", "周日 08:00-10:00"));
        scheduleService.addSchedule(createRequest("周一课", "王老师", "B202", "周一 08:00-10:00"));
        scheduleService.addSchedule(createRequest("周五课", "王老师", "C303", "周五 08:00-10:00"));
        scheduleService.addSchedule(createRequest("周三课", "王老师", "D404", "周三 08:00-10:00"));

        List<CourseScheduleResponse> result = scheduleService.getTeacherWeeklySchedule("王老师");

        assertEquals(4, result.size());
        assertEquals("周一课", result.get(0).getCourseName());
        assertEquals("周三课", result.get(1).getCourseName());
        assertEquals("周五课", result.get(2).getCourseName());
        assertEquals("周日课", result.get(3).getCourseName());
    }

    @Test
    void testGetTeacherWeeklySchedule_EmptyResult() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));

        List<CourseScheduleResponse> result = scheduleService.getTeacherWeeklySchedule("李老师");

        assertTrue(result.isEmpty());
    }

    @Test
    void testGetTeacherWeeklySchedule_NoSchedulesAtAll() {
        List<CourseScheduleResponse> result = scheduleService.getTeacherWeeklySchedule("张老师");

        assertTrue(result.isEmpty());
    }

    @Test
    void testGetTeacherWeeklySchedule_NullTeacherName_ThrowsException() {
        InvalidRequestParameterException exception = assertThrows(InvalidRequestParameterException.class, () -> {
            scheduleService.getTeacherWeeklySchedule(null);
        });
        assertEquals("老师名不能为空", exception.getMessage());
    }

    @Test
    void testGetTeacherWeeklySchedule_EmptyTeacherName_ThrowsException() {
        InvalidRequestParameterException exception = assertThrows(InvalidRequestParameterException.class, () -> {
            scheduleService.getTeacherWeeklySchedule("");
        });
        assertEquals("老师名不能为空", exception.getMessage());
    }

    @Test
    void testGetTeacherWeeklySchedule_BlankTeacherName_ThrowsException() {
        InvalidRequestParameterException exception = assertThrows(InvalidRequestParameterException.class, () -> {
            scheduleService.getTeacherWeeklySchedule("   ");
        });
        assertEquals("老师名不能为空", exception.getMessage());
    }

    @Test
    void testGetTeacherWeeklySchedule_TrimmedTeacherName() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));

        List<CourseScheduleResponse> result = scheduleService.getTeacherWeeklySchedule("  张老师  ");

        assertEquals(1, result.size());
        assertEquals("数学", result.get(0).getCourseName());
    }

    @Test
    void testGetTeacherWeeklySchedule_ResponseStructure() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));

        List<CourseScheduleResponse> result = scheduleService.getTeacherWeeklySchedule("张老师");

        assertEquals(1, result.size());
        CourseScheduleResponse response = result.get(0);
        assertNotNull(response.getId());
        assertEquals("数学", response.getCourseName());
        assertEquals("张老师", response.getTeacherName());
        assertEquals("A101", response.getClassroom());
        assertEquals("周一 08:00-10:00", response.getTimeSlot());
    }

    @Test
    void testGetTeacherWeeklySchedule_OnlyMatchesExactTeacherName() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));
        scheduleService.addSchedule(createRequest("物理", "张教授", "B202", "周一 08:00-10:00"));
        scheduleService.addSchedule(createRequest("化学", "李张老师", "C303", "周一 08:00-10:00"));

        List<CourseScheduleResponse> result = scheduleService.getTeacherWeeklySchedule("张老师");

        assertEquals(1, result.size());
        assertEquals("数学", result.get(0).getCourseName());
    }

    @Test
    void testGetTeacherWeeklySchedule_SameDaySortedByStartTime() {
        scheduleService.addSchedule(createRequest("英语", "王老师", "C303", "周一 14:00-16:00"));
        scheduleService.addSchedule(createRequest("数学", "王老师", "A101", "周一 08:00-10:00"));
        scheduleService.addSchedule(createRequest("物理", "王老师", "B202", "周一 10:00-12:00"));

        List<CourseScheduleResponse> result = scheduleService.getTeacherWeeklySchedule("王老师");

        assertEquals(3, result.size());
        assertEquals("数学", result.get(0).getCourseName());
        assertEquals("物理", result.get(1).getCourseName());
        assertEquals("英语", result.get(2).getCourseName());
    }

    @Test
    void testExportTeacherWeeklyScheduleAsCsv_ExportOrder_WeekdayThenStartTime() {
        scheduleService.addSchedule(createRequest("周日课", "张老师", "A101", "周日 08:00-10:00"));
        scheduleService.addSchedule(createRequest("周一下午", "张老师", "B202", "周一 14:00-16:00"));
        scheduleService.addSchedule(createRequest("周一上午", "张老师", "C303", "周一 08:00-10:00"));
        scheduleService.addSchedule(createRequest("周三课", "张老师", "D404", "周三 10:00-12:00"));

        byte[] csvBytes = scheduleService.exportTeacherWeeklyScheduleAsCsv("张老师");
        String csvContent = new String(csvBytes, java.nio.charset.StandardCharsets.UTF_8);

        assertTrue(csvContent.startsWith("\uFEFF"));
        String csvWithoutBom = csvContent.startsWith("\uFEFF") ? csvContent.substring(1) : csvContent;
        String[] lines = csvWithoutBom.split("\n");

        assertEquals("课程名称,老师,教室,时间段", lines[0].trim());
        assertEquals(5, lines.length);
        assertTrue(lines[1].contains("周一上午"));
        assertTrue(lines[1].contains("周一 08:00-10:00"));
        assertTrue(lines[2].contains("周一下午"));
        assertTrue(lines[2].contains("周一 14:00-16:00"));
        assertTrue(lines[3].contains("周三课"));
        assertTrue(lines[3].contains("周三 10:00-12:00"));
        assertTrue(lines[4].contains("周日课"));
        assertTrue(lines[4].contains("周日 08:00-10:00"));
    }

    @Test
    void testExportTeacherWeeklyScheduleAsCsv_EmptyResult_OnlyHeader() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));

        byte[] csvBytes = scheduleService.exportTeacherWeeklyScheduleAsCsv("李老师");
        String csvContent = new String(csvBytes, java.nio.charset.StandardCharsets.UTF_8);

        assertTrue(csvContent.startsWith("\uFEFF"));
        String csvWithoutBom = csvContent.startsWith("\uFEFF") ? csvContent.substring(1) : csvContent;
        String[] lines = csvWithoutBom.split("\n");
        assertEquals(1, lines.length);
        assertEquals("课程名称,老师,教室,时间段", lines[0].trim());
    }

    @Test
    void testExportTeacherWeeklyScheduleAsCsv_NullTeacherName_ThrowsException() {
        InvalidRequestParameterException exception = assertThrows(InvalidRequestParameterException.class, () -> {
            scheduleService.exportTeacherWeeklyScheduleAsCsv(null);
        });
        assertEquals("老师名不能为空", exception.getMessage());
    }

    @Test
    void testExportTeacherWeeklyScheduleAsCsv_EmptyTeacherName_ThrowsException() {
        InvalidRequestParameterException exception = assertThrows(InvalidRequestParameterException.class, () -> {
            scheduleService.exportTeacherWeeklyScheduleAsCsv("");
        });
        assertEquals("老师名不能为空", exception.getMessage());
    }

    @Test
    void testExportTeacherWeeklyScheduleAsCsv_BlankTeacherName_ThrowsException() {
        InvalidRequestParameterException exception = assertThrows(InvalidRequestParameterException.class, () -> {
            scheduleService.exportTeacherWeeklyScheduleAsCsv("   ");
        });
        assertEquals("老师名不能为空", exception.getMessage());
    }

    @Test
    void testExportTeacherWeeklyScheduleAsCsv_FieldEscaping_CommaAndQuote() {
        scheduleService.addSchedule(createRequest("数学,高等", "张\"教授\"", "教,室 101", "周一 08:00-10:00"));

        byte[] csvBytes = scheduleService.exportTeacherWeeklyScheduleAsCsv("张\"教授\"");
        String actualCsv = new String(csvBytes, java.nio.charset.StandardCharsets.UTF_8);

        StringBuilder expected = new StringBuilder();
        expected.append("\uFEFF");
        expected.append("课程名称,老师,教室,时间段\n");
        expected.append("\"数学,高等\"").append(',');
        expected.append("\"张\"\"教授\"\"\"").append(',');
        expected.append("\"教,室 101\"").append(',');
        expected.append("周一 08:00-10:00").append('\n');

        assertEquals(expected.toString(), actualCsv);
    }

    @Test
    void testExportTeacherWeeklyScheduleAsCsv_FieldEscaping_Newline() {
        scheduleService.addSchedule(createRequest("数学\n高等数学", "张\r教授", "教室\n101", "周一 08:00-10:00"));

        byte[] csvBytes = scheduleService.exportTeacherWeeklyScheduleAsCsv("张\r教授");
        String actualCsv = new String(csvBytes, java.nio.charset.StandardCharsets.UTF_8);

        StringBuilder expected = new StringBuilder();
        expected.append("\uFEFF");
        expected.append("课程名称,老师,教室,时间段\n");
        expected.append("\"数学\n高等数学\"").append(',');
        expected.append("\"张\r教授\"").append(',');
        expected.append("\"教室\n101\"").append(',');
        expected.append("周一 08:00-10:00").append('\n');

        assertEquals(expected.toString(), actualCsv);
    }

    @Test
    void testExportClassroomWeeklyScheduleAsCsv_ExportOrder_WeekdayThenStartTime() {
        scheduleService.addSchedule(createRequest("周日课", "张老师", "A101", "周日 08:00-10:00"));
        scheduleService.addSchedule(createRequest("周一下午", "李老师", "A101", "周一 14:00-16:00"));
        scheduleService.addSchedule(createRequest("周一上午", "王老师", "A101", "周一 08:00-10:00"));
        scheduleService.addSchedule(createRequest("周三课", "赵老师", "A101", "周三 10:00-12:00"));
        scheduleService.addSchedule(createRequest("其他教室课", "孙老师", "B202", "周一 09:00-11:00"));

        byte[] csvBytes = scheduleService.exportClassroomWeeklyScheduleAsCsv("A101");
        String csvContent = new String(csvBytes, java.nio.charset.StandardCharsets.UTF_8);

        assertTrue(csvContent.startsWith("\uFEFF"));
        assertFalse(csvContent.contains("其他教室课"));
        assertFalse(csvContent.contains("B202"));
        assertFalse(csvContent.contains("孙老师"));
        String csvWithoutBom = csvContent.startsWith("\uFEFF") ? csvContent.substring(1) : csvContent;
        String[] lines = csvWithoutBom.split("\n");

        assertEquals("课程名称,老师,教室,时间段", lines[0].trim());
        assertEquals(5, lines.length);
        assertEquals("周一上午,王老师,A101,周一 08:00-10:00", lines[1].trim());
        assertEquals("周一下午,李老师,A101,周一 14:00-16:00", lines[2].trim());
        assertEquals("周三课,赵老师,A101,周三 10:00-12:00", lines[3].trim());
        assertEquals("周日课,张老师,A101,周日 08:00-10:00", lines[4].trim());
    }

    @Test
    void testExportClassroomWeeklyScheduleAsCsv_EmptyResult_OnlyHeader() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));

        byte[] csvBytes = scheduleService.exportClassroomWeeklyScheduleAsCsv("B202");
        String csvContent = new String(csvBytes, java.nio.charset.StandardCharsets.UTF_8);

        assertTrue(csvContent.startsWith("\uFEFF"));
        String csvWithoutBom = csvContent.startsWith("\uFEFF") ? csvContent.substring(1) : csvContent;
        String[] lines = csvWithoutBom.split("\n");
        assertEquals(1, lines.length);
        assertEquals("课程名称,老师,教室,时间段", lines[0].trim());
    }

    @Test
    void testExportClassroomWeeklyScheduleAsCsv_NullClassroom_ThrowsException() {
        InvalidRequestParameterException exception = assertThrows(InvalidRequestParameterException.class, () -> {
            scheduleService.exportClassroomWeeklyScheduleAsCsv(null);
        });
        assertEquals("教室名不能为空", exception.getMessage());
    }

    @Test
    void testExportClassroomWeeklyScheduleAsCsv_EmptyClassroom_ThrowsException() {
        InvalidRequestParameterException exception = assertThrows(InvalidRequestParameterException.class, () -> {
            scheduleService.exportClassroomWeeklyScheduleAsCsv("");
        });
        assertEquals("教室名不能为空", exception.getMessage());
    }

    @Test
    void testExportClassroomWeeklyScheduleAsCsv_BlankClassroom_ThrowsException() {
        InvalidRequestParameterException exception = assertThrows(InvalidRequestParameterException.class, () -> {
            scheduleService.exportClassroomWeeklyScheduleAsCsv("   ");
        });
        assertEquals("教室名不能为空", exception.getMessage());
    }

    private CourseScheduleConflictPreCheckRequest createPreCheckRequest(
            String courseName, String teacherName, String classroom, String timeSlot) {
        CourseScheduleConflictPreCheckRequest request = new CourseScheduleConflictPreCheckRequest();
        request.setCourseName(courseName);
        request.setTeacherName(teacherName);
        request.setClassroom(classroom);
        request.setTimeSlot(timeSlot);
        return request;
    }

    @Test
    void testPreCheckConflicts_NoConflict() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));

        CourseScheduleConflictPreCheckRequest request = createPreCheckRequest(
                "物理", "李老师", "B202", "周二 14:00-16:00");

        CourseScheduleConflictPreCheckResponse response = scheduleService.preCheckConflicts(request);

        assertTrue(response.isCanSchedule());
        assertEquals(0, response.getConflictCount());
        assertTrue(response.getConflictDetails().isEmpty());

        assertEquals(1, scheduleService.findSchedules(null).size());
    }

    @Test
    void testPreCheckConflicts_TeacherConflict() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));

        CourseScheduleConflictPreCheckRequest request = createPreCheckRequest(
                "物理", "张老师", "B202", "周一 08:00-10:00");

        CourseScheduleConflictPreCheckResponse response = scheduleService.preCheckConflicts(request);

        assertFalse(response.isCanSchedule());
        assertEquals(1, response.getConflictCount());
        assertEquals(1, response.getConflictDetails().size());

        ConflictDetailDTO conflict = response.getConflictDetails().get(0);
        assertEquals(ConflictDetailDTO.ConflictType.TEACHER, conflict.getConflictType());
        assertEquals(ConflictDetailDTO.SourceType.EXISTING_COURSE, conflict.getSourceType());
        assertEquals(1L, conflict.getCourseId());
        assertEquals("数学", conflict.getCourseName());
        assertEquals("张老师", conflict.getTeacherName());
        assertEquals("A101", conflict.getClassroom());
        assertEquals("周一 08:00-10:00", conflict.getTimeSlot());
        assertEquals("老师 张老师 在时间段 周一 08:00-10:00 已有课程安排", conflict.getReason());

        assertEquals(1, scheduleService.findSchedules(null).size());
    }

    @Test
    void testPreCheckConflicts_ClassroomConflict() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));

        CourseScheduleConflictPreCheckRequest request = createPreCheckRequest(
                "物理", "李老师", "A101", "周一 08:00-10:00");

        CourseScheduleConflictPreCheckResponse response = scheduleService.preCheckConflicts(request);

        assertFalse(response.isCanSchedule());
        assertEquals(1, response.getConflictCount());
        assertEquals(1, response.getConflictDetails().size());

        ConflictDetailDTO conflict = response.getConflictDetails().get(0);
        assertEquals(ConflictDetailDTO.ConflictType.CLASSROOM, conflict.getConflictType());
        assertEquals(ConflictDetailDTO.SourceType.EXISTING_COURSE, conflict.getSourceType());
        assertEquals(1L, conflict.getCourseId());
        assertEquals("数学", conflict.getCourseName());
        assertEquals("张老师", conflict.getTeacherName());
        assertEquals("A101", conflict.getClassroom());
        assertEquals("周一 08:00-10:00", conflict.getTimeSlot());
        assertEquals("教室 A101 在时间段 周一 08:00-10:00 已有课程安排", conflict.getReason());

        assertEquals(1, scheduleService.findSchedules(null).size());
    }

    @Test
    void testPreCheckConflicts_BothTeacherAndClassroomConflict() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));

        CourseScheduleConflictPreCheckRequest request = createPreCheckRequest(
                "物理", "张老师", "A101", "周一 08:00-10:00");

        CourseScheduleConflictPreCheckResponse response = scheduleService.preCheckConflicts(request);

        assertFalse(response.isCanSchedule());
        assertEquals(2, response.getConflictCount());
        assertEquals(2, response.getConflictDetails().size());

        ConflictDetailDTO teacherConflict = response.getConflictDetails().get(0);
        assertEquals(ConflictDetailDTO.ConflictType.TEACHER, teacherConflict.getConflictType());
        assertEquals(ConflictDetailDTO.SourceType.EXISTING_COURSE, teacherConflict.getSourceType());
        assertEquals(1L, teacherConflict.getCourseId());

        ConflictDetailDTO classroomConflict = response.getConflictDetails().get(1);
        assertEquals(ConflictDetailDTO.ConflictType.CLASSROOM, classroomConflict.getConflictType());
        assertEquals(ConflictDetailDTO.SourceType.EXISTING_COURSE, classroomConflict.getSourceType());
        assertEquals(1L, classroomConflict.getCourseId());

        assertEquals(1, scheduleService.findSchedules(null).size());
    }

    @Test
    void testPreCheckConflicts_MultipleTeacherConflicts_SortedCorrectly() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周二 08:00-10:00"));
        scheduleService.addSchedule(createRequest("物理", "张老师", "B202", "周一 10:00-12:00"));
        scheduleService.addSchedule(createRequest("化学", "张老师", "C303", "周一 08:00-10:00"));

        CourseScheduleConflictPreCheckRequest request = createPreCheckRequest(
                "生物", "张老师", "D404", "周一 08:00-10:00");

        CourseScheduleConflictPreCheckResponse response = scheduleService.preCheckConflicts(request);

        assertFalse(response.isCanSchedule());
        assertEquals(1, response.getConflictCount());
        assertEquals(1, response.getConflictDetails().size());
        assertEquals(3L, response.getConflictDetails().get(0).getCourseId());
    }

    @Test
    void testPreCheckConflicts_SortingByTypeWeekdayStartTimeCourseId() {
        scheduleService.addSchedule(createRequest("课程1", "张老师", "A101", "周二 08:00-10:00"));
        scheduleService.addSchedule(createRequest("课程2", "李老师", "A101", "周一 08:00-10:00"));
        scheduleService.addSchedule(createRequest("课程3", "张老师", "B202", "周一 08:00-10:00"));

        CourseScheduleConflictPreCheckRequest request = createPreCheckRequest(
                "新课程", "张老师", "A101", "周一 08:00-10:00");

        CourseScheduleConflictPreCheckResponse response = scheduleService.preCheckConflicts(request);

        assertEquals(2, response.getConflictCount());
        assertEquals(ConflictDetailDTO.ConflictType.TEACHER, response.getConflictDetails().get(0).getConflictType());
        assertEquals(3L, response.getConflictDetails().get(0).getCourseId());
        assertEquals(ConflictDetailDTO.ConflictType.CLASSROOM, response.getConflictDetails().get(1).getConflictType());
        assertEquals(2L, response.getConflictDetails().get(1).getCourseId());
    }

    @Test
    void testPreCheckConflicts_EmptyCourseName_ThrowsException() {
        CourseScheduleConflictPreCheckRequest request = createPreCheckRequest(
                "", "张老师", "A101", "周一 08:00-10:00");

        InvalidRequestParameterException exception = assertThrows(InvalidRequestParameterException.class, () -> {
            scheduleService.preCheckConflicts(request);
        });
        assertEquals("课程名不能为空", exception.getMessage());
    }

    @Test
    void testPreCheckConflicts_EmptyTeacherName_ThrowsException() {
        CourseScheduleConflictPreCheckRequest request = createPreCheckRequest(
                "数学", "", "A101", "周一 08:00-10:00");

        InvalidRequestParameterException exception = assertThrows(InvalidRequestParameterException.class, () -> {
            scheduleService.preCheckConflicts(request);
        });
        assertEquals("老师名不能为空", exception.getMessage());
    }

    @Test
    void testPreCheckConflicts_EmptyClassroom_ThrowsException() {
        CourseScheduleConflictPreCheckRequest request = createPreCheckRequest(
                "数学", "张老师", "", "周一 08:00-10:00");

        InvalidRequestParameterException exception = assertThrows(InvalidRequestParameterException.class, () -> {
            scheduleService.preCheckConflicts(request);
        });
        assertEquals("教室名不能为空", exception.getMessage());
    }

    @Test
    void testPreCheckConflicts_EmptyTimeSlot_ThrowsException() {
        CourseScheduleConflictPreCheckRequest request = createPreCheckRequest(
                "数学", "张老师", "A101", "");

        InvalidRequestParameterException exception = assertThrows(InvalidRequestParameterException.class, () -> {
            scheduleService.preCheckConflicts(request);
        });
        assertEquals("时间段不能为空", exception.getMessage());
    }

    @Test
    void testPreCheckConflicts_NullRequest_ThrowsException() {
        InvalidRequestParameterException exception = assertThrows(InvalidRequestParameterException.class, () -> {
            scheduleService.preCheckConflicts(null);
        });
        assertEquals("课程名不能为空", exception.getMessage());
    }

    @Test
    void testPreCheckConflicts_InvalidTimeSlotFormat_ThrowsException() {
        CourseScheduleConflictPreCheckRequest request = createPreCheckRequest(
                "数学", "张老师", "A101", "明天上午");

        InvalidTimeSlotException exception = assertThrows(InvalidTimeSlotException.class, () -> {
            scheduleService.preCheckConflicts(request);
        });
        assertTrue(exception.getMessage().contains("格式不合法"));
    }

    @Test
    void testPreCheckConflicts_InvalidTimeSlotStartAfterEnd_ThrowsException() {
        CourseScheduleConflictPreCheckRequest request = createPreCheckRequest(
                "数学", "张老师", "A101", "周一 10:00-08:00");

        InvalidTimeSlotException exception = assertThrows(InvalidTimeSlotException.class, () -> {
            scheduleService.preCheckConflicts(request);
        });
        assertTrue(exception.getMessage().contains("必须早于"));
    }

    @Test
    void testPreCheckConflicts_NoAuditLogCreated() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));

        List<AuditLogResponse> logsBefore = auditLogService.getAllLogs();
        int logCountBefore = logsBefore.size();

        CourseScheduleConflictPreCheckRequest request = createPreCheckRequest(
                "物理", "张老师", "B202", "周一 08:00-10:00");
        scheduleService.preCheckConflicts(request);

        List<AuditLogResponse> logsAfter = auditLogService.getAllLogs();
        assertEquals(logCountBefore, logsAfter.size());
    }

    @Test
    void testPreCheckConflicts_NoCourseCreated() {
        CourseScheduleConflictPreCheckRequest request = createPreCheckRequest(
                "数学", "张老师", "A101", "周一 08:00-10:00");

        scheduleService.preCheckConflicts(request);

        assertEquals(0, scheduleService.findSchedules(null).size());
    }

    @Test
    void testPreCheckConflicts_TimeSlotNormalization() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));

        CourseScheduleConflictPreCheckRequest request = createPreCheckRequest(
                "物理", "张老师", "B202", "星期一 8:00-10:00");

        CourseScheduleConflictPreCheckResponse response = scheduleService.preCheckConflicts(request);

        assertFalse(response.isCanSchedule());
        assertEquals(1, response.getConflictCount());
    }

    @Test
    void testPreCheckConflicts_WhitespaceFieldsTrimmed() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));

        CourseScheduleConflictPreCheckRequest request = createPreCheckRequest(
                "  物理  ", "  张老师  ", "  B202  ", "周一 08:00-10:00");

        CourseScheduleConflictPreCheckResponse response = scheduleService.preCheckConflicts(request);

        assertFalse(response.isCanSchedule());
        assertEquals(1, response.getConflictCount());
    }

    @Test
    void testAddSchedule_TeacherOverlap_PartialTimeOverlap_ThrowsConflict() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));

        assertThrows(TeacherConflictException.class, () ->
                scheduleService.addSchedule(createRequest("物理", "张老师", "B202", "周一 09:00-11:00")));
    }

    @Test
    void testAddSchedule_ClassroomOverlap_PartialTimeOverlap_ThrowsConflict() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));

        assertThrows(ClassroomConflictException.class, () ->
                scheduleService.addSchedule(createRequest("物理", "李老师", "A101", "周一 09:00-11:00")));
    }

    @Test
    void testAddSchedule_BoundaryTouching_NoConflict() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));

        CourseScheduleResponse response = scheduleService.addSchedule(
                createRequest("物理", "张老师", "A101", "周一 10:00-12:00"));

        assertNotNull(response);
        assertEquals(2L, response.getId());
    }

    @Test
    void testAddSchedule_DifferentWeekday_NoConflict() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));

        CourseScheduleResponse response = scheduleService.addSchedule(
                createRequest("物理", "张老师", "A101", "周二 08:00-10:00"));

        assertNotNull(response);
        assertEquals(2L, response.getId());
    }

    @Test
    void testPreCheckConflicts_TeacherOverlap_PartialTimeOverlap() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));

        CourseScheduleConflictPreCheckRequest request = createPreCheckRequest(
                "物理", "张老师", "B202", "周一 09:00-11:00");

        CourseScheduleConflictPreCheckResponse response = scheduleService.preCheckConflicts(request);

        assertFalse(response.isCanSchedule());
        assertEquals(1, response.getConflictCount());
        assertEquals(ConflictDetailDTO.ConflictType.TEACHER, response.getConflictDetails().get(0).getConflictType());
        assertEquals(1L, response.getConflictDetails().get(0).getCourseId());
    }

    @Test
    void testPreCheckConflicts_ClassroomOverlap_PartialTimeOverlap() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));

        CourseScheduleConflictPreCheckRequest request = createPreCheckRequest(
                "物理", "李老师", "A101", "周一 09:00-11:00");

        CourseScheduleConflictPreCheckResponse response = scheduleService.preCheckConflicts(request);

        assertFalse(response.isCanSchedule());
        assertEquals(1, response.getConflictCount());
        assertEquals(ConflictDetailDTO.ConflictType.CLASSROOM, response.getConflictDetails().get(0).getConflictType());
        assertEquals(1L, response.getConflictDetails().get(0).getCourseId());
    }

    @Test
    void testPreCheckConflicts_BoundaryTouching_NoConflict() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));

        CourseScheduleConflictPreCheckRequest request = createPreCheckRequest(
                "物理", "张老师", "A101", "周一 10:00-12:00");

        CourseScheduleConflictPreCheckResponse response = scheduleService.preCheckConflicts(request);

        assertTrue(response.isCanSchedule());
        assertEquals(0, response.getConflictCount());
    }

    @Test
    void testPreCheckConflicts_DifferentWeekday_NoConflict() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));

        CourseScheduleConflictPreCheckRequest request = createPreCheckRequest(
                "物理", "张老师", "A101", "周二 08:00-10:00");

        CourseScheduleConflictPreCheckResponse response = scheduleService.preCheckConflicts(request);

        assertTrue(response.isCanSchedule());
        assertEquals(0, response.getConflictCount());
    }

    @Test
    void testUpdateSchedule_TeacherOverlap_PartialTimeOverlap_ThrowsConflict() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));
        CourseScheduleResponse created = scheduleService.addSchedule(
                createRequest("物理", "李老师", "B202", "周一 13:00-15:00"));

        CourseScheduleUpdateRequest updateRequest = new CourseScheduleUpdateRequest();
        updateRequest.setCourseName("物理");
        updateRequest.setTeacherName("张老师");
        updateRequest.setClassroom("B202");
        updateRequest.setTimeSlot("周一 09:00-11:00");

        assertThrows(TeacherConflictException.class, () ->
                scheduleService.updateSchedule(created.getId(), updateRequest));
    }

    @Test
    void testUpdateSchedule_BoundaryTouching_NoConflict() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));
        CourseScheduleResponse created = scheduleService.addSchedule(
                createRequest("物理", "张老师", "A101", "周一 13:00-15:00"));

        CourseScheduleUpdateRequest updateRequest = new CourseScheduleUpdateRequest();
        updateRequest.setCourseName("物理");
        updateRequest.setTeacherName("张老师");
        updateRequest.setClassroom("A101");
        updateRequest.setTimeSlot("周一 10:00-12:00");

        CourseScheduleResponse response = scheduleService.updateSchedule(created.getId(), updateRequest);
        assertEquals("周一 10:00-12:00", response.getTimeSlot());
    }

    private TeacherBatchPreCheckItemRequest createBatchItem(String courseName, String classroom, String timeSlot) {
        TeacherBatchPreCheckItemRequest item = new TeacherBatchPreCheckItemRequest();
        item.setCourseName(courseName);
        item.setClassroom(classroom);
        item.setTimeSlot(timeSlot);
        return item;
    }

    private TeacherBatchPreCheckRequest createBatchRequest(String teacherName, List<TeacherBatchPreCheckItemRequest> items) {
        TeacherBatchPreCheckRequest request = new TeacherBatchPreCheckRequest();
        request.setTeacherName(teacherName);
        request.setItems(items);
        return request;
    }

    @Test
    void testPreCheckByTeacherBatch_AllCanSchedule() {
        List<TeacherBatchPreCheckItemRequest> items = new ArrayList<>();
        items.add(createBatchItem("数学", "A101", "周一 08:00-10:00"));
        items.add(createBatchItem("物理", "A101", "周一 10:00-12:00"));
        items.add(createBatchItem("化学", "B202", "周二 08:00-10:00"));

        TeacherBatchPreCheckRequest request = createBatchRequest("张老师", items);
        BatchPreCheckResponse response = scheduleService.preCheckConflictsByTeacherBatch(request);

        assertTrue(response.isCanSchedule());
        assertEquals(0, response.getTotalConflictCount());
        assertEquals(3, response.getItems().size());

        for (int i = 0; i < 3; i++) {
            BatchPreCheckItemResponse item = response.getItems().get(i);
            assertEquals(i, item.getOriginalIndex());
            assertTrue(item.isCanSchedule());
            assertEquals(0, item.getConflictCount());
            assertTrue(item.getConflictDetails().isEmpty());
        }

        assertEquals(0, scheduleService.findSchedules(null).size());
    }

    @Test
    void testPreCheckByTeacherBatch_ConflictWithExisting() {
        scheduleService.addSchedule(createRequest("已有课程", "张老师", "A101", "周一 08:00-10:00"));

        List<TeacherBatchPreCheckItemRequest> items = new ArrayList<>();
        items.add(createBatchItem("数学", "B202", "周一 08:00-10:00"));
        items.add(createBatchItem("物理", "C303", "周二 08:00-10:00"));

        TeacherBatchPreCheckRequest request = createBatchRequest("张老师", items);
        BatchPreCheckResponse response = scheduleService.preCheckConflictsByTeacherBatch(request);

        assertFalse(response.isCanSchedule());
        assertEquals(1, response.getTotalConflictCount());
        assertEquals(2, response.getItems().size());

        BatchPreCheckItemResponse item0 = response.getItems().get(0);
        assertEquals(0, item0.getOriginalIndex());
        assertEquals("数学", item0.getCourseName());
        assertFalse(item0.isCanSchedule());
        assertEquals(1, item0.getConflictCount());
        assertEquals(1, item0.getConflictDetails().size());

        ConflictDetailDTO conflict0 = item0.getConflictDetails().get(0);
        assertEquals(ConflictDetailDTO.ConflictType.TEACHER, conflict0.getConflictType());
        assertEquals(ConflictDetailDTO.SourceType.EXISTING_COURSE, conflict0.getSourceType());
        assertEquals(1L, conflict0.getCourseId());
        assertNull(conflict0.getPendingIndex());
        assertEquals("已有课程", conflict0.getCourseName());
        assertEquals("老师 张老师 在时间段 周一 08:00-10:00 已有课程安排", conflict0.getReason());

        BatchPreCheckItemResponse item1 = response.getItems().get(1);
        assertEquals(1, item1.getOriginalIndex());
        assertTrue(item1.isCanSchedule());
        assertEquals(0, item1.getConflictCount());

        assertEquals(1, scheduleService.findSchedules(null).size());
    }

    @Test
    void testPreCheckByTeacherBatch_InternalConflict() {
        List<TeacherBatchPreCheckItemRequest> items = new ArrayList<>();
        items.add(createBatchItem("数学", "A101", "周一 08:00-10:00"));
        items.add(createBatchItem("物理", "A101", "周一 08:00-10:00"));

        TeacherBatchPreCheckRequest request = createBatchRequest("张老师", items);
        BatchPreCheckResponse response = scheduleService.preCheckConflictsByTeacherBatch(request);

        assertFalse(response.isCanSchedule());
        assertEquals(4, response.getTotalConflictCount());
        assertEquals(2, response.getItems().size());

        BatchPreCheckItemResponse item0 = response.getItems().get(0);
        assertEquals(0, item0.getOriginalIndex());
        assertFalse(item0.isCanSchedule());
        assertEquals(2, item0.getConflictCount());

        ConflictDetailDTO teacherConflict0 = item0.getConflictDetails().get(0);
        assertEquals(ConflictDetailDTO.ConflictType.TEACHER, teacherConflict0.getConflictType());
        assertEquals(ConflictDetailDTO.SourceType.PENDING_ITEM, teacherConflict0.getSourceType());
        assertNull(teacherConflict0.getCourseId());
        assertEquals(Integer.valueOf(1), teacherConflict0.getPendingIndex());
        assertEquals("物理", teacherConflict0.getCourseName());
        assertEquals("老师 张老师 在时间段 周一 08:00-10:00 与本次批量待排课程冲突", teacherConflict0.getReason());

        ConflictDetailDTO classroomConflict0 = item0.getConflictDetails().get(1);
        assertEquals(ConflictDetailDTO.ConflictType.CLASSROOM, classroomConflict0.getConflictType());
        assertEquals(ConflictDetailDTO.SourceType.PENDING_ITEM, classroomConflict0.getSourceType());
        assertNull(classroomConflict0.getCourseId());
        assertEquals(Integer.valueOf(1), classroomConflict0.getPendingIndex());
        assertEquals("物理", classroomConflict0.getCourseName());
        assertEquals("教室 A101 在时间段 周一 08:00-10:00 与本次批量待排课程冲突", classroomConflict0.getReason());

        BatchPreCheckItemResponse item1 = response.getItems().get(1);
        assertEquals(1, item1.getOriginalIndex());
        assertFalse(item1.isCanSchedule());
        assertEquals(2, item1.getConflictCount());

        assertEquals(0, scheduleService.findSchedules(null).size());
    }

    @Test
    void testPreCheckByTeacherBatch_MixedConflicts() {
        scheduleService.addSchedule(createRequest("已有课程", "张老师", "A101", "周一 08:00-10:00"));

        List<TeacherBatchPreCheckItemRequest> items = new ArrayList<>();
        items.add(createBatchItem("数学", "B202", "周一 08:00-10:00"));
        items.add(createBatchItem("物理", "C303", "周一 08:00-10:00"));
        items.add(createBatchItem("化学", "D404", "周二 08:00-10:00"));

        TeacherBatchPreCheckRequest request = createBatchRequest("张老师", items);
        BatchPreCheckResponse response = scheduleService.preCheckConflictsByTeacherBatch(request);

        assertFalse(response.isCanSchedule());
        assertEquals(4, response.getTotalConflictCount());
        assertEquals(3, response.getItems().size());

        BatchPreCheckItemResponse item0 = response.getItems().get(0);
        assertEquals(0, item0.getOriginalIndex());
        assertFalse(item0.isCanSchedule());
        assertEquals(2, item0.getConflictCount());

        BatchPreCheckItemResponse item1 = response.getItems().get(1);
        assertEquals(1, item1.getOriginalIndex());
        assertFalse(item1.isCanSchedule());
        assertEquals(2, item1.getConflictCount());

        BatchPreCheckItemResponse item2 = response.getItems().get(2);
        assertEquals(2, item2.getOriginalIndex());
        assertTrue(item2.isCanSchedule());
        assertEquals(0, item2.getConflictCount());

        assertEquals(1, scheduleService.findSchedules(null).size());
    }

    @Test
    void testPreCheckByTeacherBatch_EmptyList() {
        List<TeacherBatchPreCheckItemRequest> items = new ArrayList<>();
        TeacherBatchPreCheckRequest request = createBatchRequest("张老师", items);

        BatchPreCheckResponse response = scheduleService.preCheckConflictsByTeacherBatch(request);

        assertTrue(response.isCanSchedule());
        assertEquals(0, response.getTotalConflictCount());
        assertEquals(0, response.getItems().size());

        assertEquals(0, scheduleService.findSchedules(null).size());
    }

    @Test
    void testPreCheckByTeacherBatch_NullRequest_ThrowsException() {
        InvalidRequestParameterException exception = assertThrows(InvalidRequestParameterException.class, () ->
                scheduleService.preCheckConflictsByTeacherBatch(null));
        assertEquals("请求不能为空", exception.getMessage());
    }

    @Test
    void testPreCheckByTeacherBatch_NullTeacherName_ThrowsException() {
        List<TeacherBatchPreCheckItemRequest> items = new ArrayList<>();
        items.add(createBatchItem("数学", "A101", "周一 08:00-10:00"));

        TeacherBatchPreCheckRequest request = createBatchRequest(null, items);

        InvalidRequestParameterException exception = assertThrows(InvalidRequestParameterException.class, () ->
                scheduleService.preCheckConflictsByTeacherBatch(request));
        assertEquals("老师名不能为空", exception.getMessage());
    }

    @Test
    void testPreCheckByTeacherBatch_EmptyTeacherName_ThrowsException() {
        List<TeacherBatchPreCheckItemRequest> items = new ArrayList<>();
        items.add(createBatchItem("数学", "A101", "周一 08:00-10:00"));

        TeacherBatchPreCheckRequest request = createBatchRequest("", items);

        InvalidRequestParameterException exception = assertThrows(InvalidRequestParameterException.class, () ->
                scheduleService.preCheckConflictsByTeacherBatch(request));
        assertEquals("老师名不能为空", exception.getMessage());
    }

    @Test
    void testPreCheckByTeacherBatch_BlankTeacherName_ThrowsException() {
        List<TeacherBatchPreCheckItemRequest> items = new ArrayList<>();
        items.add(createBatchItem("数学", "A101", "周一 08:00-10:00"));

        TeacherBatchPreCheckRequest request = createBatchRequest("   ", items);

        InvalidRequestParameterException exception = assertThrows(InvalidRequestParameterException.class, () ->
                scheduleService.preCheckConflictsByTeacherBatch(request));
        assertEquals("老师名不能为空", exception.getMessage());
    }

    @Test
    void testPreCheckByTeacherBatch_NullItems_ThrowsException() {
        TeacherBatchPreCheckRequest request = createBatchRequest("张老师", null);

        InvalidRequestParameterException exception = assertThrows(InvalidRequestParameterException.class, () ->
                scheduleService.preCheckConflictsByTeacherBatch(request));
        assertEquals("待排课程列表不能为空", exception.getMessage());
    }

    @Test
    void testPreCheckByTeacherBatch_NullItem_ThrowsException() {
        List<TeacherBatchPreCheckItemRequest> items = new ArrayList<>();
        items.add(null);

        TeacherBatchPreCheckRequest request = createBatchRequest("张老师", items);

        InvalidRequestParameterException exception = assertThrows(InvalidRequestParameterException.class, () ->
                scheduleService.preCheckConflictsByTeacherBatch(request));
        assertEquals("第 1 项课程请求不能为空", exception.getMessage());
    }

    @Test
    void testPreCheckByTeacherBatch_EmptyCourseName_ThrowsException() {
        List<TeacherBatchPreCheckItemRequest> items = new ArrayList<>();
        items.add(createBatchItem("", "A101", "周一 08:00-10:00"));

        TeacherBatchPreCheckRequest request = createBatchRequest("张老师", items);

        InvalidRequestParameterException exception = assertThrows(InvalidRequestParameterException.class, () ->
                scheduleService.preCheckConflictsByTeacherBatch(request));
        assertEquals("第 1 项课程名不能为空", exception.getMessage());
    }

    @Test
    void testPreCheckByTeacherBatch_EmptyClassroom_ThrowsException() {
        List<TeacherBatchPreCheckItemRequest> items = new ArrayList<>();
        items.add(createBatchItem("数学", "", "周一 08:00-10:00"));

        TeacherBatchPreCheckRequest request = createBatchRequest("张老师", items);

        InvalidRequestParameterException exception = assertThrows(InvalidRequestParameterException.class, () ->
                scheduleService.preCheckConflictsByTeacherBatch(request));
        assertEquals("第 1 项教室名不能为空", exception.getMessage());
    }

    @Test
    void testPreCheckByTeacherBatch_EmptyTimeSlot_ThrowsException() {
        List<TeacherBatchPreCheckItemRequest> items = new ArrayList<>();
        items.add(createBatchItem("数学", "A101", ""));

        TeacherBatchPreCheckRequest request = createBatchRequest("张老师", items);

        InvalidRequestParameterException exception = assertThrows(InvalidRequestParameterException.class, () ->
                scheduleService.preCheckConflictsByTeacherBatch(request));
        assertEquals("第 1 项时间段不能为空", exception.getMessage());
    }

    @Test
    void testPreCheckByTeacherBatch_InvalidTimeSlotFormat_ThrowsException() {
        List<TeacherBatchPreCheckItemRequest> items = new ArrayList<>();
        items.add(createBatchItem("数学", "A101", "明天上午"));

        TeacherBatchPreCheckRequest request = createBatchRequest("张老师", items);

        InvalidTimeSlotException exception = assertThrows(InvalidTimeSlotException.class, () ->
                scheduleService.preCheckConflictsByTeacherBatch(request));
        assertTrue(exception.getMessage().contains("格式不合法"));
    }

    @Test
    void testPreCheckByTeacherBatch_InvalidTimeSlotStartAfterEnd_ThrowsException() {
        List<TeacherBatchPreCheckItemRequest> items = new ArrayList<>();
        items.add(createBatchItem("数学", "A101", "周一 10:00-08:00"));

        TeacherBatchPreCheckRequest request = createBatchRequest("张老师", items);

        InvalidTimeSlotException exception = assertThrows(InvalidTimeSlotException.class, () ->
                scheduleService.preCheckConflictsByTeacherBatch(request));
        assertTrue(exception.getMessage().contains("必须早于"));
    }

    @Test
    void testPreCheckByTeacherBatch_NoCourseCreated() {
        List<TeacherBatchPreCheckItemRequest> items = new ArrayList<>();
        items.add(createBatchItem("数学", "A101", "周一 08:00-10:00"));
        items.add(createBatchItem("物理", "B202", "周二 08:00-10:00"));

        TeacherBatchPreCheckRequest request = createBatchRequest("张老师", items);
        scheduleService.preCheckConflictsByTeacherBatch(request);

        assertEquals(0, scheduleService.findSchedules(null).size());
    }

    @Test
    void testPreCheckByTeacherBatch_NoAuditLogCreated() {
        scheduleService.addSchedule(createRequest("已有课程", "张老师", "A101", "周一 08:00-10:00"));

        List<AuditLogResponse> logsBefore = auditLogService.getAllLogs();
        int logCountBefore = logsBefore.size();

        List<TeacherBatchPreCheckItemRequest> items = new ArrayList<>();
        items.add(createBatchItem("数学", "B202", "周一 08:00-10:00"));
        items.add(createBatchItem("物理", "C303", "周一 08:00-10:00"));

        TeacherBatchPreCheckRequest request = createBatchRequest("张老师", items);
        scheduleService.preCheckConflictsByTeacherBatch(request);

        List<AuditLogResponse> logsAfter = auditLogService.getAllLogs();
        assertEquals(logCountBefore, logsAfter.size());
    }

    @Test
    void testPreCheckByTeacherBatch_ConflictDetailsSortedCorrectly() {
        scheduleService.addSchedule(createRequest("课程1", "张老师", "A101", "周一 08:00-10:00"));
        scheduleService.addSchedule(createRequest("课程2", "李老师", "B202", "周一 08:00-10:00"));

        List<TeacherBatchPreCheckItemRequest> items = new ArrayList<>();
        items.add(createBatchItem("数学", "B202", "周一 08:00-10:00"));

        TeacherBatchPreCheckRequest request = createBatchRequest("张老师", items);
        BatchPreCheckResponse response = scheduleService.preCheckConflictsByTeacherBatch(request);

        BatchPreCheckItemResponse item = response.getItems().get(0);
        assertEquals(2, item.getConflictCount());
        assertEquals(ConflictDetailDTO.ConflictType.TEACHER, item.getConflictDetails().get(0).getConflictType());
        assertEquals(ConflictDetailDTO.ConflictType.CLASSROOM, item.getConflictDetails().get(1).getConflictType());
    }

    @Test
    void testPreCheckByTeacherBatch_ResultsInInputOrder() {
        List<TeacherBatchPreCheckItemRequest> items = new ArrayList<>();
        items.add(createBatchItem("课程A", "A101", "周三 08:00-10:00"));
        items.add(createBatchItem("课程B", "B202", "周一 08:00-10:00"));
        items.add(createBatchItem("课程C", "C303", "周二 08:00-10:00"));

        TeacherBatchPreCheckRequest request = createBatchRequest("张老师", items);
        BatchPreCheckResponse response = scheduleService.preCheckConflictsByTeacherBatch(request);

        assertEquals(3, response.getItems().size());
        assertEquals(0, response.getItems().get(0).getOriginalIndex());
        assertEquals("课程A", response.getItems().get(0).getCourseName());
        assertEquals(1, response.getItems().get(1).getOriginalIndex());
        assertEquals("课程B", response.getItems().get(1).getCourseName());
        assertEquals(2, response.getItems().get(2).getOriginalIndex());
        assertEquals("课程C", response.getItems().get(2).getCourseName());
    }

    @Test
    void testPreCheckByTeacherBatch_WhitespaceFieldsTrimmed() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));

        List<TeacherBatchPreCheckItemRequest> items = new ArrayList<>();
        items.add(createBatchItem("  物理  ", "  B202  ", "  周一 08:00-10:00  "));

        TeacherBatchPreCheckRequest request = createBatchRequest("  张老师  ", items);
        BatchPreCheckResponse response = scheduleService.preCheckConflictsByTeacherBatch(request);

        assertFalse(response.isCanSchedule());
        assertEquals(1, response.getTotalConflictCount());
    }

    @Test
    void testPreCheckByTeacherBatch_TimeSlotNormalization() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));

        List<TeacherBatchPreCheckItemRequest> items = new ArrayList<>();
        items.add(createBatchItem("物理", "B202", "星期一 8:00-10:00"));

        TeacherBatchPreCheckRequest request = createBatchRequest("张老师", items);
        BatchPreCheckResponse response = scheduleService.preCheckConflictsByTeacherBatch(request);

        assertFalse(response.isCanSchedule());
        assertEquals(1, response.getTotalConflictCount());
    }

    @Test
    void testPreCheckByTeacherBatch_PartialTimeOverlap_ConflictWithExisting() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));

        List<TeacherBatchPreCheckItemRequest> items = new ArrayList<>();
        items.add(createBatchItem("物理", "B202", "周一 09:00-11:00"));

        TeacherBatchPreCheckRequest request = createBatchRequest("张老师", items);
        BatchPreCheckResponse response = scheduleService.preCheckConflictsByTeacherBatch(request);

        assertFalse(response.isCanSchedule());
        assertEquals(1, response.getTotalConflictCount());
    }

    @Test
    void testPreCheckByTeacherBatch_BoundaryTouching_NoConflict() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));

        List<TeacherBatchPreCheckItemRequest> items = new ArrayList<>();
        items.add(createBatchItem("物理", "A101", "周一 10:00-12:00"));

        TeacherBatchPreCheckRequest request = createBatchRequest("张老师", items);
        BatchPreCheckResponse response = scheduleService.preCheckConflictsByTeacherBatch(request);

        assertTrue(response.isCanSchedule());
        assertEquals(0, response.getTotalConflictCount());
    }

    private ClassroomBatchPreCheckItemRequest createClassroomBatchItem(String courseName, String teacherName, String timeSlot) {
        ClassroomBatchPreCheckItemRequest item = new ClassroomBatchPreCheckItemRequest();
        item.setCourseName(courseName);
        item.setTeacherName(teacherName);
        item.setTimeSlot(timeSlot);
        return item;
    }

    private ClassroomBatchPreCheckRequest createClassroomBatchRequest(String classroom, List<ClassroomBatchPreCheckItemRequest> items) {
        ClassroomBatchPreCheckRequest request = new ClassroomBatchPreCheckRequest();
        request.setClassroom(classroom);
        request.setItems(items);
        return request;
    }

    @Test
    void testPreCheckByClassroomBatch_AllCanSchedule() {
        List<ClassroomBatchPreCheckItemRequest> items = new ArrayList<>();
        items.add(createClassroomBatchItem("数学", "张老师", "周一 08:00-10:00"));
        items.add(createClassroomBatchItem("物理", "李老师", "周一 10:00-12:00"));
        items.add(createClassroomBatchItem("化学", "王老师", "周二 08:00-10:00"));

        ClassroomBatchPreCheckRequest request = createClassroomBatchRequest("A101", items);
        BatchPreCheckResponse response = scheduleService.preCheckConflictsByClassroomBatch(request);

        assertTrue(response.isCanSchedule());
        assertEquals(0, response.getTotalConflictCount());
        assertEquals(3, response.getItems().size());

        for (int i = 0; i < 3; i++) {
            BatchPreCheckItemResponse item = response.getItems().get(i);
            assertEquals(i, item.getOriginalIndex());
            assertTrue(item.isCanSchedule());
            assertEquals(0, item.getConflictCount());
            assertTrue(item.getConflictDetails().isEmpty());
        }

        assertEquals(0, scheduleService.findSchedules(null).size());
    }

    @Test
    void testPreCheckByClassroomBatch_ConflictWithExisting() {
        scheduleService.addSchedule(createRequest("已有课程", "张老师", "A101", "周一 08:00-10:00"));

        List<ClassroomBatchPreCheckItemRequest> items = new ArrayList<>();
        items.add(createClassroomBatchItem("数学", "张老师", "周一 08:00-10:00"));
        items.add(createClassroomBatchItem("物理", "李老师", "周二 08:00-10:00"));

        ClassroomBatchPreCheckRequest request = createClassroomBatchRequest("A101", items);
        BatchPreCheckResponse response = scheduleService.preCheckConflictsByClassroomBatch(request);

        assertFalse(response.isCanSchedule());
        assertEquals(2, response.getTotalConflictCount());
        assertEquals(2, response.getItems().size());

        BatchPreCheckItemResponse item0 = response.getItems().get(0);
        assertEquals(0, item0.getOriginalIndex());
        assertEquals("数学", item0.getCourseName());
        assertFalse(item0.isCanSchedule());
        assertEquals(2, item0.getConflictCount());
        assertEquals(2, item0.getConflictDetails().size());

        ConflictDetailDTO teacherConflict = item0.getConflictDetails().stream()
                .filter(c -> c.getConflictType() == ConflictDetailDTO.ConflictType.TEACHER)
                .findFirst().orElse(null);
        assertNotNull(teacherConflict);
        assertEquals(ConflictDetailDTO.SourceType.EXISTING_COURSE, teacherConflict.getSourceType());
        assertEquals(1L, teacherConflict.getCourseId());
        assertNull(teacherConflict.getPendingIndex());
        assertEquals("已有课程", teacherConflict.getCourseName());
        assertEquals("老师 张老师 在时间段 周一 08:00-10:00 已有课程安排", teacherConflict.getReason());

        ConflictDetailDTO classroomConflict = item0.getConflictDetails().stream()
                .filter(c -> c.getConflictType() == ConflictDetailDTO.ConflictType.CLASSROOM)
                .findFirst().orElse(null);
        assertNotNull(classroomConflict);
        assertEquals(ConflictDetailDTO.SourceType.EXISTING_COURSE, classroomConflict.getSourceType());
        assertEquals("教室 A101 在时间段 周一 08:00-10:00 已有课程安排", classroomConflict.getReason());

        BatchPreCheckItemResponse item1 = response.getItems().get(1);
        assertEquals(1, item1.getOriginalIndex());
        assertTrue(item1.isCanSchedule());
        assertEquals(0, item1.getConflictCount());

        assertEquals(1, scheduleService.findSchedules(null).size());
    }

    @Test
    void testPreCheckByClassroomBatch_InternalConflict() {
        List<ClassroomBatchPreCheckItemRequest> items = new ArrayList<>();
        items.add(createClassroomBatchItem("数学", "张老师", "周一 08:00-10:00"));
        items.add(createClassroomBatchItem("物理", "李老师", "周一 08:00-10:00"));

        ClassroomBatchPreCheckRequest request = createClassroomBatchRequest("A101", items);
        BatchPreCheckResponse response = scheduleService.preCheckConflictsByClassroomBatch(request);

        assertFalse(response.isCanSchedule());
        assertEquals(2, response.getTotalConflictCount());
        assertEquals(2, response.getItems().size());

        BatchPreCheckItemResponse item0 = response.getItems().get(0);
        assertEquals(0, item0.getOriginalIndex());
        assertFalse(item0.isCanSchedule());
        assertEquals(1, item0.getConflictCount());

        ConflictDetailDTO conflict0 = item0.getConflictDetails().get(0);
        assertEquals(ConflictDetailDTO.ConflictType.CLASSROOM, conflict0.getConflictType());
        assertEquals(ConflictDetailDTO.SourceType.PENDING_ITEM, conflict0.getSourceType());
        assertNull(conflict0.getCourseId());
        assertEquals(Integer.valueOf(1), conflict0.getPendingIndex());
        assertEquals("物理", conflict0.getCourseName());
        assertEquals("教室 A101 在时间段 周一 08:00-10:00 与本次批量待排课程冲突", conflict0.getReason());

        BatchPreCheckItemResponse item1 = response.getItems().get(1);
        assertEquals(1, item1.getOriginalIndex());
        assertFalse(item1.isCanSchedule());
        assertEquals(1, item1.getConflictCount());

        assertEquals(0, scheduleService.findSchedules(null).size());
    }

    @Test
    void testPreCheckByClassroomBatch_InternalConflictSameTeacher() {
        List<ClassroomBatchPreCheckItemRequest> items = new ArrayList<>();
        items.add(createClassroomBatchItem("数学", "张老师", "周一 08:00-10:00"));
        items.add(createClassroomBatchItem("物理", "张老师", "周一 08:00-10:00"));

        ClassroomBatchPreCheckRequest request = createClassroomBatchRequest("A101", items);
        BatchPreCheckResponse response = scheduleService.preCheckConflictsByClassroomBatch(request);

        assertFalse(response.isCanSchedule());
        assertEquals(4, response.getTotalConflictCount());

        BatchPreCheckItemResponse item0 = response.getItems().get(0);
        assertFalse(item0.isCanSchedule());
        assertEquals(2, item0.getConflictCount());

        ConflictDetailDTO teacherConflict0 = item0.getConflictDetails().get(0);
        assertEquals(ConflictDetailDTO.ConflictType.TEACHER, teacherConflict0.getConflictType());
        assertEquals(ConflictDetailDTO.SourceType.PENDING_ITEM, teacherConflict0.getSourceType());
        assertEquals("老师 张老师 在时间段 周一 08:00-10:00 与本次批量待排课程冲突", teacherConflict0.getReason());

        ConflictDetailDTO classroomConflict0 = item0.getConflictDetails().get(1);
        assertEquals(ConflictDetailDTO.ConflictType.CLASSROOM, classroomConflict0.getConflictType());
        assertEquals(ConflictDetailDTO.SourceType.PENDING_ITEM, classroomConflict0.getSourceType());
        assertEquals("教室 A101 在时间段 周一 08:00-10:00 与本次批量待排课程冲突", classroomConflict0.getReason());

        assertEquals(0, scheduleService.findSchedules(null).size());
    }

    @Test
    void testPreCheckByClassroomBatch_EmptyList() {
        List<ClassroomBatchPreCheckItemRequest> items = new ArrayList<>();
        ClassroomBatchPreCheckRequest request = createClassroomBatchRequest("A101", items);

        BatchPreCheckResponse response = scheduleService.preCheckConflictsByClassroomBatch(request);

        assertTrue(response.isCanSchedule());
        assertEquals(0, response.getTotalConflictCount());
        assertEquals(0, response.getItems().size());

        assertEquals(0, scheduleService.findSchedules(null).size());
    }

    @Test
    void testPreCheckByClassroomBatch_NullRequest_ThrowsException() {
        InvalidRequestParameterException exception = assertThrows(InvalidRequestParameterException.class, () ->
                scheduleService.preCheckConflictsByClassroomBatch(null));
        assertEquals("请求不能为空", exception.getMessage());
    }

    @Test
    void testPreCheckByClassroomBatch_NullClassroom_ThrowsException() {
        List<ClassroomBatchPreCheckItemRequest> items = new ArrayList<>();
        items.add(createClassroomBatchItem("数学", "张老师", "周一 08:00-10:00"));

        ClassroomBatchPreCheckRequest request = createClassroomBatchRequest(null, items);

        InvalidRequestParameterException exception = assertThrows(InvalidRequestParameterException.class, () ->
                scheduleService.preCheckConflictsByClassroomBatch(request));
        assertEquals("教室名不能为空", exception.getMessage());
    }

    @Test
    void testPreCheckByClassroomBatch_EmptyClassroom_ThrowsException() {
        List<ClassroomBatchPreCheckItemRequest> items = new ArrayList<>();
        items.add(createClassroomBatchItem("数学", "张老师", "周一 08:00-10:00"));

        ClassroomBatchPreCheckRequest request = createClassroomBatchRequest("", items);

        InvalidRequestParameterException exception = assertThrows(InvalidRequestParameterException.class, () ->
                scheduleService.preCheckConflictsByClassroomBatch(request));
        assertEquals("教室名不能为空", exception.getMessage());
    }

    @Test
    void testPreCheckByClassroomBatch_NullItems_ThrowsException() {
        ClassroomBatchPreCheckRequest request = createClassroomBatchRequest("A101", null);

        InvalidRequestParameterException exception = assertThrows(InvalidRequestParameterException.class, () ->
                scheduleService.preCheckConflictsByClassroomBatch(request));
        assertEquals("待排课程列表不能为空", exception.getMessage());
    }

    @Test
    void testPreCheckByClassroomBatch_NullItem_ThrowsException() {
        List<ClassroomBatchPreCheckItemRequest> items = new ArrayList<>();
        items.add(null);

        ClassroomBatchPreCheckRequest request = createClassroomBatchRequest("A101", items);

        InvalidRequestParameterException exception = assertThrows(InvalidRequestParameterException.class, () ->
                scheduleService.preCheckConflictsByClassroomBatch(request));
        assertEquals("第 1 项课程请求不能为空", exception.getMessage());
    }

    @Test
    void testPreCheckByClassroomBatch_EmptyCourseName_ThrowsException() {
        List<ClassroomBatchPreCheckItemRequest> items = new ArrayList<>();
        items.add(createClassroomBatchItem("", "张老师", "周一 08:00-10:00"));

        ClassroomBatchPreCheckRequest request = createClassroomBatchRequest("A101", items);

        InvalidRequestParameterException exception = assertThrows(InvalidRequestParameterException.class, () ->
                scheduleService.preCheckConflictsByClassroomBatch(request));
        assertEquals("第 1 项课程名不能为空", exception.getMessage());
    }

    @Test
    void testPreCheckByClassroomBatch_EmptyTeacherName_ThrowsException() {
        List<ClassroomBatchPreCheckItemRequest> items = new ArrayList<>();
        items.add(createClassroomBatchItem("数学", "", "周一 08:00-10:00"));

        ClassroomBatchPreCheckRequest request = createClassroomBatchRequest("A101", items);

        InvalidRequestParameterException exception = assertThrows(InvalidRequestParameterException.class, () ->
                scheduleService.preCheckConflictsByClassroomBatch(request));
        assertEquals("第 1 项老师名不能为空", exception.getMessage());
    }

    @Test
    void testPreCheckByClassroomBatch_EmptyTimeSlot_ThrowsException() {
        List<ClassroomBatchPreCheckItemRequest> items = new ArrayList<>();
        items.add(createClassroomBatchItem("数学", "张老师", ""));

        ClassroomBatchPreCheckRequest request = createClassroomBatchRequest("A101", items);

        InvalidRequestParameterException exception = assertThrows(InvalidRequestParameterException.class, () ->
                scheduleService.preCheckConflictsByClassroomBatch(request));
        assertEquals("第 1 项时间段不能为空", exception.getMessage());
    }

    @Test
    void testPreCheckByClassroomBatch_InvalidTimeSlotFormat_ThrowsException() {
        List<ClassroomBatchPreCheckItemRequest> items = new ArrayList<>();
        items.add(createClassroomBatchItem("数学", "张老师", "明天上午"));

        ClassroomBatchPreCheckRequest request = createClassroomBatchRequest("A101", items);

        InvalidTimeSlotException exception = assertThrows(InvalidTimeSlotException.class, () ->
                scheduleService.preCheckConflictsByClassroomBatch(request));
        assertTrue(exception.getMessage().contains("格式不合法"));
    }

    @Test
    void testPreCheckByClassroomBatch_InvalidTimeSlotStartAfterEnd_ThrowsException() {
        List<ClassroomBatchPreCheckItemRequest> items = new ArrayList<>();
        items.add(createClassroomBatchItem("数学", "张老师", "周一 10:00-08:00"));

        ClassroomBatchPreCheckRequest request = createClassroomBatchRequest("A101", items);

        InvalidTimeSlotException exception = assertThrows(InvalidTimeSlotException.class, () ->
                scheduleService.preCheckConflictsByClassroomBatch(request));
        assertTrue(exception.getMessage().contains("必须早于"));
    }

    @Test
    void testPreCheckByClassroomBatch_NoCourseCreated() {
        List<ClassroomBatchPreCheckItemRequest> items = new ArrayList<>();
        items.add(createClassroomBatchItem("数学", "张老师", "周一 08:00-10:00"));
        items.add(createClassroomBatchItem("物理", "李老师", "周二 08:00-10:00"));

        ClassroomBatchPreCheckRequest request = createClassroomBatchRequest("A101", items);
        scheduleService.preCheckConflictsByClassroomBatch(request);

        assertEquals(0, scheduleService.findSchedules(null).size());
    }

    @Test
    void testPreCheckByClassroomBatch_NoAuditLogCreated() {
        scheduleService.addSchedule(createRequest("已有课程", "张老师", "A101", "周一 08:00-10:00"));

        List<AuditLogResponse> logsBefore = auditLogService.getAllLogs();
        int logCountBefore = logsBefore.size();

        List<ClassroomBatchPreCheckItemRequest> items = new ArrayList<>();
        items.add(createClassroomBatchItem("数学", "李老师", "周一 08:00-10:00"));
        items.add(createClassroomBatchItem("物理", "王老师", "周一 08:00-10:00"));

        ClassroomBatchPreCheckRequest request = createClassroomBatchRequest("A101", items);
        scheduleService.preCheckConflictsByClassroomBatch(request);

        List<AuditLogResponse> logsAfter = auditLogService.getAllLogs();
        assertEquals(logCountBefore, logsAfter.size());
    }

    @Test
    void testPreCheckByClassroomBatch_ResultsInInputOrder() {
        List<ClassroomBatchPreCheckItemRequest> items = new ArrayList<>();
        items.add(createClassroomBatchItem("课程A", "张老师", "周三 08:00-10:00"));
        items.add(createClassroomBatchItem("课程B", "李老师", "周一 08:00-10:00"));
        items.add(createClassroomBatchItem("课程C", "王老师", "周二 08:00-10:00"));

        ClassroomBatchPreCheckRequest request = createClassroomBatchRequest("A101", items);
        BatchPreCheckResponse response = scheduleService.preCheckConflictsByClassroomBatch(request);

        assertEquals(3, response.getItems().size());
        assertEquals(0, response.getItems().get(0).getOriginalIndex());
        assertEquals("课程A", response.getItems().get(0).getCourseName());
        assertEquals(1, response.getItems().get(1).getOriginalIndex());
        assertEquals("课程B", response.getItems().get(1).getCourseName());
        assertEquals(2, response.getItems().get(2).getOriginalIndex());
        assertEquals("课程C", response.getItems().get(2).getCourseName());
    }

    @Test
    void testPreCheckByClassroomBatch_ConflictDetailsSortedCorrectly() {
        scheduleService.addSchedule(createRequest("课程1", "张老师", "A101", "周一 08:00-10:00"));
        scheduleService.addSchedule(createRequest("课程2", "李老师", "B202", "周一 08:00-10:00"));

        List<ClassroomBatchPreCheckItemRequest> items = new ArrayList<>();
        items.add(createClassroomBatchItem("数学", "张老师", "周一 08:00-10:00"));

        ClassroomBatchPreCheckRequest request = createClassroomBatchRequest("B202", items);
        BatchPreCheckResponse response = scheduleService.preCheckConflictsByClassroomBatch(request);

        BatchPreCheckItemResponse item = response.getItems().get(0);
        assertEquals(2, item.getConflictCount());
        assertEquals(ConflictDetailDTO.ConflictType.TEACHER, item.getConflictDetails().get(0).getConflictType());
        assertEquals(ConflictDetailDTO.ConflictType.CLASSROOM, item.getConflictDetails().get(1).getConflictType());
    }

    @Test
    void testExportPreCheckAsCsv_NoConflict() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));

        CourseScheduleConflictPreCheckRequest request = createPreCheckRequest(
                "物理", "李老师", "B202", "周二 14:00-16:00");

        byte[] csvBytes = scheduleService.exportPreCheckAsCsv(request);
        String csv = new String(csvBytes, java.nio.charset.StandardCharsets.UTF_8);

        String[] lines = csv.split("\n");
        assertEquals(2, lines.length);
        assertEquals("\uFEFF预检类型,原始序号,课程名,是否可排,冲突类型,冲突来源,冲突课程/待排项,老师,教室,时间段,原因", lines[0]);
        assertTrue(lines[1].startsWith("单项预检,"));
        assertTrue(lines[1].contains(",物理,"));
        assertTrue(lines[1].contains(",是,"));
    }

    @Test
    void testExportPreCheckAsCsv_TeacherConflict() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));

        CourseScheduleConflictPreCheckRequest request = createPreCheckRequest(
                "物理", "张老师", "B202", "周一 08:00-10:00");

        byte[] csvBytes = scheduleService.exportPreCheckAsCsv(request);
        String csv = new String(csvBytes, java.nio.charset.StandardCharsets.UTF_8);

        String[] lines = csv.split("\n");
        assertEquals(2, lines.length);
        assertTrue(lines[1].contains("单项预检"));
        assertTrue(lines[1].contains("物理"));
        assertTrue(lines[1].contains("否"));
        assertTrue(lines[1].contains("老师冲突"));
        assertTrue(lines[1].contains("已有课程"));
        assertTrue(lines[1].contains("数学"));
        assertTrue(lines[1].contains("张老师"));
        assertTrue(lines[1].contains("A101"));
        assertTrue(lines[1].contains("周一 08:00-10:00"));
        assertTrue(lines[1].contains("已有课程安排"));
    }

    @Test
    void testExportPreCheckAsCsv_ClassroomConflict() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));

        CourseScheduleConflictPreCheckRequest request = createPreCheckRequest(
                "物理", "李老师", "A101", "周一 08:00-10:00");

        byte[] csvBytes = scheduleService.exportPreCheckAsCsv(request);
        String csv = new String(csvBytes, java.nio.charset.StandardCharsets.UTF_8);

        String[] lines = csv.split("\n");
        assertEquals(2, lines.length);
        assertTrue(lines[1].contains("教室冲突"));
    }

    @Test
    void testExportPreCheckAsCsv_BothTeacherAndClassroomConflict() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));

        CourseScheduleConflictPreCheckRequest request = createPreCheckRequest(
                "物理", "张老师", "A101", "周一 08:00-10:00");

        byte[] csvBytes = scheduleService.exportPreCheckAsCsv(request);
        String csv = new String(csvBytes, java.nio.charset.StandardCharsets.UTF_8);

        String[] lines = csv.split("\n");
        assertEquals(3, lines.length);
        assertTrue(lines[1].contains("老师冲突"));
        assertTrue(lines[2].contains("教室冲突"));
    }

    @Test
    void testExportPreCheckAsCsv_SpecialCharactersEscaped() {
        scheduleService.addSchedule(createRequest("数学,高级", "张\"老师", "A101\n新楼", "周一 08:00-10:00"));

        CourseScheduleConflictPreCheckRequest request = createPreCheckRequest(
                "物理,实验", "张\"老师", "B202", "周一 08:00-10:00");

        byte[] csvBytes = scheduleService.exportPreCheckAsCsv(request);
        String csv = new String(csvBytes, java.nio.charset.StandardCharsets.UTF_8);

        assertTrue(csv.contains("\"物理,实验\""));
        assertTrue(csv.contains("数学,高级(ID:"));
        assertTrue(csv.contains("\"张\"\"老师\""));
        assertTrue(csv.contains("\"A101\n新楼\""));
    }

    @Test
    void testExportPreCheckAsCsv_NoAuditLogCreated() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));

        List<AuditLogResponse> logsBefore = auditLogService.getAllLogs();
        int logCountBefore = logsBefore.size();

        CourseScheduleConflictPreCheckRequest request = createPreCheckRequest(
                "物理", "张老师", "B202", "周一 08:00-10:00");
        scheduleService.exportPreCheckAsCsv(request);

        List<AuditLogResponse> logsAfter = auditLogService.getAllLogs();
        assertEquals(logCountBefore, logsAfter.size());
    }

    @Test
    void testExportPreCheckAsCsv_NoCourseCreated() {
        CourseScheduleConflictPreCheckRequest request = createPreCheckRequest(
                "数学", "张老师", "A101", "周一 08:00-10:00");

        scheduleService.exportPreCheckAsCsv(request);

        assertEquals(0, scheduleService.findSchedules(null).size());
    }

    @Test
    void testExportTeacherBatchPreCheckAsCsv_NoConflict() {
        List<TeacherBatchPreCheckItemRequest> items = new ArrayList<>();
        items.add(createBatchItem("数学", "A101", "周一 08:00-10:00"));
        items.add(createBatchItem("物理", "A101", "周二 08:00-10:00"));

        TeacherBatchPreCheckRequest request = createBatchRequest("张老师", items);
        byte[] csvBytes = scheduleService.exportTeacherBatchPreCheckAsCsv(request);
        String csv = new String(csvBytes, java.nio.charset.StandardCharsets.UTF_8);

        String[] lines = csv.split("\n");
        assertEquals(3, lines.length);
        assertTrue(lines[1].contains("按老师批量预检"));
        assertTrue(lines[1].contains("0"));
        assertTrue(lines[1].contains("数学"));
        assertTrue(lines[1].contains("是"));
        assertTrue(lines[2].contains("1"));
        assertTrue(lines[2].contains("物理"));
        assertTrue(lines[2].contains("是"));
    }

    @Test
    void testExportTeacherBatchPreCheckAsCsv_ConflictWithExisting() {
        scheduleService.addSchedule(createRequest("已有课程", "张老师", "A101", "周一 08:00-10:00"));

        List<TeacherBatchPreCheckItemRequest> items = new ArrayList<>();
        items.add(createBatchItem("数学", "B202", "周一 08:00-10:00"));
        items.add(createBatchItem("物理", "C303", "周二 08:00-10:00"));

        TeacherBatchPreCheckRequest request = createBatchRequest("张老师", items);
        byte[] csvBytes = scheduleService.exportTeacherBatchPreCheckAsCsv(request);
        String csv = new String(csvBytes, java.nio.charset.StandardCharsets.UTF_8);

        String[] lines = csv.split("\n");
        assertEquals(3, lines.length);
        assertTrue(lines[1].contains("0"));
        assertTrue(lines[1].contains("数学"));
        assertTrue(lines[1].contains("否"));
        assertTrue(lines[1].contains("老师冲突"));
        assertTrue(lines[1].contains("已有课程"));
        assertTrue(lines[2].contains("1"));
        assertTrue(lines[2].contains("物理"));
        assertTrue(lines[2].contains("是"));
    }

    @Test
    void testExportTeacherBatchPreCheckAsCsv_InternalConflict() {
        List<TeacherBatchPreCheckItemRequest> items = new ArrayList<>();
        items.add(createBatchItem("数学", "A101", "周一 08:00-10:00"));
        items.add(createBatchItem("物理", "B202", "周一 08:00-10:00"));

        TeacherBatchPreCheckRequest request = createBatchRequest("张老师", items);
        byte[] csvBytes = scheduleService.exportTeacherBatchPreCheckAsCsv(request);
        String csv = new String(csvBytes, java.nio.charset.StandardCharsets.UTF_8);

        String[] lines = csv.split("\n");
        assertEquals(3, lines.length);
        assertTrue(lines[1].contains("0"));
        assertTrue(lines[1].contains("否"));
        assertTrue(lines[1].contains("待排项"));
        assertTrue(lines[1].contains("物理"));
        assertTrue(lines[2].contains("1"));
        assertTrue(lines[2].contains("否"));
        assertTrue(lines[2].contains("待排项"));
        assertTrue(lines[2].contains("数学"));
    }

    @Test
    void testExportTeacherBatchPreCheckAsCsv_SpecialCharactersEscaped() {
        scheduleService.addSchedule(createRequest("已有,课程", "张\"老师", "A\n101", "周一 08:00-10:00"));

        List<TeacherBatchPreCheckItemRequest> items = new ArrayList<>();
        items.add(createBatchItem("数学,高级", "B202", "周一 08:00-10:00"));

        TeacherBatchPreCheckRequest request = createBatchRequest("张\"老师", items);
        byte[] csvBytes = scheduleService.exportTeacherBatchPreCheckAsCsv(request);
        String csv = new String(csvBytes, java.nio.charset.StandardCharsets.UTF_8);

        assertTrue(csv.contains("\"数学,高级\""));
        assertTrue(csv.contains("已有,课程(ID:"));
        assertTrue(csv.contains("\"张\"\"老师\""));
        assertTrue(csv.contains("\"A\n101\""));
    }

    @Test
    void testExportTeacherBatchPreCheckAsCsv_NoAuditLogCreated() {
        scheduleService.addSchedule(createRequest("已有课程", "张老师", "A101", "周一 08:00-10:00"));

        List<AuditLogResponse> logsBefore = auditLogService.getAllLogs();
        int logCountBefore = logsBefore.size();

        List<TeacherBatchPreCheckItemRequest> items = new ArrayList<>();
        items.add(createBatchItem("数学", "B202", "周一 08:00-10:00"));

        TeacherBatchPreCheckRequest request = createBatchRequest("张老师", items);
        scheduleService.exportTeacherBatchPreCheckAsCsv(request);

        List<AuditLogResponse> logsAfter = auditLogService.getAllLogs();
        assertEquals(logCountBefore, logsAfter.size());
    }

    @Test
    void testExportTeacherBatchPreCheckAsCsv_NoCourseCreated() {
        List<TeacherBatchPreCheckItemRequest> items = new ArrayList<>();
        items.add(createBatchItem("数学", "A101", "周一 08:00-10:00"));

        TeacherBatchPreCheckRequest request = createBatchRequest("张老师", items);
        scheduleService.exportTeacherBatchPreCheckAsCsv(request);

        assertEquals(0, scheduleService.findSchedules(null).size());
    }

    @Test
    void testExportClassroomBatchPreCheckAsCsv_NoConflict() {
        List<ClassroomBatchPreCheckItemRequest> items = new ArrayList<>();
        items.add(createClassroomBatchItem("数学", "张老师", "周一 08:00-10:00"));
        items.add(createClassroomBatchItem("物理", "李老师", "周二 08:00-10:00"));

        ClassroomBatchPreCheckRequest request = createClassroomBatchRequest("A101", items);
        byte[] csvBytes = scheduleService.exportClassroomBatchPreCheckAsCsv(request);
        String csv = new String(csvBytes, java.nio.charset.StandardCharsets.UTF_8);

        String[] lines = csv.split("\n");
        assertEquals(3, lines.length);
        assertTrue(lines[1].contains("按教室批量预检"));
        assertTrue(lines[1].contains("0"));
        assertTrue(lines[1].contains("数学"));
        assertTrue(lines[1].contains("是"));
        assertTrue(lines[2].contains("1"));
        assertTrue(lines[2].contains("物理"));
        assertTrue(lines[2].contains("是"));
    }

    @Test
    void testExportClassroomBatchPreCheckAsCsv_ConflictWithExisting() {
        scheduleService.addSchedule(createRequest("已有课程", "张老师", "A101", "周一 08:00-10:00"));

        List<ClassroomBatchPreCheckItemRequest> items = new ArrayList<>();
        items.add(createClassroomBatchItem("数学", "李老师", "周一 08:00-10:00"));
        items.add(createClassroomBatchItem("物理", "王老师", "周二 08:00-10:00"));

        ClassroomBatchPreCheckRequest request = createClassroomBatchRequest("A101", items);
        byte[] csvBytes = scheduleService.exportClassroomBatchPreCheckAsCsv(request);
        String csv = new String(csvBytes, java.nio.charset.StandardCharsets.UTF_8);

        String[] lines = csv.split("\n");
        assertEquals(3, lines.length);
        assertTrue(lines[1].contains("0"));
        assertTrue(lines[1].contains("数学"));
        assertTrue(lines[1].contains("否"));
        assertTrue(lines[1].contains("教室冲突"));
        assertTrue(lines[1].contains("已有课程"));
        assertTrue(lines[2].contains("1"));
        assertTrue(lines[2].contains("物理"));
        assertTrue(lines[2].contains("是"));
    }

    @Test
    void testExportClassroomBatchPreCheckAsCsv_InternalConflict() {
        List<ClassroomBatchPreCheckItemRequest> items = new ArrayList<>();
        items.add(createClassroomBatchItem("数学", "张老师", "周一 08:00-10:00"));
        items.add(createClassroomBatchItem("物理", "李老师", "周一 08:00-10:00"));

        ClassroomBatchPreCheckRequest request = createClassroomBatchRequest("A101", items);
        byte[] csvBytes = scheduleService.exportClassroomBatchPreCheckAsCsv(request);
        String csv = new String(csvBytes, java.nio.charset.StandardCharsets.UTF_8);

        String[] lines = csv.split("\n");
        assertEquals(3, lines.length);
        assertTrue(lines[1].contains("0"));
        assertTrue(lines[1].contains("否"));
        assertTrue(lines[1].contains("待排项"));
        assertTrue(lines[1].contains("物理"));
        assertTrue(lines[2].contains("1"));
        assertTrue(lines[2].contains("否"));
        assertTrue(lines[2].contains("待排项"));
        assertTrue(lines[2].contains("数学"));
    }

    @Test
    void testExportClassroomBatchPreCheckAsCsv_SpecialCharactersEscaped() {
        scheduleService.addSchedule(createRequest("已有,课程", "张\"老师", "A\n101", "周一 08:00-10:00"));

        List<ClassroomBatchPreCheckItemRequest> items = new ArrayList<>();
        items.add(createClassroomBatchItem("数学,高级", "李老师", "周一 08:00-10:00"));

        ClassroomBatchPreCheckRequest request = createClassroomBatchRequest("A\n101", items);
        byte[] csvBytes = scheduleService.exportClassroomBatchPreCheckAsCsv(request);
        String csv = new String(csvBytes, java.nio.charset.StandardCharsets.UTF_8);

        assertTrue(csv.contains("\"数学,高级\""));
        assertTrue(csv.contains("已有,课程(ID:"));
        assertTrue(csv.contains("\"张\"\"老师\""));
        assertTrue(csv.contains("\"A\n101\""));
    }

    @Test
    void testExportClassroomBatchPreCheckAsCsv_NoAuditLogCreated() {
        scheduleService.addSchedule(createRequest("已有课程", "张老师", "A101", "周一 08:00-10:00"));

        List<AuditLogResponse> logsBefore = auditLogService.getAllLogs();
        int logCountBefore = logsBefore.size();

        List<ClassroomBatchPreCheckItemRequest> items = new ArrayList<>();
        items.add(createClassroomBatchItem("数学", "李老师", "周一 08:00-10:00"));

        ClassroomBatchPreCheckRequest request = createClassroomBatchRequest("A101", items);
        scheduleService.exportClassroomBatchPreCheckAsCsv(request);

        List<AuditLogResponse> logsAfter = auditLogService.getAllLogs();
        assertEquals(logCountBefore, logsAfter.size());
    }

    @Test
    void testExportClassroomBatchPreCheckAsCsv_NoCourseCreated() {
        List<ClassroomBatchPreCheckItemRequest> items = new ArrayList<>();
        items.add(createClassroomBatchItem("数学", "张老师", "周一 08:00-10:00"));

        ClassroomBatchPreCheckRequest request = createClassroomBatchRequest("A101", items);
        scheduleService.exportClassroomBatchPreCheckAsCsv(request);

        assertEquals(0, scheduleService.findSchedules(null).size());
    }

    @Test
    void testPreCheckConflictsSummary_NoConflict() {
        CourseScheduleConflictPreCheckRequest request = createPreCheckRequest(
                "数学", "张老师", "A101", "周一 08:00-10:00");

        PreCheckSummaryResponse summary = scheduleService.preCheckConflictsSummary(request);

        assertEquals(PreCheckSummaryResponse.PreCheckType.SINGLE, summary.getPreCheckType());
        assertEquals(1, summary.getTotalItems());
        assertEquals(1, summary.getSchedulableItems());
        assertEquals(0, summary.getUnschedulableItems());
        assertEquals(0, summary.getTotalConflicts());
        assertEquals(0, summary.getTeacherConflicts());
        assertEquals(0, summary.getClassroomConflicts());
        assertEquals(0, summary.getExistingCourseConflicts());
        assertEquals(0, summary.getPendingItemConflicts());
    }

    @Test
    void testPreCheckConflictsSummary_TeacherConflict() {
        scheduleService.addSchedule(createRequest("已有课程", "张老师", "B202", "周一 08:00-10:00"));

        CourseScheduleConflictPreCheckRequest request = createPreCheckRequest(
                "数学", "张老师", "A101", "周一 08:00-10:00");

        PreCheckSummaryResponse summary = scheduleService.preCheckConflictsSummary(request);

        assertEquals(PreCheckSummaryResponse.PreCheckType.SINGLE, summary.getPreCheckType());
        assertEquals(1, summary.getTotalItems());
        assertEquals(0, summary.getSchedulableItems());
        assertEquals(1, summary.getUnschedulableItems());
        assertEquals(1, summary.getTotalConflicts());
        assertEquals(1, summary.getTeacherConflicts());
        assertEquals(0, summary.getClassroomConflicts());
        assertEquals(1, summary.getExistingCourseConflicts());
        assertEquals(0, summary.getPendingItemConflicts());
    }

    @Test
    void testPreCheckConflictsSummary_ClassroomConflict() {
        scheduleService.addSchedule(createRequest("已有课程", "李老师", "A101", "周一 08:00-10:00"));

        CourseScheduleConflictPreCheckRequest request = createPreCheckRequest(
                "数学", "张老师", "A101", "周一 08:00-10:00");

        PreCheckSummaryResponse summary = scheduleService.preCheckConflictsSummary(request);

        assertEquals(PreCheckSummaryResponse.PreCheckType.SINGLE, summary.getPreCheckType());
        assertEquals(1, summary.getTotalItems());
        assertEquals(0, summary.getSchedulableItems());
        assertEquals(1, summary.getUnschedulableItems());
        assertEquals(1, summary.getTotalConflicts());
        assertEquals(0, summary.getTeacherConflicts());
        assertEquals(1, summary.getClassroomConflicts());
        assertEquals(1, summary.getExistingCourseConflicts());
        assertEquals(0, summary.getPendingItemConflicts());
    }

    @Test
    void testPreCheckConflictsSummary_BothTeacherAndClassroomConflict() {
        scheduleService.addSchedule(createRequest("已有课程", "张老师", "A101", "周一 08:00-10:00"));

        CourseScheduleConflictPreCheckRequest request = createPreCheckRequest(
                "数学", "张老师", "A101", "周一 08:00-10:00");

        PreCheckSummaryResponse summary = scheduleService.preCheckConflictsSummary(request);

        assertEquals(PreCheckSummaryResponse.PreCheckType.SINGLE, summary.getPreCheckType());
        assertEquals(1, summary.getTotalItems());
        assertEquals(0, summary.getSchedulableItems());
        assertEquals(1, summary.getUnschedulableItems());
        assertEquals(2, summary.getTotalConflicts());
        assertEquals(1, summary.getTeacherConflicts());
        assertEquals(1, summary.getClassroomConflicts());
        assertEquals(2, summary.getExistingCourseConflicts());
        assertEquals(0, summary.getPendingItemConflicts());
    }

    @Test
    void testPreCheckConflictsSummary_NoAuditLogCreated() {
        List<AuditLogResponse> logsBefore = auditLogService.getAllLogs();
        int logCountBefore = logsBefore.size();

        CourseScheduleConflictPreCheckRequest request = createPreCheckRequest(
                "数学", "张老师", "A101", "周一 08:00-10:00");
        scheduleService.preCheckConflictsSummary(request);

        List<AuditLogResponse> logsAfter = auditLogService.getAllLogs();
        assertEquals(logCountBefore, logsAfter.size());
    }

    @Test
    void testPreCheckConflictsSummary_NoCourseCreated() {
        CourseScheduleConflictPreCheckRequest request = createPreCheckRequest(
                "数学", "张老师", "A101", "周一 08:00-10:00");
        scheduleService.preCheckConflictsSummary(request);

        assertEquals(0, scheduleService.findSchedules(null).size());
    }

    @Test
    void testPreCheckConflictsByTeacherBatchSummary_AllCanSchedule() {
        List<TeacherBatchPreCheckItemRequest> items = new ArrayList<>();
        items.add(createBatchItem("数学", "A101", "周一 08:00-10:00"));
        items.add(createBatchItem("物理", "B202", "周二 08:00-10:00"));
        items.add(createBatchItem("化学", "C303", "周三 08:00-10:00"));

        TeacherBatchPreCheckRequest request = createBatchRequest("张老师", items);
        PreCheckSummaryResponse summary = scheduleService.preCheckConflictsByTeacherBatchSummary(request);

        assertEquals(PreCheckSummaryResponse.PreCheckType.TEACHER_BATCH, summary.getPreCheckType());
        assertEquals(3, summary.getTotalItems());
        assertEquals(3, summary.getSchedulableItems());
        assertEquals(0, summary.getUnschedulableItems());
        assertEquals(0, summary.getTotalConflicts());
        assertEquals(0, summary.getTeacherConflicts());
        assertEquals(0, summary.getClassroomConflicts());
        assertEquals(0, summary.getExistingCourseConflicts());
        assertEquals(0, summary.getPendingItemConflicts());
    }

    @Test
    void testPreCheckConflictsByTeacherBatchSummary_MixedResults() {
        scheduleService.addSchedule(createRequest("已有课程", "张老师", "A101", "周一 08:00-10:00"));

        List<TeacherBatchPreCheckItemRequest> items = new ArrayList<>();
        items.add(createBatchItem("数学", "B202", "周一 08:00-10:00"));
        items.add(createBatchItem("物理", "C303", "周二 08:00-10:00"));
        items.add(createBatchItem("化学", "D404", "周一 08:00-10:00"));

        TeacherBatchPreCheckRequest request = createBatchRequest("张老师", items);
        PreCheckSummaryResponse summary = scheduleService.preCheckConflictsByTeacherBatchSummary(request);

        assertEquals(PreCheckSummaryResponse.PreCheckType.TEACHER_BATCH, summary.getPreCheckType());
        assertEquals(3, summary.getTotalItems());
        assertEquals(1, summary.getSchedulableItems());
        assertEquals(2, summary.getUnschedulableItems());
        assertEquals(4, summary.getTotalConflicts());
        assertEquals(4, summary.getTeacherConflicts());
        assertEquals(0, summary.getClassroomConflicts());
        assertEquals(2, summary.getExistingCourseConflicts());
        assertEquals(2, summary.getPendingItemConflicts());
    }

    @Test
    void testPreCheckConflictsByTeacherBatchSummary_SourceTypeStatistics() {
        scheduleService.addSchedule(createRequest("已有课程", "张老师", "A101", "周一 08:00-10:00"));

        List<TeacherBatchPreCheckItemRequest> items = new ArrayList<>();
        items.add(createBatchItem("数学", "B202", "周一 08:00-10:00"));
        items.add(createBatchItem("物理", "C303", "周一 08:00-10:00"));
        items.add(createBatchItem("化学", "D404", "周二 08:00-10:00"));

        TeacherBatchPreCheckRequest request = createBatchRequest("张老师", items);
        PreCheckSummaryResponse summary = scheduleService.preCheckConflictsByTeacherBatchSummary(request);

        assertEquals(PreCheckSummaryResponse.PreCheckType.TEACHER_BATCH, summary.getPreCheckType());
        assertEquals(3, summary.getTotalItems());
        assertEquals(1, summary.getSchedulableItems());
        assertEquals(2, summary.getUnschedulableItems());
        assertEquals(4, summary.getTotalConflicts());
        assertEquals(4, summary.getTeacherConflicts());
        assertEquals(0, summary.getClassroomConflicts());
        assertEquals(2, summary.getExistingCourseConflicts());
        assertEquals(2, summary.getPendingItemConflicts());
    }

    @Test
    void testPreCheckConflictsByTeacherBatchSummary_NoAuditLogCreated() {
        scheduleService.addSchedule(createRequest("已有课程", "张老师", "A101", "周一 08:00-10:00"));

        List<AuditLogResponse> logsBefore = auditLogService.getAllLogs();
        int logCountBefore = logsBefore.size();

        List<TeacherBatchPreCheckItemRequest> items = new ArrayList<>();
        items.add(createBatchItem("数学", "B202", "周一 08:00-10:00"));
        items.add(createBatchItem("物理", "C303", "周二 08:00-10:00"));

        TeacherBatchPreCheckRequest request = createBatchRequest("张老师", items);
        scheduleService.preCheckConflictsByTeacherBatchSummary(request);

        List<AuditLogResponse> logsAfter = auditLogService.getAllLogs();
        assertEquals(logCountBefore, logsAfter.size());
    }

    @Test
    void testPreCheckConflictsByTeacherBatchSummary_NoCourseCreated() {
        List<TeacherBatchPreCheckItemRequest> items = new ArrayList<>();
        items.add(createBatchItem("数学", "A101", "周一 08:00-10:00"));
        items.add(createBatchItem("物理", "B202", "周二 08:00-10:00"));

        TeacherBatchPreCheckRequest request = createBatchRequest("张老师", items);
        scheduleService.preCheckConflictsByTeacherBatchSummary(request);

        assertEquals(0, scheduleService.findSchedules(null).size());
    }

    @Test
    void testPreCheckConflictsByClassroomBatchSummary_AllCanSchedule() {
        List<ClassroomBatchPreCheckItemRequest> items = new ArrayList<>();
        items.add(createClassroomBatchItem("数学", "张老师", "周一 08:00-10:00"));
        items.add(createClassroomBatchItem("物理", "李老师", "周二 08:00-10:00"));
        items.add(createClassroomBatchItem("化学", "王老师", "周三 08:00-10:00"));

        ClassroomBatchPreCheckRequest request = createClassroomBatchRequest("A101", items);
        PreCheckSummaryResponse summary = scheduleService.preCheckConflictsByClassroomBatchSummary(request);

        assertEquals(PreCheckSummaryResponse.PreCheckType.CLASSROOM_BATCH, summary.getPreCheckType());
        assertEquals(3, summary.getTotalItems());
        assertEquals(3, summary.getSchedulableItems());
        assertEquals(0, summary.getUnschedulableItems());
        assertEquals(0, summary.getTotalConflicts());
        assertEquals(0, summary.getTeacherConflicts());
        assertEquals(0, summary.getClassroomConflicts());
        assertEquals(0, summary.getExistingCourseConflicts());
        assertEquals(0, summary.getPendingItemConflicts());
    }

    @Test
    void testPreCheckConflictsByClassroomBatchSummary_MixedResults() {
        scheduleService.addSchedule(createRequest("已有课程", "赵老师", "A101", "周一 08:00-10:00"));

        List<ClassroomBatchPreCheckItemRequest> items = new ArrayList<>();
        items.add(createClassroomBatchItem("数学", "张老师", "周一 08:00-10:00"));
        items.add(createClassroomBatchItem("物理", "李老师", "周二 08:00-10:00"));
        items.add(createClassroomBatchItem("化学", "王老师", "周一 08:00-10:00"));

        ClassroomBatchPreCheckRequest request = createClassroomBatchRequest("A101", items);
        PreCheckSummaryResponse summary = scheduleService.preCheckConflictsByClassroomBatchSummary(request);

        assertEquals(PreCheckSummaryResponse.PreCheckType.CLASSROOM_BATCH, summary.getPreCheckType());
        assertEquals(3, summary.getTotalItems());
        assertEquals(1, summary.getSchedulableItems());
        assertEquals(2, summary.getUnschedulableItems());
        assertEquals(4, summary.getTotalConflicts());
        assertEquals(0, summary.getTeacherConflicts());
        assertEquals(4, summary.getClassroomConflicts());
        assertEquals(2, summary.getExistingCourseConflicts());
        assertEquals(2, summary.getPendingItemConflicts());
    }

    @Test
    void testPreCheckConflictsByClassroomBatchSummary_SourceTypeStatistics() {
        scheduleService.addSchedule(createRequest("已有课程", "赵老师", "A101", "周一 08:00-10:00"));

        List<ClassroomBatchPreCheckItemRequest> items = new ArrayList<>();
        items.add(createClassroomBatchItem("数学", "张老师", "周一 08:00-10:00"));
        items.add(createClassroomBatchItem("物理", "张老师", "周一 08:00-10:00"));
        items.add(createClassroomBatchItem("化学", "王老师", "周二 08:00-10:00"));

        ClassroomBatchPreCheckRequest request = createClassroomBatchRequest("A101", items);
        PreCheckSummaryResponse summary = scheduleService.preCheckConflictsByClassroomBatchSummary(request);

        assertEquals(PreCheckSummaryResponse.PreCheckType.CLASSROOM_BATCH, summary.getPreCheckType());
        assertEquals(3, summary.getTotalItems());
        assertEquals(1, summary.getSchedulableItems());
        assertEquals(2, summary.getUnschedulableItems());
        assertEquals(6, summary.getTotalConflicts());
        assertEquals(2, summary.getTeacherConflicts());
        assertEquals(4, summary.getClassroomConflicts());
        assertEquals(2, summary.getExistingCourseConflicts());
        assertEquals(4, summary.getPendingItemConflicts());
    }

    @Test
    void testPreCheckConflictsByClassroomBatchSummary_NoAuditLogCreated() {
        scheduleService.addSchedule(createRequest("已有课程", "张老师", "A101", "周一 08:00-10:00"));

        List<AuditLogResponse> logsBefore = auditLogService.getAllLogs();
        int logCountBefore = logsBefore.size();

        List<ClassroomBatchPreCheckItemRequest> items = new ArrayList<>();
        items.add(createClassroomBatchItem("数学", "李老师", "周一 08:00-10:00"));
        items.add(createClassroomBatchItem("物理", "王老师", "周二 08:00-10:00"));

        ClassroomBatchPreCheckRequest request = createClassroomBatchRequest("A101", items);
        scheduleService.preCheckConflictsByClassroomBatchSummary(request);

        List<AuditLogResponse> logsAfter = auditLogService.getAllLogs();
        assertEquals(logCountBefore, logsAfter.size());
    }

    @Test
    void testPreCheckConflictsByClassroomBatchSummary_NoCourseCreated() {
        List<ClassroomBatchPreCheckItemRequest> items = new ArrayList<>();
        items.add(createClassroomBatchItem("数学", "张老师", "周一 08:00-10:00"));
        items.add(createClassroomBatchItem("物理", "李老师", "周二 08:00-10:00"));

        ClassroomBatchPreCheckRequest request = createClassroomBatchRequest("A101", items);
        scheduleService.preCheckConflictsByClassroomBatchSummary(request);

        assertEquals(0, scheduleService.findSchedules(null).size());
    }

    @Test
    void testGroupConflictsBySource_NoConflicts_ReturnsEmptyList() {
        CourseScheduleConflictPreCheckRequest request = new CourseScheduleConflictPreCheckRequest();
        request.setCourseName("数学");
        request.setTeacherName("张老师");
        request.setClassroom("A101");
        request.setTimeSlot("周一 08:00-10:00");

        List<com.coursescheduler.dto.ConflictGroupDTO> result = scheduleService.groupConflictsBySource(request);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testGroupConflictsBySource_OnlyExistingCourseConflicts() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));

        CourseScheduleConflictPreCheckRequest request = new CourseScheduleConflictPreCheckRequest();
        request.setCourseName("物理");
        request.setTeacherName("张老师");
        request.setClassroom("A101");
        request.setTimeSlot("周一 08:00-10:00");

        List<com.coursescheduler.dto.ConflictGroupDTO> result = scheduleService.groupConflictsBySource(request);

        assertEquals(1, result.size());
        com.coursescheduler.dto.ConflictGroupDTO group = result.get(0);
        assertEquals(ConflictDetailDTO.SourceType.EXISTING_COURSE, group.getSourceType());
        assertEquals(2, group.getConflictCount());
        assertEquals(1, group.getTeacherConflictCount());
        assertEquals(1, group.getClassroomConflictCount());
        assertTrue(group.getCourseNames().contains("数学"));
    }

    @Test
    void testGroupConflictsBySource_OnlyTeacherConflictFromExistingCourse() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));

        CourseScheduleConflictPreCheckRequest request = new CourseScheduleConflictPreCheckRequest();
        request.setCourseName("物理");
        request.setTeacherName("张老师");
        request.setClassroom("B202");
        request.setTimeSlot("周一 08:00-10:00");

        List<com.coursescheduler.dto.ConflictGroupDTO> result = scheduleService.groupConflictsBySource(request);

        assertEquals(1, result.size());
        com.coursescheduler.dto.ConflictGroupDTO group = result.get(0);
        assertEquals(ConflictDetailDTO.SourceType.EXISTING_COURSE, group.getSourceType());
        assertEquals(1, group.getConflictCount());
        assertEquals(1, group.getTeacherConflictCount());
        assertEquals(0, group.getClassroomConflictCount());
    }

    @Test
    void testGroupConflictsBySource_OnlyClassroomConflictFromExistingCourse() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));

        CourseScheduleConflictPreCheckRequest request = new CourseScheduleConflictPreCheckRequest();
        request.setCourseName("物理");
        request.setTeacherName("李老师");
        request.setClassroom("A101");
        request.setTimeSlot("周一 08:00-10:00");

        List<com.coursescheduler.dto.ConflictGroupDTO> result = scheduleService.groupConflictsBySource(request);

        assertEquals(1, result.size());
        com.coursescheduler.dto.ConflictGroupDTO group = result.get(0);
        assertEquals(ConflictDetailDTO.SourceType.EXISTING_COURSE, group.getSourceType());
        assertEquals(1, group.getConflictCount());
        assertEquals(0, group.getTeacherConflictCount());
        assertEquals(1, group.getClassroomConflictCount());
    }

    @Test
    void testGroupConflictsBySource_MultipleExistingCoursesWithDifferentConflictTypes() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "B202", "周一 08:00-10:00"));
        scheduleService.addSchedule(createRequest("英语", "李老师", "A101", "周一 08:00-10:00"));

        CourseScheduleConflictPreCheckRequest request = new CourseScheduleConflictPreCheckRequest();
        request.setCourseName("物理");
        request.setTeacherName("张老师");
        request.setClassroom("A101");
        request.setTimeSlot("周一 08:00-10:00");

        List<com.coursescheduler.dto.ConflictGroupDTO> result = scheduleService.groupConflictsBySource(request);

        assertEquals(1, result.size());
        com.coursescheduler.dto.ConflictGroupDTO group = result.get(0);
        assertEquals(ConflictDetailDTO.SourceType.EXISTING_COURSE, group.getSourceType());
        assertEquals(2, group.getConflictCount());
        assertEquals(1, group.getTeacherConflictCount());
        assertEquals(1, group.getClassroomConflictCount());
        assertTrue(group.getCourseNames().contains("数学"));
        assertTrue(group.getCourseNames().contains("英语"));
        assertEquals(2, group.getCourseNames().size());
    }

    @Test
    void testGroupConflictsBySource_NoCourseCreated() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));

        CourseScheduleConflictPreCheckRequest request = new CourseScheduleConflictPreCheckRequest();
        request.setCourseName("物理");
        request.setTeacherName("张老师");
        request.setClassroom("A101");
        request.setTimeSlot("周一 08:00-10:00");

        scheduleService.groupConflictsBySource(request);

        assertEquals(1, scheduleService.findSchedules(null).size());
    }

    @Test
    void testGroupConflictsBySource_DeduplicatesCourseNames() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));

        CourseScheduleConflictPreCheckRequest request = new CourseScheduleConflictPreCheckRequest();
        request.setCourseName("物理");
        request.setTeacherName("张老师");
        request.setClassroom("A101");
        request.setTimeSlot("周一 08:00-10:00");

        List<com.coursescheduler.dto.ConflictGroupDTO> result = scheduleService.groupConflictsBySource(request);

        assertEquals(1, result.size());
        com.coursescheduler.dto.ConflictGroupDTO group = result.get(0);
        assertEquals(1, group.getCourseNames().size());
        assertEquals("数学", group.getCourseNames().get(0));
    }

    @Test
    void testGroupConflictsBySourceForTeacherBatch_NoConflicts_ReturnsEmptyList() {
        TeacherBatchPreCheckRequest request = new TeacherBatchPreCheckRequest();
        request.setTeacherName("张老师");
        List<TeacherBatchPreCheckItemRequest> items = new ArrayList<>();
        TeacherBatchPreCheckItemRequest item1 = new TeacherBatchPreCheckItemRequest();
        item1.setCourseName("数学");
        item1.setClassroom("A101");
        item1.setTimeSlot("周一 08:00-10:00");
        items.add(item1);
        TeacherBatchPreCheckItemRequest item2 = new TeacherBatchPreCheckItemRequest();
        item2.setCourseName("物理");
        item2.setClassroom("B202");
        item2.setTimeSlot("周二 08:00-10:00");
        items.add(item2);
        request.setItems(items);

        List<com.coursescheduler.dto.ConflictGroupDTO> result = scheduleService.groupConflictsBySourceForTeacherBatch(request);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testGroupConflictsBySourceForTeacherBatch_OnlyExistingCourseConflicts() {
        scheduleService.addSchedule(createRequest("已有课", "张老师", "A101", "周一 08:00-10:00"));

        TeacherBatchPreCheckRequest request = new TeacherBatchPreCheckRequest();
        request.setTeacherName("张老师");
        List<TeacherBatchPreCheckItemRequest> items = new ArrayList<>();
        TeacherBatchPreCheckItemRequest item = new TeacherBatchPreCheckItemRequest();
        item.setCourseName("新课");
        item.setClassroom("B202");
        item.setTimeSlot("周一 08:00-10:00");
        items.add(item);
        request.setItems(items);

        List<com.coursescheduler.dto.ConflictGroupDTO> result = scheduleService.groupConflictsBySourceForTeacherBatch(request);

        assertEquals(1, result.size());
        com.coursescheduler.dto.ConflictGroupDTO group = result.get(0);
        assertEquals(ConflictDetailDTO.SourceType.EXISTING_COURSE, group.getSourceType());
        assertTrue(group.getConflictCount() > 0);
        assertTrue(group.getCourseNames().contains("已有课"));
    }

    @Test
    void testGroupConflictsBySourceForTeacherBatch_OnlyPendingItemConflicts() {
        TeacherBatchPreCheckRequest request = new TeacherBatchPreCheckRequest();
        request.setTeacherName("张老师");
        List<TeacherBatchPreCheckItemRequest> items = new ArrayList<>();
        TeacherBatchPreCheckItemRequest item1 = new TeacherBatchPreCheckItemRequest();
        item1.setCourseName("数学");
        item1.setClassroom("A101");
        item1.setTimeSlot("周一 08:00-10:00");
        items.add(item1);
        TeacherBatchPreCheckItemRequest item2 = new TeacherBatchPreCheckItemRequest();
        item2.setCourseName("物理");
        item2.setClassroom("B202");
        item2.setTimeSlot("周一 08:00-10:00");
        items.add(item2);
        request.setItems(items);

        List<com.coursescheduler.dto.ConflictGroupDTO> result = scheduleService.groupConflictsBySourceForTeacherBatch(request);

        assertEquals(1, result.size());
        com.coursescheduler.dto.ConflictGroupDTO group = result.get(0);
        assertEquals(ConflictDetailDTO.SourceType.PENDING_ITEM, group.getSourceType());
        assertEquals(1, group.getConflictCount());
        assertEquals(1, group.getTeacherConflictCount());
        assertEquals(0, group.getClassroomConflictCount());
        assertTrue(group.getCourseNames().contains("数学"));
        assertTrue(group.getCourseNames().contains("物理"));
    }

    @Test
    void testGroupConflictsBySourceForTeacherBatch_BothSourceTypesPresent() {
        scheduleService.addSchedule(createRequest("已有课", "张老师", "C303", "周一 08:00-10:00"));

        TeacherBatchPreCheckRequest request = new TeacherBatchPreCheckRequest();
        request.setTeacherName("张老师");
        List<TeacherBatchPreCheckItemRequest> items = new ArrayList<>();
        TeacherBatchPreCheckItemRequest item1 = new TeacherBatchPreCheckItemRequest();
        item1.setCourseName("数学");
        item1.setClassroom("A101");
        item1.setTimeSlot("周一 08:00-10:00");
        items.add(item1);
        TeacherBatchPreCheckItemRequest item2 = new TeacherBatchPreCheckItemRequest();
        item2.setCourseName("物理");
        item2.setClassroom("B202");
        item2.setTimeSlot("周一 08:00-10:00");
        items.add(item2);
        request.setItems(items);

        List<com.coursescheduler.dto.ConflictGroupDTO> result = scheduleService.groupConflictsBySourceForTeacherBatch(request);

        assertEquals(2, result.size());

        com.coursescheduler.dto.ConflictGroupDTO existingGroup = result.get(0);
        assertEquals(ConflictDetailDTO.SourceType.EXISTING_COURSE, existingGroup.getSourceType());
        assertEquals(2, existingGroup.getConflictCount());
        assertTrue(existingGroup.getCourseNames().contains("已有课"));
        assertTrue(existingGroup.getCourseNames().contains("数学"));
        assertTrue(existingGroup.getCourseNames().contains("物理"));

        com.coursescheduler.dto.ConflictGroupDTO pendingGroup = result.get(1);
        assertEquals(ConflictDetailDTO.SourceType.PENDING_ITEM, pendingGroup.getSourceType());
        assertEquals(1, pendingGroup.getConflictCount());
        assertTrue(pendingGroup.getCourseNames().contains("数学"));
        assertTrue(pendingGroup.getCourseNames().contains("物理"));
    }

    @Test
    void testGroupConflictsBySourceForTeacherBatch_TeacherAndClassroomCounts() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));

        TeacherBatchPreCheckRequest request = new TeacherBatchPreCheckRequest();
        request.setTeacherName("张老师");
        List<TeacherBatchPreCheckItemRequest> items = new ArrayList<>();
        TeacherBatchPreCheckItemRequest item1 = new TeacherBatchPreCheckItemRequest();
        item1.setCourseName("物理");
        item1.setClassroom("A101");
        item1.setTimeSlot("周一 08:00-10:00");
        items.add(item1);
        request.setItems(items);

        List<com.coursescheduler.dto.ConflictGroupDTO> result = scheduleService.groupConflictsBySourceForTeacherBatch(request);

        assertEquals(1, result.size());
        com.coursescheduler.dto.ConflictGroupDTO group = result.get(0);
        assertEquals(ConflictDetailDTO.SourceType.EXISTING_COURSE, group.getSourceType());
        assertEquals(2, group.getConflictCount());
        assertEquals(1, group.getTeacherConflictCount());
        assertEquals(1, group.getClassroomConflictCount());
        assertEquals(group.getConflictCount(), group.getTeacherConflictCount() + group.getClassroomConflictCount());
    }

    @Test
    void testGroupConflictsBySourceForClassroomBatch_NoConflicts_ReturnsEmptyList() {
        ClassroomBatchPreCheckRequest request = new ClassroomBatchPreCheckRequest();
        request.setClassroom("A101");
        List<ClassroomBatchPreCheckItemRequest> items = new ArrayList<>();
        ClassroomBatchPreCheckItemRequest item1 = new ClassroomBatchPreCheckItemRequest();
        item1.setCourseName("数学");
        item1.setTeacherName("张老师");
        item1.setTimeSlot("周一 08:00-10:00");
        items.add(item1);
        ClassroomBatchPreCheckItemRequest item2 = new ClassroomBatchPreCheckItemRequest();
        item2.setCourseName("物理");
        item2.setTeacherName("李老师");
        item2.setTimeSlot("周二 08:00-10:00");
        items.add(item2);
        request.setItems(items);

        List<com.coursescheduler.dto.ConflictGroupDTO> result = scheduleService.groupConflictsBySourceForClassroomBatch(request);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testGroupConflictsBySourceForClassroomBatch_OnlyExistingCourseConflicts() {
        scheduleService.addSchedule(createRequest("已有课", "王老师", "A101", "周一 08:00-10:00"));

        ClassroomBatchPreCheckRequest request = new ClassroomBatchPreCheckRequest();
        request.setClassroom("A101");
        List<ClassroomBatchPreCheckItemRequest> items = new ArrayList<>();
        ClassroomBatchPreCheckItemRequest item = new ClassroomBatchPreCheckItemRequest();
        item.setCourseName("新课");
        item.setTeacherName("张老师");
        item.setTimeSlot("周一 08:00-10:00");
        items.add(item);
        request.setItems(items);

        List<com.coursescheduler.dto.ConflictGroupDTO> result = scheduleService.groupConflictsBySourceForClassroomBatch(request);

        assertEquals(1, result.size());
        com.coursescheduler.dto.ConflictGroupDTO group = result.get(0);
        assertEquals(ConflictDetailDTO.SourceType.EXISTING_COURSE, group.getSourceType());
        assertTrue(group.getConflictCount() > 0);
        assertTrue(group.getCourseNames().contains("已有课"));
    }

    @Test
    void testGroupConflictsBySourceForClassroomBatch_OnlyPendingItemConflicts() {
        ClassroomBatchPreCheckRequest request = new ClassroomBatchPreCheckRequest();
        request.setClassroom("A101");
        List<ClassroomBatchPreCheckItemRequest> items = new ArrayList<>();
        ClassroomBatchPreCheckItemRequest item1 = new ClassroomBatchPreCheckItemRequest();
        item1.setCourseName("数学");
        item1.setTeacherName("张老师");
        item1.setTimeSlot("周一 08:00-10:00");
        items.add(item1);
        ClassroomBatchPreCheckItemRequest item2 = new ClassroomBatchPreCheckItemRequest();
        item2.setCourseName("物理");
        item2.setTeacherName("李老师");
        item2.setTimeSlot("周一 08:00-10:00");
        items.add(item2);
        request.setItems(items);

        List<com.coursescheduler.dto.ConflictGroupDTO> result = scheduleService.groupConflictsBySourceForClassroomBatch(request);

        assertEquals(1, result.size());
        com.coursescheduler.dto.ConflictGroupDTO group = result.get(0);
        assertEquals(ConflictDetailDTO.SourceType.PENDING_ITEM, group.getSourceType());
        assertEquals(1, group.getConflictCount());
        assertEquals(0, group.getTeacherConflictCount());
        assertEquals(1, group.getClassroomConflictCount());
        assertTrue(group.getCourseNames().contains("数学"));
        assertTrue(group.getCourseNames().contains("物理"));
    }

    @Test
    void testGroupConflictsBySourceForClassroomBatch_BothSourceTypesPresent() {
        scheduleService.addSchedule(createRequest("已有课", "王老师", "A101", "周一 08:00-10:00"));

        ClassroomBatchPreCheckRequest request = new ClassroomBatchPreCheckRequest();
        request.setClassroom("A101");
        List<ClassroomBatchPreCheckItemRequest> items = new ArrayList<>();
        ClassroomBatchPreCheckItemRequest item1 = new ClassroomBatchPreCheckItemRequest();
        item1.setCourseName("数学");
        item1.setTeacherName("张老师");
        item1.setTimeSlot("周一 08:00-10:00");
        items.add(item1);
        ClassroomBatchPreCheckItemRequest item2 = new ClassroomBatchPreCheckItemRequest();
        item2.setCourseName("物理");
        item2.setTeacherName("李老师");
        item2.setTimeSlot("周一 08:00-10:00");
        items.add(item2);
        request.setItems(items);

        List<com.coursescheduler.dto.ConflictGroupDTO> result = scheduleService.groupConflictsBySourceForClassroomBatch(request);

        assertEquals(2, result.size());

        com.coursescheduler.dto.ConflictGroupDTO existingGroup = result.get(0);
        assertEquals(ConflictDetailDTO.SourceType.EXISTING_COURSE, existingGroup.getSourceType());
        assertEquals(2, existingGroup.getConflictCount());
        assertTrue(existingGroup.getCourseNames().contains("已有课"));
        assertTrue(existingGroup.getCourseNames().contains("数学"));
        assertTrue(existingGroup.getCourseNames().contains("物理"));

        com.coursescheduler.dto.ConflictGroupDTO pendingGroup = result.get(1);
        assertEquals(ConflictDetailDTO.SourceType.PENDING_ITEM, pendingGroup.getSourceType());
        assertEquals(1, pendingGroup.getConflictCount());
        assertTrue(pendingGroup.getCourseNames().contains("数学"));
        assertTrue(pendingGroup.getCourseNames().contains("物理"));
    }

    @Test
    void testGroupConflictsBySourceForClassroomBatch_TeacherAndClassroomCounts() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));

        ClassroomBatchPreCheckRequest request = new ClassroomBatchPreCheckRequest();
        request.setClassroom("A101");
        List<ClassroomBatchPreCheckItemRequest> items = new ArrayList<>();
        ClassroomBatchPreCheckItemRequest item1 = new ClassroomBatchPreCheckItemRequest();
        item1.setCourseName("物理");
        item1.setTeacherName("张老师");
        item1.setTimeSlot("周一 08:00-10:00");
        items.add(item1);
        request.setItems(items);

        List<com.coursescheduler.dto.ConflictGroupDTO> result = scheduleService.groupConflictsBySourceForClassroomBatch(request);

        assertEquals(1, result.size());
        com.coursescheduler.dto.ConflictGroupDTO group = result.get(0);
        assertEquals(ConflictDetailDTO.SourceType.EXISTING_COURSE, group.getSourceType());
        assertEquals(2, group.getConflictCount());
        assertEquals(1, group.getTeacherConflictCount());
        assertEquals(1, group.getClassroomConflictCount());
        assertEquals(group.getConflictCount(), group.getTeacherConflictCount() + group.getClassroomConflictCount());
    }

    @Test
    void testGroupConflictsBySourceForTeacherBatch_NoCoursesCreated() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));
        int beforeCount = scheduleService.findSchedules(null).size();

        TeacherBatchPreCheckRequest request = new TeacherBatchPreCheckRequest();
        request.setTeacherName("张老师");
        List<TeacherBatchPreCheckItemRequest> items = new ArrayList<>();
        TeacherBatchPreCheckItemRequest item = new TeacherBatchPreCheckItemRequest();
        item.setCourseName("物理");
        item.setClassroom("A101");
        item.setTimeSlot("周一 08:00-10:00");
        items.add(item);
        request.setItems(items);

        scheduleService.groupConflictsBySourceForTeacherBatch(request);

        assertEquals(beforeCount, scheduleService.findSchedules(null).size());
    }

    @Test
    void testGroupConflictsBySourceForTeacherBatch_SameTimeSameTeacherSameClassroom_OnePendingPair() {
        TeacherBatchPreCheckRequest request = new TeacherBatchPreCheckRequest();
        request.setTeacherName("张老师");
        List<TeacherBatchPreCheckItemRequest> items = new ArrayList<>();
        TeacherBatchPreCheckItemRequest item1 = new TeacherBatchPreCheckItemRequest();
        item1.setCourseName("数学");
        item1.setClassroom("A101");
        item1.setTimeSlot("周一 08:00-10:00");
        items.add(item1);
        TeacherBatchPreCheckItemRequest item2 = new TeacherBatchPreCheckItemRequest();
        item2.setCourseName("物理");
        item2.setClassroom("A101");
        item2.setTimeSlot("周一 08:00-10:00");
        items.add(item2);
        request.setItems(items);

        List<com.coursescheduler.dto.ConflictGroupDTO> result = scheduleService.groupConflictsBySourceForTeacherBatch(request);

        assertEquals(1, result.size());
        com.coursescheduler.dto.ConflictGroupDTO group = result.get(0);
        assertEquals(ConflictDetailDTO.SourceType.PENDING_ITEM, group.getSourceType());
        assertEquals(1, group.getConflictCount());
        assertEquals(1, group.getTeacherConflictCount());
        assertEquals(1, group.getClassroomConflictCount());
        assertTrue(group.getCourseNames().contains("数学"));
        assertTrue(group.getCourseNames().contains("物理"));
    }

    @Test
    void testGroupConflictsBySourceForClassroomBatch_SameTimeSameTeacherSameClassroom_OnePendingPair() {
        ClassroomBatchPreCheckRequest request = new ClassroomBatchPreCheckRequest();
        request.setClassroom("A101");
        List<ClassroomBatchPreCheckItemRequest> items = new ArrayList<>();
        ClassroomBatchPreCheckItemRequest item1 = new ClassroomBatchPreCheckItemRequest();
        item1.setCourseName("数学");
        item1.setTeacherName("张老师");
        item1.setTimeSlot("周一 08:00-10:00");
        items.add(item1);
        ClassroomBatchPreCheckItemRequest item2 = new ClassroomBatchPreCheckItemRequest();
        item2.setCourseName("物理");
        item2.setTeacherName("张老师");
        item2.setTimeSlot("周一 08:00-10:00");
        items.add(item2);
        request.setItems(items);

        List<com.coursescheduler.dto.ConflictGroupDTO> result = scheduleService.groupConflictsBySourceForClassroomBatch(request);

        assertEquals(1, result.size());
        com.coursescheduler.dto.ConflictGroupDTO group = result.get(0);
        assertEquals(ConflictDetailDTO.SourceType.PENDING_ITEM, group.getSourceType());
        assertEquals(1, group.getConflictCount());
        assertEquals(1, group.getTeacherConflictCount());
        assertEquals(1, group.getClassroomConflictCount());
        assertTrue(group.getCourseNames().contains("数学"));
        assertTrue(group.getCourseNames().contains("物理"));
    }

    @Test
    void testGroupConflictsByConflictType_NoConflicts_ReturnsEmptyList() {
        CourseScheduleConflictPreCheckRequest request = new CourseScheduleConflictPreCheckRequest();
        request.setCourseName("物理");
        request.setTeacherName("李老师");
        request.setClassroom("B202");
        request.setTimeSlot("周一 08:00-10:00");

        List<com.coursescheduler.dto.ConflictTypeGroupDTO> result = scheduleService.groupConflictsByConflictType(request);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testGroupConflictsByConflictType_OnlyTeacherConflict() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));

        CourseScheduleConflictPreCheckRequest request = new CourseScheduleConflictPreCheckRequest();
        request.setCourseName("物理");
        request.setTeacherName("张老师");
        request.setClassroom("B202");
        request.setTimeSlot("周一 08:00-10:00");

        List<com.coursescheduler.dto.ConflictTypeGroupDTO> result = scheduleService.groupConflictsByConflictType(request);

        assertEquals(1, result.size());
        com.coursescheduler.dto.ConflictTypeGroupDTO group = result.get(0);
        assertEquals(ConflictDetailDTO.ConflictType.TEACHER, group.getConflictType());
        assertEquals(1, group.getConflictCount());
        assertEquals(1, group.getExistingCourseConflictCount());
        assertEquals(0, group.getPendingItemConflictCount());
        assertTrue(group.getCourseNames().contains("数学"));
    }

    @Test
    void testGroupConflictsByConflictType_OnlyClassroomConflict() {
        scheduleService.addSchedule(createRequest("数学", "李老师", "A101", "周一 08:00-10:00"));

        CourseScheduleConflictPreCheckRequest request = new CourseScheduleConflictPreCheckRequest();
        request.setCourseName("物理");
        request.setTeacherName("张老师");
        request.setClassroom("A101");
        request.setTimeSlot("周一 08:00-10:00");

        List<com.coursescheduler.dto.ConflictTypeGroupDTO> result = scheduleService.groupConflictsByConflictType(request);

        assertEquals(1, result.size());
        com.coursescheduler.dto.ConflictTypeGroupDTO group = result.get(0);
        assertEquals(ConflictDetailDTO.ConflictType.CLASSROOM, group.getConflictType());
        assertEquals(1, group.getConflictCount());
        assertEquals(1, group.getExistingCourseConflictCount());
        assertEquals(0, group.getPendingItemConflictCount());
        assertTrue(group.getCourseNames().contains("数学"));
    }

    @Test
    void testGroupConflictsByConflictType_BothConflictTypes() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));

        CourseScheduleConflictPreCheckRequest request = new CourseScheduleConflictPreCheckRequest();
        request.setCourseName("物理");
        request.setTeacherName("张老师");
        request.setClassroom("A101");
        request.setTimeSlot("周一 08:00-10:00");

        List<com.coursescheduler.dto.ConflictTypeGroupDTO> result = scheduleService.groupConflictsByConflictType(request);

        assertEquals(2, result.size());

        com.coursescheduler.dto.ConflictTypeGroupDTO teacherGroup = result.get(0);
        assertEquals(ConflictDetailDTO.ConflictType.TEACHER, teacherGroup.getConflictType());
        assertEquals(1, teacherGroup.getConflictCount());
        assertEquals(1, teacherGroup.getExistingCourseConflictCount());
        assertEquals(0, teacherGroup.getPendingItemConflictCount());

        com.coursescheduler.dto.ConflictTypeGroupDTO classroomGroup = result.get(1);
        assertEquals(ConflictDetailDTO.ConflictType.CLASSROOM, classroomGroup.getConflictType());
        assertEquals(1, classroomGroup.getConflictCount());
        assertEquals(1, classroomGroup.getExistingCourseConflictCount());
        assertEquals(0, classroomGroup.getPendingItemConflictCount());
    }

    @Test
    void testGroupConflictsByConflictType_DeduplicatesCourseNames() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));

        CourseScheduleConflictPreCheckRequest request = new CourseScheduleConflictPreCheckRequest();
        request.setCourseName("物理");
        request.setTeacherName("张老师");
        request.setClassroom("A101");
        request.setTimeSlot("周一 08:00-10:00");

        List<com.coursescheduler.dto.ConflictTypeGroupDTO> result = scheduleService.groupConflictsByConflictType(request);

        for (com.coursescheduler.dto.ConflictTypeGroupDTO group : result) {
            assertEquals(1, group.getCourseNames().size());
            assertEquals("数学", group.getCourseNames().get(0));
        }
    }

    @Test
    void testGroupConflictsByConflictType_NoCoursesCreated() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));
        int beforeCount = scheduleService.findSchedules(null).size();

        CourseScheduleConflictPreCheckRequest request = new CourseScheduleConflictPreCheckRequest();
        request.setCourseName("物理");
        request.setTeacherName("张老师");
        request.setClassroom("A101");
        request.setTimeSlot("周一 08:00-10:00");

        scheduleService.groupConflictsByConflictType(request);

        assertEquals(beforeCount, scheduleService.findSchedules(null).size());
    }

    @Test
    void testGroupConflictsByConflictType_ConflictCountEqualsExistingPlusPending() {
        CourseScheduleConflictPreCheckRequest request = new CourseScheduleConflictPreCheckRequest();
        request.setCourseName("物理");
        request.setTeacherName("张老师");
        request.setClassroom("A101");
        request.setTimeSlot("周一 08:00-10:00");

        List<com.coursescheduler.dto.ConflictTypeGroupDTO> result = scheduleService.groupConflictsByConflictType(request);

        for (com.coursescheduler.dto.ConflictTypeGroupDTO group : result) {
            assertEquals(group.getConflictCount(), group.getExistingCourseConflictCount() + group.getPendingItemConflictCount());
        }
    }

    @Test
    void testGroupConflictsByConflictTypeForTeacherBatch_NoConflicts_ReturnsEmptyList() {
        TeacherBatchPreCheckRequest request = new TeacherBatchPreCheckRequest();
        request.setTeacherName("张老师");
        List<TeacherBatchPreCheckItemRequest> items = new ArrayList<>();
        TeacherBatchPreCheckItemRequest item1 = new TeacherBatchPreCheckItemRequest();
        item1.setCourseName("数学");
        item1.setClassroom("A101");
        item1.setTimeSlot("周一 08:00-10:00");
        items.add(item1);
        TeacherBatchPreCheckItemRequest item2 = new TeacherBatchPreCheckItemRequest();
        item2.setCourseName("物理");
        item2.setClassroom("B202");
        item2.setTimeSlot("周二 08:00-10:00");
        items.add(item2);
        request.setItems(items);

        List<com.coursescheduler.dto.ConflictTypeGroupDTO> result = scheduleService.groupConflictsByConflictTypeForTeacherBatch(request);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testGroupConflictsByConflictTypeForTeacherBatch_OnlyTeacherConflictFromExistingCourse() {
        scheduleService.addSchedule(createRequest("已有课", "张老师", "A101", "周一 08:00-10:00"));

        TeacherBatchPreCheckRequest request = new TeacherBatchPreCheckRequest();
        request.setTeacherName("张老师");
        List<TeacherBatchPreCheckItemRequest> items = new ArrayList<>();
        TeacherBatchPreCheckItemRequest item = new TeacherBatchPreCheckItemRequest();
        item.setCourseName("新课");
        item.setClassroom("B202");
        item.setTimeSlot("周一 08:00-10:00");
        items.add(item);
        request.setItems(items);

        List<com.coursescheduler.dto.ConflictTypeGroupDTO> result = scheduleService.groupConflictsByConflictTypeForTeacherBatch(request);

        assertEquals(1, result.size());
        com.coursescheduler.dto.ConflictTypeGroupDTO group = result.get(0);
        assertEquals(ConflictDetailDTO.ConflictType.TEACHER, group.getConflictType());
        assertEquals(1, group.getConflictCount());
        assertEquals(1, group.getExistingCourseConflictCount());
        assertEquals(0, group.getPendingItemConflictCount());
        assertTrue(group.getCourseNames().contains("已有课"));
        assertTrue(group.getCourseNames().contains("新课"));
    }

    @Test
    void testGroupConflictsByConflictTypeForTeacherBatch_OnlyClassroomConflictFromExistingCourse() {
        scheduleService.addSchedule(createRequest("已有课", "李老师", "A101", "周一 08:00-10:00"));

        TeacherBatchPreCheckRequest request = new TeacherBatchPreCheckRequest();
        request.setTeacherName("张老师");
        List<TeacherBatchPreCheckItemRequest> items = new ArrayList<>();
        TeacherBatchPreCheckItemRequest item = new TeacherBatchPreCheckItemRequest();
        item.setCourseName("新课");
        item.setClassroom("A101");
        item.setTimeSlot("周一 08:00-10:00");
        items.add(item);
        request.setItems(items);

        List<com.coursescheduler.dto.ConflictTypeGroupDTO> result = scheduleService.groupConflictsByConflictTypeForTeacherBatch(request);

        assertEquals(1, result.size());
        com.coursescheduler.dto.ConflictTypeGroupDTO group = result.get(0);
        assertEquals(ConflictDetailDTO.ConflictType.CLASSROOM, group.getConflictType());
        assertEquals(1, group.getConflictCount());
        assertEquals(1, group.getExistingCourseConflictCount());
        assertEquals(0, group.getPendingItemConflictCount());
        assertTrue(group.getCourseNames().contains("已有课"));
        assertTrue(group.getCourseNames().contains("新课"));
    }

    @Test
    void testGroupConflictsByConflictTypeForTeacherBatch_PendingItemDedup_TeacherConflict() {
        TeacherBatchPreCheckRequest request = new TeacherBatchPreCheckRequest();
        request.setTeacherName("张老师");
        List<TeacherBatchPreCheckItemRequest> items = new ArrayList<>();
        TeacherBatchPreCheckItemRequest item1 = new TeacherBatchPreCheckItemRequest();
        item1.setCourseName("数学");
        item1.setClassroom("A101");
        item1.setTimeSlot("周一 08:00-10:00");
        items.add(item1);
        TeacherBatchPreCheckItemRequest item2 = new TeacherBatchPreCheckItemRequest();
        item2.setCourseName("物理");
        item2.setClassroom("B202");
        item2.setTimeSlot("周一 08:00-10:00");
        items.add(item2);
        request.setItems(items);

        List<com.coursescheduler.dto.ConflictTypeGroupDTO> result = scheduleService.groupConflictsByConflictTypeForTeacherBatch(request);

        assertEquals(1, result.size());
        com.coursescheduler.dto.ConflictTypeGroupDTO group = result.get(0);
        assertEquals(ConflictDetailDTO.ConflictType.TEACHER, group.getConflictType());
        assertEquals(1, group.getConflictCount());
        assertEquals(0, group.getExistingCourseConflictCount());
        assertEquals(1, group.getPendingItemConflictCount());
        assertTrue(group.getCourseNames().contains("数学"));
        assertTrue(group.getCourseNames().contains("物理"));
    }

    @Test
    void testGroupConflictsByConflictTypeForTeacherBatch_PendingItemDedup_ClassroomConflict() {
        TeacherBatchPreCheckRequest request = new TeacherBatchPreCheckRequest();
        request.setTeacherName("张老师");
        List<TeacherBatchPreCheckItemRequest> items = new ArrayList<>();
        TeacherBatchPreCheckItemRequest item1 = new TeacherBatchPreCheckItemRequest();
        item1.setCourseName("数学");
        item1.setClassroom("A101");
        item1.setTimeSlot("周一 08:00-10:00");
        items.add(item1);
        TeacherBatchPreCheckItemRequest item2 = new TeacherBatchPreCheckItemRequest();
        item2.setCourseName("物理");
        item2.setClassroom("A101");
        item2.setTimeSlot("周二 08:00-10:00");
        items.add(item2);
        request.setItems(items);

        List<com.coursescheduler.dto.ConflictTypeGroupDTO> result = scheduleService.groupConflictsByConflictTypeForTeacherBatch(request);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testGroupConflictsByConflictTypeForTeacherBatch_SameTimeSameClassroom_BothConflictTypesOnePendingPair() {
        TeacherBatchPreCheckRequest request = new TeacherBatchPreCheckRequest();
        request.setTeacherName("张老师");
        List<TeacherBatchPreCheckItemRequest> items = new ArrayList<>();
        TeacherBatchPreCheckItemRequest item1 = new TeacherBatchPreCheckItemRequest();
        item1.setCourseName("数学");
        item1.setClassroom("A101");
        item1.setTimeSlot("周一 08:00-10:00");
        items.add(item1);
        TeacherBatchPreCheckItemRequest item2 = new TeacherBatchPreCheckItemRequest();
        item2.setCourseName("物理");
        item2.setClassroom("A101");
        item2.setTimeSlot("周一 08:00-10:00");
        items.add(item2);
        request.setItems(items);

        List<com.coursescheduler.dto.ConflictTypeGroupDTO> result = scheduleService.groupConflictsByConflictTypeForTeacherBatch(request);

        assertEquals(2, result.size());

        com.coursescheduler.dto.ConflictTypeGroupDTO teacherGroup = result.get(0);
        assertEquals(ConflictDetailDTO.ConflictType.TEACHER, teacherGroup.getConflictType());
        assertEquals(1, teacherGroup.getConflictCount());
        assertEquals(0, teacherGroup.getExistingCourseConflictCount());
        assertEquals(1, teacherGroup.getPendingItemConflictCount());

        com.coursescheduler.dto.ConflictTypeGroupDTO classroomGroup = result.get(1);
        assertEquals(ConflictDetailDTO.ConflictType.CLASSROOM, classroomGroup.getConflictType());
        assertEquals(1, classroomGroup.getConflictCount());
        assertEquals(0, classroomGroup.getExistingCourseConflictCount());
        assertEquals(1, classroomGroup.getPendingItemConflictCount());
    }

    @Test
    void testGroupConflictsByConflictTypeForTeacherBatch_MixedExistingAndPending() {
        scheduleService.addSchedule(createRequest("已有课", "张老师", "C303", "周一 08:00-10:00"));

        TeacherBatchPreCheckRequest request = new TeacherBatchPreCheckRequest();
        request.setTeacherName("张老师");
        List<TeacherBatchPreCheckItemRequest> items = new ArrayList<>();
        TeacherBatchPreCheckItemRequest item1 = new TeacherBatchPreCheckItemRequest();
        item1.setCourseName("数学");
        item1.setClassroom("A101");
        item1.setTimeSlot("周一 08:00-10:00");
        items.add(item1);
        TeacherBatchPreCheckItemRequest item2 = new TeacherBatchPreCheckItemRequest();
        item2.setCourseName("物理");
        item2.setClassroom("B202");
        item2.setTimeSlot("周一 08:00-10:00");
        items.add(item2);
        request.setItems(items);

        List<com.coursescheduler.dto.ConflictTypeGroupDTO> result = scheduleService.groupConflictsByConflictTypeForTeacherBatch(request);

        assertEquals(1, result.size());
        com.coursescheduler.dto.ConflictTypeGroupDTO group = result.get(0);
        assertEquals(ConflictDetailDTO.ConflictType.TEACHER, group.getConflictType());
        assertEquals(3, group.getConflictCount());
        assertEquals(2, group.getExistingCourseConflictCount());
        assertEquals(1, group.getPendingItemConflictCount());
        assertTrue(group.getCourseNames().contains("已有课"));
        assertTrue(group.getCourseNames().contains("数学"));
        assertTrue(group.getCourseNames().contains("物理"));
    }

    @Test
    void testGroupConflictsByConflictTypeForTeacherBatch_ConflictCountEqualsExistingPlusPending() {
        scheduleService.addSchedule(createRequest("已有课", "张老师", "A101", "周一 08:00-10:00"));

        TeacherBatchPreCheckRequest request = new TeacherBatchPreCheckRequest();
        request.setTeacherName("张老师");
        List<TeacherBatchPreCheckItemRequest> items = new ArrayList<>();
        TeacherBatchPreCheckItemRequest item1 = new TeacherBatchPreCheckItemRequest();
        item1.setCourseName("数学");
        item1.setClassroom("B202");
        item1.setTimeSlot("周一 08:00-10:00");
        items.add(item1);
        TeacherBatchPreCheckItemRequest item2 = new TeacherBatchPreCheckItemRequest();
        item2.setCourseName("物理");
        item2.setClassroom("C303");
        item2.setTimeSlot("周一 08:00-10:00");
        items.add(item2);
        request.setItems(items);

        List<com.coursescheduler.dto.ConflictTypeGroupDTO> result = scheduleService.groupConflictsByConflictTypeForTeacherBatch(request);

        for (com.coursescheduler.dto.ConflictTypeGroupDTO group : result) {
            assertEquals(group.getConflictCount(), group.getExistingCourseConflictCount() + group.getPendingItemConflictCount());
        }
    }

    @Test
    void testGroupConflictsByConflictTypeForTeacherBatch_NoCoursesCreated() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));
        int beforeCount = scheduleService.findSchedules(null).size();

        TeacherBatchPreCheckRequest request = new TeacherBatchPreCheckRequest();
        request.setTeacherName("张老师");
        List<TeacherBatchPreCheckItemRequest> items = new ArrayList<>();
        TeacherBatchPreCheckItemRequest item = new TeacherBatchPreCheckItemRequest();
        item.setCourseName("物理");
        item.setClassroom("A101");
        item.setTimeSlot("周一 08:00-10:00");
        items.add(item);
        request.setItems(items);

        scheduleService.groupConflictsByConflictTypeForTeacherBatch(request);

        assertEquals(beforeCount, scheduleService.findSchedules(null).size());
    }

    @Test
    void testGroupConflictsByConflictTypeForClassroomBatch_NoConflicts_ReturnsEmptyList() {
        ClassroomBatchPreCheckRequest request = new ClassroomBatchPreCheckRequest();
        request.setClassroom("A101");
        List<ClassroomBatchPreCheckItemRequest> items = new ArrayList<>();
        ClassroomBatchPreCheckItemRequest item1 = new ClassroomBatchPreCheckItemRequest();
        item1.setCourseName("数学");
        item1.setTeacherName("张老师");
        item1.setTimeSlot("周一 08:00-10:00");
        items.add(item1);
        ClassroomBatchPreCheckItemRequest item2 = new ClassroomBatchPreCheckItemRequest();
        item2.setCourseName("物理");
        item2.setTeacherName("李老师");
        item2.setTimeSlot("周二 08:00-10:00");
        items.add(item2);
        request.setItems(items);

        List<com.coursescheduler.dto.ConflictTypeGroupDTO> result = scheduleService.groupConflictsByConflictTypeForClassroomBatch(request);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testGroupConflictsByConflictTypeForClassroomBatch_OnlyTeacherConflictFromExistingCourse() {
        scheduleService.addSchedule(createRequest("已有课", "张老师", "B202", "周一 08:00-10:00"));

        ClassroomBatchPreCheckRequest request = new ClassroomBatchPreCheckRequest();
        request.setClassroom("A101");
        List<ClassroomBatchPreCheckItemRequest> items = new ArrayList<>();
        ClassroomBatchPreCheckItemRequest item = new ClassroomBatchPreCheckItemRequest();
        item.setCourseName("新课");
        item.setTeacherName("张老师");
        item.setTimeSlot("周一 08:00-10:00");
        items.add(item);
        request.setItems(items);

        List<com.coursescheduler.dto.ConflictTypeGroupDTO> result = scheduleService.groupConflictsByConflictTypeForClassroomBatch(request);

        assertEquals(1, result.size());
        com.coursescheduler.dto.ConflictTypeGroupDTO group = result.get(0);
        assertEquals(ConflictDetailDTO.ConflictType.TEACHER, group.getConflictType());
        assertEquals(1, group.getConflictCount());
        assertEquals(1, group.getExistingCourseConflictCount());
        assertEquals(0, group.getPendingItemConflictCount());
        assertTrue(group.getCourseNames().contains("已有课"));
        assertTrue(group.getCourseNames().contains("新课"));
    }

    @Test
    void testGroupConflictsByConflictTypeForClassroomBatch_OnlyClassroomConflictFromExistingCourse() {
        scheduleService.addSchedule(createRequest("已有课", "李老师", "A101", "周一 08:00-10:00"));

        ClassroomBatchPreCheckRequest request = new ClassroomBatchPreCheckRequest();
        request.setClassroom("A101");
        List<ClassroomBatchPreCheckItemRequest> items = new ArrayList<>();
        ClassroomBatchPreCheckItemRequest item = new ClassroomBatchPreCheckItemRequest();
        item.setCourseName("新课");
        item.setTeacherName("张老师");
        item.setTimeSlot("周一 08:00-10:00");
        items.add(item);
        request.setItems(items);

        List<com.coursescheduler.dto.ConflictTypeGroupDTO> result = scheduleService.groupConflictsByConflictTypeForClassroomBatch(request);

        assertEquals(1, result.size());
        com.coursescheduler.dto.ConflictTypeGroupDTO group = result.get(0);
        assertEquals(ConflictDetailDTO.ConflictType.CLASSROOM, group.getConflictType());
        assertEquals(1, group.getConflictCount());
        assertEquals(1, group.getExistingCourseConflictCount());
        assertEquals(0, group.getPendingItemConflictCount());
        assertTrue(group.getCourseNames().contains("已有课"));
        assertTrue(group.getCourseNames().contains("新课"));
    }

    @Test
    void testGroupConflictsByConflictTypeForClassroomBatch_PendingItemDedup_ClassroomConflict() {
        ClassroomBatchPreCheckRequest request = new ClassroomBatchPreCheckRequest();
        request.setClassroom("A101");
        List<ClassroomBatchPreCheckItemRequest> items = new ArrayList<>();
        ClassroomBatchPreCheckItemRequest item1 = new ClassroomBatchPreCheckItemRequest();
        item1.setCourseName("数学");
        item1.setTeacherName("张老师");
        item1.setTimeSlot("周一 08:00-10:00");
        items.add(item1);
        ClassroomBatchPreCheckItemRequest item2 = new ClassroomBatchPreCheckItemRequest();
        item2.setCourseName("物理");
        item2.setTeacherName("李老师");
        item2.setTimeSlot("周一 08:00-10:00");
        items.add(item2);
        request.setItems(items);

        List<com.coursescheduler.dto.ConflictTypeGroupDTO> result = scheduleService.groupConflictsByConflictTypeForClassroomBatch(request);

        assertEquals(1, result.size());
        com.coursescheduler.dto.ConflictTypeGroupDTO group = result.get(0);
        assertEquals(ConflictDetailDTO.ConflictType.CLASSROOM, group.getConflictType());
        assertEquals(1, group.getConflictCount());
        assertEquals(0, group.getExistingCourseConflictCount());
        assertEquals(1, group.getPendingItemConflictCount());
        assertTrue(group.getCourseNames().contains("数学"));
        assertTrue(group.getCourseNames().contains("物理"));
    }

    @Test
    void testGroupConflictsByConflictTypeForClassroomBatch_SameTimeSameTeacher_BothConflictTypesOnePendingPair() {
        ClassroomBatchPreCheckRequest request = new ClassroomBatchPreCheckRequest();
        request.setClassroom("A101");
        List<ClassroomBatchPreCheckItemRequest> items = new ArrayList<>();
        ClassroomBatchPreCheckItemRequest item1 = new ClassroomBatchPreCheckItemRequest();
        item1.setCourseName("数学");
        item1.setTeacherName("张老师");
        item1.setTimeSlot("周一 08:00-10:00");
        items.add(item1);
        ClassroomBatchPreCheckItemRequest item2 = new ClassroomBatchPreCheckItemRequest();
        item2.setCourseName("物理");
        item2.setTeacherName("张老师");
        item2.setTimeSlot("周一 08:00-10:00");
        items.add(item2);
        request.setItems(items);

        List<com.coursescheduler.dto.ConflictTypeGroupDTO> result = scheduleService.groupConflictsByConflictTypeForClassroomBatch(request);

        assertEquals(2, result.size());

        com.coursescheduler.dto.ConflictTypeGroupDTO teacherGroup = result.get(0);
        assertEquals(ConflictDetailDTO.ConflictType.TEACHER, teacherGroup.getConflictType());
        assertEquals(1, teacherGroup.getConflictCount());
        assertEquals(0, teacherGroup.getExistingCourseConflictCount());
        assertEquals(1, teacherGroup.getPendingItemConflictCount());

        com.coursescheduler.dto.ConflictTypeGroupDTO classroomGroup = result.get(1);
        assertEquals(ConflictDetailDTO.ConflictType.CLASSROOM, classroomGroup.getConflictType());
        assertEquals(1, classroomGroup.getConflictCount());
        assertEquals(0, classroomGroup.getExistingCourseConflictCount());
        assertEquals(1, classroomGroup.getPendingItemConflictCount());
    }

    @Test
    void testGroupConflictsByConflictTypeForClassroomBatch_MixedExistingAndPending() {
        scheduleService.addSchedule(createRequest("已有课", "李老师", "A101", "周一 08:00-10:00"));

        ClassroomBatchPreCheckRequest request = new ClassroomBatchPreCheckRequest();
        request.setClassroom("A101");
        List<ClassroomBatchPreCheckItemRequest> items = new ArrayList<>();
        ClassroomBatchPreCheckItemRequest item1 = new ClassroomBatchPreCheckItemRequest();
        item1.setCourseName("数学");
        item1.setTeacherName("张老师");
        item1.setTimeSlot("周一 08:00-10:00");
        items.add(item1);
        ClassroomBatchPreCheckItemRequest item2 = new ClassroomBatchPreCheckItemRequest();
        item2.setCourseName("物理");
        item2.setTeacherName("王老师");
        item2.setTimeSlot("周一 08:00-10:00");
        items.add(item2);
        request.setItems(items);

        List<com.coursescheduler.dto.ConflictTypeGroupDTO> result = scheduleService.groupConflictsByConflictTypeForClassroomBatch(request);

        assertEquals(1, result.size());
        com.coursescheduler.dto.ConflictTypeGroupDTO group = result.get(0);
        assertEquals(ConflictDetailDTO.ConflictType.CLASSROOM, group.getConflictType());
        assertEquals(3, group.getConflictCount());
        assertEquals(2, group.getExistingCourseConflictCount());
        assertEquals(1, group.getPendingItemConflictCount());
        assertTrue(group.getCourseNames().contains("已有课"));
        assertTrue(group.getCourseNames().contains("数学"));
        assertTrue(group.getCourseNames().contains("物理"));
    }

    @Test
    void testGroupConflictsByConflictTypeForClassroomBatch_ConflictCountEqualsExistingPlusPending() {
        scheduleService.addSchedule(createRequest("已有课", "李老师", "A101", "周一 08:00-10:00"));

        ClassroomBatchPreCheckRequest request = new ClassroomBatchPreCheckRequest();
        request.setClassroom("A101");
        List<ClassroomBatchPreCheckItemRequest> items = new ArrayList<>();
        ClassroomBatchPreCheckItemRequest item1 = new ClassroomBatchPreCheckItemRequest();
        item1.setCourseName("数学");
        item1.setTeacherName("张老师");
        item1.setTimeSlot("周一 08:00-10:00");
        items.add(item1);
        ClassroomBatchPreCheckItemRequest item2 = new ClassroomBatchPreCheckItemRequest();
        item2.setCourseName("物理");
        item2.setTeacherName("王老师");
        item2.setTimeSlot("周一 08:00-10:00");
        items.add(item2);
        request.setItems(items);

        List<com.coursescheduler.dto.ConflictTypeGroupDTO> result = scheduleService.groupConflictsByConflictTypeForClassroomBatch(request);

        for (com.coursescheduler.dto.ConflictTypeGroupDTO group : result) {
            assertEquals(group.getConflictCount(), group.getExistingCourseConflictCount() + group.getPendingItemConflictCount());
        }
    }

    @Test
    void testGroupConflictsByConflictTypeForClassroomBatch_NoCoursesCreated() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));
        int beforeCount = scheduleService.findSchedules(null).size();

        ClassroomBatchPreCheckRequest request = new ClassroomBatchPreCheckRequest();
        request.setClassroom("A101");
        List<ClassroomBatchPreCheckItemRequest> items = new ArrayList<>();
        ClassroomBatchPreCheckItemRequest item = new ClassroomBatchPreCheckItemRequest();
        item.setCourseName("物理");
        item.setTeacherName("李老师");
        item.setTimeSlot("周一 08:00-10:00");
        items.add(item);
        request.setItems(items);

        scheduleService.groupConflictsByConflictTypeForClassroomBatch(request);

        assertEquals(beforeCount, scheduleService.findSchedules(null).size());
    }

    @Test
    void testGroupConflictsByConflictTypeForTeacherBatch_InvalidRequest_ThrowsException() {
        TeacherBatchPreCheckRequest request = new TeacherBatchPreCheckRequest();
        request.setTeacherName("");
        request.setItems(new ArrayList<>());

        assertThrows(InvalidRequestParameterException.class, () -> {
            scheduleService.groupConflictsByConflictTypeForTeacherBatch(request);
        });
    }

    @Test
    void testGroupConflictsByConflictTypeForClassroomBatch_InvalidRequest_ThrowsException() {
        ClassroomBatchPreCheckRequest request = new ClassroomBatchPreCheckRequest();
        request.setClassroom("");
        request.setItems(new ArrayList<>());

        assertThrows(InvalidRequestParameterException.class, () -> {
            scheduleService.groupConflictsByConflictTypeForClassroomBatch(request);
        });
    }

    @Test
    void testGroupConflictsByConflictTypeForTeacherBatch_TeacherAndClassroomCourseNamesNotMixed() {
        scheduleService.addSchedule(createRequest("已有老师课", "张老师", "X001", "周一 08:00-10:00"));
        scheduleService.addSchedule(createRequest("已有教室课", "王老师", "A101", "周二 14:00-16:00"));

        TeacherBatchPreCheckRequest request = new TeacherBatchPreCheckRequest();
        request.setTeacherName("张老师");
        List<TeacherBatchPreCheckItemRequest> items = new ArrayList<>();
        TeacherBatchPreCheckItemRequest item0 = new TeacherBatchPreCheckItemRequest();
        item0.setCourseName("待排老师A");
        item0.setClassroom("A101");
        item0.setTimeSlot("周一 08:00-10:00");
        items.add(item0);
        TeacherBatchPreCheckItemRequest item1 = new TeacherBatchPreCheckItemRequest();
        item1.setCourseName("待排教室B");
        item1.setClassroom("A101");
        item1.setTimeSlot("周二 14:00-16:00");
        items.add(item1);
        request.setItems(items);

        List<com.coursescheduler.dto.ConflictTypeGroupDTO> result = scheduleService.groupConflictsByConflictTypeForTeacherBatch(request);

        assertTrue(result.size() >= 2);
        com.coursescheduler.dto.ConflictTypeGroupDTO teacherGroup = null;
        com.coursescheduler.dto.ConflictTypeGroupDTO classroomGroup = null;
        for (com.coursescheduler.dto.ConflictTypeGroupDTO g : result) {
            if (g.getConflictType() == ConflictDetailDTO.ConflictType.TEACHER) {
                teacherGroup = g;
            } else if (g.getConflictType() == ConflictDetailDTO.ConflictType.CLASSROOM) {
                classroomGroup = g;
            }
        }
        assertNotNull(teacherGroup);
        assertNotNull(classroomGroup);
        assertTrue(teacherGroup.getCourseNames().contains("已有老师课"));
        assertTrue(teacherGroup.getCourseNames().contains("待排老师A"));
        assertFalse(teacherGroup.getCourseNames().contains("已有教室课"));
        assertFalse(teacherGroup.getCourseNames().contains("待排教室B"));
        assertTrue(classroomGroup.getCourseNames().contains("已有教室课"));
        assertTrue(classroomGroup.getCourseNames().contains("待排教室B"));
        assertFalse(classroomGroup.getCourseNames().contains("已有老师课"));
        assertFalse(classroomGroup.getCourseNames().contains("待排老师A"));
    }

    @Test
    void testGroupConflictsByConflictTypeForClassroomBatch_TeacherAndClassroomCourseNamesNotMixed() {
        scheduleService.addSchedule(createRequest("已有老师课", "张老师", "X001", "周一 08:00-10:00"));
        scheduleService.addSchedule(createRequest("已有教室课", "王老师", "A101", "周二 14:00-16:00"));

        ClassroomBatchPreCheckRequest request = new ClassroomBatchPreCheckRequest();
        request.setClassroom("A101");
        List<ClassroomBatchPreCheckItemRequest> items = new ArrayList<>();
        ClassroomBatchPreCheckItemRequest item0 = new ClassroomBatchPreCheckItemRequest();
        item0.setCourseName("待排教室A");
        item0.setTeacherName("张老师");
        item0.setTimeSlot("周二 14:00-16:00");
        items.add(item0);
        ClassroomBatchPreCheckItemRequest item1 = new ClassroomBatchPreCheckItemRequest();
        item1.setCourseName("待排老师B");
        item1.setTeacherName("张老师");
        item1.setTimeSlot("周一 08:00-10:00");
        items.add(item1);
        request.setItems(items);

        List<com.coursescheduler.dto.ConflictTypeGroupDTO> result = scheduleService.groupConflictsByConflictTypeForClassroomBatch(request);

        assertTrue(result.size() >= 2);
        com.coursescheduler.dto.ConflictTypeGroupDTO teacherGroup2 = null;
        com.coursescheduler.dto.ConflictTypeGroupDTO classroomGroup2 = null;
        for (com.coursescheduler.dto.ConflictTypeGroupDTO g : result) {
            if (g.getConflictType() == ConflictDetailDTO.ConflictType.TEACHER) {
                teacherGroup2 = g;
            } else if (g.getConflictType() == ConflictDetailDTO.ConflictType.CLASSROOM) {
                classroomGroup2 = g;
            }
        }
        assertNotNull(teacherGroup2);
        assertNotNull(classroomGroup2);
        assertTrue(teacherGroup2.getCourseNames().contains("已有老师课"));
        assertTrue(teacherGroup2.getCourseNames().contains("待排老师B"));
        assertFalse(teacherGroup2.getCourseNames().contains("已有教室课"));
        assertFalse(teacherGroup2.getCourseNames().contains("待排教室A"));
        assertTrue(classroomGroup2.getCourseNames().contains("已有教室课"));
        assertTrue(classroomGroup2.getCourseNames().contains("待排教室A"));
        assertFalse(classroomGroup2.getCourseNames().contains("已有老师课"));
        assertFalse(classroomGroup2.getCourseNames().contains("待排老师B"));
    }

    @Test
    void testGetConflictTargetDetailsForTeacherBatch_PendingPairMutualConflict_DeduplicatedToOne() {
        List<TeacherBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        TeacherBatchPreCheckItemRequest item0 = new TeacherBatchPreCheckItemRequest();
        item0.setCourseName("数学");
        item0.setClassroom("A101");
        item0.setTimeSlot("周一 08:00-10:00");
        items.add(item0);
        TeacherBatchPreCheckItemRequest item1 = new TeacherBatchPreCheckItemRequest();
        item1.setCourseName("物理");
        item1.setClassroom("A101");
        item1.setTimeSlot("周一 08:00-10:00");
        items.add(item1);
        TeacherBatchPreCheckRequest request = new TeacherBatchPreCheckRequest();
        request.setTeacherName("张老师");
        request.setItems(items);

        List<ConflictTargetDetailDTO> result = scheduleService.getConflictTargetDetailsForTeacherBatch(request);

        assertEquals(1, result.size());
        ConflictTargetDetailDTO target = result.get(0);
        assertEquals(ConflictDetailDTO.SourceType.PENDING_ITEM, target.getSourceType());
        assertEquals("数学", target.getCourseName());
        assertEquals("张老师", target.getTeacherName());
        assertEquals("A101", target.getClassroom());
        assertEquals("周一 08:00-10:00", target.getTimeSlot());
        assertNotNull(target.getRelatedPendingIndexes());
        assertEquals(2, target.getRelatedPendingIndexes().size());
        assertEquals(0, target.getRelatedPendingIndexes().get(0));
        assertEquals(1, target.getRelatedPendingIndexes().get(1));
        assertNotNull(target.getRelatedCourseNames());
        assertEquals(2, target.getRelatedCourseNames().size());
        assertEquals("数学", target.getRelatedCourseNames().get(0));
        assertEquals("物理", target.getRelatedCourseNames().get(1));
        assertEquals(2, target.getConflictCount());
        assertTrue(target.getConflictTypes().contains(ConflictDetailDTO.ConflictType.TEACHER));
        assertTrue(target.getConflictTypes().contains(ConflictDetailDTO.ConflictType.CLASSROOM));
    }

    @Test
    void testGetConflictTargetDetailsForTeacherBatch_PendingPair_FirstConflictIsExisting_StillUsesItemOwnFields() {
        scheduleService.addSchedule(createRequest("已有课", "王老师", "Z999", "周五 14:00-16:00"));

        List<TeacherBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        TeacherBatchPreCheckItemRequest item0 = new TeacherBatchPreCheckItemRequest();
        item0.setCourseName("数学");
        item0.setClassroom("A101");
        item0.setTimeSlot("周五 14:00-16:00");
        items.add(item0);
        TeacherBatchPreCheckItemRequest item1 = new TeacherBatchPreCheckItemRequest();
        item1.setCourseName("物理");
        item1.setClassroom("A101");
        item1.setTimeSlot("周五 14:00-16:00");
        items.add(item1);
        TeacherBatchPreCheckRequest request = new TeacherBatchPreCheckRequest();
        request.setTeacherName("王老师");
        request.setItems(items);

        List<ConflictTargetDetailDTO> result = scheduleService.getConflictTargetDetailsForTeacherBatch(request);

        ConflictTargetDetailDTO pairTarget = null;
        for (ConflictTargetDetailDTO t : result) {
            if (t.getSourceType() == ConflictDetailDTO.SourceType.PENDING_ITEM) {
                pairTarget = t;
                break;
            }
        }
        assertNotNull(pairTarget);
        assertEquals("数学", pairTarget.getCourseName());
        assertEquals("王老师", pairTarget.getTeacherName());
        assertEquals("A101", pairTarget.getClassroom());
        assertEquals("周五 14:00-16:00", pairTarget.getTimeSlot());
        assertNotNull(pairTarget.getRelatedCourseNames());
        assertEquals(2, pairTarget.getRelatedCourseNames().size());
        assertEquals("数学", pairTarget.getRelatedCourseNames().get(0));
        assertEquals("物理", pairTarget.getRelatedCourseNames().get(1));
    }

    @Test
    void testGetConflictTargetDetailsForTeacherBatch_PendingPairWithExisting() {
        scheduleService.addSchedule(createRequest("已有数学课", "张老师", "A101", "周一 08:00-10:00"));

        List<TeacherBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        TeacherBatchPreCheckItemRequest item0 = new TeacherBatchPreCheckItemRequest();
        item0.setCourseName("数学");
        item0.setClassroom("B202");
        item0.setTimeSlot("周一 08:00-10:00");
        items.add(item0);
        TeacherBatchPreCheckItemRequest item1 = new TeacherBatchPreCheckItemRequest();
        item1.setCourseName("物理");
        item1.setClassroom("B202");
        item1.setTimeSlot("周一 08:00-10:00");
        items.add(item1);
        TeacherBatchPreCheckRequest request = new TeacherBatchPreCheckRequest();
        request.setTeacherName("张老师");
        request.setItems(items);

        List<ConflictTargetDetailDTO> result = scheduleService.getConflictTargetDetailsForTeacherBatch(request);

        assertEquals(2, result.size());
        ConflictTargetDetailDTO existingTarget = result.get(0);
        assertEquals(ConflictDetailDTO.SourceType.EXISTING_COURSE, existingTarget.getSourceType());
        assertEquals("已有数学课", existingTarget.getCourseName());
        assertEquals(1, existingTarget.getConflictCount());
        assertEquals(ConflictDetailDTO.ConflictType.TEACHER, existingTarget.getConflictTypes().get(0));
        assertNotNull(existingTarget.getRelatedPendingIndexes());
        assertEquals(2, existingTarget.getRelatedPendingIndexes().size());
        assertNotNull(existingTarget.getRelatedCourseNames());
        assertEquals(2, existingTarget.getRelatedCourseNames().size());
        assertTrue(existingTarget.getRelatedCourseNames().contains("数学"));
        assertTrue(existingTarget.getRelatedCourseNames().contains("物理"));

        ConflictTargetDetailDTO pendingTarget = result.get(1);
        assertEquals(ConflictDetailDTO.SourceType.PENDING_ITEM, pendingTarget.getSourceType());
        assertEquals("数学", pendingTarget.getCourseName());
        assertNotNull(pendingTarget.getRelatedPendingIndexes());
        assertEquals(2, pendingTarget.getRelatedPendingIndexes().size());
        assertEquals(0, pendingTarget.getRelatedPendingIndexes().get(0));
        assertEquals(1, pendingTarget.getRelatedPendingIndexes().get(1));
        assertEquals(2, pendingTarget.getConflictCount());
        assertTrue(pendingTarget.getConflictTypes().contains(ConflictDetailDTO.ConflictType.TEACHER));
        assertTrue(pendingTarget.getConflictTypes().contains(ConflictDetailDTO.ConflictType.CLASSROOM));
    }

    @Test
    void testGetConflictTargetDetailsForTeacherBatch_NoConflict_ReturnsEmpty() {
        List<TeacherBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        TeacherBatchPreCheckItemRequest item0 = new TeacherBatchPreCheckItemRequest();
        item0.setCourseName("数学");
        item0.setClassroom("A101");
        item0.setTimeSlot("周一 08:00-10:00");
        items.add(item0);
        TeacherBatchPreCheckItemRequest item1 = new TeacherBatchPreCheckItemRequest();
        item1.setCourseName("物理");
        item1.setClassroom("B202");
        item1.setTimeSlot("周二 14:00-16:00");
        items.add(item1);
        TeacherBatchPreCheckRequest request = new TeacherBatchPreCheckRequest();
        request.setTeacherName("张老师");
        request.setItems(items);

        List<ConflictTargetDetailDTO> result = scheduleService.getConflictTargetDetailsForTeacherBatch(request);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testGetConflictTargetDetailsForClassroomBatch_PendingPairMutualConflict_DeduplicatedToOne() {
        List<ClassroomBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        ClassroomBatchPreCheckItemRequest item0 = new ClassroomBatchPreCheckItemRequest();
        item0.setCourseName("数学");
        item0.setTeacherName("张老师");
        item0.setTimeSlot("周一 08:00-10:00");
        items.add(item0);
        ClassroomBatchPreCheckItemRequest item1 = new ClassroomBatchPreCheckItemRequest();
        item1.setCourseName("物理");
        item1.setTeacherName("张老师");
        item1.setTimeSlot("周一 08:00-10:00");
        items.add(item1);
        ClassroomBatchPreCheckRequest request = new ClassroomBatchPreCheckRequest();
        request.setClassroom("A101");
        request.setItems(items);

        List<ConflictTargetDetailDTO> result = scheduleService.getConflictTargetDetailsForClassroomBatch(request);

        assertEquals(1, result.size());
        ConflictTargetDetailDTO target = result.get(0);
        assertEquals(ConflictDetailDTO.SourceType.PENDING_ITEM, target.getSourceType());
        assertEquals("数学", target.getCourseName());
        assertEquals("张老师", target.getTeacherName());
        assertEquals("A101", target.getClassroom());
        assertEquals("周一 08:00-10:00", target.getTimeSlot());
        assertNotNull(target.getRelatedPendingIndexes());
        assertEquals(2, target.getRelatedPendingIndexes().size());
        assertEquals(0, target.getRelatedPendingIndexes().get(0));
        assertEquals(1, target.getRelatedPendingIndexes().get(1));
        assertNotNull(target.getRelatedCourseNames());
        assertEquals(2, target.getRelatedCourseNames().size());
        assertEquals("数学", target.getRelatedCourseNames().get(0));
        assertEquals("物理", target.getRelatedCourseNames().get(1));
        assertEquals(2, target.getConflictCount());
        assertTrue(target.getConflictTypes().contains(ConflictDetailDTO.ConflictType.TEACHER));
        assertTrue(target.getConflictTypes().contains(ConflictDetailDTO.ConflictType.CLASSROOM));
    }

    @Test
    void testGetConflictTargetDetailsForClassroomBatch_ThreeItemsTwoPairs() {
        List<ClassroomBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        ClassroomBatchPreCheckItemRequest item0 = new ClassroomBatchPreCheckItemRequest();
        item0.setCourseName("数学");
        item0.setTeacherName("张老师");
        item0.setTimeSlot("周一 08:00-10:00");
        items.add(item0);
        ClassroomBatchPreCheckItemRequest item1 = new ClassroomBatchPreCheckItemRequest();
        item1.setCourseName("物理");
        item1.setTeacherName("张老师");
        item1.setTimeSlot("周一 08:00-10:00");
        items.add(item1);
        ClassroomBatchPreCheckItemRequest item2 = new ClassroomBatchPreCheckItemRequest();
        item2.setCourseName("化学");
        item2.setTeacherName("李老师");
        item2.setTimeSlot("周二 14:00-16:00");
        items.add(item2);
        ClassroomBatchPreCheckRequest request = new ClassroomBatchPreCheckRequest();
        request.setClassroom("A101");
        request.setItems(items);

        List<ConflictTargetDetailDTO> result = scheduleService.getConflictTargetDetailsForClassroomBatch(request);

        assertEquals(1, result.size());
        ConflictTargetDetailDTO target = result.get(0);
        assertEquals(ConflictDetailDTO.SourceType.PENDING_ITEM, target.getSourceType());
        assertEquals(0, target.getRelatedPendingIndexes().get(0));
        assertEquals(1, target.getRelatedPendingIndexes().get(1));
    }

    @Test
    void testGetConflictTargetDetails_NoConflict_ReturnsEmpty() {
        CourseScheduleConflictPreCheckRequest request = createPreCheckRequest(
                "数学", "张老师", "A101", "周一 08:00-10:00");

        List<ConflictTargetDetailDTO> result = scheduleService.getConflictTargetDetails(request);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testGetConflictTargetDetails_ExistingCourseConflict() {
        scheduleService.addSchedule(createRequest("已有数学", "张老师", "A101", "周一 08:00-10:00"));

        CourseScheduleConflictPreCheckRequest request = createPreCheckRequest(
                "新排物理", "张老师", "B202", "周一 08:00-10:00");

        List<ConflictTargetDetailDTO> result = scheduleService.getConflictTargetDetails(request);

        assertEquals(1, result.size());
        ConflictTargetDetailDTO target = result.get(0);
        assertEquals(ConflictDetailDTO.SourceType.EXISTING_COURSE, target.getSourceType());
        assertEquals("已有数学", target.getCourseName());
        assertEquals(1, target.getConflictCount());
        assertEquals(ConflictDetailDTO.ConflictType.TEACHER, target.getConflictTypes().get(0));
        assertNotNull(target.getRelatedPendingIndexes());
        assertTrue(target.getRelatedPendingIndexes().isEmpty());
    }

    @Test
    void testGetConflictSeveritySummary_Single_MultipleWarningTargets_ConflictTypeCountSummed() {
        scheduleService.addSchedule(createRequest("课1", "张老师", "A101", "周一 08:00-09:00"));
        scheduleService.addSchedule(createRequest("课2", "张老师", "B202", "周一 09:00-10:00"));
        scheduleService.addSchedule(createRequest("课3", "张老师", "C303", "周一 10:00-11:00"));

        CourseScheduleConflictPreCheckRequest request = createPreCheckRequest(
                "新课", "张老师", "D404", "周一 08:00-11:00");

        List<ConflictSeveritySummaryDTO> result = scheduleService.getConflictSeveritySummary(request);

        assertEquals(1, result.size());
        assertEquals(ConflictSeveritySummaryDTO.Severity.WARNING, result.get(0).getSeverity());
        assertEquals(3, result.get(0).getTargetCount());
        assertEquals(3, result.get(0).getConflictTypeCount());
        assertEquals(3, result.get(0).getExistingCourseTargetCount());
        assertEquals(0, result.get(0).getPendingItemTargetCount());
    }

    @Test
    void testGetConflictSeveritySummary_Single_MultipleBlockerTargets_ConflictTypeCountSummed() {
        scheduleService.addSchedule(createRequest("课1", "张老师", "A101", "周一 08:00-09:00"));
        scheduleService.addSchedule(createRequest("课2", "张老师", "A101", "周一 09:00-10:00"));

        CourseScheduleConflictPreCheckRequest request = createPreCheckRequest(
                "新课", "张老师", "A101", "周一 08:00-10:00");

        List<ConflictSeveritySummaryDTO> result = scheduleService.getConflictSeveritySummary(request);

        assertEquals(1, result.size());
        assertEquals(ConflictSeveritySummaryDTO.Severity.BLOCKER, result.get(0).getSeverity());
        assertEquals(2, result.get(0).getTargetCount());
        assertEquals(4, result.get(0).getConflictTypeCount());
        assertEquals(2, result.get(0).getExistingCourseTargetCount());
        assertEquals(0, result.get(0).getPendingItemTargetCount());
    }

    @Test
    void testGetConflictSeveritySummary_Single_MixedBlockerAndWarning_MultipleTargets() {
        scheduleService.addSchedule(createRequest("课1", "张老师", "A101", "周一 08:00-09:00"));
        scheduleService.addSchedule(createRequest("课2", "张老师", "B202", "周一 09:00-10:00"));
        scheduleService.addSchedule(createRequest("课3", "李老师", "A101", "周一 09:00-10:00"));

        CourseScheduleConflictPreCheckRequest request = createPreCheckRequest(
                "新课", "张老师", "A101", "周一 08:00-10:00");

        List<ConflictSeveritySummaryDTO> result = scheduleService.getConflictSeveritySummary(request);

        assertEquals(2, result.size());

        ConflictSeveritySummaryDTO blocker = result.get(0);
        assertEquals(ConflictSeveritySummaryDTO.Severity.BLOCKER, blocker.getSeverity());
        assertEquals(1, blocker.getTargetCount());
        assertEquals(2, blocker.getConflictTypeCount());

        ConflictSeveritySummaryDTO warning = result.get(1);
        assertEquals(ConflictSeveritySummaryDTO.Severity.WARNING, warning.getSeverity());
        assertEquals(2, warning.getTargetCount());
        assertEquals(2, warning.getConflictTypeCount());
    }

    @Test
    void testGetConflictSeveritySummary_TeacherBatch_MultipleWarningTargets() {
        scheduleService.addSchedule(createRequest("课1", "张老师", "A101", "周一 08:00-09:00"));
        scheduleService.addSchedule(createRequest("课2", "张老师", "B202", "周一 09:00-10:00"));
        scheduleService.addSchedule(createRequest("课3", "张老师", "C303", "周一 10:00-11:00"));

        List<TeacherBatchPreCheckItemRequest> items = new ArrayList<>();
        items.add(createBatchItem("新排课1", "D404", "周一 08:00-11:00"));

        TeacherBatchPreCheckRequest request = createBatchRequest("张老师", items);

        List<ConflictSeveritySummaryDTO> result = scheduleService.getConflictSeveritySummaryForTeacherBatch(request);

        assertEquals(1, result.size());
        assertEquals(ConflictSeveritySummaryDTO.Severity.WARNING, result.get(0).getSeverity());
        assertEquals(3, result.get(0).getTargetCount());
        assertEquals(3, result.get(0).getConflictTypeCount());
        assertEquals(3, result.get(0).getExistingCourseTargetCount());
        assertEquals(0, result.get(0).getPendingItemTargetCount());
    }

    @Test
    void testGetConflictSeveritySummary_TeacherBatch_MultipleBlockerTargets() {
        scheduleService.addSchedule(createRequest("课1", "张老师", "A101", "周一 08:00-09:00"));
        scheduleService.addSchedule(createRequest("课2", "张老师", "A101", "周一 09:00-10:00"));

        List<TeacherBatchPreCheckItemRequest> items = new ArrayList<>();
        items.add(createBatchItem("新排课1", "A101", "周一 08:00-10:00"));

        TeacherBatchPreCheckRequest request = createBatchRequest("张老师", items);

        List<ConflictSeveritySummaryDTO> result = scheduleService.getConflictSeveritySummaryForTeacherBatch(request);

        assertEquals(1, result.size());
        assertEquals(ConflictSeveritySummaryDTO.Severity.BLOCKER, result.get(0).getSeverity());
        assertEquals(2, result.get(0).getTargetCount());
        assertEquals(4, result.get(0).getConflictTypeCount());
        assertEquals(2, result.get(0).getExistingCourseTargetCount());
        assertEquals(0, result.get(0).getPendingItemTargetCount());
    }

    @Test
    void testGetConflictSeveritySummary_ClassroomBatch_MultipleWarningTargets() {
        scheduleService.addSchedule(createRequest("课1", "张老师", "A101", "周一 08:00-10:00"));
        scheduleService.addSchedule(createRequest("课2", "李老师", "A101", "周二 08:00-10:00"));
        scheduleService.addSchedule(createRequest("课3", "王老师", "A101", "周三 08:00-10:00"));

        List<ClassroomBatchPreCheckItemRequest> items = new ArrayList<>();
        items.add(createClassroomBatchItem("新排课1", "赵老师", "周一 08:00-10:00"));

        ClassroomBatchPreCheckRequest request = createClassroomBatchRequest("A101", items);

        List<ConflictSeveritySummaryDTO> result = scheduleService.getConflictSeveritySummaryForClassroomBatch(request);

        assertEquals(1, result.size());
        assertEquals(ConflictSeveritySummaryDTO.Severity.WARNING, result.get(0).getSeverity());
        assertEquals(1, result.get(0).getTargetCount());
        assertEquals(1, result.get(0).getConflictTypeCount());
    }

    @Test
    void testGetConflictSeveritySummary_ClassroomBatch_MultipleBlockerTargets() {
        scheduleService.addSchedule(createRequest("课1", "张老师", "A101", "周一 08:00-09:00"));
        scheduleService.addSchedule(createRequest("课2", "张老师", "A101", "周一 09:00-10:00"));

        List<ClassroomBatchPreCheckItemRequest> items = new ArrayList<>();
        items.add(createClassroomBatchItem("新排课1", "张老师", "周一 08:00-10:00"));

        ClassroomBatchPreCheckRequest request = createClassroomBatchRequest("A101", items);

        List<ConflictSeveritySummaryDTO> result = scheduleService.getConflictSeveritySummaryForClassroomBatch(request);

        assertEquals(1, result.size());
        assertEquals(ConflictSeveritySummaryDTO.Severity.BLOCKER, result.get(0).getSeverity());
        assertEquals(2, result.get(0).getTargetCount());
        assertEquals(4, result.get(0).getConflictTypeCount());
        assertEquals(2, result.get(0).getExistingCourseTargetCount());
        assertEquals(0, result.get(0).getPendingItemTargetCount());
    }

    @Test
    void testGetConflictSeveritySummary_Single_IncludesRequestCourseName() {
        scheduleService.addSchedule(createRequest("已有数学", "张老师", "A101", "周一 08:00-10:00"));

        CourseScheduleConflictPreCheckRequest request = createPreCheckRequest(
                "新排物理", "张老师", "B202", "周一 08:00-10:00");

        List<ConflictSeveritySummaryDTO> result = scheduleService.getConflictSeveritySummary(request);

        assertEquals(1, result.size());
        assertEquals(ConflictSeveritySummaryDTO.Severity.WARNING, result.get(0).getSeverity());
        assertTrue(result.get(0).getCourseNames().contains("已有数学"));
        assertTrue(result.get(0).getCourseNames().contains("新排物理"));
    }

    @Test
    void testGetConflictSeveritySummary_Single_BlockerIncludesRequestCourseName() {
        scheduleService.addSchedule(createRequest("已有数学", "张老师", "A101", "周一 08:00-10:00"));

        CourseScheduleConflictPreCheckRequest request = createPreCheckRequest(
                "新排物理", "张老师", "A101", "周一 08:00-10:00");

        List<ConflictSeveritySummaryDTO> result = scheduleService.getConflictSeveritySummary(request);

        assertEquals(1, result.size());
        assertEquals(ConflictSeveritySummaryDTO.Severity.BLOCKER, result.get(0).getSeverity());
        assertTrue(result.get(0).getCourseNames().contains("已有数学"));
        assertTrue(result.get(0).getCourseNames().contains("新排物理"));
    }

    @Test
    void testGetConflictSeveritySummary_Single_MixedGroupsBothIncludeRequestCourseName() {
        scheduleService.addSchedule(createRequest("课1", "张老师", "A101", "周一 08:00-09:00"));
        scheduleService.addSchedule(createRequest("课2", "李老师", "A101", "周一 09:00-10:00"));

        CourseScheduleConflictPreCheckRequest request = createPreCheckRequest(
                "新排课", "张老师", "A101", "周一 08:00-10:00");

        List<ConflictSeveritySummaryDTO> result = scheduleService.getConflictSeveritySummary(request);

        assertEquals(2, result.size());

        ConflictSeveritySummaryDTO blocker = result.get(0);
        assertTrue(blocker.getCourseNames().contains("新排课"));

        ConflictSeveritySummaryDTO warning = result.get(1);
        assertTrue(warning.getCourseNames().contains("新排课"));
    }

    @Test
    void testGetPendingConflictPairsForTeacherBatch_NoPendingConflicts_ReturnsEmpty() {
        List<TeacherBatchPreCheckItemRequest> items = new ArrayList<>();
        items.add(createBatchItem("数学", "A101", "周一 08:00-10:00"));
        items.add(createBatchItem("物理", "B202", "周一 10:00-12:00"));
        items.add(createBatchItem("化学", "C303", "周二 08:00-10:00"));

        TeacherBatchPreCheckRequest request = createBatchRequest("张老师", items);
        List<PendingConflictPairDTO> result = scheduleService.getPendingConflictPairsForTeacherBatch(request);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testGetPendingConflictPairsForTeacherBatch_ConflictOnlyWithExisting_ReturnsEmpty() {
        scheduleService.addSchedule(createRequest("已有课程", "张老师", "A101", "周一 08:00-10:00"));

        List<TeacherBatchPreCheckItemRequest> items = new ArrayList<>();
        items.add(createBatchItem("数学", "B202", "周一 08:00-10:00"));
        items.add(createBatchItem("物理", "C303", "周二 08:00-10:00"));

        TeacherBatchPreCheckRequest request = createBatchRequest("张老师", items);
        List<PendingConflictPairDTO> result = scheduleService.getPendingConflictPairsForTeacherBatch(request);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testGetPendingConflictPairsForTeacherBatch_SinglePair_TeacherAndClassroomConflict() {
        List<TeacherBatchPreCheckItemRequest> items = new ArrayList<>();
        items.add(createBatchItem("数学", "A101", "周一 08:00-10:00"));
        items.add(createBatchItem("物理", "A101", "周一 08:00-10:00"));

        TeacherBatchPreCheckRequest request = createBatchRequest("张老师", items);
        List<PendingConflictPairDTO> result = scheduleService.getPendingConflictPairsForTeacherBatch(request);

        assertNotNull(result);
        assertEquals(1, result.size());

        PendingConflictPairDTO pair = result.get(0);
        assertEquals(0, pair.getLeft().getIndex());
        assertEquals(1, pair.getRight().getIndex());

        PendingConflictPairItemDTO left = pair.getLeft();
        assertEquals("数学", left.getCourseName());
        assertEquals("张老师", left.getTeacherName());
        assertEquals("A101", left.getClassroom());
        assertEquals("周一 08:00-10:00", left.getTimeSlot());

        PendingConflictPairItemDTO right = pair.getRight();
        assertEquals("物理", right.getCourseName());
        assertEquals("张老师", right.getTeacherName());
        assertEquals("A101", right.getClassroom());
        assertEquals("周一 08:00-10:00", right.getTimeSlot());

        assertEquals(2, pair.getConflictCount());
        assertEquals(2, pair.getConflictTypes().size());
        assertEquals(ConflictDetailDTO.ConflictType.TEACHER, pair.getConflictTypes().get(0));
        assertEquals(ConflictDetailDTO.ConflictType.CLASSROOM, pair.getConflictTypes().get(1));
    }

    @Test
    void testGetPendingConflictPairsForTeacherBatch_SinglePair_OnlyTeacherConflict() {
        List<TeacherBatchPreCheckItemRequest> items = new ArrayList<>();
        items.add(createBatchItem("数学", "A101", "周一 08:00-10:00"));
        items.add(createBatchItem("物理", "B202", "周一 08:00-10:00"));

        TeacherBatchPreCheckRequest request = createBatchRequest("张老师", items);
        List<PendingConflictPairDTO> result = scheduleService.getPendingConflictPairsForTeacherBatch(request);

        assertNotNull(result);
        assertEquals(1, result.size());

        PendingConflictPairDTO pair = result.get(0);
        assertEquals(0, pair.getLeft().getIndex());
        assertEquals(1, pair.getRight().getIndex());
        assertEquals(1, pair.getConflictCount());
        assertEquals(1, pair.getConflictTypes().size());
        assertEquals(ConflictDetailDTO.ConflictType.TEACHER, pair.getConflictTypes().get(0));
    }

    @Test
    void testGetPendingConflictPairsForTeacherBatch_MultiplePairs_SortedByIndex() {
        List<TeacherBatchPreCheckItemRequest> items = new ArrayList<>();
        items.add(createBatchItem("课0", "A101", "周一 08:00-10:00"));
        items.add(createBatchItem("课1", "A101", "周二 08:00-10:00"));
        items.add(createBatchItem("课2", "A101", "周一 08:00-10:00"));
        items.add(createBatchItem("课3", "A101", "周二 08:00-10:00"));

        TeacherBatchPreCheckRequest request = createBatchRequest("张老师", items);
        List<PendingConflictPairDTO> result = scheduleService.getPendingConflictPairsForTeacherBatch(request);

        assertNotNull(result);
        assertEquals(2, result.size());

        assertEquals(0, result.get(0).getLeft().getIndex());
        assertEquals(2, result.get(0).getRight().getIndex());

        assertEquals(1, result.get(1).getLeft().getIndex());
        assertEquals(3, result.get(1).getRight().getIndex());
    }

    @Test
    void testGetPendingConflictPairsForTeacherBatch_ABDeduplication_OnlySmallerIndexFirst() {
        List<TeacherBatchPreCheckItemRequest> items = new ArrayList<>();
        items.add(createBatchItem("数学", "A101", "周一 08:00-10:00"));
        items.add(createBatchItem("物理", "A101", "周一 08:00-10:00"));

        TeacherBatchPreCheckRequest request = createBatchRequest("张老师", items);
        List<PendingConflictPairDTO> result = scheduleService.getPendingConflictPairsForTeacherBatch(request);

        assertEquals(1, result.size());
        PendingConflictPairDTO pair = result.get(0);
        assertTrue(pair.getLeft().getIndex() < pair.getRight().getIndex());
        assertEquals(0, pair.getLeft().getIndex());
        assertEquals(1, pair.getRight().getIndex());
    }

    @Test
    void testGetPendingConflictPairsForTeacherBatch_EmptyList_ReturnsEmpty() {
        List<TeacherBatchPreCheckItemRequest> items = new ArrayList<>();
        TeacherBatchPreCheckRequest request = createBatchRequest("张老师", items);

        List<PendingConflictPairDTO> result = scheduleService.getPendingConflictPairsForTeacherBatch(request);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testGetPendingConflictPairsForTeacherBatch_ThreeItemsAllConflict_CorrectPairs() {
        List<TeacherBatchPreCheckItemRequest> items = new ArrayList<>();
        items.add(createBatchItem("课0", "A101", "周一 08:00-10:00"));
        items.add(createBatchItem("课1", "A101", "周一 08:00-10:00"));
        items.add(createBatchItem("课2", "A101", "周一 08:00-10:00"));

        TeacherBatchPreCheckRequest request = createBatchRequest("张老师", items);
        List<PendingConflictPairDTO> result = scheduleService.getPendingConflictPairsForTeacherBatch(request);

        assertNotNull(result);
        assertEquals(3, result.size());

        assertEquals(0, result.get(0).getLeft().getIndex());
        assertEquals(1, result.get(0).getRight().getIndex());

        assertEquals(0, result.get(1).getLeft().getIndex());
        assertEquals(2, result.get(1).getRight().getIndex());

        assertEquals(1, result.get(2).getLeft().getIndex());
        assertEquals(2, result.get(2).getRight().getIndex());
    }

    @Test
    void testGetPendingConflictPairsForClassroomBatch_NoPendingConflicts_ReturnsEmpty() {
        List<ClassroomBatchPreCheckItemRequest> items = new ArrayList<>();
        items.add(createClassroomBatchItem("数学", "张老师", "周一 08:00-10:00"));
        items.add(createClassroomBatchItem("物理", "李老师", "周一 10:00-12:00"));
        items.add(createClassroomBatchItem("化学", "王老师", "周二 08:00-10:00"));

        ClassroomBatchPreCheckRequest request = createClassroomBatchRequest("A101", items);
        List<PendingConflictPairDTO> result = scheduleService.getPendingConflictPairsForClassroomBatch(request);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testGetPendingConflictPairsForClassroomBatch_SinglePair_ClassroomAndTeacherConflict() {
        List<ClassroomBatchPreCheckItemRequest> items = new ArrayList<>();
        items.add(createClassroomBatchItem("数学", "张老师", "周一 08:00-10:00"));
        items.add(createClassroomBatchItem("物理", "张老师", "周一 08:00-10:00"));

        ClassroomBatchPreCheckRequest request = createClassroomBatchRequest("A101", items);
        List<PendingConflictPairDTO> result = scheduleService.getPendingConflictPairsForClassroomBatch(request);

        assertNotNull(result);
        assertEquals(1, result.size());

        PendingConflictPairDTO pair = result.get(0);
        assertEquals(0, pair.getLeft().getIndex());
        assertEquals(1, pair.getRight().getIndex());

        PendingConflictPairItemDTO left = pair.getLeft();
        assertEquals("数学", left.getCourseName());
        assertEquals("张老师", left.getTeacherName());
        assertEquals("A101", left.getClassroom());
        assertEquals("周一 08:00-10:00", left.getTimeSlot());

        PendingConflictPairItemDTO right = pair.getRight();
        assertEquals("物理", right.getCourseName());
        assertEquals("张老师", right.getTeacherName());
        assertEquals("A101", right.getClassroom());
        assertEquals("周一 08:00-10:00", right.getTimeSlot());

        assertEquals(2, pair.getConflictCount());
        assertEquals(2, pair.getConflictTypes().size());
        assertEquals(ConflictDetailDTO.ConflictType.TEACHER, pair.getConflictTypes().get(0));
        assertEquals(ConflictDetailDTO.ConflictType.CLASSROOM, pair.getConflictTypes().get(1));
    }

    @Test
    void testGetPendingConflictPairsForClassroomBatch_SinglePair_OnlyClassroomConflict() {
        List<ClassroomBatchPreCheckItemRequest> items = new ArrayList<>();
        items.add(createClassroomBatchItem("数学", "张老师", "周一 08:00-10:00"));
        items.add(createClassroomBatchItem("物理", "李老师", "周一 08:00-10:00"));

        ClassroomBatchPreCheckRequest request = createClassroomBatchRequest("A101", items);
        List<PendingConflictPairDTO> result = scheduleService.getPendingConflictPairsForClassroomBatch(request);

        assertNotNull(result);
        assertEquals(1, result.size());

        PendingConflictPairDTO pair = result.get(0);
        assertEquals(0, pair.getLeft().getIndex());
        assertEquals(1, pair.getRight().getIndex());
        assertEquals(1, pair.getConflictCount());
        assertEquals(1, pair.getConflictTypes().size());
        assertEquals(ConflictDetailDTO.ConflictType.CLASSROOM, pair.getConflictTypes().get(0));
    }

    @Test
    void testGetPendingConflictPairsForClassroomBatch_MultiplePairs_SortedByIndex() {
        List<ClassroomBatchPreCheckItemRequest> items = new ArrayList<>();
        items.add(createClassroomBatchItem("课0", "张老师", "周一 08:00-10:00"));
        items.add(createClassroomBatchItem("课1", "李老师", "周二 08:00-10:00"));
        items.add(createClassroomBatchItem("课2", "王老师", "周一 08:00-10:00"));
        items.add(createClassroomBatchItem("课3", "赵老师", "周二 08:00-10:00"));

        ClassroomBatchPreCheckRequest request = createClassroomBatchRequest("A101", items);
        List<PendingConflictPairDTO> result = scheduleService.getPendingConflictPairsForClassroomBatch(request);

        assertNotNull(result);
        assertEquals(2, result.size());

        assertEquals(0, result.get(0).getLeft().getIndex());
        assertEquals(2, result.get(0).getRight().getIndex());

        assertEquals(1, result.get(1).getLeft().getIndex());
        assertEquals(3, result.get(1).getRight().getIndex());
    }

    @Test
    void testGetPendingConflictPairsForClassroomBatch_ABDeduplication_OnlySmallerIndexFirst() {
        List<ClassroomBatchPreCheckItemRequest> items = new ArrayList<>();
        items.add(createClassroomBatchItem("数学", "张老师", "周一 08:00-10:00"));
        items.add(createClassroomBatchItem("物理", "李老师", "周一 08:00-10:00"));

        ClassroomBatchPreCheckRequest request = createClassroomBatchRequest("A101", items);
        List<PendingConflictPairDTO> result = scheduleService.getPendingConflictPairsForClassroomBatch(request);

        assertEquals(1, result.size());
        PendingConflictPairDTO pair = result.get(0);
        assertTrue(pair.getLeft().getIndex() < pair.getRight().getIndex());
        assertEquals(0, pair.getLeft().getIndex());
        assertEquals(1, pair.getRight().getIndex());
    }

    @Test
    void testGetPendingConflictPairsForClassroomBatch_MixedExternalAndInternal_OnlyInternalPairs() {
        scheduleService.addSchedule(createRequest("已有课程", "张老师", "A101", "周一 08:00-10:00"));

        List<ClassroomBatchPreCheckItemRequest> items = new ArrayList<>();
        items.add(createClassroomBatchItem("数学", "李老师", "周一 08:00-10:00"));
        items.add(createClassroomBatchItem("物理", "王老师", "周一 08:00-10:00"));
        items.add(createClassroomBatchItem("化学", "赵老师", "周二 08:00-10:00"));

        ClassroomBatchPreCheckRequest request = createClassroomBatchRequest("A101", items);
        List<PendingConflictPairDTO> result = scheduleService.getPendingConflictPairsForClassroomBatch(request);

        assertNotNull(result);
        assertEquals(1, result.size());

        PendingConflictPairDTO pair = result.get(0);
        assertEquals(0, pair.getLeft().getIndex());
        assertEquals(1, pair.getRight().getIndex());
        assertEquals(1, pair.getConflictCount());
        assertEquals(ConflictDetailDTO.ConflictType.CLASSROOM, pair.getConflictTypes().get(0));
    }

    @Test
    void testGetPendingConflictPairsForTeacherBatch_NullRequest_ThrowsException() {
        InvalidRequestParameterException exception = assertThrows(InvalidRequestParameterException.class, () ->
                scheduleService.getPendingConflictPairsForTeacherBatch(null));
        assertEquals("请求不能为空", exception.getMessage());
    }

    @Test
    void testGetPendingConflictPairsForClassroomBatch_NullRequest_ThrowsException() {
        InvalidRequestParameterException exception = assertThrows(InvalidRequestParameterException.class, () ->
                scheduleService.getPendingConflictPairsForClassroomBatch(null));
        assertEquals("请求不能为空", exception.getMessage());
    }

    @Test
    void testConflictRiskPreview_NoConflict_ReturnsLOW() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));

        ConflictRiskPreviewRequest request = new ConflictRiskPreviewRequest();
        request.setTeacherName("李老师");
        request.setClassroom("B202");
        request.setTimeSlot("周二 08:00-10:00");

        ConflictRiskPreviewResponse response = scheduleService.conflictRiskPreview(request);

        assertEquals(0, response.getTeacherConflictCount());
        assertEquals(0, response.getClassroomConflictCount());
        assertTrue(response.getConflictCourseNames().isEmpty());
        assertEquals(ConflictRiskPreviewResponse.RiskLevel.LOW, response.getRiskLevel());
    }

    @Test
    void testConflictRiskPreview_TeacherConflictOnly_ReturnsMEDIUM() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));

        ConflictRiskPreviewRequest request = new ConflictRiskPreviewRequest();
        request.setTeacherName("张老师");
        request.setClassroom("B202");
        request.setTimeSlot("周一 08:00-10:00");

        ConflictRiskPreviewResponse response = scheduleService.conflictRiskPreview(request);

        assertEquals(1, response.getTeacherConflictCount());
        assertEquals(0, response.getClassroomConflictCount());
        assertEquals(1, response.getConflictCourseNames().size());
        assertTrue(response.getConflictCourseNames().contains("数学"));
        assertEquals(ConflictRiskPreviewResponse.RiskLevel.MEDIUM, response.getRiskLevel());
    }

    @Test
    void testConflictRiskPreview_ClassroomConflictOnly_ReturnsMEDIUM() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));

        ConflictRiskPreviewRequest request = new ConflictRiskPreviewRequest();
        request.setTeacherName("李老师");
        request.setClassroom("A101");
        request.setTimeSlot("周一 08:00-10:00");

        ConflictRiskPreviewResponse response = scheduleService.conflictRiskPreview(request);

        assertEquals(0, response.getTeacherConflictCount());
        assertEquals(1, response.getClassroomConflictCount());
        assertEquals(1, response.getConflictCourseNames().size());
        assertTrue(response.getConflictCourseNames().contains("数学"));
        assertEquals(ConflictRiskPreviewResponse.RiskLevel.MEDIUM, response.getRiskLevel());
    }

    @Test
    void testConflictRiskPreview_BothTeacherAndClassroomConflict_ReturnsHIGH() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "B202", "周一 08:00-10:00"));
        scheduleService.addSchedule(createRequest("英语", "李老师", "A101", "周一 08:00-10:00"));

        ConflictRiskPreviewRequest request = new ConflictRiskPreviewRequest();
        request.setTeacherName("张老师");
        request.setClassroom("A101");
        request.setTimeSlot("周一 08:00-10:00");

        ConflictRiskPreviewResponse response = scheduleService.conflictRiskPreview(request);

        assertEquals(1, response.getTeacherConflictCount());
        assertEquals(1, response.getClassroomConflictCount());
        assertEquals(2, response.getConflictCourseNames().size());
        assertTrue(response.getConflictCourseNames().contains("数学"));
        assertTrue(response.getConflictCourseNames().contains("英语"));
        assertEquals(ConflictRiskPreviewResponse.RiskLevel.HIGH, response.getRiskLevel());
    }

    @Test
    void testConflictRiskPreview_NullRequest_ThrowsException() {
        InvalidRequestParameterException exception = assertThrows(InvalidRequestParameterException.class, () ->
                scheduleService.conflictRiskPreview(null));
        assertEquals("冲突风险预览请求不能为空", exception.getMessage());
    }

    @Test
    void testConflictRiskPreview_EmptyTeacherName_ThrowsException() {
        ConflictRiskPreviewRequest request = new ConflictRiskPreviewRequest();
        request.setTeacherName("");
        request.setClassroom("A101");
        request.setTimeSlot("周一 08:00-10:00");

        InvalidRequestParameterException exception = assertThrows(InvalidRequestParameterException.class, () ->
                scheduleService.conflictRiskPreview(request));
        assertEquals("老师名不能为空", exception.getMessage());
    }

    @Test
    void testConflictRiskPreview_EmptyClassroom_ThrowsException() {
        ConflictRiskPreviewRequest request = new ConflictRiskPreviewRequest();
        request.setTeacherName("张老师");
        request.setClassroom("");
        request.setTimeSlot("周一 08:00-10:00");

        InvalidRequestParameterException exception = assertThrows(InvalidRequestParameterException.class, () ->
                scheduleService.conflictRiskPreview(request));
        assertEquals("教室名不能为空", exception.getMessage());
    }

    @Test
    void testConflictRiskPreview_EmptyTimeSlot_ThrowsException() {
        ConflictRiskPreviewRequest request = new ConflictRiskPreviewRequest();
        request.setTeacherName("张老师");
        request.setClassroom("A101");
        request.setTimeSlot("");

        InvalidRequestParameterException exception = assertThrows(InvalidRequestParameterException.class, () ->
                scheduleService.conflictRiskPreview(request));
        assertEquals("时间段不能为空", exception.getMessage());
    }

    @Test
    void testConflictRiskPreview_DuplicateCourseNamesDeduplicated() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));

        ConflictRiskPreviewRequest request = new ConflictRiskPreviewRequest();
        request.setTeacherName("张老师");
        request.setClassroom("A101");
        request.setTimeSlot("周一 08:00-10:00");

        ConflictRiskPreviewResponse response = scheduleService.conflictRiskPreview(request);

        assertEquals(1, response.getTeacherConflictCount());
        assertEquals(1, response.getClassroomConflictCount());
        assertEquals(1, response.getConflictCourseNames().size());
        assertEquals("数学", response.getConflictCourseNames().get(0));
        assertEquals(ConflictRiskPreviewResponse.RiskLevel.HIGH, response.getRiskLevel());
    }

    @Test
    void testGetTeacherFreeDaySummary_NoCourses_AllDaysFree() {
        TeacherFreeDaySummaryResponse summary = scheduleService.getTeacherFreeDaySummary("张老师");

        assertEquals("张老师", summary.getTeacherName());
        assertTrue(summary.getBusyDays().isEmpty());
        assertEquals(7, summary.getFreeDayCount());
        assertEquals(Arrays.asList("周一", "周二", "周三", "周四", "周五", "周六", "周日"),
                summary.getFreeDays());
    }

    @Test
    void testGetTeacherFreeDaySummary_PartialDays_CorrectBusyAndFree() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));
        scheduleService.addSchedule(createRequest("物理", "张老师", "B202", "周三 08:00-10:00"));
        scheduleService.addSchedule(createRequest("化学", "李老师", "C303", "周二 08:00-10:00"));
        scheduleService.addSchedule(createRequest("英语", "张老师", "D404", "周五 14:00-16:00"));

        TeacherFreeDaySummaryResponse summary = scheduleService.getTeacherFreeDaySummary("张老师");

        assertEquals(Arrays.asList("周一", "周三", "周五"), summary.getBusyDays());
        assertEquals(Arrays.asList("周二", "周四", "周六", "周日"), summary.getFreeDays());
        assertEquals(4, summary.getFreeDayCount());
    }

    @Test
    void testGetTeacherFreeDaySummary_AllSevenDaysOccupied_NoFreeDays() {
        scheduleService.addSchedule(createRequest("周一", "张老师", "A101", "周一 08:00-10:00"));
        scheduleService.addSchedule(createRequest("周二", "张老师", "A101", "周二 08:00-10:00"));
        scheduleService.addSchedule(createRequest("周三", "张老师", "A101", "周三 08:00-10:00"));
        scheduleService.addSchedule(createRequest("周四", "张老师", "A101", "周四 08:00-10:00"));
        scheduleService.addSchedule(createRequest("周五", "张老师", "A101", "周五 08:00-10:00"));
        scheduleService.addSchedule(createRequest("周六", "张老师", "A101", "周六 08:00-10:00"));
        scheduleService.addSchedule(createRequest("周日", "张老师", "A101", "周日 08:00-10:00"));

        TeacherFreeDaySummaryResponse summary = scheduleService.getTeacherFreeDaySummary("张老师");

        assertEquals(7, summary.getBusyDays().size());
        assertTrue(summary.getFreeDays().isEmpty());
        assertEquals(0, summary.getFreeDayCount());
    }

    @Test
    void testGetTeacherFreeDaySummary_EmptyTeacherName_ThrowsException() {
        InvalidRequestParameterException ex = assertThrows(InvalidRequestParameterException.class, () ->
                scheduleService.getTeacherFreeDaySummary("")
        );
        assertEquals("老师名不能为空", ex.getMessage());
    }

    @Test
    void testGetTeacherFreeDaySummary_NullTeacherName_ThrowsException() {
        InvalidRequestParameterException ex = assertThrows(InvalidRequestParameterException.class, () ->
                scheduleService.getTeacherFreeDaySummary(null)
        );
        assertEquals("老师名不能为空", ex.getMessage());
    }

    @Test
    void testGetTeacherFreeDaySummary_WhitespaceTeacherName_ThrowsException() {
        InvalidRequestParameterException ex = assertThrows(InvalidRequestParameterException.class, () ->
                scheduleService.getTeacherFreeDaySummary("   ")
        );
        assertEquals("老师名不能为空", ex.getMessage());
    }

    @Test
    void testGetTeacherFreeDaySummary_InvalidTimeSlotCourses_NotCounted() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));

        CourseScheduleCreateRequest invalid1 = createRequest("物理", "张老师", "B202", "周八 08:00-10:00");
        CourseScheduleCreateRequest invalid2 = createRequest("化学", "张老师", "C303", null);

        try {
            scheduleService.addSchedule(invalid1);
        } catch (Exception ignored) {
        }
        try {
            scheduleService.addSchedule(invalid2);
        } catch (Exception ignored) {
        }

        scheduleService.addSchedule(createRequest("生物", "张老师", "D404", "周四 09:00-11:00"));

        TeacherFreeDaySummaryResponse summary = scheduleService.getTeacherFreeDaySummary("张老师");

        assertEquals(Arrays.asList("周一", "周四"), summary.getBusyDays());
        assertEquals(Arrays.asList("周二", "周三", "周五", "周六", "周日"), summary.getFreeDays());
        assertEquals(5, summary.getFreeDayCount());
    }

    @Test
    void testGetTeacherFreeDaySummary_TrimmedTeacherName_CorrectResult() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周二 08:00-10:00"));

        TeacherFreeDaySummaryResponse summary = scheduleService.getTeacherFreeDaySummary("  张老师  ");

        assertEquals("张老师", summary.getTeacherName());
        assertEquals(Arrays.asList("周二"), summary.getBusyDays());
        assertEquals(6, summary.getFreeDayCount());
    }

    @Test
    void testGetTeacherFreeDaySummary_SundayCourses_CountedCorrectly() {
        scheduleService.addSchedule(createRequest("周六课", "张老师", "A101", "周六 08:00-10:00"));
        scheduleService.addSchedule(createRequest("周日课", "张老师", "B202", "周日 10:00-12:00"));

        TeacherFreeDaySummaryResponse summary = scheduleService.getTeacherFreeDaySummary("张老师");

        assertEquals(Arrays.asList("周六", "周日"), summary.getBusyDays());
        assertEquals(5, summary.getFreeDayCount());
    }

    @Test
    void testGetTeacherFreeDaySummary_BusyDaysSorted_MondayToSunday() {
        scheduleService.addSchedule(createRequest("周日", "张老师", "A101", "周日 08:00-10:00"));
        scheduleService.addSchedule(createRequest("周三", "张老师", "B202", "周三 08:00-10:00"));
        scheduleService.addSchedule(createRequest("周一", "张老师", "C303", "周一 08:00-10:00"));

        TeacherFreeDaySummaryResponse summary = scheduleService.getTeacherFreeDaySummary("张老师");

        assertEquals(Arrays.asList("周一", "周三", "周日"), summary.getBusyDays());
    }

    @Test
    void testGetTeacherFreeDaySummary_MultipleCoursesSameDay_CountedOnce() {
        scheduleService.addSchedule(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"));
        scheduleService.addSchedule(createRequest("物理", "张老师", "B202", "周一 10:00-12:00"));
        scheduleService.addSchedule(createRequest("化学", "张老师", "C303", "周一 14:00-16:00"));

        TeacherFreeDaySummaryResponse summary = scheduleService.getTeacherFreeDaySummary("张老师");

        assertEquals(1, summary.getBusyDays().size());
        assertEquals(Arrays.asList("周一"), summary.getBusyDays());
        assertEquals(6, summary.getFreeDayCount());
    }
}
