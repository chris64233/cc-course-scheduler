package com.coursescheduler.service;

import com.coursescheduler.dto.RescheduleConflictDTO;
import com.coursescheduler.dto.ReschedulePlanItemRequest;
import com.coursescheduler.dto.ReschedulePlanItemResponse;
import com.coursescheduler.dto.ReschedulePlanResponse;
import com.coursescheduler.dto.ReschedulePlanSubmitRequest;
import com.coursescheduler.dto.ReschedulePreCheckResponse;
import com.coursescheduler.exception.InvalidRequestParameterException;
import com.coursescheduler.exception.RescheduleConflictException;
import com.coursescheduler.exception.ReschedulePlanNotFoundException;
import com.coursescheduler.exception.ReschedulePlanStateException;
import com.coursescheduler.exception.ScheduleNotFoundException;
import com.coursescheduler.model.CourseSchedule;
import com.coursescheduler.model.ReschedulePlan;
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
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * 多门课程原子调课方案服务。
 *
 * <p>业务规则：
 * <ul>
 *   <li>方案包含多条现有课程安排的调整（新星期/时段、新教室），引用的课程安排互不重复；</li>
 *   <li>提交时保存每条课程的原始排课内容，确认时据此判断是否被他人修改；</li>
 *   <li>预检查把方案内即将腾出的旧时段/旧教室视为可用，允许互换时段，
 *       但不允许与方案外课程产生教师/教室冲突；</li>
 *   <li>确认时一次性应用全部变更，任一校验失败则整份方案不生效；</li>
 *   <li>调课业务号（bizKey）幂等：相同业务号相同内容重复提交返回首次结果，
 *       内容不同则冲突；方案确认或拒绝后不可再次处理。</li>
 * </ul>
 */
@Service
public class ReschedulePlanService {

    private final CourseScheduleService courseScheduleService;
    private final Clock clock;

    private final Map<String, ReschedulePlan> plansByBizKey = new LinkedHashMap<>();
    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();
    private long idGenerator = 1;

    @Autowired
    public ReschedulePlanService(CourseScheduleService courseScheduleService, Clock clock) {
        this.courseScheduleService = courseScheduleService;
        this.clock = clock;
    }

    /**
     * 提交结果：response 为方案内容，replayed 为 true 表示命中幂等返回的是首次提交的结果。
     */
    public static class SubmitOutcome {
        private final ReschedulePlanResponse response;
        private final boolean replayed;

        SubmitOutcome(ReschedulePlanResponse response, boolean replayed) {
            this.response = response;
            this.replayed = replayed;
        }

        public ReschedulePlanResponse getResponse() {
            return response;
        }

        public boolean isReplayed() {
            return replayed;
        }
    }

    public SubmitOutcome submit(ReschedulePlanSubmitRequest request) {
        if (request == null) {
            throw new InvalidRequestParameterException("请求不能为空");
        }
        String bizKey = request.getBizKey();
        if (bizKey == null || bizKey.trim().isEmpty()) {
            throw new InvalidRequestParameterException("调课业务号不能为空");
        }
        bizKey = bizKey.trim();

        List<ReschedulePlanItemRequest> itemRequests = request.getItems();
        if (itemRequests == null || itemRequests.isEmpty()) {
            throw new InvalidRequestParameterException("调课方案至少包含一条课程调整");
        }

        // 规范化并校验每条调整
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
                        "调课方案引用的课程安排重复，ID: " + item.getScheduleId());
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
            ReschedulePlan existing = plansByBizKey.get(bizKey);
            if (existing != null) {
                if (existing.getFingerprint().equals(fingerprint)) {
                    return new SubmitOutcome(toResponse(existing), true);
                }
                throw new ReschedulePlanStateException(
                        "相同调课业务号已提交过不同内容的方案，业务号: " + bizKey);
            }

            Set<Long> scheduleIds = itemRequests.stream()
                    .map(ReschedulePlanItemRequest::getScheduleId)
                    .collect(Collectors.toSet());
            Map<Long, CourseSchedule> snapshotsById = new HashMap<>();
            for (CourseSchedule s : courseScheduleService.snapshotSchedulesByIds(scheduleIds)) {
                snapshotsById.put(s.getId(), s);
            }

            List<ReschedulePlanItem> items = new ArrayList<>();
            for (int i = 0; i < itemRequests.size(); i++) {
                Long scheduleId = itemRequests.get(i).getScheduleId();
                CourseSchedule snapshot = snapshotsById.get(scheduleId);
                if (snapshot == null) {
                    throw new ScheduleNotFoundException("课程安排不存在，ID: " + scheduleId);
                }
                items.add(new ReschedulePlanItem(
                        scheduleId,
                        snapshot.getCourseName(),
                        snapshot.getTeacherName(),
                        snapshot.getClassroom(),
                        snapshot.getTimeSlot(),
                        normalizedClassrooms.get(i),
                        normalizedTimeSlots.get(i)));
            }

