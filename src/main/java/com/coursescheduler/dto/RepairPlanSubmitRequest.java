package com.coursescheduler.dto;

import java.util.List;

/**
 * 停用成组修复方案提交请求。
 *
 * <p>方案必须覆盖对应修复任务中全部待修复课程，bizKey 为修复业务号用于幂等控制。
 */
public class RepairPlanSubmitRequest {
    private String bizKey;
    private Long taskId;
    private List<ReschedulePlanItemRequest> items;
    private String operator;

    public String getBizKey() {
        return bizKey;
    }

    public void setBizKey(String bizKey) {
        this.bizKey = bizKey;
    }

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public List<ReschedulePlanItemRequest> getItems() {
        return items;
    }

    public void setItems(List<ReschedulePlanItemRequest> items) {
        this.items = items;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }
}
