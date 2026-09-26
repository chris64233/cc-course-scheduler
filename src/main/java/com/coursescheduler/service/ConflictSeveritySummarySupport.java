package com.coursescheduler.service;

import com.coursescheduler.dto.ConflictDetailDTO;
import com.coursescheduler.dto.ConflictSeveritySummaryDTO;
import com.coursescheduler.dto.ConflictTargetDetailDTO;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class ConflictSeveritySummarySupport {

    private ConflictSeveritySummarySupport() {
    }

    public static List<ConflictSeveritySummaryDTO> buildFromSingle(List<ConflictDetailDTO> conflicts, String courseName) {
        if (conflicts == null || conflicts.isEmpty()) {
            return new ArrayList<>();
        }

        List<ConflictTargetDetailDTO> targets = ConflictTargetDetailSupport.buildTargetDetails(conflicts);
        List<ConflictSeveritySummaryDTO> summaries = buildFromBatch(targets);

        if (courseName != null) {
            for (ConflictSeveritySummaryDTO summary : summaries) {
                if (summary.getCourseNames() == null) {
                    summary.setCourseNames(new ArrayList<>());
                }
                if (!summary.getCourseNames().contains(courseName)) {
                    summary.getCourseNames().add(courseName);
                }
            }
        }

        return summaries;
    }

    public static List<ConflictSeveritySummaryDTO> buildFromBatch(List<ConflictTargetDetailDTO> targetDetails) {
        if (targetDetails == null || targetDetails.isEmpty()) {
            return new ArrayList<>();
        }

        List<ConflictSeveritySummaryDTO> result = new ArrayList<>();

        int blockerTargetCount = 0;
        int blockerConflictTypeCount = 0;
        int blockerExistingCount = 0;
        int blockerPendingCount = 0;
        Set<String> blockerCourseNames = new LinkedHashSet<>();

        int warningTargetCount = 0;
        int warningConflictTypeCount = 0;
        int warningExistingCount = 0;
        int warningPendingCount = 0;
        Set<String> warningCourseNames = new LinkedHashSet<>();

        for (ConflictTargetDetailDTO td : targetDetails) {
            if (td == null) {
                continue;
            }
            boolean hasTeacher = td.getConflictTypes() != null
                    && td.getConflictTypes().contains(ConflictDetailDTO.ConflictType.TEACHER);
            boolean hasClassroom = td.getConflictTypes() != null
                    && td.getConflictTypes().contains(ConflictDetailDTO.ConflictType.CLASSROOM);

            if (hasTeacher && hasClassroom) {
                blockerTargetCount++;
                blockerConflictTypeCount += td.getConflictTypes().size();
                if (td.getSourceType() == ConflictDetailDTO.SourceType.EXISTING_COURSE) {
                    blockerExistingCount++;
                } else if (td.getSourceType() == ConflictDetailDTO.SourceType.PENDING_ITEM) {
                    blockerPendingCount++;
                }
                if (td.getCourseName() != null) {
                    blockerCourseNames.add(td.getCourseName());
                }
                if (td.getConflictingCourseName() != null) {
                    blockerCourseNames.add(td.getConflictingCourseName());
                }
                if (td.getRelatedCourseNames() != null) {
                    for (String rn : td.getRelatedCourseNames()) {
                        if (rn != null) {
                            blockerCourseNames.add(rn);
                        }
                    }
                }
            } else {
                warningTargetCount++;
                warningConflictTypeCount += (td.getConflictTypes() != null ? td.getConflictTypes().size() : 0);
                if (td.getSourceType() == ConflictDetailDTO.SourceType.EXISTING_COURSE) {
                    warningExistingCount++;
                } else if (td.getSourceType() == ConflictDetailDTO.SourceType.PENDING_ITEM) {
                    warningPendingCount++;
                }
                if (td.getCourseName() != null) {
                    warningCourseNames.add(td.getCourseName());
                }
                if (td.getConflictingCourseName() != null) {
                    warningCourseNames.add(td.getConflictingCourseName());
                }
                if (td.getRelatedCourseNames() != null) {
                    for (String rn : td.getRelatedCourseNames()) {
                        if (rn != null) {
                            warningCourseNames.add(rn);
                        }
                    }
                }
            }
        }

        if (blockerTargetCount > 0) {
            ConflictSeveritySummaryDTO blocker = new ConflictSeveritySummaryDTO();
            blocker.setSeverity(ConflictSeveritySummaryDTO.Severity.BLOCKER);
            blocker.setCount(blockerTargetCount);
            blocker.setTargetCount(blockerTargetCount);
            blocker.setConflictTypeCount(blockerConflictTypeCount);
            blocker.setExistingCourseTargetCount(blockerExistingCount);
            blocker.setPendingItemTargetCount(blockerPendingCount);
            blocker.setDescription("存在同时涉及老师和教室的冲突目标");
            blocker.setCourseNames(new ArrayList<>(blockerCourseNames));
            result.add(blocker);
        }

        if (warningTargetCount > 0) {
            ConflictSeveritySummaryDTO warning = new ConflictSeveritySummaryDTO();
            warning.setSeverity(ConflictSeveritySummaryDTO.Severity.WARNING);
            warning.setCount(warningTargetCount);
            warning.setTargetCount(warningTargetCount);
            warning.setConflictTypeCount(warningConflictTypeCount);
            warning.setExistingCourseTargetCount(warningExistingCount);
            warning.setPendingItemTargetCount(warningPendingCount);
            warning.setDescription("存在单一类型冲突目标");
            warning.setCourseNames(new ArrayList<>(warningCourseNames));
            result.add(warning);
        }

        return result;
    }
}
