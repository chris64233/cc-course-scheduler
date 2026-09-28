package com.coursescheduler.dto;

/**
 * 教室停用取消请求。
 */
public class RoomOutageCancelRequest {
    private String operator;

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }
}
