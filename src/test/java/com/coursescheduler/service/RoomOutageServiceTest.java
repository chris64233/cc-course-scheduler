package com.coursescheduler.service;

import com.coursescheduler.dto.AuditLogFilterRequest;
import com.coursescheduler.dto.AuditLogResponse;
import com.coursescheduler.dto.CourseScheduleCreateRequest;
import com.coursescheduler.dto.CourseScheduleResponse;
import com.coursescheduler.dto.RepairPlanResponse;
import com.coursescheduler.dto.RepairPlanSubmitRequest;
import com.coursescheduler.dto.RepairTaskResponse;
import com.coursescheduler.dto.RescheduleConflictDTO;
import com.coursescheduler.dto.ReschedulePlanItemRequest;
import com.coursescheduler.dto.ReschedulePlanResponse;
import com.coursescheduler.dto.ReschedulePlanSubmitRequest;
import com.coursescheduler.dto.ReschedulePreCheckResponse;
import com.coursescheduler.dto.RoomOutageAdjustRequest;
import com.coursescheduler.dto.RoomOutageRequest;
import com.coursescheduler.dto.RoomOutageResponse;
import com.coursescheduler.exception.ClassroomConflictException;
import com.coursescheduler.exception.InvalidRequestParameterException;
import com.coursescheduler.exception.OutageConflictException;
import com.coursescheduler.exception.RepairStateException;
import com.coursescheduler.exception.RescheduleConflictException;
import com.coursescheduler.model.OperationType;
import com.coursescheduler.model.OutageStatus;
import com.coursescheduler.model.RepairTaskStatus;
import com.coursescheduler.model.ReschedulePlanStatus;
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

class RoomOutageServiceTest {

    private AuditLogService auditLogService;
    private CourseScheduleService scheduleService;
    private ReschedulePlanService planService;
    private RoomOutageService outageService;
    private final DomainLock domainLock = new DomainLock();

    @BeforeEach
    void setUp() {
        Clock clock = Clock.systemDefaultZone();
        auditLogService = new AuditLogService(clock);
        scheduleService = new CourseScheduleService(auditLogService, domainLock);
        outageService = new RoomOutageService(scheduleService, auditLogService, clock, domainLock);
        scheduleService.setOutageWindowProvider(outageService);
        planService = new ReschedulePlanService(scheduleService, clock, domainLock);
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
                .filter(s -> s.getId().equals(id)).findFirst().orElseThrow();
    }

    private RoomOutageRequest outageRequest(String eventNo, String classroom,
                                            String timeSlot, String reason, String operator) {
        RoomOutageRequest request = new RoomOutageRequest();
        request.setEventNo(eventNo);
        request.setClassroom(classroom);
        request.setTimeSlot(timeSlot);
        request.setReason(reason);
        request.setOperator(operator);
        return request;
    }

    private RoomOutageService.RegisterOutcome register(String eventNo, String classroom,
                                                       String timeSlot) {
        return outageService.register(
                outageRequest(eventNo, classroom, timeSlot, "设备检修", "管理员"));
    }

    private RepairPlanSubmitRequest repairRequest(String bizKey, ReschedulePlanItemRequest... items) {
        return new RepairPlanSubmitRequest(bizKey, "教务员", Arrays.asList(items));
    }

    private ReschedulePlanItemRequest item(Long scheduleId, String classroom, String timeSlot) {
        return new ReschedulePlanItemRequest(scheduleId, classroom, timeSlot);
    }

    // ----- 停用登记与冻结 -----

    @Test
    void testRegister_FreezesAllConflictingSchedules_AsRepairTask() {
        CourseScheduleResponse hit1 = addSchedule("数学", "张老师", "A101", "周三 10:00-12:00");
        CourseScheduleResponse hit2 = addSchedule("物理", "李老师", "A101", "周三 14:00-16:00");
        // 不冲突：另一间教室
        addSchedule("英语", "王老师", "B202", "周三 10:00-12:00");
        // 不冲突：同教室但时段不重叠
        addSchedule("化学", "赵老师", "A101", "周三 16:30-17:30");

        RoomOutageService.RegisterOutcome outcome = register("EVT-1", "A101", "周三 09:00-16:30");

        assertFalse(outcome.isReplayed());
        RoomOutageResponse outage = outcome.getResponse();
        assertEquals(OutageStatus.ACTIVE, outage.getStatus());
        assertEquals(1, outage.getTaskIds().size());

        RepairTaskResponse task = outageService.getTask(outage.getTaskIds().get(0));
        assertEquals(RepairTaskStatus.PENDING, task.getStatus());
        assertEquals(2, task.getAffectedCount());
        assertEquals(0, task.getResolvedCount());
        assertEquals(1, task.getVersion());
        assertTrue(task.getAffectedCourses().stream()
                .anyMatch(c -> c.getScheduleId().equals(hit1.getId())
                        && c.getOriginalClassroom().equals("A101")
                        && c.getFrozenRevision() == 1));
        assertTrue(task.getAffectedCourses().stream()
                .anyMatch(c -> c.getScheduleId().equals(hit2.getId())));
    }

