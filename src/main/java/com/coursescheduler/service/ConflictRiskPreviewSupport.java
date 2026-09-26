package com.coursescheduler.service;

import com.coursescheduler.dto.ConflictRiskPreviewResponse;
import com.coursescheduler.model.CourseSchedule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class ConflictRiskPreviewSupport {

    private ConflictRiskPreviewSupport() {
    }

    public static ConflictRiskPreviewResponse buildResponse(
            List<ScheduleConflictSupport.ConflictMatch> matches) {

        List<ScheduleConflictSupport.ConflictMatch> safeMatches =
                matches != null ? matches : Collections.<ScheduleConflictSupport.ConflictMatch>emptyList();

        int teacherConflictCount = 0;
        int classroomConflictCount = 0;
        Set<String> seenCourseNames = new LinkedHashSet<>();
        List<String> conflictCourseNames = new ArrayList<>();

        for (ScheduleConflictSupport.ConflictMatch match : safeMatches) {
            if (match == null) {
                continue;
            }
            ScheduleConflictSupport.ConflictType type = match.getConflictType();
            if (type != ScheduleConflictSupport.ConflictType.TEACHER
                    && type != ScheduleConflictSupport.ConflictType.CLASSROOM) {
                continue;
            }
            CourseSchedule schedule = match.getSchedule();
            if (schedule == null) {
                continue;
            }

            if (type == ScheduleConflictSupport.ConflictType.TEACHER) {
                teacherConflictCount++;
            } else {
                classroomConflictCount++;
            }

            String courseName = schedule.getCourseName();
            if (courseName != null && seenCourseNames.add(courseName)) {
                conflictCourseNames.add(courseName);
            }
        }

        ConflictRiskPreviewResponse.RiskLevel riskLevel = determineRiskLevel(
                teacherConflictCount, classroomConflictCount);

        return new ConflictRiskPreviewResponse(
                teacherConflictCount, classroomConflictCount, conflictCourseNames, riskLevel);
    }

    private static ConflictRiskPreviewResponse.RiskLevel determineRiskLevel(
            int teacherConflictCount, int classroomConflictCount) {
        if (teacherConflictCount > 0 && classroomConflictCount > 0) {
            return ConflictRiskPreviewResponse.RiskLevel.HIGH;
        }
        if (teacherConflictCount > 0 || classroomConflictCount > 0) {
            return ConflictRiskPreviewResponse.RiskLevel.MEDIUM;
        }
        return ConflictRiskPreviewResponse.RiskLevel.LOW;
    }
}
