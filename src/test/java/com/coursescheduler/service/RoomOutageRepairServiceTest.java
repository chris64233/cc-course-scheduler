package com.coursescheduler.service;

import com.coursescheduler.dto.AuditLogFilterRequest;
import com.coursescheduler.dto.AuditLogResponse;
import com.coursescheduler.dto.CourseChangeChainResponse;
import com.coursescheduler.dto.CourseScheduleCreateRequest;
import com.coursescheduler.dto.CourseScheduleResponse;
import com.coursescheduler.dto.RepairPlanActionRequest;
import com.coursescheduler.dto.RepairPlanResponse;
import com.coursescheduler.dto.RepairPlanSubmitRequest;
import com.coursescheduler.dto.RepairTaskResponse;
import com.coursescheduler.dto.RescheduleConflictDTO;
import com.coursescheduler.dto.ReschedulePlanItemRequest;
import com.coursescheduler.dto.ReschedulePlanSubmitRequest;
import com.coursescheduler.dto.ReschedulePreCheckResponse;
import com.coursescheduler.dto.RoomOutageAdjustRequest;
import com.coursescheduler.dto.RoomOutageCreateRequest;
import com.coursescheduler.dto.RoomOutageResponse;
import com.coursescheduler.exception.InvalidRequestParameterException;
import com.coursescheduler.exception.RepairCoverageException;
import com.coursescheduler.exception.RepairTaskStateException;
import com.coursescheduler.exception.RescheduleConflictException;
import com.coursescheduler.exception.RoomOutageBlockedException;
import com.coursescheduler.exception.RoomOutageConflictException;
import com.coursescheduler.model.OperationType;
import com.coursescheduler.model.RepairTaskStatus;
import com.coursescheduler.model.ReschedulePlanStatus;
import com.coursescheduler.model.RoomOutageStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class RoomOutageRepairServiceTest {

    private AuditLogService auditLogService;
    private CourseScheduleService scheduleService;
    private RoomOutageRegistry outageRegistry;
    private RepairTaskRegistry taskRegistry;
    private SchedulingLocks schedulingLocks;
    private RoomOutageService outageService;
    private RepairPlanService repairPlanService;
    private ReschedulePlanService reschedulePlanService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.systemDefaultZone();
        auditLogService = new AuditLogService(clock);
        outageRegistry = new RoomOutageRegistry();
        taskRegistry = new RepairTaskRegistry();
        schedulingLocks = new SchedulingLocks();
        scheduleService = new CourseScheduleService(auditLogService, outageRegistry, schedulingLocks);
        outageService = new RoomOutageService(scheduleService, outageRegistry, taskRegistry,
                auditLogService, schedulingLocks, clock);
        repairPlanService = new RepairPlanService(scheduleService, outageService, outageRegistry,
                taskRegistry, auditLogService, schedulingLocks, clock);
        reschedulePlanService = new ReschedulePlanService(scheduleService, outageRegistry,
                schedulingLocks, clock);
    }

    private CourseScheduleResponse addSchedule(String courseName, String teacherName,
                                               String classroom, String timeSlot) {
        CourseScheduleCreateRequest request = new CourseScheduleCreateRequest();
        request.setCourseName(courseName);
        request.setTeacherName(teacherName);
        request.setClassroom(classroom);
        request.setTimeSlot(timeSlot);
        return scheduleService.addSchedule(request);
    }

    private CourseScheduleResponse findSchedule(Long id) {
        return scheduleService.findSchedules(null).stream()
                .filter(s -> s.getId().equals(id))
                .findFirst()
                .orElse(null);
    }

    private RoomOutageCreateRequest outageRequest(String eventNo, String classroom,
                                                  String reason, String... timeSlots) {
        RoomOutageCreateRequest request = new RoomOutageCreateRequest();
        request.setEventNo(eventNo);
        request.setClassroom(classroom);
        request.setReason(reason);
        request.setTimeSlots(Arrays.asList(timeSlots));
        request.setOperator("管理员");
        return request;
    }

    private ReschedulePlanItemRequest item(Long scheduleId, String newClassroom, String newTimeSlot) {
        return new ReschedulePlanItemRequest(scheduleId, newClassroom, newTimeSlot);
    }

    private RepairPlanSubmitRequest repairRequest(String bizKey, Long taskId,
                                                  ReschedulePlanItemRequest... items) {
        RepairPlanSubmitRequest request = new RepairPlanSubmitRequest();
        request.setBizKey(bizKey);
        request.setTaskId(taskId);
        request.setItems(Arrays.asList(items));
        request.setOperator("教务处");
        return request;
    }

    private RepairPlanActionRequest action(String operator) {
        RepairPlanActionRequest request = new RepairPlanActionRequest();
        request.setOperator(operator);
        return request;
    }

    private com.coursescheduler.dto.RoomOutageCancelRequest cancelAction(String operator) {
        com.coursescheduler.dto.RoomOutageCancelRequest request =
                new com.coursescheduler.dto.RoomOutageCancelRequest();
        request.setOperator(operator);
        return request;
    }

    // ---------- 停用生效与冻结 ----------

    @Test
    void testCreateOutage_FreezesAllConflictingCoursesAsRepairTask() {
        CourseScheduleResponse hit1 = addSchedule("数学", "张老师", "A101", "周三 09:00-10:00");
        CourseScheduleResponse hit2 = addSchedule("物理", "李老师", "A101", "周三 10:00-12:00");
        CourseScheduleResponse miss = addSchedule("化学", "王老师", "A101", "周四 10:00-12:00");

        RoomOutageService.CreateOutcome outcome = outageService.createOutage(
                outageRequest("EVT-1", "A101", "设备检修", "周三 08:00-12:00"));

        assertFalse(outcome.isReplayed());
        RoomOutageResponse outage = outcome.getResponse();
        assertEquals(1L, outage.getId());
        assertEquals(RoomOutageStatus.ACTIVE, outage.getStatus());
        assertEquals(1L, outage.getVersion());
        assertNotNull(outage.getRepairTaskId());
        assertEquals("管理员", outage.getCreatedBy());

        RepairTaskResponse task = outageService.getRepairTask(outage.getRepairTaskId());
        assertEquals(RepairTaskStatus.OPEN, task.getStatus());
        assertEquals(2, task.getAffectedCount());
        assertEquals(0, task.getResolvedCount());
        assertTrue(task.getAffectedCourses().stream()
                .anyMatch(c -> c.getScheduleId().equals(hit1.getId())));
        assertTrue(task.getAffectedCourses().stream()
                .anyMatch(c -> c.getScheduleId().equals(hit2.getId())));
        assertTrue(task.getAffectedCourses().stream()
                .noneMatch(c -> c.getScheduleId().equals(miss.getId())));
    }

    @Test
    void testCreateOutage_NoConflict_NoRepairTask() {
        addSchedule("数学", "张老师", "A101", "周一 08:00-10:00");

        RoomOutageResponse outage = outageService.createOutage(
                outageRequest("EVT-1", "A101", "设备检修", "周三 10:00-12:00")).getResponse();

        assertNull(outage.getRepairTaskId());
        assertTrue(outageService.listRepairTasks().isEmpty());
    }

    @Test
    void testCreateOutage_SameEventSameContent_Idempotent() {
        CourseScheduleResponse hit = addSchedule("数学", "张老师", "A101", "周三 10:00-12:00");

        RoomOutageService.CreateOutcome first = outageService.createOutage(
                outageRequest("EVT-1", "A101", "设备检修", "周三 08:00-12:00"));
        // 停用生效后新增课程到停用窗内（且不与既有课重叠）会被阻止
        assertThrows(RoomOutageBlockedException.class,
                () -> addSchedule("物理", "李老师", "A101", "周三 08:00-09:00"));

        RoomOutageService.CreateOutcome second = outageService.createOutage(
                outageRequest("EVT-1", "A101", "设备检修", "周三 08:00-12:00"));

        assertTrue(second.isReplayed());
        assertEquals(first.getResponse().getId(), second.getResponse().getId());
        assertEquals(first.getResponse().getRepairTaskId(), second.getResponse().getRepairTaskId());
        // 冻结内容不随后来的课程变化：任务仍只有首次生效时冲突的 1 门
        RepairTaskResponse task = outageService.getRepairTask(second.getResponse().getRepairTaskId());
        assertEquals(1, task.getAffectedCount());
        assertEquals(hit.getId(), task.getAffectedCourses().get(0).getScheduleId());
        assertEquals(1, outageService.listOutages().size());
    }

    @Test
    void testCreateOutage_SameEventDifferentContent_Conflict() {
        outageService.createOutage(outageRequest("EVT-1", "A101", "设备检修", "周三 10:00-12:00"));

        RoomOutageConflictException ex = assertThrows(RoomOutageConflictException.class,
                () -> outageService.createOutage(
                        outageRequest("EVT-1", "A101", "突发停电", "周三 10:00-12:00")));
        assertTrue(ex.getMessage().contains("EVT-1"));
    }

    @Test
    void testCreateOutage_InvalidRequest_Throws() {
        assertThrows(InvalidRequestParameterException.class,
                () -> outageService.createOutage(outageRequest("  ", "A101", "原因", "周三 10:00-12:00")));
        assertThrows(InvalidRequestParameterException.class,
                () -> outageService.createOutage(outageRequest("EVT-1", "  ", "原因", "周三 10:00-12:00")));
        assertThrows(InvalidRequestParameterException.class,
                () -> outageService.createOutage(outageRequest("EVT-1", "A101", "  ", "周三 10:00-12:00")));
        RoomOutageCreateRequest noSlots = outageRequest("EVT-1", "A101", "原因");
        noSlots.setTimeSlots(java.util.Collections.emptyList());
        assertThrows(InvalidRequestParameterException.class, () -> outageService.createOutage(noSlots));
    }

    @Test
    void testNewSchedule_CannotEnterBlockedSlot() {
        outageService.createOutage(outageRequest("EVT-1", "A101", "设备检修", "周三 10:00-12:00"));

        RoomOutageBlockedException ex = assertThrows(RoomOutageBlockedException.class,
                () -> addSchedule("数学", "张老师", "A101", "周三 11:00-12:00"));
        assertTrue(ex.getMessage().contains("EVT-1"));

        // 同教室不重叠时段仍可安排
        CourseScheduleResponse ok = addSchedule("物理", "李老师", "A101", "周三 14:00-16:00");
        assertNotNull(ok);
    }

    // ---------- 范围调整 ----------

    @Test
    void testAdjustOutage_ExpandsRange_AppendsAffectedAndReopensResolvedTask() {
        CourseScheduleResponse first = addSchedule("数学", "张老师", "A101", "周三 10:00-12:00");
        RoomOutageResponse outage = outageService.createOutage(
                outageRequest("EVT-1", "A101", "设备检修", "周三 10:00-12:00")).getResponse();
        Long taskId = outage.getRepairTaskId();

        // 先完成修复
        RepairPlanResponse plan = repairPlanService.submit(repairRequest("FIX-1", taskId,
                item(first.getId(), "B202", "周三 10:00-12:00"))).getResponse();
        repairPlanService.confirm(plan.getId(), action("教务处"));
        assertEquals(RepairTaskStatus.RESOLVED, outageService.getRepairTask(taskId).getStatus());

        // 停用范围扩大到周四：新增冲突课程，已修复的周三课程不再受影响，任务重新打开
        CourseScheduleResponse second = addSchedule("化学", "王老师", "A101", "周四 08:00-10:00");
        RoomOutageAdjustRequest adjust = new RoomOutageAdjustRequest();
        adjust.setTimeSlots(Arrays.asList("周三 10:00-12:00", "周四 08:00-10:00"));
        adjust.setReason("设备检修延长");
        adjust.setOperator("管理员");
        RoomOutageResponse adjusted = outageService.adjustOutage(outage.getId(), adjust);

        assertEquals(2L, adjusted.getVersion());
        assertEquals(2, adjusted.getRangeHistory().size());
        RepairTaskResponse task = outageService.getRepairTask(taskId);
        assertEquals(RepairTaskStatus.OPEN, task.getStatus());
        assertEquals(2, task.getAffectedCount());
        assertEquals(1, task.getResolvedCount());
        assertTrue(task.getAffectedCourses().stream()
                .anyMatch(c -> c.getScheduleId().equals(second.getId()) && !c.isResolved()));
    }

    @Test
    void testAdjustOutage_StaleVersion_Conflict() {
        RoomOutageResponse outage = outageService.createOutage(
                outageRequest("EVT-1", "A101", "设备检修", "周三 10:00-12:00")).getResponse();

        RoomOutageAdjustRequest first = new RoomOutageAdjustRequest();
        first.setReason("原因2");
        first.setExpectedVersion(1L);
        outageService.adjustOutage(outage.getId(), first);

        RoomOutageAdjustRequest stale = new RoomOutageAdjustRequest();
        stale.setReason("原因3");
        stale.setExpectedVersion(1L);
        assertThrows(RoomOutageConflictException.class,
                () -> outageService.adjustOutage(outage.getId(), stale));
    }

    @Test
    void testAdjustOutage_CancelledOutage_Conflict() {
        RoomOutageResponse outage = outageService.createOutage(
                outageRequest("EVT-1", "A101", "设备检修", "周三 10:00-12:00")).getResponse();
        outageService.cancelOutage(outage.getId(), null);

        RoomOutageAdjustRequest adjust = new RoomOutageAdjustRequest();
        adjust.setReason("新原因");
        assertThrows(RoomOutageConflictException.class,
                () -> outageService.adjustOutage(outage.getId(), adjust));
    }

    // ---------- 修复方案：覆盖、预检、确认 ----------

    @Test
    void testRepairSubmit_MissingAffectedCourse_NotAllowed() {
        CourseScheduleResponse s1 = addSchedule("数学", "张老师", "A101", "周三 09:00-10:00");
        addSchedule("物理", "李老师", "A101", "周三 10:00-12:00");
        Long taskId = outageService.createOutage(
                outageRequest("EVT-1", "A101", "设备检修", "周三 08:00-12:00"))
                .getResponse().getRepairTaskId();

        RepairCoverageException ex = assertThrows(RepairCoverageException.class,
                () -> repairPlanService.submit(repairRequest("FIX-1", taskId,
                        item(s1.getId(), "B202", "周三 09:00-10:00"))));
        assertEquals(1, ex.getMissingScheduleIds().size());
        assertTrue(ex.getExtraScheduleIds().isEmpty());
    }

    @Test
    void testRepairSubmit_ExtraCourseNotInTask_NotAllowed() {
        CourseScheduleResponse hit = addSchedule("数学", "张老师", "A101", "周三 10:00-12:00");
        CourseScheduleResponse outside = addSchedule("英语", "赵老师", "C303", "周五 10:00-12:00");
        Long taskId = outageService.createOutage(
                outageRequest("EVT-1", "A101", "设备检修", "周三 10:00-12:00"))
                .getResponse().getRepairTaskId();

        RepairCoverageException ex = assertThrows(RepairCoverageException.class,
                () -> repairPlanService.submit(repairRequest("FIX-1", taskId,
                        item(hit.getId(), "B202", "周三 10:00-12:00"),
                        item(outside.getId(), "D404", "周五 14:00-16:00"))));
        assertTrue(ex.getExtraScheduleIds().contains(outside.getId()));
    }

    @Test
    void testRepairPreCheck_TeacherAndClassroomConflicts_PerCourseReasons() {
        CourseScheduleResponse hit = addSchedule("数学", "张老师", "A101", "周三 10:00-12:00");
        // 目标时段张老师已有课
        addSchedule("英语", "张老师", "X999", "周四 08:00-10:00");
        // 目标教室目标时段被占
        addSchedule("化学", "王老师", "B202", "周四 09:00-11:00");
        Long taskId = outageService.createOutage(
                outageRequest("EVT-1", "A101", "设备检修", "周三 10:00-12:00"))
                .getResponse().getRepairTaskId();

        RepairPlanResponse plan = repairPlanService.submit(repairRequest("FIX-1", taskId,
                item(hit.getId(), "B202", "周四 08:00-10:00"))).getResponse();

        ReschedulePreCheckResponse preCheck = repairPlanService.preCheck(plan.getId());
        assertFalse(preCheck.isCanReschedule());
        List<RescheduleConflictDTO> conflicts = preCheck.getConflicts();
        assertTrue(conflicts.stream().anyMatch(c -> c.getConflictType() == RescheduleConflictDTO.ConflictType.TEACHER));
        assertTrue(conflicts.stream().anyMatch(c -> c.getConflictType() == RescheduleConflictDTO.ConflictType.CLASSROOM));
        assertTrue(conflicts.stream().allMatch(c -> c.getScheduleId().equals(hit.getId())));
    }

    @Test
    void testRepairConfirm_Success_AppliesAtomicallyMarksResolvedAndAudits() {
        CourseScheduleResponse hit = addSchedule("数学", "张老师", "A101", "周三 10:00-12:00");
        RoomOutageResponse outage = outageService.createOutage(
                outageRequest("EVT-1", "A101", "设备检修", "周三 10:00-12:00")).getResponse();
        Long taskId = outage.getRepairTaskId();

        RepairPlanResponse plan = repairPlanService.submit(repairRequest("FIX-1", taskId,
                item(hit.getId(), "B202", "周四 08:00-10:00"))).getResponse();
        RepairPlanResponse confirmed = repairPlanService.confirm(plan.getId(), action("李教务"));

        assertEquals(ReschedulePlanStatus.CONFIRMED, confirmed.getStatus());
        assertEquals("李教务", confirmed.getProcessedBy());

        CourseScheduleResponse moved = findSchedule(hit.getId());
        assertEquals("B202", moved.getClassroom());
        assertEquals("周四 08:00-10:00", moved.getTimeSlot());
        assertEquals(2L, moved.getVersion());

        RepairTaskResponse task = outageService.getRepairTask(taskId);
        assertEquals(RepairTaskStatus.RESOLVED, task.getStatus());
        assertEquals(1, task.getResolvedCount());
        assertEquals(plan.getId(), task.getConfirmedRepairPlanId());

        AuditLogFilterRequest filter = new AuditLogFilterRequest();
        filter.setOperationType(OperationType.REPAIR_CONFIRM);
        filter.setSuccess(true);
        List<AuditLogResponse> logs = auditLogService.queryLogs(filter);
        assertEquals(1, logs.size());
        AuditLogResponse log = logs.get(0);
        assertEquals("A101", log.getPreviousClassroom());
        assertEquals("周三 10:00-12:00", log.getPreviousTimeSlot());
        assertEquals("B202", log.getClassroom());
        assertEquals("周四 08:00-10:00", log.getTimeSlot());
        assertEquals("李教务", log.getOperator());
        assertEquals("FIX-1", log.getRefNo());
    }

    @Test
    void testRepairConfirm_MovesIntoNewlyAdjustedOutageRange_WholePlanRejected() {
        CourseScheduleResponse hit = addSchedule("数学", "张老师", "A101", "周三 10:00-12:00");
        RoomOutageResponse outage = outageService.createOutage(
                outageRequest("EVT-1", "A101", "设备检修", "周三 10:00-12:00")).getResponse();
        Long taskId = outage.getRepairTaskId();

        // 方案把课程移到 B202 周四
        RepairPlanResponse plan = repairPlanService.submit(repairRequest("FIX-1", taskId,
                item(hit.getId(), "B202", "周四 08:00-10:00"))).getResponse();

        // 确认前 B202 周四恰好被另一个停用事件覆盖
        outageService.createOutage(outageRequest("EVT-2", "B202", "考试占用", "周四 08:00-10:00"));

        RescheduleConflictException ex = assertThrows(RescheduleConflictException.class,
                () -> repairPlanService.confirm(plan.getId(), action("李教务")));
        assertTrue(ex.getConflicts().stream()
                .anyMatch(c -> c.getConflictType() == RescheduleConflictDTO.ConflictType.OUTAGE_BLOCKED));

        // 整份方案未生效，任务仍待修复
        assertEquals("A101", findSchedule(hit.getId()).getClassroom());
        assertEquals(RepairTaskStatus.OPEN, outageService.getRepairTask(taskId).getStatus());
        assertEquals(ReschedulePlanStatus.PENDING, repairPlanService.getPlan(plan.getId()).getStatus());
    }

    @Test
    void testRepairConfirm_CourseVersionChangedByNormalReschedule_PlanRejected() {
        CourseScheduleResponse hit = addSchedule("数学", "张老师", "A101", "周三 10:00-12:00");
        Long taskId = outageService.createOutage(
                outageRequest("EVT-1", "A101", "设备检修", "周三 10:00-12:00"))
                .getResponse().getRepairTaskId();

        RepairPlanResponse repair = repairPlanService.submit(repairRequest("FIX-1", taskId,
                item(hit.getId(), "B202", "周四 08:00-10:00"))).getResponse();

        // 并发的普通调课先把课程移走了
        ReschedulePlanSubmitRequest normalRequest = new ReschedulePlanSubmitRequest("RES-1",
                java.util.Collections.singletonList(item(hit.getId(), "C303", "周五 08:00-10:00")));
        Long normalId = reschedulePlanService.submit(normalRequest).getResponse().getId();
        reschedulePlanService.confirm(normalId);

        RescheduleConflictException ex = assertThrows(RescheduleConflictException.class,
                () -> repairPlanService.confirm(repair.getId(), action("李教务")));
        assertTrue(ex.getConflicts().stream()
                .anyMatch(c -> c.getConflictType() == RescheduleConflictDTO.ConflictType.VERSION_CONFLICT
                        || c.getConflictType() == RescheduleConflictDTO.ConflictType.SCHEDULE_CHANGED));

        // 旧修复方案没有覆盖较新的安排：课程保留普通调课后的位置
        CourseScheduleResponse current = findSchedule(hit.getId());
        assertEquals("C303", current.getClassroom());
        assertEquals("周五 08:00-10:00", current.getTimeSlot());
        // 课程已由并发调课移出停用范围，任务自然消解
        assertEquals(RepairTaskStatus.RESOLVED, outageService.getRepairTask(taskId).getStatus());
    }

    @Test
    void testRepairConfirm_ConcurrentWithOutageAdjust_OnlyOneWins() throws Exception {
        CourseScheduleResponse hit = addSchedule("数学", "张老师", "A101", "周三 10:00-12:00");
        RoomOutageResponse outage = outageService.createOutage(
                outageRequest("EVT-1", "A101", "设备检修", "周三 10:00-12:00")).getResponse();
        Long taskId = outage.getRepairTaskId();

        RepairPlanResponse repair = repairPlanService.submit(repairRequest("FIX-1", taskId,
                item(hit.getId(), "A101", "周四 08:00-10:00"))).getResponse();

        RoomOutageAdjustRequest adjust = new RoomOutageAdjustRequest();
        // 范围扩大，恰好覆盖修复目标位置（同教室周四时段）
        adjust.setTimeSlots(Arrays.asList("周三 10:00-12:00", "周四 08:00-10:00"));
        adjust.setOperator("管理员");

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger repairOk = new AtomicInteger();
        AtomicInteger repairRejected = new AtomicInteger();

        executor.submit(() -> {
            ready.countDown();
            try {
                start.await();
                try {
                    repairPlanService.confirm(repair.getId(), action("李教务"));
                    repairOk.incrementAndGet();
                } catch (RescheduleConflictException e) {
                    repairRejected.incrementAndGet();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        executor.submit(() -> {
            ready.countDown();
            try {
                start.await();
                outageService.adjustOutage(outage.getId(), adjust);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        assertTrue(ready.await(10, TimeUnit.SECONDS));
        start.countDown();
        executor.shutdown();
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));

        CourseScheduleResponse current = findSchedule(hit.getId());
        if (repairOk.get() == 1) {
            // 修复先成功：课程移到 A101 周四；范围调整后该位置再次停用，课程被重新纳入任务
            assertEquals("A101", current.getClassroom());
            assertEquals("周四 08:00-10:00", current.getTimeSlot());
            assertEquals(RepairTaskStatus.OPEN, outageService.getRepairTask(taskId).getStatus());
            assertTrue(outageService.getRepairTask(taskId).getAffectedCourses().stream()
                    .anyMatch(c -> c.getScheduleId().equals(hit.getId()) && !c.isResolved()));
        } else {
            // 范围调整先成功：修复目标进入停用范围，确认必须失败，课程未被移动
            assertEquals(1, repairRejected.get());
            assertEquals("A101", current.getClassroom());
            assertEquals("周三 10:00-12:00", current.getTimeSlot());
        }
    }

    @Test
    void testRepairPlan_IdempotentAndStateRules() {
        CourseScheduleResponse hit = addSchedule("数学", "张老师", "A101", "周三 10:00-12:00");
        Long taskId = outageService.createOutage(
                outageRequest("EVT-1", "A101", "设备检修", "周三 10:00-12:00"))
                .getResponse().getRepairTaskId();

        RepairPlanSubmitRequest request = repairRequest("FIX-1", taskId,
                item(hit.getId(), "B202", "周四 08:00-10:00"));
        RepairPlanService.SubmitOutcome first = repairPlanService.submit(request);
        RepairPlanService.SubmitOutcome second = repairPlanService.submit(request);
        assertFalse(first.isReplayed());
        assertTrue(second.isReplayed());
        assertEquals(first.getResponse().getId(), second.getResponse().getId());

        assertThrows(com.coursescheduler.exception.RepairPlanStateException.class,
                () -> repairPlanService.submit(repairRequest("FIX-1", taskId,
                        item(hit.getId(), "C303", "周四 08:00-10:00"))));

        Long planId = first.getResponse().getId();
        repairPlanService.confirm(planId, action("李教务"));
        assertThrows(com.coursescheduler.exception.RepairPlanStateException.class,
                () -> repairPlanService.confirm(planId, action("李教务")));
        assertThrows(com.coursescheduler.exception.RepairPlanStateException.class,
                () -> repairPlanService.preCheck(planId));
    }

    @Test
    void testRepairSubmit_AfterTaskResolved_Throws() {
        CourseScheduleResponse hit = addSchedule("数学", "张老师", "A101", "周三 10:00-12:00");
        Long taskId = outageService.createOutage(
                outageRequest("EVT-1", "A101", "设备检修", "周三 10:00-12:00"))
                .getResponse().getRepairTaskId();
        Long planId = repairPlanService.submit(repairRequest("FIX-1", taskId,
                item(hit.getId(), "B202", "周四 08:00-10:00"))).getResponse().getId();
        repairPlanService.confirm(planId, action("李教务"));

        assertThrows(RepairTaskStateException.class,
                () -> repairPlanService.submit(repairRequest("FIX-2", taskId,
                        item(hit.getId(), "C303", "周五 08:00-10:00"))));
    }

    @Test
    void testNormalReschedule_CannotMoveCourseIntoBlockedSlot_CanMoveFrozenCourseOut() {
        CourseScheduleResponse frozen = addSchedule("数学", "张老师", "A101", "周三 10:00-12:00");
        CourseScheduleResponse other = addSchedule("英语", "钱老师", "C303", "周一 08:00-10:00");
        outageService.createOutage(
                outageRequest("EVT-1", "A101", "设备检修", "周三 10:00-12:00"));

        // 普通调课不能把其他课程移入停用时段
        ReschedulePlanSubmitRequest blocked = new ReschedulePlanSubmitRequest("RES-BLOCK",
                java.util.Collections.singletonList(item(other.getId(), "A101", "周三 11:00-12:00")));
        Long blockedId = reschedulePlanService.submit(blocked).getResponse().getId();
        RescheduleConflictException ex = assertThrows(RescheduleConflictException.class,
                () -> reschedulePlanService.confirm(blockedId));
        assertTrue(ex.getConflicts().stream()
                .anyMatch(c -> c.getConflictType() == RescheduleConflictDTO.ConflictType.OUTAGE_BLOCKED));
        assertEquals("C303", findSchedule(other.getId()).getClassroom());

        // 普通调课可以把被冻结的课程移出停用时段（随后修复任务自然消解）
        ReschedulePlanSubmitRequest moveOut = new ReschedulePlanSubmitRequest("RES-OUT",
                java.util.Collections.singletonList(item(frozen.getId(), "B202", "周四 08:00-10:00")));
        Long moveOutId = reschedulePlanService.submit(moveOut).getResponse().getId();
        reschedulePlanService.confirm(moveOutId);
        assertEquals("B202", findSchedule(frozen.getId()).getClassroom());
    }

    // ---------- 取消停用：不回退已完成修复 ----------

    @Test
    void testCancelOutage_DoesNotRollBackCompletedRepair() {
        CourseScheduleResponse hit = addSchedule("数学", "张老师", "A101", "周三 10:00-12:00");
        RoomOutageResponse outage = outageService.createOutage(
                outageRequest("EVT-1", "A101", "设备检修", "周三 10:00-12:00")).getResponse();
        Long taskId = outage.getRepairTaskId();

        Long planId = repairPlanService.submit(repairRequest("FIX-1", taskId,
                item(hit.getId(), "B202", "周四 08:00-10:00"))).getResponse().getId();
        repairPlanService.confirm(planId, action("李教务"));

        RoomOutageResponse cancelled = outageService.cancelOutage(outage.getId(), cancelAction("管理员"));
        assertEquals(RoomOutageStatus.CANCELLED, cancelled.getStatus());

        // 已完成的修复保留
        assertEquals("B202", findSchedule(hit.getId()).getClassroom());
        assertEquals(RepairTaskStatus.RESOLVED, outageService.getRepairTask(taskId).getStatus());

        // 停用取消后原教室时段恢复可排（新增课程不再被阻止）
        CourseScheduleResponse newCourse = addSchedule("物理", "李老师", "A101", "周三 10:00-12:00");
        assertNotNull(newCourse);

        // 停用事件、修复前后安排、处理人员记录均保留
        AuditLogFilterRequest filter = new AuditLogFilterRequest();
        filter.setOperationType(OperationType.ROOM_OUTAGE_CANCEL);
        List<AuditLogResponse> cancelLogs = auditLogService.queryLogs(filter);
        assertEquals(1, cancelLogs.size());
        assertEquals("管理员", cancelLogs.get(0).getOperator());
    }

    @Test
    void testCancelOutage_WithOpenTask_ClosesTaskKeepsCoursesInPlace() {
        CourseScheduleResponse hit = addSchedule("数学", "张老师", "A101", "周三 10:00-12:00");
        RoomOutageResponse outage = outageService.createOutage(
                outageRequest("EVT-1", "A101", "设备检修", "周三 10:00-12:00")).getResponse();

        outageService.cancelOutage(outage.getId(), null);

        assertEquals(RepairTaskStatus.CANCELLED,
                outageService.getRepairTask(outage.getRepairTaskId()).getStatus());
        assertEquals("A101", findSchedule(hit.getId()).getClassroom());
        assertThrows(RoomOutageConflictException.class,
                () -> outageService.cancelOutage(outage.getId(), null));
    }

    @Test
    void testRestoreOriginalSchedule_CreateNewNormalReschedulePlan() {
        // 停用取消后要恢复原排课：走新的普通调课方案
        CourseScheduleResponse hit = addSchedule("数学", "张老师", "A101", "周三 10:00-12:00");
        RoomOutageResponse outage = outageService.createOutage(
                outageRequest("EVT-1", "A101", "设备检修", "周三 10:00-12:00")).getResponse();
        Long taskId = outage.getRepairTaskId();
        Long repairId = repairPlanService.submit(repairRequest("FIX-1", taskId,
                item(hit.getId(), "B202", "周四 08:00-10:00"))).getResponse().getId();
        repairPlanService.confirm(repairId, action("李教务"));
        outageService.cancelOutage(outage.getId(), null);

        ReschedulePlanSubmitRequest restore = new ReschedulePlanSubmitRequest("RES-RESTORE-1",
                java.util.Collections.singletonList(item(hit.getId(), "A101", "周三 10:00-12:00")));
        Long restorePlanId = reschedulePlanService.submit(restore).getResponse().getId();
        reschedulePlanService.confirm(restorePlanId);

        CourseScheduleResponse restored = findSchedule(hit.getId());
        assertEquals("A101", restored.getClassroom());
        assertEquals("周三 10:00-12:00", restored.getTimeSlot());
    }

    // ---------- 查询：冲突明细与变更链 ----------

    @Test
    void testQueries_OutageImpactRepairPlanAndChangeChain() {
        CourseScheduleResponse hit = addSchedule("数学", "张老师", "A101", "周三 10:00-12:00");
        RoomOutageResponse outage = outageService.createOutage(
                outageRequest("EVT-1", "A101", "设备检修", "周三 10:00-12:00")).getResponse();
        Long taskId = outage.getRepairTaskId();

        RepairPlanResponse plan = repairPlanService.submit(repairRequest("FIX-1", taskId,
                item(hit.getId(), "B202", "周四 08:00-10:00"))).getResponse();
        // 先产生一次带冲突的预检
        repairPlanService.reject(plan.getId(), action("李教务"));
        RepairPlanResponse plan2 = repairPlanService.submit(repairRequest("FIX-2", taskId,
                item(hit.getId(), "B202", "周四 08:00-10:00"))).getResponse();
        repairPlanService.confirm(plan2.getId(), action("李教务"));

        // 停用影响范围
        RepairTaskResponse task = outageService.getRepairTask(taskId);
        assertEquals(1, task.getAffectedCount());
        assertEquals(1, task.getResolvedCount());
        assertEquals("EVT-1", task.getEventNo());

        // 修复方案明细
        List<RepairPlanResponse> plans = repairPlanService.listPlans();
        assertEquals(2, plans.size());
        assertEquals(ReschedulePlanStatus.REJECTED, plans.get(0).getStatus());
        assertEquals(ReschedulePlanStatus.CONFIRMED, plans.get(1).getStatus());

        // 课程变更链：创建 -> 停用冻结 -> 修复确认
        CourseChangeChainResponse chain = auditLogService.getChangeChain(hit.getId());
        assertEquals(hit.getId(), chain.getScheduleId());
        assertTrue(chain.getEvents().size() >= 3);
        assertTrue(chain.getEvents().stream()
                .anyMatch(e -> e.getOperationType() == OperationType.ROOM_OUTAGE_CREATE));
        assertTrue(chain.getEvents().stream()
                .anyMatch(e -> e.getOperationType() == OperationType.REPAIR_CONFIRM
                        && "FIX-2".equals(e.getRefNo())
                        && "李教务".equals(e.getOperator())));
    }
}
