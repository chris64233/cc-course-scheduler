package com.coursescheduler.service;

import com.coursescheduler.model.CourseSchedule;
import com.coursescheduler.model.RescheduleIssue;
import com.coursescheduler.model.RescheduleIssueSource;
import com.coursescheduler.model.RescheduleIssueType;
import com.coursescheduler.model.RescheduleItem;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 原子调课方案冲突评估。
 *
 * <p>评估分两步并返回整份方案的全部冲突明细（任一不合法也不提前中断）：
 * <ol>
 *   <li>快照校验：每条引用必须仍存在，且提交时冻结的原始排课内容未被他人修改；</li>
 *   <li>目标资源冲突：以“方案应用后的状态”做检查——方案内课程即将腾出的旧时段先被释放，
 *       因此两门或多门课程交换时段合法；但仍要阻止与未参与调课的课程发生教师/教室冲突。</li>
 * </ol>
 *
 * <p>方案内冲突判定：若两条安排新时段重叠，且教师相同或教室相同，则报内部冲突。
 * 新时段等于（或重叠于）自身的旧时段（即并未腾出）时，同样与未参与调课课程一视同仁处理。
 */
public final class RescheduleConflictSupport {

    private RescheduleConflictSupport() {
    }

    /**
     * 评估整份方案。
     *
     * @param items    方案条目（itemIndex 已在条目上给出）
     * @param scheduleById 当前排课按 ID 的索引
     * @return 全部冲突明细（已排序），为空表示方案可直接生效
     */
    public static List<RescheduleIssue> evaluate(
            List<RescheduleItem> items,
            Map<Long, CourseSchedule> scheduleById) {
        List<RescheduleIssue> issues = new ArrayList<>();

        boolean[] stale = new boolean[items.size()];
        for (RescheduleItem item : items) {
            int i = item.getItemIndex();
            CourseSchedule current = scheduleById.get(item.getScheduleId());
            if (current == null) {
                stale[i] = true;
                issues.add(new RescheduleIssue(
                        i, item.getScheduleId(), RescheduleIssueType.SCHEDULE_DELETED,
                        RescheduleIssueSource.STALE_PLAN,
                        item.getOriginalCourseName(), item.getOriginalTeacherName(),
                        item.getOriginalClassroom(), item.getOriginalTimeSlot(),
                        null,
                        "课程安排（ID: " + item.getScheduleId() + "）已被删除，调课方案引用的安排不存在"));
            } else if (!isUnchanged(item, current)) {
                stale[i] = true;
                issues.add(new RescheduleIssue(
                        i, current.getId(), RescheduleIssueType.SCHEDULE_CHANGED,
                        RescheduleIssueSource.STALE_PLAN,
                        current.getCourseName(), current.getTeacherName(),
                        current.getClassroom(), current.getTimeSlot(),
                        null,
                        "课程 " + current.getCourseName() + "（ID: " + current.getId()
                                + "）在方案提交后已被修改，原始排课内容已过期"));
            }
        }

        // 目标资源视角：参与方案的课程以“教师不变、新教室/新时段”参与计算；
        // 未参与方案的课程保持原样。方案内课程的旧时段视为已腾出（互换时段因此合法），
        // 外部冲突只检查未参与调课的课程，方案内冲突单独成对计算。
        List<CourseSchedule> virtualMoved = new ArrayList<>();
        for (RescheduleItem item : items) {
            int i = item.getItemIndex();
            if (stale[i]) {
                // 快照已过期：无法确定该条目的真实教师，跳过后续资源冲突检查，
                // 过期问题本身已记录在案。
                continue;
            }
            CourseSchedule current = scheduleById.get(item.getScheduleId());
            virtualMoved.add(new CourseSchedule(
                    current.getId(), current.getCourseName(), current.getTeacherName(),
                    item.getTargetClassroom(), item.getTargetTimeSlot()));
        }

        // 外部冲突：与未参与调课的现有课程冲突。
        for (CourseSchedule moved : virtualMoved) {
            int i = itemIndexOf(items, moved.getId());
            for (CourseSchedule external : scheduleById.values()) {
                if (belongsToPlan(external.getId(), items)) {
                    continue;
                }
                if (ScheduleConflictSupport.isTeacherConflict(
                        external, moved.getTeacherName(), moved.getTimeSlot(), null)) {
                    issues.add(new RescheduleIssue(
                            i, moved.getId(), RescheduleIssueType.TEACHER,
                            RescheduleIssueSource.EXTERNAL_COURSE,
                            external.getCourseName(), external.getTeacherName(),
                            external.getClassroom(), external.getTimeSlot(),
                            null,
                            "老师 " + moved.getTeacherName() + " 在目标时间段 " + moved.getTimeSlot()
                                    + " 已有未参与调课的课程安排"));
                }
                if (ScheduleConflictSupport.isClassroomConflict(
                        external, moved.getClassroom(), moved.getTimeSlot(), null)) {
                    issues.add(new RescheduleIssue(
                            i, moved.getId(), RescheduleIssueType.CLASSROOM,
                            RescheduleIssueSource.EXTERNAL_COURSE,
                            external.getCourseName(), external.getTeacherName(),
                            external.getClassroom(), external.getTimeSlot(),
                            null,
                            "教室 " + moved.getClassroom() + " 在目标时间段 " + moved.getTimeSlot()
                                    + " 已有未参与调课的课程安排"));
                }
            }
            // 自身旧时段未腾出（新时段与自身旧时段重叠）且旧时段还有其他课程时，
            // 上面的外部循环不会覆盖（自身属于方案）；此处不再额外处理：与自身交换不产生冲突。
        }

        // 内部冲突：方案内两条安排的目标时段互相冲突。
        for (int a = 0; a < virtualMoved.size(); a++) {
            CourseSchedule movedA = virtualMoved.get(a);
            int indexA = itemIndexOf(items, movedA.getId());
            for (int b = a + 1; b < virtualMoved.size(); b++) {
                CourseSchedule movedB = virtualMoved.get(b);
                int indexB = itemIndexOf(items, movedB.getId());
                if (!ScheduleConflictSupport.timeSlotsOverlap(movedA.getTimeSlot(), movedB.getTimeSlot())) {
                    continue;
                }
                if (movedA.getTeacherName().equals(movedB.getTeacherName())) {
                    issues.add(buildInternalIssue(indexA, indexB, movedA, movedB,
                            RescheduleIssueType.TEACHER, "老师"));
                }
                if (movedA.getClassroom().equals(movedB.getClassroom())) {
                    issues.add(buildInternalIssue(indexA, indexB, movedA, movedB,
                            RescheduleIssueType.CLASSROOM, "教室"));
                }
            }
        }

        sortIssues(issues);
        return issues;
    }

