package com.coursescheduler.dto;

import java.util.ArrayList;
import java.util.List;

public class PendingConflictImpactSummaryDTO {

    private int index;
    private String courseName;
    private int directPairCount;
    private int connectedGroupSize;
    private List<String> conflictTypes;
    private boolean largestGroup;
    private List<String> affectedItems;

    public PendingConflictImpactSummaryDTO() {
        this.conflictTypes = new ArrayList<>();
        this.affectedItems = new ArrayList<>();
    }

    public PendingConflictImpactSummaryDTO(int index, String courseName,
                                            int directPairCount, int connectedGroupSize,
                                            List<String> conflictTypes, boolean largestGroup,
                                            List<String> affectedItems) {
        this.index = index;
        this.courseName = courseName;
        this.directPairCount = directPairCount;
        this.connectedGroupSize = connectedGroupSize;
        this.conflictTypes = conflictTypes != null ? conflictTypes : new ArrayList<>();
        this.largestGroup = largestGroup;
        this.affectedItems = affectedItems != null ? affectedItems : new ArrayList<>();
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public String getCourseName() {
        return courseName;
    }

    public void setCourseName(String courseName) {
        this.courseName = courseName;
    }

    public int getDirectPairCount() {
        return directPairCount;
    }

    public void setDirectPairCount(int directPairCount) {
        this.directPairCount = directPairCount;
    }

    public int getConnectedGroupSize() {
        return connectedGroupSize;
    }

    public void setConnectedGroupSize(int connectedGroupSize) {
        this.connectedGroupSize = connectedGroupSize;
    }

    public List<String> getConflictTypes() {
        return conflictTypes;
    }

    public void setConflictTypes(List<String> conflictTypes) {
        this.conflictTypes = conflictTypes;
    }

    public boolean isLargestGroup() {
        return largestGroup;
    }

    public void setLargestGroup(boolean largestGroup) {
        this.largestGroup = largestGroup;
    }

    public List<String> getAffectedItems() {
        return affectedItems;
    }

    public void setAffectedItems(List<String> affectedItems) {
        this.affectedItems = affectedItems;
    }
}
