package com.coursescheduler.service;

import com.coursescheduler.dto.BatchPreCheckItemResponse;
import com.coursescheduler.dto.BatchPreCheckResponse;
import com.coursescheduler.dto.ConflictDetailDTO;
import com.coursescheduler.dto.ManualReviewSummaryResponse;
import com.coursescheduler.dto.PendingConflictConnectedGroupDTO;

import java.util.List;

public class ManualReviewSummarySupport {

    private enum ManualReviewTrigger {
        DUAL_TYPE_CONFLICT,
        CHAIN_CONFLICT
    }

    private ManualReviewSummarySupport() {
    }

    public static ManualReviewSummaryResponse calculate(BatchPreCheckResponse response) {
        if (response == null || response.getItems() == null || response.getItems().isEmpty()) {
            return new ManualReviewSummaryResponse(0, 0, false, null);
        }

        ConflictStats stats = collectStats(response);

        if (stats.conflictItems == 0) {
            return new ManualReviewSummaryResponse(stats.totalItems, 0, false, null);
        }

        ManualReviewTrigger trigger = evaluateTrigger(response, stats.hasDualTypeConflict);
        return new ManualReviewSummaryResponse(
                stats.totalItems, stats.conflictItems,
                trigger != null, toReason(trigger));
    }

    private static ConflictStats collectStats(BatchPreCheckResponse response) {
        int totalItems = 0;
        int conflictItems = 0;
        boolean hasDualTypeConflict = false;

        for (BatchPreCheckItemResponse item : response.getItems()) {
            if (item == null) {
                continue;
            }
            totalItems++;
            if (!item.isCanSchedule()) {
                conflictItems++;
            }
            if (hasBothConflictTypes(item)) {
                hasDualTypeConflict = true;
            }
        }
        return new ConflictStats(totalItems, conflictItems, hasDualTypeConflict);
    }

    private static boolean hasBothConflictTypes(BatchPreCheckItemResponse item) {
        List<ConflictDetailDTO> details = item.getConflictDetails();
        if (details == null) {
            return false;
        }
        boolean hasTeacher = false;
        boolean hasClassroom = false;
        for (ConflictDetailDTO c : details) {
            if (c == null || c.getConflictType() == null) {
                continue;
            }
            if (c.getConflictType() == ConflictDetailDTO.ConflictType.TEACHER) {
                hasTeacher = true;
            } else if (c.getConflictType() == ConflictDetailDTO.ConflictType.CLASSROOM) {
                hasClassroom = true;
            }
        }
        return hasTeacher && hasClassroom;
    }

    private static ManualReviewTrigger evaluateTrigger(BatchPreCheckResponse response, boolean hasDualTypeConflict) {
        if (hasDualTypeConflict) {
            return ManualReviewTrigger.DUAL_TYPE_CONFLICT;
        }
        if (hasChainConflictOfThreeOrMore(response)) {
            return ManualReviewTrigger.CHAIN_CONFLICT;
        }
        return null;
    }

    private static String toReason(ManualReviewTrigger trigger) {
        if (trigger == null) {
            return null;
        }
        switch (trigger) {
            case DUAL_TYPE_CONFLICT:
                return "待排项同时存在老师和教室冲突，需要人工处理";
            case CHAIN_CONFLICT:
                return "待排项之间形成3个及以上链式冲突，需要人工处理";
            default:
                return null;
        }
    }

    private static boolean hasChainConflictOfThreeOrMore(BatchPreCheckResponse response) {
        List<PendingConflictConnectedGroupDTO> groups = PendingConflictConnectedGroupSupport.buildConnectedGroups(response, null);
        for (PendingConflictConnectedGroupDTO group : groups) {
            if (group.getItemCount() >= 3) {
                return true;
            }
        }
        return false;
    }

    private static class ConflictStats {
        final int totalItems;
        final int conflictItems;
        final boolean hasDualTypeConflict;

        ConflictStats(int totalItems, int conflictItems, boolean hasDualTypeConflict) {
            this.totalItems = totalItems;
            this.conflictItems = conflictItems;
            this.hasDualTypeConflict = hasDualTypeConflict;
        }
    }
}
