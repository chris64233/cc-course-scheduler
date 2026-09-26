package com.coursescheduler.dto;

import java.util.ArrayList;
import java.util.List;

public class PendingConflictConnectedGroupDTO {

    private int groupIndex;
    private List<Integer> indexes;
    private List<String> courseNames;
    private List<String> conflictTypes;
    private int pairCount;
    private int itemCount;

    public PendingConflictConnectedGroupDTO() {
        this.indexes = new ArrayList<>();
        this.courseNames = new ArrayList<>();
        this.conflictTypes = new ArrayList<>();
    }

    public PendingConflictConnectedGroupDTO(int groupIndex, List<Integer> indexes,
                                             List<String> courseNames, int pairCount,
                                             List<String> conflictTypes) {
        this.groupIndex = groupIndex;
        this.indexes = indexes != null ? indexes : new ArrayList<>();
        this.courseNames = courseNames != null ? courseNames : new ArrayList<>();
        this.pairCount = pairCount;
        this.itemCount = this.indexes.size();
        this.conflictTypes = conflictTypes != null ? conflictTypes : new ArrayList<>();
    }

    public int getGroupIndex() {
        return groupIndex;
    }

    public void setGroupIndex(int groupIndex) {
        this.groupIndex = groupIndex;
    }

    public List<Integer> getIndexes() {
        return indexes;
    }

    public void setIndexes(List<Integer> indexes) {
        this.indexes = indexes;
        this.itemCount = indexes != null ? indexes.size() : 0;
    }

    public List<String> getCourseNames() {
        return courseNames;
    }

    public void setCourseNames(List<String> courseNames) {
        this.courseNames = courseNames;
    }

    public List<String> getConflictTypes() {
        return conflictTypes;
    }

    public void setConflictTypes(List<String> conflictTypes) {
        this.conflictTypes = conflictTypes;
    }

    public int getPairCount() {
        return pairCount;
    }

    public void setPairCount(int pairCount) {
        this.pairCount = pairCount;
    }

    public int getItemCount() {
        return itemCount;
    }

    public void setItemCount(int itemCount) {
        this.itemCount = itemCount;
    }
}
