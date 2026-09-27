package com.coursescheduler.service;

import com.coursescheduler.dto.RescheduleItemRequest;
import com.coursescheduler.dto.ReschedulePlanCreateRequest;
import com.coursescheduler.dto.ReschedulePlanResponse;
import com.coursescheduler.dto.ReschedulePreCheckResponse;
import com.coursescheduler.dto.RescheduleResultResponse;
import com.coursescheduler.exception.InvalidRequestParameterException;
import com.coursescheduler.exception.RescheduleConflictException;
import com.coursescheduler.exception.ReschedulePlanNotFoundException;
import com.coursescheduler.exception.ReschedulePlanProcessedException;
import com.coursescheduler.model.OperationType;
import com.coursescheduler.model.RescheduleIssueSource;
import com.coursescheduler.model.RescheduleIssueType;
import com.coursescheduler.model.ReschedulePlanStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class RescheduleServiceTest {

    private static final int LATCH_TIMEOUT_SECONDS = 10;

    private CourseScheduleService scheduleService;
    private AuditLogService auditLogService;
    private RescheduleService rescheduleService;

    @BeforeEach
    void setUp() {
        auditLogService = new AuditLogService(Clock.systemDefaultZone());
        scheduleService = new CourseScheduleService(auditLogService);
        rescheduleService = new RescheduleService(scheduleService, auditLogService, Clock.systemDefaultZone());
    }

    private void seed(String courseName, String teacher, String classroom, String timeSlot) {
        scheduleService.addSchedule(createSchedule(courseName, teacher, classroom, timeSlot));
    }

    private com.coursescheduler.dto.CourseScheduleCreateRequest createSchedule(
            String courseName, String teacher, String classroom, String timeSlot) {
        com.coursescheduler.dto.CourseScheduleCreateRequest request =
                new com.coursescheduler.dto.CourseScheduleCreateRequest();
        request.setCourseName(courseName);
        request.setTeacherName(teacher);
        request.setClassroom(classroom);
        request.setTimeSlot(timeSlot);
        return request;
    }

    private ReschedulePlanCreateRequest planRequest(String businessId, List<RescheduleItemRequest> items) {
        ReschedulePlanCreateRequest request = new ReschedulePlanCreateRequest();
        request.setBusinessId(businessId);
        request.setItems(items);
        return request;
    }

    private RescheduleItemRequest item(Long scheduleId, String classroom, String timeSlot) {
        return new RescheduleItemRequest(scheduleId, classroom, timeSlot);
    }

    private List<RescheduleItemRequest> items(RescheduleItemRequest... items) {
        return new ArrayList<>(List.of(items));
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

    // ---------- 基本提交与快照 ----------

    @Test
    void createPlan_savesOriginalSnapshotAndTargets() {
        seed("数学", "张老师", "A101", "周一 08:00-10:00");

        ReschedulePlanResponse plan = rescheduleService.createPlan(
                planRequest("BIZ-1", items(item(1L, "B202", "周三 10:00-12:00"))));

        assertEquals("BIZ-1", plan.getBusinessId());
        assertEquals(ReschedulePlanStatus.PENDING, plan.getStatus());
        assertEquals(1, plan.getItems().size());
        assertEquals("数学", plan.getItems().get(0).getCourseName());
        assertEquals("张老师", plan.getItems().get(0).getOriginalTeacherName());
        assertEquals("A101", plan.getItems().get(0).getOriginalClassroom());
        assertEquals("周一 08:00-10:00", plan.getItems().get(0).getOriginalTimeSlot());
        assertEquals("B202", plan.getItems().get(0).getTargetClassroom());
        assertEquals("周三 10:00-12:00", plan.getItems().get(0).getTargetTimeSlot());
        assertNotNull(plan.getPlanId());
    }

    @Test
    void createPlan_missingBusinessId_throws() {
        seed("数学", "张老师", "A101", "周一 08:00-10:00");
        assertThrows(InvalidRequestParameterException.class, () ->
                rescheduleService.createPlan(planRequest("  ", items(item(1L, "B202", "周三 10:00-12:00")))));
    }

    @Test
    void createPlan_emptyItems_throws() {
        assertThrows(InvalidRequestParameterException.class, () ->
                rescheduleService.createPlan(planRequest("BIZ-1", new ArrayList<>())));
    }

    @Test
    void createPlan_duplicateScheduleReferences_throws() {
        seed("数学", "张老师", "A101", "周一 08:00-10:00");
        assertThrows(InvalidRequestParameterException.class, () ->
                rescheduleService.createPlan(planRequest("BIZ-1", items(
                        item(1L, "B202", "周三 10:00-12:00"),
                        item(1L, "C303", "周四 10:00-12:00")))));
    }

    @Test
    void createPlan_referencesMissingSchedule_throws() {
        seed("数学", "张老师", "A101", "周一 08:00-10:00");
        assertThrows(InvalidRequestParameterException.class, () ->
                rescheduleService.createPlan(planRequest("BIZ-1", items(item(99L, "B202", "周三 10:00-12:00")))));
    }

    // ---------- 互换时段 ----------

    @Test
    void confirm_twoCoursesSwapTimeSlots_succeeds() {
        // 张老师 周一 A101，李老师 周一 B202，两门课互换时段（同时也互换教室）。
        seed("数学", "张老师", "A101", "周一 08:00-10:00");
        seed("物理", "李老师", "B202", "周一 10:00-12:00");

        ReschedulePlanResponse plan = rescheduleService.createPlan(planRequest("SWAP-1", items(
                item(1L, "B202", "周一 10:00-12:00"),
                item(2L, "A101", "周一 08:00-10:00"))));

        ReschedulePreCheckResponse preCheck = rescheduleService.preCheck(plan.getPlanId());
        assertTrue(preCheck.isValid());
        assertEquals(0, preCheck.getConflictCount());

        RescheduleResultResponse result = rescheduleService.confirm(plan.getPlanId());
        assertTrue(result.isApplied());
        assertEquals(ReschedulePlanStatus.CONFIRMED, result.getStatus());
        assertEquals(2, result.getChanges().size());

        List<com.coursescheduler.dto.CourseScheduleResponse> all = scheduleService.findSchedules(null);
        com.coursescheduler.dto.CourseScheduleResponse math =
                all.stream().filter(s -> s.getId() == 1L).findFirst().orElseThrow();
        com.coursescheduler.dto.CourseScheduleResponse physics =
                all.stream().filter(s -> s.getId() == 2L).findFirst().orElseThrow();
        assertEquals("B202", math.getClassroom());
        assertEquals("周一 10:00-12:00", math.getTimeSlot());
        assertEquals("A101", physics.getClassroom());
        assertEquals("周一 08:00-10:00", physics.getTimeSlot());
    }

    @Test
    void confirm_sameTeacherSwapTimeSlots_succeeds() {
        // 同一老师两门课互换时段，旧时段被腾出，不构成教师冲突。
        seed("数学", "张老师", "A101", "周一 08:00-10:00");
        seed("物理", "张老师", "B202", "周一 10:00-12:00");

        ReschedulePlanResponse plan = rescheduleService.createPlan(planRequest("SWAP-2", items(
                item(1L, "A101", "周一 10:00-12:00"),
                item(2L, "B202", "周一 08:00-10:00"))));

        assertTrue(rescheduleService.preCheck(plan.getPlanId()).isValid());
        assertTrue(rescheduleService.confirm(plan.getPlanId()).isApplied());
    }

    // ---------- 外部冲突 ----------

    @Test
    void preCheck_teacherConflictWithNonParticipatingCourse_returnsConflict() {
        seed("数学", "张老师", "A101", "周一 08:00-10:00");
        seed("物理", "李老师", "B202", "周一 10:00-12:00");
        // 张老师周三 10:00 已有课（课程 3，不参与调课）。
        seed("化学", "张老师", "C303", "周三 10:00-12:00");

        ReschedulePlanResponse plan = rescheduleService.createPlan(planRequest("C-1", items(
                item(1L, "A101", "周三 10:00-12:00"),
                item(2L, "B202", "周三 08:00-10:00"))));

        ReschedulePreCheckResponse preCheck = rescheduleService.preCheck(plan.getPlanId());
        assertFalse(preCheck.isValid());
        // 冲突主体为参与调课的课程 1，冲突对象为未参与调课的课程 3。
        assertTrue(preCheck.getIssues().stream().anyMatch(i ->
                i.getIssueType() == RescheduleIssueType.TEACHER
                        && i.getSource() == RescheduleIssueSource.EXTERNAL_COURSE
                        && i.getScheduleId() == 1L
                        && "化学".equals(i.getCourseName())));
    }

    @Test
    void preCheck_classroomConflictWithNonParticipatingCourse_returnsConflict() {
        seed("数学", "张老师", "A101", "周一 08:00-10:00");
        seed("物理", "李老师", "B202", "周三 10:00-12:00");

        // 数学搬到周三 10:00 的 B202，与未参与调课的物理冲突。
        ReschedulePlanResponse plan = rescheduleService.createPlan(
                planRequest("C-2", items(item(1L, "B202", "周三 10:00-12:00"))));

        ReschedulePreCheckResponse preCheck = rescheduleService.preCheck(plan.getPlanId());
        assertFalse(preCheck.isValid());
        assertEquals(1, preCheck.getConflictCount());
        assertEquals(RescheduleIssueType.CLASSROOM, preCheck.getIssues().get(0).getIssueType());
        assertEquals(RescheduleIssueSource.EXTERNAL_COURSE, preCheck.getIssues().get(0).getSource());
    }

    // ---------- 方案内部冲突 ----------

    @Test
    void preCheck_internalTeacherConflict_returnsConflict() {
        seed("数学", "张老师", "A101", "周一 08:00-10:00");
        seed("物理", "张老师", "B202", "周二 08:00-10:00");

        // 两门课都搬到周三同一时段，同老师冲突。
        ReschedulePlanResponse plan = rescheduleService.createPlan(planRequest("I-1", items(
                item(1L, "A101", "周三 08:00-10:00"),
                item(2L, "B202", "周三 08:00-10:00"))));

        ReschedulePreCheckResponse preCheck = rescheduleService.preCheck(plan.getPlanId());
        assertFalse(preCheck.isValid());
        assertTrue(preCheck.getIssues().stream().anyMatch(i ->
                i.getIssueType() == RescheduleIssueType.TEACHER
                        && i.getSource() == RescheduleIssueSource.INTERNAL_ITEM
                        && i.getOtherItemIndex() == 1));
    }

    @Test
    void preCheck_internalClassroomConflict_returnsConflict() {
        seed("数学", "张老师", "A101", "周一 08:00-10:00");
        seed("物理", "李老师", "B202", "周二 08:00-10:00");

        // 两门课都搬到同一教室同一时段，教室冲突。
        ReschedulePlanResponse plan = rescheduleService.createPlan(planRequest("I-2", items(
                item(1L, "C303", "周三 08:00-10:00"),
                item(2L, "C303", "周三 08:00-10:00"))));

        ReschedulePreCheckResponse preCheck = rescheduleService.preCheck(plan.getPlanId());
        assertFalse(preCheck.isValid());
        assertTrue(preCheck.getIssues().stream().anyMatch(i ->
                i.getIssueType() == RescheduleIssueType.CLASSROOM
                        && i.getSource() == RescheduleIssueSource.INTERNAL_ITEM));
    }

    // ---------- 快照过期（并发修改/删除）----------

    @Test
    void confirm_scheduleChangedByOtherAfterSubmission_wholePlanRejected() {
        seed("数学", "张老师", "A101", "周一 08:00-10:00");
        seed("物理", "李老师", "B202", "周一 10:00-12:00");

        ReschedulePlanResponse plan = rescheduleService.createPlan(planRequest("STALE-1", items(
                item(1L, "A101", "周三 08:00-10:00"),
                item(2L, "B202", "周四 08:00-10:00"))));

        // 别人在确认前修改了课程 1。
        com.coursescheduler.dto.CourseScheduleUpdateRequest update =
                new com.coursescheduler.dto.CourseScheduleUpdateRequest();
        update.setCourseName("数学-改");
        update.setTeacherName("张老师");
        update.setClassroom("A101");
        update.setTimeSlot("周五 08:00-10:00");
        scheduleService.updateSchedule(1L, update);

        RescheduleResultResponse result = rescheduleService.confirm(plan.getPlanId());
        assertFalse(result.isApplied());
        assertEquals(ReschedulePlanStatus.PENDING, result.getStatus());
        assertTrue(result.getIssues().stream().anyMatch(i ->
                i.getIssueType() == RescheduleIssueType.SCHEDULE_CHANGED
                        && i.getSource() == RescheduleIssueSource.STALE_PLAN));

        // 没有任何部分调课结果：课程 2 也未被改动。
        com.coursescheduler.dto.CourseScheduleResponse physics =
                scheduleService.findSchedules(null).stream()
                        .filter(s -> s.getId() == 2L).findFirst().orElseThrow();
        assertEquals("B202", physics.getClassroom());
        assertEquals("周一 10:00-12:00", physics.getTimeSlot());
    }

    @Test
    void confirm_scheduleDeletedAfterSubmission_wholePlanRejected() {
        seed("数学", "张老师", "A101", "周一 08:00-10:00");
        seed("物理", "李老师", "B202", "周一 10:00-12:00");

        ReschedulePlanResponse plan = rescheduleService.createPlan(planRequest("STALE-2", items(
                item(1L, "A101", "周三 08:00-10:00"),
                item(2L, "B202", "周四 08:00-10:00"))));

        scheduleService.deleteSchedule(1L);

        RescheduleResultResponse result = rescheduleService.confirm(plan.getPlanId());
        assertFalse(result.isApplied());
        assertTrue(result.getIssues().stream().anyMatch(i ->
                i.getIssueType() == RescheduleIssueType.SCHEDULE_DELETED
                        && i.getScheduleId() == 1L));
        // 课程 2 保持不变。
        assertEquals("周一 10:00-12:00",
                scheduleService.findSchedules(null).get(0).getTimeSlot());
    }

    @Test
    void confirm_targetResourceTakenByOtherAfterSubmission_wholePlanRejected() {
        seed("数学", "张老师", "A101", "周一 08:00-10:00");
        seed("物理", "李老师", "B202", "周一 10:00-12:00");

        ReschedulePlanResponse plan = rescheduleService.createPlan(
                planRequest("STALE-3", items(item(1L, "C303", "周三 08:00-10:00"))));
        assertTrue(rescheduleService.preCheck(plan.getPlanId()).isValid());

        // 别人在目标时段占用了 C303。
        seed("化学", "王老师", "C303", "周三 08:00-10:00");

        RescheduleResultResponse result = rescheduleService.confirm(plan.getPlanId());
        assertFalse(result.isApplied());
        assertEquals(ReschedulePlanStatus.PENDING, result.getStatus());
        assertTrue(result.getIssues().stream().anyMatch(i ->
                i.getIssueType() == RescheduleIssueType.CLASSROOM
                        && i.getSource() == RescheduleIssueSource.EXTERNAL_COURSE
                        && "化学".equals(i.getCourseName())));
        // 课程 1 保持原样。
        assertEquals("A101",
                scheduleService.findSchedules(null).stream()
                        .filter(s -> s.getId() == 1L).findFirst().orElseThrow().getClassroom());
    }

    @Test
    void confirm_failedThenExternalChangeReverted_confirmSucceeds() {
        seed("数学", "张老师", "A101", "周一 08:00-10:00");
        seed("物理", "李老师", "B202", "周一 10:00-12:00");

        ReschedulePlanResponse plan = rescheduleService.createPlan(
                planRequest("RETRY-1", items(item(1L, "C303", "周三 08:00-10:00"))));

        seed("化学", "王老师", "C303", "周三 08:00-10:00");
        assertFalse(rescheduleService.confirm(plan.getPlanId()).isApplied());
        assertEquals(ReschedulePlanStatus.PENDING, rescheduleService.getPlan(plan.getPlanId()).getStatus());

        // 占用解除后可重新确认。
        scheduleService.deleteSchedule(3L);
        RescheduleResultResponse result = rescheduleService.confirm(plan.getPlanId());
        assertTrue(result.isApplied());
        assertEquals(ReschedulePlanStatus.CONFIRMED, result.getStatus());
    }

    // ---------- 并发：最多一个成功 ----------

    @Test
    void confirm_twoPlansModifySameScheduleConcurrently_atMostOneSucceeds() throws Exception {
        seed("数学", "张老师", "A101", "周一 08:00-10:00");

        ReschedulePlanResponse plan1 = rescheduleService.createPlan(
                planRequest("CC-1", items(item(1L, "A101", "周三 08:00-10:00"))));
        ReschedulePlanResponse plan2 = rescheduleService.createPlan(
                planRequest("CC-2", items(item(1L, "A101", "周四 08:00-10:00"))));

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(2);

        // 两个任务分别确认两个方案。
        executor.submit(() -> {
            ready.countDown();
            try {
                start.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            if (rescheduleService.confirm(plan1.getPlanId()).isApplied()) {
                successCount.incrementAndGet();
            }
        });
        executor.submit(() -> {
            ready.countDown();
            try {
                start.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            if (rescheduleService.confirm(plan2.getPlanId()).isApplied()) {
                successCount.incrementAndGet();
            }
        });

        assertTrue(ready.await(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS));
        start.countDown();
        shutdownExecutorSafely(executor);

        assertEquals(1, successCount.get());

        // 恰好一个方案确认成功，另一个保持 PENDING 且记录快照过期冲突。
        ReschedulePlanResponse after1 = rescheduleService.getPlan(plan1.getPlanId());
        ReschedulePlanResponse after2 = rescheduleService.getPlan(plan2.getPlanId());
        long confirmed = List.of(after1, after2).stream()
                .filter(p -> p.getStatus() == ReschedulePlanStatus.CONFIRMED).count();
        long pending = List.of(after1, after2).stream()
                .filter(p -> p.getStatus() == ReschedulePlanStatus.PENDING).count();
        assertEquals(1, confirmed);
        assertEquals(1, pending);
        assertTrue(List.of(after1, after2).stream()
                .filter(p -> p.getStatus() == ReschedulePlanStatus.PENDING)
                .allMatch(p -> p.getIssues().stream()
                        .anyMatch(i -> i.getIssueType() == RescheduleIssueType.SCHEDULE_CHANGED)));

        // 课程最终的时段必然是两个目标之一，且只被调整一次。
        String finalTimeSlot = scheduleService.findSchedules(null).get(0).getTimeSlot();
        assertTrue(finalTimeSlot.equals("周三 08:00-10:00") || finalTimeSlot.equals("周四 08:00-10:00"),
                "最终时段应为获胜方案的目标时段，实际：" + finalTimeSlot);
    }

    // ---------- 幂等 ----------

    @Test
    void createPlan_sameBusinessIdAndSameContent_returnsFirstResult() {
        seed("数学", "张老师", "A101", "周一 08:00-10:00");

        ReschedulePlanResponse first = rescheduleService.createPlan(
                planRequest("IDEM-1", items(item(1L, "B202", "周三 10:00-12:00"))));
        ReschedulePlanResponse second = rescheduleService.createPlan(
                planRequest("IDEM-1", items(item(1L, "B202", "周三 10:00-12:00"))));

        assertEquals(first.getPlanId(), second.getPlanId());
        assertEquals(1, rescheduleService.listPlans(null).size());
    }

    @Test
    void createPlan_sameBusinessIdDifferentContent_throwsConflict() {
        seed("数学", "张老师", "A101", "周一 08:00-10:00");

        rescheduleService.createPlan(
                planRequest("IDEM-2", items(item(1L, "B202", "周三 10:00-12:00"))));
        assertThrows(RescheduleConflictException.class, () ->
                rescheduleService.createPlan(
                        planRequest("IDEM-2", items(item(1L, "C303", "周四 10:00-12:00")))));
    }

    @Test
    void confirm_confirmedPlan_isIdempotent() {
        seed("数学", "张老师", "A101", "周一 08:00-10:00");
        ReschedulePlanResponse plan = rescheduleService.createPlan(
                planRequest("IDEM-3", items(item(1L, "B202", "周三 10:00-12:00"))));

        RescheduleResultResponse first = rescheduleService.confirm("IDEM-3");
        RescheduleResultResponse second = rescheduleService.confirm(plan.getPlanId());

        assertTrue(first.isApplied());
        assertTrue(second.isApplied());
        assertEquals(ReschedulePlanStatus.CONFIRMED, second.getStatus());
        // 课程仍然只被调整一次。
        assertEquals("周三 10:00-12:00",
                scheduleService.findSchedules(null).get(0).getTimeSlot());
    }

    // ---------- 终态处理 ----------

    @Test
    void reject_thenConfirm_throws() {
        seed("数学", "张老师", "A101", "周一 08:00-10:00");
        rescheduleService.createPlan(planRequest("FIN-1", items(item(1L, "B202", "周三 10:00-12:00"))));

        RescheduleResultResponse rejected = rescheduleService.reject("FIN-1");
        assertEquals(ReschedulePlanStatus.REJECTED, rejected.getStatus());
        assertThrows(ReschedulePlanProcessedException.class, () -> rescheduleService.confirm("FIN-1"));
    }

    @Test
    void confirm_thenReject_throws() {
        seed("数学", "张老师", "A101", "周一 08:00-10:00");
        rescheduleService.createPlan(planRequest("FIN-2", items(item(1L, "B202", "周三 10:00-12:00"))));
        rescheduleService.confirm("FIN-2");
        assertThrows(ReschedulePlanProcessedException.class, () -> rescheduleService.reject("FIN-2"));
    }

    @Test
    void reject_isIdempotent() {
        seed("数学", "张老师", "A101", "周一 08:00-10:00");
        rescheduleService.createPlan(planRequest("FIN-3", items(item(1L, "B202", "周三 10:00-12:00"))));
        assertEquals(ReschedulePlanStatus.REJECTED, rescheduleService.reject("FIN-3").getStatus());
        assertEquals(ReschedulePlanStatus.REJECTED, rescheduleService.reject("FIN-3").getStatus());
    }

    @Test
    void operationsOnUnknownPlan_throwNotFound() {
        assertThrows(ReschedulePlanNotFoundException.class, () -> rescheduleService.getPlan("NOPE"));
        assertThrows(ReschedulePlanNotFoundException.class, () -> rescheduleService.confirm("NOPE"));
        assertThrows(ReschedulePlanNotFoundException.class, () -> rescheduleService.reject("NOPE"));
    }

    // ---------- 查询与审计 ----------

    @Test
    void queries_returnPlanIssuesResultAndChanges() {
        seed("数学", "张老师", "A101", "周一 08:00-10:00");
        seed("物理", "李老师", "B202", "周三 10:00-12:00");

        ReschedulePlanResponse plan = rescheduleService.createPlan(
                planRequest("Q-1", items(item(1L, "B202", "周三 10:00-12:00"))));

        // 确认前可查到冲突明细。
        assertFalse(rescheduleService.getIssues(plan.getPlanId()).isEmpty());
        assertEquals(ReschedulePlanStatus.PENDING,
                rescheduleService.getResult(plan.getPlanId()).getStatus());

        // 解除冲突后确认，可查到变更明细。
        scheduleService.deleteSchedule(2L);
        rescheduleService.confirm(plan.getPlanId());

        assertTrue(rescheduleService.getIssues(plan.getPlanId()).isEmpty());
        assertEquals(1, rescheduleService.getChanges(plan.getPlanId()).size());
        RescheduleResultResponse result = rescheduleService.getResult(plan.getPlanId());
        assertTrue(result.isApplied());
        assertNotNull(result.getProcessedAt());

        // 按状态过滤列表。
        assertEquals(1, rescheduleService.listPlans(ReschedulePlanStatus.CONFIRMED).size());
        assertEquals(0, rescheduleService.listPlans(ReschedulePlanStatus.PENDING).size());
    }

    @Test
    void confirm_recordsPerCourseAuditLogsBeforeAndAfter() {
        seed("数学", "张老师", "A101", "周一 08:00-10:00");
        seed("物理", "李老师", "B202", "周一 10:00-12:00");

        ReschedulePlanResponse plan = rescheduleService.createPlan(planRequest("AUD-1", items(
                item(1L, "B202", "周一 10:00-12:00"),
                item(2L, "A101", "周一 08:00-10:00"))));
        rescheduleService.confirm(plan.getPlanId());

        com.coursescheduler.dto.AuditLogFilterRequest filter =
                new com.coursescheduler.dto.AuditLogFilterRequest();
        filter.setOperationType(OperationType.RESCHEDULE);
        filter.setSuccess(true);
        List<com.coursescheduler.dto.AuditLogResponse> logs = auditLogService.queryLogs(filter);
        assertEquals(2, logs.size());
    }

    @Test
    void confirm_failureRecordsFailedAuditLogs() {
        seed("数学", "张老师", "A101", "周一 08:00-10:00");

        ReschedulePlanResponse plan = rescheduleService.createPlan(
                planRequest("AUD-2", items(item(1L, "A101", "周三 08:00-10:00"))));
        scheduleService.deleteSchedule(1L);
        rescheduleService.confirm(plan.getPlanId());

        long count = auditLogService.getAllLogs().stream()
                .filter(l -> l.getOperationType() == OperationType.RESCHEDULE_FAILED && !l.isSuccess())
                .count();
        assertEquals(1, count);
    }
}
