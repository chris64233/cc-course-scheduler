package com.coursescheduler.service;

import com.coursescheduler.dto.BatchPreCheckItemResponse;
import com.coursescheduler.dto.BatchPreCheckResponse;
import com.coursescheduler.dto.ConflictDetailDTO;
import com.coursescheduler.dto.PendingConflictPairDTO;
import com.coursescheduler.dto.PendingConflictPairItemDTO;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PendingConflictPairSupport {

    private PendingConflictPairSupport() {
    }

    public static List<PendingConflictPairDTO> buildPendingConflictPairs(
            BatchPreCheckResponse response,
            Map<Integer, ConflictTargetDetailSupport.ItemDisplayInfo> displayContext
    ) {
        if (response == null || response.getItems() == null || response.getItems().isEmpty()) {
            return new ArrayList<>();
        }

        Map<String, List<ConflictDetailDTO>> pairConflicts = new LinkedHashMap<>();
        Map<String, int[]> pairIndices = new LinkedHashMap<>();

        for (BatchPreCheckItemResponse item : response.getItems()) {
            if (item == null || item.getConflictDetails() == null) {
                continue;
            }
            for (ConflictDetailDTO c : item.getConflictDetails()) {
                if (c == null || c.getSourceType() != ConflictDetailDTO.SourceType.PENDING_ITEM) {
                    continue;
                }
                if (c.getPendingIndex() == null) {
                    continue;
                }

                int firstIndex = item.getOriginalIndex();
                int secondIndex = c.getPendingIndex();
                if (firstIndex == secondIndex) {
                    continue;
                }

                int lo = Math.min(firstIndex, secondIndex);
                int hi = Math.max(firstIndex, secondIndex);
                String pairKey = lo + "_" + hi;
                if (!pairConflicts.containsKey(pairKey)) {
                    pairConflicts.put(pairKey, new ArrayList<>());
                    pairIndices.put(pairKey, new int[]{lo, hi});
                }
                pairConflicts.get(pairKey).add(c);
            }
        }

        List<PendingConflictPairDTO> result = new ArrayList<>();
        for (Map.Entry<String, List<ConflictDetailDTO>> entry : pairConflicts.entrySet()) {
            String pairKey = entry.getKey();
            List<ConflictDetailDTO> conflicts = entry.getValue();
            int[] indices = pairIndices.get(pairKey);
            int lo = indices[0];
            int hi = indices[1];

            PendingConflictPairItemDTO leftItem = buildItemDTO(lo, displayContext);
            PendingConflictPairItemDTO rightItem = buildItemDTO(hi, displayContext);

            Set<ConflictDetailDTO.ConflictType> typeSet = new LinkedHashSet<>();
            for (ConflictDetailDTO c : conflicts) {
                if (c.getConflictType() != null) {
                    typeSet.add(c.getConflictType());
                }
            }
            List<ConflictDetailDTO.ConflictType> conflictTypes = new ArrayList<>(typeSet);
            conflictTypes.sort(Comparator.comparingInt(t -> t == ConflictDetailDTO.ConflictType.TEACHER ? 0 : 1));

            result.add(new PendingConflictPairDTO(
                    leftItem,
                    rightItem,
                    conflictTypes.size(),
                    conflictTypes
            ));
        }

        result.sort(Comparator.comparingInt((PendingConflictPairDTO p) -> p.getLeft().getIndex())
                .thenComparingInt(p -> p.getRight().getIndex()));

        return result;
    }

    private static PendingConflictPairItemDTO buildItemDTO(
            int index,
            Map<Integer, ConflictTargetDetailSupport.ItemDisplayInfo> displayContext) {
        String courseName = null;
        String teacherName = null;
        String classroom = null;
        String timeSlot = null;

        if (displayContext != null) {
            ConflictTargetDetailSupport.ItemDisplayInfo info = displayContext.get(index);
            if (info != null) {
                courseName = info.getCourseName();
                teacherName = info.getTeacherName();
                classroom = info.getClassroom();
                timeSlot = info.getTimeSlot();
            }
        }

        return new PendingConflictPairItemDTO(index, courseName, teacherName, classroom, timeSlot);
    }
}
