package com.coursescheduler.service;

import com.coursescheduler.dto.BatchPreCheckResponse;
import com.coursescheduler.dto.PendingConflictConnectedGroupDTO;
import com.coursescheduler.dto.PendingConflictPairDTO;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class PendingConflictConnectedGroupSupport {

    private PendingConflictConnectedGroupSupport() {
    }

    public static List<PendingConflictConnectedGroupDTO> buildConnectedGroups(
            BatchPreCheckResponse response,
            Map<Integer, ConflictTargetDetailSupport.ItemDisplayInfo> displayContext
    ) {
        if (response == null) {
            return new ArrayList<>();
        }

        List<PendingConflictPairDTO> pairs = PendingConflictPairSupport.buildPendingConflictPairs(response, displayContext);
        if (pairs.isEmpty()) {
            return new ArrayList<>();
        }

        Set<Integer> allIndices = new TreeSet<>();
        for (PendingConflictPairDTO pair : pairs) {
            allIndices.add(pair.getLeft().getIndex());
            allIndices.add(pair.getRight().getIndex());
        }

        Map<Integer, Integer> parent = new java.util.LinkedHashMap<>();
        for (Integer idx : allIndices) {
            parent.put(idx, idx);
        }

        for (PendingConflictPairDTO pair : pairs) {
            union(parent, pair.getLeft().getIndex(), pair.getRight().getIndex());
        }

        Map<Integer, Set<Integer>> components = new java.util.LinkedHashMap<>();
        for (Integer idx : allIndices) {
            Integer root = find(parent, idx);
            if (!components.containsKey(root)) {
                components.put(root, new TreeSet<>());
            }
            components.get(root).add(idx);
        }

        List<PendingConflictConnectedGroupDTO> result = new ArrayList<>();
        int groupIndex = 0;
        for (Map.Entry<Integer, Set<Integer>> entry : components.entrySet()) {
            Set<Integer> indices = entry.getValue();
            List<Integer> itemIndices = new ArrayList<>(indices);
            List<String> itemNames = new ArrayList<>();
            for (Integer idx : indices) {
                itemNames.add(resolveName(idx, displayContext));
            }

            int conflictCount = 0;
            Set<String> conflictTypes = new LinkedHashSet<>();
            for (PendingConflictPairDTO pair : pairs) {
                if (indices.contains(pair.getLeft().getIndex()) && indices.contains(pair.getRight().getIndex())) {
                    conflictCount++;
                    if (pair.getConflictTypes() != null) {
                        for (com.coursescheduler.dto.ConflictDetailDTO.ConflictType ct : pair.getConflictTypes()) {
                            conflictTypes.add(ct.name());
                        }
                    }
                }
            }

            result.add(new PendingConflictConnectedGroupDTO(
                    groupIndex,
                    itemIndices,
                    itemNames,
                    conflictCount,
                    new ArrayList<>(conflictTypes)
            ));
            groupIndex++;
        }
        return result;
    }

    private static Integer find(Map<Integer, Integer> parent, Integer x) {
        if (!parent.get(x).equals(x)) {
            parent.put(x, find(parent, parent.get(x)));
        }
        return parent.get(x);
    }

    private static void union(Map<Integer, Integer> parent, Integer a, Integer b) {
        Integer rootA = find(parent, a);
        Integer rootB = find(parent, b);
        if (!rootA.equals(rootB)) {
            parent.put(rootA, rootB);
        }
    }

    private static String resolveName(int index, Map<Integer, ConflictTargetDetailSupport.ItemDisplayInfo> displayContext) {
        if (displayContext != null) {
            ConflictTargetDetailSupport.ItemDisplayInfo info = displayContext.get(index);
            if (info != null && info.getCourseName() != null) {
                return info.getCourseName();
            }
        }
        return String.valueOf(index);
    }
}
