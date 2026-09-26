package com.coursescheduler.service;

import com.coursescheduler.model.CourseSchedule;
import com.coursescheduler.util.TimeSlotUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ScheduleConflictSupport {

    public enum ConflictType {
        TEACHER,
        CLASSROOM
    }

    public static class ConflictMatch {
        private final CourseSchedule schedule;
        private final ConflictType conflictType;

        ConflictMatch(CourseSchedule schedule, ConflictType conflictType) {
            this.schedule = schedule;
            this.conflictType = conflictType;
        }

        public CourseSchedule getSchedule() {
            return schedule;
        }

        public ConflictType getConflictType() {
            return conflictType;
        }
    }

    public static class PendingItem {
        private final int index;
        private final String courseName;
        private final String teacherName;
        private final String classroom;
        private final String timeSlot;

        public PendingItem(int index, String courseName, String teacherName, String classroom, String timeSlot) {
            this.index = index;
            this.courseName = courseName;
            this.teacherName = teacherName;
            this.classroom = classroom;
            this.timeSlot = timeSlot;
        }

        public int getIndex() {
            return index;
        }

        public String getCourseName() {
            return courseName;
        }

        public String getTeacherName() {
            return teacherName;
        }

        public String getClassroom() {
            return classroom;
        }

        public String getTimeSlot() {
            return timeSlot;
        }
    }

    public static class PendingConflictMatch {
        private final PendingItem pendingItem;
        private final ConflictType conflictType;

        PendingConflictMatch(PendingItem pendingItem, ConflictType conflictType) {
            this.pendingItem = pendingItem;
            this.conflictType = conflictType;
        }

        public PendingItem getPendingItem() {
            return pendingItem;
        }

        public ConflictType getConflictType() {
            return conflictType;
        }
    }

    private ScheduleConflictSupport() {
    }

    public static List<ConflictMatch> findAllConflicts(
            List<CourseSchedule> scheduleList,
            String teacherName,
            String classroom,
            String normalizedTimeSlot,
            Set<Long> excludeIds
    ) {
        List<ConflictMatch> matches = new ArrayList<>();
        for (CourseSchedule existing : scheduleList) {
            if (isTeacherConflict(existing, teacherName, normalizedTimeSlot, excludeIds)) {
                matches.add(new ConflictMatch(existing, ConflictType.TEACHER));
            }
            if (isClassroomConflict(existing, classroom, normalizedTimeSlot, excludeIds)) {
                matches.add(new ConflictMatch(existing, ConflictType.CLASSROOM));
            }
        }
        return matches;
    }

    public static boolean hasAnyTeacherConflict(
            List<CourseSchedule> scheduleList,
            String teacherName,
            String normalizedTimeSlot,
            Set<Long> excludeIds
    ) {
        return scheduleList.stream()
                .anyMatch(s -> isTeacherConflict(s, teacherName, normalizedTimeSlot, excludeIds));
    }

    public static boolean hasAnyClassroomConflict(
            List<CourseSchedule> scheduleList,
            String classroom,
            String normalizedTimeSlot,
            Set<Long> excludeIds
    ) {
        return scheduleList.stream()
                .anyMatch(s -> isClassroomConflict(s, classroom, normalizedTimeSlot, excludeIds));
    }

    public static boolean isTeacherConflict(
            CourseSchedule schedule,
            String teacherName,
            String normalizedTimeSlot,
            Set<Long> excludeIds
    ) {
        if (excludeIds != null && excludeIds.contains(schedule.getId())) {
            return false;
        }
        return schedule.getTeacherName().equals(teacherName)
                && timeSlotsOverlap(schedule.getTimeSlot(), normalizedTimeSlot);
    }

    public static boolean isClassroomConflict(
            CourseSchedule schedule,
            String classroom,
            String normalizedTimeSlot,
            Set<Long> excludeIds
    ) {
        if (excludeIds != null && excludeIds.contains(schedule.getId())) {
            return false;
        }
        return schedule.getClassroom().equals(classroom)
                && timeSlotsOverlap(schedule.getTimeSlot(), normalizedTimeSlot);
    }

    public static boolean timeSlotsOverlap(String normalizedTimeSlot1, String normalizedTimeSlot2) {
        int weekday1 = TimeSlotUtils.extractWeekdayIndexFromNormalized(normalizedTimeSlot1);
        int weekday2 = TimeSlotUtils.extractWeekdayIndexFromNormalized(normalizedTimeSlot2);
        if (weekday1 != weekday2) {
            return false;
        }
        int start1 = TimeSlotUtils.extractStartTimeMinutesFromNormalized(normalizedTimeSlot1);
        int end1 = TimeSlotUtils.extractEndTimeMinutesFromNormalized(normalizedTimeSlot1);
        int start2 = TimeSlotUtils.extractStartTimeMinutesFromNormalized(normalizedTimeSlot2);
        int end2 = TimeSlotUtils.extractEndTimeMinutesFromNormalized(normalizedTimeSlot2);
        return start1 < end2 && start2 < end1;
    }

    public static String buildConflictReason(
            ConflictType type,
            String teacherOrClassroom,
            String timeSlot,
            String conflictSuffix
    ) {
        String base;
        switch (type) {
            case TEACHER:
                base = "老师 " + teacherOrClassroom + " 在时间段 " + timeSlot;
                break;
            case CLASSROOM:
                base = "教室 " + teacherOrClassroom + " 在时间段 " + timeSlot;
                break;
            default:
                throw new IllegalArgumentException("未知冲突类型: " + type);
        }
        if (conflictSuffix == null || conflictSuffix.isEmpty()) {
            return base + " 已有课程安排";
        }
        return base + " " + conflictSuffix;
    }

    public static List<PendingConflictMatch> findInternalConflictsForItem(
            List<PendingItem> pendingItems,
            int currentIndex
    ) {
        List<PendingConflictMatch> matches = new ArrayList<>();
        if (pendingItems == null || currentIndex < 0 || currentIndex >= pendingItems.size()) {
            return matches;
        }
        PendingItem current = pendingItems.get(currentIndex);
        for (int j = 0; j < pendingItems.size(); j++) {
            if (currentIndex == j) {
                continue;
            }
            PendingItem other = pendingItems.get(j);
            if (timeSlotsOverlap(current.timeSlot, other.timeSlot)) {
                if (current.teacherName.equals(other.teacherName)) {
                    matches.add(new PendingConflictMatch(other, ConflictType.TEACHER));
                }
                if (current.classroom.equals(other.classroom)) {
                    matches.add(new PendingConflictMatch(other, ConflictType.CLASSROOM));
                }
            }
        }
        return matches;
    }
}
