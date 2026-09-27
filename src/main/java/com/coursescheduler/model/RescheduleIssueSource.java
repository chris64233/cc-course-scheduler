package com.coursescheduler.model;

/**
 * 调课冲突来源。
 *
 * <ul>
 *   <li>{@link #EXTERNAL_COURSE}：与未参与本次调课的现有课程冲突。</li>
 *   <li>{@link #INTERNAL_ITEM}：与同一方案内的另一条调课安排冲突（目标资源仍互相占用）。</li>
 *   <li>{@link #STALE_PLAN}：方案保存的原始快照与当前排课不一致（已删除或已变更）。</li>
 * </ul>
 */
public enum RescheduleIssueSource {
    EXTERNAL_COURSE,
    INTERNAL_ITEM,
    STALE_PLAN
}
