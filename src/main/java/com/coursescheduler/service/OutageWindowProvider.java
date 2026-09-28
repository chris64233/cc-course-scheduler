package com.coursescheduler.service;

import java.util.List;

/**
 * 当前生效中的教室停用窗口提供器。
 *
 * <p>实现方（{@link RoomOutageService}）与课程安排服务共用 {@link DomainLock}，
 * 因此本接口的方法约定在<strong>持有域锁（读锁或写锁）</strong>时调用，
 * 实现内部直接读取内存状态、不再重复加锁，以避免课程服务与停用服务之间出现锁顺序反转。
 */
public interface OutageWindowProvider {

    /**
     * 返回全部生效中的停用窗口快照（只读），课程新增/修改/调课确认时用于拦截停用时段。
     */
    List<OutageWindow> activeWindows();

    /**
     * 一个生效中的教室停用窗口。
     */
    class OutageWindow {
        private final String eventNo;
        private final String classroom;
        private final String timeSlot;
        private final String reason;

        public OutageWindow(String eventNo, String classroom, String timeSlot, String reason) {
            this.eventNo = eventNo;
            this.classroom = classroom;
            this.timeSlot = timeSlot;
            this.reason = reason;
        }

        public String getEventNo() {
            return eventNo;
        }

        public String getClassroom() {
            return classroom;
        }

        public String getTimeSlot() {
            return timeSlot;
        }

        public String getReason() {
            return reason;
        }
    }
}
