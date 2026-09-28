package com.coursescheduler.service;

import com.coursescheduler.dto.RepairPlanActionRequest;
import com.coursescheduler.dto.RepairPlanResponse;
import com.coursescheduler.dto.RepairPlanSubmitRequest;
import com.coursescheduler.dto.RescheduleConflictDTO;
import com.coursescheduler.dto.ReschedulePlanItemRequest;
import com.coursescheduler.dto.ReschedulePlanItemResponse;
import com.coursescheduler.dto.ReschedulePreCheckResponse;
import com.coursescheduler.exception.InvalidRequestParameterException;
import com.coursescheduler.exception.RepairCoverageException;
import com.coursescheduler.exception.RepairPlanNotFoundException;
import com.coursescheduler.exception.RepairPlanStateException;
import com.coursescheduler.exception.RepairTaskNotFoundException;
import com.coursescheduler.exception.RepairTaskStateException;
import com.coursescheduler.exception.ScheduleNotFoundException;
import com.coursescheduler.model.AffectedCourse;
import com.coursescheduler.model.CourseSchedule;
import com.coursescheduler.model.OperationType;
import com.coursescheduler.model.RepairPlan;
import com.coursescheduler.model.RepairTask;
import com.coursescheduler.model.RepairTaskStatus;
import com.coursescheduler.model.ReschedulePlanItem;
import com.coursescheduler.model.ReschedulePlanStatus;
import com.coursescheduler.util.TimeSlotUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 教室停用后的成组修复排课服务。
 *
 * <p>业务规则：
 * <ul>
 *   <li>修复方案必须覆盖对应修复任务中全部待修复课程，可以换教室、换时段，
 *       也可在多门课程之间交换资源（规则与普通调课一致，腾出的旧资源视为可用）；</li>
 *   <li>提交时逐门给出无法安排的具体原因（覆盖缺口、版本不一致、教师/教室冲突、
 *       目标教室停用等）；缺少任何一门的可行安排时不允许确认部分方案；</li>
 *   <li>确认时在共享写锁内重新检查课程版本、教师时间与目标教室的停用状态，
 *       全部通过才在一次事务中应用所有变更，任一失败整份方案不生效；</li>
 *   <li>方案按修复业务号 bizKey 幂等：同号同内容返回首次结果，同号异内容冲突；</li>
 *   <li>修复确认成功后更新修复任务状态，并为每门课程写入 REPAIR_CONFIRM 审计日志，
 *       记录修复前后安排与处理人员。</li>
 * </ul>
 */
@Service
public class RepairPlanService {

    private final CourseScheduleService courseScheduleService;
    private final RoomOutageService outageService;
    private final RoomOutageRegistry outageRegistry;
    private final RepairTaskRegistry taskRegistry;
    private final AuditLogService auditLogService;
    private final SchedulingLocks schedulingLocks;
    private final Clock clock;

    private final Map<Long, RepairPlan> plansById = new LinkedHashMap<>();
    private final Map<String, RepairPlan> plansByBizKey = new LinkedHashMap<>();
    private long idGenerator = 1;

    @Autowired
    public RepairPlanService(CourseScheduleService courseScheduleService,
                             RoomOutageService outageService,
                             RoomOutageRegistry outageRegistry,
                             RepairTaskRegistry taskRegistry,
                             AuditLogService auditLogService,
                             SchedulingLocks schedulingLocks,
                             Clock clock) {
        this.courseScheduleService = courseScheduleService;
        this.outageService = outageService;
        this.outageRegistry = outageRegistry;
        this.taskRegistry = taskRegistry;
        this.auditLogService = auditLogService;
        this.schedulingLocks = schedulingLocks;
        this.clock = clock;
    }

    /**
     * 提交结果：replayed=true 表示命中幂等。
     */
    public static class SubmitOutcome {
        private final RepairPlanResponse response;
        private final boolean replayed;