    @Test
    void testRegister_NoConflictingSchedule_TaskImmediatelyRepaired() {
        addSchedule("化学", "赵老师", "A101", "周三 14:00-16:00");

        RoomOutageResponse outage = register("EVT-1", "A101", "周三 09:00-10:00").getResponse();

        RepairTaskResponse task = outageService.getTask(outage.getTaskIds().get(0));
        assertEquals(RepairTaskStatus.REPAIRED, task.getStatus());
        assertEquals(0, task.getAffectedCount());
    }

    @Test
    void testRegister_SameEventNoSameContent_Idempotent() {
        register("EVT-1", "A101", "周三 09:00-12:00");
        RoomOutageService.RegisterOutcome replay = register("EVT-1", "A101", "周三 09:00-12:00");

        assertTrue(replay.isReplayed());
        assertEquals(1, outageService.listOutages().size());
        assertEquals(1, outageService.listTasks(null).size());
    }

    @Test
    void testRegister_SameEventNoDifferentContent_Conflict() {
        register("EVT-1", "A101", "周三 09:00-12:00");
        assertThrows(OutageConflictException.class,
                () -> register("EVT-1", "A101", "周三 14:00-16:00"));
        assertThrows(OutageConflictException.class,
                () -> outageService.register(
                        outageRequest("EVT-1", "B202", "周三 09:00-12:00", "设备检修", "管理员")));
    }

    @Test
    void testRegister_MissingFields_Rejected() {
        assertThrows(InvalidRequestParameterException.class,
                () -> outageService.register(
                        outageRequest(" ", "A101", "周三 09:00-12:00", "原因", "管理员")));
        assertThrows(InvalidRequestParameterException.class,
                () -> outageService.register(
                        outageRequest("EVT-1", " ", "周三 09:00-12:00", "原因", "管理员")));
        assertThrows(InvalidRequestParameterException.class,
                () -> outageService.register(
                        outageRequest("EVT-1", "A101", "周三 09:00-12:00", "原因", " ")));
    }

    @Test
    void testRegister_NewScheduleCannotEnterOutageWindow() {
        register("EVT-1", "A101", "周三 09:00-12:00");

        // 完全落入停用时段
        assertThrows(ClassroomConflictException.class,
                () -> addSchedule("数学", "张老师", "A101", "周三 10:00-11:00"));
        // 部分重叠也不允许
        assertThrows(ClassroomConflictException.class,
                () -> addSchedule("数学", "张老师", "A101", "周三 11:30-12:30"));
        // 不同教室不受影响
        assertDoesNotThrow(() -> addSchedule("数学", "张老师", "B202", "周三 10:00-11:00"));
        // 同教室不重叠时段不受影响
        assertDoesNotThrow(() -> addSchedule("数学", "张老师", "A101", "周三 14:00-16:00"));
    }

    // ----- 修复方案：覆盖性与预检查 -----

    @Test
    void testSubmitRepairPlan_MustCoverAllAffectedCourses() {
        CourseScheduleResponse s1 = addSchedule("数学", "张老师", "A101", "周三 10:00-12:00");
        CourseScheduleResponse s2 = addSchedule("物理", "李老师", "A101", "周三 14:00-16:00");
        RepairTaskResponse task = firstTask(register("EVT-1", "A101", "周三 09:00-16:30"));

        InvalidRequestParameterException ex = assertThrows(InvalidRequestParameterException.class,
                () -> outageService.submitRepairPlan(task.getId(),
                        repairRequest("REP-1", item(s1.getId(), "B202", "周三 10:00-12:00"))));
        assertTrue(ex.getMessage().contains(String.valueOf(s2.getId())));
    }

