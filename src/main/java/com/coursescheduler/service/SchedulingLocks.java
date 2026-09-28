package com.coursescheduler.service;

import org.springframework.stereotype.Component;

import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 排课领域共享读写锁。
 *
 * <p>课程安排、调课方案、教室停用、修复任务、修复方案共用同一把锁，
 * 保证普通调课、停用范围调整与修复确认并发时彼此串行可见，
 * 同线程内嵌套获取读写锁可重入，不存在跨服务锁顺序死锁。
 */
@Component
public class SchedulingLocks {

    private final ReentrantReadWriteLock dataLock = new ReentrantReadWriteLock(true);

    public ReentrantReadWriteLock getLock() {
        return dataLock;
    }
}
