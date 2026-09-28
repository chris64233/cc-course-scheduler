package com.coursescheduler.controller;

import com.coursescheduler.dto.AuditLogFilterRequest;
import com.coursescheduler.dto.AuditLogResponse;
import com.coursescheduler.dto.ChangeSummaryResponse;
import com.coursescheduler.dto.BatchPreCheckResponse;
import com.coursescheduler.dto.ClassroomBatchPreCheckRequest;
import com.coursescheduler.dto.ClassroomCourseStatisticsResponse;
import com.coursescheduler.dto.ClassroomFreeDaySummaryResponse;
import com.coursescheduler.dto.ClassroomWeeklySummaryResponse;
import com.coursescheduler.dto.CourseChangeAbnormalDaysDTO;
import com.coursescheduler.dto.CourseChangeChainResponse;
import com.coursescheduler.dto.CourseChangeSummaryDTO;
import com.coursescheduler.dto.CourseChangeTrendDTO;
import com.coursescheduler.dto.FailureReasonSummaryDTO;
import com.coursescheduler.dto.OperationFailureSummaryDTO;
import com.coursescheduler.dto.OperationSummaryDTO;
import com.coursescheduler.dto.RecentActiveCourseDTO;
import com.coursescheduler.dto.CourseScheduleBatchDeleteResponse;
import com.coursescheduler.dto.CourseScheduleBatchResponse;
import com.coursescheduler.dto.CourseScheduleBatchTimeSlotUpdateRequest;
import com.coursescheduler.dto.CourseScheduleBatchTimeSlotUpdateResponse;
import com.coursescheduler.dto.CourseScheduleCreateRequest;
import com.coursescheduler.dto.CourseScheduleDeleteResponse;
import com.coursescheduler.dto.CourseScheduleFilterRequest;
import com.coursescheduler.dto.CourseScheduleResponse;
import com.coursescheduler.dto.CourseScheduleStatisticsResponse;
import com.coursescheduler.dto.CourseScheduleWeekdayStatisticsResponse;
import com.coursescheduler.dto.CourseScheduleUpdateRequest;
import com.coursescheduler.dto.TeacherCourseStatisticsResponse;
import com.coursescheduler.dto.TeacherConsecutiveBusyDaysResponse;
import com.coursescheduler.dto.TeacherFreeDaySummaryResponse;
import com.coursescheduler.dto.TeacherWeeklySummaryResponse;
import com.coursescheduler.dto.ConflictDetailDTO;
import com.coursescheduler.dto.ConflictGroupDTO;
import com.coursescheduler.dto.ConflictSeveritySummaryDTO;
import com.coursescheduler.dto.ConflictTargetDetailDTO;
import com.coursescheduler.dto.ConflictTypeGroupDTO;
import com.coursescheduler.dto.ManualReviewSummaryResponse;
import com.coursescheduler.dto.PendingConflictConnectedGroupDTO;
import com.coursescheduler.dto.PendingConflictImpactSummaryDTO;
import com.coursescheduler.dto.PendingConflictPairDTO;
import com.coursescheduler.dto.CourseScheduleConflictPreCheckRequest;
import com.coursescheduler.dto.CourseScheduleConflictPreCheckResponse;
import com.coursescheduler.dto.ConflictRiskPreviewRequest;
import com.coursescheduler.dto.ConflictRiskPreviewResponse;
import com.coursescheduler.dto.PreCheckSummaryResponse;
import com.coursescheduler.dto.TeacherBatchPreCheckRequest;
import com.coursescheduler.dto.TeacherFreeTimeRequest;
import com.coursescheduler.dto.TeacherFreeTimeResponse;
import com.coursescheduler.model.OperationType;
import com.coursescheduler.service.AuditLogService;
import com.coursescheduler.service.CourseScheduleService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/schedules")
public class CourseScheduleController {
    private final CourseScheduleService scheduleService;
    private final AuditLogService auditLogService;

    public CourseScheduleController(CourseScheduleService scheduleService, AuditLogService auditLogService) {
        this.scheduleService = scheduleService;
        this.auditLogService = auditLogService;
    }