    @Test
    void testPreCheck_MoveOutOfOutage_Succeeds() {
        CourseScheduleResponse s1 = addSchedule("数学", "张老师", "A101", "周三 10:00-12:00");
        RepairTaskResponse task = firstTask(register("EVT-1", "A101", "周三 09:00-16:30"));

        RepairPlanResponse plan = outageService.submitRepairPlan(task.getId(),
                repairRequest("REP-1", item(s1.getId(), "B202", "周三 10:00-12:00"))).getResponse();

        ReschedulePreCheckResponse preCheck = outageService.preCheckRepairPlan(plan.getId());
        assertTrue(preCheck.isCanReschedule());
        assertEquals(0, preCheck.getConflictCount());
    }

    @Test
    void testPreCheck_MoveIntoAnotherOutage_ReturnsRoomOutageConflict() {
        CourseScheduleResponse s1 = addSchedule("数学", "张老师", "A101", "周三 10:00-12:00");
        register("EVT-1", "A101", "周三 09:00-16:30");
        register("EVT-2", "B202", "周三 10:00-12:00");
        RepairTaskResponse task = outageService.listTasks("EVT-1").get(0);

        RepairPlanResponse plan = outageService.submitRepairPlan(task.getId(),
                repairRequest("REP-1", item(s1.getId(), "B202", "周三 10:00-12:00"))).getResponse();

        ReschedulePreCheckResponse preCheck = outageService.preCheckRepairPlan(plan.getId());
        assertFalse(preCheck.isCanReschedule());
        assertTrue(preCheck.getConflicts().stream()
                .anyMatch(c -> c.getConflictType() == RescheduleConflictDTO.ConflictType.ROOM_OUTAGE
                        && c.getScheduleId().equals(s1.getId())));
    }

    @Test
    void testPreCheck_TeacherAndClassroomConflict_PerCourseReasons() {
        CourseScheduleResponse s1 = addSchedule("数学", "张老师", "A101", "周三 10:00-12:00");
        firstTask(register("EVT-1", "A101", "周三 09:00-16:30"));
        // 张老师在目标时段已有课（教师冲突）
        addSchedule("英语", "张老师", "C303", "周四 08:00-10:00");
        // 目标教室在目标时段被占用（教室冲突）
        addSchedule("化学", "王老师", "B202", "周四 09:00-11:00");

        RepairTaskResponse task = outageService.listTasks("EVT-1").get(0);
        RepairPlanResponse plan = outageService.submitRepairPlan(task.getId(),
                repairRequest("REP-1", item(s1.getId(), "B202", "周四 09:00-10:00"))).getResponse();

        ReschedulePreCheckResponse preCheck = outageService.preCheckRepairPlan(plan.getId());
        assertFalse(preCheck.isCanReschedule());
        assertEquals(2, preCheck.getConflictCount());
        assertTrue(preCheck.getConflicts().stream()
                .anyMatch(c -> c.getConflictType() == RescheduleConflictDTO.ConflictType.TEACHER));
        assertTrue(preCheck.getConflicts().stream()
                .anyMatch(c -> c.getConflictType() == RescheduleConflictDTO.ConflictType.CLASSROOM));
    }

    @Test
    void testPreCheck_SwapResourcesBetweenTwoAffectedCourses_Succeeds() {
        // 两门受影响课分属不同老师，分别安排到空闲教室/时段
        CourseScheduleResponse s1 = addSchedule("数学", "张老师", "A101", "周三 10:00-11:00");
        CourseScheduleResponse s2 = addSchedule("物理", "李老师", "A101", "周三 14:00-15:00");
        RepairTaskResponse task = firstTask(register("EVT-1", "A101", "周三 09:00-16:30"));

        RepairPlanResponse plan = outageService.submitRepairPlan(task.getId(),
                repairRequest("REP-1",
                        item(s1.getId(), "B202", "周五 08:00-09:00"),
                        item(s2.getId(), "C303", "周五 08:00-09:00"))).getResponse();

        assertTrue(outageService.preCheckRepairPlan(plan.getId()).isCanReschedule());
    }

    // ----- 修复确认：原子性、版本、审计 -----

