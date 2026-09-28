package com.coursescheduler.dto;

/**
 * 修复方案确认/拒绝请求，携带处理人员。
 */
public class RepairPlanActionRequest {
    private String operator;

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }
}
