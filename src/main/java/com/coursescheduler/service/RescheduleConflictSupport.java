package com.coursescheduler.service;

import com.coursescheduler.dto.RescheduleConflictDTO;
import com.coursescheduler.model.CourseSchedule;
import com.coursescheduler.model.ReschedulePlanItem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 调课方案冲突计算。
 *
 * <p>核心规则：同一方案内课程即将腾出的旧时段/旧教室不再视为占用，
 * 因此允许两门或多门课程互换时段；但方案内课程调整后的新时段/新教室
 * 不得与未参与调课的课程产生教师或教室冲突，方案内部的新安排之间也不得冲突。
 */
public class RescheduleConflictSupport {

    private RescheduleConflictSupport() {
    }

    /**
     * 计算整份调课方案的冲突明细。返回空列表表示方案可以生效。
     *
     * @param schedules 当前全部课程安排
     * @param items     方案内的调整项（携带提交时保存的原始排课内容）
     */
    public static List<RescheduleConflictDTO> computeConflicts(
            List<CourseSchedule> schedules,
            List<ReschedulePlanItem> items
    ) {
        List<RescheduleConflictDTO> conflicts = new ArrayList<>();

        Set<Long> planScheduleIds = new HashSet<>();
        for (ReschedulePlanItem item : items) {
            planScheduleIds.add(item.getScheduleId());
        }

        Map<Long, CourseSchedule> schedulesById = new HashMap<>();
        for (CourseSchedule s : schedules) {
            schedulesById.put(s.getId(), s);
        }

        // 1. 校验每条调整引用的课程仍然存在且与提交方案时看到的内容一致
        List<ReschedulePlanItem> validItems = new ArrayList<>();
        for (ReschedulePlanItem item : items) {
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
            validItems.add(item);
        }

        // 2. 与方案外课程的教师/教室冲突（方案内课程即将腾出的旧时段/旧教室不计入占用）
        for (ReschedulePlanItem item : validItems) {
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
                ReschedulePlanItem a = validItems.get(i);
                ReschedulePlanItem b = validItems.get(j);
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

        return conflicts;
    }

    private static void addInternalConflict(List<RescheduleConflictDTO> conflicts,
                                            ReschedulePlanItem a, ReschedulePlanItem b,
                                            RescheduleConflictDTO.ConflictType type,
                                            String message) {
        conflicts.add(new RescheduleConflictDTO(
                a.getScheduleId(), type, b.getScheduleId(), b.getCourseName(), message));
        conflicts.add(new RescheduleConflictDTO(
                b.getScheduleId(), type, a.getScheduleId(), a.getCourseName(), message));
    }
}