    @Test
    void testConfirm_AppliesAllChangesAtomically_AndRecordsAudit() {
        CourseScheduleResponse s1 = addSchedule("数学", "张老师", "A101", "周三 10:00-12:00");
        CourseScheduleResponse s2 = addSchedule("物理", "李老师", "A101", "周三 14:00-16:00");
        RepairTaskResponse task = firstTask(register("EVT-1", "A101", "周三 09:00-16:30"));

        RepairPlanResponse plan = outageService.submitRepairPlan(task.getId(),
                repairRequest("REP-1",
                        item(s1.getId(), "B202", "周三 10:00-12:00"),
                        item(s2.getId(), "C303", "周四 08:00-10:00"))).getResponse();

        RepairPlanResponse confirmed = outageService.confirmRepairPlan(plan.getId());
        assertEquals(ReschedulePlanStatus.CONFIRMED, confirmed.getStatus());
        assertNotNull(confirmed.getProcessedAt());

        assertEquals("B202", findSchedule(s1.getId()).getClassroom());
        assertEquals("C303", findSchedule(s2.getId()).getClassroom());

        RepairTaskResponse after = outageService.getTask(task.getId());
        assertEquals(RepairTaskStatus.REPAIRED, after.getStatus());
        assertEquals(2, after.getResolvedCount());

        AuditLogFilterRequest filter = new AuditLogFilterRequest();
        filter.setOperationType(OperationType.REPAIR_RESCHEDULE);
        filter.setSuccess(true);
        List<AuditLogResponse> logs = auditLogService.queryLogs(filter);
        assertEquals(2, logs.size());
        AuditLogResponse log1 = logs.stream().filter(l -> l.getCourseId().equals(s1.getId())).findFirst().orElseThrow();
        assertEquals("A101", log1.getPreviousClassroom());
        assertEquals("周三 10:00-12:00", log1.getPreviousTimeSlot());
        assertEquals("B202", log1.getClassroom());
        assertEquals("教务员", log1.getOperator());
        assertEquals("REP-1", log1.getReferenceNo());
    }

    @Test
    void testConfirm_OneCourseUnscheduleable_WholePlanNotApplied() {
        CourseScheduleResponse s1 = addSchedule("数学", "张老师", "A101", "周三 10:00-12:00");
        CourseScheduleResponse s2 = addSchedule("物理", "李老师", "A101", "周三 14:00-16:00");
        RepairTaskResponse task = firstTask(register("EVT-1", "A101", "周三 09:00-16:30"));
        // s1 的目标教室被占
        addSchedule("化学", "王老师", "B202", "周三 10:00-12:00");

        RepairPlanResponse plan = outageService.submitRepairPlan(task.getId(),
                repairRequest("REP-1",
                        item(s1.getId(), "B202", "周三 10:00-12:00"),
                        item(s2.getId(), "C303", "周四 08:00-10:00"))).getResponse();

        RescheduleConflictException ex = assertThrows(RescheduleConflictException.class,
                () -> outageService.confirmRepairPlan(plan.getId()));
        assertTrue(ex.getConflicts().stream()
                .anyMatch(c -> c.getConflictType() == RescheduleConflictDTO.ConflictType.CLASSROOM));

        // 原子性：两门课都保持原样
        assertEquals("A101", findSchedule(s1.getId()).getClassroom());
        assertEquals("A101", findSchedule(s2.getId()).getClassroom());
        assertEquals(RepairTaskStatus.PENDING, outageService.getTask(task.getId()).getStatus());
        assertEquals(ReschedulePlanStatus.PENDING, outageService.getRepairPlan(plan.getId()).getStatus());
    }

    @Test
    void testConfirm_CourseChangedByNormalRescheduleAfterSubmit_FailsWithRevisionConflict() {
        CourseScheduleResponse s1 = addSchedule("数学", "张老师", "A101", "周三 10:00-12:00");
        CourseScheduleResponse s2 = addSchedule("物理", "李老师", "A101", "周三 14:00-16:00");
        RepairTaskResponse task = firstTask(register("EVT-1", "A101", "周三 09:00-16:30"));

        RepairPlanResponse plan = outageService.submitRepairPlan(task.getId(),
                repairRequest("REP-1",
                        item(s1.getId(), "B202", "周三 10:00-12:00"),
                        item(s2.getId(), "C303", "周四 08:00-10:00"))).getResponse();

        // 提交修复方案后，普通调课先把 s1 移走
        ReschedulePlanResponse normal = planService.submit(new ReschedulePlanSubmitRequest(
                "NORMAL-1", Arrays.asList(item(s1.getId(), "D404", "周五 08:00-10:00")))).getResponse();
        planService.confirm(normal.getId());

        RescheduleConflictException ex = assertThrows(RescheduleConflictException.class,
                () -> outageService.confirmRepairPlan(plan.getId()));
        assertTrue(ex.getConflicts().stream().anyMatch(c ->
                c.getConflictType() == RescheduleConflictDTO.ConflictType.SCHEDULE_CHANGED
                        || c.getConflictType() == RescheduleConflictDTO.ConflictType.REVISION_CHANGED));
        // s2 未被部分修复
        assertEquals("A101", findSchedule(s2.getId()).getClassroom());
    }