    @GetMapping
    public ResponseEntity<List<CourseScheduleResponse>> getSchedules(
            @RequestParam(required = false) String courseName,
            @RequestParam(required = false) String teacherName,
            @RequestParam(required = false) String classroom,
            @RequestParam(required = false) String timeSlot,
            @RequestParam(required = false) String weekday,
            @RequestParam(required = false) String startTimeFrom,
            @RequestParam(required = false) String startTimeTo,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false, defaultValue = "asc") String sortDirection) {
        CourseScheduleFilterRequest filter = new CourseScheduleFilterRequest();
        filter.setCourseName(courseName);
        filter.setTeacherName(teacherName);
        filter.setClassroom(classroom);
        filter.setTimeSlot(timeSlot);
        filter.setWeekday(weekday);
        filter.setStartTimeFrom(startTimeFrom);
        filter.setStartTimeTo(startTimeTo);
        filter.setSortBy(sortBy);
        filter.setSortDirection(sortDirection);
        return ResponseEntity.ok(scheduleService.findSchedules(filter));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportSchedules(
            @RequestParam(required = false) String courseName,
            @RequestParam(required = false) String teacherName,
            @RequestParam(required = false) String classroom,
            @RequestParam(required = false) String timeSlot,
            @RequestParam(required = false) String weekday,
            @RequestParam(required = false) String startTimeFrom,
            @RequestParam(required = false) String startTimeTo,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false, defaultValue = "asc") String sortDirection) {
        CourseScheduleFilterRequest filter = new CourseScheduleFilterRequest();
        filter.setCourseName(courseName);
        filter.setTeacherName(teacherName);
        filter.setClassroom(classroom);
        filter.setTimeSlot(timeSlot);
        filter.setWeekday(weekday);
        filter.setStartTimeFrom(startTimeFrom);
        filter.setStartTimeTo(startTimeTo);
        filter.setSortBy(sortBy);
        filter.setSortDirection(sortDirection);

        byte[] csvBytes = scheduleService.exportSchedulesAsCsv(filter);
        return createCsvDownloadResponse(csvBytes, "课程安排.csv", "course_schedules.csv");
    }

    private ResponseEntity<byte[]> createCsvDownloadResponse(
            byte[] csvBytes, String chineseFileName, String fallbackFileName) {
        String fileName;
        try {
            fileName = URLEncoder.encode(chineseFileName, "UTF-8").replace("+", "%20");
        } catch (java.io.UnsupportedEncodingException e) {
            fileName = fallbackFileName;
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
        headers.setContentDispositionFormData("attachment", fileName);
        headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");

        return new ResponseEntity<>(csvBytes, headers, HttpStatus.OK);
    }

    @PostMapping
    public ResponseEntity<CourseScheduleResponse> addSchedule(@RequestBody(required = false) CourseScheduleCreateRequest request) {
        CourseScheduleResponse created = scheduleService.addSchedule(request);
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }

    @PostMapping("/pre-check")
    public ResponseEntity<CourseScheduleConflictPreCheckResponse> preCheckConflicts(
            @RequestBody(required = false) CourseScheduleConflictPreCheckRequest request) {
        CourseScheduleConflictPreCheckResponse response = scheduleService.preCheckConflicts(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/pre-check/teacher-batch")
    public ResponseEntity<BatchPreCheckResponse> preCheckConflictsByTeacherBatch(
            @RequestBody(required = false) TeacherBatchPreCheckRequest request) {
        BatchPreCheckResponse response = scheduleService.preCheckConflictsByTeacherBatch(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/pre-check/classroom-batch")
    public ResponseEntity<BatchPreCheckResponse> preCheckConflictsByClassroomBatch(
            @RequestBody(required = false) ClassroomBatchPreCheckRequest request) {
        BatchPreCheckResponse response = scheduleService.preCheckConflictsByClassroomBatch(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/pre-check/summary")
    public ResponseEntity<PreCheckSummaryResponse> preCheckConflictsSummary(
            @RequestBody(required = false) CourseScheduleConflictPreCheckRequest request) {
        PreCheckSummaryResponse response = scheduleService.preCheckConflictsSummary(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/conflict-risk-preview")
    public ResponseEntity<ConflictRiskPreviewResponse> conflictRiskPreview(
            @RequestBody(required = false) ConflictRiskPreviewRequest request) {
        ConflictRiskPreviewResponse response = scheduleService.conflictRiskPreview(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/pre-check/grouped-by-source")
    public ResponseEntity<List<ConflictGroupDTO>> groupConflictsBySource(
            @RequestBody(required = false) CourseScheduleConflictPreCheckRequest request) {
        List<ConflictGroupDTO> response = scheduleService.groupConflictsBySource(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/pre-check/teacher-batch/summary")
    public ResponseEntity<PreCheckSummaryResponse> preCheckConflictsByTeacherBatchSummary(
            @RequestBody(required = false) TeacherBatchPreCheckRequest request) {
        PreCheckSummaryResponse response = scheduleService.preCheckConflictsByTeacherBatchSummary(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/pre-check/classroom-batch/summary")
    public ResponseEntity<PreCheckSummaryResponse> preCheckConflictsByClassroomBatchSummary(
            @RequestBody(required = false) ClassroomBatchPreCheckRequest request) {
        PreCheckSummaryResponse response = scheduleService.preCheckConflictsByClassroomBatchSummary(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/pre-check/teacher-batch/grouped-by-source")
    public ResponseEntity<List<ConflictGroupDTO>> groupConflictsBySourceForTeacherBatch(
            @RequestBody(required = false) TeacherBatchPreCheckRequest request) {
        List<ConflictGroupDTO> response = scheduleService.groupConflictsBySourceForTeacherBatch(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/pre-check/classroom-batch/grouped-by-source")
    public ResponseEntity<List<ConflictGroupDTO>> groupConflictsBySourceForClassroomBatch(
            @RequestBody(required = false) ClassroomBatchPreCheckRequest request) {
        List<ConflictGroupDTO> response = scheduleService.groupConflictsBySourceForClassroomBatch(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/pre-check/grouped-by-conflict-type")
    public ResponseEntity<List<ConflictTypeGroupDTO>> groupConflictsByConflictType(
            @RequestBody(required = false) CourseScheduleConflictPreCheckRequest request) {
        List<ConflictTypeGroupDTO> response = scheduleService.groupConflictsByConflictType(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/pre-check/teacher-batch/grouped-by-conflict-type")
    public ResponseEntity<List<ConflictTypeGroupDTO>> groupConflictsByConflictTypeForTeacherBatch(
            @RequestBody(required = false) TeacherBatchPreCheckRequest request) {
        List<ConflictTypeGroupDTO> response = scheduleService.groupConflictsByConflictTypeForTeacherBatch(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/pre-check/classroom-batch/grouped-by-conflict-type")
    public ResponseEntity<List<ConflictTypeGroupDTO>> groupConflictsByConflictTypeForClassroomBatch(
            @RequestBody(required = false) ClassroomBatchPreCheckRequest request) {
        List<ConflictTypeGroupDTO> response = scheduleService.groupConflictsByConflictTypeForClassroomBatch(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/pre-check/target-details")
    public ResponseEntity<List<ConflictTargetDetailDTO>> getConflictTargetDetails(
            @RequestBody(required = false) CourseScheduleConflictPreCheckRequest request) {
        List<ConflictTargetDetailDTO> response = scheduleService.getConflictTargetDetails(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/pre-check/teacher-batch/target-details")
    public ResponseEntity<List<ConflictTargetDetailDTO>> getConflictTargetDetailsForTeacherBatch(
            @RequestBody(required = false) TeacherBatchPreCheckRequest request) {
        List<ConflictTargetDetailDTO> response = scheduleService.getConflictTargetDetailsForTeacherBatch(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/pre-check/classroom-batch/target-details")
    public ResponseEntity<List<ConflictTargetDetailDTO>> getConflictTargetDetailsForClassroomBatch(
            @RequestBody(required = false) ClassroomBatchPreCheckRequest request) {
        List<ConflictTargetDetailDTO> response = scheduleService.getConflictTargetDetailsForClassroomBatch(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/pre-check/severity-summary")
    public ResponseEntity<List<ConflictSeveritySummaryDTO>> getConflictSeveritySummary(
            @RequestBody(required = false) CourseScheduleConflictPreCheckRequest request) {
        List<ConflictSeveritySummaryDTO> response = scheduleService.getConflictSeveritySummary(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/pre-check/teacher-batch/severity-summary")
    public ResponseEntity<List<ConflictSeveritySummaryDTO>> getConflictSeveritySummaryForTeacherBatch(
            @RequestBody(required = false) TeacherBatchPreCheckRequest request) {
        List<ConflictSeveritySummaryDTO> response = scheduleService.getConflictSeveritySummaryForTeacherBatch(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/pre-check/classroom-batch/severity-summary")
    public ResponseEntity<List<ConflictSeveritySummaryDTO>> getConflictSeveritySummaryForClassroomBatch(
            @RequestBody(required = false) ClassroomBatchPreCheckRequest request) {
        List<ConflictSeveritySummaryDTO> response = scheduleService.getConflictSeveritySummaryForClassroomBatch(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/pre-check/teacher-batch/pending-conflict-pairs")
    public ResponseEntity<List<PendingConflictPairDTO>> getPendingConflictPairsForTeacherBatch(
            @RequestBody(required = false) TeacherBatchPreCheckRequest request) {
        List<PendingConflictPairDTO> response = scheduleService.getPendingConflictPairsForTeacherBatch(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/pre-check/classroom-batch/pending-conflict-pairs")
    public ResponseEntity<List<PendingConflictPairDTO>> getPendingConflictPairsForClassroomBatch(
            @RequestBody(required = false) ClassroomBatchPreCheckRequest request) {
        List<PendingConflictPairDTO> response = scheduleService.getPendingConflictPairsForClassroomBatch(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/pre-check/teacher-batch/pending-conflict-connected-groups")
    public ResponseEntity<List<PendingConflictConnectedGroupDTO>> getPendingConflictConnectedGroupsForTeacherBatch(
            @RequestBody(required = false) TeacherBatchPreCheckRequest request) {
        List<PendingConflictConnectedGroupDTO> response = scheduleService.getPendingConflictConnectedGroupsForTeacherBatch(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/pre-check/classroom-batch/pending-conflict-connected-groups")
    public ResponseEntity<List<PendingConflictConnectedGroupDTO>> getPendingConflictConnectedGroupsForClassroomBatch(
            @RequestBody(required = false) ClassroomBatchPreCheckRequest request) {
        List<PendingConflictConnectedGroupDTO> response = scheduleService.getPendingConflictConnectedGroupsForClassroomBatch(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/pre-check/teacher-batch/pending-conflict-impact-summary")
    public ResponseEntity<List<PendingConflictImpactSummaryDTO>> getPendingConflictImpactSummaryForTeacherBatch(
            @RequestBody(required = false) TeacherBatchPreCheckRequest request) {
        List<PendingConflictImpactSummaryDTO> response = scheduleService.getPendingConflictImpactSummaryForTeacherBatch(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/pre-check/classroom-batch/pending-conflict-impact-summary")
    public ResponseEntity<List<PendingConflictImpactSummaryDTO>> getPendingConflictImpactSummaryForClassroomBatch(
            @RequestBody(required = false) ClassroomBatchPreCheckRequest request) {
        List<PendingConflictImpactSummaryDTO> response = scheduleService.getPendingConflictImpactSummaryForClassroomBatch(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/pre-check/teacher-batch/manual-review-summary")
    public ResponseEntity<ManualReviewSummaryResponse> getManualReviewSummaryForTeacherBatch(
            @RequestBody(required = false) TeacherBatchPreCheckRequest request) {
        ManualReviewSummaryResponse response = scheduleService.getManualReviewSummaryForTeacherBatch(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/pre-check/classroom-batch/manual-review-summary")
    public ResponseEntity<ManualReviewSummaryResponse> getManualReviewSummaryForClassroomBatch(
            @RequestBody(required = false) ClassroomBatchPreCheckRequest request) {
        ManualReviewSummaryResponse response = scheduleService.getManualReviewSummaryForClassroomBatch(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/pre-check/export")
    public ResponseEntity<byte[]> exportPreCheck(
            @RequestBody(required = false) CourseScheduleConflictPreCheckRequest request) {
        byte[] csvBytes = scheduleService.exportPreCheckAsCsv(request);
        return createCsvDownloadResponse(csvBytes, "排课预检结果.csv", "pre_check_result.csv");
    }

    @PostMapping("/pre-check/teacher-batch/export")
    public ResponseEntity<byte[]> exportTeacherBatchPreCheck(
            @RequestBody(required = false) TeacherBatchPreCheckRequest request) {
        byte[] csvBytes = scheduleService.exportTeacherBatchPreCheckAsCsv(request);
        return createCsvDownloadResponse(csvBytes, "按老师批量排课预检结果.csv", "teacher_batch_pre_check_result.csv");
    }

    @PostMapping("/pre-check/classroom-batch/export")
    public ResponseEntity<byte[]> exportClassroomBatchPreCheck(
            @RequestBody(required = false) ClassroomBatchPreCheckRequest request) {
        byte[] csvBytes = scheduleService.exportClassroomBatchPreCheckAsCsv(request);
        return createCsvDownloadResponse(csvBytes, "按教室批量排课预检结果.csv", "classroom_batch_pre_check_result.csv");
    }

    @PostMapping("/undo")
    public ResponseEntity<List<CourseScheduleResponse>> undo() {
        List<CourseScheduleResponse> result = scheduleService.undo();
        return ResponseEntity.ok(result);
    }

    @PostMapping("/batch")
    public ResponseEntity<CourseScheduleBatchResponse> addSchedulesBatch(@RequestBody(required = false) List<CourseScheduleCreateRequest> requests) {
        CourseScheduleBatchResponse response = scheduleService.addSchedulesBatch(requests);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<CourseScheduleResponse> updateSchedule(
            @PathVariable Long id,
            @RequestBody(required = false) CourseScheduleUpdateRequest request) {
        CourseScheduleResponse updated = scheduleService.updateSchedule(id, request);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<CourseScheduleDeleteResponse> deleteSchedule(@PathVariable Long id) {
        CourseScheduleDeleteResponse deleted = scheduleService.deleteSchedule(id);
        return ResponseEntity.ok(deleted);
    }

    @DeleteMapping("/batch")
    public ResponseEntity<CourseScheduleBatchDeleteResponse> deleteSchedulesBatch(@RequestBody(required = false) List<Long> ids) {
        CourseScheduleBatchDeleteResponse response = scheduleService.deleteSchedulesBatch(ids);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/batch/time-slot")
    public ResponseEntity<CourseScheduleBatchTimeSlotUpdateResponse> batchUpdateTimeSlot(
            @RequestBody(required = false) CourseScheduleBatchTimeSlotUpdateRequest request) {
        CourseScheduleBatchTimeSlotUpdateResponse response = scheduleService.batchUpdateTimeSlot(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/statistics")
    public ResponseEntity<CourseScheduleStatisticsResponse> getStatistics() {
        return ResponseEntity.ok(scheduleService.getStatistics());
    }

    @GetMapping("/statistics/weekday")
    public ResponseEntity<CourseScheduleWeekdayStatisticsResponse> getWeekdayStatistics() {
        return ResponseEntity.ok(scheduleService.getWeekdayStatistics());
    }

    @GetMapping("/statistics/teacher")
    public ResponseEntity<List<TeacherCourseStatisticsResponse>> getTeacherCourseStatistics() {
        return ResponseEntity.ok(scheduleService.getTeacherCourseStatistics());
    }

    @GetMapping("/statistics/classroom")
    public ResponseEntity<List<ClassroomCourseStatisticsResponse>> getClassroomCourseStatistics() {
        return ResponseEntity.ok(scheduleService.getClassroomCourseStatistics());
    }

    @GetMapping("/classroom-daily")
    public ResponseEntity<List<CourseScheduleResponse>> getClassroomDailySchedule(
            @RequestParam String classroom,
            @RequestParam String weekday) {
        return ResponseEntity.ok(scheduleService.getClassroomDailySchedule(classroom, weekday));
    }

    @GetMapping("/classroom-weekly")
    public ResponseEntity<List<CourseScheduleResponse>> getClassroomWeeklySchedule(
            @RequestParam String classroom) {
        return ResponseEntity.ok(scheduleService.getClassroomWeeklySchedule(classroom));
    }

    @GetMapping("/classroom-weekly/export")
    public ResponseEntity<byte[]> exportClassroomWeeklySchedule(
            @RequestParam String classroom) {
        byte[] csvBytes = scheduleService.exportClassroomWeeklyScheduleAsCsv(classroom);
        return createCsvDownloadResponse(csvBytes, "教室周课表.csv", "classroom_weekly_schedule.csv");
    }

    @GetMapping("/teacher-daily")
    public ResponseEntity<List<CourseScheduleResponse>> getTeacherDailySchedule(
            @RequestParam String teacherName,
            @RequestParam String weekday) {
        return ResponseEntity.ok(scheduleService.getTeacherDailySchedule(teacherName, weekday));
    }

    @GetMapping("/teacher-weekly")
    public ResponseEntity<List<CourseScheduleResponse>> getTeacherWeeklySchedule(
            @RequestParam String teacherName) {
        return ResponseEntity.ok(scheduleService.getTeacherWeeklySchedule(teacherName));
    }

    @GetMapping("/teacher-weekly-summary")
    public ResponseEntity<TeacherWeeklySummaryResponse> getTeacherWeeklySummary(
            @RequestParam String teacherName) {
        return ResponseEntity.ok(scheduleService.getTeacherWeeklySummary(teacherName));
    }

    @GetMapping("/teacher-free-day-summary")
    public ResponseEntity<TeacherFreeDaySummaryResponse> getTeacherFreeDaySummary(
            @RequestParam String teacherName) {
        return ResponseEntity.ok(scheduleService.getTeacherFreeDaySummary(teacherName));
    }

    @GetMapping("/teacher-consecutive-busy-days")
    public ResponseEntity<TeacherConsecutiveBusyDaysResponse> getTeacherConsecutiveBusyDays(
            @RequestParam String teacherName) {
        return ResponseEntity.ok(scheduleService.getTeacherConsecutiveBusyDays(teacherName));
    }

    @GetMapping("/classroom-weekly-summary")
    public ResponseEntity<ClassroomWeeklySummaryResponse> getClassroomWeeklySummary(
            @RequestParam String classroom) {
        return ResponseEntity.ok(scheduleService.getClassroomWeeklySummary(classroom));
    }

    @GetMapping("/classroom-free-day-summary")
    public ResponseEntity<ClassroomFreeDaySummaryResponse> getClassroomFreeDaySummary(
            @RequestParam String classroom) {
        return ResponseEntity.ok(scheduleService.getClassroomFreeDaySummary(classroom));
    }

    @GetMapping("/teacher-weekly/export")
    public ResponseEntity<byte[]> exportTeacherWeeklySchedule(
            @RequestParam String teacherName) {
        byte[] csvBytes = scheduleService.exportTeacherWeeklyScheduleAsCsv(teacherName);
        return createCsvDownloadResponse(csvBytes, "老师周课表.csv", "teacher_weekly_schedule.csv");
    }

    @GetMapping("/teacher-free-time")
    public ResponseEntity<TeacherFreeTimeResponse> getTeacherFreeTime(
            @RequestParam String teacherName,
            @RequestParam String weekday,
            @RequestParam String startTimeFrom,
            @RequestParam String startTimeTo) {
        TeacherFreeTimeRequest request = new TeacherFreeTimeRequest();
        request.setTeacherName(teacherName);
        request.setWeekday(weekday);
        request.setStartTimeFrom(startTimeFrom);
        request.setStartTimeTo(startTimeTo);
        return ResponseEntity.ok(scheduleService.getTeacherFreeTime(request));
    }

    @GetMapping("/teacher-free-time/export")
    public ResponseEntity<byte[]> exportTeacherFreeTime(
            @RequestParam String teacherName,
            @RequestParam String weekday,
            @RequestParam String startTimeFrom,
            @RequestParam String startTimeTo) {
        TeacherFreeTimeRequest request = new TeacherFreeTimeRequest();
        request.setTeacherName(teacherName);
        request.setWeekday(weekday);
        request.setStartTimeFrom(startTimeFrom);
        request.setStartTimeTo(startTimeTo);

        byte[] csvBytes = scheduleService.exportTeacherFreeTimeAsCsv(request);
        return createCsvDownloadResponse(csvBytes, "老师空闲时间.csv", "teacher_free_time.csv");
    }

    @GetMapping("/audit-logs")
    public ResponseEntity<List<AuditLogResponse>> getAuditLogs(
            @RequestParam(required = false) OperationType operationType,
            @RequestParam(required = false) Boolean success) {
        AuditLogFilterRequest filter = new AuditLogFilterRequest();
        filter.setOperationType(operationType);
        filter.setSuccess(success);
        return ResponseEntity.ok(auditLogService.queryLogs(filter));
    }

    /** 查询一门课程的完整变更链：新增、普通调课、停用冻结、成组修复等 */
    @GetMapping("/{id}/change-chain")
    public ResponseEntity<CourseChangeChainResponse> getChangeChain(@PathVariable Long id) {
        return ResponseEntity.ok(auditLogService.getChangeChain(id));
    }

    @GetMapping("/change-summary")
    public ResponseEntity<List<ChangeSummaryResponse>> getChangeSummary(
            @RequestParam(required = false, defaultValue = "10") int limit) {
        return ResponseEntity.ok(auditLogService.getChangeSummary(limit));
    }

    @GetMapping("/change-summary/by-course")
    public ResponseEntity<List<CourseChangeSummaryDTO>> getChangeSummaryByCourse() {
        return ResponseEntity.ok(auditLogService.getChangeSummaryByCourse());
    }

    @GetMapping("/change-summary/by-operation")
    public ResponseEntity<List<OperationSummaryDTO>> getChangeSummaryByOperation() {
        return ResponseEntity.ok(auditLogService.getChangeSummaryByOperation());
    }

    @GetMapping("/recent-active-courses")
    public ResponseEntity<List<RecentActiveCourseDTO>> getRecentActiveCourses(
            @RequestParam(required = false, defaultValue = "10") int limit) {
        return ResponseEntity.ok(auditLogService.getRecentActiveCourses(limit));
    }

    @GetMapping("/course-change-trend")
    public ResponseEntity<List<CourseChangeTrendDTO>> getCourseChangeTrend(
            @RequestParam(required = false, defaultValue = "7") int days) {
        return ResponseEntity.ok(auditLogService.getCourseChangeTrend(days));
    }

    @GetMapping("/course-change-abnormal-days")
    public ResponseEntity<List<CourseChangeAbnormalDaysDTO>> getCourseChangeAbnormalDays(
            @RequestParam(required = false, defaultValue = "7") int days) {
        return ResponseEntity.ok(auditLogService.getCourseChangeAbnormalDays(days));
    }

    @GetMapping("/failure-reason-summary")
    public ResponseEntity<List<FailureReasonSummaryDTO>> getFailureReasonSummary(
            @RequestParam(required = false, defaultValue = "10") int limit) {
        return ResponseEntity.ok(auditLogService.getFailureReasonSummary(limit));
    }

    @GetMapping("/operation-failure-summary")
    public ResponseEntity<List<OperationFailureSummaryDTO>> getOperationFailureSummary(
            @RequestParam(required = false, defaultValue = "10") int limit) {
        return ResponseEntity.ok(auditLogService.getOperationFailureSummary(limit));
    }
}