            ReschedulePlan plan = new ReschedulePlan();
            plan.setId(idGenerator++);
            plan.setBizKey(bizKey);
            plan.setFingerprint(fingerprint);
            plan.setStatus(ReschedulePlanStatus.PENDING);
            plan.setItems(items);
            plan.setCreatedAt(LocalDateTime.now(clock));
            plansByBizKey.put(bizKey, plan);

            return new SubmitOutcome(toResponse(plan), false);
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    public ReschedulePreCheckResponse preCheck(Long planId) {
        rwLock.writeLock().lock();
        try {
            ReschedulePlan plan = findPlanOrThrow(planId);
            ensurePending(plan, "预检查");

            List<CourseSchedule> snapshot = courseScheduleService.snapshotAllSchedules();
            List<RescheduleConflictDTO> conflicts =
                    RescheduleConflictSupport.computeConflicts(snapshot, plan.getItems());

            plan.setConflicts(conflicts);
            plan.setResultMessage(conflicts.isEmpty() ? "预检查通过，方案可以确认" : "预检查发现 " + conflicts.size() + " 项冲突");

            return new ReschedulePreCheckResponse(plan.getId(), conflicts.isEmpty(), conflicts.size(), conflicts);
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    public ReschedulePlanResponse confirm(Long planId) {
        rwLock.writeLock().lock();
        try {
            ReschedulePlan plan = findPlanOrThrow(planId);
            ensurePending(plan, "确认");

            List<RescheduleConflictDTO> conflicts =
                    courseScheduleService.applyRescheduleAtomically(plan.getItems());
            if (!conflicts.isEmpty()) {
                plan.setConflicts(conflicts);
                plan.setResultMessage("调课失败：存在 " + conflicts.size() + " 项冲突，整份方案未生效");
                throw new RescheduleConflictException(
                        "调课方案存在冲突，整份方案未生效", conflicts);
            }

            plan.setStatus(ReschedulePlanStatus.CONFIRMED);
            plan.setConflicts(new ArrayList<>());
            plan.setResultMessage("调课成功，全部 " + plan.getItems().size() + " 条课程调整已生效");
            plan.setProcessedAt(LocalDateTime.now(clock));
            return toResponse(plan);
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    public ReschedulePlanResponse reject(Long planId) {
        rwLock.writeLock().lock();
        try {
            ReschedulePlan plan = findPlanOrThrow(planId);
            ensurePending(plan, "拒绝");

            plan.setStatus(ReschedulePlanStatus.REJECTED);
            plan.setResultMessage("调课方案已拒绝");
            plan.setProcessedAt(LocalDateTime.now(clock));
            return toResponse(plan);
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    public ReschedulePlanResponse getPlan(Long planId) {
        rwLock.readLock().lock();
        try {
            return toResponse(findPlanOrThrow(planId));
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public List<ReschedulePlanResponse> listPlans() {
        rwLock.readLock().lock();
        try {
            return plansByBizKey.values().stream()
                    .map(this::toResponse)
                    .collect(Collectors.toList());
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public void resetForTesting() {
        rwLock.writeLock().lock();
        try {
            plansByBizKey.clear();
            idGenerator = 1;
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    private ReschedulePlan findPlanOrThrow(Long planId) {
        if (planId == null) {
            throw new ReschedulePlanNotFoundException("调课方案不存在，ID: null");
        }
        for (ReschedulePlan plan : plansByBizKey.values()) {
            if (plan.getId().equals(planId)) {
                return plan;
            }
        }
        throw new ReschedulePlanNotFoundException("调课方案不存在，ID: " + planId);
    }

    private void ensurePending(ReschedulePlan plan, String operation) {
        if (plan.getStatus() != ReschedulePlanStatus.PENDING) {
            String statusText = plan.getStatus() == ReschedulePlanStatus.CONFIRMED ? "已确认" : "已拒绝";
            throw new ReschedulePlanStateException(
                    "调课方案" + statusText + "，不可再次" + operation + "，方案 ID: " + plan.getId());
        }
    }

    private String buildFingerprint(List<ReschedulePlanItemRequest> itemRequests,
                                    List<String> normalizedClassrooms,
                                    List<String> normalizedTimeSlots) {
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < itemRequests.size(); i++) {
            parts.add(itemRequests.get(i).getScheduleId()
                    + "|" + normalizedClassrooms.get(i)
                    + "|" + normalizedTimeSlots.get(i));
        }
        parts.sort(String::compareTo);
        return String.join(";", parts);
    }

    private ReschedulePlanResponse toResponse(ReschedulePlan plan) {
        ReschedulePlanResponse response = new ReschedulePlanResponse();
        response.setId(plan.getId());
        response.setBizKey(plan.getBizKey());
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
        response.setCreatedAt(plan.getCreatedAt());
        response.setProcessedAt(plan.getProcessedAt());
        return response;
    }
}