    @Test
    void testConfirm_NormalRescheduleMovingCourseIntoOutage_Rejected() {
        CourseScheduleResponse s1 = addSchedule("数学", "张老师", "D404", "周五 08:00-10:00");
        register("EVT-1", "A101", "周三 09:00-16:30");

        ReschedulePlanResponse normal = planService.submit(new ReschedulePlanSubmitRequest(
                "NORMAL-1", Arrays.asList(item(s1.getId(), "A101", "周三 10:00-12:00")))).getResponse();

        RescheduleConflictException ex = assertThrows(RescheduleConflictException.class,
                () -> planService.confirm(normal.getId()));
        assertTrue(ex.getConflicts().stream()
                .anyMatch(c -> c.getConflictType() == RescheduleConflictDTO.ConflictType.ROOM_OUTAGE));
        assertEquals("D404", findSchedule(s1.getId()).getClassroom());
    }

    // ----- 停用范围调整 -----

    @Test
    void testAdjustRange_RefreezesTaskAndInvalidatesPendingPlan() {
        CourseScheduleResponse s1 = addSchedule("数学", "张老师", "A101", "周三 10:00-12:00");
        RepairTaskResponse task = firstTask(register("EVT-1", "A101", "周三 09:00-10:30"));

        RepairPlanResponse plan = outageService.submitRepairPlan(task.getId(),
                repairRequest("REP-1", item(s1.getId(), "B202", "周三 10:00-12:00"))).getResponse();

        // 停用范围扩大到下午，s1 的修复目标也被覆盖
        RoomOutageAdjustRequest adjust = new RoomOutageAdjustRequest();
        adjust.setTimeSlot("周三 09:00-16:30");
        adjust.setOperator("管理员");
        RoomOutageResponse updated = outageService.adjust("EVT-1", adjust);
        assertEquals("周三 09:00-16:30", updated.getTimeSlot());

        RepairTaskResponse refrozen = outageService.getTask(task.getId());
        assertEquals(2, refrozen.getVersion());
        assertEquals(RepairTaskStatus.PENDING, refrozen.getStatus());

        ReschedulePreCheckResponse preCheck = outageService.preCheckRepairPlan(plan.getId());
        assertFalse(preCheck.isCanReschedule());
        assertTrue(preCheck.getConflicts().stream()
                .anyMatch(c -> c.getConflictType() == RescheduleConflictDTO.ConflictType.TASK_VERSION_CHANGED));

        assertThrows(RescheduleConflictException.class,
                () -> outageService.confirmRepairPlan(plan.getId()));
    }

    @Test
    void testAdjustRange_NoActualChange_IsIdempotent() {
        register("EVT-1", "A101", "周三 09:00-16:30");
        RoomOutageAdjustRequest adjust = new RoomOutageAdjustRequest();
        adjust.setTimeSlot("周三 09:00-16:30");
        adjust.setReason("设备检修");
        adjust.setOperator("管理员");

        RoomOutageResponse response = outageService.adjust("EVT-1", adjust);
        assertEquals(1, outageService.getTask(response.getTaskIds().get(0)).getVersion());
    }

