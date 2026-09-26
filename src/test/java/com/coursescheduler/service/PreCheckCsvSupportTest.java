package com.coursescheduler.service;

import com.coursescheduler.dto.BatchPreCheckItemResponse;
import com.coursescheduler.dto.BatchPreCheckResponse;
import com.coursescheduler.dto.ConflictDetailDTO;
import com.coursescheduler.dto.CourseScheduleConflictPreCheckResponse;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PreCheckCsvSupportTest {

    private static final String BOM = "\uFEFF";
    private static final String EXPECTED_HEADER =
            "预检类型,原始序号,课程名,是否可排,冲突类型,冲突来源,冲突课程/待排项,老师,教室,时间段,原因";

    @Test
    void testBuildSinglePreCheckCsv_NoConflict_OutputsOneRow() {
        CourseScheduleConflictPreCheckResponse response =
                new CourseScheduleConflictPreCheckResponse(true, 0, new ArrayList<>());

        byte[] csvBytes = PreCheckCsvSupport.buildSinglePreCheckCsv("物理", response);
        String csv = new String(csvBytes, StandardCharsets.UTF_8);

        assertTrue(csv.startsWith(BOM));
        String contentWithoutBom = csv.substring(1);
        String[] lines = contentWithoutBom.split("\n");

        assertEquals(2, lines.length);
        assertEquals(EXPECTED_HEADER, lines[0]);

        String[] cells = lines[1].split(",", -1);
        assertEquals("单项预检", cells[0]);
        assertEquals("", cells[1]);
        assertEquals("物理", cells[2]);
        assertEquals("是", cells[3]);
        assertEquals("", cells[4]);
        assertEquals("", cells[5]);
        assertEquals("", cells[6]);
        assertEquals("", cells[7]);
        assertEquals("", cells[8]);
        assertEquals("", cells[9]);
        assertEquals("", cells[10]);
    }

    @Test
    void testBuildSinglePreCheckCsv_OriginalIndexIsEmpty() {
        CourseScheduleConflictPreCheckResponse response =
                new CourseScheduleConflictPreCheckResponse(true, 0, null);

        byte[] csvBytes = PreCheckCsvSupport.buildSinglePreCheckCsv("物理", response);
        String csv = new String(csvBytes, StandardCharsets.UTF_8);
        String contentWithoutBom = csv.substring(1);
        String[] lines = contentWithoutBom.split("\n");

        String[] cells = lines[1].split(",", -1);
        assertEquals("", cells[1], "单项预检的原始序号必须为空");
    }

    @Test
    void testBuildSinglePreCheckCsv_NullConflictDetails_StillOutputsOneRow() {
        CourseScheduleConflictPreCheckResponse response =
                new CourseScheduleConflictPreCheckResponse(true, 0, null);

        byte[] csvBytes = PreCheckCsvSupport.buildSinglePreCheckCsv("数学", response);
        String csv = new String(csvBytes, StandardCharsets.UTF_8);
        String contentWithoutBom = csv.substring(1);
        String[] lines = contentWithoutBom.split("\n");

        assertEquals(2, lines.length);
    }

    @Test
    void testBuildSinglePreCheckCsv_TeacherConflict_RowHasConflictFields() {
        ConflictDetailDTO conflict = new ConflictDetailDTO(
                ConflictDetailDTO.ConflictType.TEACHER,
                1L, "数学", "张老师", "A101", "周一 08:00-10:00", "已有课程安排");

        CourseScheduleConflictPreCheckResponse response =
                new CourseScheduleConflictPreCheckResponse(false, 1,
                        Collections.singletonList(conflict));

        byte[] csvBytes = PreCheckCsvSupport.buildSinglePreCheckCsv("物理", response);
        String csv = new String(csvBytes, StandardCharsets.UTF_8);
        String contentWithoutBom = csv.substring(1);
        String[] lines = contentWithoutBom.split("\n");

        assertEquals(2, lines.length);
        String[] cells = lines[1].split(",", -1);
        assertEquals("单项预检", cells[0]);
        assertEquals("", cells[1]);
        assertEquals("物理", cells[2]);
        assertEquals("否", cells[3]);
        assertEquals("老师冲突", cells[4]);
        assertEquals("已有课程", cells[5]);
        assertEquals("数学(ID: 1)", cells[6]);
        assertEquals("张老师", cells[7]);
        assertEquals("A101", cells[8]);
        assertEquals("周一 08:00-10:00", cells[9]);
        assertEquals("已有课程安排", cells[10]);
    }

    @Test
    void testBuildSinglePreCheckCsv_ClassroomConflictWithPendingItem() {
        ConflictDetailDTO conflict = new ConflictDetailDTO(
                ConflictDetailDTO.ConflictType.CLASSROOM,
                2, "化学", "李老师", "B202", "周二 14:00-16:00", "待排项冲突");

        CourseScheduleConflictPreCheckResponse response =
                new CourseScheduleConflictPreCheckResponse(false, 1,
                        Collections.singletonList(conflict));

        byte[] csvBytes = PreCheckCsvSupport.buildSinglePreCheckCsv("物理", response);
        String csv = new String(csvBytes, StandardCharsets.UTF_8);
        String contentWithoutBom = csv.substring(1);
        String[] lines = contentWithoutBom.split("\n");

        assertEquals(2, lines.length);
        String[] cells = lines[1].split(",", -1);
        assertEquals("教室冲突", cells[4]);
        assertEquals("待排项", cells[5]);
        assertEquals("化学(序号: 2)", cells[6]);
    }

    @Test
    void testBuildSinglePreCheckCsv_BothTeacherAndClassroom_ExpandsToTwoRows() {
        ConflictDetailDTO teacherConflict = new ConflictDetailDTO(
                ConflictDetailDTO.ConflictType.TEACHER,
                1L, "数学", "张老师", "A101", "周一 08:00-10:00", "老师冲突");
        ConflictDetailDTO classroomConflict = new ConflictDetailDTO(
                ConflictDetailDTO.ConflictType.CLASSROOM,
                2L, "英语", "王老师", "B202", "周一 08:00-10:00", "教室冲突");

        CourseScheduleConflictPreCheckResponse response =
                new CourseScheduleConflictPreCheckResponse(false, 2,
                        Arrays.asList(teacherConflict, classroomConflict));

        byte[] csvBytes = PreCheckCsvSupport.buildSinglePreCheckCsv("物理", response);
        String csv = new String(csvBytes, StandardCharsets.UTF_8);
        String contentWithoutBom = csv.substring(1);
        String[] lines = contentWithoutBom.split("\n");

        assertEquals(3, lines.length);

        String[] row1 = lines[1].split(",", -1);
        assertEquals("物理", row1[2]);
        assertEquals("否", row1[3]);
        assertEquals("老师冲突", row1[4]);
        assertEquals("数学(ID: 1)", row1[6]);

        String[] row2 = lines[2].split(",", -1);
        assertEquals("物理", row2[2]);
        assertEquals("否", row2[3]);
        assertEquals("教室冲突", row2[4]);
        assertEquals("英语(ID: 2)", row2[6]);
    }

    @Test
    void testBuildSinglePreCheckCsv_SpecialCharactersEscaped() {
        ConflictDetailDTO conflict = new ConflictDetailDTO(
                ConflictDetailDTO.ConflictType.TEACHER,
                1L, "数学,高级", "张\"老师", "A101\n新楼", "周一 08:00-10:00", "原因\"包含引号");

        CourseScheduleConflictPreCheckResponse response =
                new CourseScheduleConflictPreCheckResponse(false, 1,
                        Collections.singletonList(conflict));

        byte[] csvBytes = PreCheckCsvSupport.buildSinglePreCheckCsv("物理,实验", response);
        String csv = new String(csvBytes, StandardCharsets.UTF_8);

        assertTrue(csv.contains("\"物理,实验\""), "课程名的逗号应被转义");
        assertTrue(csv.contains("数学,高级(ID: 1)"), "冲突目标列含逗号应被转义");
        assertTrue(csv.contains("\"张\"\"老师\""), "老师名的引号应被转义");
        assertTrue(csv.contains("\"A101\n新楼\""), "教室的换行应被转义");
        assertTrue(csv.contains("\"原因\"\"包含引号\""), "原因的引号应被转义");
    }

    @Test
    void testBuildBatchPreCheckCsv_MultiItemMixedConflicts_ExpandsCorrectly() {
        List<BatchPreCheckItemResponse> items = new ArrayList<>();

        items.add(new BatchPreCheckItemResponse(
                0, "数学", true, 0, new ArrayList<>()));

        ConflictDetailDTO teacherConflict = new ConflictDetailDTO(
                ConflictDetailDTO.ConflictType.TEACHER,
                1L, "已有课", "张老师", "A101", "周一 08:00-10:00", "老师冲突");
        ConflictDetailDTO classroomConflict = new ConflictDetailDTO(
                ConflictDetailDTO.ConflictType.CLASSROOM,
                2L, "另一课", "李老师", "B202", "周一 08:00-10:00", "教室冲突");
        items.add(new BatchPreCheckItemResponse(
                1, "物理", false, 2,
                Arrays.asList(teacherConflict, classroomConflict)));

        ConflictDetailDTO pendingConflict = new ConflictDetailDTO(
                ConflictDetailDTO.ConflictType.TEACHER,
                3, "化学", "王老师", "C303", "周二 08:00-10:00", "待排项");
        items.add(new BatchPreCheckItemResponse(
                2, "生物", false, 1,
                Collections.singletonList(pendingConflict)));

        BatchPreCheckResponse response = new BatchPreCheckResponse(false, 3, items);

        byte[] csvBytes = PreCheckCsvSupport.buildBatchPreCheckCsv("按老师批量预检", response);
        String csv = new String(csvBytes, StandardCharsets.UTF_8);
        String contentWithoutBom = csv.substring(1);
        String[] lines = contentWithoutBom.split("\n");

        assertEquals(5, lines.length, "1个表头 + 1个无冲突 + 2个多冲突 + 1个单冲突 = 5行");

        String[] row0NoConflict = lines[1].split(",", -1);
        assertEquals("0", row0NoConflict[1]);
        assertEquals("数学", row0NoConflict[2]);
        assertEquals("是", row0NoConflict[3]);
        assertEquals("", row0NoConflict[4]);
        assertEquals("", row0NoConflict[5]);

        String[] row1Conflict1 = lines[2].split(",", -1);
        assertEquals("1", row1Conflict1[1]);
        assertEquals("物理", row1Conflict1[2]);
        assertEquals("否", row1Conflict1[3]);
        assertEquals("老师冲突", row1Conflict1[4]);
        assertEquals("已有课程", row1Conflict1[5]);
        assertEquals("已有课(ID: 1)", row1Conflict1[6]);

        String[] row1Conflict2 = lines[3].split(",", -1);
        assertEquals("1", row1Conflict2[1]);
        assertEquals("物理", row1Conflict2[2]);
        assertEquals("否", row1Conflict2[3]);
        assertEquals("教室冲突", row1Conflict2[4]);
        assertEquals("已有课程", row1Conflict2[5]);
        assertEquals("另一课(ID: 2)", row1Conflict2[6]);

        String[] row2 = lines[4].split(",", -1);
        assertEquals("2", row2[1]);
        assertEquals("生物", row2[2]);
        assertEquals("否", row2[3]);
        assertEquals("老师冲突", row2[4]);
        assertEquals("待排项", row2[5]);
        assertEquals("化学(序号: 3)", row2[6]);
    }

    @Test
    void testBuildBatchPreCheckCsv_NoItems_OnlyHeader() {
        BatchPreCheckResponse response = new BatchPreCheckResponse(true, 0, new ArrayList<>());

        byte[] csvBytes = PreCheckCsvSupport.buildBatchPreCheckCsv("按教室批量预检", response);
        String csv = new String(csvBytes, StandardCharsets.UTF_8);
        String contentWithoutBom = csv.substring(1);
        String[] lines = contentWithoutBom.split("\n");

        assertEquals(1, lines.length);
        assertEquals(EXPECTED_HEADER, lines[0]);
    }

    @Test
    void testBuildBatchPreCheckCsv_NullItems_OnlyHeader() {
        BatchPreCheckResponse response = new BatchPreCheckResponse(true, 0, null);

        byte[] csvBytes = PreCheckCsvSupport.buildBatchPreCheckCsv("按教室批量预检", response);
        String csv = new String(csvBytes, StandardCharsets.UTF_8);
        String contentWithoutBom = csv.substring(1);
        String[] lines = contentWithoutBom.split("\n");

        assertEquals(1, lines.length);
    }

    @Test
    void testBuildBatchPreCheckCsv_SpecialCharactersEscaped() {
        ConflictDetailDTO conflict = new ConflictDetailDTO(
                ConflictDetailDTO.ConflictType.CLASSROOM,
                1L, "已有,课程", "张\"老师", "A\n101", "周一 08:00-10:00", "原因,有逗号");

        BatchPreCheckItemResponse item = new BatchPreCheckItemResponse(
                0, "数学,高级", false, 1,
                Collections.singletonList(conflict));

        BatchPreCheckResponse response = new BatchPreCheckResponse(false, 1,
                Collections.singletonList(item));

        byte[] csvBytes = PreCheckCsvSupport.buildBatchPreCheckCsv("按教室批量预检", response);
        String csv = new String(csvBytes, StandardCharsets.UTF_8);

        assertTrue(csv.contains("\"数学,高级\""));
        assertTrue(csv.contains("已有,课程(ID: 1)"));
        assertTrue(csv.contains("\"张\"\"老师\""));
        assertTrue(csv.contains("\"A\n101\""));
        assertTrue(csv.contains("\"原因,有逗号\""));
    }

    @Test
    void testBuildBatchPreCheckCsv_ClassroomBatchPreCheckType() {
        BatchPreCheckItemResponse item = new BatchPreCheckItemResponse(
                5, "测试课", true, 0, new ArrayList<>());

        BatchPreCheckResponse response = new BatchPreCheckResponse(true, 0,
                Collections.singletonList(item));

        byte[] csvBytes = PreCheckCsvSupport.buildBatchPreCheckCsv("按教室批量预检", response);
        String csv = new String(csvBytes, StandardCharsets.UTF_8);
        String contentWithoutBom = csv.substring(1);
        String[] lines = contentWithoutBom.split("\n");

        String[] cells = lines[1].split(",", -1);
        assertEquals("按教室批量预检", cells[0]);
        assertEquals("5", cells[1]);
    }

    @Test
    void testGetConflictTypeText_Teacher() {
        assertEquals("老师冲突",
                PreCheckCsvSupport.getConflictTypeText(ConflictDetailDTO.ConflictType.TEACHER));
    }

    @Test
    void testGetConflictTypeText_Classroom() {
        assertEquals("教室冲突",
                PreCheckCsvSupport.getConflictTypeText(ConflictDetailDTO.ConflictType.CLASSROOM));
    }

    @Test
    void testGetConflictTypeText_Null() {
        assertEquals("", PreCheckCsvSupport.getConflictTypeText(null));
    }

    @Test
    void testGetSourceTypeText_ExistingCourse() {
        assertEquals("已有课程",
                PreCheckCsvSupport.getSourceTypeText(ConflictDetailDTO.SourceType.EXISTING_COURSE));
    }

    @Test
    void testGetSourceTypeText_PendingItem() {
        assertEquals("待排项",
                PreCheckCsvSupport.getSourceTypeText(ConflictDetailDTO.SourceType.PENDING_ITEM));
    }

    @Test
    void testGetSourceTypeText_Null() {
        assertEquals("", PreCheckCsvSupport.getSourceTypeText(null));
    }

    @Test
    void testBuildConflictTargetText_ExistingCourseWithId() {
        ConflictDetailDTO conflict = new ConflictDetailDTO(
                ConflictDetailDTO.ConflictType.TEACHER,
                42L, "数学", "张老师", "A101", "周一 08:00-10:00", "原因");

        assertEquals("数学(ID: 42)", PreCheckCsvSupport.buildConflictTargetText(conflict));
    }

    @Test
    void testBuildConflictTargetText_ExistingCourseWithoutId_FallsBackToName() {
        ConflictDetailDTO conflict = new ConflictDetailDTO();
        conflict.setConflictType(ConflictDetailDTO.ConflictType.TEACHER);
        conflict.setSourceType(ConflictDetailDTO.SourceType.EXISTING_COURSE);
        conflict.setCourseId(null);
        conflict.setCourseName("数学");

        assertEquals("数学", PreCheckCsvSupport.buildConflictTargetText(conflict));
    }

    @Test
    void testBuildConflictTargetText_PendingItemWithIndex() {
        ConflictDetailDTO conflict = new ConflictDetailDTO(
                ConflictDetailDTO.ConflictType.CLASSROOM,
                5, "化学", "李老师", "B202", "周二 14:00-16:00", "原因");

        assertEquals("化学(序号: 5)", PreCheckCsvSupport.buildConflictTargetText(conflict));
    }

    @Test
    void testBuildConflictTargetText_PendingItemWithoutIndex_FallsBackToName() {
        ConflictDetailDTO conflict = new ConflictDetailDTO();
        conflict.setConflictType(ConflictDetailDTO.ConflictType.TEACHER);
        conflict.setSourceType(ConflictDetailDTO.SourceType.PENDING_ITEM);
        conflict.setPendingIndex(null);
        conflict.setCourseName("化学");

        assertEquals("化学", PreCheckCsvSupport.buildConflictTargetText(conflict));
    }

    @Test
    void testBuildConflictTargetText_NullConflict() {
        assertEquals("", PreCheckCsvSupport.buildConflictTargetText(null));
    }

    @Test
    void testBuildConflictTargetText_NullCourseName() {
        ConflictDetailDTO conflict = new ConflictDetailDTO();
        conflict.setSourceType(ConflictDetailDTO.SourceType.EXISTING_COURSE);
        conflict.setCourseId(1L);
        conflict.setCourseName(null);

        assertEquals("(ID: 1)", PreCheckCsvSupport.buildConflictTargetText(conflict));
    }

    @Test
    void testBuildConflictTargetText_NullSourceType_FallsBackToName() {
        ConflictDetailDTO conflict = new ConflictDetailDTO();
        conflict.setSourceType(null);
        conflict.setCourseName("物理");

        assertEquals("物理", PreCheckCsvSupport.buildConflictTargetText(conflict));
    }

    @Test
    void testBuildSinglePreCheckCsv_NullResponse_OnlyHeader() {
        byte[] csvBytes = PreCheckCsvSupport.buildSinglePreCheckCsv("物理", null);
        String csv = new String(csvBytes, StandardCharsets.UTF_8);
        String contentWithoutBom = csv.substring(1);
        String[] lines = contentWithoutBom.split("\n");

        assertEquals(1, lines.length);
        assertEquals(EXPECTED_HEADER, lines[0]);
    }

    @Test
    void testBuildSinglePreCheckCsv_ConflictTargetColumn_ExistingCourseWithId() {
        ConflictDetailDTO conflict = new ConflictDetailDTO(
                ConflictDetailDTO.ConflictType.TEACHER,
                99L, "高数", "张老师", "A101", "周一 08:00-10:00", "老师冲突");

        CourseScheduleConflictPreCheckResponse response =
                new CourseScheduleConflictPreCheckResponse(false, 1,
                        Collections.singletonList(conflict));

        byte[] csvBytes = PreCheckCsvSupport.buildSinglePreCheckCsv("线代", response);
        String csv = new String(csvBytes, StandardCharsets.UTF_8);
        String contentWithoutBom = csv.substring(1);
        String[] lines = contentWithoutBom.split("\n");

        String[] cells = lines[1].split(",", -1);
        assertEquals("高数(ID: 99)", cells[6]);
    }

    @Test
    void testBuildSinglePreCheckCsv_ConflictTargetColumn_PendingItemWithIndex() {
        ConflictDetailDTO conflict = new ConflictDetailDTO(
                ConflictDetailDTO.ConflictType.CLASSROOM,
                7, "有机化学", "李老师", "B202", "周三 10:00-12:00", "待排冲突");

        CourseScheduleConflictPreCheckResponse response =
                new CourseScheduleConflictPreCheckResponse(false, 1,
                        Collections.singletonList(conflict));

        byte[] csvBytes = PreCheckCsvSupport.buildSinglePreCheckCsv("无机化学", response);
        String csv = new String(csvBytes, StandardCharsets.UTF_8);
        String contentWithoutBom = csv.substring(1);
        String[] lines = contentWithoutBom.split("\n");

        String[] cells = lines[1].split(",", -1);
        assertEquals("有机化学(序号: 7)", cells[6]);
    }
}
