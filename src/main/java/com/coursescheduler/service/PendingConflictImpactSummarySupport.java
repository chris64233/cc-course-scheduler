package com.coursescheduler.service;

import com.coursescheduler.dto.BatchPreCheckItemResponse;
import com.coursescheduler.dto.BatchPreCheckResponse;
import com.coursescheduler.dto.ConflictDetailDTO;
import com.coursescheduler.dto.PendingConflictImpactSummaryDTO;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PendingConflictImpactSummarySupport {

    private PendingConflictImpactSummarySupport() {
    }

    public static List<PendingConflictImpactSummaryDTO> buildImpactSummary(
            BatchPreCheckResponse response,
            Map<Integer, ConflictTargetDetailSupport.ItemDisplayInfo> displayContext
    ) {
        if (response == null || response.getItems() == null || response.getItems().isEmpty()) {
            return new ArrayList<>();
        }

        Map<Integer, BatchPreCheckItemResponse> itemsByIndex = new HashMap<>();
        Map<Integer, Set<Integer>> directConflicts = new HashMap<>();
        Map<Integer, Set<String>> conflictTypesByIndex = new HashMap<>();
        Set<Integer> allPendingIndexes = new HashSet<>();

        for (BatchPreCheckItemResponse item : response.getItems()) {
            if (item == null || item.getConflictDetails() == null) {
                continue;
            }
            int sourceIndex = item.getOriginalIndex();
            itemsByIndex.put(sourceIndex, item);

            for (ConflictDetailDTO c : item.getConflictDetails()) {
                if (c == null || c.getSourceType() != ConflictDetailDTO.SourceType.PENDING_ITEM) {
                    continue;
                }
                if (c.getPendingIndex() == null) {
                    continue;
                }
                int targetIndex = c.getPendingIndex();
                if (sourceIndex == targetIndex) {
                    continue;
                }

                allPendingIndexes.add(sourceIndex);
                allPendingIndexes.add(targetIndex);

                if (!directConflicts.containsKey(sourceIndex)) {
                    directConflicts.put(sourceIndex, new HashSet<>());
                    conflictTypesByIndex.put(sourceIndex, new HashSet<>());
                }
                directConflicts.get(sourceIndex).add(targetIndex);
                if (c.getConflictType() != null) {
                    conflictTypesByIndex.get(sourceIndex).add(c.getConflictType().name());
                }
            }
        }

        if (directConflicts.isEmpty()) {
            return new ArrayList<>();
        }

        int maxIndex = 0;
        for (int idx : allPendingIndexes) {
            if (idx > maxIndex) {
                maxIndex = idx;
            }
        }

        int[] parent = new int[maxIndex + 1];
        int[] size = new int[maxIndex + 1];
        for (int i = 0; i <= maxIndex; i++) {
            parent[i] = i;
            size[i] = 1;
        }

        for (Map.Entry<Integer, Set<Integer>> entry : directConflicts.entrySet()) {
            int source = entry.getKey();
            for (int target : entry.getValue()) {
                union(parent, size, source, target);
            }
        }

        Map<Integer, Integer> componentSizeByIndex = new HashMap<>();
        int largestComponentSize = 0;
        for (int idx : allPendingIndexes) {
            int root = find(parent, idx);
            int componentSize = size[root];
            componentSizeByIndex.put(idx, componentSize);
            if (componentSize > largestComponentSize) {
                largestComponentSize = componentSize;
            }
        }

        List<PendingConflictImpactSummaryDTO> result = new ArrayList<>();
        for (Map.Entry<Integer, Set<Integer>> entry : directConflicts.entrySet()) {
            int index = entry.getKey();
            Set<Integer> affected = entry.getValue();
            Set<String> types = conflictTypesByIndex.get(index);

            List<String> affectedItems = new ArrayList<>();
            for (Integer affectedIdx : affected) {
                affectedItems.add(resolveName(affectedIdx, displayContext, itemsByIndex));
            }

            int connectedGroupSize = componentSizeByIndex.getOrDefault(index, 1);
            boolean largestGroup = connectedGroupSize == largestComponentSize;

            result.add(new PendingConflictImpactSummaryDTO(
                    index,
                    resolveName(index, displayContext, itemsByIndex),
                    affected.size(),
                    connectedGroupSize,
                    new ArrayList<>(types),
                    largestGroup,
                    affectedItems
            ));
        }

        Collections.sort(result, (a, b) -> Integer.compare(a.getIndex(), b.getIndex()));
        return result;
    }

    private static int find(int[] parent, int x) {
        while (parent[x] != x) {
            parent[x] = parent[parent[x]];
            x = parent[x];
        }
        return x;
    }

    private static void union(int[] parent, int[] size, int x, int y) {
        int rootX = find(parent, x);
        int rootY = find(parent, y);
        if (rootX == rootY) {
            return;
        }
        if (size[rootX] < size[rootY]) {
            int temp = rootX;
            rootX = rootY;
            rootY = temp;
        }
        parent[rootY] = rootX;
        size[rootX] += size[rootY];
    }

    private static String resolveName(int index,
                                       Map<Integer, ConflictTargetDetailSupport.ItemDisplayInfo> displayContext,
                                       Map<Integer, BatchPreCheckItemResponse> itemsByIndex) {
        if (displayContext != null) {
            ConflictTargetDetailSupport.ItemDisplayInfo info = displayContext.get(index);
            if (info != null && info.getCourseName() != null) {
                return info.getCourseName();
            }
        }
        BatchPreCheckItemResponse item = itemsByIndex.get(index);
        if (item != null && item.getCourseName() != null) {
            return item.getCourseName();
        }
        return String.valueOf(index);
    }
}