    public static Map<Long, CourseSchedule> indexById(List<CourseSchedule> schedules) {
        Map<Long, CourseSchedule> map = new HashMap<>();
        for (CourseSchedule schedule : schedules) {
            map.put(schedule.getId(), schedule);
        }
        return map;
    }

    private static boolean belongsToPlan(Long scheduleId, List<RescheduleItem> items) {
        for (RescheduleItem item : items) {
            if (item.getScheduleId().equals(scheduleId)) {
                return true;
            }
        }
        return false;
    }

    private static int itemIndexOf(List<RescheduleItem> items, Long scheduleId) {
        for (RescheduleItem item : items) {
            if (item.getScheduleId().equals(scheduleId)) {
                return item.getItemIndex();
            }
        }
        return -1;
    }

    private static boolean isUnchanged(RescheduleItem item, CourseSchedule current) {
        return nullSafeEquals(current.getCourseName(), item.getOriginalCourseName())
                && nullSafeEquals(current.getTeacherName(), item.getOriginalTeacherName())
                && nullSafeEquals(current.getClassroom(), item.getOriginalClassroom())
                && nullSafeEquals(current.getTimeSlot(), item.getOriginalTimeSlot());
    }

    private static boolean nullSafeEquals(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    private static RescheduleIssue buildInternalIssue(
            int indexA, int indexB, CourseSchedule movedA, CourseSchedule movedB,
            RescheduleIssueType issueType, String resourceLabel) {
        String resource = issueType == RescheduleIssueType.TEACHER
                ? movedA.getTeacherName() : movedA.getClassroom();
        return new RescheduleIssue(
                indexA, movedA.getId(), issueType,
                RescheduleIssueSource.INTERNAL_ITEM,
                movedB.getCourseName(), movedB.getTeacherName(),
                movedB.getClassroom(), movedB.getTimeSlot(),
                indexB,
                resourceLabel + " " + resource + " 在目标时间段 " + movedA.getTimeSlot()
                        + " 与方案内第 " + (indexB + 1) + " 条安排冲突");
    }

    private static void sortIssues(List<RescheduleIssue> issues) {
        issues.sort(Comparator
                .comparingInt(RescheduleIssue::getItemIndex)
                .thenComparing(issue -> issue.getIssueType().ordinal())
                .thenComparing(issue -> issue.getSource().ordinal())
                .thenComparing(issue -> issue.getOtherItemIndex() == null
                        ? Integer.MAX_VALUE : issue.getOtherItemIndex()));
    }
}
