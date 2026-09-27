package com.coursescheduler.dto;

import java.util.List;

/**
 * 调课方案提交请求。bizKey 为调课业务号，用于幂等控制。
 */
public class ReschedulePlanSubmitRequest {
    private String bizKey;
    private List<ReschedulePlanItemRequest> items;

    public ReschedulePlanSubmitRequest() {}

    public ReschedulePlanSubmitRequest(String bizKey, List<ReschedulePlanItemRequest> items) {
        this.bizKey = bizKey;
        this.items = items;
    }

    public String getBizKey() {
        return bizKey;
    }

    public void setBizKey(String bizKey) {
        this.bizKey = bizKey;
    }

    public List<ReschedulePlanItemRequest> getItems() {
        return items;
    }

    public void setItems(List<ReschedulePlanItemRequest> items) {
        this.items = items;
    }
}