        SubmitOutcome(RepairPlanResponse response, boolean replayed) {
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

    public SubmitOutcome submit(RepairPlanSubmitRequest request) {
        if (request == null) {
            throw new InvalidRequestParameterException("请求不能为空");
        }
        String bizKey = normalizeRequired(request.getBizKey(), "修复业务号");
        if (request.getTaskId() == null || request.getTaskId() <= 0) {
            throw new InvalidRequestParameterException("修复任务 ID 不合法");
        }
        Long taskId = request.getTaskId();
        List<ReschedulePlanItemRequest> itemRequests = request.getItems();
        if (itemRequests == null || itemRequests.isEmpty()) {
            throw new InvalidRequestParameterException("修复方案至少包含一条课程调整");
        }
        String operator = normalizeOptional(request.getOperator());

        List<String> normalizedClassrooms = new ArrayList<>();
        List<String> normalizedTimeSlots = new ArrayList<>();
        Set<Long> seenScheduleIds = new HashSet<>();
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
        String fingerprint = buildFingerprint(taskId, itemRequests, normalizedClassrooms, normalizedTimeSlots);

        schedulingLocks.getLock().writeLock().lock();
        try {
            RepairPlan existing = plansByBizKey.get(bizKey);
            if (existing != null) {
                if (existing.getFingerprint().equals(fingerprint)) {
                    return new SubmitOutcome(toResponse(existing), true);
                }
                throw new RepairPlanStateException(
                        "相同修复业务号已提交过不同内容的方案，业务号: " + bizKey);
            }

            RepairTask task = findTaskOrThrow(taskId);
            if (task.getStatus() != RepairTaskStatus.OPEN) {
                throw new RepairTaskStateException(
                        "修复任务" + (task.getStatus() == RepairTaskStatus.RESOLVED ? "已完成" : "已取消")
                                + "，不可再提交修复方案，任务 ID: " + task.getId());
            }

            // 先核对任务：冻结后已删除/已移出停用时段的课程不再要求覆盖
            outageService.reconcileTask(task, courseScheduleService.snapshotAllSchedules());
            outageService.refreshTaskResolvedStatus(task, LocalDateTime.now(clock));
            if (task.getStatus() != RepairTaskStatus.OPEN) {
                throw new RepairTaskStateException(
                        "修复任务已自然消解完成，无需再提交修复方案，任务 ID: " + task.getId());
            }

            Set<Long> pendingIds = task.getAffectedCourses().stream()
                    .filter(c -> !c.isResolved())
                    .map(AffectedCourse::getScheduleId)
                    .collect(Collectors.toSet());
            Set<Long> coveredIds = new HashSet<>(seenScheduleIds);

            List<Long> missingIds = new ArrayList<>();
            for (Long id : pendingIds) {
                if (!coveredIds.contains(id)) {
                    missingIds.add(id);
                }
            }
            List<Long> extraIds = new ArrayList<>();
            for (Long id : coveredIds) {
                if (!pendingIds.contains(id)) {
                    extraIds.add(id);
                }
            }
            if (!missingIds.isEmpty() || !extraIds.isEmpty()) {
                throw new RepairCoverageException(
                        "修复方案必须且只能覆盖修复任务中全部 " + pendingIds.size()
                                + " 门待修复课程，缺少 " + missingIds.size()
                                + " 门，越界 " + extraIds.size() + " 门",
                        missingIds, extraIds);
            }

            Map<Long, CourseSchedule> currentById = new HashMap<>();
            for (CourseSchedule schedule : courseScheduleService.snapshotAllSchedules()) {
                currentById.put(schedule.getId(), schedule);
            }

            List<ReschedulePlanItem> items = new ArrayList<>();
            for (int i = 0; i < itemRequests.size(); i++) {
                Long scheduleId = itemRequests.get(i).getScheduleId();
                CourseSchedule current = currentById.get(scheduleId);
                if (current == null) {
                    throw new ScheduleNotFoundException("课程安排不存在，ID: " + scheduleId);
                }
                items.add(new ReschedulePlanItem(
                        scheduleId,
                        current.getCourseName(),
                        current.getTeacherName(),
                        current.getClassroom(),
                        current.getTimeSlot(),
                        normalizedClassrooms.get(i),
                        normalizedTimeSlots.get(i),
                        current.getVersion()));
            }

            RepairPlan plan = new RepairPlan();
            plan.setId(idGenerator++);
            plan.setBizKey(bizKey);
            plan.setFingerprint(fingerprint);
            plan.setRepairTaskId(taskId);
            plan.setStatus(ReschedulePlanStatus.PENDING);
            plan.setItems(items);
            plan.setCreatedBy(operator);
            plan.setCreatedAt(LocalDateTime.now(clock));
            plansById.put(plan.getId(), plan);
            plansByBizKey.put(bizKey, plan);

            return new SubmitOutcome(toResponse(plan), false);
        } finally {
            schedulingLocks.getLock().writeLock().unlock();
        }
    }

    public ReschedulePreCheckResponse preCheck(Long planId) {
        schedulingLocks.getLock().writeLock().lock();
        try {
            RepairPlan plan = findPlanOrThrow(planId);
            ensurePending(plan, "预检查");
            RepairTask task = findTaskOrThrow(plan.getRepairTaskId());
            if (task.getStatus() != RepairTaskStatus.OPEN) {
                throw new RepairTaskStateException(
                        "修复任务" + (task.getStatus() == RepairTaskStatus.RESOLVED ? "已完成" : "已取消")
                                + "，不可再预检查修复方案，任务 ID: " + task.getId());
            }

            List<RescheduleConflictDTO> conflicts = evaluateConflicts(plan, task);
            plan.setConflicts(conflicts);
            plan.setResultMessage(conflicts.isEmpty()
                    ? "预检查通过，修复方案可以确认"
                    : "预检查发现 " + conflicts.size() + " 项冲突");

            return new ReschedulePreCheckResponse(plan.getId(), conflicts.isEmpty(),
                    conflicts.size(), conflicts);
        } finally {
            schedulingLocks.getLock().writeLock().unlock();
        }
    }

    public RepairPlanResponse confirm(Long planId, RepairPlanActionRequest request) {
        String operator = request != null ? normalizeOptional(request.getOperator()) : null;
        schedulingLocks.getLock().writeLock().lock();
        try {
            RepairPlan plan = findPlanOrThrow(planId);
            ensurePending(plan, "确认");
            RepairTask task = findTaskOrThrow(plan.getRepairTaskId());
            if (task.getStatus() != RepairTaskStatus.OPEN) {
                throw new RepairTaskStateException(
                        "修复任务" + (task.getStatus() == RepairTaskStatus.RESOLVED ? "已完成" : "已取消")
                                + "，不可再确认修复方案，任务 ID: " + task.getId());
            }

            List<RescheduleConflictDTO> conflicts = evaluateConflicts(plan, task);
            if (!conflicts.isEmpty()) {
                plan.setConflicts(conflicts);
                plan.setResultMessage("修复失败：存在 " + conflicts.size() + " 项冲突，整份修复方案未生效");
                throw new com.coursescheduler.exception.RescheduleConflictException(
                        "修复方案存在冲突，整份方案未生效", conflicts);
            }

            List<RescheduleConflictDTO> applied = courseScheduleService.applyPlanAtomically(
                    plan.getItems(), OperationType.REPAIR_CONFIRM,
                    operator != null ? operator : plan.getCreatedBy(), plan.getBizKey());
            if (!applied.isEmpty()) {
                plan.setConflicts(applied);
                plan.setResultMessage("修复失败：应用阶段重新校验发现 " + applied.size()
                        + " 项冲突，整份修复方案未生效");
                throw new com.coursescheduler.exception.RescheduleConflictException(
                        "修复方案在应用阶段发现冲突，整份方案未生效", applied);
            }

            LocalDateTime now = LocalDateTime.now(clock);
            for (ReschedulePlanItem item : plan.getItems()) {
                AffectedCourse course = findAffectedOrThrow(task, item.getScheduleId());
                course.setResolved(true);
                course.setResolvedReason("已由修复方案 " + plan.getBizKey() + " 修复");
            }
            boolean allResolved = task.getAffectedCourses().stream()
                    .allMatch(AffectedCourse::isResolved);
            if (allResolved) {
                task.setStatus(RepairTaskStatus.RESOLVED);
                task.setResolvedAt(now);
                task.setConfirmedRepairPlanId(plan.getId());
            }

            plan.setStatus(ReschedulePlanStatus.CONFIRMED);
            plan.setConflicts(new ArrayList<>());
            plan.setResultMessage("修复成功，全部 " + plan.getItems().size()
                    + " 门课程的调整已生效" + (allResolved ? "，修复任务完成" : "，修复任务仍有待修复课程"));
            plan.setProcessedBy(operator != null ? operator : plan.getCreatedBy());
            plan.setProcessedAt(now);
            return toResponse(plan);
        } finally {
            schedulingLocks.getLock().writeLock().unlock();
        }
    }

    public RepairPlanResponse reject(Long planId, RepairPlanActionRequest request) {
        String operator = request != null ? normalizeOptional(request.getOperator()) : null;
        schedulingLocks.getLock().writeLock().lock();
        try {
            RepairPlan plan = findPlanOrThrow(planId);
            ensurePending(plan, "拒绝");

            plan.setStatus(ReschedulePlanStatus.REJECTED);
            plan.setResultMessage("修复方案已拒绝");
            plan.setProcessedBy(operator != null ? operator : plan.getCreatedBy());
            plan.setProcessedAt(LocalDateTime.now(clock));
            return toResponse(plan);
        } finally {
            schedulingLocks.getLock().writeLock().unlock();
        }
    }

    public RepairPlanResponse getPlan(Long planId) {
        schedulingLocks.getLock().readLock().lock();
        try {
            return toResponse(findPlanOrThrow(planId));
        } finally {
            schedulingLocks.getLock().readLock().unlock();
        }
    }

    public List<RepairPlanResponse> listPlans() {
        schedulingLocks.getLock().readLock().lock();
        try {
            return plansById.values().stream()
                    .map(this::toResponse)
                    .collect(Collectors.toList());
        } finally {
            schedulingLocks.getLock().readLock().unlock();
        }
    }

    /**
     * 在当前数据上重新计算方案冲突，并把任务覆盖缺口一并计入，
     * 保证缺少任何一门的可行安排时预检/确认都失败。
     * 调用方须持有写锁。
     */
    private List<RescheduleConflictDTO> evaluateConflicts(RepairPlan plan, RepairTask task) {
        outageService.reconcileTask(task, courseScheduleService.snapshotAllSchedules());
        outageService.refreshTaskResolvedStatus(task, LocalDateTime.now(clock));

        List<RescheduleConflictDTO> conflicts = RescheduleConflictSupport.computeConflicts(
                courseScheduleService.snapshotAllSchedules(),
                plan.getItems(),
                outageRegistry.activeOutages());

        // 覆盖缺口仅在方案本身的课程仍有效时才有意义；
        // 任务自然消解但方案引用已过时（版本/内容不一致）时，直接返回方案级冲突明细。
        if (!conflicts.isEmpty()) {
            return conflicts;
        }
        if (task.getStatus() != RepairTaskStatus.OPEN) {
            throw new RepairTaskStateException(
                    "修复任务" + (task.getStatus() == RepairTaskStatus.RESOLVED ? "已完成" : "已取消")
                            + "，待修复课程已不存在或均已移出停用时段，方案无需再处理，任务 ID: "
                            + task.getId());
        }

        Set<Long> coveredIds = plan.getItems().stream()
                .map(ReschedulePlanItem::getScheduleId)
                .collect(Collectors.toSet());
        for (AffectedCourse course : task.getAffectedCourses()) {
            if (!course.isResolved() && !coveredIds.contains(course.getScheduleId())) {
                conflicts.add(new RescheduleConflictDTO(
                        course.getScheduleId(),
                        RescheduleConflictDTO.ConflictType.NOT_COVERED,
                        null,
                        null,
                        "修复方案缺少该待修复课程的可行安排，课程: " + course.getCourseName()
                                + "（ID: " + course.getScheduleId() + "）"));
            }
        }
        return conflicts;
    }

    private AffectedCourse findAffectedOrThrow(RepairTask task, Long scheduleId) {
        for (AffectedCourse course : task.getAffectedCourses()) {
            if (course.getScheduleId().equals(scheduleId)) {
                return course;
            }
        }
        throw new RepairCoverageException(
                "课程不属于该修复任务，ID: " + scheduleId, new ArrayList<>(),
                java.util.Collections.singletonList(scheduleId));
    }

    private RepairTask findTaskOrThrow(Long taskId) {
        RepairTask task = taskRegistry.getById(taskId);
        if (task == null) {
            throw new RepairTaskNotFoundException("修复任务不存在，ID: " + taskId);
        }
        return task;
    }

    private RepairPlan findPlanOrThrow(Long planId) {
        if (planId == null) {
            throw new RepairPlanNotFoundException("修复方案不存在，ID: null");
        }
        RepairPlan plan = plansById.get(planId);
        if (plan == null) {
            throw new RepairPlanNotFoundException("修复方案不存在，ID: " + planId);
        }
        return plan;
    }

    private void ensurePending(RepairPlan plan, String operation) {
        if (plan.getStatus() != ReschedulePlanStatus.PENDING) {
            String statusText = plan.getStatus() == ReschedulePlanStatus.CONFIRMED ? "已确认" : "已拒绝";
            throw new RepairPlanStateException(
                    "修复方案" + statusText + "，不可再次" + operation + "，方案 ID: " + plan.getId());
        }
    }

    private String normalizeRequired(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new InvalidRequestParameterException(field + "不能为空");
        }
        return value.trim();
    }

