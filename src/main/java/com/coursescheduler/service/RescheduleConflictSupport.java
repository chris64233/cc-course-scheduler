package com.coursescheduler.service;

import com.coursescheduler.dto.RescheduleConflictDTO;
import com.coursescheduler.model.CourseSchedule;
import com.coursescheduler.model.PlanChangeItem;
import com.coursescheduler.model.RoomOutage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 调课/修复方案冲突计算。
 *
 * <p>核心规则：同一方案内课程即将腾出的旧时段/旧教室不再视为占用，
 * 因此允许两门或多门课程互换时段；但方案内课程调整后的新时段/新教室
 * 不得与未参与方案的课程产生教师或教室冲突，方案内部的新安排之间也不得冲突；
 * 新安排不得落入当前生效中的教室停用时段；引用的课程必须仍存在、
 * 内容与版本与提交方案时一致。
 */
public class RescheduleConflictSupport {

    private RescheduleConflictSupport() {
    }

    /**
     * 计算整份方案的冲突明细。返回空列表表示方案可以生效。
     *
     * @param schedules      当前全部课程安排
     * @param items          方案内的调整项（携带提交时保存的原始排课内容与版本）
     * @param activeOutages  当前生效中的教室停用事件
     */
    public static List<RescheduleConflictDTO> computeConflicts(
            List<CourseSchedule> schedules,
            List<? extends PlanChangeItem> items,
            List<RoomOutage> activeOutages
    ) {
        List<RescheduleConflictDTO> conflicts = new ArrayList<>();

        Set<Long> planScheduleIds = new HashSet<>();
        for (PlanChangeItem item : items) {
            planScheduleIds.add(item.getScheduleId());
        }

        Map<Long, CourseSchedule> schedulesById = new HashMap<>();
        for (CourseSchedule s : schedules) {
            schedulesById.put(s.getId(), s);
        }

        // 1. 校验每条调整引用的课程仍然存在且与提交方案时看到的内容、版本一致
        List<PlanChangeItem> validItems = new ArrayList<>();
        for (PlanChangeItem item : items) {
            CourseSchedule current = schedulesById.get(item.getScheduleId());
            if (current == null) {
                conflicts.add(new RescheduleConflictDTO(
                        item.getScheduleId(),
                        RescheduleConflictDTO.ConflictType.SCHEDULE_NOT_FOUND,
                        null,
                        null,
                        "课程安排已被删除，ID: " + item.getScheduleId()));
                continue;
            }
            if (item.getOriginalVersion() != null && current.getVersion() != item.getOriginalVersion()) {
                conflicts.add(new RescheduleConflictDTO(
                        item.getScheduleId(),
                        RescheduleConflictDTO.ConflictType.VERSION_CONFLICT,
                        null,
                        null,
                        "课程安排版本已变化（提交方案时版本 " + item.getOriginalVersion()
                                + "，当前版本 " + current.getVersion() + "），存在并发的较新安排，ID: "
                                + item.getScheduleId()));
            }
            if (!current.getCourseName().equals(item.getCourseName())
                    || !current.getTeacherName().equals(item.getTeacherName())
                    || !current.getClassroom().equals(item.getOriginalClassroom())
                    || !current.getTimeSlot().equals(item.getOriginalTimeSlot())) {
                conflicts.add(new RescheduleConflictDTO(
                        item.getScheduleId(),
                        RescheduleConflictDTO.ConflictType.SCHEDULE_CHANGED,
                        null,
                        null,
                        "课程安排已被他人修改，与提交调课方案时的内容不一致，ID: " + item.getScheduleId()));
                continue;
            }
            if (item.getOriginalVersion() != null && current.getVersion() != item.getOriginalVersion()) {
                // 版本变化但内容恰好相同：仍视为并发修改，不参与后续安排
                continue;
            }
            validItems.add(item);
        }

        // 2. 与方案外课程的教师/教室冲突（方案内课程即将腾出的旧时段/旧教室不计入占用）
        for (PlanChangeItem item : validItems) {
            for (CourseSchedule existing : schedules) {
                if (planScheduleIds.contains(existing.getId())) {
                    continue;
                }
                if (ScheduleConflictSupport.timeSlotsOverlap(existing.getTimeSlot(), item.getNewTimeSlot())) {
                    if (existing.getTeacherName().equals(item.getTeacherName())) {
                        conflicts.add(new RescheduleConflictDTO(
                                item.getScheduleId(),
                                RescheduleConflictDTO.ConflictType.TEACHER,
                                existing.getId(),
                                existing.getCourseName(),
                                ScheduleConflictSupport.buildConflictReason(
                                        ScheduleConflictSupport.ConflictType.TEACHER,
                                        item.getTeacherName(), item.getNewTimeSlot(), "已有课程安排")));
                    }
                    if (existing.getClassroom().equals(item.getNewClassroom())) {
                        conflicts.add(new RescheduleConflictDTO(
                                item.getScheduleId(),
                                RescheduleConflictDTO.ConflictType.CLASSROOM,
                                existing.getId(),
                                existing.getCourseName(),
                                ScheduleConflictSupport.buildConflictReason(
                                        ScheduleConflictSupport.ConflictType.CLASSROOM,
                                        item.getNewClassroom(), item.getNewTimeSlot(), "已有课程安排")));
                    }
                }
            }
        }

        // 3. 方案内部调整后的新安排之间的教师/教室冲突
        for (int i = 0; i < validItems.size(); i++) {
            for (int j = i + 1; j < validItems.size(); j++) {
                PlanChangeItem a = validItems.get(i);
                PlanChangeItem b = validItems.get(j);
                if (!ScheduleConflictSupport.timeSlotsOverlap(a.getNewTimeSlot(), b.getNewTimeSlot())) {
                    continue;
                }
                if (a.getTeacherName().equals(b.getTeacherName())) {
                    addInternalConflict(conflicts, a, b, RescheduleConflictDTO.ConflictType.INTERNAL_TEACHER,
                            ScheduleConflictSupport.buildConflictReason(
                                    ScheduleConflictSupport.ConflictType.TEACHER,
                                    a.getTeacherName(), a.getNewTimeSlot(), "与方案内其他课程调整冲突"));
                }
                if (a.getNewClassroom().equals(b.getNewClassroom())) {
                    addInternalConflict(conflicts, a, b, RescheduleConflictDTO.ConflictType.INTERNAL_CLASSROOM,
                            ScheduleConflictSupport.buildConflictReason(
                                    ScheduleConflictSupport.ConflictType.CLASSROOM,
                                    a.getNewClassroom(), a.getNewTimeSlot(), "与方案内其他课程调整冲突"));
                }
            }
        }

        // 4. 新安排落入当前生效中的教室停用时段
        if (activeOutages != null) {
            for (PlanChangeItem item : validItems) {
                RoomOutage outage = RoomOutageBlockSupport.findBlockingOutage(
                        activeOutages, item.getNewClassroom(), item.getNewTimeSlot());
                if (outage != null) {
                    conflicts.add(new RescheduleConflictDTO(
                            item.getScheduleId(),
                            RescheduleConflictDTO.ConflictType.OUTAGE_BLOCKED,
                            null,
                            null,
                            RoomOutageBlockSupport.describeBlock(outage, item.getNewTimeSlot())));
                }
            }
        }

        return conflicts;
    }

    private static void addInternalConflict(List<RescheduleConflictDTO> conflicts,
                                            PlanChangeItem a, PlanChangeItem b,
                                            RescheduleConflictDTO.ConflictType type,
                                            String message) {
        conflicts.add(new RescheduleConflictDTO(
                a.getScheduleId(), type, b.getScheduleId(), b.getCourseName(), message));
        conflicts.add(new RescheduleConflictDTO(
                b.getScheduleId(), type, a.getScheduleId(), a.getCourseName(), message));
    }
}
