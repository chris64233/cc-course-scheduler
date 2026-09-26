package com.coursescheduler.service;

import com.coursescheduler.dto.BatchPreCheckItemResponse;
import com.coursescheduler.dto.BatchPreCheckResponse;
import com.coursescheduler.dto.ConflictDetailDTO;
import com.coursescheduler.dto.CourseScheduleConflictPreCheckResponse;
import com.coursescheduler.dto.PreCheckSummaryResponse;

import java.util.Collections;
import java.util.List;

public class PreCheckSummarySupport {

    private PreCheckSummarySupport() {
    }

    private static class ConflictAccumulator {
        private int totalConflicts;
        private int teacherConflicts;
        private int classroomConflicts;
        private int existingCourseConflicts;
        private int pendingItemConflicts;

        void accumulate(List<ConflictDetailDTO> conflicts) {
            List<ConflictDetailDTO> safe = conflicts != null ? conflicts : Collections.<ConflictDetailDTO>emptyList();
            for (ConflictDetailDTO conflict : safe) {
                if (conflict == null) {
                    continue;
                }
                ConflictDetailDTO.ConflictType type = conflict.getConflictType();
                ConflictDetailDTO.SourceType source = conflict.getSourceType();
                if (type == null || source == null) {
                    continue;
                }
                totalConflicts++;
                if (type == ConflictDetailDTO.ConflictType.TEACHER) {
                    teacherConflicts++;
                } else if (type == ConflictDetailDTO.ConflictType.CLASSROOM) {
                    classroomConflicts++;
                }
                if (source == ConflictDetailDTO.SourceType.EXISTING_COURSE) {
                    existingCourseConflicts++;
                } else if (source == ConflictDetailDTO.SourceType.PENDING_ITEM) {
                    pendingItemConflicts++;
                }
            }
        }

        int getTotalConflicts() { return totalConflicts; }
        int getTeacherConflicts() { return teacherConflicts; }
        int getClassroomConflicts() { return classroomConflicts; }
        int getExistingCourseConflicts() { return existingCourseConflicts; }
        int getPendingItemConflicts() { return pendingItemConflicts; }
    }

    public static PreCheckSummaryResponse calculateSingleSummary(
            CourseScheduleConflictPreCheckResponse response
    ) {
        ConflictAccumulator acc = new ConflictAccumulator();
        int totalItems = 0;
        int schedulableItems = 0;
        int unschedulableItems = 0;

        if (response != null) {
            totalItems = 1;
            if (response.isCanSchedule()) {
                schedulableItems = 1;
            } else {
                unschedulableItems = 1;
            }
            acc.accumulate(response.getConflictDetails());
        }

        return new PreCheckSummaryResponse(
                PreCheckSummaryResponse.PreCheckType.SINGLE,
                totalItems,
                schedulableItems,
                unschedulableItems,
                acc.getTotalConflicts(),
                acc.getTeacherConflicts(),
                acc.getClassroomConflicts(),
                acc.getExistingCourseConflicts(),
                acc.getPendingItemConflicts()
        );
    }

    public static PreCheckSummaryResponse calculateBatchSummary(
            BatchPreCheckResponse response,
            PreCheckSummaryResponse.PreCheckType preCheckType
    ) {
        List<BatchPreCheckItemResponse> items = response != null
                ? response.getItems() : Collections.<BatchPreCheckItemResponse>emptyList();
        if (items == null) {
            items = Collections.emptyList();
        }

        int totalItems = 0;
        int schedulableItems = 0;
        int unschedulableItems = 0;
        ConflictAccumulator acc = new ConflictAccumulator();

        for (BatchPreCheckItemResponse item : items) {
            if (item == null) {
                continue;
            }
            totalItems++;
            if (item.isCanSchedule()) {
                schedulableItems++;
            } else {
                unschedulableItems++;
            }
            acc.accumulate(item.getConflictDetails());
        }

        return new PreCheckSummaryResponse(
                preCheckType,
                totalItems,
                schedulableItems,
                unschedulableItems,
                acc.getTotalConflicts(),
                acc.getTeacherConflicts(),
                acc.getClassroomConflicts(),
                acc.getExistingCourseConflicts(),
                acc.getPendingItemConflicts()
        );
    }
}
