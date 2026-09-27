package com.coursescheduler.service;

import com.coursescheduler.dto.AuditLogFilterRequest;
import com.coursescheduler.dto.AuditLogResponse;
import com.coursescheduler.dto.CourseScheduleCreateRequest;
import com.coursescheduler.dto.CourseScheduleResponse;
import com.coursescheduler.dto.CourseScheduleUpdateRequest;
import com.coursescheduler.dto.RescheduleConflictDTO;
import com.coursescheduler.dto.ReschedulePlanItemRequest;
import com.coursescheduler.dto.ReschedulePlanResponse;
import com.coursescheduler.dto.ReschedulePlanSubmitRequest;
import com.coursescheduler.dto.ReschedulePreCheckResponse;
import com.coursescheduler.exception.InvalidRequestParameterException;
import com.coursescheduler.exception.RescheduleConflictException;
import com.coursescheduler.exception.ReschedulePlanNotFoundException;
import com.coursescheduler.exception.ReschedulePlanStateException;
import com.coursescheduler.exception.ScheduleNotFoundException;
import com.coursescheduler.model.OperationType;
import com.coursescheduler.model.ReschedulePlanStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ReschedulePlanServiceTest {

    private CourseScheduleService scheduleService;
    private AuditLogService auditLogService;
    private ReschedulePlanService planService;

    @BeforeEach
    void setUp() {
        auditLogService = new AuditLogService(Clock.systemDefaultZone());
        scheduleService = new CourseScheduleService(auditLogService);
        planService = new ReschedulePlanService(scheduleService, Clock.systemDefaultZone());
    }

    private CourseScheduleResponse addSchedule(String courseName, String teacherName, String classroom, String timeSlot) {
        CourseScheduleCreateRequest request = new CourseScheduleCreateRequest();
        request.setCourseName(courseName);
        request.setTeacherName(teacherName);
        request.setClassroom(classroom);
        request.setTimeSlot(timeSlot);
        return scheduleService.addSchedule(request);
    }

    private ReschedulePlanSubmitRequest submitRequest(String bizKey, ReschedulePlanItemRequest... items) {
        return new ReschedulePlanSubmitRequest(bizKey, Arrays.asList(items));
    }

    private ReschedulePlanItemRequest item(Long scheduleId, String newClassroom, String newTimeSlot) {
        return new ReschedulePlanItemRequest(scheduleId, newClassroom, newTimeSlot);
    }

    private CourseScheduleResponse findSchedule(Long id) {
        return scheduleService.findSchedules(null).stream()
                .filter(s -> s.getId().equals(id))
                .findFirst()
                .orElse(null);
    }

    @Test
    void testSubmit_Success_SavesOriginalSnapshot() {
        CourseScheduleResponse s1 = addSchedule("数学", "张老师", "A101", "周一 08:00-10:00");
        CourseScheduleResponse s2 = addSchedule("物理", "李老师", "B202", "周二 14:00-16:00");

        ReschedulePlanService.SubmitOutcome outcome = planService.submit(submitRequest("BIZ-1",
                item(s1.getId(), "C303", "周三 10:00-12:00"),
                item(s2.getId(), "D404", "周四 08:00-10:00")));

        assertFalse(outcome.isReplayed());
        ReschedulePlanResponse plan = outcome.getResponse();
        assertEquals(1L, plan.getId());
        assertEquals("BIZ-1", plan.getBizKey());
        assertEquals(ReschedulePlanStatus.PENDING, plan.getStatus());
        assertEquals(2, plan.getItems().size());

        assertEquals("数学", plan.getItems().get(0).getCourseName());
        assertEquals("A101", plan.getItems().get(0).getOriginalClassroom());
        assertEquals("周一 08:00-10:00", plan.getItems().get(0).getOriginalTimeSlot());
        assertEquals("C303", plan.getItems().get(0).getNewClassroom());
        assertEquals("周三 10:00-12:00", plan.getItems().get(0).getNewTimeSlot());

        assertEquals("物理", plan.getItems().get(1).getCourseName());
        assertEquals("B202", plan.getItems().get(1).getOriginalClassroom());
        assertEquals("周二 14:00-16:00", plan.getItems().get(1).getOriginalTimeSlot());
    }

    @Test
    void testSubmit_BlankBizKey_Throws() {
        CourseScheduleResponse s1 = addSchedule("数学", "张老师", "A101", "周一 08:00-10:00");
        assertThrows(InvalidRequestParameterException.class,
                () -> planService.submit(submitRequest("  ", item(s1.getId(), "C303", "周三 10:00-12:00"))));
    }

    @Test
    void testSubmit_EmptyItems_Throws() {
        assertThrows(InvalidRequestParameterException.class,
                () -> planService.submit(new ReschedulePlanSubmitRequest("BIZ-1", new ArrayList<>())));
        assertThrows(InvalidRequestParameterException.class,
                () -> planService.submit(new ReschedulePlanSubmitRequest("BIZ-1", null)));
    }

    @Test
    void testSubmit_DuplicateScheduleIds_Throws() {
        CourseScheduleResponse s1 = addSchedule("数学", "张老师", "A101", "周一 08:00-10:00");
        assertThrows(InvalidRequestParameterException.class,
                () -> planService.submit(submitRequest("BIZ-1",
                        item(s1.getId(), "C303", "周三 10:00-12:00"),
                        item(s1.getId(), "D404", "周四 08:00-10:00"))));
    }

    @Test
    void testSubmit_MissingSchedule_ThrowsNotFound() {
        assertThrows(ScheduleNotFoundException.class,
                () -> planService.submit(submitRequest("BIZ-1", item(999L, "C303", "周三 10:00-12:00"))));
    }

    @Test
    void testSubmit_InvalidNewTimeSlot_Throws() {
        CourseScheduleResponse s1 = addSchedule("数学", "张老师", "A101", "周一 08:00-10:00");
        assertThrows(com.coursescheduler.exception.InvalidTimeSlotException.class,
                () -> planService.submit(submitRequest("BIZ-1", item(s1.getId(), "C303", "周三 25:00-26:00"))));
    }

    @Test
    void testSubmit_IdempotentReplay_ReturnsFirstResult() {
        CourseScheduleResponse s1 = addSchedule("数学", "张老师", "A101", "周一 08:00-10:00");

        ReschedulePlanService.SubmitOutcome first = planService.submit(submitRequest("BIZ-1",
                item(s1.getId(), "C303", "周三 10:00-12:00")));
        ReschedulePlanService.SubmitOutcome second = planService.submit(submitRequest("BIZ-1",
                item(s1.getId(), "C303", "周三 10:00-12:00")));

        assertFalse(first.isReplayed());
        assertTrue(second.isReplayed());
        assertEquals(first.getResponse().getId(), second.getResponse().getId());
        assertEquals(1, planService.listPlans().size());
    }

    @Test
    void testSubmit_SameBizKeyDifferentContent_ThrowsConflict() {
        CourseScheduleResponse s1 = addSchedule("数学", "张老师", "A101", "周一 08:00-10:00");
        planService.submit(submitRequest("BIZ-1", item(s1.getId(), "C303", "周三 10:00-12:00")));

        assertThrows(ReschedulePlanStateException.class,
                () -> planService.submit(submitRequest("BIZ-1", item(s1.getId(), "D404", "周三 10:00-12:00"))));
    }

    @Test
    void testPreCheck_SwapTimeSlots_NoConflicts() {
        CourseScheduleResponse s1 = addSchedule("数学", "张老师", "A101", "周一 08:00-10:00");
        CourseScheduleResponse s2 = addSchedule("物理", "李老师", "B202", "周二 14:00-16:00");

        // 两门课程互换时段和教室：旧时段/旧教室在方案内腾出，不应算冲突
        ReschedulePlanResponse plan = planService.submit(submitRequest("BIZ-SWAP",
                item(s1.getId(), "B202", "周二 14:00-16:00"),
                item(s2.getId(), "A101", "周一 08:00-10:00"))).getResponse();

        ReschedulePreCheckResponse preCheck = planService.preCheck(plan.getId());

        assertTrue(preCheck.isCanReschedule());
        assertEquals(0, preCheck.getConflictCount());
        assertTrue(preCheck.getConflicts().isEmpty());
    }

    @Test
    void testPreCheck_ConflictWithOutsideCourse_ReturnsAllDetails() {
        CourseScheduleResponse s1 = addSchedule("数学", "张老师", "A101", "周一 08:00-10:00");
        // 方案外课程：同老师占用目标时段
        addSchedule("英语", "张老师", "B202", "周三 10:00-12:00");
        // 方案外课程：目标教室在该时段被占用
        addSchedule("化学", "王老师", "C303", "周三 11:00-13:00");

        ReschedulePlanResponse plan = planService.submit(submitRequest("BIZ-1",
                item(s1.getId(), "C303", "周三 10:30-12:00"))).getResponse();

        ReschedulePreCheckResponse preCheck = planService.preCheck(plan.getId());

        assertFalse(preCheck.isCanReschedule());
        assertEquals(2, preCheck.getConflictCount());
        assertTrue(preCheck.getConflicts().stream()
                .anyMatch(c -> c.getConflictType() == RescheduleConflictDTO.ConflictType.TEACHER));
        assertTrue(preCheck.getConflicts().stream()
                .anyMatch(c -> c.getConflictType() == RescheduleConflictDTO.ConflictType.CLASSROOM));
        // 冲突明细已保存在方案上，可通过查询获取
        ReschedulePlanResponse stored = planService.getPlan(plan.getId());
        assertEquals(2, stored.getConflictCount());
    }

    @Test
    void testPreCheck_InternalConflictBetweenItems_Detected() {
        CourseScheduleResponse s1 = addSchedule("数学", "张老师", "A101", "周一 08:00-10:00");
        CourseScheduleResponse s2 = addSchedule("物理", "张老师", "B202", "周二 14:00-16:00");

        // 同一老师的两门课程被调整到重叠时段
        ReschedulePlanResponse plan = planService.submit(submitRequest("BIZ-1",
                item(s1.getId(), "A101", "周三 10:00-12:00"),
                item(s2.getId(), "B202", "周三 11:00-13:00"))).getResponse();

        ReschedulePreCheckResponse preCheck = planService.preCheck(plan.getId());

        assertFalse(preCheck.isCanReschedule());
        assertTrue(preCheck.getConflicts().stream()
                .anyMatch(c -> c.getConflictType() == RescheduleConflictDTO.ConflictType.INTERNAL_TEACHER));
    }

    @Test
    void testConfirm_AppliesAllChangesAtomically_AndRecordsAudit() {
        CourseScheduleResponse s1 = addSchedule("数学", "张老师", "A101", "周一 08:00-10:00");
        CourseScheduleResponse s2 = addSchedule("物理", "李老师", "B202", "周二 14:00-16:00");

        ReschedulePlanResponse plan = planService.submit(submitRequest("BIZ-1",
                item(s1.getId(), "C303", "周三 10:00-12:00"),
                item(s2.getId(), "D404", "周四 08:00-10:00"))).getResponse();

        ReschedulePlanResponse confirmed = planService.confirm(plan.getId());

        assertEquals(ReschedulePlanStatus.CONFIRMED, confirmed.getStatus());
        assertNotNull(confirmed.getProcessedAt());

        CourseScheduleResponse updated1 = findSchedule(s1.getId());
        assertEquals("C303", updated1.getClassroom());
        assertEquals("周三 10:00-12:00", updated1.getTimeSlot());
        CourseScheduleResponse updated2 = findSchedule(s2.getId());
        assertEquals("D404", updated2.getClassroom());
        assertEquals("周四 08:00-10:00", updated2.getTimeSlot());

        // 每条课程都有调整前后的审计记录
        AuditLogFilterRequest filter = new AuditLogFilterRequest();
        filter.setOperationType(OperationType.RESCHEDULE);
        filter.setSuccess(true);
        List<AuditLogResponse> logs = auditLogService.queryLogs(filter);
        assertEquals(2, logs.size());
        AuditLogResponse log1 = logs.stream().filter(l -> l.getCourseId().equals(s1.getId())).findFirst().orElseThrow();
        assertEquals("A101", log1.getPreviousClassroom());
        assertEquals("周一 08:00-10:00", log1.getPreviousTimeSlot());
        assertEquals("C303", log1.getClassroom());
        assertEquals("周三 10:00-12:00", log1.getTimeSlot());
    }

    @Test
    void testConfirm_CourseModifiedAfterSubmit_WholePlanNotApplied() {
        CourseScheduleResponse s1 = addSchedule("数学", "张老师", "A101", "周一 08:00-10:00");
        CourseScheduleResponse s2 = addSchedule("物理", "李老师", "B202", "周二 14:00-16:00");

        ReschedulePlanResponse plan = planService.submit(submitRequest("BIZ-1",
                item(s1.getId(), "C303", "周三 10:00-12:00"),
                item(s2.getId(), "D404", "周四 08:00-10:00"))).getResponse();

        // 他人在方案提交后修改了 s1
        CourseScheduleUpdateRequest update = new CourseScheduleUpdateRequest();
        update.setCourseName("数学");
        update.setTeacherName("张老师");
        update.setClassroom("A101");
        update.setTimeSlot("周五 08:00-10:00");
        scheduleService.updateSchedule(s1.getId(), update);

        RescheduleConflictException ex = assertThrows(RescheduleConflictException.class,
                () -> planService.confirm(plan.getId()));
        assertTrue(ex.getConflicts().stream()
                .anyMatch(c -> c.getConflictType() == RescheduleConflictDTO.ConflictType.SCHEDULE_CHANGED));

        // 原子性：s2 也不能被调整
        assertEquals("B202", findSchedule(s2.getId()).getClassroom());
        assertEquals("周二 14:00-16:00", findSchedule(s2.getId()).getTimeSlot());
        // 方案仍未确认，保留冲突明细
        ReschedulePlanResponse stored = planService.getPlan(plan.getId());
        assertEquals(ReschedulePlanStatus.PENDING, stored.getStatus());
        assertTrue(stored.getConflictCount() > 0);
    }

    @Test
    void testConfirm_CourseDeletedAfterSubmit_WholePlanNotApplied() {
        CourseScheduleResponse s1 = addSchedule("数学", "张老师", "A101", "周一 08:00-10:00");
        CourseScheduleResponse s2 = addSchedule("物理", "李老师", "B202", "周二 14:00-16:00");

        ReschedulePlanResponse plan = planService.submit(submitRequest("BIZ-1",
                item(s1.getId(), "C303", "周三 10:00-12:00"),
                item(s2.getId(), "D404", "周四 08:00-10:00"))).getResponse();

        scheduleService.deleteSchedule(s1.getId());

        RescheduleConflictException ex = assertThrows(RescheduleConflictException.class,
                () -> planService.confirm(plan.getId()));
        assertTrue(ex.getConflicts().stream()
                .anyMatch(c -> c.getConflictType() == RescheduleConflictDTO.ConflictType.SCHEDULE_NOT_FOUND));
        assertEquals("B202", findSchedule(s2.getId()).getClassroom());
    }

    @Test
    void testConfirm_TargetResourceOccupiedAfterSubmit_WholePlanNotApplied() {
        CourseScheduleResponse s1 = addSchedule("数学", "张老师", "A101", "周一 08:00-10:00");

        ReschedulePlanResponse plan = planService.submit(submitRequest("BIZ-1",
                item(s1.getId(), "C303", "周三 10:00-12:00"))).getResponse();

        // 他人在方案提交后占用了目标教室/时段
        addSchedule("化学", "王老师", "C303", "周三 11:00-13:00");

        RescheduleConflictException ex = assertThrows(RescheduleConflictException.class,
                () -> planService.confirm(plan.getId()));
        assertTrue(ex.getConflicts().stream()
                .anyMatch(c -> c.getConflictType() == RescheduleConflictDTO.ConflictType.CLASSROOM));
        assertEquals("A101", findSchedule(s1.getId()).getClassroom());
        assertEquals("周一 08:00-10:00", findSchedule(s1.getId()).getTimeSlot());
    }

    @Test
    void testConfirm_ConcurrentPlansOnSameCourse_OnlyOneSucceeds() throws Exception {
        CourseScheduleResponse s1 = addSchedule("数学", "张老师", "A101", "周一 08:00-10:00");

        ReschedulePlanResponse planA = planService.submit(submitRequest("BIZ-A",
                item(s1.getId(), "C303", "周三 10:00-12:00"))).getResponse();
        ReschedulePlanResponse planB = planService.submit(submitRequest("BIZ-B",
                item(s1.getId(), "D404", "周四 08:00-10:00"))).getResponse();

        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger conflictCount = new AtomicInteger();

        List<Long> planIds = Arrays.asList(planA.getId(), planB.getId());
        for (Long planId : planIds) {
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    planService.confirm(planId);
                    successCount.incrementAndGet();
                } catch (RescheduleConflictException e) {
                    conflictCount.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        assertTrue(ready.await(10, TimeUnit.SECONDS));
        start.countDown();
        executor.shutdown();
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));

        assertEquals(1, successCount.get());
        assertEquals(1, conflictCount.get());

        // 恰好一个方案生效，课程只被调整一次，无部分结果
        CourseScheduleResponse current = findSchedule(s1.getId());
        boolean appliedA = "C303".equals(current.getClassroom()) && "周三 10:00-12:00".equals(current.getTimeSlot());
        boolean appliedB = "D404".equals(current.getClassroom()) && "周四 08:00-10:00".equals(current.getTimeSlot());
        assertTrue(appliedA ^ appliedB);

        ReschedulePlanStatus statusA = planService.getPlan(planA.getId()).getStatus();
        ReschedulePlanStatus statusB = planService.getPlan(planB.getId()).getStatus();
        assertTrue(statusA == ReschedulePlanStatus.CONFIRMED ^ statusB == ReschedulePlanStatus.CONFIRMED);
    }

    @Test
    void testConfirm_Twice_ThrowsStateException() {
        CourseScheduleResponse s1 = addSchedule("数学", "张老师", "A101", "周一 08:00-10:00");
        ReschedulePlanResponse plan = planService.submit(submitRequest("BIZ-1",
                item(s1.getId(), "C303", "周三 10:00-12:00"))).getResponse();

        planService.confirm(plan.getId());

        assertThrows(ReschedulePlanStateException.class, () -> planService.confirm(plan.getId()));
        assertThrows(ReschedulePlanStateException.class, () -> planService.reject(plan.getId()));
        assertThrows(ReschedulePlanStateException.class, () -> planService.preCheck(plan.getId()));
    }

    @Test
    void testReject_ThenCannotConfirm() {
        CourseScheduleResponse s1 = addSchedule("数学", "张老师", "A101", "周一 08:00-10:00");
        ReschedulePlanResponse plan = planService.submit(submitRequest("BIZ-1",
                item(s1.getId(), "C303", "周三 10:00-12:00"))).getResponse();

        ReschedulePlanResponse rejected = planService.reject(plan.getId());
        assertEquals(ReschedulePlanStatus.REJECTED, rejected.getStatus());
        assertNotNull(rejected.getProcessedAt());

        assertThrows(ReschedulePlanStateException.class, () -> planService.confirm(plan.getId()));
        assertThrows(ReschedulePlanStateException.class, () -> planService.reject(plan.getId()));

        // 拒绝后课程保持原样
        assertEquals("A101", findSchedule(s1.getId()).getClassroom());
    }

    @Test
    void testGetPlan_NotFound_Throws() {
        assertThrows(ReschedulePlanNotFoundException.class, () -> planService.getPlan(42L));
        assertThrows(ReschedulePlanNotFoundException.class, () -> planService.preCheck(42L));
        assertThrows(ReschedulePlanNotFoundException.class, () -> planService.confirm(42L));
        assertThrows(ReschedulePlanNotFoundException.class, () -> planService.reject(42L));
    }

    @Test
    void testListPlans_ReturnsAllSubmitted() {
        CourseScheduleResponse s1 = addSchedule("数学", "张老师", "A101", "周一 08:00-10:00");
        CourseScheduleResponse s2 = addSchedule("物理", "李老师", "B202", "周二 14:00-16:00");

        planService.submit(submitRequest("BIZ-1", item(s1.getId(), "C303", "周三 10:00-12:00")));
        planService.submit(submitRequest("BIZ-2", item(s2.getId(), "D404", "周四 08:00-10:00")));

        List<ReschedulePlanResponse> plans = planService.listPlans();
        assertEquals(2, plans.size());
        assertEquals("BIZ-1", plans.get(0).getBizKey());
        assertEquals("BIZ-2", plans.get(1).getBizKey());
    }

    @Test
    void testConfirm_SwapBetweenTwoCourses_Succeeds() {
        CourseScheduleResponse s1 = addSchedule("数学", "张老师", "A101", "周一 08:00-10:00");
        CourseScheduleResponse s2 = addSchedule("物理", "李老师", "B202", "周二 14:00-16:00");

        ReschedulePlanResponse plan = planService.submit(submitRequest("BIZ-SWAP",
                item(s1.getId(), "B202", "周二 14:00-16:00"),
                item(s2.getId(), "A101", "周一 08:00-10:00"))).getResponse();

        ReschedulePlanResponse confirmed = planService.confirm(plan.getId());
        assertEquals(ReschedulePlanStatus.CONFIRMED, confirmed.getStatus());

        CourseScheduleResponse updated1 = findSchedule(s1.getId());
        assertEquals("B202", updated1.getClassroom());
        assertEquals("周二 14:00-16:00", updated1.getTimeSlot());
        CourseScheduleResponse updated2 = findSchedule(s2.getId());
        assertEquals("A101", updated2.getClassroom());
        assertEquals("周一 08:00-10:00", updated2.getTimeSlot());
    }
}
