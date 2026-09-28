package com.coursescheduler.service;

import org.springframework.stereotype.Component;

import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 排课域共享锁。
 *
 * <p>课程安排的增删改、普通调课确认、教室停用登记/范围调整/取消和停用修复确认
 * 共用同一把读写锁：所有写操作全局串行，保证「停用生效」与「新增课程/普通调课」
 * 并发时不会出现新课程排入已停用时段、旧方案覆盖较新课程安排等交错问题。
 */
@Component
public class DomainLock {

    private final ReadWriteLock readWriteLock = new ReentrantReadWriteLock();

    public ReadWriteLock getLock() {
        return readWriteLock;
    }
}
