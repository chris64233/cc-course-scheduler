package com.coursescheduler.dto;

import java.util.List;

/**
 * 停用修复方案提交请求。
 *
 * <p>{@code bizKey} 为修复业务号，用于幂等；方案必须覆盖任务中全部尚未修复的课程，
 * 每条调整指定新教室和新时段，也可通过让多门课程互换资源完成修复。
 */
public class RepairPlanSubmitRequest {
    private String bizKey;
    private String operator;
    private List<ReschedulePlanItemRequest> items;

    public RepairPlanSubmitRequest() {}

    public RepairPlanSubmitRequest(String bizKey, String operator, List<ReschedulePlanItemRequest> items) {
        this.bizKey = bizKey;
        this.operator = operator;
        this.items = items;
    }

    public String getBizKey() {
        return bizKey;
    }

    public void setBizKey(String bizKey) {
        this.bizKey = bizKey;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public List<ReschedulePlanItemRequest> getItems() {
        return items;
    }

    public void setItems(List<ReschedulePlanItemRequest> items) {
        this.items = items;
    }
}
