package com.coursescheduler.service;

import com.coursescheduler.dto.BatchPreCheckItemResponse;
import com.coursescheduler.dto.BatchPreCheckResponse;
import com.coursescheduler.dto.ConflictDetailDTO;
import com.coursescheduler.dto.ConflictTargetDetailDTO;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ConflictTargetDetailSupport {

    private ConflictTargetDetailSupport() {
    }

    public static class ItemDisplayInfo {
        private String courseName;
        private String teacherName;
        private String classroom;
        private String timeSlot;

        public ItemDisplayInfo() {
        }

        public ItemDisplayInfo(String courseName, String teacherName, String classroom) {
            this(courseName, teacherName, classroom, null);
        }

        public ItemDisplayInfo(String courseName, String teacherName, String classroom, String timeSlot) {
            this.courseName = courseName;
            this.teacherName = teacherName;
            this.classroom = classroom;
            this.timeSlot = timeSlot;
        }

        public String getCourseName() {
            return courseName;
        }

        public void setCourseName(String courseName) {
            this.courseName = courseName;
        }

        public String getTeacherName() {
            return teacherName;
        }

        public void setTeacherName(String teacherName) {
            this.teacherName = teacherName;
        }

        public String getClassroom() {
            return classroom;
        }

        public void setClassroom(String classroom) {
            this.classroom = classroom;
        }

        public String getTimeSlot() {
            return timeSlot;
        }

        public void setTimeSlot(String timeSlot) {
            this.timeSlot = timeSlot;
        }
    }

    public static List<ConflictTargetDetailDTO> buildTargetDetails(List<ConflictDetailDTO> conflicts) {
        if (conflicts == null || conflicts.isEmpty()) {
            return new ArrayList<>();
        }

        Map<String, ConflictTargetDetailDTO> existingById = new java.util.LinkedHashMap<>();
        Map<String, ConflictTargetDetailDTO> pendingByIndex = new java.util.LinkedHashMap<>();

        for (ConflictDetailDTO c : conflicts) {
            if (c == null) {
                continue;
            }

            String key;
            ConflictTargetDetailDTO target;

            if (c.getSourceType() == ConflictDetailDTO.SourceType.PENDING_ITEM && c.getPendingIndex() != null) {
                key = "pending-" + c.getPendingIndex();
                if (pendingByIndex.containsKey(key)) {
                    target = pendingByIndex.get(key);
                } else {
                    target = new ConflictTargetDetailDTO();
                    target.setSourceType(ConflictDetailDTO.SourceType.PENDING_ITEM);
                    target.setCourseName(c.getCourseName());
                    target.setTeacherName(c.getTeacherName());
                    target.setClassroom(c.getClassroom());
                    target.setTimeSlot(c.getTimeSlot());
                    target.setTargetIdentifier(resolveTargetIdentifier(c));
                    target.setTargetName(resolveTargetName(c));
                    target.setConflictingCourseName(c.getCourseName());
                    target.setConflictingTeacherName(c.getTeacherName());
                    target.setConflictingClassroom(c.getClassroom());
                    target.setConflictingTimeSlot(c.getTimeSlot());
                    target.setReason(c.getReason());
                    target.setConflictType(c.getConflictType());
                    pendingByIndex.put(key, target);
                }
                target.setConflictCount(target.getConflictCount() + 1);
                if (c.getConflictType() != null && !target.getConflictTypes().contains(c.getConflictType())) {
                    target.getConflictTypes().add(c.getConflictType());
                }
            } else if (c.getSourceType() == ConflictDetailDTO.SourceType.EXISTING_COURSE && c.getCourseId() != null) {
                key = "existing-" + c.getCourseId();
                if (existingById.containsKey(key)) {
                    target = existingById.get(key);
                } else {
                    target = new ConflictTargetDetailDTO();
                    target.setSourceType(ConflictDetailDTO.SourceType.EXISTING_COURSE);
                    target.setCourseId(c.getCourseId());
                    target.setTargetIdentifier(String.valueOf(c.getCourseId()));
                    target.setTargetName(c.getCourseName());
                    target.setCourseName(c.getCourseName());
                    target.setTeacherName(c.getTeacherName());
                    target.setClassroom(c.getClassroom());
                    target.setTimeSlot(c.getTimeSlot());
                    target.setConflictingCourseName(c.getCourseName());
                    target.setConflictingTeacherName(c.getTeacherName());
                    target.setConflictingClassroom(c.getClassroom());
                    target.setConflictingTimeSlot(c.getTimeSlot());
                    target.setReason(c.getReason());
                    target.setConflictType(c.getConflictType());
                    existingById.put(key, target);
                }
                target.setConflictCount(target.getConflictCount() + 1);
                if (c.getConflictType() != null && !target.getConflictTypes().contains(c.getConflictType())) {
                    target.getConflictTypes().add(c.getConflictType());
                }
            }
        }

        List<ConflictTargetDetailDTO> result = new ArrayList<>();
        for (ConflictTargetDetailDTO t : existingById.values()) {
            t.setConflictCount(t.getConflictTypes() != null ? t.getConflictTypes().size() : 0);
            result.add(t);
        }
        for (ConflictTargetDetailDTO t : pendingByIndex.values()) {
            t.setConflictCount(t.getConflictTypes() != null ? t.getConflictTypes().size() : 0);
            result.add(t);
        }
        return result;
    }

    public static List<ConflictTargetDetailDTO> buildTargetDetailsFromBatch(
            BatchPreCheckResponse response,
            Map<Integer, ItemDisplayInfo> displayContext
    ) {
        if (response == null || response.getItems() == null || response.getItems().isEmpty()) {
            return new ArrayList<>();
        }

        Map<String, ConflictTargetDetailDTO> pendingByIndex = new java.util.LinkedHashMap<>();
        Map<String, ConflictTargetDetailDTO> existingById = new java.util.LinkedHashMap<>();

        for (BatchPreCheckItemResponse item : response.getItems()) {
            if (item == null || item.getConflictDetails() == null) {
                continue;
            }
            ItemDisplayInfo info = displayContext != null ? displayContext.get(item.getOriginalIndex()) : null;

            for (ConflictDetailDTO c : item.getConflictDetails()) {
                if (c == null) {
                    continue;
                }

                String key;
                ConflictTargetDetailDTO target;

                if (c.getSourceType() == ConflictDetailDTO.SourceType.PENDING_ITEM && c.getPendingIndex() != null) {
                    int leftIndex = item.getOriginalIndex();
                    int rightIndex = c.getPendingIndex();
                    int keyIndex = Math.min(leftIndex, rightIndex);
                    key = "pending-" + keyIndex;
                    if (pendingByIndex.containsKey(key)) {
                        target = pendingByIndex.get(key);
                    } else {
                        target = new ConflictTargetDetailDTO();
                        target.setSourceType(ConflictDetailDTO.SourceType.PENDING_ITEM);
                        ItemDisplayInfo keyInfo = displayContext != null ? displayContext.get(keyIndex) : null;
                        if (keyInfo != null) {
                            target.setCourseName(keyInfo.getCourseName());
                            target.setTeacherName(keyInfo.getTeacherName());
                            target.setClassroom(keyInfo.getClassroom());
                        } else {
                            target.setCourseName(c.getCourseName());
                            target.setTeacherName(c.getTeacherName());
                            target.setClassroom(c.getClassroom());
                        }
                        target.setTimeSlot(c.getTimeSlot());
                        target.setTargetIdentifier(resolveTargetIdentifier(c, info));
                        target.setTargetName(resolveTargetName(c, info));
                        target.setPendingIndex(keyIndex);
                        pendingByIndex.put(key, target);
                    }
                    if (!target.getRelatedPendingIndexes().contains(leftIndex)) {
                        target.getRelatedPendingIndexes().add(leftIndex);
                    }
                    if (!target.getRelatedPendingIndexes().contains(rightIndex)) {
                        target.getRelatedPendingIndexes().add(rightIndex);
                    }
                    if (item.getCourseName() != null && !target.getRelatedCourseNames().contains(item.getCourseName())) {
                        target.getRelatedCourseNames().add(item.getCourseName());
                    }
                    String conflictingCourseName = c.getCourseName();
                    if (conflictingCourseName != null && !target.getRelatedCourseNames().contains(conflictingCourseName)) {
                        target.getRelatedCourseNames().add(conflictingCourseName);
                    }
                    target.setConflictCount(target.getConflictCount() + 1);
                    if (c.getConflictType() != null && !target.getConflictTypes().contains(c.getConflictType())) {
                        target.getConflictTypes().add(c.getConflictType());
                    }
                } else if (c.getSourceType() == ConflictDetailDTO.SourceType.EXISTING_COURSE && c.getCourseId() != null) {
                    key = "existing-" + c.getCourseId();
                    if (existingById.containsKey(key)) {
                        target = existingById.get(key);
                    } else {
                        target = new ConflictTargetDetailDTO();
                        target.setSourceType(ConflictDetailDTO.SourceType.EXISTING_COURSE);
                        target.setCourseId(c.getCourseId());
                        target.setTargetIdentifier(String.valueOf(c.getCourseId()));
                        target.setTargetName(c.getCourseName());
                        target.setCourseName(c.getCourseName());
                        target.setTeacherName(c.getTeacherName());
                        target.setClassroom(c.getClassroom());
                        target.setTimeSlot(c.getTimeSlot());
                        target.setConflictingCourseName(c.getCourseName());
                        target.setConflictingTeacherName(c.getTeacherName());
                        target.setConflictingClassroom(c.getClassroom());
                        target.setConflictingTimeSlot(c.getTimeSlot());
                        target.setReason(c.getReason());
                        target.setConflictType(c.getConflictType());
                        existingById.put(key, target);
                    }
                    if (!target.getRelatedPendingIndexes().contains(item.getOriginalIndex())) {
                        target.getRelatedPendingIndexes().add(item.getOriginalIndex());
                    }
                    if (item.getCourseName() != null && !target.getRelatedCourseNames().contains(item.getCourseName())) {
                        target.getRelatedCourseNames().add(item.getCourseName());
                    }
                    target.setConflictCount(target.getConflictCount() + 1);
                    if (c.getConflictType() != null && !target.getConflictTypes().contains(c.getConflictType())) {
                        target.getConflictTypes().add(c.getConflictType());
                    }
                }
            }
        }

        List<ConflictTargetDetailDTO> result = new ArrayList<>();
        for (ConflictTargetDetailDTO t : existingById.values()) {
            t.setConflictCount(t.getConflictTypes() != null ? t.getConflictTypes().size() : 0);
            result.add(t);
        }
        for (ConflictTargetDetailDTO t : pendingByIndex.values()) {
            t.setConflictCount(t.getConflictTypes() != null ? t.getConflictTypes().size() : 0);
            result.add(t);
        }
        return result;
    }

    public static List<ConflictTargetDetailDTO> buildTargetDetailsFromBatch(BatchPreCheckResponse response) {
        return buildTargetDetailsFromBatch(response, null);
    }

    private static String resolveTargetIdentifier(ConflictDetailDTO c) {
        if (c.getConflictType() == ConflictDetailDTO.ConflictType.TEACHER) {
            return c.getTeacherName();
        } else if (c.getConflictType() == ConflictDetailDTO.ConflictType.CLASSROOM) {
            return c.getClassroom();
        }
        return null;
    }

    private static String resolveTargetIdentifier(ConflictDetailDTO c, ItemDisplayInfo info) {
        if (c.getConflictType() == ConflictDetailDTO.ConflictType.TEACHER) {
            return info != null && info.getTeacherName() != null ? info.getTeacherName() : c.getTeacherName();
        } else if (c.getConflictType() == ConflictDetailDTO.ConflictType.CLASSROOM) {
            return info != null && info.getClassroom() != null ? info.getClassroom() : c.getClassroom();
        }
        return null;
    }

    private static String resolveTargetName(ConflictDetailDTO c) {
        return resolveTargetIdentifier(c);
    }

    private static String resolveTargetName(ConflictDetailDTO c, ItemDisplayInfo info) {
        return resolveTargetIdentifier(c, info);
    }
}
