package com.coursescheduler.service;

import com.coursescheduler.dto.ConflictRiskPreviewResponse;
import com.coursescheduler.model.CourseSchedule;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConflictRiskPreviewSupportTest {

    private ScheduleConflictSupport.ConflictMatch teacherMatch(
            Long id, String courseName, String teacherName, String classroom, String timeSlot) {
        CourseSchedule schedule = new CourseSchedule(id, courseName, teacherName, classroom, timeSlot);
        return new ScheduleConflictSupport.ConflictMatch(schedule, ScheduleConflictSupport.ConflictType.TEACHER);
    }

    private ScheduleConflictSupport.ConflictMatch classroomMatch(
            Long id, String courseName, String teacherName, String classroom, String timeSlot) {
        CourseSchedule schedule = new CourseSchedule(id, courseName, teacherName, classroom, timeSlot);
        return new ScheduleConflictSupport.ConflictMatch(schedule, ScheduleConflictSupport.ConflictType.CLASSROOM);
    }

    @Test
    void buildResponse_NullMatches_ReturnsLOW() {
        ConflictRiskPreviewResponse response = ConflictRiskPreviewSupport.buildResponse(null);

        assertEquals(0, response.getTeacherConflictCount());
        assertEquals(0, response.getClassroomConflictCount());
        assertTrue(response.getConflictCourseNames().isEmpty());
        assertEquals(ConflictRiskPreviewResponse.RiskLevel.LOW, response.getRiskLevel());
    }

    @Test
    void buildResponse_EmptyMatches_ReturnsLOW() {
        ConflictRiskPreviewResponse response = ConflictRiskPreviewSupport.buildResponse(
                Collections.<ScheduleConflictSupport.ConflictMatch>emptyList());

        assertEquals(0, response.getTeacherConflictCount());
        assertEquals(0, response.getClassroomConflictCount());
        assertTrue(response.getConflictCourseNames().isEmpty());
        assertEquals(ConflictRiskPreviewResponse.RiskLevel.LOW, response.getRiskLevel());
    }

    @Test
    void buildResponse_SingleTeacherConflict_ReturnsMEDIUM() {
        List<ScheduleConflictSupport.ConflictMatch> matches = Collections.singletonList(
                teacherMatch(1L, "高等数学", "张教授", "A101", "周一 08:00-10:00"));

        ConflictRiskPreviewResponse response = ConflictRiskPreviewSupport.buildResponse(matches);

        assertEquals(1, response.getTeacherConflictCount());
        assertEquals(0, response.getClassroomConflictCount());
        assertEquals(1, response.getConflictCourseNames().size());
        assertEquals("高等数学", response.getConflictCourseNames().get(0));
        assertEquals(ConflictRiskPreviewResponse.RiskLevel.MEDIUM, response.getRiskLevel());
    }

    @Test
    void buildResponse_SingleClassroomConflict_ReturnsMEDIUM() {
        List<ScheduleConflictSupport.ConflictMatch> matches = Collections.singletonList(
                classroomMatch(1L, "线性代数", "李老师", "B202", "周二 10:00-12:00"));

        ConflictRiskPreviewResponse response = ConflictRiskPreviewSupport.buildResponse(matches);

        assertEquals(0, response.getTeacherConflictCount());
        assertEquals(1, response.getClassroomConflictCount());
        assertEquals(1, response.getConflictCourseNames().size());
        assertEquals("线性代数", response.getConflictCourseNames().get(0));
        assertEquals(ConflictRiskPreviewResponse.RiskLevel.MEDIUM, response.getRiskLevel());
    }

    @Test
    void buildResponse_TeacherAndClassroomDifferentCourses_ReturnsHIGH() {
        List<ScheduleConflictSupport.ConflictMatch> matches = Arrays.asList(
                teacherMatch(1L, "高等数学", "张教授", "B202", "周一 08:00-10:00"),
                classroomMatch(2L, "大学物理", "王老师", "A101", "周一 08:00-10:00"));

        ConflictRiskPreviewResponse response = ConflictRiskPreviewSupport.buildResponse(matches);

        assertEquals(1, response.getTeacherConflictCount());
        assertEquals(1, response.getClassroomConflictCount());
        assertEquals(2, response.getConflictCourseNames().size());
        assertTrue(response.getConflictCourseNames().contains("高等数学"));
        assertTrue(response.getConflictCourseNames().contains("大学物理"));
        assertEquals(ConflictRiskPreviewResponse.RiskLevel.HIGH, response.getRiskLevel());
    }

    @Test
    void buildResponse_SameCourseBothTeacherAndClassroomConflict_DeduplicatesName() {
        CourseSchedule schedule = new CourseSchedule(1L, "高等数学", "张教授", "A101", "周一 08:00-10:00");
        List<ScheduleConflictSupport.ConflictMatch> matches = Arrays.asList(
                new ScheduleConflictSupport.ConflictMatch(schedule, ScheduleConflictSupport.ConflictType.TEACHER),
                new ScheduleConflictSupport.ConflictMatch(schedule, ScheduleConflictSupport.ConflictType.CLASSROOM));

        ConflictRiskPreviewResponse response = ConflictRiskPreviewSupport.buildResponse(matches);

        assertEquals(1, response.getTeacherConflictCount());
        assertEquals(1, response.getClassroomConflictCount());
        assertEquals(1, response.getConflictCourseNames().size());
        assertEquals("高等数学", response.getConflictCourseNames().get(0));
        assertEquals(ConflictRiskPreviewResponse.RiskLevel.HIGH, response.getRiskLevel());
    }

    @Test
    void buildResponse_MultipleTeacherConflicts_PreservesInsertionOrder() {
        List<ScheduleConflictSupport.ConflictMatch> matches = Arrays.asList(
                teacherMatch(1L, "高等数学", "张教授", "A101", "周一 08:00-10:00"),
                teacherMatch(2L, "数值分析", "张教授", "B202", "周一 09:00-11:00"),
                teacherMatch(3L, "泛函分析", "张教授", "C303", "周一 08:00-10:00"));

        ConflictRiskPreviewResponse response = ConflictRiskPreviewSupport.buildResponse(matches);

        assertEquals(3, response.getTeacherConflictCount());
        assertEquals(0, response.getClassroomConflictCount());
        assertEquals(Arrays.asList("高等数学", "数值分析", "泛函分析"), response.getConflictCourseNames());
        assertEquals(ConflictRiskPreviewResponse.RiskLevel.MEDIUM, response.getRiskLevel());
    }

    @Test
    void buildResponse_MultipleBothConflicts_RealisticScenario() {
        List<ScheduleConflictSupport.ConflictMatch> matches = Arrays.asList(
                teacherMatch(1L, "高等数学", "张教授", "C301", "周一 08:00-10:00"),
                classroomMatch(2L, "概率论", "李老师", "A101", "周一 08:00-10:00"),
                classroomMatch(3L, "离散数学", "王老师", "A101", "周一 09:00-11:00"),
                teacherMatch(4L, "拓扑学", "张教授", "D401", "周一 10:00-12:00"));

        ConflictRiskPreviewResponse response = ConflictRiskPreviewSupport.buildResponse(matches);

        assertEquals(2, response.getTeacherConflictCount());
        assertEquals(2, response.getClassroomConflictCount());
        assertEquals(4, response.getConflictCourseNames().size());
        assertEquals(Arrays.asList("高等数学", "概率论", "离散数学", "拓扑学"), response.getConflictCourseNames());
        assertEquals(ConflictRiskPreviewResponse.RiskLevel.HIGH, response.getRiskLevel());
    }

    @Test
    void buildResponse_DuplicateCourseNamesAcrossConflictTypes_Deduplicated() {
        CourseSchedule scheduleA = new CourseSchedule(1L, "公共课A", "张教授", "A101", "周一 08:00-10:00");
        CourseSchedule scheduleB = new CourseSchedule(2L, "公共课B", "张教授", "B202", "周一 08:00-10:00");
        List<ScheduleConflictSupport.ConflictMatch> matches = Arrays.asList(
                new ScheduleConflictSupport.ConflictMatch(scheduleA, ScheduleConflictSupport.ConflictType.TEACHER),
                new ScheduleConflictSupport.ConflictMatch(scheduleA, ScheduleConflictSupport.ConflictType.CLASSROOM),
                new ScheduleConflictSupport.ConflictMatch(scheduleB, ScheduleConflictSupport.ConflictType.TEACHER),
                new ScheduleConflictSupport.ConflictMatch(scheduleB, ScheduleConflictSupport.ConflictType.CLASSROOM));

        ConflictRiskPreviewResponse response = ConflictRiskPreviewSupport.buildResponse(matches);

        assertEquals(2, response.getTeacherConflictCount());
        assertEquals(2, response.getClassroomConflictCount());
        assertEquals(2, response.getConflictCourseNames().size());
        assertEquals(Arrays.asList("公共课A", "公共课B"), response.getConflictCourseNames());
        assertEquals(ConflictRiskPreviewResponse.RiskLevel.HIGH, response.getRiskLevel());
    }

    @Test
    void buildResponse_NullConflictType_SkipsEntireMatch() {
        CourseSchedule scheduleWithUnknownType =
                new CourseSchedule(1L, "奇怪的课", "未知老师", "???", "周一 08:00-10:00");
        List<ScheduleConflictSupport.ConflictMatch> matches = Arrays.asList(
                teacherMatch(2L, "高等数学", "张教授", "A101", "周一 08:00-10:00"),
                new ScheduleConflictSupport.ConflictMatch(scheduleWithUnknownType, null),
                classroomMatch(3L, "线性代数", "李老师", "B202", "周一 08:00-10:00"));

        ConflictRiskPreviewResponse response = ConflictRiskPreviewSupport.buildResponse(matches);

        assertEquals(1, response.getTeacherConflictCount());
        assertEquals(1, response.getClassroomConflictCount());
        assertEquals(2, response.getConflictCourseNames().size());
        assertTrue(response.getConflictCourseNames().contains("高等数学"));
        assertTrue(response.getConflictCourseNames().contains("线性代数"));
        assertFalse(response.getConflictCourseNames().contains("奇怪的课"));
        assertEquals(ConflictRiskPreviewResponse.RiskLevel.HIGH, response.getRiskLevel());
    }

    @Test
    void buildResponse_ReturnedCourseNamesListIsUnmodifiable() {
        List<ScheduleConflictSupport.ConflictMatch> matches = Collections.singletonList(
                teacherMatch(1L, "高等数学", "张教授", "A101", "周一 08:00-10:00"));

        ConflictRiskPreviewResponse response = ConflictRiskPreviewSupport.buildResponse(matches);

        assertThrows(UnsupportedOperationException.class, () ->
                response.getConflictCourseNames().add("新课程"));
    }

    @Test
    void responseConstructor_DefensiveCopyOfCourseNamesList() {
        List<String> mutableNames = new ArrayList<>(Arrays.asList("高等数学", "线性代数"));

        ConflictRiskPreviewResponse response = new ConflictRiskPreviewResponse(
                1, 1, mutableNames, ConflictRiskPreviewResponse.RiskLevel.HIGH);
        assertEquals(Arrays.asList("高等数学", "线性代数"), response.getConflictCourseNames());

        mutableNames.clear();
        mutableNames.add("被篡改的名字");

        assertEquals(Arrays.asList("高等数学", "线性代数"), response.getConflictCourseNames());
    }

    @Test
    void responseSetter_DefensiveCopyOfCourseNamesList() {
        ConflictRiskPreviewResponse response = new ConflictRiskPreviewResponse();
        List<String> mutableNames = new ArrayList<>(Arrays.asList("课程A", "课程B"));

        response.setConflictCourseNames(mutableNames);
        assertEquals(Arrays.asList("课程A", "课程B"), response.getConflictCourseNames());

        mutableNames.clear();
        mutableNames.add("不应出现");

        assertEquals(Arrays.asList("课程A", "课程B"), response.getConflictCourseNames());
    }
}
