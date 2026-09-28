package com.coursescheduler.service;

import com.coursescheduler.dto.RepairTaskResponse;
import com.coursescheduler.dto.RoomOutageAdjustRequest;
import com.coursescheduler.dto.RoomOutageCancelRequest;
import com.coursescheduler.dto.RoomOutageCreateRequest;
import com.coursescheduler.dto.RoomOutageResponse;
import com.coursescheduler.exception.InvalidRequestParameterException;
import com.coursescheduler.exception.RepairTaskNotFoundException;
import com.coursescheduler.exception.RoomOutageConflictException;
import com.coursescheduler.exception.RoomOutageNotFoundException;
import com.coursescheduler.model.AffectedCourse;
import com.coursescheduler.model.CourseSchedule;
import com.coursescheduler.model.OperationType;
import com.coursescheduler.model.OutageRangeHistory;
import com.coursescheduler.model.RepairTask;
import com.coursescheduler.model.RepairTaskStatus;
import com.coursescheduler.model.RoomOutage;
import com.coursescheduler.model.RoomOutageStatus;
import com.coursescheduler.util.TimeSlotUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 教室临时停用服务。
 *
 * <p>业务规则：
 * <ul>
 *   <li>停用事件指定教室、停用时间段、原因与外部事件号；生效后找出所有冲突课程，
 *       冻结为一份修复任务（快照课程内容与版本）；</li>
 *   <li>相同外部事件号相同内容重复提交幂等返回，同号异内容冲突；</li>
 *   <li>停用范围调整走乐观版本校验，新增冲突的课程追加进同一修复任务；</li>
 *   <li>停用取消不自动回退已完成的修复，仅关闭修复任务（未修复课程保持原状）；</li>
 *   <li>所有读写在 {@link SchedulingLocks} 共享写锁内，与课程增改、普通调课、
 *       修复确认互斥，保证看到的停用范围与课程安排彼此一致。</li>
 * </ul>
 */
@Service
public class RoomOutageService {

    private final CourseScheduleService courseScheduleService;
    private final RoomOutageRegistry outageRegistry;
    private final RepairTaskRegistry taskRegistry;
    private final AuditLogService auditLogService;
    private final SchedulingLocks schedulingLocks;
    private final Clock clock;

    @Autowired
    public RoomOutageService(CourseScheduleService courseScheduleService,
                             RoomOutageRegistry outageRegistry,
                             RepairTaskRegistry taskRegistry,
                             AuditLogService auditLogService,
                             SchedulingLocks schedulingLocks,
                             Clock clock) {
        this.courseScheduleService = courseScheduleService;
        this.outageRegistry = outageRegistry;
        this.taskRegistry = taskRegistry;
        this.auditLogService = auditLogService;
        this.schedulingLocks = schedulingLocks;
        this.clock = clock;
    }

    /**
     * 提交结果：replayed=true 表示命中幂等，返回首次生效的事件。
     */
    public static class CreateOutcome {
        private final RoomOutageResponse response;
        private final boolean replayed;

