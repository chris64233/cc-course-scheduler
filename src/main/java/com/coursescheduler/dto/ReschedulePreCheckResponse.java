package com.coursescheduler.dto;

import java.util.List;

/**
 * 调课方案预检结果。valid 为 true 时整份方案可以直接确认；
 * 为 false 时 issues 给出整份方案的全部冲突明细。
 */
public class ReschedulePreCheckResponse {
    private String planId;
    private String businessId;
    private boolean valid;
    private int conflictCount;
    private List<RescheduleIssueDTO> issues;

    public ReschedulePreCheckResponse() {}

    public ReschedulePreCheckResponse(String planId, String businessId, boolean valid,
                                      int conflictCount, List<RescheduleIssueDTO> issues) {
        this.planId = planId;
        this.businessId = businessId;
        this.valid = valid;
        this.conflictCount = conflictCount;
        this.issues = issues;
    }

    public String getPlanId() {
        return planId;
    }

    public void setPlanId(String planId) {
        this.planId = planId;
    }

    public String getBusinessId() {
        return businessId;
    }

    public void setBusinessId(String businessId) {
        this.businessId = businessId;
    }

    public boolean isValid() {
        return valid;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }

    public int getConflictCount() {
        return conflictCount;
    }

    public void setConflictCount(int conflictCount) {
        this.conflictCount = conflictCount;
    }

    public List<RescheduleIssueDTO> getIssues() {
        return issues;
    }

    public void setIssues(List<RescheduleIssueDTO> issues) {
        this.issues = issues;
    }
}
