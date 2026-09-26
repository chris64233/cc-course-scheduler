package com.coursescheduler.service;

import com.coursescheduler.dto.BatchPreCheckItemResponse;
import com.coursescheduler.dto.BatchPreCheckResponse;
import com.coursescheduler.dto.ConflictDetailDTO;
import com.coursescheduler.dto.ConflictGroupDTO;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConflictGroupSupportTest {

    @Test
    void groupBySource_NullConflicts_ReturnsEmptyList() {
        List<ConflictGroupDTO> result = ConflictGroupSupport.groupBySource(null);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void groupBySource_EmptyConflicts_ReturnsEmptyList() {
        List<ConflictGroupDTO> result = ConflictGroupSupport.groupBySource(Collections.emptyList());

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void groupBySource_NoConflicts_ReturnsEmptyList() {
        List<ConflictDetailDTO> conflicts = new ArrayList<>();
        List<ConflictGroupDTO> result = ConflictGroupSupport.groupBySource(conflicts);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void groupBySource_OnlyExistingCourseConflicts() {
        List<ConflictDetailDTO> conflicts = Arrays.asList(
                new ConflictDetailDTO(ConflictDetailDTO.ConflictType.TEACHER, 1L, "数学", "张老师", "A101", "周一 08:00-10:00", "老师冲突"),
                new ConflictDetailDTO(ConflictDetailDTO.ConflictType.CLASSROOM, 2L, "英语", "李老师", "A101", "周一 08:00-10:00", "教室冲突")
        );

        List<ConflictGroupDTO> result = ConflictGroupSupport.groupBySource(conflicts);

        assertEquals(1, result.size());
        ConflictGroupDTO group = result.get(0);
        assertEquals(ConflictDetailDTO.SourceType.EXISTING_COURSE, group.getSourceType());
        assertEquals(2, group.getConflictCount());
        assertEquals(1, group.getTeacherConflictCount());
        assertEquals(1, group.getClassroomConflictCount());
        assertTrue(group.getCourseNames().contains("数学"));
        assertTrue(group.getCourseNames().contains("英语"));
    }

    @Test
    void groupBySource_OnlyPendingItemConflicts() {
        List<ConflictDetailDTO> conflicts = Arrays.asList(
                new ConflictDetailDTO(ConflictDetailDTO.ConflictType.TEACHER, 0, "待排数学", "张老师", "A101", "周一 08:00-10:00", "老师冲突"),
                new ConflictDetailDTO(ConflictDetailDTO.ConflictType.CLASSROOM, 1, "待排英语", "李老师", "A101", "周一 08:00-10:00", "教室冲突")
        );

        List<ConflictGroupDTO> result = ConflictGroupSupport.groupBySource(conflicts);

        assertEquals(1, result.size());
        ConflictGroupDTO group = result.get(0);
        assertEquals(ConflictDetailDTO.SourceType.PENDING_ITEM, group.getSourceType());
        assertEquals(2, group.getConflictCount());
        assertEquals(1, group.getTeacherConflictCount());
        assertEquals(1, group.getClassroomConflictCount());
        assertTrue(group.getCourseNames().contains("待排数学"));
        assertTrue(group.getCourseNames().contains("待排英语"));
    }

    @Test
    void groupBySource_BothSourceTypesPresent() {
        List<ConflictDetailDTO> conflicts = Arrays.asList(
                new ConflictDetailDTO(ConflictDetailDTO.ConflictType.TEACHER, 1L, "数学", "张老师", "A101", "周一 08:00-10:00", "已有课程老师冲突"),
                new ConflictDetailDTO(ConflictDetailDTO.ConflictType.CLASSROOM, 0, "待排物理", "李老师", "A101", "周一 08:00-10:00", "待排项教室冲突")
        );

        List<ConflictGroupDTO> result = ConflictGroupSupport.groupBySource(conflicts);

        assertEquals(2, result.size());

        ConflictGroupDTO existingGroup = result.get(0);
        assertEquals(ConflictDetailDTO.SourceType.EXISTING_COURSE, existingGroup.getSourceType());
        assertEquals(1, existingGroup.getConflictCount());
        assertEquals(1, existingGroup.getTeacherConflictCount());
        assertEquals(0, existingGroup.getClassroomConflictCount());
        assertTrue(existingGroup.getCourseNames().contains("数学"));

        ConflictGroupDTO pendingGroup = result.get(1);
        assertEquals(ConflictDetailDTO.SourceType.PENDING_ITEM, pendingGroup.getSourceType());
        assertEquals(1, pendingGroup.getConflictCount());
        assertEquals(0, pendingGroup.getTeacherConflictCount());
        assertEquals(1, pendingGroup.getClassroomConflictCount());
        assertTrue(pendingGroup.getCourseNames().contains("待排物理"));
    }

    @Test
    void groupBySource_TeacherConflictCountOnly() {
        List<ConflictDetailDTO> conflicts = Arrays.asList(
                new ConflictDetailDTO(ConflictDetailDTO.ConflictType.TEACHER, 1L, "数学", "张老师", "A101", "周一 08:00-10:00", "老师冲突1"),
                new ConflictDetailDTO(ConflictDetailDTO.ConflictType.TEACHER, 2L, "英语", "张老师", "B202", "周一 08:00-10:00", "老师冲突2")
        );

        List<ConflictGroupDTO> result = ConflictGroupSupport.groupBySource(conflicts);

        assertEquals(1, result.size());
        ConflictGroupDTO group = result.get(0);
        assertEquals(2, group.getConflictCount());
        assertEquals(2, group.getTeacherConflictCount());
        assertEquals(0, group.getClassroomConflictCount());
    }

    @Test
    void groupBySource_ClassroomConflictCountOnly() {
        List<ConflictDetailDTO> conflicts = Arrays.asList(
                new ConflictDetailDTO(ConflictDetailDTO.ConflictType.CLASSROOM, 1L, "数学", "张老师", "A101", "周一 08:00-10:00", "教室冲突1"),
                new ConflictDetailDTO(ConflictDetailDTO.ConflictType.CLASSROOM, 2L, "英语", "李老师", "A101", "周一 08:00-10:00", "教室冲突2")
        );

        List<ConflictGroupDTO> result = ConflictGroupSupport.groupBySource(conflicts);

        assertEquals(1, result.size());
        ConflictGroupDTO group = result.get(0);
        assertEquals(2, group.getConflictCount());
        assertEquals(0, group.getTeacherConflictCount());
        assertEquals(2, group.getClassroomConflictCount());
    }

    @Test
    void groupBySource_MixedConflictTypesInBothSources() {
        List<ConflictDetailDTO> conflicts = Arrays.asList(
                new ConflictDetailDTO(ConflictDetailDTO.ConflictType.TEACHER, 1L, "数学", "张老师", "A101", "周一 08:00-10:00", "已有老师"),
                new ConflictDetailDTO(ConflictDetailDTO.ConflictType.CLASSROOM, 2L, "英语", "李老师", "A101", "周一 08:00-10:00", "已有教室"),
                new ConflictDetailDTO(ConflictDetailDTO.ConflictType.TEACHER, 0, "待排物理", "张老师", "B202", "周一 08:00-10:00", "待排老师"),
                new ConflictDetailDTO(ConflictDetailDTO.ConflictType.CLASSROOM, 1, "待排化学", "王老师", "A101", "周一 08:00-10:00", "待排教室")
        );

        List<ConflictGroupDTO> result = ConflictGroupSupport.groupBySource(conflicts);

        assertEquals(2, result.size());

        ConflictGroupDTO existingGroup = result.get(0);
        assertEquals(ConflictDetailDTO.SourceType.EXISTING_COURSE, existingGroup.getSourceType());
        assertEquals(2, existingGroup.getConflictCount());
        assertEquals(1, existingGroup.getTeacherConflictCount());
        assertEquals(1, existingGroup.getClassroomConflictCount());

        ConflictGroupDTO pendingGroup = result.get(1);
        assertEquals(ConflictDetailDTO.SourceType.PENDING_ITEM, pendingGroup.getSourceType());
        assertEquals(2, pendingGroup.getConflictCount());
        assertEquals(1, pendingGroup.getTeacherConflictCount());
        assertEquals(1, pendingGroup.getClassroomConflictCount());
    }

    @Test
    void groupBySource_DeduplicatesCourseNames() {
        List<ConflictDetailDTO> conflicts = Arrays.asList(
                new ConflictDetailDTO(ConflictDetailDTO.ConflictType.TEACHER, 1L, "数学", "张老师", "A101", "周一 08:00-10:00", "老师冲突"),
                new ConflictDetailDTO(ConflictDetailDTO.ConflictType.CLASSROOM, 1L, "数学", "张老师", "A101", "周一 08:00-10:00", "教室冲突")
        );

        List<ConflictGroupDTO> result = ConflictGroupSupport.groupBySource(conflicts);

        assertEquals(1, result.size());
        ConflictGroupDTO group = result.get(0);
        assertEquals(1, group.getCourseNames().size());
        assertEquals("数学", group.getCourseNames().get(0));
    }

    @Test
    void groupBySource_SkipsNullEntries() {
        List<ConflictDetailDTO> conflicts = new ArrayList<>();
        conflicts.add(new ConflictDetailDTO(ConflictDetailDTO.ConflictType.TEACHER, 1L, "数学", "张老师", "A101", "周一 08:00-10:00", "老师冲突"));
        conflicts.add(null);

        List<ConflictGroupDTO> result = ConflictGroupSupport.groupBySource(conflicts);

        assertEquals(1, result.size());
        assertEquals(1, result.get(0).getConflictCount());
    }

    @Test
    void groupBySource_SkipsNullSourceType() {
        ConflictDetailDTO broken = new ConflictDetailDTO();
        broken.setConflictType(ConflictDetailDTO.ConflictType.TEACHER);
        broken.setSourceType(null);
        broken.setCourseName("坏数据");

        ConflictDetailDTO valid = new ConflictDetailDTO(
                ConflictDetailDTO.ConflictType.CLASSROOM, 1L, "数学", "张老师", "A101", "周一 08:00-10:00", "ok"
        );

        List<ConflictGroupDTO> result = ConflictGroupSupport.groupBySource(Arrays.asList(broken, valid));

        assertEquals(1, result.size());
        assertEquals(1, result.get(0).getConflictCount());
        assertEquals(ConflictDetailDTO.SourceType.EXISTING_COURSE, result.get(0).getSourceType());
    }

    @Test
    void groupBySource_CourseNameNull_SkippedInCourseNamesList() {
        ConflictDetailDTO conflict = new ConflictDetailDTO();
        conflict.setConflictType(ConflictDetailDTO.ConflictType.TEACHER);
        conflict.setSourceType(ConflictDetailDTO.SourceType.EXISTING_COURSE);
        conflict.setCourseName(null);

        List<ConflictGroupDTO> result = ConflictGroupSupport.groupBySource(Collections.singletonList(conflict));

        assertEquals(1, result.size());
        assertTrue(result.get(0).getCourseNames().isEmpty());
    }

    @Test
    void groupBySource_ConflictCountEqualsTeacherPlusClassroom() {
        List<ConflictDetailDTO> conflicts = Arrays.asList(
                new ConflictDetailDTO(ConflictDetailDTO.ConflictType.TEACHER, 1L, "数学", "张老师", "A101", "周一 08:00-10:00", "t1"),
                new ConflictDetailDTO(ConflictDetailDTO.ConflictType.CLASSROOM, 1L, "数学", "张老师", "A101", "周一 08:00-10:00", "c1"),
                new ConflictDetailDTO(ConflictDetailDTO.ConflictType.TEACHER, 0, "待排", "张老师", "B202", "周一 08:00-10:00", "t2"),
                new ConflictDetailDTO(ConflictDetailDTO.ConflictType.CLASSROOM, 1, "待排", "王老师", "A101", "周一 08:00-10:00", "c2")
        );

        List<ConflictGroupDTO> result = ConflictGroupSupport.groupBySource(conflicts);

        for (ConflictGroupDTO group : result) {
            assertEquals(group.getConflictCount(), group.getTeacherConflictCount() + group.getClassroomConflictCount());
        }
    }

    @Test
    void groupBySource_ExistingCourseGroupComesBeforePendingItemGroup() {
        List<ConflictDetailDTO> conflicts = Arrays.asList(
                new ConflictDetailDTO(ConflictDetailDTO.ConflictType.TEACHER, 0, "待排", "张老师", "A101", "周一 08:00-10:00", "待排冲突"),
                new ConflictDetailDTO(ConflictDetailDTO.ConflictType.TEACHER, 1L, "数学", "张老师", "A101", "周一 08:00-10:00", "已有冲突")
        );

        List<ConflictGroupDTO> result = ConflictGroupSupport.groupBySource(conflicts);

        assertEquals(2, result.size());
        assertEquals(ConflictDetailDTO.SourceType.EXISTING_COURSE, result.get(0).getSourceType());
        assertEquals(ConflictDetailDTO.SourceType.PENDING_ITEM, result.get(1).getSourceType());
    }

    @Test
    void groupBySource_SkipsNullConflictType() {
        ConflictDetailDTO broken = new ConflictDetailDTO();
        broken.setConflictType(null);
        broken.setSourceType(ConflictDetailDTO.SourceType.EXISTING_COURSE);
        broken.setCourseName("坏数据");

        ConflictDetailDTO valid = new ConflictDetailDTO(
                ConflictDetailDTO.ConflictType.CLASSROOM, 1L, "数学", "张老师", "A101", "周一 08:00-10:00", "ok"
        );

        List<ConflictGroupDTO> result = ConflictGroupSupport.groupBySource(Arrays.asList(broken, valid));

        assertEquals(1, result.size());
        assertEquals(1, result.get(0).getConflictCount());
        assertEquals(ConflictDetailDTO.SourceType.EXISTING_COURSE, result.get(0).getSourceType());
        assertEquals(0, result.get(0).getTeacherConflictCount());
        assertEquals(1, result.get(0).getClassroomConflictCount());
    }

    @Test
    void groupBySource_BothSourceTypeAndConflictTypeNull_AllSkipped() {
        ConflictDetailDTO noSource = new ConflictDetailDTO();
        noSource.setConflictType(ConflictDetailDTO.ConflictType.TEACHER);
        noSource.setSourceType(null);
        noSource.setCourseName("无来源");

        ConflictDetailDTO noType = new ConflictDetailDTO();
        noType.setConflictType(null);
        noType.setSourceType(ConflictDetailDTO.SourceType.PENDING_ITEM);
        noType.setCourseName("无类型");

        List<ConflictGroupDTO> result = ConflictGroupSupport.groupBySource(Arrays.asList(noSource, noType));

        assertTrue(result.isEmpty());
    }

    @Test
    void groupBySourceFromBatch_NullBatchResponse_ReturnsEmpty() {
        List<ConflictGroupDTO> result = ConflictGroupSupport.groupBySourceFromBatch(null);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void groupBySourceFromBatch_NullItems_ReturnsEmpty() {
        BatchPreCheckResponse response = new BatchPreCheckResponse();
        response.setItems(null);

        List<ConflictGroupDTO> result = ConflictGroupSupport.groupBySourceFromBatch(response);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void groupBySourceFromBatch_EmptyItems_ReturnsEmpty() {
        BatchPreCheckResponse response = new BatchPreCheckResponse();
        response.setItems(new ArrayList<>());

        List<ConflictGroupDTO> result = ConflictGroupSupport.groupBySourceFromBatch(response);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void groupBySourceFromBatch_SingleItemWithConflicts() {
        ConflictDetailDTO c1 = new ConflictDetailDTO(
                ConflictDetailDTO.ConflictType.TEACHER, 1L, "数学", "张老师", "A101", "周一 08:00-10:00", "老师冲突");
        ConflictDetailDTO c2 = new ConflictDetailDTO(
                ConflictDetailDTO.ConflictType.CLASSROOM, 2L, "英语", "李老师", "A101", "周一 08:00-10:00", "教室冲突");

        BatchPreCheckItemResponse item = new BatchPreCheckItemResponse();
        item.setOriginalIndex(0);
        item.setCourseName("物理");
        item.setCanSchedule(false);
        item.setConflictCount(2);
        item.setConflictDetails(Arrays.asList(c1, c2));

        BatchPreCheckResponse response = new BatchPreCheckResponse();
        response.setCanSchedule(false);
        response.setTotalConflictCount(2);
        response.setItems(Collections.singletonList(item));

        List<ConflictGroupDTO> result = ConflictGroupSupport.groupBySourceFromBatch(response);

        assertEquals(1, result.size());
        ConflictGroupDTO group = result.get(0);
        assertEquals(ConflictDetailDTO.SourceType.EXISTING_COURSE, group.getSourceType());
        assertEquals(2, group.getConflictCount());
        assertEquals(1, group.getTeacherConflictCount());
        assertEquals(1, group.getClassroomConflictCount());
        assertTrue(group.getCourseNames().contains("数学"));
        assertTrue(group.getCourseNames().contains("英语"));
        assertTrue(group.getCourseNames().contains("物理"));
    }

    @Test
    void groupBySourceFromBatch_MultipleItemsWithMixedSources() {
        ConflictDetailDTO existingTeacher = new ConflictDetailDTO(
                ConflictDetailDTO.ConflictType.TEACHER, 1L, "数学", "张老师", "A101", "周一 08:00-10:00", "已有老师");
        ConflictDetailDTO pendingClassroom = new ConflictDetailDTO(
                ConflictDetailDTO.ConflictType.CLASSROOM, 1, "待排化学", "李老师", "A101", "周一 08:00-10:00", "待排教室");
        ConflictDetailDTO existingClassroom = new ConflictDetailDTO(
                ConflictDetailDTO.ConflictType.CLASSROOM, 2L, "英语", "王老师", "B202", "周一 08:00-10:00", "已有教室");
        ConflictDetailDTO pendingTeacher = new ConflictDetailDTO(
                ConflictDetailDTO.ConflictType.TEACHER, 0, "待排物理", "张老师", "B202", "周一 08:00-10:00", "待排老师");

        BatchPreCheckItemResponse item0 = new BatchPreCheckItemResponse();
        item0.setOriginalIndex(0);
        item0.setCourseName("物理");
        item0.setConflictDetails(Arrays.asList(existingTeacher, pendingClassroom));

        BatchPreCheckItemResponse item1 = new BatchPreCheckItemResponse();
        item1.setOriginalIndex(1);
        item1.setCourseName("化学");
        item1.setConflictDetails(Arrays.asList(existingClassroom, pendingTeacher));

        BatchPreCheckResponse response = new BatchPreCheckResponse();
        response.setItems(Arrays.asList(item0, item1));

        List<ConflictGroupDTO> result = ConflictGroupSupport.groupBySourceFromBatch(response);

        assertEquals(2, result.size());

        ConflictGroupDTO existingGroup = result.get(0);
        assertEquals(ConflictDetailDTO.SourceType.EXISTING_COURSE, existingGroup.getSourceType());
        assertEquals(2, existingGroup.getConflictCount());
        assertEquals(1, existingGroup.getTeacherConflictCount());
        assertEquals(1, existingGroup.getClassroomConflictCount());
        assertTrue(existingGroup.getCourseNames().contains("数学"));
        assertTrue(existingGroup.getCourseNames().contains("英语"));
        assertTrue(existingGroup.getCourseNames().contains("物理"));
        assertTrue(existingGroup.getCourseNames().contains("化学"));

        ConflictGroupDTO pendingGroup = result.get(1);
        assertEquals(ConflictDetailDTO.SourceType.PENDING_ITEM, pendingGroup.getSourceType());
        assertEquals(1, pendingGroup.getConflictCount());
        assertEquals(1, pendingGroup.getTeacherConflictCount());
        assertEquals(1, pendingGroup.getClassroomConflictCount());
        assertTrue(pendingGroup.getCourseNames().contains("待排化学"));
        assertTrue(pendingGroup.getCourseNames().contains("待排物理"));
        assertTrue(pendingGroup.getCourseNames().contains("物理"));
        assertTrue(pendingGroup.getCourseNames().contains("化学"));
    }

    @Test
    void groupBySourceFromBatch_SkipsNullItemsAndNullDetails() {
        ConflictDetailDTO valid = new ConflictDetailDTO(
                ConflictDetailDTO.ConflictType.TEACHER, 1L, "数学", "张老师", "A101", "周一 08:00-10:00", "ok");

        BatchPreCheckItemResponse item0 = new BatchPreCheckItemResponse();
        item0.setConflictDetails(Collections.singletonList(valid));

        BatchPreCheckItemResponse itemWithNullDetails = new BatchPreCheckItemResponse();
        itemWithNullDetails.setConflictDetails(null);

        BatchPreCheckResponse response = new BatchPreCheckResponse();
        List<BatchPreCheckItemResponse> items = new ArrayList<>();
        items.add(item0);
        items.add(null);
        items.add(itemWithNullDetails);
        response.setItems(items);

        List<ConflictGroupDTO> result = ConflictGroupSupport.groupBySourceFromBatch(response);

        assertEquals(1, result.size());
        assertEquals(1, result.get(0).getConflictCount());
    }

    @Test
    void groupBySourceFromBatch_NoConflicts_ReturnsEmpty() {
        BatchPreCheckItemResponse item0 = new BatchPreCheckItemResponse();
        item0.setOriginalIndex(0);
        item0.setCourseName("数学");
        item0.setCanSchedule(true);
        item0.setConflictCount(0);
        item0.setConflictDetails(new ArrayList<>());

        BatchPreCheckItemResponse item1 = new BatchPreCheckItemResponse();
        item1.setOriginalIndex(1);
        item1.setCourseName("英语");
        item1.setCanSchedule(true);
        item1.setConflictCount(0);
        item1.setConflictDetails(new ArrayList<>());

        BatchPreCheckResponse response = new BatchPreCheckResponse();
        response.setCanSchedule(true);
        response.setTotalConflictCount(0);
        response.setItems(Arrays.asList(item0, item1));

        List<ConflictGroupDTO> result = ConflictGroupSupport.groupBySourceFromBatch(response);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void groupBySourceFromBatch_DeduplicatesCourseNamesAcrossItems() {
        ConflictDetailDTO c1 = new ConflictDetailDTO(
                ConflictDetailDTO.ConflictType.TEACHER, 1L, "数学", "张老师", "A101", "周一 08:00-10:00", "1");
        ConflictDetailDTO c2 = new ConflictDetailDTO(
                ConflictDetailDTO.ConflictType.CLASSROOM, 1L, "数学", "张老师", "A101", "周一 08:00-10:00", "2");

        BatchPreCheckItemResponse item0 = new BatchPreCheckItemResponse();
        item0.setOriginalIndex(0);
        item0.setCourseName("物理");
        item0.setConflictDetails(Collections.singletonList(c1));

        BatchPreCheckItemResponse item1 = new BatchPreCheckItemResponse();
        item1.setOriginalIndex(1);
        item1.setCourseName("化学");
        item1.setConflictDetails(Collections.singletonList(c2));

        BatchPreCheckResponse response = new BatchPreCheckResponse();
        response.setItems(Arrays.asList(item0, item1));

        List<ConflictGroupDTO> result = ConflictGroupSupport.groupBySourceFromBatch(response);

        assertEquals(1, result.size());
        assertTrue(result.get(0).getCourseNames().contains("数学"));
        assertEquals(1, result.get(0).getCourseNames().stream().filter(n -> n.equals("数学")).count());
    }

    @Test
    void groupBySourceFromBatch_PendingItemDedup_TwoItemsConflictNotDoubleCounted() {
        ConflictDetailDTO pendingFromItem0 = new ConflictDetailDTO(
                ConflictDetailDTO.ConflictType.TEACHER, 1, "物理", "张老师", "B202", "周一 08:00-10:00", "待排老师");
        ConflictDetailDTO pendingFromItem1 = new ConflictDetailDTO(
                ConflictDetailDTO.ConflictType.TEACHER, 0, "数学", "张老师", "A101", "周一 08:00-10:00", "待排老师");

        BatchPreCheckItemResponse item0 = new BatchPreCheckItemResponse();
        item0.setOriginalIndex(0);
        item0.setCourseName("数学");
        item0.setConflictDetails(Collections.singletonList(pendingFromItem0));

        BatchPreCheckItemResponse item1 = new BatchPreCheckItemResponse();
        item1.setOriginalIndex(1);
        item1.setCourseName("物理");
        item1.setConflictDetails(Collections.singletonList(pendingFromItem1));

        BatchPreCheckResponse response = new BatchPreCheckResponse();
        response.setItems(Arrays.asList(item0, item1));

        List<ConflictGroupDTO> result = ConflictGroupSupport.groupBySourceFromBatch(response);

        assertEquals(1, result.size());
        ConflictGroupDTO group = result.get(0);
        assertEquals(ConflictDetailDTO.SourceType.PENDING_ITEM, group.getSourceType());
        assertEquals(1, group.getConflictCount());
        assertEquals(1, group.getTeacherConflictCount());
        assertEquals(0, group.getClassroomConflictCount());
        assertTrue(group.getCourseNames().contains("数学"));
        assertTrue(group.getCourseNames().contains("物理"));
    }

    @Test
    void groupBySourceFromBatch_PendingItemDedup_BothConflictTypes() {
        ConflictDetailDTO pendingTeacherFrom0 = new ConflictDetailDTO(
                ConflictDetailDTO.ConflictType.TEACHER, 1, "物理", "张老师", "B202", "周一 08:00-10:00", "待排老师");
        ConflictDetailDTO pendingClassroomFrom0 = new ConflictDetailDTO(
                ConflictDetailDTO.ConflictType.CLASSROOM, 1, "物理", "李老师", "A101", "周一 08:00-10:00", "待排教室");
        ConflictDetailDTO pendingTeacherFrom1 = new ConflictDetailDTO(
                ConflictDetailDTO.ConflictType.TEACHER, 0, "数学", "张老师", "A101", "周一 08:00-10:00", "待排老师");
        ConflictDetailDTO pendingClassroomFrom1 = new ConflictDetailDTO(
                ConflictDetailDTO.ConflictType.CLASSROOM, 0, "数学", "王老师", "A101", "周一 08:00-10:00", "待排教室");

        BatchPreCheckItemResponse item0 = new BatchPreCheckItemResponse();
        item0.setOriginalIndex(0);
        item0.setCourseName("数学");
        item0.setConflictDetails(Arrays.asList(pendingTeacherFrom0, pendingClassroomFrom0));

        BatchPreCheckItemResponse item1 = new BatchPreCheckItemResponse();
        item1.setOriginalIndex(1);
        item1.setCourseName("物理");
        item1.setConflictDetails(Arrays.asList(pendingTeacherFrom1, pendingClassroomFrom1));

        BatchPreCheckResponse response = new BatchPreCheckResponse();
        response.setItems(Arrays.asList(item0, item1));

        List<ConflictGroupDTO> result = ConflictGroupSupport.groupBySourceFromBatch(response);

        assertEquals(1, result.size());
        ConflictGroupDTO group = result.get(0);
        assertEquals(ConflictDetailDTO.SourceType.PENDING_ITEM, group.getSourceType());
        assertEquals(1, group.getConflictCount());
        assertEquals(1, group.getTeacherConflictCount());
        assertEquals(1, group.getClassroomConflictCount());
        assertTrue(group.getCourseNames().contains("数学"));
        assertTrue(group.getCourseNames().contains("物理"));
    }

    @Test
    void groupBySourceFromBatch_PendingItemDedup_ThreeItemsConflict() {
        ConflictDetailDTO pendingFrom0to1 = new ConflictDetailDTO(
                ConflictDetailDTO.ConflictType.TEACHER, 1, "物理", "张老师", "B202", "周一 08:00-10:00", "01");
        ConflictDetailDTO pendingFrom0to2 = new ConflictDetailDTO(
                ConflictDetailDTO.ConflictType.TEACHER, 2, "化学", "张老师", "C303", "周一 08:00-10:00", "02");
        ConflictDetailDTO pendingFrom1to0 = new ConflictDetailDTO(
                ConflictDetailDTO.ConflictType.TEACHER, 0, "数学", "张老师", "A101", "周一 08:00-10:00", "10");
        ConflictDetailDTO pendingFrom1to2 = new ConflictDetailDTO(
                ConflictDetailDTO.ConflictType.TEACHER, 2, "化学", "张老师", "C303", "周一 08:00-10:00", "12");
        ConflictDetailDTO pendingFrom2to0 = new ConflictDetailDTO(
                ConflictDetailDTO.ConflictType.TEACHER, 0, "数学", "张老师", "A101", "周一 08:00-10:00", "20");
        ConflictDetailDTO pendingFrom2to1 = new ConflictDetailDTO(
                ConflictDetailDTO.ConflictType.TEACHER, 1, "物理", "张老师", "B202", "周一 08:00-10:00", "21");

        BatchPreCheckItemResponse item0 = new BatchPreCheckItemResponse();
        item0.setOriginalIndex(0);
        item0.setCourseName("数学");
        item0.setConflictDetails(Arrays.asList(pendingFrom0to1, pendingFrom0to2));

        BatchPreCheckItemResponse item1 = new BatchPreCheckItemResponse();
        item1.setOriginalIndex(1);
        item1.setCourseName("物理");
        item1.setConflictDetails(Arrays.asList(pendingFrom1to0, pendingFrom1to2));

        BatchPreCheckItemResponse item2 = new BatchPreCheckItemResponse();
        item2.setOriginalIndex(2);
        item2.setCourseName("化学");
        item2.setConflictDetails(Arrays.asList(pendingFrom2to0, pendingFrom2to1));

        BatchPreCheckResponse response = new BatchPreCheckResponse();
        response.setItems(Arrays.asList(item0, item1, item2));

        List<ConflictGroupDTO> result = ConflictGroupSupport.groupBySourceFromBatch(response);

        assertEquals(1, result.size());
        ConflictGroupDTO group = result.get(0);
        assertEquals(ConflictDetailDTO.SourceType.PENDING_ITEM, group.getSourceType());
        assertEquals(3, group.getConflictCount());
        assertEquals(3, group.getTeacherConflictCount());
        assertEquals(0, group.getClassroomConflictCount());
        assertTrue(group.getCourseNames().contains("数学"));
        assertTrue(group.getCourseNames().contains("物理"));
        assertTrue(group.getCourseNames().contains("化学"));
    }

    @Test
    void groupBySourceFromBatch_CourseNamesIncludeItemCourseName() {
        ConflictDetailDTO existingConflict = new ConflictDetailDTO(
                ConflictDetailDTO.ConflictType.TEACHER, 1L, "已有数学", "张老师", "A101", "周一 08:00-10:00", "已有");

        BatchPreCheckItemResponse item0 = new BatchPreCheckItemResponse();
        item0.setOriginalIndex(0);
        item0.setCourseName("新排物理");
        item0.setConflictDetails(Collections.singletonList(existingConflict));

        BatchPreCheckResponse response = new BatchPreCheckResponse();
        response.setItems(Collections.singletonList(item0));

        List<ConflictGroupDTO> result = ConflictGroupSupport.groupBySourceFromBatch(response);

        assertEquals(1, result.size());
        ConflictGroupDTO group = result.get(0);
        assertEquals(ConflictDetailDTO.SourceType.EXISTING_COURSE, group.getSourceType());
        assertTrue(group.getCourseNames().contains("已有数学"));
        assertTrue(group.getCourseNames().contains("新排物理"));
    }

    @Test
    void groupBySourceFromBatch_ExistingCourseNoDedup() {
        ConflictDetailDTO c1 = new ConflictDetailDTO(
                ConflictDetailDTO.ConflictType.TEACHER, 1L, "数学", "张老师", "A101", "周一 08:00-10:00", "1");
        ConflictDetailDTO c2 = new ConflictDetailDTO(
                ConflictDetailDTO.ConflictType.TEACHER, 2L, "英语", "张老师", "A101", "周一 08:00-10:00", "2");

        BatchPreCheckItemResponse item0 = new BatchPreCheckItemResponse();
        item0.setOriginalIndex(0);
        item0.setCourseName("物理");
        item0.setConflictDetails(Collections.singletonList(c1));

        BatchPreCheckItemResponse item1 = new BatchPreCheckItemResponse();
        item1.setOriginalIndex(1);
        item1.setCourseName("化学");
        item1.setConflictDetails(Collections.singletonList(c2));

        BatchPreCheckResponse response = new BatchPreCheckResponse();
        response.setItems(Arrays.asList(item0, item1));

        List<ConflictGroupDTO> result = ConflictGroupSupport.groupBySourceFromBatch(response);

        assertEquals(1, result.size());
        ConflictGroupDTO group = result.get(0);
        assertEquals(ConflictDetailDTO.SourceType.EXISTING_COURSE, group.getSourceType());
        assertEquals(2, group.getConflictCount());
    }
}