    private String normalizeOptional(String value) {
        return value != null && !value.trim().isEmpty() ? value.trim() : null;
    }

    private String buildFingerprint(Long taskId, List<ReschedulePlanItemRequest> itemRequests,
                                    List<String> classrooms, List<String> timeSlots) {
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < itemRequests.size(); i++) {
            parts.add(itemRequests.get(i).getScheduleId()
                    + "|" + classrooms.get(i)
                    + "|" + timeSlots.get(i));
        }
        parts.sort(String::compareTo);
        return taskId + "#" + String.join(";", parts);
    }

    private RepairPlanResponse toResponse(RepairPlan plan) {
        RepairPlanResponse response = new RepairPlanResponse();
        response.setId(plan.getId());
        response.setBizKey(plan.getBizKey());
        response.setTaskId(plan.getRepairTaskId());
        RepairTask task = taskRegistry.getById(plan.getRepairTaskId());
        response.setEventNo(task != null ? task.getEventNo() : null);
        response.setStatus(plan.getStatus());
        response.setItems(plan.getItems().stream()
                .map(item -> new ReschedulePlanItemResponse(
                        item.getScheduleId(),
                        item.getCourseName(),
                        item.getTeacherName(),
                        item.getOriginalClassroom(),
                        item.getOriginalTimeSlot(),
                        item.getNewClassroom(),
                        item.getNewTimeSlot()))
                .collect(Collectors.toList()));
        response.setConflicts(new ArrayList<>(plan.getConflicts()));
        response.setConflictCount(plan.getConflicts().size());
        response.setResultMessage(plan.getResultMessage());
        response.setCreatedBy(plan.getCreatedBy());
        response.setCreatedAt(plan.getCreatedAt());
        response.setProcessedBy(plan.getProcessedBy());
        response.setProcessedAt(plan.getProcessedAt());

        if (task != null) {
            List<Long> pendingIds = task.getAffectedCourses().stream()
                    .filter(c -> !c.isResolved())
                    .map(AffectedCourse::getScheduleId)
                    .collect(Collectors.toList());
            Set<Long> coveredIds = plan.getItems().stream()
                    .map(ReschedulePlanItem::getScheduleId)
                    .collect(Collectors.toSet());
            List<Long> missing = new ArrayList<>();
            for (Long id : pendingIds) {
                if (!coveredIds.contains(id)) {
                    missing.add(id);
                }
            }
            response.setExpectedCount(pendingIds.size());
            response.setCoveredCount((int) plan.getItems().stream()
                    .map(ReschedulePlanItem::getScheduleId)
                    .filter(pendingIds::contains)
                    .count());
            response.setMissingScheduleIds(missing);
        }
        return response;
    }

    public void resetForTesting() {
        schedulingLocks.getLock().writeLock().lock();
        try {
            plansById.clear();
            plansByBizKey.clear();
            idGenerator = 1;
        } finally {
            schedulingLocks.getLock().writeLock().unlock();
        }
    }
}
