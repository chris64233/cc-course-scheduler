package com.coursescheduler.model;

/**
 * 原子调课方案状态。
 *
 * <p>状态流转：
 * <ul>
 *   <li>{@link #PENDING}：方案已提交，等待确认或拒绝；可重复预检，可多次尝试确认。</li>
 *   <li>{@link #CONFIRMED}：确认成功，全部变更已一次性生效，终态。</li>
 *   <li>{@link #REJECTED}：方案被拒绝，终态。</li>
 * </ul>
 *
 * <p>确认失败（方案过期或目标资源被占用）不会把方案推入终态，方案仍为
 * {@link #PENDING}，调用方可在外部数据恢复后重试，或显式拒绝该方案。
 */
public enum ReschedulePlanStatus {
    PENDING,
    CONFIRMED,
    REJECTED
}
