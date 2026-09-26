package com.coursescheduler.service;

import com.coursescheduler.dto.BatchPreCheckItemResponse;
import com.coursescheduler.dto.BatchPreCheckResponse;
import com.coursescheduler.dto.ConflictDetailDTO;
import com.coursescheduler.dto.CourseScheduleConflictPreCheckResponse;

import java.nio.charset.StandardCharsets;
import java.util.List;

public final class PreCheckCsvSupport {

    private static final String BOM = "\uFEFF";
    private static final String PRE_CHECK_HEADER =
            "预检类型,原始序号,课程名,是否可排,冲突类型,冲突来源,冲突课程/待排项,老师,教室,时间段,原因\n";

    private PreCheckCsvSupport() {
    }

    public static byte[] buildSinglePreCheckCsv(
            String courseName,
            CourseScheduleConflictPreCheckResponse response) {
        StringBuilder csv = new StringBuilder();
        csv.append(BOM);
        csv.append(PRE_CHECK_HEADER);

        if (response == null) {
            return csv.toString().getBytes(StandardCharsets.UTF_8);
        }

        String preCheckType = "单项预检";
        String originalIndex = "";
        appendPreCheckRows(csv, preCheckType, originalIndex, courseName,
                response.isCanSchedule(), response.getConflictDetails());

        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    public static byte[] buildBatchPreCheckCsv(
            String preCheckType,
            BatchPreCheckResponse response) {
        StringBuilder csv = new StringBuilder();
        csv.append(BOM);
        csv.append(PRE_CHECK_HEADER);

        if (response != null && response.getItems() != null) {
            for (BatchPreCheckItemResponse item : response.getItems()) {
                String originalIndex = String.valueOf(item.getOriginalIndex());
                appendPreCheckRows(csv, preCheckType, originalIndex, item.getCourseName(),
                        item.isCanSchedule(), item.getConflictDetails());
            }
        }

        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void appendPreCheckRows(
            StringBuilder csv,
            String preCheckType,
            String originalIndex,
            String courseName,
            boolean canSchedule,
            List<ConflictDetailDTO> conflictDetails) {

        String canScheduleText = canSchedule ? "是" : "否";

        if (conflictDetails == null || conflictDetails.isEmpty()) {
            appendPreCheckRow(csv, preCheckType, originalIndex, courseName, canScheduleText,
                    "", "", "", "", "", "", "");
            return;
        }

        for (ConflictDetailDTO conflict : conflictDetails) {
            String conflictTypeText = getConflictTypeText(conflict.getConflictType());
            String sourceTypeText = getSourceTypeText(conflict.getSourceType());
            String conflictTargetText = buildConflictTargetText(conflict);

            appendPreCheckRow(csv, preCheckType, originalIndex, courseName, canScheduleText,
                    conflictTypeText,
                    sourceTypeText,
                    conflictTargetText,
                    conflict.getTeacherName(),
                    conflict.getClassroom(),
                    conflict.getTimeSlot(),
                    conflict.getReason());
        }
    }

    private static void appendPreCheckRow(
            StringBuilder csv,
            String preCheckType,
            String originalIndex,
            String courseName,
            String canSchedule,
            String conflictType,
            String conflictSource,
            String conflictCourseName,
            String teacherName,
            String classroom,
            String timeSlot,
            String reason) {

        csv.append(CsvSupport.escapeField(preCheckType)).append(',');
        csv.append(CsvSupport.escapeField(originalIndex)).append(',');
        csv.append(CsvSupport.escapeField(courseName)).append(',');
        csv.append(CsvSupport.escapeField(canSchedule)).append(',');
        csv.append(CsvSupport.escapeField(conflictType)).append(',');
        csv.append(CsvSupport.escapeField(conflictSource)).append(',');
        csv.append(CsvSupport.escapeField(conflictCourseName)).append(',');
        csv.append(CsvSupport.escapeField(teacherName)).append(',');
        csv.append(CsvSupport.escapeField(classroom)).append(',');
        csv.append(CsvSupport.escapeField(timeSlot)).append(',');
        csv.append(CsvSupport.escapeField(reason)).append('\n');
    }

    static String getConflictTypeText(ConflictDetailDTO.ConflictType type) {
        if (type == null) {
            return "";
        }
        switch (type) {
            case TEACHER:
                return "老师冲突";
            case CLASSROOM:
                return "教室冲突";
            default:
                return "";
        }
    }

    static String getSourceTypeText(ConflictDetailDTO.SourceType type) {
        if (type == null) {
            return "";
        }
        switch (type) {
            case EXISTING_COURSE:
                return "已有课程";
            case PENDING_ITEM:
                return "待排项";
            default:
                return "";
        }
    }

    static String buildConflictTargetText(ConflictDetailDTO conflict) {
        if (conflict == null) {
            return "";
        }
        String name = conflict.getCourseName();
        if (name == null) {
            name = "";
        }
        if (conflict.getSourceType() == ConflictDetailDTO.SourceType.EXISTING_COURSE) {
            if (conflict.getCourseId() != null) {
                return name + "(ID: " + conflict.getCourseId() + ")";
            }
            return name;
        }
        if (conflict.getSourceType() == ConflictDetailDTO.SourceType.PENDING_ITEM) {
            if (conflict.getPendingIndex() != null) {
                return name + "(序号: " + conflict.getPendingIndex() + ")";
            }
            return name;
        }
        return name;
    }
}
