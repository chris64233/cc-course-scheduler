package com.coursescheduler.service;

import com.coursescheduler.dto.BatchPreCheckItemResponse;
import com.coursescheduler.dto.BatchPreCheckResponse;
import com.coursescheduler.dto.ConflictDetailDTO;
import com.coursescheduler.dto.CourseScheduleConflictPreCheckResponse;
import com.coursescheduler.dto.PreCheckSummaryResponse;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PreCheckSummarySupportTest {

    @Test
    void calculateSingleSummary_NullResponse_ReturnsAllZeros() {
        PreCheckSummaryResponse result = PreCheckSummarySupport.calculateSingleSummary(null);

        assertEquals(PreCheckSummaryResponse.PreCheckType.SINGLE, result.getPreCheckType());
        assertEquals(0, result.getTotalItems());
        assertEquals(0, result.getSchedulableItems());
        assertEquals(0, result.getUnschedulableItems());
        assertEquals(0, result.getTotalConflicts());
        assertEquals(0, result.getTeacherConflicts());
        assertEquals(0, result.getClassroomConflicts());
        assertEquals(0, result.getExistingCourseConflicts());
        assertEquals(0, result.getPendingItemConflicts());
    }

    @Test
    void calculateSingleSummary_NullConflictDetails_CountsNoConflicts() {
        CourseScheduleConflictPreCheckResponse response = new CourseScheduleConflictPreCheckResponse();
        response.setCanSchedule(true);
        response.setConflictCount(0);
        response.setConflictDetails(null);

        PreCheckSummaryResponse result = PreCheckSummarySupport.calculateSingleSummary(response);

        assertEquals(1, result.getTotalItems());
        assertEquals(1, result.getSchedulableItems());
        assertEquals(0, result.getUnschedulableItems());
        assertEquals(0, result.getTotalConflicts());
        assertEquals(0, result.getTeacherConflicts());
        assertEquals(0, result.getClassroomConflicts());
    }

    @Test
    void calculateSingleSummary_NullConflictEntriesInList_SkipsNulls() {
        CourseScheduleConflictPreCheckResponse response = new CourseScheduleConflictPreCheckResponse();
        response.setCanSchedule(false);
        response.setConflictCount(2);
        response.setConflictDetails(Arrays.asList(
                new ConflictDetailDTO(ConflictDetailDTO.ConflictType.TEACHER, 1L, "已有课", "张老师", "A101", "周一 08:00-10:00", "time overlap"),
                null,
                new ConflictDetailDTO(ConflictDetailDTO.ConflictType.CLASSROOM, 2, "待排", "李老师", "A101", "周一 08:00-10:00", "classroom overlap")
        ));

        PreCheckSummaryResponse result = PreCheckSummarySupport.calculateSingleSummary(response);

        assertEquals(1, result.getTotalItems());
        assertEquals(0, result.getSchedulableItems());
        assertEquals(1, result.getUnschedulableItems());
        assertEquals(2, result.getTotalConflicts());
        assertEquals(1, result.getTeacherConflicts());
        assertEquals(1, result.getClassroomConflicts());
        assertEquals(1, result.getExistingCourseConflicts());
        assertEquals(1, result.getPendingItemConflicts());
    }

    @Test
    void calculateSingleSummary_ConflictCountExceedsDetails_UsesDetailCount() {
        CourseScheduleConflictPreCheckResponse response = new CourseScheduleConflictPreCheckResponse();
        response.setCanSchedule(false);
        response.setConflictCount(99);
        response.setConflictDetails(Collections.singletonList(
                new ConflictDetailDTO(ConflictDetailDTO.ConflictType.TEACHER, 1L, "已有课", "张老师", "A101", "周一 08:00-10:00", "overlap")
        ));

        PreCheckSummaryResponse result = PreCheckSummarySupport.calculateSingleSummary(response);

        assertEquals(1, result.getTotalConflicts());
        assertEquals(1, result.getTeacherConflicts());
        assertEquals(0, result.getClassroomConflicts());
        assertEquals(1, result.getExistingCourseConflicts());
        assertEquals(0, result.getPendingItemConflicts());
        assertEquals(
                result.getTotalConflicts(),
                result.getTeacherConflicts() + result.getClassroomConflicts()
        );
        assertEquals(
                result.getTotalConflicts(),
                result.getExistingCourseConflicts() + result.getPendingItemConflicts()
        );
    }

    @Test
    void calculateSingleSummary_ConflictCountZeroButDetailsPresent_UsesDetailCount() {
        CourseScheduleConflictPreCheckResponse response = new CourseScheduleConflictPreCheckResponse();
        response.setCanSchedule(false);
        response.setConflictCount(0);
        response.setConflictDetails(Arrays.asList(
                new ConflictDetailDTO(ConflictDetailDTO.ConflictType.TEACHER, 1L, "已有课", "张老师", "A101", "周一 08:00-10:00", "t"),
                new ConflictDetailDTO(ConflictDetailDTO.ConflictType.CLASSROOM, 2, "待排", "李老师", "A101", "周一 08:00-10:00", "c")
        ));

        PreCheckSummaryResponse result = PreCheckSummarySupport.calculateSingleSummary(response);

        assertEquals(2, result.getTotalConflicts());
        assertEquals(1, result.getTeacherConflicts());
        assertEquals(1, result.getClassroomConflicts());
    }

    @Test
    void calculateSingleSummary_NullConflictType_SkipsEntry() {
        ConflictDetailDTO broken = new ConflictDetailDTO();
        broken.setConflictType(null);
        broken.setSourceType(ConflictDetailDTO.SourceType.EXISTING_COURSE);
        broken.setCourseName("坏数据");
        broken.setReason("no type");

        ConflictDetailDTO valid = new ConflictDetailDTO(
                ConflictDetailDTO.ConflictType.CLASSROOM, 1L, "有效课", "张老师", "A101", "周一 08:00-10:00", "ok"
        );

        CourseScheduleConflictPreCheckResponse response = new CourseScheduleConflictPreCheckResponse();
        response.setCanSchedule(false);
        response.setConflictCount(2);
        response.setConflictDetails(Arrays.asList(broken, valid));

        PreCheckSummaryResponse result = PreCheckSummarySupport.calculateSingleSummary(response);

        assertEquals(1, result.getTotalConflicts());
        assertEquals(0, result.getTeacherConflicts());
        assertEquals(1, result.getClassroomConflicts());
        assertEquals(1, result.getExistingCourseConflicts());
        assertEquals(0, result.getPendingItemConflicts());
        assertEquals(
                result.getTotalConflicts(),
                result.getTeacherConflicts() + result.getClassroomConflicts()
        );
        assertEquals(
                result.getTotalConflicts(),
                result.getExistingCourseConflicts() + result.getPendingItemConflicts()
        );
    }

    @Test
    void calculateSingleSummary_NullSourceType_SkipsEntry() {
        ConflictDetailDTO broken = new ConflictDetailDTO();
        broken.setConflictType(ConflictDetailDTO.ConflictType.TEACHER);
        broken.setSourceType(null);
        broken.setCourseName("坏数据");
        broken.setReason("no source");

        ConflictDetailDTO valid = new ConflictDetailDTO(
                ConflictDetailDTO.ConflictType.TEACHER, 3, "待排", "李老师", "B202", "周二 10:00-12:00", "ok"
        );

        CourseScheduleConflictPreCheckResponse response = new CourseScheduleConflictPreCheckResponse();
        response.setCanSchedule(false);
        response.setConflictCount(2);
        response.setConflictDetails(Arrays.asList(broken, valid));

        PreCheckSummaryResponse result = PreCheckSummarySupport.calculateSingleSummary(response);

        assertEquals(1, result.getTotalConflicts());
        assertEquals(1, result.getTeacherConflicts());
        assertEquals(0, result.getClassroomConflicts());
        assertEquals(0, result.getExistingCourseConflicts());
        assertEquals(1, result.getPendingItemConflicts());
        assertEquals(
                result.getTotalConflicts(),
                result.getTeacherConflicts() + result.getClassroomConflicts()
        );
        assertEquals(
                result.getTotalConflicts(),
                result.getExistingCourseConflicts() + result.getPendingItemConflicts()
        );
    }

    @Test
    void calculateBatchSummary_NullResponse_ReturnsAllZeros() {
        PreCheckSummaryResponse result = PreCheckSummarySupport.calculateBatchSummary(
                null,
                PreCheckSummaryResponse.PreCheckType.TEACHER_BATCH
        );

        assertEquals(PreCheckSummaryResponse.PreCheckType.TEACHER_BATCH, result.getPreCheckType());
        assertEquals(0, result.getTotalItems());
        assertEquals(0, result.getSchedulableItems());
        assertEquals(0, result.getUnschedulableItems());
        assertEquals(0, result.getTotalConflicts());
        assertEquals(0, result.getTeacherConflicts());
        assertEquals(0, result.getClassroomConflicts());
        assertEquals(0, result.getExistingCourseConflicts());
        assertEquals(0, result.getPendingItemConflicts());
    }

    @Test
    void calculateBatchSummary_NullItemsList_CountsAsEmpty() {
        BatchPreCheckResponse response = new BatchPreCheckResponse();
        response.setItems(null);

        PreCheckSummaryResponse result = PreCheckSummarySupport.calculateBatchSummary(
                response,
                PreCheckSummaryResponse.PreCheckType.CLASSROOM_BATCH
        );

        assertEquals(PreCheckSummaryResponse.PreCheckType.CLASSROOM_BATCH, result.getPreCheckType());
        assertEquals(0, result.getTotalItems());
        assertEquals(0, result.getSchedulableItems());
        assertEquals(0, result.getUnschedulableItems());
        assertEquals(0, result.getTotalConflicts());
    }

    @Test
    void calculateBatchSummary_ItemsContainNulls_SkipsNullsInAllCounters() {
        BatchPreCheckItemResponse schedulable = new BatchPreCheckItemResponse();
        schedulable.setCourseName("数学");
        schedulable.setCanSchedule(true);
        schedulable.setConflictCount(0);
        schedulable.setConflictDetails(Collections.<ConflictDetailDTO>emptyList());

        BatchPreCheckItemResponse unschedulable = new BatchPreCheckItemResponse();
        unschedulable.setCourseName("物理");
        unschedulable.setCanSchedule(false);
        unschedulable.setConflictCount(1);
        unschedulable.setConflictDetails(Collections.singletonList(
                new ConflictDetailDTO(ConflictDetailDTO.ConflictType.TEACHER, 1L, "已有课", "张老师", "A101", "周一 08:00-10:00", "overlap")
        ));

        BatchPreCheckResponse response = new BatchPreCheckResponse();
        List<BatchPreCheckItemResponse> items = new ArrayList<>();
        items.add(schedulable);
        items.add(null);
        items.add(unschedulable);
        items.add(null);
        response.setItems(items);

        PreCheckSummaryResponse result = PreCheckSummarySupport.calculateBatchSummary(
                response,
                PreCheckSummaryResponse.PreCheckType.TEACHER_BATCH
        );

        assertEquals(2, result.getTotalItems());
        assertEquals(1, result.getSchedulableItems());
        assertEquals(1, result.getUnschedulableItems());
        assertEquals(1, result.getTotalConflicts());
        assertEquals(1, result.getTeacherConflicts());
        assertEquals(0, result.getClassroomConflicts());
        assertEquals(1, result.getExistingCourseConflicts());
        assertEquals(0, result.getPendingItemConflicts());
        assertEquals(
                result.getTotalItems(),
                result.getSchedulableItems() + result.getUnschedulableItems()
        );
    }

    @Test
    void calculateBatchSummary_ItemHasNullConflictDetails_AccumulatesSafely() {
        BatchPreCheckItemResponse item = new BatchPreCheckItemResponse();
        item.setCourseName("化学");
        item.setCanSchedule(false);
        item.setConflictCount(0);
        item.setConflictDetails(null);

        BatchPreCheckResponse response = new BatchPreCheckResponse();
        response.setItems(Collections.singletonList(item));

        PreCheckSummaryResponse result = PreCheckSummarySupport.calculateBatchSummary(
                response,
                PreCheckSummaryResponse.PreCheckType.TEACHER_BATCH
        );

        assertEquals(1, result.getTotalItems());
        assertEquals(0, result.getSchedulableItems());
        assertEquals(1, result.getUnschedulableItems());
        assertEquals(0, result.getTotalConflicts());
        assertEquals(0, result.getTeacherConflicts());
        assertEquals(0, result.getClassroomConflicts());
    }

    @Test
    void calculateBatchSummary_ItemConflictCountExceedsDetails_UsesDetailCount() {
        BatchPreCheckItemResponse itemA = new BatchPreCheckItemResponse();
        itemA.setCourseName("数学");
        itemA.setCanSchedule(false);
        itemA.setConflictCount(100);
        itemA.setConflictDetails(Collections.singletonList(
                new ConflictDetailDTO(ConflictDetailDTO.ConflictType.TEACHER, 1L, "已有课", "张老师", "A101", "周一 08:00-10:00", "t")
        ));

        BatchPreCheckItemResponse itemB = new BatchPreCheckItemResponse();
        itemB.setCourseName("物理");
        itemB.setCanSchedule(false);
        itemB.setConflictCount(200);
        itemB.setConflictDetails(Arrays.asList(
                new ConflictDetailDTO(ConflictDetailDTO.ConflictType.CLASSROOM, 2L, "已有课", "李老师", "B202", "周二 10:00-12:00", "c"),
                new ConflictDetailDTO(ConflictDetailDTO.ConflictType.CLASSROOM, 3, "待排", "王老师", "B202", "周二 10:00-12:00", "p")
        ));

        BatchPreCheckResponse response = new BatchPreCheckResponse();
        response.setItems(Arrays.asList(itemA, itemB));

        PreCheckSummaryResponse result = PreCheckSummarySupport.calculateBatchSummary(
                response,
                PreCheckSummaryResponse.PreCheckType.TEACHER_BATCH
        );

        assertEquals(3, result.getTotalConflicts());
        assertEquals(1, result.getTeacherConflicts());
        assertEquals(2, result.getClassroomConflicts());
        assertEquals(2, result.getExistingCourseConflicts());
        assertEquals(1, result.getPendingItemConflicts());
        assertEquals(
                result.getTotalConflicts(),
                result.getTeacherConflicts() + result.getClassroomConflicts()
        );
        assertEquals(
                result.getTotalConflicts(),
                result.getExistingCourseConflicts() + result.getPendingItemConflicts()
        );
    }
}
