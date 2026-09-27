package com.coursescheduler.dto;

import java.util.List;

/**
 * 提交原子调课方案请求。
 * businessId 为业务幂等号；items 中引用的课程安排必须互不重复。
 */
public class ReschedulePlanCreateRequest {
    private String businessId;
    private List<RescheduleItemRequest> items;

    public ReschedulePlanCreateRequest() {}

    public String getBusinessId() {
        return businessId;
    }

    public void setBusinessId(String businessId) {
        this.businessId = businessId;
    }

    public List<RescheduleItemRequest> getItems() {
        return items;
    }

    public void setItems(List<RescheduleItemRequest> items) {
        this.items = items;
    }
}
