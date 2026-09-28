package com.coursescheduler.exception;

import java.util.List;

/**
 * 修复方案未完整覆盖修复任务中的待修复课程，或引用了不属于任务的课程。
 * 不允许确认部分方案。
 */
public class RepairCoverageException extends RuntimeException {
    private final List<Long> missingScheduleIds;
    private final List<Long> extraScheduleIds;

    public RepairCoverageException(String message, List<Long> missingScheduleIds,
                                   List<Long> extraScheduleIds) {
        super(message);
        this.missingScheduleIds = missingScheduleIds;
        this.extraScheduleIds = extraScheduleIds;
    }

    public List<Long> getMissingScheduleIds() {
        return missingScheduleIds;
    }

    public List<Long> getExtraScheduleIds() {
        return extraScheduleIds;
    }
}
