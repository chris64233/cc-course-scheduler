package com.coursescheduler.service;

import com.coursescheduler.dto.BatchPreCheckItemResponse;
import com.coursescheduler.dto.BatchPreCheckResponse;
import com.coursescheduler.dto.ClassroomBatchPreCheckItemRequest;
import com.coursescheduler.dto.ClassroomBatchPreCheckRequest;
import com.coursescheduler.dto.ClassroomCourseStatisticsResponse;
import com.coursescheduler.dto.ClassroomFreeDaySummaryResponse;
import com.coursescheduler.dto.ClassroomWeeklySummaryResponse;
import com.coursescheduler.dto.CourseScheduleBatchDeleteFailure;
import com.coursescheduler.dto.CourseScheduleBatchDeleteResponse;
import com.coursescheduler.dto.CourseScheduleBatchFailure;
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
import com.coursescheduler.dto.TeacherConsecutiveBusyDaysResponse;
import com.coursescheduler.dto.TeacherCourseStatisticsResponse;
import com.coursescheduler.dto.TeacherFreeDaySummaryResponse;
import com.coursescheduler.dto.TeacherWeeklySummaryResponse;
import com.coursescheduler.dto.ConflictDetailDTO;
import com.coursescheduler.dto.ConflictGroupDTO;
import com.coursescheduler.dto.ConflictRiskPreviewRequest;
import com.coursescheduler.dto.ConflictRiskPreviewResponse;
import com.coursescheduler.dto.ConflictTargetDetailDTO;
import com.coursescheduler.dto.PendingConflictConnectedGroupDTO;
import com.coursescheduler.dto.PendingConflictImpactSummaryDTO;
import com.coursescheduler.dto.PendingConflictPairDTO;
import com.coursescheduler.dto.ConflictSeveritySummaryDTO;
import com.coursescheduler.dto.ConflictTypeGroupDTO;
import com.coursescheduler.dto.CourseScheduleConflictPreCheckRequest;
import com.coursescheduler.dto.CourseScheduleConflictPreCheckResponse;
import com.coursescheduler.dto.TeacherBatchPreCheckItemRequest;
import com.coursescheduler.dto.TeacherBatchPreCheckRequest;
import com.coursescheduler.dto.ManualReviewSummaryResponse;
import com.coursescheduler.dto.PreCheckSummaryResponse;
import com.coursescheduler.dto.TeacherFreeTimeRequest;
import com.coursescheduler.dto.TeacherFreeTimeResponse;
import com.coursescheduler.exception.ClassroomConflictException;
import com.coursescheduler.exception.InvalidRequestParameterException;
import com.coursescheduler.exception.InvalidTimeSlotException;
import com.coursescheduler.exception.NoUndoAvailableException;
import com.coursescheduler.exception.ScheduleNotFoundException;
import com.coursescheduler.exception.TeacherConflictException;
import com.coursescheduler.model.CourseSchedule;
import com.coursescheduler.model.OperationType;
import com.coursescheduler.model.ReschedulePlanItem;
import com.coursescheduler.dto.RescheduleConflictDTO;
import com.coursescheduler.util.TimeSlotUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

@Service
public class CourseScheduleService {

    private final List<CourseSchedule> schedules = new java.util.ArrayList<>();
    private final ReadWriteLock rwLock;
    private final AuditLogService auditLogService;
    /**
     * 生效中的教室停用窗口提供器，由 {@link RoomOutageService} 注入；
     * 为空（纯单元测试）时视为没有任何停用。
     */
    private OutageWindowProvider outageWindowProvider;
    private long idGenerator = 1;
    private UndoSnapshot undoSnapshot = null;

    @Autowired
    public CourseScheduleService(AuditLogService auditLogService, DomainLock domainLock) {
        this.auditLogService = auditLogService;
        this.rwLock = domainLock.getLock();
    }

    /**
     * 仅用于不接入停用能力的单元测试，使用服务私有锁。
     */
    public CourseScheduleService(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
        this.rwLock = new ReentrantReadWriteLock();
    }

    @Autowired(required = false)
    public void setOutageWindowProvider(OutageWindowProvider outageWindowProvider) {
        this.outageWindowProvider = outageWindowProvider;
    }

    private static class UndoSnapshot {
        final List<CourseSchedule> schedules;
        final long idGenerator;

        UndoSnapshot(List<CourseSchedule> schedules, long idGenerator) {
            this.schedules = new ArrayList<>();
            for (CourseSchedule s : schedules) {
                this.schedules.add(new CourseSchedule(s.getId(), s.getCourseName(), s.getTeacherName(),
                        s.getClassroom(), s.getTimeSlot(), s.getRevision()));
            }
            this.idGenerator = idGenerator;
        }
    }

    private UndoSnapshot takeUndoSnapshot() {
        return new UndoSnapshot(schedules, idGenerator);
    }

    private void commitUndoSnapshot(UndoSnapshot snapshot) {
        undoSnapshot = snapshot;
    }

