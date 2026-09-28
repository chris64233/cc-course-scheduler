package com.coursescheduler.service;

import com.coursescheduler.dto.AffectedCourseDTO;
import com.coursescheduler.dto.RepairPlanResponse;
import com.coursescheduler.dto.RepairPlanSubmitRequest;
import com.coursescheduler.dto.RepairTaskResponse;
import com.coursescheduler.dto.RescheduleConflictDTO;
import com.coursescheduler.dto.ReschedulePlanItemRequest;
import com.coursescheduler.dto.ReschedulePlanItemResponse;
import com.coursescheduler.dto.ReschedulePreCheckResponse;
import com.coursescheduler.dto.RoomOutageAdjustRequest;
import com.coursescheduler.dto.RoomOutageRequest;
import com.coursescheduler.dto.RoomOutageResponse;
import com.coursescheduler.exception.InvalidRequestParameterException;
import com.coursescheduler.exception.OutageConflictException;
import com.coursescheduler.exception.OutageNotFoundException;
import com.coursescheduler.exception.RepairStateException;
import com.coursescheduler.exception.RescheduleConflictException;
import com.coursescheduler.exception.ScheduleNotFoundException;
import com.coursescheduler.model.AffectedCourse;
import com.coursescheduler.model.CourseSchedule;
import com.coursescheduler.model.OperationType;
import com.coursescheduler.model.OutageStatus;
import com.coursescheduler.model.RepairPlan;
import com.coursescheduler.model.RepairTask;
import com.coursescheduler.model.RepairTaskStatus;
import com.coursescheduler.model.ReschedulePlanItem;
import com.coursescheduler.model.ReschedulePlanStatus;
import com.coursescheduler.model.RoomOutage;
import com.coursescheduler.util.TimeSlotUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.stream.Collectors;

/**
 * 教室临时停用与成组修复排课服务。
 *
 * <p>与课程安排服务、普通调课服务共用 {@link DomainLock}：所有停用/修复/课程写操作
 * 全局串行。停用登记或范围调整时在同一临界区内冻结受影响课程（含课程版本快照），
 * 因此新增课程不可能在停用生效后排入停用时段，旧修复方案也无法覆盖范围调整后的状态。
 */
@Service
public class RoomOutageService implements OutageWindowProvider {

    private final CourseScheduleService courseScheduleService;
    private final AuditLogService auditLogService;
    private final Clock clock;
    private final ReadWriteLock rwLock;

    private final Map<String, RoomOutage> outagesByEventNo = new LinkedHashMap<>();
    private final Map<Long, RoomOutage> outagesById = new LinkedHashMap<>();
    private final Map<Long, RepairTask> tasksById = new LinkedHashMap<>();
    private final Map<Long, RepairPlan> plansById = new LinkedHashMap<>();
    private final Map<String, RepairPlan> plansByBizKey = new LinkedHashMap<>();
    private long outageIdGenerator = 1;
    private long taskIdGenerator = 1;
    private long planIdGenerator = 1;

    @Autowired
    public RoomOutageService(@Lazy CourseScheduleService courseScheduleService,
                             AuditLogService auditLogService,
                             Clock clock,
                             DomainLock domainLock) {
        this.courseScheduleService = courseScheduleService;
        this.auditLogService = auditLogService;
        this.clock = clock;
        this.rwLock = domainLock.getLock();
    }

    /** 登记结果：replayed=true 表示命中外部事件号幂等，返回既有事件。 */
    public static class RegisterOutcome {
        private final RoomOutageResponse response;
        private final boolean replayed;

        RegisterOutcome(RoomOutageResponse response, boolean replayed) {
            this.response = response;
            this.replayed = replayed;
        }

        public RoomOutageResponse getResponse() {
            return response;
        }

        public boolean isReplayed() {
            return replayed;
        }
    }

    /** 修复方案提交结果：replayed=true 表示命中修复业务号幂等。 */
    public static class SubmitPlanOutcome {
        private final RepairPlanResponse response;
        private final boolean replayed;

        SubmitPlanOutcome(RepairPlanResponse response, boolean replayed) {
            this.response = response;
            this.replayed = replayed;
        }

        public RepairPlanResponse getResponse() {
            return response;
        }

        public boolean isReplayed() {
            return replayed;
        }
    }

    // ---------------------------------------------------------------------
    // 停用事件：登记 / 范围调整 / 取消
    // ---------------------------------------------------------------------

