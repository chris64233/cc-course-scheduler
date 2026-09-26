package com.coursescheduler.service;

import com.coursescheduler.dto.BatchPreCheckItemResponse;
import com.coursescheduler.dto.BatchPreCheckResponse;
import com.coursescheduler.dto.ConflictDetailDTO;
import com.coursescheduler.dto.ManualReviewSummaryResponse;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ManualReviewSummarySupportTest {

    @Test
    void calculate_NullResponse_ReturnsZeroValues() {
        ManualReviewSummaryResponse result = ManualReviewSummarySupport.calculate(null);

        assertEquals(0, result.getTotalItems());
        assertEquals(0, result.getConflictItems());
        assertFalse(result.isManualReviewRequired());
        assertNull(result.getReason());
    }

    @Test
    void calculate_EmptyItems_ReturnsZeroValues() {
        BatchPreCheckResponse response = new BatchPreCheckResponse();
        response.setItems(Collections.emptyList());

        ManualReviewSummaryResponse result = ManualReviewSummarySupport.calculate(response);

        assertEquals(0, result.getTotalItems());
        assertEquals(0, result.getConflictItems());
        assertFalse(result.isManualReviewRequired());
    }

    @Test
    void calculate_NoConflicts_ReturnsFalse() {
        BatchPreCheckItemResponse item0 = new BatchPreCheckItemResponse(0, "数学", true, 0, Collections.emptyList());
        BatchPreCheckItemResponse item1 = new BatchPreCheckItemResponse(1, "物理", true, 0, Collections.emptyList());

        BatchPreCheckResponse response = new BatchPreCheckResponse(true, 0, Arrays.asList(item0, item1));

        ManualReviewSummaryResponse result = ManualReviewSummarySupport.calculate(response);

        assertEquals(2, result.getTotalItems());
        assertEquals(0, result.getConflictItems());
        assertFalse(result.isManualReviewRequired());
        assertNull(result.getReason());
    }

    @Test
    void calculate_SingleConflict_OnlyTeacher_ReturnsFalse() {
        ConflictDetailDTO teacherConflict = new ConflictDetailDTO(
                ConflictDetailDTO.ConflictType.TEACHER, 1L, "已有课", "张老师", "A101", "周一 08:00-10:00", "老师冲突");

        BatchPreCheckItemResponse item0 = new BatchPreCheckItemResponse(0, "数学", false, 1, Collections.singletonList(teacherConflict));
        BatchPreCheckItemResponse item1 = new BatchPreCheckItemResponse(1, "物理", true, 0, Collections.emptyList());

        BatchPreCheckResponse response = new BatchPreCheckResponse(false, 1, Arrays.asList(item0, item1));

        ManualReviewSummaryResponse result = ManualReviewSummarySupport.calculate(response);

        assertEquals(2, result.getTotalItems());
        assertEquals(1, result.getConflictItems());
        assertFalse(result.isManualReviewRequired());
        assertNull(result.getReason());
    }

    @Test
    void calculate_SingleConflict_OnlyClassroom_ReturnsFalse() {
        ConflictDetailDTO classroomConflict = new ConflictDetailDTO(
                ConflictDetailDTO.ConflictType.CLASSROOM, 1L, "已有课", "张老师", "A101", "周一 08:00-10:00", "教室冲突");

        BatchPreCheckItemResponse item0 = new BatchPreCheckItemResponse(0, "数学", false, 1, Collections.singletonList(classroomConflict));

        BatchPreCheckResponse response = new BatchPreCheckResponse(false, 1, Collections.singletonList(item0));

        ManualReviewSummaryResponse result = ManualReviewSummarySupport.calculate(response);

        assertEquals(1, result.getTotalItems());
        assertEquals(1, result.getConflictItems());
        assertFalse(result.isManualReviewRequired());
        assertNull(result.getReason());
    }

    @Test
    void calculate_PendingItemWithBothTeacherAndClassroomConflicts_ReturnsTrue() {
        ConflictDetailDTO teacherPending = new ConflictDetailDTO(
                ConflictDetailDTO.ConflictType.TEACHER, 1, "待排课B", "张老师", "A101", "周一 08:00-10:00", "老师冲突");
        ConflictDetailDTO classroomPending = new ConflictDetailDTO(
                ConflictDetailDTO.ConflictType.CLASSROOM, 1, "待排课B", "张老师", "A101", "周一 08:00-10:00", "教室冲突");

        BatchPreCheckItemResponse item0 = new BatchPreCheckItemResponse(0, "数学", false, 2,
                Arrays.asList(teacherPending, classroomPending));

        BatchPreCheckResponse response = new BatchPreCheckResponse(false, 2, Collections.singletonList(item0));

        ManualReviewSummaryResponse result = ManualReviewSummarySupport.calculate(response);

        assertEquals(1, result.getTotalItems());
        assertEquals(1, result.getConflictItems());
        assertTrue(result.isManualReviewRequired());
        assertTrue(result.getReason().contains("老师和教室冲突"));
    }

    @Test
    void calculate_ExistingTeacherPlusPendingClassroom_WithPendingItems_ReturnsTrue() {
        ConflictDetailDTO teacherExisting = new ConflictDetailDTO(
                ConflictDetailDTO.ConflictType.TEACHER, 1L, "已有课", "张老师", "A101", "周一 08:00-10:00", "老师冲突");
        ConflictDetailDTO classroomPending = new ConflictDetailDTO(
                ConflictDetailDTO.ConflictType.CLASSROOM, 1, "待排课B", "张老师", "A101", "周一 08:00-10:00", "教室冲突");

        BatchPreCheckItemResponse item0 = new BatchPreCheckItemResponse(0, "数学", false, 2,
                Arrays.asList(teacherExisting, classroomPending));

        BatchPreCheckResponse response = new BatchPreCheckResponse(false, 2, Collections.singletonList(item0));

        ManualReviewSummaryResponse result = ManualReviewSummarySupport.calculate(response);

        assertTrue(result.isManualReviewRequired());
        assertTrue(result.getReason().contains("老师和教室冲突"));
    }

    @Test
    void calculate_OnlyExistingConflicts_BothTypes_ReturnsTrue() {
        ConflictDetailDTO teacherExisting = new ConflictDetailDTO(
                ConflictDetailDTO.ConflictType.TEACHER, 1L, "已有课A", "张老师", "A101", "周一 08:00-10:00", "老师冲突");
        ConflictDetailDTO classroomExisting = new ConflictDetailDTO(
                ConflictDetailDTO.ConflictType.CLASSROOM, 2L, "已有课B", "李老师", "A101", "周一 08:00-10:00", "教室冲突");

        BatchPreCheckItemResponse item0 = new BatchPreCheckItemResponse(0, "数学", false, 2,
                Arrays.asList(teacherExisting, classroomExisting));

        BatchPreCheckResponse response = new BatchPreCheckResponse(false, 2, Collections.singletonList(item0));

        ManualReviewSummaryResponse result = ManualReviewSummarySupport.calculate(response);

        assertTrue(result.isManualReviewRequired());
        assertTrue(result.getReason().contains("老师和教室冲突"));
    }

    @Test
    void calculate_ThreeItemChainConflict_ReturnsTrue() {
        ConflictDetailDTO aToB = new ConflictDetailDTO(
                ConflictDetailDTO.ConflictType.TEACHER, 1, "B", "张老师", "A101", "周一 08:00-10:00", "A与B老师冲突");
        ConflictDetailDTO bToC = new ConflictDetailDTO(
                ConflictDetailDTO.ConflictType.TEACHER, 2, "C", "张老师", "A101", "周一 08:00-10:00", "B与C老师冲突");

        BatchPreCheckItemResponse itemA = new BatchPreCheckItemResponse(0, "A", false, 1, Collections.singletonList(aToB));
        BatchPreCheckItemResponse itemB = new BatchPreCheckItemResponse(1, "B", false, 1, Collections.singletonList(bToC));
        BatchPreCheckItemResponse itemC = new BatchPreCheckItemResponse(2, "C", true, 0, Collections.emptyList());

        BatchPreCheckResponse response = new BatchPreCheckResponse(false, 2, Arrays.asList(itemA, itemB, itemC));

        ManualReviewSummaryResponse result = ManualReviewSummarySupport.calculate(response);

        assertEquals(3, result.getTotalItems());
        assertEquals(2, result.getConflictItems());
        assertTrue(result.isManualReviewRequired());
        assertTrue(result.getReason().contains("链式冲突"));
    }

    @Test
    void calculate_TwoItemPairConflict_ReturnsFalse() {
        ConflictDetailDTO aToB = new ConflictDetailDTO(
                ConflictDetailDTO.ConflictType.TEACHER, 1, "B", "张老师", "A101", "周一 08:00-10:00", "A与B老师冲突");

        BatchPreCheckItemResponse itemA = new BatchPreCheckItemResponse(0, "A", false, 1, Collections.singletonList(aToB));
        BatchPreCheckItemResponse itemB = new BatchPreCheckItemResponse(1, "B", true, 0, Collections.emptyList());

        BatchPreCheckResponse response = new BatchPreCheckResponse(false, 1, Arrays.asList(itemA, itemB));

        ManualReviewSummaryResponse result = ManualReviewSummarySupport.calculate(response);

        assertEquals(2, result.getTotalItems());
        assertEquals(1, result.getConflictItems());
        assertFalse(result.isManualReviewRequired());
        assertNull(result.getReason());
    }

    @Test
    void calculate_NullItemsInResponse_SkipsNulls() {
        BatchPreCheckItemResponse item0 = new BatchPreCheckItemResponse(0, "数学", true, 0, Collections.emptyList());

        List<BatchPreCheckItemResponse> items = new ArrayList<>();
        items.add(item0);
        items.add(null);

        BatchPreCheckResponse response = new BatchPreCheckResponse(true, 0, items);

        ManualReviewSummaryResponse result = ManualReviewSummarySupport.calculate(response);

        assertEquals(1, result.getTotalItems());
        assertEquals(0, result.getConflictItems());
        assertFalse(result.isManualReviewRequired());
    }

    @Test
    void calculate_NullConflictDetails_SkipsGracefully() {
        BatchPreCheckItemResponse item = new BatchPreCheckItemResponse();
        item.setOriginalIndex(0);
        item.setCourseName("数学");
        item.setCanSchedule(true);
        item.setConflictCount(0);
        item.setConflictDetails(null);

        BatchPreCheckResponse response = new BatchPreCheckResponse(true, 0, Collections.singletonList(item));

        ManualReviewSummaryResponse result = ManualReviewSummarySupport.calculate(response);

        assertEquals(1, result.getTotalItems());
        assertEquals(0, result.getConflictItems());
        assertFalse(result.isManualReviewRequired());
    }

    @Test
    void calculate_NullConflictEntryInDetails_SkipsNull() {
        ConflictDetailDTO teacherConflict = new ConflictDetailDTO(
                ConflictDetailDTO.ConflictType.TEACHER, 1L, "已有课", "张老师", "A101", "周一 08:00-10:00", "老师冲突");

        BatchPreCheckItemResponse item = new BatchPreCheckItemResponse(0, "数学", false, 1,
                Arrays.asList(teacherConflict, null));

        BatchPreCheckResponse response = new BatchPreCheckResponse(false, 1, Collections.singletonList(item));

        ManualReviewSummaryResponse result = ManualReviewSummarySupport.calculate(response);

        assertEquals(1, result.getTotalItems());
        assertEquals(1, result.getConflictItems());
        assertFalse(result.isManualReviewRequired());
    }

    @Test
    void calculate_DualTypeConflictNoPendingItem_ReturnsTrue() {
        ConflictDetailDTO teacherExisting = new ConflictDetailDTO(
                ConflictDetailDTO.ConflictType.TEACHER, 1L, "已有课", "张老师", "A101", "周一 08:00-10:00", "老师冲突");
        ConflictDetailDTO classroomExisting = new ConflictDetailDTO(
                ConflictDetailDTO.ConflictType.CLASSROOM, 2L, "已有课", "李老师", "A101", "周一 08:00-10:00", "教室冲突");

        BatchPreCheckItemResponse item = new BatchPreCheckItemResponse(0, "数学", false, 2,
                Arrays.asList(teacherExisting, classroomExisting));

        BatchPreCheckResponse response = new BatchPreCheckResponse(false, 2, Collections.singletonList(item));

        ManualReviewSummaryResponse result = ManualReviewSummarySupport.calculate(response);

        assertTrue(result.isManualReviewRequired());
        assertTrue(result.getReason().contains("老师和教室冲突"));
    }
}
