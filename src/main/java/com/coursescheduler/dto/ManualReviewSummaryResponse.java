package com.coursescheduler.dto;

public class ManualReviewSummaryResponse {

    private int totalItems;
    private int conflictItems;
    private boolean manualReviewRequired;
    private String reason;

    public ManualReviewSummaryResponse() {
    }

    public ManualReviewSummaryResponse(int totalItems, int conflictItems,
                                        boolean manualReviewRequired, String reason) {
        this.totalItems = totalItems;
        this.conflictItems = conflictItems;
        this.manualReviewRequired = manualReviewRequired;
        this.reason = reason;
    }

    public int getTotalItems() {
        return totalItems;
    }

    public void setTotalItems(int totalItems) {
        this.totalItems = totalItems;
    }

    public int getConflictItems() {
        return conflictItems;
    }

    public void setConflictItems(int conflictItems) {
        this.conflictItems = conflictItems;
    }

    public boolean isManualReviewRequired() {
        return manualReviewRequired;
    }

    public void setManualReviewRequired(boolean manualReviewRequired) {
        this.manualReviewRequired = manualReviewRequired;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
