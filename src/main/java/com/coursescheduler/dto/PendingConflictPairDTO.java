package com.coursescheduler.dto;

import java.util.ArrayList;
import java.util.List;

public class PendingConflictPairDTO {

    private PendingConflictPairItemDTO left;
    private PendingConflictPairItemDTO right;
    private int conflictCount;
    private List<ConflictDetailDTO.ConflictType> conflictTypes;

    public PendingConflictPairDTO() {
        this.conflictTypes = new ArrayList<>();
    }

    public PendingConflictPairDTO(PendingConflictPairItemDTO left,
                                   PendingConflictPairItemDTO right,
                                   int conflictCount,
                                   List<ConflictDetailDTO.ConflictType> conflictTypes) {
        this.left = left;
        this.right = right;
        this.conflictCount = conflictCount;
        this.conflictTypes = conflictTypes != null ? conflictTypes : new ArrayList<>();
    }

    public PendingConflictPairItemDTO getLeft() {
        return left;
    }

    public void setLeft(PendingConflictPairItemDTO left) {
        this.left = left;
    }

    public PendingConflictPairItemDTO getRight() {
        return right;
    }

    public void setRight(PendingConflictPairItemDTO right) {
        this.right = right;
    }

    public int getConflictCount() {
        return conflictCount;
    }

    public void setConflictCount(int conflictCount) {
        this.conflictCount = conflictCount;
    }

    public List<ConflictDetailDTO.ConflictType> getConflictTypes() {
        return conflictTypes;
    }

    public void setConflictTypes(List<ConflictDetailDTO.ConflictType> conflictTypes) {
        this.conflictTypes = conflictTypes;
    }
}