    public List<CourseScheduleResponse> undo() {
        rwLock.writeLock().lock();
        try {
            if (undoSnapshot == null) {
                auditLogService.recordLog(OperationType.UNDO, null, null, null, null, null, false, "没有可撤销的操作");
                throw new NoUndoAvailableException("没有可撤销的操作");
            }
            schedules.clear();
            for (CourseSchedule s : undoSnapshot.schedules) {
                schedules.add(copySchedule(s));
            }
            idGenerator = undoSnapshot.idGenerator;
            undoSnapshot = null;
            auditLogService.recordLog(OperationType.UNDO, null, null, null, null, null, true, null);
            return schedules.stream().map(this::toResponse).collect(Collectors.toList());
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    public List<CourseScheduleResponse> findSchedules(CourseScheduleFilterRequest filter) {
        return findFilteredSortedSchedules(filter);
    }

    public byte[] exportSchedulesAsCsv(CourseScheduleFilterRequest filter) {
        List<CourseScheduleResponse> snapshot = findFilteredSortedSchedules(filter);
        return CourseScheduleCsvSupport.buildCourseListCsv(snapshot);
    }

    private List<CourseScheduleResponse> findFilteredSortedSchedules(CourseScheduleFilterRequest filter) {
        rwLock.readLock().lock();
        try {
            ScheduleFilterSupport.NormalizedFilter normalized = ScheduleFilterSupport.normalize(filter);
            ScheduleSortSupport.SortCondition sortCondition =
                    ScheduleSortSupport.parse(normalized.getSortBy(), normalized.getSortDirection());
            return sortCondition.apply(
                            schedules.stream().filter(s -> ScheduleFilterSupport.matches(s, normalized))
                    )
                    .map(this::toResponse)
                    .collect(Collectors.toList());
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public CourseScheduleStatisticsResponse getStatistics() {
        rwLock.readLock().lock();
        try {
            long totalCourses = schedules.size();
            long teacherCount = schedules.stream()
                    .map(CourseSchedule::getTeacherName)
                    .distinct()
                    .count();
            long classroomCount = schedules.stream()
                    .map(CourseSchedule::getClassroom)
                    .distinct()
                    .count();
            return new CourseScheduleStatisticsResponse(totalCourses, teacherCount, classroomCount);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public CourseScheduleWeekdayStatisticsResponse getWeekdayStatistics() {
        rwLock.readLock().lock();
        try {
            return WeekdayStatisticsSupport.calculateStatistics(schedules);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public List<TeacherCourseStatisticsResponse> getTeacherCourseStatistics() {
        rwLock.readLock().lock();
        try {
            return TeacherStatisticsSupport.calculateStatistics(schedules);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public List<ClassroomCourseStatisticsResponse> getClassroomCourseStatistics() {
        rwLock.readLock().lock();
        try {
            return ClassroomStatisticsSupport.calculateStatistics(schedules);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public List<CourseScheduleResponse> getClassroomDailySchedule(String classroom, String weekday) {
        return findDailySchedule(ScheduleQuerySupport.QueryType.CLASSROOM, normalizeClassroom(classroom), TimeSlotUtils.parseWeekdayInput(weekday));
    }

    public List<CourseScheduleResponse> getTeacherDailySchedule(String teacherName, String weekday) {
        return findDailySchedule(ScheduleQuerySupport.QueryType.TEACHER, normalizeTeacherName(teacherName), TimeSlotUtils.parseWeekdayInput(weekday));
    }

    public List<CourseScheduleResponse> getClassroomWeeklySchedule(String classroom) {
        return findWeeklySchedule(ScheduleQuerySupport.QueryType.CLASSROOM, normalizeClassroom(classroom));
    }

    public List<CourseScheduleResponse> getTeacherWeeklySchedule(String teacherName) {
        return findWeeklySchedule(ScheduleQuerySupport.QueryType.TEACHER, normalizeTeacherName(teacherName));
    }

    public TeacherWeeklySummaryResponse getTeacherWeeklySummary(String teacherName) {
        String normalizedTeacherName = normalizeTeacherName(teacherName);
        rwLock.readLock().lock();
        try {
            return TeacherStatisticsSupport.calculateWeeklySummary(normalizedTeacherName, schedules);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public TeacherFreeDaySummaryResponse getTeacherFreeDaySummary(String teacherName) {
        String normalizedTeacherName = normalizeTeacherName(teacherName);
        rwLock.readLock().lock();
        try {
            return TeacherStatisticsSupport.calculateFreeDaySummary(normalizedTeacherName, schedules);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public TeacherConsecutiveBusyDaysResponse getTeacherConsecutiveBusyDays(String teacherName) {
        String normalizedTeacherName = normalizeTeacherName(teacherName);
        rwLock.readLock().lock();
        try {
            return TeacherStatisticsSupport.calculateConsecutiveBusyDays(normalizedTeacherName, schedules);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public ClassroomWeeklySummaryResponse getClassroomWeeklySummary(String classroom) {
        String normalizedClassroom = normalizeClassroom(classroom);
        rwLock.readLock().lock();
        try {
            return ClassroomStatisticsSupport.calculateWeeklySummary(normalizedClassroom, schedules);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public ClassroomFreeDaySummaryResponse getClassroomFreeDaySummary(String classroom) {
        String normalizedClassroom = normalizeClassroom(classroom);
        rwLock.readLock().lock();
        try {
            return ClassroomStatisticsSupport.calculateFreeDaySummary(normalizedClassroom, schedules);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    private String normalizeTeacherName(String teacherName) {
        if (teacherName == null || teacherName.trim().isEmpty()) {
            throw new InvalidRequestParameterException("老师名不能为空");
        }
        return teacherName.trim();
    }

    private String normalizeClassroom(String classroom) {
        if (classroom == null || classroom.trim().isEmpty()) {
            throw new InvalidRequestParameterException("教室名不能为空");
        }
        return classroom.trim();
    }

    private List<CourseScheduleResponse> findDailySchedule(
            ScheduleQuerySupport.QueryType queryType, String queryValue, int targetWeekdayIndex
    ) {
        rwLock.readLock().lock();
        try {
            return toResponseList(
                    ScheduleQuerySupport.findDailySchedules(schedules, queryType, queryValue, targetWeekdayIndex)
            );
        } finally {
            rwLock.readLock().unlock();
        }
    }

    private List<CourseScheduleResponse> findWeeklySchedule(
            ScheduleQuerySupport.QueryType queryType, String queryValue
    ) {
        rwLock.readLock().lock();
        try {
            return toResponseList(
                    ScheduleQuerySupport.findWeeklySchedules(schedules, queryType, queryValue)
            );
        } finally {
            rwLock.readLock().unlock();
        }
    }

    private List<CourseScheduleResponse> toResponseList(List<CourseSchedule> source) {
        return source.stream().map(this::toResponse).collect(Collectors.toList());
    }

    public TeacherFreeTimeResponse getTeacherFreeTime(TeacherFreeTimeRequest request) {
        TeacherFreeTimeSupport.validateRequest(
                request.getTeacherName(), request.getWeekday(),
                request.getStartTimeFrom(), request.getStartTimeTo()
        );

        String normalizedTeacherName = request.getTeacherName().trim();
        int weekdayIndex = TimeSlotUtils.parseWeekdayInput(request.getWeekday());
        int[] timeRange = TeacherFreeTimeSupport.parseTimeRange(
                request.getStartTimeFrom(), request.getStartTimeTo()
        );

        rwLock.readLock().lock();
        try {
            return TeacherFreeTimeSupport.calculate(
                    schedules, normalizedTeacherName, weekdayIndex,
                    timeRange[0], timeRange[1]
            );
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public byte[] exportTeacherWeeklyScheduleAsCsv(String teacherName) {
        List<CourseScheduleResponse> snapshot = getTeacherWeeklySchedule(teacherName);
        return CourseScheduleCsvSupport.buildCourseListCsv(snapshot);
    }

    public byte[] exportClassroomWeeklyScheduleAsCsv(String classroom) {
        List<CourseScheduleResponse> snapshot = getClassroomWeeklySchedule(classroom);
        return CourseScheduleCsvSupport.buildCourseListCsv(snapshot);
    }

    public byte[] exportTeacherFreeTimeAsCsv(TeacherFreeTimeRequest request) {
        TeacherFreeTimeResponse response = getTeacherFreeTime(request);

        StringBuilder csv = new StringBuilder();
        csv.append("\uFEFF");
        csv.append("老师,周几,查询开始时间,查询结束时间,时间段类型,开始时间,结束时间,课程名称,教室\n");

        List<TimelineEntry> timeline = new ArrayList<>();
        for (TeacherFreeTimeResponse.OccupiedSlot slot : response.getOccupiedSlots()) {
            int startMinutes = TimeSlotUtils.extractStartTimeMinutesFromNormalized(slot.getTimeSlot());
            int endMinutes = TimeSlotUtils.extractEndTimeMinutesFromNormalized(slot.getTimeSlot());
            timeline.add(new TimelineEntry(startMinutes, "有课",
                    TimeSlotUtils.minutesToTimePoint(startMinutes),
                    TimeSlotUtils.minutesToTimePoint(endMinutes),
                    slot.getCourseName(), slot.getClassroom()));
        }
        for (TeacherFreeTimeResponse.FreeTimeWindow window : response.getFreeTimeWindows()) {
            int startMinutes = TimeSlotUtils.parseTimePoint(window.getStartTime());
            timeline.add(new TimelineEntry(startMinutes, "空闲",
                    window.getStartTime(), window.getEndTime(),
                    null, null));
        }
        timeline.sort(Comparator.comparingInt(TimelineEntry::getSortKey));

        for (TimelineEntry entry : timeline) {
            csv.append(CsvSupport.escapeField(response.getTeacherName())).append(',');
            csv.append(CsvSupport.escapeField(response.getWeekday())).append(',');
            csv.append(CsvSupport.escapeField(response.getQueryStartTimeFrom())).append(',');
            csv.append(CsvSupport.escapeField(response.getQueryStartTimeTo())).append(',');
            csv.append(entry.type).append(',');
            csv.append(CsvSupport.escapeField(entry.startTime)).append(',');
            csv.append(CsvSupport.escapeField(entry.endTime)).append(',');
            csv.append(CsvSupport.escapeField(entry.courseName)).append(',');
            csv.append(CsvSupport.escapeField(entry.classroom)).append('\n');
        }

        return csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private static class TimelineEntry {
        final int sortKey;
        final String type;
        final String startTime;
        final String endTime;
        final String courseName;
        final String classroom;

        TimelineEntry(int sortKey, String type, String startTime, String endTime,
                       String courseName, String classroom) {
            this.sortKey = sortKey;
            this.type = type;
            this.startTime = startTime;
            this.endTime = endTime;
            this.courseName = courseName;
            this.classroom = classroom;
        }

        int getSortKey() {
            return sortKey;
        }
    }

    public void clearAllSchedules() {
        rwLock.writeLock().lock();
        try {
            for (CourseSchedule s : schedules) {
                auditLogService.recordLog(OperationType.BATCH_DELETE, s, true, null);
            }
            schedules.clear();
            undoSnapshot = null;
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    public void resetForTesting() {
        rwLock.writeLock().lock();
        try {
            schedules.clear();
            idGenerator = 1;
            undoSnapshot = null;
            auditLogService.clearAllLogs();
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    public CourseScheduleDeleteResponse deleteSchedule(Long id) {
        rwLock.writeLock().lock();
        try {
            CourseSchedule found;
            try {
                found = findById(id);
            } catch (ScheduleNotFoundException e) {
                auditLogService.recordLog(OperationType.DELETE, id, null, null, null, null, false, e.getMessage());
                throw e;
            }
            commitUndoSnapshot(takeUndoSnapshot());
            schedules.remove(found);
            auditLogService.recordLog(OperationType.DELETE, found, true, null);
            return toDeleteResponse(found);
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    public CourseScheduleBatchDeleteResponse deleteSchedulesBatch(List<Long> ids) {
        rwLock.writeLock().lock();
        try {
            if (ids == null || ids.isEmpty()) {
                if (ids == null) {
                    auditLogService.recordLog(OperationType.BATCH_DELETE, null, null, null, null, null, false, "请求列表不能为空");
                    throw new InvalidRequestParameterException("请求列表不能为空");
                }
                return new CourseScheduleBatchDeleteResponse(0, 0, 0, new ArrayList<>(), new ArrayList<>());
            }

            java.util.Set<Long> processedValidIds = new java.util.HashSet<>();
            List<CourseScheduleResponse> successItems = new ArrayList<>();
            List<CourseScheduleBatchDeleteFailure> failureItems = new ArrayList<>();
            List<CourseSchedule> toRemove = new ArrayList<>();

            for (Long id : ids) {
                if (id == null || id <= 0) {
                    String errorMsg = "无效的课程 ID";
                    failureItems.add(new CourseScheduleBatchDeleteFailure(id, errorMsg));
                    auditLogService.recordLog(OperationType.BATCH_DELETE, id, null, null, null, null, false, errorMsg);
                    continue;
                }

                if (processedValidIds.contains(id)) {
                    String errorMsg = "重复 id 已忽略";
                    failureItems.add(new CourseScheduleBatchDeleteFailure(id, errorMsg));
                    auditLogService.recordLog(OperationType.BATCH_DELETE, id, null, null, null, null, false, errorMsg);
                    continue;
                }
                processedValidIds.add(id);

                CourseSchedule found = null;
                for (CourseSchedule s : schedules) {
                    if (s.getId().equals(id)) {
                        found = s;
                        break;
                    }
                }

                if (found != null) {
                    toRemove.add(found);
                    successItems.add(toResponse(found));
                    auditLogService.recordLog(OperationType.BATCH_DELETE, found, true, null);
                } else {
                    String errorMsg = "课程安排不存在";
                    failureItems.add(new CourseScheduleBatchDeleteFailure(id, errorMsg));
                    auditLogService.recordLog(OperationType.BATCH_DELETE, id, null, null, null, null, false, errorMsg);
                }
            }

            if (!toRemove.isEmpty()) {
                commitUndoSnapshot(takeUndoSnapshot());
            }
            schedules.removeAll(toRemove);

            return new CourseScheduleBatchDeleteResponse(
                    ids.size(),
                    successItems.size(),
                    failureItems.size(),
                    successItems,
                    failureItems
            );
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    public CourseScheduleResponse updateSchedule(Long id, CourseScheduleUpdateRequest request) {
        rwLock.writeLock().lock();
        try {
            String courseName = request != null ? request.getCourseName() : null;
            String teacherName = request != null ? request.getTeacherName() : null;
            String classroom = request != null ? request.getClassroom() : null;
            String timeSlot = request != null ? request.getTimeSlot() : null;

            CourseSchedule existing;
            try {
                existing = findById(id);
            } catch (ScheduleNotFoundException e) {
                auditLogService.recordLog(OperationType.UPDATE, id, courseName, teacherName, classroom, timeSlot, false, e.getMessage());
                throw e;
            }

            ValidatedScheduleData validated;
            try {
                validated = validateAndNormalize(courseName, teacherName, classroom, timeSlot);
            } catch (InvalidRequestParameterException | InvalidTimeSlotException e) {
                auditLogService.recordLog(OperationType.UPDATE, id, courseName, teacherName, classroom, timeSlot, false, e.getMessage());
                throw e;
            }

            try {
                checkConflictsAgainstList(schedules, validated, id, "");
            } catch (TeacherConflictException | ClassroomConflictException e) {
                auditLogService.recordLog(OperationType.UPDATE, id, validated.courseName, validated.teacherName, validated.classroom, validated.timeSlot, false, e.getMessage());
                throw e;
            }

            commitUndoSnapshot(takeUndoSnapshot());
            boolean contentChanged = !existing.getCourseName().equals(validated.courseName)
                    || !existing.getTeacherName().equals(validated.teacherName)
                    || !existing.getClassroom().equals(validated.classroom)
                    || !existing.getTimeSlot().equals(validated.timeSlot);
            existing.setCourseName(validated.courseName);
            existing.setTeacherName(validated.teacherName);
            existing.setClassroom(validated.classroom);
            existing.setTimeSlot(validated.timeSlot);
            if (contentChanged) {
                existing.incrementRevision();
            }

            auditLogService.recordLog(OperationType.UPDATE, existing, true, null);
            return toResponse(existing);
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    public CourseScheduleResponse addSchedule(CourseScheduleCreateRequest request) {
        rwLock.writeLock().lock();
        try {
            String courseName = request != null ? request.getCourseName() : null;
            String teacherName = request != null ? request.getTeacherName() : null;
            String classroom = request != null ? request.getClassroom() : null;
            String timeSlot = request != null ? request.getTimeSlot() : null;

            ValidatedScheduleData validated;
            try {
                validated = validateAndNormalize(courseName, teacherName, classroom, timeSlot);
            } catch (InvalidRequestParameterException | InvalidTimeSlotException e) {
                auditLogService.recordLog(OperationType.CREATE, null, courseName, teacherName, classroom, timeSlot, false, e.getMessage());
                throw e;
            }

            try {
                checkConflictsAgainstList(schedules, validated, null, "");
            } catch (TeacherConflictException | ClassroomConflictException e) {
                auditLogService.recordLog(OperationType.CREATE, null, validated.courseName, validated.teacherName, validated.classroom, validated.timeSlot, false, e.getMessage());
                throw e;
            }

            commitUndoSnapshot(takeUndoSnapshot());
            CourseSchedule schedule = createScheduleFromValidated(validated);
            schedules.add(schedule);
            auditLogService.recordLog(OperationType.CREATE, schedule, true, null);
            return toResponse(schedule);
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    public CourseScheduleConflictPreCheckResponse preCheckConflicts(CourseScheduleConflictPreCheckRequest request) {
        String courseName = request != null ? request.getCourseName() : null;
        String teacherName = request != null ? request.getTeacherName() : null;
        String classroom = request != null ? request.getClassroom() : null;
        String timeSlot = request != null ? request.getTimeSlot() : null;

        ValidatedScheduleData validated = validateAndNormalize(courseName, teacherName, classroom, timeSlot);

        rwLock.readLock().lock();
        try {
            java.util.Set<Long> emptyExcludes = java.util.Collections.emptySet();

            List<ScheduleConflictSupport.ConflictMatch> matches = ScheduleConflictSupport.findAllConflicts(
                    schedules, validated.teacherName, validated.classroom, validated.timeSlot, emptyExcludes);

            List<ConflictDetailDTO> conflicts = new ArrayList<>();
            for (ScheduleConflictSupport.ConflictMatch match : matches) {
                conflicts.add(buildConflictDetailFromMatch(
                        match, validated.teacherName, validated.classroom, validated.timeSlot, null));
            }
            OutageWindowProvider.OutageWindow outageMatch =
                    findOverlappingOutage(validated.classroom, validated.timeSlot);
            if (outageMatch != null) {
                conflicts.add(new ConflictDetailDTO(
                        ConflictDetailDTO.ConflictType.CLASSROOM,
                        (Long) null,
                        "教室停用",
                        null,
                        validated.classroom,
                        validated.timeSlot,
                        "教室 " + validated.classroom + " 在时间段 " + validated.timeSlot
                                + " 已临时停用（外部事件号 " + outageMatch.getEventNo() + "）"));
            }

            sortConflictDetails(conflicts);

            return new CourseScheduleConflictPreCheckResponse(
                    conflicts.isEmpty(),
                    conflicts.size(),
                    conflicts
            );
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public ConflictRiskPreviewResponse conflictRiskPreview(ConflictRiskPreviewRequest request) {
        if (request == null) {
            throw new InvalidRequestParameterException("冲突风险预览请求不能为空");
        }

        String teacherName = request.getTeacherName();
        String classroom = request.getClassroom();
        String timeSlot = request.getTimeSlot();

        if (teacherName == null || teacherName.trim().isEmpty()) {
            throw new InvalidRequestParameterException("老师名不能为空");
        }
        if (classroom == null || classroom.trim().isEmpty()) {
            throw new InvalidRequestParameterException("教室名不能为空");
        }
        if (timeSlot == null || timeSlot.trim().isEmpty()) {
            throw new InvalidRequestParameterException("时间段不能为空");
        }

        String normalizedTeacherName = teacherName.trim();
        String normalizedClassroom = classroom.trim();
        String normalizedTimeSlot = TimeSlotUtils.normalize(timeSlot);

        rwLock.readLock().lock();
        try {
            java.util.Set<Long> emptyExcludes = java.util.Collections.emptySet();
            List<ScheduleConflictSupport.ConflictMatch> matches = ScheduleConflictSupport.findAllConflicts(
                    schedules, normalizedTeacherName, normalizedClassroom, normalizedTimeSlot, emptyExcludes);

            return ConflictRiskPreviewSupport.buildResponse(matches);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    @FunctionalInterface
    private interface BatchItemExtractor<T> {
        BatchItemFields extract(int index, T item) throws InvalidRequestParameterException, InvalidTimeSlotException;
    }

    private static class BatchItemFields {
        final String courseName;
        final String teacherName;
        final String classroom;
        final String timeSlot;

        BatchItemFields(String courseName, String teacherName, String classroom, String timeSlot) {
            this.courseName = courseName;
            this.teacherName = teacherName;
            this.classroom = classroom;
            this.timeSlot = timeSlot;
        }
    }

    private static void validateItemCommonFields(int index, String courseName, String timeSlot,
                                                 String otherFieldName, String otherFieldValue) {
        if (courseName == null || courseName.trim().isEmpty()) {
            throw new InvalidRequestParameterException("第 " + (index + 1) + " 项课程名不能为空");
        }
        if (otherFieldValue == null || otherFieldValue.trim().isEmpty()) {
            throw new InvalidRequestParameterException("第 " + (index + 1) + " 项" + otherFieldName + "不能为空");
        }
        if (timeSlot == null || timeSlot.trim().isEmpty()) {
            throw new InvalidRequestParameterException("第 " + (index + 1) + " 项时间段不能为空");
        }
    }

    private <T> BatchPreCheckResponse doBatchPreCheck(
            String commonFieldLabel,
            String commonFieldValue,
            List<T> items,
            BatchItemExtractor<T> itemExtractor) {

        if (commonFieldValue == null || commonFieldValue.trim().isEmpty()) {
            throw new InvalidRequestParameterException(commonFieldLabel + "不能为空");
        }
        if (items == null) {
            throw new InvalidRequestParameterException("待排课程列表不能为空");
        }

        String normalizedCommonField = commonFieldValue.trim();

        List<ValidatedScheduleData> validatedItems = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            T item = items.get(i);
            if (item == null) {
                throw new InvalidRequestParameterException("第 " + (i + 1) + " 项课程请求不能为空");
            }
            BatchItemFields fields = itemExtractor.extract(i, item);
            validatedItems.add(new ValidatedScheduleData(
                    fields.courseName, fields.teacherName, fields.classroom, fields.timeSlot));
        }

        rwLock.readLock().lock();
        try {
            java.util.Set<Long> emptyExcludes = java.util.Collections.emptySet();
            List<BatchPreCheckItemResponse> itemResponses = new ArrayList<>();
            int totalConflictCount = 0;

            List<ScheduleConflictSupport.PendingItem> pendingItems = new ArrayList<>();
            for (int i = 0; i < validatedItems.size(); i++) {
                ValidatedScheduleData v = validatedItems.get(i);
                pendingItems.add(new ScheduleConflictSupport.PendingItem(
                        i, v.courseName, v.teacherName, v.classroom, v.timeSlot));
            }

            for (int i = 0; i < validatedItems.size(); i++) {
                ValidatedScheduleData currentItem = validatedItems.get(i);
                List<ConflictDetailDTO> conflicts = new ArrayList<>();

                List<ScheduleConflictSupport.ConflictMatch> existingMatches = ScheduleConflictSupport.findAllConflicts(
                        schedules, currentItem.teacherName, currentItem.classroom, currentItem.timeSlot, emptyExcludes);

                for (ScheduleConflictSupport.ConflictMatch match : existingMatches) {
                    conflicts.add(buildConflictDetailFromMatch(
                            match, currentItem.teacherName, currentItem.classroom, currentItem.timeSlot, null));
                }

                List<ScheduleConflictSupport.PendingConflictMatch> internalMatches =
                        ScheduleConflictSupport.findInternalConflictsForItem(pendingItems, i);

                for (ScheduleConflictSupport.PendingConflictMatch match : internalMatches) {
                    conflicts.add(buildConflictDetailFromPendingMatch(
                            match, currentItem.teacherName, currentItem.classroom, currentItem.timeSlot));
                }

                OutageWindowProvider.OutageWindow outageMatch =
                        findOverlappingOutage(currentItem.classroom, currentItem.timeSlot);
                if (outageMatch != null) {
                    conflicts.add(new ConflictDetailDTO(
                            ConflictDetailDTO.ConflictType.CLASSROOM,
                            (Long) null,
                            "教室停用",
                            null,
                            currentItem.classroom,
                            currentItem.timeSlot,
                            "教室 " + currentItem.classroom + " 在时间段 " + currentItem.timeSlot
                                    + " 已临时停用（外部事件号 " + outageMatch.getEventNo() + "）"));
                }

                sortConflictDetails(conflicts);

                boolean canSchedule = conflicts.isEmpty();
                totalConflictCount += conflicts.size();

                itemResponses.add(new BatchPreCheckItemResponse(
                        i,
                        currentItem.courseName,
                        canSchedule,
                        conflicts.size(),
                        conflicts
                ));
            }

            boolean allCanSchedule = totalConflictCount == 0;
            return new BatchPreCheckResponse(allCanSchedule, totalConflictCount, itemResponses);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public BatchPreCheckResponse preCheckConflictsByTeacherBatch(TeacherBatchPreCheckRequest request) {
        if (request == null) {
            throw new InvalidRequestParameterException("请求不能为空");
        }

        return doBatchPreCheck("老师名", request.getTeacherName(), request.getItems(), (index, item) -> {
            String courseName = item.getCourseName();
            String classroom = item.getClassroom();
            String timeSlot = item.getTimeSlot();

            validateItemCommonFields(index, courseName, timeSlot, "教室名", classroom);

            return new BatchItemFields(
                    courseName.trim(),
                    request.getTeacherName().trim(),
                    classroom.trim(),
                    TimeSlotUtils.normalize(timeSlot)
            );
        });
    }

    public BatchPreCheckResponse preCheckConflictsByClassroomBatch(ClassroomBatchPreCheckRequest request) {
        if (request == null) {
            throw new InvalidRequestParameterException("请求不能为空");
        }

        return doBatchPreCheck("教室名", request.getClassroom(), request.getItems(), (index, item) -> {
            String courseName = item.getCourseName();
            String teacherName = item.getTeacherName();
            String timeSlot = item.getTimeSlot();

            validateItemCommonFields(index, courseName, timeSlot, "老师名", teacherName);

            return new BatchItemFields(
                    courseName.trim(),
                    teacherName.trim(),
                    request.getClassroom().trim(),
                    TimeSlotUtils.normalize(timeSlot)
            );
        });
    }

    public PreCheckSummaryResponse preCheckConflictsSummary(CourseScheduleConflictPreCheckRequest request) {
        CourseScheduleConflictPreCheckResponse response = preCheckConflicts(request);
        return PreCheckSummarySupport.calculateSingleSummary(response);
    }

    public List<ConflictGroupDTO> groupConflictsBySource(CourseScheduleConflictPreCheckRequest request) {
        CourseScheduleConflictPreCheckResponse response = preCheckConflicts(request);
        return ConflictGroupSupport.groupBySource(response.getConflictDetails());
    }

    public List<ConflictGroupDTO> groupConflictsBySourceForTeacherBatch(TeacherBatchPreCheckRequest request) {
        BatchPreCheckResponse response = preCheckConflictsByTeacherBatch(request);
        return ConflictGroupSupport.groupBySourceFromBatch(response);
    }

    public List<ConflictGroupDTO> groupConflictsBySourceForClassroomBatch(ClassroomBatchPreCheckRequest request) {
        BatchPreCheckResponse response = preCheckConflictsByClassroomBatch(request);
        return ConflictGroupSupport.groupBySourceFromBatch(response);
    }

    public List<ConflictTypeGroupDTO> groupConflictsByConflictType(CourseScheduleConflictPreCheckRequest request) {
        CourseScheduleConflictPreCheckResponse response = preCheckConflicts(request);
        return ConflictTypeGroupSupport.groupByConflictType(response.getConflictDetails());
    }

    public List<ConflictTypeGroupDTO> groupConflictsByConflictTypeForTeacherBatch(TeacherBatchPreCheckRequest request) {
        BatchPreCheckResponse response = preCheckConflictsByTeacherBatch(request);
        return ConflictTypeGroupSupport.groupByConflictTypeFromBatch(response);
    }

    public List<ConflictTypeGroupDTO> groupConflictsByConflictTypeForClassroomBatch(ClassroomBatchPreCheckRequest request) {
        BatchPreCheckResponse response = preCheckConflictsByClassroomBatch(request);
        return ConflictTypeGroupSupport.groupByConflictTypeFromBatch(response);
    }

    public List<ConflictTargetDetailDTO> getConflictTargetDetails(CourseScheduleConflictPreCheckRequest request) {
        CourseScheduleConflictPreCheckResponse response = preCheckConflicts(request);
        return ConflictTargetDetailSupport.buildTargetDetails(response.getConflictDetails());
    }

    public List<ConflictTargetDetailDTO> getConflictTargetDetailsForTeacherBatch(TeacherBatchPreCheckRequest request) {
        BatchPreCheckResponse response = preCheckConflictsByTeacherBatch(request);

        Map<Integer, ConflictTargetDetailSupport.ItemDisplayInfo> itemDisplayContext =
                BatchDisplayContextSupport.buildTeacherBatchDisplayContext(request);

        return ConflictTargetDetailSupport.buildTargetDetailsFromBatch(response, itemDisplayContext);
    }

    public List<ConflictTargetDetailDTO> getConflictTargetDetailsForClassroomBatch(ClassroomBatchPreCheckRequest request) {
        BatchPreCheckResponse response = preCheckConflictsByClassroomBatch(request);

        Map<Integer, ConflictTargetDetailSupport.ItemDisplayInfo> itemDisplayContext =
                BatchDisplayContextSupport.buildClassroomBatchDisplayContext(request);

        return ConflictTargetDetailSupport.buildTargetDetailsFromBatch(response, itemDisplayContext);
    }

    public List<PendingConflictPairDTO> getPendingConflictPairsForTeacherBatch(TeacherBatchPreCheckRequest request) {
        BatchPreCheckResponse response = preCheckConflictsByTeacherBatch(request);

        Map<Integer, ConflictTargetDetailSupport.ItemDisplayInfo> itemDisplayContext =
                BatchDisplayContextSupport.buildTeacherBatchDisplayContext(request);

        return PendingConflictPairSupport.buildPendingConflictPairs(response, itemDisplayContext);
    }

    public List<PendingConflictPairDTO> getPendingConflictPairsForClassroomBatch(ClassroomBatchPreCheckRequest request) {
        BatchPreCheckResponse response = preCheckConflictsByClassroomBatch(request);

        Map<Integer, ConflictTargetDetailSupport.ItemDisplayInfo> itemDisplayContext =
                BatchDisplayContextSupport.buildClassroomBatchDisplayContext(request);

        return PendingConflictPairSupport.buildPendingConflictPairs(response, itemDisplayContext);
    }

    public List<PendingConflictConnectedGroupDTO> getPendingConflictConnectedGroupsForTeacherBatch(TeacherBatchPreCheckRequest request) {
        BatchPreCheckResponse response = preCheckConflictsByTeacherBatch(request);

        Map<Integer, ConflictTargetDetailSupport.ItemDisplayInfo> itemDisplayContext =
                BatchDisplayContextSupport.buildTeacherBatchDisplayContext(request);

        return PendingConflictConnectedGroupSupport.buildConnectedGroups(response, itemDisplayContext);
    }

    public List<PendingConflictConnectedGroupDTO> getPendingConflictConnectedGroupsForClassroomBatch(ClassroomBatchPreCheckRequest request) {
        BatchPreCheckResponse response = preCheckConflictsByClassroomBatch(request);

        Map<Integer, ConflictTargetDetailSupport.ItemDisplayInfo> itemDisplayContext =
                BatchDisplayContextSupport.buildClassroomBatchDisplayContext(request);

        return PendingConflictConnectedGroupSupport.buildConnectedGroups(response, itemDisplayContext);
    }

    public List<PendingConflictImpactSummaryDTO> getPendingConflictImpactSummaryForTeacherBatch(TeacherBatchPreCheckRequest request) {
        BatchPreCheckResponse response = preCheckConflictsByTeacherBatch(request);

        Map<Integer, ConflictTargetDetailSupport.ItemDisplayInfo> itemDisplayContext =
                BatchDisplayContextSupport.buildTeacherBatchDisplayContext(request);

        return PendingConflictImpactSummarySupport.buildImpactSummary(response, itemDisplayContext);
    }

    public List<PendingConflictImpactSummaryDTO> getPendingConflictImpactSummaryForClassroomBatch(ClassroomBatchPreCheckRequest request) {
        BatchPreCheckResponse response = preCheckConflictsByClassroomBatch(request);

        Map<Integer, ConflictTargetDetailSupport.ItemDisplayInfo> itemDisplayContext =
                BatchDisplayContextSupport.buildClassroomBatchDisplayContext(request);

        return PendingConflictImpactSummarySupport.buildImpactSummary(response, itemDisplayContext);
    }

    public PreCheckSummaryResponse preCheckConflictsByTeacherBatchSummary(TeacherBatchPreCheckRequest request) {
        BatchPreCheckResponse response = preCheckConflictsByTeacherBatch(request);
        return PreCheckSummarySupport.calculateBatchSummary(response, PreCheckSummaryResponse.PreCheckType.TEACHER_BATCH);
    }

    public PreCheckSummaryResponse preCheckConflictsByClassroomBatchSummary(ClassroomBatchPreCheckRequest request) {
        BatchPreCheckResponse response = preCheckConflictsByClassroomBatch(request);
        return PreCheckSummarySupport.calculateBatchSummary(response, PreCheckSummaryResponse.PreCheckType.CLASSROOM_BATCH);
    }

    public ManualReviewSummaryResponse getManualReviewSummaryForTeacherBatch(TeacherBatchPreCheckRequest request) {
        BatchPreCheckResponse response = preCheckConflictsByTeacherBatch(request);
        return ManualReviewSummarySupport.calculate(response);
    }

    public ManualReviewSummaryResponse getManualReviewSummaryForClassroomBatch(ClassroomBatchPreCheckRequest request) {
        BatchPreCheckResponse response = preCheckConflictsByClassroomBatch(request);
        return ManualReviewSummarySupport.calculate(response);
    }

    public byte[] exportPreCheckAsCsv(CourseScheduleConflictPreCheckRequest request) {
        String courseName = request != null ? request.getCourseName() : null;
        CourseScheduleConflictPreCheckResponse response = preCheckConflicts(request);
        String normalizedCourseName = courseName != null ? courseName.trim() : "";
        return PreCheckCsvSupport.buildSinglePreCheckCsv(normalizedCourseName, response);
    }

    public byte[] exportTeacherBatchPreCheckAsCsv(TeacherBatchPreCheckRequest request) {
        BatchPreCheckResponse response = preCheckConflictsByTeacherBatch(request);
        return PreCheckCsvSupport.buildBatchPreCheckCsv("按老师批量预检", response);
    }

    public byte[] exportClassroomBatchPreCheckAsCsv(ClassroomBatchPreCheckRequest request) {
        BatchPreCheckResponse response = preCheckConflictsByClassroomBatch(request);
        return PreCheckCsvSupport.buildBatchPreCheckCsv("按教室批量预检", response);
    }

    public CourseScheduleBatchResponse addSchedulesBatch(List<CourseScheduleCreateRequest> requests) {
        rwLock.writeLock().lock();
        try {
            if (requests == null) {
                auditLogService.recordLog(OperationType.BATCH_CREATE, null, null, null, null, null, false, "请求列表不能为空");
                throw new InvalidRequestParameterException("请求列表不能为空");
            }

            UndoSnapshot pendingSnapshot = takeUndoSnapshot();

            List<CourseScheduleResponse> successItems = new ArrayList<>();
            List<CourseScheduleBatchFailure> failureItems = new ArrayList<>();
            List<CourseSchedule> batchCreatedSchedules = new ArrayList<>();

            for (int i = 0; i < requests.size(); i++) {
                CourseScheduleCreateRequest request = requests.get(i);
                String displayCourseName = "未知课程";
                try {
                    if (request == null) {
                        String errorMsg = "课程请求不能为空";
                        failureItems.add(new CourseScheduleBatchFailure(i, displayCourseName, errorMsg));
                        auditLogService.recordLog(OperationType.BATCH_CREATE, null, displayCourseName, null, null, null, false, errorMsg);
                        continue;
                    }
                    displayCourseName = request.getCourseName() != null ? request.getCourseName() : "未知课程";

                    ValidatedScheduleData validated = validateAndNormalize(
                            request.getCourseName(),
                            request.getTeacherName(),
                            request.getClassroom(),
                            request.getTimeSlot()
                    );

                    checkConflictsAgainstList(
                            batchCreatedSchedules,
                            validated,
                            null,
                            "与本次批量新增的其他课程冲突"
                    );

                    checkConflictsAgainstList(
                            schedules,
                            validated,
                            null,
                            "已有课程安排"
                    );

                    CourseSchedule schedule = createScheduleFromValidated(validated);
                    batchCreatedSchedules.add(schedule);
                    successItems.add(toResponse(schedule));
                    auditLogService.recordLog(OperationType.BATCH_CREATE, schedule, true, null);
                } catch (InvalidRequestParameterException | InvalidTimeSlotException |
                         TeacherConflictException | ClassroomConflictException e) {
                    String teacherName = request != null ? request.getTeacherName() : null;
                    String classroom = request != null ? request.getClassroom() : null;
                    String timeSlot = request != null ? request.getTimeSlot() : null;
                    failureItems.add(new CourseScheduleBatchFailure(i, displayCourseName, e.getMessage()));
                    auditLogService.recordLog(OperationType.BATCH_CREATE, null, displayCourseName, teacherName, classroom, timeSlot, false, e.getMessage());
                }
            }

            if (!batchCreatedSchedules.isEmpty()) {
                commitUndoSnapshot(pendingSnapshot);
            }
            schedules.addAll(batchCreatedSchedules);

            return new CourseScheduleBatchResponse(
                    requests.size(),
                    successItems.size(),
                    failureItems.size(),
                    successItems,
                    failureItems
            );
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    public CourseScheduleBatchTimeSlotUpdateResponse batchUpdateTimeSlot(CourseScheduleBatchTimeSlotUpdateRequest request) {
        rwLock.writeLock().lock();
        try {
            if (request == null) {
                auditLogService.recordLog(OperationType.BATCH_UPDATE_TIME_SLOT, null, null, null, null, null, false, "请求不能为空");
                throw new InvalidRequestParameterException("请求不能为空");
            }
            if (request.getFromTimeSlot() == null || request.getFromTimeSlot().trim().isEmpty()) {
                auditLogService.recordLog(OperationType.BATCH_UPDATE_TIME_SLOT, null, null, null, null, null, false, "原时间段不能为空");
                throw new InvalidRequestParameterException("原时间段不能为空");
            }
            if (request.getToTimeSlot() == null || request.getToTimeSlot().trim().isEmpty()) {
                auditLogService.recordLog(OperationType.BATCH_UPDATE_TIME_SLOT, null, null, null, null, request.getFromTimeSlot(), false, "目标时间段不能为空");
                throw new InvalidRequestParameterException("目标时间段不能为空");
            }

            String normalizedFromTimeSlot;
            String normalizedToTimeSlot;
            try {
                normalizedFromTimeSlot = TimeSlotUtils.normalize(request.getFromTimeSlot());
            } catch (InvalidTimeSlotException e) {
                auditLogService.recordLog(OperationType.BATCH_UPDATE_TIME_SLOT, null, null, null, null, request.getFromTimeSlot(), false, e.getMessage());
                throw e;
            }
            try {
                normalizedToTimeSlot = TimeSlotUtils.normalize(request.getToTimeSlot());
            } catch (InvalidTimeSlotException e) {
                auditLogService.recordLog(OperationType.BATCH_UPDATE_TIME_SLOT, null, null, null, null, request.getToTimeSlot(), false, e.getMessage());
                throw e;
            }

            if (normalizedFromTimeSlot.equals(normalizedToTimeSlot)) {
                auditLogService.recordLog(OperationType.BATCH_UPDATE_TIME_SLOT, null, null, null, null, normalizedToTimeSlot, false, "原时间段与目标时间段相同");
                return new CourseScheduleBatchTimeSlotUpdateResponse(0, 0, normalizedFromTimeSlot, normalizedToTimeSlot, new ArrayList<>());
            }

            List<CourseSchedule> toUpdate = new ArrayList<>();
            for (CourseSchedule s : schedules) {
                if (s.getTimeSlot().equals(normalizedFromTimeSlot)) {
                    toUpdate.add(s);
                }
            }

            int matchedCount = toUpdate.size();
            if (matchedCount == 0) {
                auditLogService.recordLog(OperationType.BATCH_UPDATE_TIME_SLOT, null, null, null, null, normalizedFromTimeSlot, false, "没有匹配的课程");
                return new CourseScheduleBatchTimeSlotUpdateResponse(0, 0, normalizedFromTimeSlot, normalizedToTimeSlot, new ArrayList<>());
            }

            try {
                checkBatchTimeSlotExternalConflicts(toUpdate, normalizedToTimeSlot);
                checkBatchTimeSlotInternalConflicts(toUpdate, normalizedToTimeSlot);
                checkBatchTimeSlotOutageConflicts(toUpdate, normalizedToTimeSlot);
            } catch (TeacherConflictException | ClassroomConflictException e) {
                for (CourseSchedule s : toUpdate) {
                    auditLogService.recordLog(
                            OperationType.BATCH_UPDATE_TIME_SLOT,
                            s.getId(),
                            s.getCourseName(),
                            s.getTeacherName(),
                            s.getClassroom(),
                            normalizedToTimeSlot,
                            false,
                            e.getMessage()
                    );
                }
                throw e;
            }

            commitUndoSnapshot(takeUndoSnapshot());
            List<CourseScheduleResponse> updatedItems = new ArrayList<>();
            for (CourseSchedule schedule : toUpdate) {
                schedule.setTimeSlot(normalizedToTimeSlot);
                schedule.incrementRevision();
                updatedItems.add(toResponse(schedule));
                auditLogService.recordLog(OperationType.BATCH_UPDATE_TIME_SLOT, schedule, true, null);
            }

            return new CourseScheduleBatchTimeSlotUpdateResponse(
                    matchedCount,
                    matchedCount,
                    normalizedFromTimeSlot,
                    normalizedToTimeSlot,
                    updatedItems
            );
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * 获取指定 ID 课程安排的快照（副本），用于调课方案提交时保存原始排课内容。
     */
    public List<CourseSchedule> snapshotSchedulesByIds(java.util.Set<Long> ids) {
        rwLock.readLock().lock();
        try {
            List<CourseSchedule> snapshot = new ArrayList<>();
            for (CourseSchedule s : schedules) {
                if (ids.contains(s.getId())) {
                    snapshot.add(copySchedule(s));
                }
            }
            return snapshot;
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * 获取全部课程安排的快照（副本），用于调课方案预检查。
     */
    public List<CourseSchedule> snapshotAllSchedules() {
        rwLock.readLock().lock();
        try {
            List<CourseSchedule> snapshot = new ArrayList<>();
            for (CourseSchedule s : schedules) {
                snapshot.add(copySchedule(s));
            }
            return snapshot;
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * 原子地应用整份普通调课方案。
     *
     * <p>在写锁内重新校验：每条课程仍存在且与方案提交时的快照一致、
     * 调整后不与方案外课程冲突、不落入生效中的教室停用范围、方案内部互不冲突。
     * 任一校验失败则整份方案不生效并返回全部冲突明细；全部通过才一次性应用所有变更。
     *
     * @return 冲突明细，空列表表示方案已成功应用
     */
    public List<RescheduleConflictDTO> applyRescheduleAtomically(List<ReschedulePlanItem> items) {
        rwLock.writeLock().lock();
        try {
            List<OutageWindowProvider.OutageWindow> windows = currentOutageWindows();
            List<RescheduleConflictDTO> conflicts =
                    RescheduleConflictSupport.computeConflicts(schedules, items, windows);
            if (!conflicts.isEmpty()) {
                for (ReschedulePlanItem item : items) {
                    auditLogService.recordLog(OperationType.RESCHEDULE, item.getScheduleId(),
                            item.getCourseName(), item.getTeacherName(),
                            item.getNewClassroom(), item.getNewTimeSlot(),
                            false, "调课方案未生效：存在冲突");
                }
                return conflicts;
            }

            commitUndoSnapshot(takeUndoSnapshot());
            for (ReschedulePlanItem item : items) {
                CourseSchedule schedule = findById(item.getScheduleId());
                applyItemChange(schedule, item.getNewClassroom(), item.getNewTimeSlot());
                auditLogService.recordRescheduleLog(
                        schedule, item.getOriginalClassroom(), item.getOriginalTimeSlot(), true, null);
            }
            return new ArrayList<>();
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * 原子地应用整份停用修复方案。调用方（停用修复服务）须已持有域写锁，
     * 本方法<strong>不再加锁</strong>，以保证「校验任务版本 + 应用课程变更」在同一临界区内完成。
     *
     * @param items         修复调整项
     * @param outageWindows 当前生效停用窗口（含本次停用范围调整后的最新窗口）
     * @return 冲突明细，空列表表示修复已成功应用
     */
    public List<RescheduleConflictDTO> applyRepairAtomicallyWhileLocked(
            List<ReschedulePlanItem> items,
            List<OutageWindowProvider.OutageWindow> outageWindows,
            String operator, String referenceNo) {
        List<RescheduleConflictDTO> conflicts =
                RescheduleConflictSupport.computeConflicts(schedules, items, outageWindows);
        if (!conflicts.isEmpty()) {
            for (ReschedulePlanItem item : items) {
                auditLogService.recordLog(OperationType.REPAIR_RESCHEDULE, item.getScheduleId(),
                        item.getCourseName(), item.getTeacherName(),
                        item.getNewClassroom(), item.getNewTimeSlot(),
                        false, "停用修复方案未生效：存在冲突", operator, referenceNo);
            }
            return conflicts;
        }

        commitUndoSnapshot(takeUndoSnapshot());
        for (ReschedulePlanItem item : items) {
            CourseSchedule schedule = findById(item.getScheduleId());
            applyItemChange(schedule, item.getNewClassroom(), item.getNewTimeSlot());
            auditLogService.recordRescheduleLog(OperationType.REPAIR_RESCHEDULE,
                    schedule, item.getOriginalClassroom(), item.getOriginalTimeSlot(),
                    true, null, operator, referenceNo);
        }
        return new ArrayList<>();
    }

    /**
     * 返回当前生效停用窗口快照；无停用时返回空列表。可在持有域锁时调用。
     */
    public List<OutageWindowProvider.OutageWindow> currentOutageWindows() {
        if (outageWindowProvider == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(outageWindowProvider.activeWindows());
    }

    private void applyItemChange(CourseSchedule schedule, String newClassroom, String newTimeSlot) {
        boolean contentChanged = !schedule.getClassroom().equals(newClassroom)
                || !schedule.getTimeSlot().equals(newTimeSlot);
        schedule.setClassroom(newClassroom);
        schedule.setTimeSlot(newTimeSlot);
        if (contentChanged) {
            schedule.incrementRevision();
        }
    }

    private CourseSchedule copySchedule(CourseSchedule s) {
        return new CourseSchedule(s.getId(), s.getCourseName(), s.getTeacherName(),
                s.getClassroom(), s.getTimeSlot(), s.getRevision());
    }

    private void checkBatchTimeSlotExternalConflicts(
            List<CourseSchedule> toUpdate,
            String targetTimeSlot
    ) {
        java.util.Set<Long> toUpdateIds = new java.util.HashSet<>();
        for (CourseSchedule s : toUpdate) {
            toUpdateIds.add(s.getId());
        }

        for (CourseSchedule moving : toUpdate) {
            if (ScheduleConflictSupport.hasAnyTeacherConflict(schedules, moving.getTeacherName(), targetTimeSlot, toUpdateIds)) {
                throw new TeacherConflictException(
                        ScheduleConflictSupport.buildConflictReason(
                                ScheduleConflictSupport.ConflictType.TEACHER,
                                moving.getTeacherName(), targetTimeSlot, null)
                );
            }
            if (ScheduleConflictSupport.hasAnyClassroomConflict(schedules, moving.getClassroom(), targetTimeSlot, toUpdateIds)) {
                throw new ClassroomConflictException(
                        ScheduleConflictSupport.buildConflictReason(
                                ScheduleConflictSupport.ConflictType.CLASSROOM,
                                moving.getClassroom(), targetTimeSlot, null)
                );
            }
        }
    }

    private void checkBatchTimeSlotOutageConflicts(List<CourseSchedule> toUpdate, String targetTimeSlot) {
        if (outageWindowProvider == null) {
            return;
        }
        for (CourseSchedule moving : toUpdate) {
            OutageWindowProvider.OutageWindow window =
                    findOverlappingOutage(moving.getClassroom(), targetTimeSlot);
            if (window != null) {
                throw new ClassroomConflictException(
                        "教室 " + moving.getClassroom() + " 在时间段 " + targetTimeSlot
                                + " 已临时停用（外部事件号 " + window.getEventNo() + "），不得批量调整到该时段");
            }
        }
    }

    private void checkBatchTimeSlotInternalConflicts(
            List<CourseSchedule> toUpdate,
            String targetTimeSlot
    ) {
        List<CourseSchedule> confirmed = new ArrayList<>();
        for (CourseSchedule moving : toUpdate) {
            java.util.Set<Long> emptyExcludes = java.util.Collections.emptySet();
            if (ScheduleConflictSupport.hasAnyTeacherConflict(confirmed, moving.getTeacherName(), targetTimeSlot, emptyExcludes)) {
                throw new TeacherConflictException(
                        ScheduleConflictSupport.buildConflictReason(
                                ScheduleConflictSupport.ConflictType.TEACHER,
                                moving.getTeacherName(), targetTimeSlot, null)
                );
            }
            if (ScheduleConflictSupport.hasAnyClassroomConflict(confirmed, moving.getClassroom(), targetTimeSlot, emptyExcludes)) {
                throw new ClassroomConflictException(
                        ScheduleConflictSupport.buildConflictReason(
                                ScheduleConflictSupport.ConflictType.CLASSROOM,
                                moving.getClassroom(), targetTimeSlot, null)
                );
            }
            confirmed.add(moving);
        }
    }

    private ValidatedScheduleData validateAndNormalize(
            String courseName, String teacherName, String classroom, String timeSlot
    ) {
        if (courseName == null || courseName.trim().isEmpty()) {
            throw new InvalidRequestParameterException("课程名不能为空");
        }
        if (teacherName == null || teacherName.trim().isEmpty()) {
            throw new InvalidRequestParameterException("老师名不能为空");
        }
        if (classroom == null || classroom.trim().isEmpty()) {
            throw new InvalidRequestParameterException("教室名不能为空");
        }
        if (timeSlot == null || timeSlot.trim().isEmpty()) {
            throw new InvalidRequestParameterException("时间段不能为空");
        }

        String normalizedCourseName = courseName.trim();
        String normalizedTeacherName = teacherName.trim();
        String normalizedClassroom = classroom.trim();
        String normalizedTimeSlot = TimeSlotUtils.normalize(timeSlot);

        return new ValidatedScheduleData(normalizedCourseName, normalizedTeacherName, normalizedClassroom, normalizedTimeSlot);
    }

    private void checkConflictsAgainstList(
            List<CourseSchedule> existingSchedules,
            ValidatedScheduleData validated,
            Long excludeId,
            String conflictSuffix
    ) {
        java.util.Set<Long> excludeIds = excludeId != null
                ? java.util.Collections.singleton(excludeId)
                : java.util.Collections.emptySet();

        if (ScheduleConflictSupport.hasAnyTeacherConflict(
                existingSchedules, validated.teacherName, validated.timeSlot, excludeIds)) {
            throw new TeacherConflictException(ScheduleConflictSupport.buildConflictReason(
                    ScheduleConflictSupport.ConflictType.TEACHER,
                    validated.teacherName, validated.timeSlot, conflictSuffix));
        }

        if (ScheduleConflictSupport.hasAnyClassroomConflict(
                existingSchedules, validated.classroom, validated.timeSlot, excludeIds)) {
            throw new ClassroomConflictException(ScheduleConflictSupport.buildConflictReason(
                    ScheduleConflictSupport.ConflictType.CLASSROOM,
                    validated.classroom, validated.timeSlot, conflictSuffix));
        }

        checkNotInOutageWindow(validated.classroom, validated.timeSlot);
    }

    /**
     * 校验目标教室在目标时间段没有生效中的停用安排。后来新增/调整的课程不得排入已停用时段。
     * 需在持有域锁时调用。
     */
    private void checkNotInOutageWindow(String classroom, String timeSlot) {
        if (outageWindowProvider == null) {
            return;
        }
        for (OutageWindowProvider.OutageWindow window : outageWindowProvider.activeWindows()) {
            if (window.getClassroom().equals(classroom)
                    && ScheduleConflictSupport.timeSlotsOverlap(window.getTimeSlot(), timeSlot)) {
                throw new ClassroomConflictException(
                        "教室 " + classroom + " 在时间段 " + timeSlot
                                + " 已临时停用（外部事件号 " + window.getEventNo()
                                + (window.getReason() != null && !window.getReason().isEmpty()
                                        ? "，原因：" + window.getReason()
                                        : "")
                                + "），不得排入课程");
            }
        }
    }

    /**
     * 查询目标教室/时间段是否落入任一停用窗口；无冲突时返回 null。
     */
    private OutageWindowProvider.OutageWindow findOverlappingOutage(String classroom, String timeSlot) {
        if (outageWindowProvider == null) {
            return null;
        }
        for (OutageWindowProvider.OutageWindow window : outageWindowProvider.activeWindows()) {
            if (window.getClassroom().equals(classroom)
                    && ScheduleConflictSupport.timeSlotsOverlap(window.getTimeSlot(), timeSlot)) {
                return window;
            }
        }
        return null;
    }

    private CourseSchedule createScheduleFromValidated(ValidatedScheduleData validated) {
        CourseSchedule schedule = new CourseSchedule();
        schedule.setId(idGenerator++);
        schedule.setCourseName(validated.courseName);
        schedule.setTeacherName(validated.teacherName);
        schedule.setClassroom(validated.classroom);
        schedule.setTimeSlot(validated.timeSlot);
        return schedule;
    }

    private static class ValidatedScheduleData {
        final String courseName;
        final String teacherName;
        final String classroom;
        final String timeSlot;

        ValidatedScheduleData(String courseName, String teacherName, String classroom, String timeSlot) {
            this.courseName = courseName;
            this.teacherName = teacherName;
            this.classroom = classroom;
            this.timeSlot = timeSlot;
        }
    }

    private CourseSchedule findById(Long id) {
        for (CourseSchedule s : schedules) {
            if (s.getId().equals(id)) {
                return s;
            }
        }
        throw new ScheduleNotFoundException("课程安排不存在，ID: " + id);
    }

    private CourseScheduleDeleteResponse toDeleteResponse(CourseSchedule schedule) {
        return new CourseScheduleDeleteResponse(
                schedule.getId(),
                schedule.getCourseName(),
                schedule.getTeacherName(),
                schedule.getClassroom(),
                schedule.getTimeSlot()
        );
    }

    private CourseScheduleResponse toResponse(CourseSchedule schedule) {
        return new CourseScheduleResponse(
                schedule.getId(),
                schedule.getCourseName(),
                schedule.getTeacherName(),
                schedule.getClassroom(),
                schedule.getTimeSlot()
        );
    }

    private ConflictDetailDTO buildConflictDetailFromMatch(
            ScheduleConflictSupport.ConflictMatch match,
            String itemTeacherName,
            String itemClassroom,
            String itemTimeSlot,
            String conflictSuffix
    ) {
        ConflictDetailDTO.ConflictType dtoType = ConflictDetailDTO.ConflictType.valueOf(
                match.getConflictType().name());
        CourseSchedule existing = match.getSchedule();
        String subject = match.getConflictType() == ScheduleConflictSupport.ConflictType.TEACHER
                ? itemTeacherName : itemClassroom;
        String reason = ScheduleConflictSupport.buildConflictReason(
                match.getConflictType(), subject, itemTimeSlot, conflictSuffix);
        return new ConflictDetailDTO(
                dtoType,
                existing.getId(),
                existing.getCourseName(),
                existing.getTeacherName(),
                existing.getClassroom(),
                existing.getTimeSlot(),
                reason
        );
    }

    private ConflictDetailDTO buildConflictDetailFromPendingMatch(
            ScheduleConflictSupport.PendingConflictMatch match,
            String itemTeacherName,
            String itemClassroom,
            String itemTimeSlot
    ) {
        ScheduleConflictSupport.PendingItem other = match.getPendingItem();
        ConflictDetailDTO.ConflictType dtoType = ConflictDetailDTO.ConflictType.valueOf(
                match.getConflictType().name());
        String subject = match.getConflictType() == ScheduleConflictSupport.ConflictType.TEACHER
                ? itemTeacherName : itemClassroom;
        String reason = ScheduleConflictSupport.buildConflictReason(
                match.getConflictType(), subject, itemTimeSlot, "与本次批量待排课程冲突");
        return new ConflictDetailDTO(
                dtoType,
                other.getIndex(),
                other.getCourseName(),
                other.getTeacherName(),
                other.getClassroom(),
                other.getTimeSlot(),
                reason
        );
    }

    public List<ConflictSeveritySummaryDTO> getConflictSeveritySummary(CourseScheduleConflictPreCheckRequest request) {
        CourseScheduleConflictPreCheckResponse response = preCheckConflicts(request);
        return ConflictSeveritySummarySupport.buildFromSingle(response.getConflictDetails(), request.getCourseName());
    }

    public List<ConflictSeveritySummaryDTO> getConflictSeveritySummaryForTeacherBatch(TeacherBatchPreCheckRequest request) {
        BatchPreCheckResponse response = preCheckConflictsByTeacherBatch(request);
        List<ConflictTargetDetailDTO> targetDetails = ConflictTargetDetailSupport.buildTargetDetailsFromBatch(response);
        return ConflictSeveritySummarySupport.buildFromBatch(targetDetails);
    }

    public List<ConflictSeveritySummaryDTO> getConflictSeveritySummaryForClassroomBatch(ClassroomBatchPreCheckRequest request) {
        BatchPreCheckResponse response = preCheckConflictsByClassroomBatch(request);
        List<ConflictTargetDetailDTO> targetDetails = ConflictTargetDetailSupport.buildTargetDetailsFromBatch(response);
        return ConflictSeveritySummarySupport.buildFromBatch(targetDetails);
    }

    private void sortConflictDetails(List<ConflictDetailDTO> conflicts) {
        conflicts.sort(Comparator
                .comparing((ConflictDetailDTO c) -> c.getConflictType().ordinal())
                .thenComparingInt(c -> TimeSlotUtils.extractWeekdayIndexFromNormalized(c.getTimeSlot()))
                .thenComparingInt(c -> TimeSlotUtils.extractStartTimeMinutesFromNormalized(c.getTimeSlot()))
                .thenComparing((ConflictDetailDTO c) -> c.getCourseId(),
                        java.util.Comparator.nullsLast(Long::compareTo))
                .thenComparing((ConflictDetailDTO c) -> c.getPendingIndex(),
                        java.util.Comparator.nullsLast(Integer::compareTo))
        );
    }
}