        CreateOutcome(RoomOutageResponse response, boolean replayed) {
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

    public CreateOutcome createOutage(RoomOutageCreateRequest request) {
        if (request == null) {
            throw new InvalidRequestParameterException("请求不能为空");
        }
        String eventNo = normalizeRequired(request.getEventNo(), "外部事件号");
        String classroom = normalizeRequired(request.getClassroom(), "教室");
        String reason = normalizeRequired(request.getReason(), "停用原因");
        List<String> timeSlots = normalizeTimeSlots(request.getTimeSlots());
        String operator = normalizeOptional(request.getOperator());
        String fingerprint = fingerprint(classroom, reason, timeSlots);

        schedulingLocks.getLock().writeLock().lock();
        try {
            RoomOutage existing = outageRegistry.getByEventNo(eventNo);
            if (existing != null) {
                if (existing.getCreateFingerprint().equals(fingerprint)) {
                    return new CreateOutcome(toResponse(existing), true);
                }
                throw new RoomOutageConflictException(
                        "相同外部事件号已提交过不同内容的停用事件，事件号: " + eventNo);
            }

            LocalDateTime now = LocalDateTime.now(clock);
            RoomOutage outage = new RoomOutage();
            outage.setId(outageRegistry.nextId());
            outage.setEventNo(eventNo);
            outage.setClassroom(classroom);
            outage.setReason(reason);
            outage.setTimeSlots(new ArrayList<>(timeSlots));
            outage.setStatus(RoomOutageStatus.ACTIVE);
            outage.setVersion(1);
            outage.setCreateFingerprint(fingerprint);
            outage.setCreatedBy(operator);
            outage.setCreatedAt(now);
            outage.getRangeHistory().add(new OutageRangeHistory(
                    new ArrayList<>(timeSlots), reason, 1, now, operator));
            outageRegistry.put(outage);

            List<CourseSchedule> snapshot = courseScheduleService.snapshotAllSchedules();
            List<AffectedCourse> affected = findAffectedCourses(snapshot, outage);
            RepairTask task = null;
            if (!affected.isEmpty()) {
                task = new RepairTask(taskRegistry.nextId(), outage.getId(), eventNo,
                        RepairTaskStatus.OPEN, affected, now);
                taskRegistry.put(task);
                outage.setRepairTaskId(task.getId());
            }

            auditLogService.recordLog(OperationType.ROOM_OUTAGE_CREATE, null,
                    "教室停用", null, classroom, String.join("; ", timeSlots),
                    true, "停用生效，外部事件号: " + eventNo + "，原因: " + reason
                            + "，受影响课程 " + affected.size() + " 门",
                    operator, eventNo);
            for (AffectedCourse course : affected) {
                auditLogService.recordLog(OperationType.ROOM_OUTAGE_CREATE,
                        course.getScheduleId(), course.getCourseName(), course.getTeacherName(),
                        course.getClassroom(), course.getTimeSlot(), true,
                        "因教室停用（" + reason + "）冻结为修复任务", operator, eventNo);
            }

            return new CreateOutcome(toResponse(outage), false);
        } finally {
            schedulingLocks.getLock().writeLock().unlock();
        }
    }

    public RoomOutageResponse adjustOutage(Long outageId, RoomOutageAdjustRequest request) {
        if (request == null) {
            throw new InvalidRequestParameterException("请求不能为空");
        }
        schedulingLocks.getLock().writeLock().lock();
        try {
            RoomOutage outage = findOutageOrThrow(outageId);
            if (outage.getStatus() != RoomOutageStatus.ACTIVE) {
                throw new RoomOutageConflictException(
                        "停用事件已取消，不能调整范围，事件号: " + outage.getEventNo());
            }
            if (request.getExpectedVersion() != null
                    && request.getExpectedVersion() != outage.getVersion()) {
                throw new RoomOutageConflictException(
                        "停用事件版本已变化（期望版本 " + request.getExpectedVersion()
                                + "，当前版本 " + outage.getVersion()
                                + "），存在并发的范围调整，事件号: " + outage.getEventNo());
            }

            List<String> newTimeSlots = request.getTimeSlots() != null
                    ? normalizeTimeSlots(request.getTimeSlots())
                    : new ArrayList<>(outage.getTimeSlots());
            String newReason = request.getReason() != null && !request.getReason().trim().isEmpty()
                    ? request.getReason().trim()
                    : outage.getReason();
            String operator = normalizeOptional(request.getOperator());

            outage.setTimeSlots(new ArrayList<>(newTimeSlots));
            outage.setReason(newReason);
            outage.setVersion(outage.getVersion() + 1);
            LocalDateTime now = LocalDateTime.now(clock);
            outage.setUpdatedBy(operator);
            outage.setUpdatedAt(now);
            outage.getRangeHistory().add(new OutageRangeHistory(
                    new ArrayList<>(newTimeSlots), newReason, outage.getVersion(), now, operator));

            // 范围调整后：把新增冲突的课程（含已修复后又落入新范围的课程）
            // 追加/重开进同一修复任务；已经删除或已移出停用时段的冻结课程标记为自然消解。
            RepairTask task = outage.getRepairTaskId() != null
                    ? taskRegistry.getById(outage.getRepairTaskId())
                    : null;
            List<CourseSchedule> snapshot = courseScheduleService.snapshotAllSchedules();
            List<AffectedCourse> newlyAffected = findAffectedCourses(snapshot, outage);
            if (task == null) {
                if (!newlyAffected.isEmpty()) {
                    task = new RepairTask(taskRegistry.nextId(), outage.getId(), outage.getEventNo(),
                            RepairTaskStatus.OPEN, newlyAffected, now);
                    taskRegistry.put(task);
                    outage.setRepairTaskId(task.getId());
                }
            } else {
                boolean added = mergeAffectedCourses(task, newlyAffected);
                if (added && task.getStatus() == RepairTaskStatus.RESOLVED) {
                    // 范围扩大后再次出现受影响课程，任务重新打开
                    task.setStatus(RepairTaskStatus.OPEN);
                    task.setResolvedAt(null);
                    task.setConfirmedRepairPlanId(null);
                }
                if (task.getStatus() == RepairTaskStatus.OPEN) {
                    reconcileTask(task, snapshot);
                    refreshTaskResolvedStatus(task, now);
                }
            }

            auditLogService.recordLog(OperationType.ROOM_OUTAGE_ADJUST, null,
                    "教室停用", null, outage.getClassroom(), String.join("; ", newTimeSlots),
                    true, "停用范围/原因调整，新版本: " + outage.getVersion()
                            + "，外部事件号: " + outage.getEventNo(),
                    operator, outage.getEventNo());
            return toResponse(outage);
        } finally {
            schedulingLocks.getLock().writeLock().unlock();
        }
    }

    public RoomOutageResponse cancelOutage(Long outageId, RoomOutageCancelRequest request) {
        String operator = request != null ? normalizeOptional(request.getOperator()) : null;
        schedulingLocks.getLock().writeLock().lock();
        try {
            RoomOutage outage = findOutageOrThrow(outageId);
            if (outage.getStatus() == RoomOutageStatus.CANCELLED) {
                throw new RoomOutageConflictException(
                        "停用事件已取消，不能重复取消，事件号: " + outage.getEventNo());
            }
            outage.setStatus(RoomOutageStatus.CANCELLED);
            outage.setCancelledBy(operator);
            LocalDateTime now = LocalDateTime.now(clock);
            outage.setCancelledAt(now);

            RepairTask task = outage.getRepairTaskId() != null
                    ? taskRegistry.getById(outage.getRepairTaskId())
                    : null;
            if (task != null && task.getStatus() == RepairTaskStatus.OPEN) {
                // 取消不自动回退已完成的修复；仍待修复的课程保持原状，任务关闭。
                task.setStatus(RepairTaskStatus.CANCELLED);
            }

            auditLogService.recordLog(OperationType.ROOM_OUTAGE_CANCEL, null,
                    "教室停用", null, outage.getClassroom(), String.join("; ", outage.getTimeSlots()),
                    true, "停用取消，已完成的修复不自动回退，外部事件号: " + outage.getEventNo(),
                    operator, outage.getEventNo());
            return toResponse(outage);
        } finally {
            schedulingLocks.getLock().writeLock().unlock();
        }
    }

    public RoomOutageResponse getOutage(Long outageId) {
        schedulingLocks.getLock().readLock().lock();
        try {
            return toResponse(findOutageOrThrow(outageId));
        } finally {
            schedulingLocks.getLock().readLock().unlock();
        }
    }

    public List<RoomOutageResponse> listOutages() {
        schedulingLocks.getLock().readLock().lock();
        try {
            return outageRegistry.listAll().stream()
                    .map(this::toResponse)
                    .collect(Collectors.toList());
        } finally {
            schedulingLocks.getLock().readLock().unlock();
        }
    }

    public RepairTaskResponse getRepairTask(Long taskId) {
        schedulingLocks.getLock().readLock().lock();
        try {
            RepairTask task = taskRegistry.getById(taskId);
            if (task == null) {
                throw new RepairTaskNotFoundException("修复任务不存在，ID: " + taskId);
            }
            return OutageResponseSupport.toTaskResponse(task);
        } finally {
            schedulingLocks.getLock().readLock().unlock();
        }
    }

    public List<RepairTaskResponse> listRepairTasks() {
        schedulingLocks.getLock().readLock().lock();
        try {
            return taskRegistry.listAll().stream()
                    .map(OutageResponseSupport::toTaskResponse)
                    .collect(Collectors.toList());
        } finally {
            schedulingLocks.getLock().readLock().unlock();
        }
    }

    /**
     * 核对修复任务：冻结后已被删除、或已由其他途径移出停用时段的课程标记为自然消解。
     * 调用方须持有写锁。
     */
    public void reconcileTask(RepairTask task, List<CourseSchedule> currentSchedules) {
        if (task.getStatus() != RepairTaskStatus.OPEN) {
            return;
        }
        List<RoomOutage> activeOutages = outageRegistry.activeOutages();
        for (AffectedCourse course : task.getAffectedCourses()) {
            if (course.isResolved()) {
                continue;
            }
            CourseSchedule current = findSchedule(currentSchedules, course.getScheduleId());
            if (current == null) {
                course.setResolved(true);
                course.setResolvedReason("冻结后课程安排已被删除，无需修复");
                continue;
            }
            RoomOutage blocker = RoomOutageBlockSupport.findBlockingOutage(
                    activeOutages, current.getClassroom(), current.getTimeSlot());
            if (blocker == null) {
                course.setResolved(true);
                course.setResolvedReason("冻结后课程已移出停用时段，无需修复");
            }
        }
    }

    /**
     * 若任务内课程全部消解/修复，则置为 RESOLVED。调用方须持有写锁。
     */
    public void refreshTaskResolvedStatus(RepairTask task, LocalDateTime resolvedAt) {
        if (task.getStatus() != RepairTaskStatus.OPEN) {
            return;
        }
        boolean allResolved = task.getAffectedCourses().stream().allMatch(AffectedCourse::isResolved);
        if (allResolved) {
            task.setStatus(RepairTaskStatus.RESOLVED);
            task.setResolvedAt(resolvedAt);
        }
    }

    /**
     * 合并当前受影响课程到任务：
     * 未冻结过的课程追加为待修复；已标记消解/修复但当前再次落入停用范围的课程重新打开。
     *
     * @return 是否有课程被追加或重新打开（即任务是否需要处于 OPEN）
     */
    private boolean mergeAffectedCourses(RepairTask task, List<AffectedCourse> currentAffected) {
        Map<Long, AffectedCourse> knownById = new LinkedHashMap<>();
        for (AffectedCourse course : task.getAffectedCourses()) {
            knownById.put(course.getScheduleId(), course);
        }
        boolean changed = false;
        for (AffectedCourse course : currentAffected) {
            AffectedCourse known = knownById.get(course.getScheduleId());
            if (known == null) {
                task.getAffectedCourses().add(course);
                changed = true;
            } else if (known.isResolved()
                    && !"冻结后课程安排已被删除，无需修复".equals(known.getResolvedReason())
                    && !"冻结后课程已移出停用时段，无需修复".equals(known.getResolvedReason())) {
                // 已修复的课程因范围扩大再次落入停用时段，需要重新修复
                known.setResolved(false);
                known.setResolvedReason(null);
                changed = true;
            }
        }
        return changed;
    }

    private List<AffectedCourse> findAffectedCourses(List<CourseSchedule> snapshot, RoomOutage outage) {
        List<AffectedCourse> affected = new ArrayList<>();
        for (CourseSchedule schedule : snapshot) {
            if (RoomOutageBlockSupport.conflictsWithOutage(schedule, outage)) {
                affected.add(new AffectedCourse(
                        schedule.getId(),
                        schedule.getCourseName(),
                        schedule.getTeacherName(),
                        schedule.getClassroom(),
                        schedule.getTimeSlot(),
                        schedule.getVersion()));
            }
        }
        return affected;
    }

    private CourseSchedule findSchedule(List<CourseSchedule> schedules, Long id) {
        for (CourseSchedule schedule : schedules) {
            if (schedule.getId().equals(id)) {
                return schedule;
            }
        }
        return null;
    }

    private RoomOutage findOutageOrThrow(Long outageId) {
        if (outageId == null) {
            throw new RoomOutageNotFoundException("停用事件不存在，ID: null");
        }
        RoomOutage outage = outageRegistry.getById(outageId);
        if (outage == null) {
            throw new RoomOutageNotFoundException("停用事件不存在，ID: " + outageId);
        }
        return outage;
    }

    private RoomOutageResponse toResponse(RoomOutage outage) {
        RepairTask task = outage.getRepairTaskId() != null
                ? taskRegistry.getById(outage.getRepairTaskId())
                : null;
        return OutageResponseSupport.toOutageResponse(outage, task);
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

    private List<String> normalizeTimeSlots(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            throw new InvalidRequestParameterException("停用时间段至少包含一个");
        }
        List<String> normalized = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String slot : raw) {
            if (slot == null || slot.trim().isEmpty()) {
                throw new InvalidRequestParameterException("停用时间段不能为空");
            }
            String value = TimeSlotUtils.normalize(slot);
            if (seen.add(value)) {
                normalized.add(value);
            }
        }
        return normalized;
    }

    private String fingerprint(String classroom, String reason, List<String> timeSlots) {
        return classroom + "|" + reason + "|" + String.join(";", timeSlots);
    }

    public void resetForTesting() {
        schedulingLocks.getLock().writeLock().lock();
        try {
            outageRegistry.reset();
            taskRegistry.reset();
        } finally {
            schedulingLocks.getLock().writeLock().unlock();
        }
    }
}