    public RegisterOutcome register(RoomOutageRequest request) {
        if (request == null) {
            throw new InvalidRequestParameterException("停用事件不能为空");
        }
        String eventNo = normalizeRequired(request.getEventNo(), "外部事件号");
        String classroom = normalizeRequired(request.getClassroom(), "教室名");
        if (request.getTimeSlot() == null || request.getTimeSlot().trim().isEmpty()) {
            throw new InvalidRequestParameterException("停用时间段不能为空");
        }
        String timeSlot = TimeSlotUtils.normalize(request.getTimeSlot());
        String reason = request.getReason() == null ? "" : request.getReason().trim();
        String operator = normalizeRequired(request.getOperator(), "处理人员");

        String fingerprint = fingerprint(classroom, timeSlot, reason);

        rwLock.writeLock().lock();
        try {
            RoomOutage existing = outagesByEventNo.get(eventNo);
            if (existing != null) {
                if (existing.getStatus() == OutageStatus.CANCELLED) {
                    throw new OutageConflictException(
                            "外部事件号 " + eventNo + " 的停用事件已取消，不能重复登记");
                }
                if (!existing.getFingerprint().equals(fingerprint)) {
                    throw new OutageConflictException(
                            "相同外部事件号已登记不同内容的停用事件，事件号: " + eventNo);
                }
                return new RegisterOutcome(toOutageResponse(existing), true);
            }

            LocalDateTime now = LocalDateTime.now(clock);
            RoomOutage outage = new RoomOutage();
            outage.setId(outageIdGenerator++);
            outage.setEventNo(eventNo);
            outage.setFingerprint(fingerprint);
            outage.setClassroom(classroom);
            outage.setTimeSlot(timeSlot);
            outage.setReason(reason);
            outage.setStatus(OutageStatus.ACTIVE);
            outage.setOperator(operator);
            outage.setCreatedAt(now);
            outage.setUpdatedAt(now);

            RepairTask task = freezeNewTask(outage, now);
            outage.getTaskIds().add(task.getId());
            outagesByEventNo.put(eventNo, outage);
            outagesById.put(outage.getId(), outage);

            auditLogService.recordLog(OperationType.OUTAGE_REGISTER, null,
                    "教室停用:" + classroom, null, classroom, timeSlot,
                    true, null, operator, eventNo);

            return new RegisterOutcome(toOutageResponse(outage), false);
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    public RoomOutageResponse adjust(String eventNo, RoomOutageAdjustRequest request) {
        String normalizedEventNo = normalizeRequired(eventNo, "外部事件号");
        if (request == null) {
            throw new InvalidRequestParameterException("停用范围调整请求不能为空");
        }
        if (request.getTimeSlot() == null || request.getTimeSlot().trim().isEmpty()) {
            throw new InvalidRequestParameterException("停用时间段不能为空");
        }
        String newTimeSlot = TimeSlotUtils.normalize(request.getTimeSlot());
        String newClassroom = request.getClassroom() == null || request.getClassroom().trim().isEmpty()
                ? null : request.getClassroom().trim();
        String newReason = request.getReason() == null || request.getReason().trim().isEmpty()
                ? null : request.getReason().trim();
        String operator = normalizeRequired(request.getOperator(), "处理人员");

        rwLock.writeLock().lock();
        try {
            RoomOutage outage = findActiveOutage(normalizedEventNo);
            String classroom = newClassroom != null ? newClassroom : outage.getClassroom();
            String reason = newReason != null ? newReason : outage.getReason();
            String fingerprint = fingerprint(classroom, newTimeSlot, reason);
            if (fingerprint.equals(outage.getFingerprint())) {
                // 范围没有实际变化，幂等返回当前事件
                return toOutageResponse(outage);
            }

            outage.setClassroom(classroom);
            outage.setTimeSlot(newTimeSlot);
            outage.setReason(reason);
            outage.setFingerprint(fingerprint);
            outage.setUpdatedAt(LocalDateTime.now(clock));

            // 在同一任务上重新冻结受影响课程并递增版本，此前提交的待处理修复方案随之过期
            RepairTask task = latestTask(outage);
            refreezeTask(task, outage, LocalDateTime.now(clock));

            auditLogService.recordLog(OperationType.OUTAGE_UPDATE, null,
                    "教室停用调整:" + classroom, null, classroom, newTimeSlot,
                    true, null, operator, normalizedEventNo);

            return toOutageResponse(outage);
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    public RoomOutageResponse cancel(String eventNo, String operator) {
        String normalizedEventNo = normalizeRequired(eventNo, "外部事件号");
        String normalizedOperator = normalizeRequired(operator, "处理人员");

        rwLock.writeLock().lock();
        try {
            RoomOutage outage = findOutage(normalizedEventNo);
            if (outage.getStatus() == OutageStatus.CANCELLED) {
                // 取消幂等：重复取消返回当前状态
                return toOutageResponse(outage);
            }

            LocalDateTime now = LocalDateTime.now(clock);
            outage.setStatus(OutageStatus.CANCELLED);
            outage.setCancelledAt(now);
            outage.setUpdatedAt(now);

            // 未完成的修复任务随之取消；已完成的修复保留，不自动回退
            for (Long taskId : outage.getTaskIds()) {
                RepairTask task = tasksById.get(taskId);
                if (task != null && task.getStatus() == RepairTaskStatus.PENDING) {
                    task.setStatus(RepairTaskStatus.CANCELLED);
                    task.setCancelledAt(now);
                }
            }

            auditLogService.recordLog(OperationType.OUTAGE_CANCEL, null,
                    "教室停用取消:" + outage.getClassroom(), null,
                    outage.getClassroom(), outage.getTimeSlot(),
                    true, null, normalizedOperator, normalizedEventNo);

            return toOutageResponse(outage);
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * 停用登记时冻结一份新的修复任务。必须在持有域写锁时调用。
     */
    private RepairTask freezeNewTask(RoomOutage outage, LocalDateTime now) {
        RepairTask task = new RepairTask();
        task.setId(taskIdGenerator++);
        task.setOutageId(outage.getId());
        task.setEventNo(outage.getEventNo());
        task.setVersion(1);
        task.setCreatedAt(now);
        populateTaskFromOutage(task, outage, now);
        tasksById.put(task.getId(), task);
        return task;
    }

    /**
     * 停用范围调整时在原任务上重新冻结：更新停用范围快照、重算受影响课程、递增版本。
     * 已经确认的修复方案记录仍保留；在新范围下重新出现冲突的课程（包括曾修复但再次
     * 落入新范围的课程）重新成为待修复课程。任务可能从已修复回到待修复。
     * 必须在持有域写锁时调用。
     */
    private void refreezeTask(RepairTask task, RoomOutage outage, LocalDateTime now) {
        task.setVersion(task.getVersion() + 1);
        populateTaskFromOutage(task, outage, now);
    }

    /**
     * 按停用事件当前内容重建任务的受影响课程冻结集合（全部以 ACTIVE 状态冻结）。
     * 必须在持有域写锁时调用。
     */
    private void populateTaskFromOutage(RepairTask task, RoomOutage outage, LocalDateTime now) {
        task.setClassroom(outage.getClassroom());
        task.setTimeSlot(outage.getTimeSlot());
        task.setReason(outage.getReason());

        List<CourseSchedule> snapshot = courseScheduleService.snapshotAllSchedules();
        List<AffectedCourse> affected = new ArrayList<>();
        for (CourseSchedule s : snapshot) {
            if (s.getClassroom().equals(outage.getClassroom())
                    && ScheduleConflictSupport.timeSlotsOverlap(s.getTimeSlot(), outage.getTimeSlot())) {
                AffectedCourse course = new AffectedCourse(
                        s.getId(), s.getCourseName(), s.getTeacherName(),
                        s.getClassroom(), s.getTimeSlot(), s.getRevision());
                course.setState(AffectedCourse.State.ACTIVE);
                affected.add(course);
            }
        }
        task.setAffectedCourses(affected);
        task.setResolvedScheduleIds(new ArrayList<>());

        if (affected.isEmpty()) {
            task.setStatus(RepairTaskStatus.REPAIRED);
            task.setProcessedAt(now);
        } else {
            task.setStatus(RepairTaskStatus.PENDING);
            task.setProcessedAt(null);
        }
        task.setCancelledAt(null);
    }

    private RepairTask latestTask(RoomOutage outage) {
        List<Long> taskIds = outage.getTaskIds();
        RepairTask task = tasksById.get(taskIds.get(taskIds.size() - 1));
        if (task == null) {
            throw new IllegalStateException("停用事件缺少修复任务，事件号: " + outage.getEventNo());
        }
        return task;
    }

    // ---------------------------------------------------------------------
    // OutageWindowProvider：在持有域锁时被课程服务调用
    // ---------------------------------------------------------------------

    @Override
    public List<OutageWindow> activeWindows() {
        List<OutageWindow> windows = new ArrayList<>();
        for (RoomOutage outage : outagesByEventNo.values()) {
            if (outage.getStatus() == OutageStatus.ACTIVE) {
                windows.add(new OutageWindow(
                        outage.getEventNo(), outage.getClassroom(),
                        outage.getTimeSlot(), outage.getReason()));
            }
        }
        return windows;
    }

    // ---------------------------------------------------------------------
    // 修复方案：提交 / 预检查 / 确认 / 拒绝
    // ---------------------------------------------------------------------

    public SubmitPlanOutcome submitRepairPlan(Long taskId, RepairPlanSubmitRequest request) {
        if (request == null) {
            throw new InvalidRequestParameterException("修复方案不能为空");
        }
        String bizKey = normalizeRequired(request.getBizKey(), "修复业务号");
        String operator = normalizeRequired(request.getOperator(), "处理人员");
        List<ReschedulePlanItemRequest> itemRequests = request.getItems();
        if (itemRequests == null || itemRequests.isEmpty()) {
            throw new InvalidRequestParameterException("修复方案至少包含一条课程调整");
        }

        List<String> normalizedClassrooms = new ArrayList<>();
        List<String> normalizedTimeSlots = new ArrayList<>();
        Set<Long> seenScheduleIds = new LinkedHashSet<>();
        for (int i = 0; i < itemRequests.size(); i++) {
            ReschedulePlanItemRequest item = itemRequests.get(i);
            if (item == null) {
                throw new InvalidRequestParameterException("第 " + (i + 1) + " 条课程调整不能为空");
            }
            if (item.getScheduleId() == null || item.getScheduleId() <= 0) {
                throw new InvalidRequestParameterException("第 " + (i + 1) + " 条课程调整的课程 ID 不合法");
            }
            if (!seenScheduleIds.add(item.getScheduleId())) {
                throw new InvalidRequestParameterException(
                        "修复方案引用的课程安排重复，ID: " + item.getScheduleId());
            }
            if (item.getNewClassroom() == null || item.getNewClassroom().trim().isEmpty()) {
                throw new InvalidRequestParameterException("第 " + (i + 1) + " 条课程调整的新教室不能为空");
            }
            if (item.getNewTimeSlot() == null || item.getNewTimeSlot().trim().isEmpty()) {
                throw new InvalidRequestParameterException("第 " + (i + 1) + " 条课程调整的新时间段不能为空");
            }
            normalizedClassrooms.add(item.getNewClassroom().trim());
            normalizedTimeSlots.add(TimeSlotUtils.normalize(item.getNewTimeSlot()));
        }
        String fingerprint = buildFingerprint(itemRequests, normalizedClassrooms, normalizedTimeSlots);

        rwLock.writeLock().lock();
        try {
            RepairTask task = findTask(taskId);
            ensureTaskPending(task, "提交修复方案");

            RepairPlan existingByBiz = plansByBizKey.get(bizKey);
            if (existingByBiz != null) {
                if (!existingByBiz.getFingerprint().equals(fingerprint)
                        || !existingByBiz.getTaskId().equals(taskId)) {
                    throw new RepairStateException(
                            "相同修复业务号已提交过不同内容的修复方案，业务号: " + bizKey);
                }
                return new SubmitPlanOutcome(toPlanResponse(existingByBiz), true);
            }

            // 提交前先对账：若课程已被删除/移出停用范围，则从任务中移除；若仍冲突但未覆盖则拒绝
            reconcileTask(task);
            ensureTaskPending(task, "提交修复方案");

            Set<Long> requiredIds = currentRequiredScheduleIds(task);
            Map<Long, CourseSchedule> schedulesById = loadSchedulesByIds(seenScheduleIds);
            for (Long id : seenScheduleIds) {
                if (!schedulesById.containsKey(id)) {
                    throw new ScheduleNotFoundException("课程安排不存在，ID: " + id);
                }
            }
            for (Long requiredId : requiredIds) {
                if (!seenScheduleIds.contains(requiredId)) {
                    throw new InvalidRequestParameterException(
                            "修复方案必须覆盖任务中全部受影响课程，缺少课程安排 ID: " + requiredId);
                }
            }

            List<ReschedulePlanItem> items = new ArrayList<>();
            Map<Long, Long> submitRevisions = new LinkedHashMap<>();
            for (int i = 0; i < itemRequests.size(); i++) {
                Long scheduleId = itemRequests.get(i).getScheduleId();
                CourseSchedule current = schedulesById.get(scheduleId);
                items.add(new ReschedulePlanItem(
                        scheduleId,
                        current.getCourseName(),
                        current.getTeacherName(),
                        current.getClassroom(),
                        current.getTimeSlot(),
                        normalizedClassrooms.get(i),
                        normalizedTimeSlots.get(i)));
                submitRevisions.put(scheduleId, current.getRevision());
            }

            RepairPlan plan = new RepairPlan();
            plan.setId(planIdGenerator++);
            plan.setTaskId(task.getId());
            plan.setEventNo(task.getEventNo());
            plan.setBizKey(bizKey);
            plan.setFingerprint(fingerprint);
            plan.setStatus(ReschedulePlanStatus.PENDING);
            plan.setTaskVersion(task.getVersion());
            plan.setItems(items);
            plan.setTargetScheduleIds(new LinkedHashSet<>(seenScheduleIds));
            plan.setSubmitRevisions(submitRevisions);
            plan.setOperator(operator);
            plan.setCreatedAt(LocalDateTime.now(clock));
            plansById.put(plan.getId(), plan);
            plansByBizKey.put(bizKey, plan);
            task.getPlanIds().add(plan.getId());

            return new SubmitPlanOutcome(toPlanResponse(plan), false);
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    public ReschedulePreCheckResponse preCheckRepairPlan(Long planId) {
        rwLock.writeLock().lock();
        try {
            RepairPlan plan = findPlan(planId);
            ensurePlanPending(plan, "预检查");
            RepairTask task = findTask(plan.getTaskId());
            ensureTaskPending(task, "修复方案预检查");

            reconcileTask(task);
            ensureTaskPending(task, "修复方案预检查");

            List<RescheduleConflictDTO> conflicts =
                    validateRepairPlan(task, plan, courseScheduleService.snapshotAllSchedules());
            plan.setConflicts(conflicts);
            plan.setResultMessage(conflicts.isEmpty()
                    ? "预检查通过，修复方案可以确认"
                    : "预检查发现 " + conflicts.size() + " 项冲突");
            return new ReschedulePreCheckResponse(plan.getId(), conflicts.isEmpty(), conflicts.size(), conflicts);
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    public RepairPlanResponse confirmRepairPlan(Long planId) {
        rwLock.writeLock().lock();
        try {
            RepairPlan plan = findPlan(planId);
            ensurePlanPending(plan, "确认");
            RepairTask task = findTask(plan.getTaskId());
            ensureTaskPending(task, "确认修复方案");

            // 确认前再次对账受影响课程集合
            reconcileTask(task);
            ensureTaskPending(task, "确认修复方案");

            List<CourseSchedule> snapshot = courseScheduleService.snapshotAllSchedules();
            List<RescheduleConflictDTO> conflicts = validateRepairPlan(task, plan, snapshot);
            if (!conflicts.isEmpty()) {
                plan.setConflicts(conflicts);
                plan.setResultMessage("修复失败：存在 " + conflicts.size() + " 项冲突，整份修复方案未生效");
                throw new RescheduleConflictException(
                        "停用修复方案存在冲突，整份方案未生效", conflicts);
            }

            // 重新检查课程版本、教师时间和目标教室停用状态，并在一次事务（域写锁临界区）中应用
            List<RescheduleConflictDTO> applyConflicts =
                    courseScheduleService.applyRepairAtomicallyWhileLocked(
                            plan.getItems(), activeWindows(),
                            plan.getOperator(), plan.getBizKey());
            if (!applyConflicts.isEmpty()) {
                plan.setConflicts(applyConflicts);
                plan.setResultMessage(
                        "修复失败：存在 " + applyConflicts.size() + " 项冲突，整份修复方案未生效");
                throw new RescheduleConflictException(
                        "停用修复方案存在冲突，整份方案未生效", applyConflicts);
            }

            // 标记本方案覆盖的待修复课程为已修复（冻结记录保留）
            Set<Long> plannedIds = new LinkedHashSet<>();
            for (ReschedulePlanItem item : plan.getItems()) {
                plannedIds.add(item.getScheduleId());
                if (!task.getResolvedScheduleIds().contains(item.getScheduleId())) {
                    task.getResolvedScheduleIds().add(item.getScheduleId());
                }
            }
            for (AffectedCourse affected : task.getAffectedCourses()) {
                if (affected.getState() == AffectedCourse.State.ACTIVE
                        && plannedIds.contains(affected.getScheduleId())) {
                    affected.setState(AffectedCourse.State.RESOLVED);
                }
            }

            LocalDateTime now = LocalDateTime.now(clock);
            if (currentRequiredScheduleIds(task).isEmpty()) {
                task.setStatus(RepairTaskStatus.REPAIRED);
                task.setProcessedAt(now);
            }

            plan.setStatus(ReschedulePlanStatus.CONFIRMED);
            plan.setConflicts(new ArrayList<>());
            plan.setResultMessage("修复成功，全部 " + plan.getItems().size() + " 条课程调整已生效");
            plan.setProcessedAt(now);
            return toPlanResponse(plan);
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    public RepairPlanResponse rejectRepairPlan(Long planId) {
        rwLock.writeLock().lock();
        try {
            RepairPlan plan = findPlan(planId);
            ensurePlanPending(plan, "拒绝");
            plan.setStatus(ReschedulePlanStatus.REJECTED);
            plan.setResultMessage("停用修复方案已拒绝");
            plan.setProcessedAt(LocalDateTime.now(clock));
            return toPlanResponse(plan);
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * 校验修复方案：任务版本、课程覆盖、课程版本、教师/教室冲突、停用范围、方案内部冲突。
     * 必须在持有域写锁时调用。
     */
    private List<RescheduleConflictDTO> validateRepairPlan(
            RepairTask task, RepairPlan plan, List<CourseSchedule> snapshot) {
        List<RescheduleConflictDTO> conflicts = new ArrayList<>();

        if (plan.getTaskVersion() != task.getVersion()) {
            conflicts.add(new RescheduleConflictDTO(
                    null, RescheduleConflictDTO.ConflictType.TASK_VERSION_CHANGED,
                    null, null,
                    "停用范围已调整，修复任务版本从 " + plan.getTaskVersion()
                            + " 变为 " + task.getVersion() + "，请基于最新任务重新制定修复方案"));
        }

        Map<Long, CourseSchedule> schedulesById = new HashMap<>();
        for (CourseSchedule s : snapshot) {
            schedulesById.put(s.getId(), s);
        }

        Set<Long> requiredIds = currentRequiredScheduleIds(task);
        Set<Long> plannedIds = new LinkedHashSet<>(plan.getTargetScheduleIds());

        // 缺少任何一门受影响课程的安排都不能确认，逐门给出原因
        for (Long requiredId : requiredIds) {
            if (!plannedIds.contains(requiredId)) {
                AffectedCourse affected = findAffected(task, requiredId);
                conflicts.add(new RescheduleConflictDTO(
                        requiredId, RescheduleConflictDTO.ConflictType.MISSING_COVERAGE,
                        null, affected != null ? affected.getCourseName() : null,
                        "受影响课程缺少修复安排（必须覆盖任务中全部受影响课程），ID: " + requiredId));
            }
        }

        // 用一份临时 item 列表复用统一冲突规则（教师/教室/停用/内部冲突）
        List<ReschedulePlanItem> liveItems = new ArrayList<>();
        for (ReschedulePlanItem item : plan.getItems()) {
            CourseSchedule current = schedulesById.get(item.getScheduleId());
            if (current == null) {
                conflicts.add(new RescheduleConflictDTO(
                        item.getScheduleId(),
                        RescheduleConflictDTO.ConflictType.SCHEDULE_NOT_FOUND,
                        null, null,
                        "课程安排已被删除，ID: " + item.getScheduleId()));
                continue;
            }
            Long submitRevision = plan.getSubmitRevisions().get(item.getScheduleId());
            if (submitRevision != null && current.getRevision() != submitRevision) {
                conflicts.add(new RescheduleConflictDTO(
                        item.getScheduleId(),
                        RescheduleConflictDTO.ConflictType.REVISION_CHANGED,
                        null, null,
                        "课程安排版本已变化（提交修复方案后被其他调课修改），ID: "
                                + item.getScheduleId()
                                + "，提交时版本 " + submitRevision + "，当前版本 " + current.getRevision()));
                continue;
            }
            liveItems.add(new ReschedulePlanItem(
                    current.getId(), current.getCourseName(), current.getTeacherName(),
                    current.getClassroom(), current.getTimeSlot(),
                    item.getNewClassroom(), item.getNewTimeSlot()));
        }

        conflicts.addAll(RescheduleConflictSupport.computeConflicts(
                snapshot, liveItems, activeWindows()));
        return conflicts;
    }

    // ---------------------------------------------------------------------
    // 查询：停用事件 / 任务影响范围 / 修复方案
    // ---------------------------------------------------------------------

    public RoomOutageResponse getOutage(Long id) {
        rwLock.readLock().lock();
        try {
            RoomOutage outage = outagesById.get(id);
            if (outage == null) {
                throw new OutageNotFoundException("教室停用事件不存在，ID: " + id);
            }
            return toOutageResponse(outage);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public RoomOutageResponse getOutageByEventNo(String eventNo) {
        String normalizedEventNo = normalizeRequired(eventNo, "外部事件号");
        rwLock.readLock().lock();
        try {
            return toOutageResponse(findOutage(normalizedEventNo));
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public List<RoomOutageResponse> listOutages() {
        rwLock.readLock().lock();
        try {
            return outagesByEventNo.values().stream()
                    .map(this::toOutageResponse)
                    .collect(Collectors.toList());
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public RepairTaskResponse getTask(Long taskId) {
        rwLock.readLock().lock();
        try {
            return toTaskResponse(findTask(taskId));
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public List<RepairTaskResponse> listTasks(String eventNo) {
        rwLock.readLock().lock();
        try {
            return tasksById.values().stream()
                    .filter(t -> eventNo == null || eventNo.trim().isEmpty()
                            || t.getEventNo().equals(eventNo.trim()))
                    .map(this::toTaskResponse)
                    .collect(Collectors.toList());
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public RepairPlanResponse getRepairPlan(Long planId) {
        rwLock.readLock().lock();
        try {
            return toPlanResponse(findPlan(planId));
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public List<RepairPlanResponse> listRepairPlans(String eventNo) {
        rwLock.readLock().lock();
        try {
            return plansById.values().stream()
                    .filter(p -> eventNo == null || eventNo.trim().isEmpty()
                            || p.getEventNo().equals(eventNo.trim()))
                    .map(this::toPlanResponse)
                    .collect(Collectors.toList());
        } finally {
            rwLock.readLock().unlock();
        }
    }

    // ---------------------------------------------------------------------
    // 内部辅助
    // ---------------------------------------------------------------------

    /**
     * 任务对账：冻结后若课程已被删除，或已由普通调课/其他修复移出停用范围，
     * 则标记相应处置状态（冻结记录完整保留），不再要求当前方案覆盖它。
     * 对账后若无待修复课程，任务视为已修复。必须在持有域写锁时调用。
     */
    private void reconcileTask(RepairTask task) {
        Map<Long, CourseSchedule> schedulesById = new HashMap<>();
        for (CourseSchedule s : courseScheduleService.snapshotAllSchedules()) {
            schedulesById.put(s.getId(), s);
        }
        boolean hasActive = false;
        for (AffectedCourse affected : task.getAffectedCourses()) {
            if (affected.getState() == AffectedCourse.State.RESOLVED) {
                continue;
            }
            CourseSchedule current = schedulesById.get(affected.getScheduleId());
            if (current == null) {
                affected.setState(AffectedCourse.State.DELETED);
            } else if (!conflictsWithAnyActiveWindow(current)) {
                affected.setState(AffectedCourse.State.MOVED_AWAY);
            } else {
                affected.setState(AffectedCourse.State.ACTIVE);
                hasActive = true;
            }
        }

        if (task.getStatus() == RepairTaskStatus.PENDING && !hasActive) {
            task.setStatus(RepairTaskStatus.REPAIRED);
            task.setProcessedAt(LocalDateTime.now(clock));
        }
    }

    private boolean conflictsWithAnyActiveWindow(CourseSchedule schedule) {
        for (OutageWindow window : activeWindows()) {
            if (window.getClassroom().equals(schedule.getClassroom())
                    && ScheduleConflictSupport.timeSlotsOverlap(window.getTimeSlot(), schedule.getTimeSlot())) {
                return true;
            }
        }
        return false;
    }

    private Set<Long> currentRequiredScheduleIds(RepairTask task) {
        Set<Long> ids = new LinkedHashSet<>();
        for (AffectedCourse affected : task.getAffectedCourses()) {
            if (affected.getState() == AffectedCourse.State.ACTIVE) {
                ids.add(affected.getScheduleId());
            }
        }
        return ids;
    }

    private AffectedCourse findAffected(RepairTask task, Long scheduleId) {
        for (AffectedCourse affected : task.getAffectedCourses()) {
            if (affected.getScheduleId().equals(scheduleId)) {
                return affected;
            }
        }
        return null;
    }

    private Map<Long, CourseSchedule> loadSchedulesByIds(Set<Long> ids) {
        Map<Long, CourseSchedule> result = new LinkedHashMap<>();
        for (CourseSchedule s : courseScheduleService.snapshotSchedulesByIds(ids)) {
            result.put(s.getId(), s);
        }
        return result;
    }

    private RoomOutage findOutage(String eventNo) {
        RoomOutage outage = outagesByEventNo.get(eventNo);
        if (outage == null) {
            throw new OutageNotFoundException("教室停用事件不存在，外部事件号: " + eventNo);
        }
        return outage;
    }

    private RoomOutage findActiveOutage(String eventNo) {
        RoomOutage outage = findOutage(eventNo);
        if (outage.getStatus() == OutageStatus.CANCELLED) {
            throw new OutageConflictException("教室停用事件已取消，事件号: " + eventNo);
        }
        return outage;
    }

    private RepairTask findTask(Long taskId) {
        if (taskId == null) {
            throw new OutageNotFoundException("修复任务不存在，ID: null");
        }
        RepairTask task = tasksById.get(taskId);
        if (task == null) {
            throw new OutageNotFoundException("修复任务不存在，ID: " + taskId);
        }
        return task;
    }

    private RepairPlan findPlan(Long planId) {
        if (planId == null) {
            throw new OutageNotFoundException("修复方案不存在，ID: null");
        }
        RepairPlan plan = plansById.get(planId);
        if (plan == null) {
            throw new OutageNotFoundException("修复方案不存在，ID: " + planId);
        }
        return plan;
    }

    private void ensureTaskPending(RepairTask task, String operation) {
        if (task.getStatus() != RepairTaskStatus.PENDING) {
            String statusText = task.getStatus() == RepairTaskStatus.REPAIRED ? "已修复" : "已取消";
            throw new RepairStateException(
                    "修复任务" + statusText + "，不可" + operation + "，任务 ID: " + task.getId());
        }
    }

    private void ensurePlanPending(RepairPlan plan, String operation) {
        if (plan.getStatus() != ReschedulePlanStatus.PENDING) {
            String statusText = plan.getStatus() == ReschedulePlanStatus.CONFIRMED ? "已确认" : "已拒绝";
            throw new RepairStateException(
                    "修复方案" + statusText + "，不可再次" + operation + "，方案 ID: " + plan.getId());
        }
    }

    private String normalizeRequired(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new InvalidRequestParameterException(fieldName + "不能为空");
        }
        return value.trim();
    }

    private String fingerprint(String classroom, String timeSlot, String reason) {
        return classroom + "|" + timeSlot + "|" + (reason == null ? "" : reason);
    }

    private String buildFingerprint(List<ReschedulePlanItemRequest> itemRequests,
                                    List<String> classrooms, List<String> timeSlots) {
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < itemRequests.size(); i++) {
            parts.add(itemRequests.get(i).getScheduleId() + "|" + classrooms.get(i) + "|" + timeSlots.get(i));
        }
        parts.sort(String::compareTo);
        return String.join(";", parts);
    }

    private RoomOutageResponse toOutageResponse(RoomOutage outage) {
        RoomOutageResponse response = new RoomOutageResponse();
        response.setId(outage.getId());
        response.setEventNo(outage.getEventNo());
        response.setClassroom(outage.getClassroom());
        response.setTimeSlot(outage.getTimeSlot());
        response.setReason(outage.getReason());
        response.setStatus(outage.getStatus());
        response.setOperator(outage.getOperator());
        response.setCreatedAt(outage.getCreatedAt());
        response.setUpdatedAt(outage.getUpdatedAt());
        response.setCancelledAt(outage.getCancelledAt());
        response.setTaskIds(new ArrayList<>(outage.getTaskIds()));
        return response;
    }

    private RepairTaskResponse toTaskResponse(RepairTask task) {
        RepairTaskResponse response = new RepairTaskResponse();
        response.setId(task.getId());
        response.setOutageId(task.getOutageId());
        response.setEventNo(task.getEventNo());
        response.setClassroom(task.getClassroom());
        response.setTimeSlot(task.getTimeSlot());
        response.setReason(task.getReason());
        response.setStatus(task.getStatus());
        response.setVersion(task.getVersion());
        response.setCreatedAt(task.getCreatedAt());
        response.setProcessedAt(task.getProcessedAt());
        response.setCancelledAt(task.getCancelledAt());
        response.setPlanIds(new ArrayList<>(task.getPlanIds()));

        List<AffectedCourseDTO> courseDtos = new ArrayList<>();
        int resolved = 0;
        for (AffectedCourse affected : task.getAffectedCourses()) {
            boolean isResolved = affected.getState() == AffectedCourse.State.RESOLVED;
            if (isResolved) {
                resolved++;
            }
            courseDtos.add(new AffectedCourseDTO(
                    affected.getScheduleId(), affected.getCourseName(), affected.getTeacherName(),
                    affected.getOriginalClassroom(), affected.getOriginalTimeSlot(),
                    affected.getFrozenRevision(), isResolved, affected.getState().name()));
        }
        response.setAffectedCourses(courseDtos);
        response.setAffectedCount(courseDtos.size());
        response.setResolvedCount(resolved);
        return response;
    }

    private RepairPlanResponse toPlanResponse(RepairPlan plan) {
        RepairPlanResponse response = new RepairPlanResponse();
        response.setId(plan.getId());
        response.setTaskId(plan.getTaskId());
        response.setEventNo(plan.getEventNo());
        response.setBizKey(plan.getBizKey());
        response.setStatus(plan.getStatus());
        response.setTaskVersion(plan.getTaskVersion());
        response.setItems(plan.getItems().stream()
                .map(item -> new ReschedulePlanItemResponse(
                        item.getScheduleId(), item.getCourseName(), item.getTeacherName(),
                        item.getOriginalClassroom(), item.getOriginalTimeSlot(),
                        item.getNewClassroom(), item.getNewTimeSlot()))
                .collect(Collectors.toList()));
        response.setAffectedScheduleIds(new ArrayList<>(plan.getTargetScheduleIds()));
        response.setConflicts(new ArrayList<>(plan.getConflicts()));
        response.setConflictCount(plan.getConflicts().size());
        response.setResultMessage(plan.getResultMessage());
        response.setOperator(plan.getOperator());
        response.setCreatedAt(plan.getCreatedAt());
        response.setProcessedAt(plan.getProcessedAt());
        return response;
    }

    public void resetForTesting() {
        rwLock.writeLock().lock();
        try {
            outagesByEventNo.clear();
            outagesById.clear();
            tasksById.clear();
            plansById.clear();
            plansByBizKey.clear();
            outageIdGenerator = 1;
            taskIdGenerator = 1;
            planIdGenerator = 1;
        } finally {
            rwLock.writeLock().unlock();
        }
    }
}
