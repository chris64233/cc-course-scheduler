package com.coursescheduler.service;

import com.coursescheduler.model.RoomOutage;

import java.util.List;

/**
 * 教室停用阻塞判定。
 *
 * <p>课程的「教室 + 时间段」命中任一生效中停用事件的任一停用时段时即被阻塞。
 */
public final class RoomOutageBlockSupport {

    private RoomOutageBlockSupport() {
    }

    /**
     * 找出阻塞给定排课位置（教室 + 时间段）的生效中停用事件；没有则返回 null。
     */
    public static RoomOutage findBlockingOutage(List<RoomOutage> outages, String classroom, String timeSlot) {
        for (RoomOutage outage : outages) {
            if (blocks(outage, classroom, timeSlot)) {
                return outage;
            }
        }
        return null;
    }

    public static boolean blocks(RoomOutage outage, String classroom, String timeSlot) {
        if (outage == null || outage.getStatus() != com.coursescheduler.model.RoomOutageStatus.ACTIVE) {
            return false;
        }
        if (!outage.getClassroom().equals(classroom)) {
            return false;
        }
        for (String blockedSlot : outage.getTimeSlots()) {
            if (ScheduleConflictSupport.timeSlotsOverlap(blockedSlot, timeSlot)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断课程是否与给定停用事件冲突（教室相同且时间段重叠）。
     */
    public static boolean conflictsWithOutage(com.coursescheduler.model.CourseSchedule schedule, RoomOutage outage) {
        if (!schedule.getClassroom().equals(outage.getClassroom())) {
            return false;
        }
        for (String blockedSlot : outage.getTimeSlots()) {
            if (ScheduleConflictSupport.timeSlotsOverlap(blockedSlot, schedule.getTimeSlot())) {
                return true;
            }
        }
        return false;
    }

    public static String describeBlock(RoomOutage outage, String timeSlot) {
        return "教室 " + outage.getClassroom() + " 在时间段 " + timeSlot
                + " 已因停用事件 " + outage.getEventNo() + "（" + outage.getReason() + "）停用";
    }
}
