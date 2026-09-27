package com.coursescheduler.service;

import com.coursescheduler.dto.RescheduleChangeDTO;
import com.coursescheduler.dto.RescheduleItemDTO;
import com.coursescheduler.dto.RescheduleIssueDTO;
import com.coursescheduler.dto.ReschedulePlanCreateRequest;
import com.coursescheduler.dto.ReschedulePlanResponse;
import com.coursescheduler.dto.ReschedulePreCheckResponse;
import com.coursescheduler.dto.RescheduleResultResponse;
import com.coursescheduler.exception.InvalidRequestParameterException;
import com.coursescheduler.exception.RescheduleConflictException;
import com.coursescheduler.exception.ReschedulePlanNotFoundException;
import com.coursescheduler.exception.ReschedulePlanProcessedException;
import com.coursescheduler.model.CourseSchedule;
import com.coursescheduler.model.OperationType;
import com.coursescheduler.model.RescheduleChange;
import com.coursescheduler.model.RescheduleItem;
import com.coursescheduler.model.RescheduleIssue;
import com.coursescheduler.model.ReschedulePlan;
import com.coursescheduler.model.ReschedulePlanStatus;
import com.coursescheduler.util.TimeSlotUtils;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * 原子调课方案服务。
 *
 * <p>职责：方案提交（冻结原始快照、业务号幂等）、预检（考虑方案内即将腾出的旧时段）、
 * 确认（基于快照的乐观校验，全部变更一次性生效）、拒绝、方案与冲突/结果/变更明细查询。
 *
 * <p>并发安全：方案存储使用自身读写锁；真正的课程变更通过
 * {@link CourseScheduleService#applyRescheduleAtomically(List)} 在排课写锁内完成，
 * 因此多个方案并发修改同一课程时最多一个成功。
 */
@Service
public class RescheduleService {

    private static final DateTimeFormatter PLAN_ID_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final CourseScheduleService scheduleService;
    private final AuditLogService auditLogService;
    private final Clock clock;

    private final List<ReschedulePlan> plans = new ArrayList<>();
    private final Map<String, String> businessIdToPlanId = new LinkedHashMap<>();
    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();
    private long planSequence = 1;

    public RescheduleService(CourseScheduleService scheduleService,
                             AuditLogService auditLogService,
                             Clock clock) {
        this.scheduleService = scheduleService;
        this.auditLogService = auditLogService;
        this.clock = clock;
    }

    // ==================== 提交方案 ====================

    public ReschedulePlanResponse createPlan(ReschedulePlanCreateRequest request) {
        if (request == null) {
            throw new InvalidRequestParameterException("调课方案请求不能为空");
        }
        String businessId = normalizeBusinessId(request.getBusinessId());
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new InvalidRequestParameterException("调课条目列表不能为空");
        }

        // 校验并规范化条目内容（与存储无关的参数校验在锁外完成）。
        List<RescheduleItem> normalizedItems = new ArrayList<>();
        Set<Long> uniqueScheduleIds = new HashSet<>();
        for (int i = 0; i < request.getItems().size(); i++) {
            var raw = request.getItems().get(i);
            if (raw == null) {
                throw new InvalidRequestParameterException("第 " + (i + 1) + " 项调课条目不能为空");
            }
            Long scheduleId = raw.getScheduleId();
            if (scheduleId == null || scheduleId <= 0) {
                throw new InvalidRequestParameterException("第 " + (i + 1) + " 项课程安排 ID 不合法");
            }
            if (raw.getClassroom() == null || raw.getClassroom().trim().isEmpty()) {
                throw new InvalidRequestParameterException("第 " + (i + 1) + " 项目标教室不能为空");
            }
            if (raw.getTimeSlot() == null || raw.getTimeSlot().trim().isEmpty()) {
                throw new InvalidRequestParameterException("第 " + (i + 1) + " 项目标时段不能为空");
            }
            if (!uniqueScheduleIds.add(scheduleId)) {
                throw new InvalidRequestParameterException(
                        "课程安排 ID " + scheduleId + " 在同一方案中被重复引用，方案内课程安排必须互不重复");
            }

            RescheduleItem item = new RescheduleItem();
            item.setItemIndex(i);
            item.setScheduleId(scheduleId);
            item.setTargetClassroom(raw.getClassroom().trim());
            item.setTargetTimeSlot(TimeSlotUtils.normalize(raw.getTimeSlot()));
            normalizedItems.add(item);
        }

        // 冻结当前排课快照，并把提交时看到的原始排课内容写入每条条目。
        Map<Long, CourseSchedule> snapshot = scheduleService.getScheduleSnapshotById();
        for (RescheduleItem item : normalizedItems) {
            CourseSchedule original = snapshot.get(item.getScheduleId());
            if (original == null) {
                throw new InvalidRequestParameterException(
                        "课程安排不存在，ID: " + item.getScheduleId());
            }
            item.setOriginalCourseName(original.getCourseName());
            item.setOriginalTeacherName(original.getTeacherName());
            item.setOriginalClassroom(original.getClassroom());
            item.setOriginalTimeSlot(original.getTimeSlot());
        }

        rwLock.writeLock().lock();
        try {
            String existingPlanId = businessIdToPlanId.get(businessId);
            if (existingPlanId != null) {
                ReschedulePlan existing = findPlanByIdInternal(existingPlanId);
                if (!samePlanContent(existing.getItems(), normalizedItems)) {
                    throw new RescheduleConflictException(
                            "业务号 " + businessId + " 已绑定内容不同的调课方案，禁止重复提交");
                }
                return toPlanResponse(existing);
            }

            String planId = generatePlanId();
            ReschedulePlan plan = new ReschedulePlan(planId, businessId, LocalDateTime.now(clock), normalizedItems);

            // 提交时即给出一份预检结果，便于客户端直接查看方案冲突。
            List<RescheduleIssue> issues = RescheduleConflictSupport.evaluate(
                    new ArrayList<>(plan.getItems()), snapshot);
            plan.setLastIssues(issues);

            plans.add(plan);
            businessIdToPlanId.put(businessId, planId);
            return toPlanResponse(plan);
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    // ==================== 预检 ====================

    public ReschedulePreCheckResponse preCheck(String planIdOrBusinessId) {
        rwLock.writeLock().lock();
        try {
            ReschedulePlan plan = findPlanByIdOrBusinessIdInternal(planIdOrBusinessId);

            List<RescheduleIssue> issues;
            if (plan.getStatus() == ReschedulePlanStatus.PENDING) {
                Map<Long, CourseSchedule> current = scheduleService.getScheduleSnapshotById();
                issues = RescheduleConflictSupport.evaluate(new ArrayList<>(plan.getItems()), current);
                plan.setLastIssues(issues);
            } else {
                issues = new ArrayList<>(plan.getLastIssues());
            }
            return new ReschedulePreCheckResponse(
                    plan.getPlanId(), plan.getBusinessId(),
                    issues.isEmpty(), issues.size(), toIssueDTOs(issues));
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    // ==================== 确认 ====================

    public RescheduleResultResponse confirm(String planIdOrBusinessId) {
        rwLock.writeLock().lock();
        try {
            ReschedulePlan plan = findPlanByIdOrBusinessIdInternal(planIdOrBusinessId);

            if (plan.getStatus() == ReschedulePlanStatus.CONFIRMED) {
                return buildResult(plan, true);
            }
            if (plan.getStatus() == ReschedulePlanStatus.REJECTED) {
                throw new ReschedulePlanProcessedException(
                        "调课方案已拒绝，不能再次处理，方案 ID: " + plan.getPlanId());
            }

            List<RescheduleChange> changes;
            try {
                changes = scheduleService.applyRescheduleAtomically(new ArrayList<>(plan.getItems()));
            } catch (RescheduleConflictException e) {
                plan.setLastIssues(e.getIssues());
                recordFailureAuditLogs(plan, e.getIssues());
                return buildResult(plan, false);
            }

            plan.setChanges(changes);
            plan.setLastIssues(new ArrayList<>());
            plan.setStatus(ReschedulePlanStatus.CONFIRMED);
            plan.setProcessedAt(LocalDateTime.now(clock));

            for (RescheduleChange change : changes) {
                auditLogService.recordLog(
                        OperationType.RESCHEDULE,
                        change.getScheduleId(),
                        change.getCourseName(),
                        change.getTeacherName(),
                        change.getAfterClassroom(),
                        change.getAfterTimeSlot(),
                        true,
                        "调课方案 " + plan.getPlanId() + " 确认成功："
                                + change.getBeforeClassroom() + " " + change.getBeforeTimeSlot()
                                + " -> " + change.getAfterClassroom() + " " + change.getAfterTimeSlot());
            }
            return buildResult(plan, true);
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    // ==================== 拒绝 ====================

    public RescheduleResultResponse reject(String planIdOrBusinessId) {
        rwLock.writeLock().lock();
        try {
            ReschedulePlan plan = findPlanByIdOrBusinessIdInternal(planIdOrBusinessId);

            if (plan.getStatus() == ReschedulePlanStatus.REJECTED) {
                return buildResult(plan, false);
            }
            if (plan.getStatus() == ReschedulePlanStatus.CONFIRMED) {
                throw new ReschedulePlanProcessedException(
                        "调课方案已确认生效，不能再次处理，方案 ID: " + plan.getPlanId());
            }

            plan.setStatus(ReschedulePlanStatus.REJECTED);
            plan.setProcessedAt(LocalDateTime.now(clock));

            for (RescheduleItem item : plan.getItems()) {
                auditLogService.recordLog(
                        OperationType.RESCHEDULE_REJECT,
                        item.getScheduleId(),
                        item.getOriginalCourseName(),
                        item.getOriginalTeacherName(),
                        item.getTargetClassroom(),
                        item.getTargetTimeSlot(),
                        true,
                        "调课方案 " + plan.getPlanId() + " 已拒绝");
            }
            return buildResult(plan, false);
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    // ==================== 查询 ====================

    public ReschedulePlanResponse getPlan(String planIdOrBusinessId) {
        rwLock.readLock().lock();
        try {
            return toPlanResponse(findPlanByIdOrBusinessIdInternal(planIdOrBusinessId));
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public List<ReschedulePlanResponse> listPlans(ReschedulePlanStatus status) {
        rwLock.readLock().lock();
        try {
            return plans.stream()
                    .filter(p -> status == null || p.getStatus() == status)
                    .sorted(Comparator.comparing(ReschedulePlan::getCreatedAt))
                    .map(this::toPlanResponse)
                    .collect(Collectors.toList());
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public List<RescheduleIssueDTO> getIssues(String planIdOrBusinessId) {
        rwLock.readLock().lock();
        try {
            return toIssueDTOs(new ArrayList<>(
                    findPlanByIdOrBusinessIdInternal(planIdOrBusinessId).getLastIssues()));
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public List<RescheduleChangeDTO> getChanges(String planIdOrBusinessId) {
        rwLock.readLock().lock();
        try {
            return toChangeDTOs(new ArrayList<>(
                    findPlanByIdOrBusinessIdInternal(planIdOrBusinessId).getChanges()));
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * 查询方案最近一次处理结果：已确认/已拒绝返回终态结果；待处理方案返回当前状态与最近冲突明细。
     */
    public RescheduleResultResponse getResult(String planIdOrBusinessId) {
        rwLock.readLock().lock();
        try {
            ReschedulePlan plan = findPlanByIdOrBusinessIdInternal(planIdOrBusinessId);
            return buildResult(plan, plan.getStatus() == ReschedulePlanStatus.CONFIRMED);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public void resetForTesting() {
        rwLock.writeLock().lock();
        try {
            plans.clear();
            businessIdToPlanId.clear();
            planSequence = 1;
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    // ==================== 内部方法 ====================

    private void recordFailureAuditLogs(ReschedulePlan plan, List<RescheduleIssue> issues) {
        Set<Long> reportedSchedules = new HashSet<>();
        for (RescheduleIssue issue : issues) {
            if (issue.getScheduleId() == null || !reportedSchedules.add(issue.getScheduleId())) {
                continue;
            }
            RescheduleItem item = plan.getItems().get(issue.getItemIndex());
            auditLogService.recordLog(
                    OperationType.RESCHEDULE_FAILED,
                    issue.getScheduleId(),
                    item.getOriginalCourseName(),
                    item.getOriginalTeacherName(),
                    item.getTargetClassroom(),
                    item.getTargetTimeSlot(),
                    false,
                    issue.getReason());
        }
    }

    private String normalizeBusinessId(String businessId) {
        if (businessId == null || businessId.trim().isEmpty()) {
            throw new InvalidRequestParameterException("调课业务号不能为空");
        }
        return businessId.trim();
    }

    private String generatePlanId() {
        return "RP-" + LocalDateTime.now(clock).format(PLAN_ID_TIME_FORMAT) + "-" + planSequence++;
    }

    private ReschedulePlan findPlanByIdInternal(String planId) {
        for (ReschedulePlan plan : plans) {
            if (plan.getPlanId().equals(planId)) {
                return plan;
            }
        }
        throw new ReschedulePlanNotFoundException("调课方案不存在，ID: " + planId);
    }

    private ReschedulePlan findPlanByIdOrBusinessIdInternal(String planIdOrBusinessId) {
        if (planIdOrBusinessId == null || planIdOrBusinessId.trim().isEmpty()) {
            throw new InvalidRequestParameterException("方案 ID 或业务号不能为空");
        }
        String key = planIdOrBusinessId.trim();
        String planId = businessIdToPlanId.get(key);
        if (planId != null) {
            return findPlanByIdInternal(planId);
        }
        return findPlanByIdInternal(key);
    }

    /**
     * 幂等内容比较：相同业务号重复提交时，方案引用的课程及目标排课必须完全一致。
     */
    private boolean samePlanContent(List<RescheduleItem> existing, List<RescheduleItem> candidate) {
        if (existing.size() != candidate.size()) {
            return false;
        }
        for (int i = 0; i < existing.size(); i++) {
            RescheduleItem a = existing.get(i);
            RescheduleItem b = candidate.get(i);
            if (!a.getScheduleId().equals(b.getScheduleId())
                    || !a.getTargetClassroom().equals(b.getTargetClassroom())
                    || !a.getTargetTimeSlot().equals(b.getTargetTimeSlot())) {
                return false;
            }
        }
        return true;
    }

    private RescheduleResultResponse buildResult(ReschedulePlan plan, boolean applied) {
        RescheduleResultResponse response = new RescheduleResultResponse();
        response.setPlanId(plan.getPlanId());
        response.setBusinessId(plan.getBusinessId());
        response.setStatus(plan.getStatus());
        response.setApplied(applied);
        List<RescheduleIssue> issues = new ArrayList<>(plan.getLastIssues());
        response.setConflictCount(issues.size());
        response.setIssues(toIssueDTOs(issues));
        response.setChanges(toChangeDTOs(new ArrayList<>(plan.getChanges())));
        response.setProcessedAt(plan.getProcessedAt());
        return response;
    }

    private ReschedulePlanResponse toPlanResponse(ReschedulePlan plan) {
        ReschedulePlanResponse response = new ReschedulePlanResponse();
        response.setPlanId(plan.getPlanId());
        response.setBusinessId(plan.getBusinessId());
        response.setStatus(plan.getStatus());
        response.setCreatedAt(plan.getCreatedAt());
        response.setProcessedAt(plan.getProcessedAt());

        List<RescheduleItemDTO> itemDTOs = new ArrayList<>();
        for (RescheduleItem item : plan.getItems()) {
            RescheduleItemDTO dto = new RescheduleItemDTO();
            dto.setItemIndex(item.getItemIndex());
            dto.setScheduleId(item.getScheduleId());
            dto.setCourseName(item.getOriginalCourseName());
            dto.setOriginalTeacherName(item.getOriginalTeacherName());
            dto.setOriginalClassroom(item.getOriginalClassroom());
            dto.setOriginalTimeSlot(item.getOriginalTimeSlot());
            dto.setTargetClassroom(item.getTargetClassroom());
            dto.setTargetTimeSlot(item.getTargetTimeSlot());
            itemDTOs.add(dto);
        }
        response.setItems(itemDTOs);
        response.setIssues(toIssueDTOs(new ArrayList<>(plan.getLastIssues())));
        response.setChanges(toChangeDTOs(new ArrayList<>(plan.getChanges())));
        return response;
    }

    private List<RescheduleIssueDTO> toIssueDTOs(List<RescheduleIssue> issues) {
        List<RescheduleIssueDTO> dtos = new ArrayList<>();
        for (RescheduleIssue issue : issues) {
            dtos.add(RescheduleIssueDTO.from(issue));
        }
        return dtos;
    }

    private List<RescheduleChangeDTO> toChangeDTOs(List<RescheduleChange> changes) {
        List<RescheduleChangeDTO> dtos = new ArrayList<>();
        for (RescheduleChange change : changes) {
            RescheduleChangeDTO dto = new RescheduleChangeDTO();
            dto.setItemIndex(change.getItemIndex());
            dto.setScheduleId(change.getScheduleId());
            dto.setCourseName(change.getCourseName());
            dto.setTeacherName(change.getTeacherName());
            dto.setBeforeClassroom(change.getBeforeClassroom());
            dto.setBeforeTimeSlot(change.getBeforeTimeSlot());
            dto.setAfterClassroom(change.getAfterClassroom());
            dto.setAfterTimeSlot(change.getAfterTimeSlot());
            dtos.add(dto);
        }
        return dtos;
    }
}
