package com.coursescheduler.exception;

/**
 * 调课方案状态不合法：如方案已确认/已拒绝后再次处理，或相同业务号提交了不同内容。
 */
public class ReschedulePlanStateException extends RuntimeException {
    public ReschedulePlanStateException(String message) {
        super(message);
    }
}
