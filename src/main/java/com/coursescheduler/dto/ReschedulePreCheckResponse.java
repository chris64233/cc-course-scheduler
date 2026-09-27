package com.coursescheduler.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 调课方案预检查响应：任一调整不合法时返回整份方案的冲突明细。
 */
public class ReschedulePreCheckResponse {
    private Long planId;
    private boolean canReschedule;
    private int conflictCount;
    private List<RescheduleConflictDTO> conflicts = new ArrayList<>();

    public ReschedulePreCheckResponse() {}

    public ReschedulePreCheckResponse(Long planId, boolean canReschedule, int conflictCount,
                                      List<RescheduleConflictDTO> conflicts) {
        this.planId = planId;
        this.canReschedule = canReschedule;
        this.conflictCount = conflictCount;
        this.conflicts = conflicts != null ? conflicts : new ArrayList<>();
    }

    public Long getPlanId() {
        return planId;
    }

    public void setPlanId(Long planId) {
        this.planId = planId;
    }

    public boolean isCanReschedule() {
        return canReschedule;
    }

    public void setCanReschedule(boolean canReschedule) {
        this.canReschedule = canReschedule;
    }

    public int getConflictCount() {
        return conflictCount;
    }

    public void setConflictCount(int conflictCount) {
        this.conflictCount = conflictCount;
    }

    public List<RescheduleConflictDTO> getConflicts() {
        return conflicts;
    }

    public void setConflicts(List<RescheduleConflictDTO> conflicts) {
        this.conflicts = conflicts != null ? conflicts : new ArrayList<>();
    }
}
