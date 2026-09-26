package com.coursescheduler.service;

import com.coursescheduler.dto.BatchPreCheckItemResponse;
import com.coursescheduler.dto.BatchPreCheckResponse;
import com.coursescheduler.dto.ConflictDetailDTO;
import com.coursescheduler.dto.ConflictTypeGroupDTO;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ConflictTypeGroupSupport {

    private ConflictTypeGroupSupport() {
    }

    public static List<ConflictTypeGroupDTO> groupByConflictType(List<ConflictDetailDTO> conflicts) {
        if (conflicts == null || conflicts.isEmpty()) {
            return new ArrayList<>();
        }

        Map<ConflictDetailDTO.ConflictType, List<ConflictDetailDTO>> byType = new LinkedHashMap<>();
        Map<ConflictDetailDTO.ConflictType, Set<String>> courseNamesByType = new LinkedHashMap<>();

        for (ConflictDetailDTO c : conflicts) {
            if (c == null || c.getConflictType() == null) {
                continue;
            }
            ConflictDetailDTO.ConflictType type = c.getConflictType();
            if (!byType.containsKey(type)) {
                byType.put(type, new ArrayList<>());
                courseNamesByType.put(type, new LinkedHashSet<>());
            }
            byType.get(type).add(c);
            if (c.getCourseName() != null) {
                courseNamesByType.get(type).add(c.getCourseName());
            }
        }

        List<ConflictTypeGroupDTO> result = new ArrayList<>();
        for (Map.Entry<ConflictDetailDTO.ConflictType, List<ConflictDetailDTO>> entry : byType.entrySet()) {
            List<ConflictDetailDTO> groupConflicts = entry.getValue();
            List<String> courseNames = new ArrayList<>(courseNamesByType.get(entry.getKey()));

            int existingCount = 0;
            int pendingCount = 0;
            for (ConflictDetailDTO c : groupConflicts) {
                if (c.getSourceType() == ConflictDetailDTO.SourceType.EXISTING_COURSE) {
                    existingCount++;
                } else if (c.getSourceType() == ConflictDetailDTO.SourceType.PENDING_ITEM) {
                    pendingCount++;
                }
            }

            ConflictTypeGroupDTO dto = new ConflictTypeGroupDTO(
                    entry.getKey(),
                    groupConflicts.size(),
                    groupConflicts,
                    courseNames
            );
            dto.setConflictCount(groupConflicts.size());
            dto.setExistingCourseConflictCount(existingCount);
            dto.setPendingItemConflictCount(pendingCount);
            result.add(dto);
        }
        return result;
    }

    public static List<ConflictTypeGroupDTO> groupByConflictTypeFromBatch(BatchPreCheckResponse response) {
        if (response == null || response.getItems() == null || response.getItems().isEmpty()) {
            return new ArrayList<>();
        }

        Map<ConflictDetailDTO.ConflictType, List<ConflictDetailDTO>> byType = new LinkedHashMap<>();
        Map<ConflictDetailDTO.ConflictType, Set<String>> courseNamesByType = new LinkedHashMap<>();
        Map<ConflictDetailDTO.ConflictType, Set<String>> existingKeysByType = new LinkedHashMap<>();
        Map<ConflictDetailDTO.ConflictType, Set<String>> pendingKeysByType = new LinkedHashMap<>();

        for (BatchPreCheckItemResponse item : response.getItems()) {
            if (item == null || item.getConflictDetails() == null) {
                continue;
            }
            int itemIdx = item.getOriginalIndex();
            for (ConflictDetailDTO c : item.getConflictDetails()) {
                if (c == null || c.getConflictType() == null) {
                    continue;
                }
                ConflictDetailDTO.ConflictType type = c.getConflictType();
                if (!byType.containsKey(type)) {
                    byType.put(type, new ArrayList<>());
                    courseNamesByType.put(type, new LinkedHashSet<>());
                    existingKeysByType.put(type, new LinkedHashSet<>());
                    pendingKeysByType.put(type, new LinkedHashSet<>());
                }
                byType.get(type).add(c);
                if (item.getCourseName() != null) {
                    courseNamesByType.get(type).add(item.getCourseName());
                }
                if (c.getCourseName() != null) {
                    courseNamesByType.get(type).add(c.getCourseName());
                }

                if (c.getSourceType() == ConflictDetailDTO.SourceType.EXISTING_COURSE && c.getCourseId() != null) {
                    existingKeysByType.get(type).add(c.getCourseId() + "_" + itemIdx + "_" + type);
                } else if (c.getSourceType() == ConflictDetailDTO.SourceType.PENDING_ITEM && c.getPendingIndex() != null) {
                    int lo = Math.min(itemIdx, c.getPendingIndex());
                    int hi = Math.max(itemIdx, c.getPendingIndex());
                    pendingKeysByType.get(type).add(lo + "_" + hi + "_" + type);
                }
            }
        }

        List<ConflictTypeGroupDTO> result = new ArrayList<>();
        for (Map.Entry<ConflictDetailDTO.ConflictType, List<ConflictDetailDTO>> entry : byType.entrySet()) {
            ConflictDetailDTO.ConflictType type = entry.getKey();
            List<ConflictDetailDTO> groupConflicts = entry.getValue();
            List<String> courseNames = new ArrayList<>(courseNamesByType.get(type));
            int existingCount = existingKeysByType.get(type).size();
            int pendingCount = pendingKeysByType.get(type).size();

            ConflictTypeGroupDTO dto = new ConflictTypeGroupDTO(
                    type,
                    groupConflicts.size(),
                    groupConflicts,
                    courseNames
            );
            dto.setConflictCount(existingCount + pendingCount);
            dto.setExistingCourseConflictCount(existingCount);
            dto.setPendingItemConflictCount(pendingCount);
            result.add(dto);
        }
        return result;
    }
}