    @Test
    void testRepairConfirmedThenRangeExpanded_ReopensTaskAndOldPlanCannotReconfirm() {
        CourseScheduleResponse s1 = addSchedule("数学", "张老师", "A101", "周三 10:00-12:00");
        RepairTaskResponse task = firstTask(register("EVT-1", "A101", "周三 09:00-10:30"));

        RepairPlanResponse plan = outageService.submitRepairPlan(task.getId(),
                repairRequest("REP-1", item(s1.getId(), "B202", "周三 10:00-12:00"))).getResponse();
        outageService.confirmRepairPlan(plan.getId());
        assertEquals(RepairTaskStatus.REPAIRED, outageService.getTask(task.getId()).getStatus());

        // 范围扩大到覆盖修复后的安排 B202？停用教室仍是 A101，s1 已不在 A101——改为扩大 A101 时段，
        // s1 已移走，此时任务无受影响课程，保持已修复。
        RoomOutageAdjustRequest adjust = new RoomOutageAdjustRequest();
        adjust.setTimeSlot("周三 09:00-16:00");
        adjust.setOperator("管理员");
        outageService.adjust("EVT-1", adjust);
        assertEquals(RepairTaskStatus.REPAIRED, outageService.getTask(task.getId()).getStatus());

        // 已确认方案不可再次确认
        assertThrows(RepairStateException.class, () -> outageService.confirmRepairPlan(plan.getId()));
    }

    // ----- 停用取消 -----

    @Test
    void testCancel_DoesNotRollbackCompletedRepair_AndKeepsRecords() {
        CourseScheduleResponse s1 = addSchedule("数学", "张老师", "A101", "周三 10:00-12:00");
        RepairTaskResponse task = firstTask(register("EVT-1", "A101", "周三 09:00-16:30"));
        RepairPlanResponse plan = outageService.submitRepairPlan(task.getId(),
                repairRequest("REP-1", item(s1.getId(), "B202", "周三 10:00-12:00"))).getResponse();
        outageService.confirmRepairPlan(plan.getId());

        RoomOutageResponse cancelled = outageService.cancel("EVT-1", "管理员");
        assertEquals(OutageStatus.CANCELLED, cancelled.getStatus());
        assertNotNull(cancelled.getCancelledAt());

        // 已完成的修复不自动回退
        assertEquals("B202", findSchedule(s1.getId()).getClassroom());
        // 停用事件、修复方案记录完整保留
        assertEquals(RepairTaskStatus.REPAIRED, outageService.getTask(task.getId()).getStatus());
        assertEquals(ReschedulePlanStatus.CONFIRMED, outageService.getRepairPlan(plan.getId()).getStatus());

        // 取消后可以把课程排回原教室
        assertDoesNotThrow(() -> addSchedule("英语", "王老师", "A101", "周三 10:00-12:00"));

        // 取消幂等
        assertDoesNotThrow(() -> outageService.cancel("EVT-1", "管理员"));
    }

    @Test
    void testCancel_PendingTaskBecomesCancelledAndPlansCannotConfirm() {
        CourseScheduleResponse s1 = addSchedule("数学", "张老师", "A101", "周三 10:00-12:00");
        RepairTaskResponse task = firstTask(register("EVT-1", "A101", "周三 09:00-16:30"));
        RepairPlanResponse plan = outageService.submitRepairPlan(task.getId(),
                repairRequest("REP-1", item(s1.getId(), "B202", "周三 10:00-12:00"))).getResponse();

        outageService.cancel("EVT-1", "管理员");
        assertEquals(RepairTaskStatus.CANCELLED, outageService.getTask(task.getId()).getStatus());
        assertThrows(RepairStateException.class, () -> outageService.confirmRepairPlan(plan.getId()));
        assertThrows(OutageConflictException.class,
                () -> outageService.adjust("EVT-1", new RoomOutageAdjustRequest() {{
                    setTimeSlot("周四 08:00-10:00");
                    setOperator("管理员");
                }}));
    }

    @Test
    void testRestoreAfterCancel_CreatesNewNormalReschedulePlan() {
        CourseScheduleResponse s1 = addSchedule("数学", "张老师", "A101", "周三 10:00-12:00");
        RepairTaskResponse task = firstTask(register("EVT-1", "A101", "周三 09:00-16:30"));
        RepairPlanResponse repair = outageService.submitRepairPlan(task.getId(),
                repairRequest("REP-1", item(s1.getId(), "B202", "周三 10:00-12:00"))).getResponse();
        outageService.confirmRepairPlan(repair.getId());
        outageService.cancel("EVT-1", "管理员");

        // 需要恢复原排课时创建一份新的普通调课方案
        ReschedulePlanResponse restore = planService.submit(new ReschedulePlanSubmitRequest(
                "RESTORE-1", Arrays.asList(item(s1.getId(), "A101", "周三 10:00-12:00")))).getResponse();
        planService.confirm(restore.getId());

        assertEquals("A101", findSchedule(s1.getId()).getClassroom());
        // 变更链完整：修复 + 恢复均保留
        List<AuditLogResponse> chain = auditLogService.getCourseChangeChain(s1.getId());
        assertTrue(chain.stream().anyMatch(l -> l.getOperationType() == OperationType.REPAIR_RESCHEDULE));
        assertTrue(chain.stream().anyMatch(l -> l.getOperationType() == OperationType.RESCHEDULE));
    }

