package com.coursescheduler.service;

import com.coursescheduler.dto.BatchPreCheckItemResponse;
import com.coursescheduler.dto.BatchPreCheckResponse;
import com.coursescheduler.dto.ConflictDetailDTO;
import com.coursescheduler.dto.ConflictGroupDTO;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ConflictGroupSupport {

    private ConflictGroupSupport() {
    }

    public static List<ConflictGroupDTO> groupBySource(List<ConflictDetailDTO> conflicts) {
        if (conflicts == null || conflicts.isEmpty()) {
            return new ArrayList<>();
        }

        Map<ConflictDetailDTO.SourceType, List<ConflictDetailDTO>> bySource = new LinkedHashMap<>();
        bySource.put(ConflictDetailDTO.SourceType.EXISTING_COURSE, new ArrayList<>());
        bySource.put(ConflictDetailDTO.SourceType.PENDING_ITEM, new ArrayList<>());

        for (ConflictDetailDTO c : conflicts) {
            if (c == null || c.getSourceType() == null || c.getConflictType() == null) {
                continue;
            }
            List<ConflictDetailDTO> group = bySource.get(c.getSourceType());
            if (group != null) {
                group.add(c);
            }
        }

        List<ConflictGroupDTO> result = new ArrayList<>();
        for (Map.Entry<ConflictDetailDTO.SourceType, List<ConflictDetailDTO>> entry : bySource.entrySet()) {
            List<ConflictDetailDTO> groupConflicts = entry.getValue();
            if (groupConflicts.isEmpty()) {
                continue;
            }
            int teacherCount = 0;
            int classroomCount = 0;
            LinkedHashSet<String> courseNames = new LinkedHashSet<>();
            for (ConflictDetailDTO c : groupConflicts) {
                if (c.getConflictType() == ConflictDetailDTO.ConflictType.TEACHER) {
                    teacherCount++;
                } else if (c.getConflictType() == ConflictDetailDTO.ConflictType.CLASSROOM) {
                    classroomCount++;
                }
                if (c.getCourseName() != null) {
                    courseNames.add(c.getCourseName());
                }
            }
            result.add(new ConflictGroupDTO(
                    entry.getKey(),
                    groupConflicts.size(),
                    teacherCount,
                    classroomCount,
                    new ArrayList<>(courseNames)
            ));
        }
        return result;
    }

    public static List<ConflictGroupDTO> groupBySourceFromBatch(BatchPreCheckResponse batchResponse) {
        if (batchResponse == null || batchResponse.getItems() == null || batchResponse.getItems().isEmpty()) {
            return new ArrayList<>();
        }

        List<ConflictDetailDTO> existingCourseConflicts = new ArrayList<>();
        Set<String> existingCourseItemNames = new LinkedHashSet<>();

        Set<String> pendingPairKeys = new LinkedHashSet<>();
        Set<String> pendingTeacherPairs = new LinkedHashSet<>();
        Set<String> pendingClassroomPairs = new LinkedHashSet<>();
        Set<String> pendingCourseNames = new LinkedHashSet<>();

        for (BatchPreCheckItemResponse item : batchResponse.getItems()) {
            if (item == null || item.getConflictDetails() == null) {
                continue;
            }
            for (ConflictDetailDTO c : item.getConflictDetails()) {
                if (c == null || c.getSourceType() == null || c.getConflictType() == null) {
                    continue;
                }
                if (c.getSourceType() == ConflictDetailDTO.SourceType.EXISTING_COURSE) {
                    existingCourseConflicts.add(c);
                    if (item.getCourseName() != null) {
                        existingCourseItemNames.add(item.getCourseName());
                    }
                } else if (c.getSourceType() == ConflictDetailDTO.SourceType.PENDING_ITEM) {
                    int idx1 = item.getOriginalIndex();
                    int idx2 = c.getPendingIndex() != null ? c.getPendingIndex() : -1;
                    int lo = Math.min(idx1, idx2);
                    int hi = Math.max(idx1, idx2);
                    String pairKey = lo + "_" + hi;
                    pendingPairKeys.add(pairKey);
                    if (c.getConflictType() == ConflictDetailDTO.ConflictType.TEACHER) {
                        pendingTeacherPairs.add(pairKey);
                    } else if (c.getConflictType() == ConflictDetailDTO.ConflictType.CLASSROOM) {
                        pendingClassroomPairs.add(pairKey);
                    }
                    if (c.getCourseName() != null) {
                        pendingCourseNames.add(c.getCourseName());
                    }
                    if (item.getCourseName() != null) {
                        pendingCourseNames.add(item.getCourseName());
                    }
                }
            }
        }

        List<ConflictGroupDTO> result = new ArrayList<>();

        if (!existingCourseConflicts.isEmpty()) {
            int teacherCount = 0;
            int classroomCount = 0;
            LinkedHashSet<String> courseNames = new LinkedHashSet<>();
            for (ConflictDetailDTO c : existingCourseConflicts) {
                if (c.getConflictType() == ConflictDetailDTO.ConflictType.TEACHER) {
                    teacherCount++;
                } else if (c.getConflictType() == ConflictDetailDTO.ConflictType.CLASSROOM) {
                    classroomCount++;
                }
                if (c.getCourseName() != null) {
                    courseNames.add(c.getCourseName());
                }
            }
            courseNames.addAll(existingCourseItemNames);
            result.add(new ConflictGroupDTO(
                    ConflictDetailDTO.SourceType.EXISTING_COURSE,
                    existingCourseConflicts.size(),
                    teacherCount,
                    classroomCount,
                    new ArrayList<>(courseNames)
            ));
        }

        if (!pendingPairKeys.isEmpty()) {
            result.add(new ConflictGroupDTO(
                    ConflictDetailDTO.SourceType.PENDING_ITEM,
                    pendingPairKeys.size(),
                    pendingTeacherPairs.size(),
                    pendingClassroomPairs.size(),
                    new ArrayList<>(pendingCourseNames)
            ));
        }

        return result;
    }
}