    // ----- 查询 -----

    @Test
    void testQueries_ImpactScope_Plans_Conflicts_ChangeChain() {
        CourseScheduleResponse s1 = addSchedule("数学", "张老师", "A101", "周三 10:00-12:00");
        RepairTaskResponse task = firstTask(register("EVT-1", "A101", "周三 09:00-16:30"));
        addSchedule("化学", "王老师", "B202", "周三 10:00-12:00");

        RepairPlanResponse plan = outageService.submitRepairPlan(task.getId(),
                repairRequest("REP-1", item(s1.getId(), "B202", "周三 10:00-12:00"))).getResponse();
        outageService.preCheckRepairPlan(plan.getId());

        // 冲突明细挂在方案上可查询
        RepairPlanResponse stored = outageService.getRepairPlan(plan.getId());
        assertTrue(stored.getConflictCount() >= 1);
        assertEquals("EVT-1", outageService.listRepairPlans("EVT-1").get(0).getEventNo());

        // 修复成功后变更链包含停用事件与修复记录
        RepairPlanResponse plan2 = outageService.submitRepairPlan(task.getId(),
                repairRequest("REP-2", item(s1.getId(), "C303", "周四 08:00-10:00"))).getResponse();
        outageService.confirmRepairPlan(plan2.getId());

        List<AuditLogResponse> chain = outageService == null ? null
                : auditLogService.getCourseChangeChain(s1.getId());
        assertNotNull(chain);
        assertTrue(chain.stream().anyMatch(l -> l.getOperationType() == OperationType.REPAIR_RESCHEDULE
                && "REP-2".equals(l.getReferenceNo())));
    }

    // ----- 并发：普通调课与修复确认 -----

    @Test
    void testConcurrent_NormalRescheduleAndRepair_AtMostOneMovesSameCourse() throws Exception {
        CourseScheduleResponse s1 = addSchedule("数学", "张老师", "A101", "周三 10:00-12:00");
        RepairTaskResponse task = firstTask(register("EVT-1", "A101", "周三 09:00-16:30"));

        RepairPlanResponse repair = outageService.submitRepairPlan(task.getId(),
                repairRequest("REP-1", item(s1.getId(), "B202", "周三 10:00-12:00"))).getResponse();
        ReschedulePlanResponse normal = planService.submit(new ReschedulePlanSubmitRequest(
                "NORMAL-1", Arrays.asList(item(s1.getId(), "C303", "周四 08:00-10:00")))).getResponse();

        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger failure = new AtomicInteger();

        executor.submit(() -> {
            ready.countDown();
            try {
                start.await();
                outageService.confirmRepairPlan(repair.getId());
                success.incrementAndGet();
            } catch (RescheduleConflictException | RepairStateException e) {
                failure.incrementAndGet();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        executor.submit(() -> {
            ready.countDown();
            try {
                start.await();
                planService.confirm(normal.getId());
                success.incrementAndGet();
            } catch (RescheduleConflictException | com.coursescheduler.exception.ReschedulePlanStateException e) {
                failure.incrementAndGet();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        assertTrue(ready.await(10, TimeUnit.SECONDS));
        start.countDown();
        executor.shutdown();
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));

        assertEquals(1, success.get());
        assertEquals(1, failure.get());
        // 课程只被移动一次：最终位置是两个目标之一，而非中间态
        CourseScheduleResponse finalSchedule = findSchedule(s1.getId());
        boolean movedByRepair = "B202".equals(finalSchedule.getClassroom());
        boolean movedByNormal = "C303".equals(finalSchedule.getClassroom());
        assertTrue(movedByRepair ^ movedByNormal);
    }

    private RepairTaskResponse firstTask(RoomOutageService.RegisterOutcome outcome) {
        Long taskId = outcome.getResponse().getTaskIds().get(0);
        return outageService.getTask(taskId);
    }
}
