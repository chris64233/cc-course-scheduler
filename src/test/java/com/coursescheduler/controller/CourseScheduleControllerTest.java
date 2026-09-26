package com.coursescheduler.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.coursescheduler.dto.AuditLogResponse;
import com.coursescheduler.dto.ConflictDetailDTO;
import com.coursescheduler.dto.CourseScheduleBatchTimeSlotUpdateRequest;
import com.coursescheduler.dto.CourseScheduleConflictPreCheckRequest;
import com.coursescheduler.dto.CourseScheduleConflictPreCheckResponse;
import com.coursescheduler.dto.ConflictRiskPreviewRequest;
import com.coursescheduler.dto.ConflictRiskPreviewResponse;
import com.coursescheduler.dto.CourseScheduleCreateRequest;
import com.coursescheduler.dto.CourseScheduleUpdateRequest;
import com.coursescheduler.dto.ClassroomBatchPreCheckItemRequest;
import com.coursescheduler.dto.ClassroomBatchPreCheckRequest;
import com.coursescheduler.dto.TeacherBatchPreCheckItemRequest;
import com.coursescheduler.dto.TeacherBatchPreCheckRequest;
import com.coursescheduler.dto.PreCheckSummaryResponse;
import com.coursescheduler.dto.PendingConflictPairDTO;
import com.coursescheduler.model.OperationType;
import com.coursescheduler.service.AuditLogService;
import com.coursescheduler.service.CourseScheduleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class CourseScheduleControllerTest {

    static class TestClock extends Clock {
        private Instant instant;
        private final ZoneId zone;

        TestClock(Instant instant) {
            this.instant = instant;
            this.zone = ZoneId.systemDefault();
        }

        void advanceSeconds(long seconds) {
            this.instant = this.instant.plusSeconds(seconds);
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    @TestConfiguration
    static class TestClockConfig {
        static final TestClock TEST_CLOCK = new TestClock(Instant.parse("2026-06-13T10:00:00Z"));

        @Bean(name = "testClock")
        @Primary
        public Clock testClock() {
            return TEST_CLOCK;
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CourseScheduleService scheduleService;

    @Autowired
    private AuditLogService auditLogService;

    @BeforeEach
    void setUp() {
        scheduleService.resetForTesting();
        TestClockConfig.TEST_CLOCK.instant = Instant.parse("2026-06-13T10:00:00Z");
    }

    private CourseScheduleCreateRequest createRequest(String courseName, String teacherName, String classroom, String timeSlot) {
        CourseScheduleCreateRequest request = new CourseScheduleCreateRequest();
        request.setCourseName(courseName);
        request.setTeacherName(teacherName);
        request.setClassroom(classroom);
        request.setTimeSlot(timeSlot);
        return request;
    }

    @Test
    void testGetAllSchedules_EmptyList() throws Exception {
        mockMvc.perform(get("/api/schedules"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void testAddSchedule_Success() throws Exception {
        CourseScheduleCreateRequest request = createRequest("高等数学", "王教授", "教学楼 301", "周二 14:00-16:00");

        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.courseName").value("高等数学"))
                .andExpect(jsonPath("$.teacherName").value("王教授"))
                .andExpect(jsonPath("$.classroom").value("教学楼 301"))
                .andExpect(jsonPath("$.timeSlot").value("周二 14:00-16:00"));
    }

    @Test
    void testAddSchedule_TeacherConflict_Returns409() throws Exception {
        CourseScheduleCreateRequest request1 = createRequest("线性代数", "李老师", "A101", "周三 08:00-10:00");

        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isCreated());

        CourseScheduleCreateRequest request2 = createRequest("概率论", "李老师", "B202", "周三 08:00-10:00");

        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void testAddSchedule_EmptyCourseName_Returns400() throws Exception {
        CourseScheduleCreateRequest request = createRequest("", "王教授", "教学楼 301", "周二 14:00-16:00");

        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testAddSchedule_EmptyTeacherName_Returns400() throws Exception {
        CourseScheduleCreateRequest request = createRequest("高等数学", "", "教学楼 301", "周二 14:00-16:00");

        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testGetAllSchedules_AfterAdding() throws Exception {
        CourseScheduleCreateRequest request = createRequest("计算机基础", "赵老师", "机房 1", "周四 09:00-11:00");

        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].courseName").value("计算机基础"))
                .andExpect(jsonPath("$[0].teacherName").value("赵老师"));
    }

    @Test
    void testGetSchedules_NoCondition_ReturnsAll() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张教授", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("物理", "李副教授", "教学楼B202", "周二 08:00-10:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void testGetSchedules_ByTeacher_Keyword() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张教授", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("物理", "李副教授", "教学楼B202", "周二 08:00-10:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules")
                        .param("teacherName", "张"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].teacherName").value("张教授"));
    }

    @Test
    void testGetSchedules_ByClassroom_Keyword() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张教授", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("物理", "李副教授", "教学楼B202", "周二 08:00-10:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules")
                        .param("classroom", "教学楼"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].classroom").value("教学楼B202"));
    }

    @Test
    void testGetSchedules_MultipleConditions() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张教授", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("物理", "张教授", "教学楼B202", "周一 10:00-12:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules")
                        .param("teacherName", "张")
                        .param("classroom", "B202"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].courseName").value("物理"));
    }

    @Test
    void testGetSchedules_NoMatch_ReturnsEmpty() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张教授", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules")
                        .param("teacherName", "赵"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void testGetSchedules_TrimmedParams() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张教授", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules")
                        .param("teacherName", "  张  ")
                        .param("classroom", "  A101  "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].courseName").value("数学"));
    }

    @Test
    void testGetSchedules_ByTimeSlot_ExactMatch() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张教授", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("物理", "李副教授", "教学楼B202", "周一 10:00-12:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules")
                        .param("timeSlot", "周一 08:00-10:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].courseName").value("数学"));
    }

    @Test
    void testGetStatistics_WithData() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张教授", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("物理", "张教授", "教学楼B202", "周二 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("化学", "李副教授", "A101", "周三 08:00-10:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules/statistics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCourses").value(3))
                .andExpect(jsonPath("$.teacherCount").value(2))
                .andExpect(jsonPath("$.classroomCount").value(2));
    }

    @Test
    void testGetStatistics_EmptyData() throws Exception {
        mockMvc.perform(get("/api/schedules/statistics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCourses").value(0))
                .andExpect(jsonPath("$.teacherCount").value(0))
                .andExpect(jsonPath("$.classroomCount").value(0));
    }

    @Test
    void testDeleteSchedule_Success() throws Exception {
        CourseScheduleCreateRequest request = createRequest("高等数学", "王教授", "教学楼 301", "周二 14:00-16:00");
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1));

        mockMvc.perform(delete("/api/schedules/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.courseName").value("高等数学"))
                .andExpect(jsonPath("$.teacherName").value("王教授"))
                .andExpect(jsonPath("$.classroom").value("教学楼 301"))
                .andExpect(jsonPath("$.timeSlot").value("周二 14:00-16:00"));

        mockMvc.perform(get("/api/schedules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void testDeleteSchedule_NotFound_Returns404() throws Exception {
        mockMvc.perform(delete("/api/schedules/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.error").value("课程安排不存在，ID: 999"));
    }

    @Test
    void testDeleteSchedule_ZeroId_Returns404() throws Exception {
        mockMvc.perform(delete("/api/schedules/0"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("课程安排不存在，ID: 0"));
    }

    @Test
    void testDeleteSchedule_NegativeId_Returns404() throws Exception {
        mockMvc.perform(delete("/api/schedules/-1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("课程安排不存在，ID: -1"));
    }

    @Test
    void testDeleteSchedule_MalformedId_Returns400() throws Exception {
        mockMvc.perform(delete("/api/schedules/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void testDeleteSchedule_ListAndStatisticsUpdated() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张教授", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("物理", "李副教授", "B202", "周二 08:00-10:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/schedules/2"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/schedules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].courseName").value("数学"));

        mockMvc.perform(get("/api/schedules/statistics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCourses").value(1))
                .andExpect(jsonPath("$.teacherCount").value(1))
                .andExpect(jsonPath("$.classroomCount").value(1));
    }

    @Test
    void testDeleteSchedule_CanReAddSameTeacherSameTimeSlot() throws Exception {
        CourseScheduleCreateRequest request = createRequest("数学", "张教授", "A101", "周一 08:00-10:00");
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/schedules/1"))
                .andExpect(status().isOk());

        CourseScheduleCreateRequest reAddRequest = createRequest("物理", "张教授", "B202", "周一 08:00-10:00");
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reAddRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.teacherName").value("张教授"))
                .andExpect(jsonPath("$.timeSlot").value("周一 08:00-10:00"));
    }

    private CourseScheduleUpdateRequest createUpdateRequest(String courseName, String teacherName, String classroom, String timeSlot) {
        CourseScheduleUpdateRequest request = new CourseScheduleUpdateRequest();
        request.setCourseName(courseName);
        request.setTeacherName(teacherName);
        request.setClassroom(classroom);
        request.setTimeSlot(timeSlot);
        return request;
    }

    @Test
    void testUpdateSchedule_Success() throws Exception {
        CourseScheduleCreateRequest request = createRequest("数学", "张教授", "A101", "周一 08:00-10:00");
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1));

        CourseScheduleUpdateRequest updateRequest = createUpdateRequest("高等数学", "李教授", "B202", "周二 14:00-16:00");
        mockMvc.perform(put("/api/schedules/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.courseName").value("高等数学"))
                .andExpect(jsonPath("$.teacherName").value("李教授"))
                .andExpect(jsonPath("$.classroom").value("B202"))
                .andExpect(jsonPath("$.timeSlot").value("周二 14:00-16:00"));

        mockMvc.perform(get("/api/schedules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].courseName").value("高等数学"));
    }

    @Test
    void testUpdateSchedule_NotFound_Returns404() throws Exception {
        CourseScheduleUpdateRequest updateRequest = createUpdateRequest("高等数学", "李教授", "B202", "周二 14:00-16:00");
        mockMvc.perform(put("/api/schedules/999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.error").value("课程安排不存在，ID: 999"));
    }

    @Test
    void testUpdateSchedule_FilterWorksAfterUpdate() throws Exception {
        CourseScheduleCreateRequest request = createRequest("数学", "张教授", "A101", "周一 08:00-10:00");
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        CourseScheduleUpdateRequest updateRequest = createUpdateRequest("物理", "李教授", "B202", "周二 14:00-16:00");
        mockMvc.perform(put("/api/schedules/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/schedules").param("teacherName", "李"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].courseName").value("物理"));

        mockMvc.perform(get("/api/schedules").param("classroom", "B202"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].courseName").value("物理"));

        mockMvc.perform(get("/api/schedules").param("timeSlot", "周二 14:00-16:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].courseName").value("物理"));

        mockMvc.perform(get("/api/schedules/statistics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCourses").value(1))
                .andExpect(jsonPath("$.teacherCount").value(1))
                .andExpect(jsonPath("$.classroomCount").value(1));
    }

    @Test
    void testUpdateSchedule_TeacherConflictWithOther_Returns409() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张教授", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("物理", "李教授", "B202", "周二 14:00-16:00"))))
                .andExpect(status().isCreated());

        CourseScheduleUpdateRequest updateRequest = createUpdateRequest("化学", "张教授", "C303", "周一 08:00-10:00");
        mockMvc.perform(put("/api/schedules/2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.error").value("老师 张教授 在时间段 周一 08:00-10:00 已有课程安排"));

        mockMvc.perform(get("/api/schedules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].courseName").value("物理"));
    }

    @Test
    void testUpdateSchedule_OnlyChangeCourseName_NoSelfConflict() throws Exception {
        CourseScheduleCreateRequest request = createRequest("数学", "张教授", "A101", "周一 08:00-10:00");
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1));

        CourseScheduleUpdateRequest updateRequest = createUpdateRequest("高等数学", "张教授", "A101", "周一 08:00-10:00");
        mockMvc.perform(put("/api/schedules/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.courseName").value("高等数学"))
                .andExpect(jsonPath("$.teacherName").value("张教授"))
                .andExpect(jsonPath("$.classroom").value("A101"))
                .andExpect(jsonPath("$.timeSlot").value("周一 08:00-10:00"));

        mockMvc.perform(get("/api/schedules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void testUpdateSchedule_EmptyCourseName_Returns400WithFieldError() throws Exception {
        CourseScheduleCreateRequest request = createRequest("数学", "张教授", "A101", "周一 08:00-10:00");
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        CourseScheduleUpdateRequest updateRequest = createUpdateRequest("", "李教授", "B202", "周二 14:00-16:00");
        mockMvc.perform(put("/api/schedules/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("课程名不能为空"));
    }

    @Test
    void testUpdateSchedule_EmptyTeacherName_Returns400WithFieldError() throws Exception {
        CourseScheduleCreateRequest request = createRequest("数学", "张教授", "A101", "周一 08:00-10:00");
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        CourseScheduleUpdateRequest updateRequest = createUpdateRequest("高等数学", "", "B202", "周二 14:00-16:00");
        mockMvc.perform(put("/api/schedules/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("老师名不能为空"));
    }

    @Test
    void testUpdateSchedule_EmptyClassroom_Returns400WithFieldError() throws Exception {
        CourseScheduleCreateRequest request = createRequest("数学", "张教授", "A101", "周一 08:00-10:00");
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        CourseScheduleUpdateRequest updateRequest = createUpdateRequest("高等数学", "李教授", "", "周二 14:00-16:00");
        mockMvc.perform(put("/api/schedules/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("教室名不能为空"));
    }

    @Test
    void testUpdateSchedule_EmptyTimeSlot_Returns400WithFieldError() throws Exception {
        CourseScheduleCreateRequest request = createRequest("数学", "张教授", "A101", "周一 08:00-10:00");
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        CourseScheduleUpdateRequest updateRequest = createUpdateRequest("高等数学", "李教授", "B202", "");
        mockMvc.perform(put("/api/schedules/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("时间段不能为空"));
    }

    @Test
    void testUpdateSchedule_BlankCourseName_Returns400WithFieldError() throws Exception {
        CourseScheduleCreateRequest request = createRequest("数学", "张教授", "A101", "周一 08:00-10:00");
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        CourseScheduleUpdateRequest updateRequest = createUpdateRequest("   ", "李教授", "B202", "周二 14:00-16:00");
        mockMvc.perform(put("/api/schedules/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("课程名不能为空"));
    }

    @Test
    void testUpdateSchedule_BlankTeacherName_Returns400WithFieldError() throws Exception {
        CourseScheduleCreateRequest request = createRequest("数学", "张教授", "A101", "周一 08:00-10:00");
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        CourseScheduleUpdateRequest updateRequest = createUpdateRequest("高等数学", "   ", "B202", "周二 14:00-16:00");
        mockMvc.perform(put("/api/schedules/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("老师名不能为空"));
    }

    @Test
    void testUpdateSchedule_BlankClassroom_Returns400WithFieldError() throws Exception {
        CourseScheduleCreateRequest request = createRequest("数学", "张教授", "A101", "周一 08:00-10:00");
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        CourseScheduleUpdateRequest updateRequest = createUpdateRequest("高等数学", "李教授", "   ", "周二 14:00-16:00");
        mockMvc.perform(put("/api/schedules/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("教室名不能为空"));
    }

    @Test
    void testUpdateSchedule_BlankTimeSlot_Returns400WithFieldError() throws Exception {
        CourseScheduleCreateRequest request = createRequest("数学", "张教授", "A101", "周一 08:00-10:00");
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        CourseScheduleUpdateRequest updateRequest = createUpdateRequest("高等数学", "李教授", "B202", "   ");
        mockMvc.perform(put("/api/schedules/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("时间段不能为空"));
    }

    @Test
    void testUpdateSchedule_MissingCourseName_Returns400WithFieldError() throws Exception {
        CourseScheduleCreateRequest request = createRequest("数学", "张教授", "A101", "周一 08:00-10:00");
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        Map<String, Object> updateBody = new HashMap<>();
        updateBody.put("teacherName", "李教授");
        updateBody.put("classroom", "B202");
        updateBody.put("timeSlot", "周二 14:00-16:00");
        mockMvc.perform(put("/api/schedules/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateBody)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("课程名不能为空"));
    }

    @Test
    void testUpdateSchedule_MissingTeacherName_Returns400WithFieldError() throws Exception {
        CourseScheduleCreateRequest request = createRequest("数学", "张教授", "A101", "周一 08:00-10:00");
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        Map<String, Object> updateBody = new HashMap<>();
        updateBody.put("courseName", "高等数学");
        updateBody.put("classroom", "B202");
        updateBody.put("timeSlot", "周二 14:00-16:00");
        mockMvc.perform(put("/api/schedules/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateBody)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("老师名不能为空"));
    }

    @Test
    void testUpdateSchedule_MissingClassroom_Returns400WithFieldError() throws Exception {
        CourseScheduleCreateRequest request = createRequest("数学", "张教授", "A101", "周一 08:00-10:00");
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        Map<String, Object> updateBody = new HashMap<>();
        updateBody.put("courseName", "高等数学");
        updateBody.put("teacherName", "李教授");
        updateBody.put("timeSlot", "周二 14:00-16:00");
        mockMvc.perform(put("/api/schedules/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateBody)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("教室名不能为空"));
    }

    @Test
    void testUpdateSchedule_MissingTimeSlot_Returns400WithFieldError() throws Exception {
        CourseScheduleCreateRequest request = createRequest("数学", "张教授", "A101", "周一 08:00-10:00");
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        Map<String, Object> updateBody = new HashMap<>();
        updateBody.put("courseName", "高等数学");
        updateBody.put("teacherName", "李教授");
        updateBody.put("classroom", "B202");
        mockMvc.perform(put("/api/schedules/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateBody)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("时间段不能为空"));
    }

    @Test
    void testUpdateSchedule_AllFieldsMissing_Returns400WithAllFieldErrors() throws Exception {
        CourseScheduleCreateRequest request = createRequest("数学", "张教授", "A101", "周一 08:00-10:00");
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        Map<String, Object> updateBody = new HashMap<>();
        mockMvc.perform(put("/api/schedules/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateBody)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void testGetSchedules_DefaultOrder_ByCreation() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("物理", "李副教授", "教学楼B202", "周二 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张教授", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("化学", "王教授", "C303", "周三 09:00-11:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].courseName").value("物理"))
                .andExpect(jsonPath("$[1].id").value(2))
                .andExpect(jsonPath("$[1].courseName").value("数学"))
                .andExpect(jsonPath("$[2].id").value(3))
                .andExpect(jsonPath("$[2].courseName").value("化学"));
    }

    @Test
    void testGetSchedules_SortByCourseName_Ascending() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("物理", "李副教授", "教学楼B202", "周二 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张教授", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("化学", "王教授", "C303", "周三 09:00-11:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules")
                        .param("sortBy", "courseName")
                        .param("sortDirection", "asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].courseName").value("化学"))
                .andExpect(jsonPath("$[1].courseName").value("数学"))
                .andExpect(jsonPath("$[2].courseName").value("物理"));
    }

    @Test
    void testGetSchedules_SortByCourseName_Descending() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("物理", "李副教授", "教学楼B202", "周二 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张教授", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("化学", "王教授", "C303", "周三 09:00-11:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules")
                        .param("sortBy", "courseName")
                        .param("sortDirection", "desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].courseName").value("物理"))
                .andExpect(jsonPath("$[1].courseName").value("数学"))
                .andExpect(jsonPath("$[2].courseName").value("化学"));
    }

    @Test
    void testGetSchedules_SortByTeacherName_Ascending() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("物理", "张教授", "教学楼B202", "周二 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "李副教授", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("化学", "王教授", "C303", "周三 09:00-11:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules")
                        .param("sortBy", "teacherName")
                        .param("sortDirection", "asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].teacherName").value("张教授"))
                .andExpect(jsonPath("$[1].teacherName").value("李副教授"))
                .andExpect(jsonPath("$[2].teacherName").value("王教授"));
    }

    @Test
    void testGetSchedules_SortByTeacherName_Descending() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("物理", "张教授", "教学楼B202", "周二 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "李副教授", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("化学", "王教授", "C303", "周三 09:00-11:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules")
                        .param("sortBy", "teacherName")
                        .param("sortDirection", "desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].teacherName").value("王教授"))
                .andExpect(jsonPath("$[1].teacherName").value("李副教授"))
                .andExpect(jsonPath("$[2].teacherName").value("张教授"));
    }

    @Test
    void testGetSchedules_SortByClassroom_Ascending() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("物理", "张教授", "B202", "周二 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "李副教授", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("化学", "王教授", "C303", "周三 09:00-11:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules")
                        .param("sortBy", "classroom")
                        .param("sortDirection", "asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].classroom").value("A101"))
                .andExpect(jsonPath("$[1].classroom").value("B202"))
                .andExpect(jsonPath("$[2].classroom").value("C303"));
    }

    @Test
    void testGetSchedules_SortByClassroom_Descending() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("物理", "张教授", "B202", "周二 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "李副教授", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("化学", "王教授", "C303", "周三 09:00-11:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules")
                        .param("sortBy", "classroom")
                        .param("sortDirection", "desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].classroom").value("C303"))
                .andExpect(jsonPath("$[1].classroom").value("B202"))
                .andExpect(jsonPath("$[2].classroom").value("A101"));
    }

    @Test
    void testGetSchedules_SortByTimeSlot_Ascending() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("物理", "张教授", "B202", "周二 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "李副教授", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("化学", "王教授", "C303", "周三 09:00-11:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules")
                        .param("sortBy", "timeSlot")
                        .param("sortDirection", "asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].timeSlot").value("周一 08:00-10:00"))
                .andExpect(jsonPath("$[1].timeSlot").value("周三 09:00-11:00"))
                .andExpect(jsonPath("$[2].timeSlot").value("周二 08:00-10:00"));
    }

    @Test
    void testGetSchedules_SortByTimeSlot_Descending() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("物理", "张教授", "B202", "周二 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "李副教授", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("化学", "王教授", "C303", "周三 09:00-11:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules")
                        .param("sortBy", "timeSlot")
                        .param("sortDirection", "desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].timeSlot").value("周二 08:00-10:00"))
                .andExpect(jsonPath("$[1].timeSlot").value("周三 09:00-11:00"))
                .andExpect(jsonPath("$[2].timeSlot").value("周一 08:00-10:00"));
    }

    @Test
    void testGetSchedules_FilterThenSort_ByTeacherThenCourseName() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("物理", "张教授", "B202", "周二 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张教授", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("化学", "李副教授", "C303", "周三 09:00-11:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("生物", "张教授", "D404", "周四 10:00-12:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules")
                        .param("teacherName", "张")
                        .param("sortBy", "courseName")
                        .param("sortDirection", "asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].courseName").value("数学"))
                .andExpect(jsonPath("$[0].teacherName").value("张教授"))
                .andExpect(jsonPath("$[1].courseName").value("物理"))
                .andExpect(jsonPath("$[1].teacherName").value("张教授"))
                .andExpect(jsonPath("$[2].courseName").value("生物"))
                .andExpect(jsonPath("$[2].teacherName").value("张教授"));
    }

    @Test
    void testGetSchedules_FilterThenSort_ByClassroomThenTimeSlotDesc() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("物理", "张教授", "教学楼B202", "周二 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "李副教授", "教学楼B202", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("化学", "王教授", "C303", "周三 09:00-11:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("生物", "赵老师", "教学楼B202", "周四 10:00-12:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules")
                        .param("classroom", "教学楼")
                        .param("sortBy", "timeSlot")
                        .param("sortDirection", "desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].timeSlot").value("周四 10:00-12:00"))
                .andExpect(jsonPath("$[0].classroom").value("教学楼B202"))
                .andExpect(jsonPath("$[1].timeSlot").value("周二 08:00-10:00"))
                .andExpect(jsonPath("$[1].classroom").value("教学楼B202"))
                .andExpect(jsonPath("$[2].timeSlot").value("周一 08:00-10:00"))
                .andExpect(jsonPath("$[2].classroom").value("教学楼B202"));
    }

    @Test
    void testGetSchedules_InvalidSortField_Returns400() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张教授", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules")
                        .param("sortBy", "invalidField"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.error").value("不支持的排序字段: invalidField。支持的字段有: [courseName, teacherName, classroom, timeSlot]"));
    }

    @Test
    void testGetSchedules_InvalidSortDirection_Returns400() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张教授", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules")
                        .param("sortBy", "courseName")
                        .param("sortDirection", "invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.error").value("不支持的排序方向: invalid。支持的方向有: [asc, desc]"));
    }

    @Test
    void testGetSchedules_SortDirectionCaseInsensitive() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("物理", "李副教授", "教学楼B202", "周二 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张教授", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("化学", "王教授", "C303", "周三 09:00-11:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules")
                        .param("sortBy", "courseName")
                        .param("sortDirection", "DESC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].courseName").value("物理"))
                .andExpect(jsonPath("$[1].courseName").value("数学"))
                .andExpect(jsonPath("$[2].courseName").value("化学"));

        mockMvc.perform(get("/api/schedules")
                        .param("sortBy", "courseName")
                        .param("sortDirection", "Desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].courseName").value("物理"));
    }

    @Test
    void testGetSchedules_OnlySortDirection_NoSortBy_UsesDefaultOrder() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("物理", "李副教授", "教学楼B202", "周二 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张教授", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules")
                        .param("sortDirection", "desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].courseName").value("物理"))
                .andExpect(jsonPath("$[1].id").value(2))
                .andExpect(jsonPath("$[1].courseName").value("数学"));
    }

    @Test
    void testAddSchedule_NonStandardTimeSlot_FormatNormalized() throws Exception {
        CourseScheduleCreateRequest request = createRequest("数学", "王教授", "A101", "星期一 8:00-10:00");

        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.timeSlot").value("周一 08:00-10:00"));
    }

    @Test
    void testAddSchedule_NumericWeekday_FormatNormalized() throws Exception {
        CourseScheduleCreateRequest request = createRequest("物理", "李老师", "B202", "周1 9:00-11:30");

        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.timeSlot").value("周一 09:00-11:30"));
    }

    @Test
    void testAddSchedule_ExtraSpacesInTimeSlot_FormatNormalized() throws Exception {
        CourseScheduleCreateRequest request = createRequest("化学", "张教授", "C303", "  周二   14:00 - 16:00  ");

        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.timeSlot").value("周二 14:00-16:00"));
    }

    @Test
    void testAddSchedule_InvalidTimeSlotFormat_Returns400WithClearMessage() throws Exception {
        CourseScheduleCreateRequest request = createRequest("数学", "王教授", "A101", "明天上午");

        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("格式不合法")))
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("周X HH:mm-HH:mm")));
    }

    @Test
    void testAddSchedule_StartAfterEndTime_Returns400WithClearMessage() throws Exception {
        CourseScheduleCreateRequest request = createRequest("数学", "王教授", "A101", "周一 10:00-08:00");

        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("必须早于")));
    }

    @Test
    void testAddSchedule_InvalidHour_Returns400WithClearMessage() throws Exception {
        CourseScheduleCreateRequest request = createRequest("数学", "王教授", "A101", "周一 25:00-26:00");

        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("不合法")));
    }

    @Test
    void testUpdateSchedule_NonStandardTimeSlot_FormatNormalized() throws Exception {
        CourseScheduleCreateRequest request = createRequest("数学", "王教授", "A101", "周一 08:00-10:00");
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        CourseScheduleUpdateRequest updateRequest = createUpdateRequest("高等数学", "王教授", "A101", "星期1 9:00-11:00");
        mockMvc.perform(put("/api/schedules/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timeSlot").value("周一 09:00-11:00"));
    }

    @Test
    void testUpdateSchedule_InvalidTimeSlotFormat_Returns400WithClearMessage() throws Exception {
        CourseScheduleCreateRequest request = createRequest("数学", "王教授", "A101", "周一 08:00-10:00");
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        CourseScheduleUpdateRequest updateRequest = createUpdateRequest("高等数学", "王教授", "A101", "随便写个时间");
        mockMvc.perform(put("/api/schedules/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("格式不合法")));
    }

    @Test
    void testGetSchedules_NonStandardTimeSlot_FindsCorrectCourses() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张教授", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("物理", "李教授", "B202", "周二 14:00-16:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules")
                        .param("timeSlot", "星期一 8:00-10:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].courseName").value("数学"));
    }

    @Test
    void testGetSchedules_NumericWeekdayTimeSlot_FindsCorrectCourses() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张教授", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules")
                        .param("timeSlot", "周1 08:00-10:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].courseName").value("数学"));
    }

    @Test
    void testGetSchedules_InvalidTimeSlotFormat_Returns400WithClearMessage() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张教授", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules")
                        .param("timeSlot", "不是合法时间"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("格式不合法")));
    }

    @Test
    void testAddSchedule_DifferentFormatsSameTimeSlot_ClassroomConflictDetected() throws Exception {
        CourseScheduleCreateRequest request1 = createRequest("数学", "张教授", "A101", "周一 08:00-10:00");
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isCreated());

        CourseScheduleCreateRequest request2 = createRequest("物理", "李教授", "A101", "星期一 8:00-10:00");
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("A101")))
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("周一 08:00-10:00")));
    }

    private void addCourseNameControllerSampleData() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("高等数学", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("线性代数", "李老师", "B202", "周一 10:00-12:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学分析", "张老师", "A102", "周二 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("大学物理", "王教授", "C303", "周二 14:00-16:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("物理实验", "王教授", "实验室1", "周三 09:00-11:00"))))
                .andExpect(status().isCreated());
    }

    @Test
    void testGetSchedules_ByCourseName_Only() throws Exception {
        addCourseNameControllerSampleData();

        mockMvc.perform(get("/api/schedules")
                        .param("courseName", "数学"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].courseName").value(org.hamcrest.Matchers.containsString("数学")))
                .andExpect(jsonPath("$[1].courseName").value(org.hamcrest.Matchers.containsString("数学")));
    }

    @Test
    void testGetSchedules_ByCourseNameAndTeacher() throws Exception {
        addCourseNameControllerSampleData();

        mockMvc.perform(get("/api/schedules")
                        .param("courseName", "数学")
                        .param("teacherName", "张"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].courseName").value(org.hamcrest.Matchers.containsString("数学")))
                .andExpect(jsonPath("$[0].teacherName").value(org.hamcrest.Matchers.containsString("张")))
                .andExpect(jsonPath("$[1].courseName").value(org.hamcrest.Matchers.containsString("数学")))
                .andExpect(jsonPath("$[1].teacherName").value(org.hamcrest.Matchers.containsString("张")));
    }

    @Test
    void testGetSchedules_ByCourseName_WithSort() throws Exception {
        addCourseNameControllerSampleData();

        mockMvc.perform(get("/api/schedules")
                        .param("courseName", "物")
                        .param("sortBy", "courseName")
                        .param("sortDirection", "asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].courseName").value("大学物理"))
                .andExpect(jsonPath("$[1].courseName").value("物理实验"));
    }

    @Test
    void testGetSchedules_BlankCourseName_NoEffect() throws Exception {
        addCourseNameControllerSampleData();

        mockMvc.perform(get("/api/schedules")
                        .param("courseName", "   "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5));
    }

    @Test
    void testGetSchedules_EmptyCourseName_NoEffect() throws Exception {
        addCourseNameControllerSampleData();

        mockMvc.perform(get("/api/schedules")
                        .param("courseName", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5));
    }

    @Test
    void testGetSchedules_ByCourseName_NoMatch_ReturnsEmpty() throws Exception {
        addCourseNameControllerSampleData();

        mockMvc.perform(get("/api/schedules")
                        .param("courseName", "化学"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void testGetSchedules_ByCourseName_TrimmedKeyword() throws Exception {
        addCourseNameControllerSampleData();

        mockMvc.perform(get("/api/schedules")
                        .param("courseName", "  代数  "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].courseName").value("线性代数"));
    }

    @Test
    void testGetSchedules_ByCourseNameAndClassroom() throws Exception {
        addCourseNameControllerSampleData();

        mockMvc.perform(get("/api/schedules")
                        .param("courseName", "数学")
                        .param("classroom", "A101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].courseName").value("高等数学"))
                .andExpect(jsonPath("$[0].classroom").value("A101"));
    }

    @Test
    void testGetSchedules_ByCourseNameAndTimeSlot() throws Exception {
        addCourseNameControllerSampleData();

        mockMvc.perform(get("/api/schedules")
                        .param("courseName", "物")
                        .param("timeSlot", "周二 14:00-16:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].courseName").value("大学物理"))
                .andExpect(jsonPath("$[0].timeSlot").value("周二 14:00-16:00"));
    }

    @Test
    void testBatchAddSchedules_AllSuccess() throws Exception {
        List<CourseScheduleCreateRequest> requests = new java.util.ArrayList<>();
        requests.add(createRequest("高等数学", "张教授", "A101", "周一 08:00-10:00"));
        requests.add(createRequest("大学物理", "李教授", "B202", "周二 14:00-16:00"));
        requests.add(createRequest("线性代数", "王教授", "C303", "周三 09:00-11:00"));

        mockMvc.perform(post("/api/schedules/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requests)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(3))
                .andExpect(jsonPath("$.successCount").value(3))
                .andExpect(jsonPath("$.failureCount").value(0))
                .andExpect(jsonPath("$.successItems.length()").value(3))
                .andExpect(jsonPath("$.failureItems").isEmpty())
                .andExpect(jsonPath("$.successItems[0].courseName").value("高等数学"))
                .andExpect(jsonPath("$.successItems[1].courseName").value("大学物理"))
                .andExpect(jsonPath("$.successItems[2].courseName").value("线性代数"));

        mockMvc.perform(get("/api/schedules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    void testBatchAddSchedules_PartialFailure() throws Exception {
        List<CourseScheduleCreateRequest> requests = new java.util.ArrayList<>();
        requests.add(createRequest("高等数学", "张教授", "A101", "周一 08:00-10:00"));
        requests.add(createRequest("", "李教授", "B202", "周二 14:00-16:00"));
        requests.add(createRequest("线性代数", "王教授", "C303", "周三 09:00-11:00"));

        mockMvc.perform(post("/api/schedules/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requests)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(3))
                .andExpect(jsonPath("$.successCount").value(2))
                .andExpect(jsonPath("$.failureCount").value(1))
                .andExpect(jsonPath("$.successItems.length()").value(2))
                .andExpect(jsonPath("$.failureItems.length()").value(1))
                .andExpect(jsonPath("$.failureItems[0].index").value(1))
                .andExpect(jsonPath("$.failureItems[0].errorMessage").value("课程名不能为空"));

        mockMvc.perform(get("/api/schedules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void testBatchAddSchedules_InternalTeacherConflict() throws Exception {
        List<CourseScheduleCreateRequest> requests = new java.util.ArrayList<>();
        requests.add(createRequest("高等数学", "张教授", "A101", "周一 08:00-10:00"));
        requests.add(createRequest("线性代数", "张教授", "B202", "周一 08:00-10:00"));

        mockMvc.perform(post("/api/schedules/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requests)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(2))
                .andExpect(jsonPath("$.successCount").value(1))
                .andExpect(jsonPath("$.failureCount").value(1))
                .andExpect(jsonPath("$.successItems.length()").value(1))
                .andExpect(jsonPath("$.failureItems.length()").value(1))
                .andExpect(jsonPath("$.failureItems[0].index").value(1))
                .andExpect(jsonPath("$.failureItems[0].courseName").value("线性代数"))
                .andExpect(jsonPath("$.failureItems[0].errorMessage").value(org.hamcrest.Matchers.containsString("与本次批量新增的其他课程冲突")));

        mockMvc.perform(get("/api/schedules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void testBatchAddSchedules_InternalClassroomConflict() throws Exception {
        List<CourseScheduleCreateRequest> requests = new java.util.ArrayList<>();
        requests.add(createRequest("高等数学", "张教授", "A101", "周一 08:00-10:00"));
        requests.add(createRequest("线性代数", "李教授", "A101", "周一 08:00-10:00"));

        mockMvc.perform(post("/api/schedules/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requests)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(2))
                .andExpect(jsonPath("$.successCount").value(1))
                .andExpect(jsonPath("$.failureCount").value(1))
                .andExpect(jsonPath("$.failureItems[0].index").value(1))
                .andExpect(jsonPath("$.failureItems[0].errorMessage").value(org.hamcrest.Matchers.containsString("与本次批量新增的其他课程冲突")));
    }

    @Test
    void testBatchAddSchedules_ConflictWithExisting() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("已有课程", "张教授", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        List<CourseScheduleCreateRequest> requests = new java.util.ArrayList<>();
        requests.add(createRequest("高等数学", "张教授", "B202", "周二 14:00-16:00"));
        requests.add(createRequest("线性代数", "张教授", "C303", "周一 08:00-10:00"));

        mockMvc.perform(post("/api/schedules/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requests)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(2))
                .andExpect(jsonPath("$.successCount").value(1))
                .andExpect(jsonPath("$.failureCount").value(1))
                .andExpect(jsonPath("$.failureItems[0].index").value(1))
                .andExpect(jsonPath("$.failureItems[0].errorMessage").value(org.hamcrest.Matchers.containsString("已有课程安排")));

        mockMvc.perform(get("/api/schedules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void testBatchAddSchedules_InvalidTimeSlotFormat() throws Exception {
        List<CourseScheduleCreateRequest> requests = new java.util.ArrayList<>();
        requests.add(createRequest("高等数学", "张教授", "A101", "周一 08:00-10:00"));
        requests.add(createRequest("线性代数", "李教授", "B202", "明天上午"));
        requests.add(createRequest("大学物理", "王教授", "C303", "周二 14:00-16:00"));

        mockMvc.perform(post("/api/schedules/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requests)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(3))
                .andExpect(jsonPath("$.successCount").value(2))
                .andExpect(jsonPath("$.failureCount").value(1))
                .andExpect(jsonPath("$.failureItems[0].index").value(1))
                .andExpect(jsonPath("$.failureItems[0].errorMessage").value(org.hamcrest.Matchers.containsString("格式不合法")));

        mockMvc.perform(get("/api/schedules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void testBatchAddSchedules_EmptyList() throws Exception {
        List<CourseScheduleCreateRequest> requests = new java.util.ArrayList<>();

        mockMvc.perform(post("/api/schedules/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requests)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(0))
                .andExpect(jsonPath("$.successCount").value(0))
                .andExpect(jsonPath("$.failureCount").value(0))
                .andExpect(jsonPath("$.successItems").isEmpty())
                .andExpect(jsonPath("$.failureItems").isEmpty());
    }

    @Test
    void testBatchAddSchedules_MultipleFailures() throws Exception {
        List<CourseScheduleCreateRequest> requests = new java.util.ArrayList<>();
        requests.add(createRequest("高等数学", "张教授", "A101", "周一 08:00-10:00"));
        requests.add(createRequest("", "李教授", "B202", "周二 14:00-16:00"));
        requests.add(createRequest("线性代数", "", "C303", "周三 09:00-11:00"));
        requests.add(createRequest("大学物理", "王教授", "", "周四 10:00-12:00"));
        requests.add(createRequest("化学", "赵教授", "D404", "invalid time"));

        mockMvc.perform(post("/api/schedules/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requests)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(5))
                .andExpect(jsonPath("$.successCount").value(1))
                .andExpect(jsonPath("$.failureCount").value(4))
                .andExpect(jsonPath("$.failureItems[0].index").value(1))
                .andExpect(jsonPath("$.failureItems[1].index").value(2))
                .andExpect(jsonPath("$.failureItems[2].index").value(3))
                .andExpect(jsonPath("$.failureItems[3].index").value(4));

        mockMvc.perform(get("/api/schedules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void testBatchAddSchedules_StartAfterEndTime() throws Exception {
        List<CourseScheduleCreateRequest> requests = new java.util.ArrayList<>();
        requests.add(createRequest("高等数学", "张教授", "A101", "周一 10:00-08:00"));
        requests.add(createRequest("线性代数", "李教授", "B202", "周二 14:00-16:00"));

        mockMvc.perform(post("/api/schedules/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requests)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(2))
                .andExpect(jsonPath("$.successCount").value(1))
                .andExpect(jsonPath("$.failureCount").value(1))
                .andExpect(jsonPath("$.failureItems[0].index").value(0))
                .andExpect(jsonPath("$.failureItems[0].errorMessage").value(org.hamcrest.Matchers.containsString("必须早于")));
    }

    @Test
    void testBatchAddSchedules_NonStandardTimeSlot_FormatNormalized() throws Exception {
        List<CourseScheduleCreateRequest> requests = new java.util.ArrayList<>();
        requests.add(createRequest("高等数学", "张教授", "A101", "星期一 8:00-10:00"));
        requests.add(createRequest("线性代数", "李教授", "B202", "周2 14:00-16:00"));

        mockMvc.perform(post("/api/schedules/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requests)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.successCount").value(2))
                .andExpect(jsonPath("$.successItems[0].timeSlot").value("周一 08:00-10:00"))
                .andExpect(jsonPath("$.successItems[1].timeSlot").value("周二 14:00-16:00"));
    }

    @Test
    void testBatchAddSchedules_ListWithNullElement() throws Exception {
        List<CourseScheduleCreateRequest> requests = new java.util.ArrayList<>();
        requests.add(createRequest("高等数学", "张教授", "A101", "周一 08:00-10:00"));
        requests.add(null);
        requests.add(createRequest("线性代数", "李教授", "B202", "周二 14:00-16:00"));

        mockMvc.perform(post("/api/schedules/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requests)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(3))
                .andExpect(jsonPath("$.successCount").value(2))
                .andExpect(jsonPath("$.failureCount").value(1))
                .andExpect(jsonPath("$.failureItems[0].index").value(1))
                .andExpect(jsonPath("$.failureItems[0].errorMessage").value("课程请求不能为空"));

        mockMvc.perform(get("/api/schedules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void testBatchAddSchedules_MultipleNullElements() throws Exception {
        List<CourseScheduleCreateRequest> requests = new java.util.ArrayList<>();
        requests.add(null);
        requests.add(createRequest("高等数学", "张教授", "A101", "周一 08:00-10:00"));
        requests.add(null);
        requests.add(createRequest("线性代数", "李教授", "B202", "周二 14:00-16:00"));
        requests.add(null);

        mockMvc.perform(post("/api/schedules/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requests)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(5))
                .andExpect(jsonPath("$.successCount").value(2))
                .andExpect(jsonPath("$.failureCount").value(3))
                .andExpect(jsonPath("$.failureItems[0].index").value(0))
                .andExpect(jsonPath("$.failureItems[1].index").value(2))
                .andExpect(jsonPath("$.failureItems[2].index").value(4));

        mockMvc.perform(get("/api/schedules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void testBatchAddSchedules_EmptyBody() throws Exception {
        mockMvc.perform(post("/api/schedules/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("请求列表不能为空"));
    }

    @Test
    void testBatchAddSchedules_NullRequestBody() throws Exception {
        mockMvc.perform(post("/api/schedules/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("请求列表不能为空"));
    }

    @Test
    void testBatchDeleteSchedules_AllSuccess() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张教授", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("物理", "李教授", "B202", "周二 14:00-16:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("化学", "王教授", "C303", "周三 09:00-11:00"))))
                .andExpect(status().isCreated());

        List<Long> ids = new java.util.ArrayList<>();
        ids.add(1L);
        ids.add(2L);
        ids.add(3L);

        mockMvc.perform(delete("/api/schedules/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ids)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(3))
                .andExpect(jsonPath("$.successCount").value(3))
                .andExpect(jsonPath("$.failureCount").value(0))
                .andExpect(jsonPath("$.successItems.length()").value(3))
                .andExpect(jsonPath("$.failureItems").isEmpty());

        mockMvc.perform(get("/api/schedules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(get("/api/schedules/statistics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCourses").value(0));
    }

    @Test
    void testBatchDeleteSchedules_PartialNotFound() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张教授", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("物理", "李教授", "B202", "周二 14:00-16:00"))))
                .andExpect(status().isCreated());

        List<Long> ids = new java.util.ArrayList<>();
        ids.add(1L);
        ids.add(999L);
        ids.add(2L);
        ids.add(888L);

        mockMvc.perform(delete("/api/schedules/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ids)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(4))
                .andExpect(jsonPath("$.successCount").value(2))
                .andExpect(jsonPath("$.failureCount").value(2))
                .andExpect(jsonPath("$.successItems.length()").value(2))
                .andExpect(jsonPath("$.failureItems.length()").value(2))
                .andExpect(jsonPath("$.failureItems[0].id").value(999))
                .andExpect(jsonPath("$.failureItems[0].errorMessage").value("课程安排不存在"))
                .andExpect(jsonPath("$.failureItems[1].id").value(888))
                .andExpect(jsonPath("$.failureItems[1].errorMessage").value("课程安排不存在"));

        mockMvc.perform(get("/api/schedules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void testBatchDeleteSchedules_AllFailure() throws Exception {
        List<Long> ids = new java.util.ArrayList<>();
        ids.add(999L);
        ids.add(888L);
        ids.add(777L);

        mockMvc.perform(delete("/api/schedules/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ids)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(3))
                .andExpect(jsonPath("$.successCount").value(0))
                .andExpect(jsonPath("$.failureCount").value(3))
                .andExpect(jsonPath("$.successItems").isEmpty())
                .andExpect(jsonPath("$.failureItems.length()").value(3));
    }

    @Test
    void testBatchDeleteSchedules_DuplicateIds() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张教授", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("物理", "李教授", "B202", "周二 14:00-16:00"))))
                .andExpect(status().isCreated());

        List<Long> ids = new java.util.ArrayList<>();
        ids.add(1L);
        ids.add(2L);
        ids.add(1L);
        ids.add(2L);
        ids.add(1L);

        mockMvc.perform(delete("/api/schedules/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ids)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(5))
                .andExpect(jsonPath("$.successCount").value(2))
                .andExpect(jsonPath("$.failureCount").value(3))
                .andExpect(jsonPath("$.successItems.length()").value(2))
                .andExpect(jsonPath("$.failureItems.length()").value(3))
                .andExpect(jsonPath("$.failureItems[0].id").value(1))
                .andExpect(jsonPath("$.failureItems[0].errorMessage").value("重复 id 已忽略"))
                .andExpect(jsonPath("$.failureItems[1].id").value(2))
                .andExpect(jsonPath("$.failureItems[1].errorMessage").value("重复 id 已忽略"))
                .andExpect(jsonPath("$.failureItems[2].id").value(1))
                .andExpect(jsonPath("$.failureItems[2].errorMessage").value("重复 id 已忽略"));

        mockMvc.perform(get("/api/schedules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void testBatchDeleteSchedules_EmptyList() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张教授", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        List<Long> ids = new java.util.ArrayList<>();

        mockMvc.perform(delete("/api/schedules/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ids)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(0))
                .andExpect(jsonPath("$.successCount").value(0))
                .andExpect(jsonPath("$.failureCount").value(0))
                .andExpect(jsonPath("$.successItems").isEmpty())
                .andExpect(jsonPath("$.failureItems").isEmpty());

        mockMvc.perform(get("/api/schedules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void testBatchDeleteSchedules_NegativeIds() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张教授", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        List<Long> ids = new java.util.ArrayList<>();
        ids.add(1L);
        ids.add(-1L);
        ids.add(-5L);
        ids.add(0L);

        mockMvc.perform(delete("/api/schedules/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ids)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(4))
                .andExpect(jsonPath("$.successCount").value(1))
                .andExpect(jsonPath("$.failureCount").value(3))
                .andExpect(jsonPath("$.successItems.length()").value(1))
                .andExpect(jsonPath("$.failureItems.length()").value(3))
                .andExpect(jsonPath("$.failureItems[0].id").value(-1))
                .andExpect(jsonPath("$.failureItems[0].errorMessage").value("无效的课程 ID"))
                .andExpect(jsonPath("$.failureItems[1].id").value(-5))
                .andExpect(jsonPath("$.failureItems[1].errorMessage").value("无效的课程 ID"))
                .andExpect(jsonPath("$.failureItems[2].id").value(0))
                .andExpect(jsonPath("$.failureItems[2].errorMessage").value("无效的课程 ID"));

        mockMvc.perform(get("/api/schedules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void testBatchDeleteSchedules_ListAndStatisticsUpdated() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张教授", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("物理", "李教授", "B202", "周二 14:00-16:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("化学", "张教授", "C303", "周三 09:00-11:00"))))
                .andExpect(status().isCreated());

        List<Long> ids = new java.util.ArrayList<>();
        ids.add(2L);

        mockMvc.perform(delete("/api/schedules/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ids)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.successCount").value(1));

        mockMvc.perform(get("/api/schedules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].courseName").value("数学"))
                .andExpect(jsonPath("$[1].courseName").value("化学"));

        mockMvc.perform(get("/api/schedules/statistics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCourses").value(2))
                .andExpect(jsonPath("$.teacherCount").value(1))
                .andExpect(jsonPath("$.classroomCount").value(2));
    }

    @Test
    void testBatchDeleteSchedules_NullRequestBody() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张教授", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/schedules/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("请求列表不能为空"));

        mockMvc.perform(get("/api/schedules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void testBatchDeleteSchedules_DuplicateNegativeIds() throws Exception {
        List<Long> ids = new java.util.ArrayList<>();
        ids.add(-1L);
        ids.add(-1L);
        ids.add(-5L);
        ids.add(-5L);
        ids.add(-1L);

        mockMvc.perform(delete("/api/schedules/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ids)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(5))
                .andExpect(jsonPath("$.successCount").value(0))
                .andExpect(jsonPath("$.failureCount").value(5))
                .andExpect(jsonPath("$.successItems").isEmpty())
                .andExpect(jsonPath("$.failureItems.length()").value(5))
                .andExpect(jsonPath("$.failureItems[0].id").value(-1))
                .andExpect(jsonPath("$.failureItems[0].errorMessage").value("无效的课程 ID"))
                .andExpect(jsonPath("$.failureItems[1].id").value(-1))
                .andExpect(jsonPath("$.failureItems[1].errorMessage").value("无效的课程 ID"))
                .andExpect(jsonPath("$.failureItems[2].id").value(-5))
                .andExpect(jsonPath("$.failureItems[2].errorMessage").value("无效的课程 ID"))
                .andExpect(jsonPath("$.failureItems[3].id").value(-5))
                .andExpect(jsonPath("$.failureItems[3].errorMessage").value("无效的课程 ID"))
                .andExpect(jsonPath("$.failureItems[4].id").value(-1))
                .andExpect(jsonPath("$.failureItems[4].errorMessage").value("无效的课程 ID"));
    }

    @Test
    void testBatchDeleteSchedules_DuplicateZeroIds() throws Exception {
        List<Long> ids = new java.util.ArrayList<>();
        ids.add(0L);
        ids.add(0L);
        ids.add(0L);

        mockMvc.perform(delete("/api/schedules/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ids)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(3))
                .andExpect(jsonPath("$.successCount").value(0))
                .andExpect(jsonPath("$.failureCount").value(3))
                .andExpect(jsonPath("$.successItems").isEmpty())
                .andExpect(jsonPath("$.failureItems.length()").value(3))
                .andExpect(jsonPath("$.failureItems[0].id").value(0))
                .andExpect(jsonPath("$.failureItems[0].errorMessage").value("无效的课程 ID"))
                .andExpect(jsonPath("$.failureItems[1].id").value(0))
                .andExpect(jsonPath("$.failureItems[1].errorMessage").value("无效的课程 ID"))
                .andExpect(jsonPath("$.failureItems[2].id").value(0))
                .andExpect(jsonPath("$.failureItems[2].errorMessage").value("无效的课程 ID"));
    }

    @Test
    void testBatchDeleteSchedules_MixedInvalidDuplicates() throws Exception {
        List<Long> ids = new java.util.ArrayList<>();
        ids.add(-1L);
        ids.add(0L);
        ids.add(-1L);
        ids.add(0L);
        ids.add(-10L);
        ids.add(0L);

        mockMvc.perform(delete("/api/schedules/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ids)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(6))
                .andExpect(jsonPath("$.successCount").value(0))
                .andExpect(jsonPath("$.failureCount").value(6))
                .andExpect(jsonPath("$.successItems").isEmpty())
                .andExpect(jsonPath("$.failureItems.length()").value(6));
    }

    @Test
    void testBatchDeleteSchedules_InvalidDuplicatesPlusValidDuplicates() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张教授", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        List<Long> ids = new java.util.ArrayList<>();
        ids.add(-1L);
        ids.add(1L);
        ids.add(-1L);
        ids.add(0L);
        ids.add(1L);
        ids.add(0L);
        ids.add(1L);

        mockMvc.perform(delete("/api/schedules/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ids)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(7))
                .andExpect(jsonPath("$.successCount").value(1))
                .andExpect(jsonPath("$.failureCount").value(6))
                .andExpect(jsonPath("$.successItems.length()").value(1))
                .andExpect(jsonPath("$.failureItems.length()").value(6))
                .andExpect(jsonPath("$.failureItems[0].id").value(-1))
                .andExpect(jsonPath("$.failureItems[0].errorMessage").value("无效的课程 ID"))
                .andExpect(jsonPath("$.failureItems[1].id").value(-1))
                .andExpect(jsonPath("$.failureItems[1].errorMessage").value("无效的课程 ID"))
                .andExpect(jsonPath("$.failureItems[2].id").value(0))
                .andExpect(jsonPath("$.failureItems[2].errorMessage").value("无效的课程 ID"))
                .andExpect(jsonPath("$.failureItems[3].id").value(1))
                .andExpect(jsonPath("$.failureItems[3].errorMessage").value("重复 id 已忽略"))
                .andExpect(jsonPath("$.failureItems[4].id").value(0))
                .andExpect(jsonPath("$.failureItems[4].errorMessage").value("无效的课程 ID"))
                .andExpect(jsonPath("$.failureItems[5].id").value(1))
                .andExpect(jsonPath("$.failureItems[5].errorMessage").value("重复 id 已忽略"));

        mockMvc.perform(get("/api/schedules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void testGetWeekdayStatistics_EmptyData() throws Exception {
        mockMvc.perform(get("/api/schedules/statistics/weekday"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monday").value(0))
                .andExpect(jsonPath("$.tuesday").value(0))
                .andExpect(jsonPath("$.wednesday").value(0))
                .andExpect(jsonPath("$.thursday").value(0))
                .andExpect(jsonPath("$.friday").value(0))
                .andExpect(jsonPath("$.saturday").value(0))
                .andExpect(jsonPath("$.sunday").value(0));
    }

    @Test
    void testGetWeekdayStatistics_WithData() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("物理", "李老师", "B202", "周一 10:00-12:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("化学", "王老师", "C303", "周二 08:00-10:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules/statistics/weekday"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monday").value(2))
                .andExpect(jsonPath("$.tuesday").value(1))
                .andExpect(jsonPath("$.wednesday").value(0))
                .andExpect(jsonPath("$.thursday").value(0))
                .andExpect(jsonPath("$.friday").value(0))
                .andExpect(jsonPath("$.saturday").value(0))
                .andExpect(jsonPath("$.sunday").value(0));
    }

    @Test
    void testGetWeekdayStatistics_NonStandardFormat_Normalized() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张老师", "A101", "星期一 8:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("物理", "李老师", "B202", "周1 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("化学", "王老师", "C303", "星期2 09:00-11:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules/statistics/weekday"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monday").value(2))
                .andExpect(jsonPath("$.tuesday").value(1))
                .andExpect(jsonPath("$.wednesday").value(0))
                .andExpect(jsonPath("$.sunday").value(0));
    }

    @Test
    void testGetWeekdayStatistics_AfterUpdate_ChangesAccordingly() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules/statistics/weekday"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monday").value(1))
                .andExpect(jsonPath("$.tuesday").value(0));

        CourseScheduleUpdateRequest updateRequest = createUpdateRequest("高等数学", "张老师", "A101", "周二 08:00-10:00");
        mockMvc.perform(put("/api/schedules/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/schedules/statistics/weekday"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monday").value(0))
                .andExpect(jsonPath("$.tuesday").value(1));
    }

    @Test
    void testGetWeekdayStatistics_AfterDelete_ChangesAccordingly() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("物理", "李老师", "B202", "周一 10:00-12:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules/statistics/weekday"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monday").value(2));

        mockMvc.perform(delete("/api/schedules/1"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/schedules/statistics/weekday"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monday").value(1));
    }

    @Test
    void testGetWeekdayStatistics_AfterBatchDelete_ChangesAccordingly() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("物理", "李老师", "B202", "周二 14:00-16:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("化学", "王老师", "C303", "周一 10:00-12:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("英语", "赵老师", "D404", "周三 09:00-11:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules/statistics/weekday"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monday").value(2))
                .andExpect(jsonPath("$.tuesday").value(1))
                .andExpect(jsonPath("$.wednesday").value(1));

        List<Long> ids = new java.util.ArrayList<>();
        ids.add(1L);
        ids.add(2L);
        ids.add(3L);
        mockMvc.perform(delete("/api/schedules/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ids)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/schedules/statistics/weekday"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monday").value(0))
                .andExpect(jsonPath("$.tuesday").value(0))
                .andExpect(jsonPath("$.wednesday").value(1));
    }

    @Test
    void testGetTeacherCourseStatistics_EmptyData() throws Exception {
        mockMvc.perform(get("/api/schedules/statistics/teacher"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void testGetTeacherCourseStatistics_MultipleTeachers_SortedByCountDesc() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("物理", "张老师", "B202", "周一 10:00-12:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("化学", "张老师", "C303", "周二 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("英语", "李老师", "D404", "周二 10:00-12:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("生物", "李老师", "E505", "周三 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("历史", "王老师", "F606", "周三 10:00-12:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules/statistics/teacher"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].teacherName").value("张老师"))
                .andExpect(jsonPath("$[0].courseCount").value(3))
                .andExpect(jsonPath("$[1].teacherName").value("李老师"))
                .andExpect(jsonPath("$[1].courseCount").value(2))
                .andExpect(jsonPath("$[2].teacherName").value("王老师"))
                .andExpect(jsonPath("$[2].courseCount").value(1));
    }

    @Test
    void testGetTeacherCourseStatistics_SameCount_SortedByNameAsc() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "李老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("物理", "王老师", "B202", "周一 10:00-12:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("化学", "张老师", "C303", "周二 08:00-10:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules/statistics/teacher"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].courseCount").value(1))
                .andExpect(jsonPath("$[1].courseCount").value(1))
                .andExpect(jsonPath("$[2].courseCount").value(1))
                .andExpect(jsonPath("$[0].teacherName").value("张老师"))
                .andExpect(jsonPath("$[1].teacherName").value("李老师"))
                .andExpect(jsonPath("$[2].teacherName").value("王老师"));
    }

    @Test
    void testGetTeacherCourseStatistics_AfterUpdateTeacher_ChangesAccordingly() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("物理", "李老师", "B202", "周二 08:00-10:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules/statistics/teacher"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        CourseScheduleUpdateRequest updateRequest = createUpdateRequest("数学", "李老师", "A101", "周一 08:00-10:00");
        mockMvc.perform(put("/api/schedules/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/schedules/statistics/teacher"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].teacherName").value("李老师"))
                .andExpect(jsonPath("$[0].courseCount").value(2));
    }

    @Test
    void testGetTeacherCourseStatistics_AfterDelete_ChangesAccordingly() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("物理", "李老师", "B202", "周二 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("化学", "张老师", "C303", "周三 08:00-10:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules/statistics/teacher"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        mockMvc.perform(delete("/api/schedules/2"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/schedules/statistics/teacher"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].teacherName").value("张老师"))
                .andExpect(jsonPath("$[0].courseCount").value(2));
    }

    @Test
    void testGetTeacherCourseStatistics_AfterBatchDelete_ChangesAccordingly() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("物理", "李老师", "B202", "周二 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("化学", "张老师", "C303", "周三 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("英语", "王老师", "D404", "周四 08:00-10:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules/statistics/teacher"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));

        List<Long> ids = new java.util.ArrayList<>();
        ids.add(1L);
        ids.add(2L);
        ids.add(4L);
        mockMvc.perform(delete("/api/schedules/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ids)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/schedules/statistics/teacher"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].teacherName").value("张老师"))
                .andExpect(jsonPath("$[0].courseCount").value(1));
    }

    @Test
    void testAddSchedule_WithWhitespace_FieldsTrimmed() throws Exception {
        CourseScheduleCreateRequest request = createRequest("  高等数学  ", "  张教授  ", "  A101  ", "周一 08:00-10:00");

        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.courseName").value("高等数学"))
                .andExpect(jsonPath("$.teacherName").value("张教授"))
                .andExpect(jsonPath("$.classroom").value("A101"));
    }

    @Test
    void testUpdateSchedule_WithWhitespace_FieldsTrimmed() throws Exception {
        CourseScheduleCreateRequest request = createRequest("数学", "张老师", "A101", "周一 08:00-10:00");
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        CourseScheduleUpdateRequest updateRequest = createUpdateRequest("  线性代数  ", "  李教授  ", "  B202  ", "周二 14:00-16:00");
        mockMvc.perform(put("/api/schedules/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courseName").value("线性代数"))
                .andExpect(jsonPath("$.teacherName").value("李教授"))
                .andExpect(jsonPath("$.classroom").value("B202"));
    }

    @Test
    void testAddSchedule_TeacherNameWithWhitespace_StatisticsMerged() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("物理", "  张老师  ", "B202", "周一 10:00-12:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("化学", "  张老师", "C303", "周二 08:00-10:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules/statistics/teacher"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].teacherName").value("张老师"))
                .andExpect(jsonPath("$[0].courseCount").value(3));
    }

    @Test
    void testUpdateSchedule_TeacherNameWithWhitespace_StatisticsMerged() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("物理", "李老师", "B202", "周二 08:00-10:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules/statistics/teacher"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        CourseScheduleUpdateRequest updateRequest = createUpdateRequest("物理", "  张老师  ", "B202", "周二 08:00-10:00");
        mockMvc.perform(put("/api/schedules/2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/schedules/statistics/teacher"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].teacherName").value("张老师"))
                .andExpect(jsonPath("$[0].courseCount").value(2));
    }

    @Test
    void testBatchAddSchedules_WithWhitespace_FieldsTrimmed() throws Exception {
        List<CourseScheduleCreateRequest> requests = new java.util.ArrayList<>();
        requests.add(createRequest("  数学  ", "  张老师  ", "  A101  ", "周一 08:00-10:00"));
        requests.add(createRequest("  物理  ", "  李老师  ", "  B202  ", "周二 14:00-16:00"));

        mockMvc.perform(post("/api/schedules/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requests)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.successCount").value(2))
                .andExpect(jsonPath("$.successItems[0].courseName").value("数学"))
                .andExpect(jsonPath("$.successItems[0].teacherName").value("张老师"))
                .andExpect(jsonPath("$.successItems[0].classroom").value("A101"))
                .andExpect(jsonPath("$.successItems[1].courseName").value("物理"))
                .andExpect(jsonPath("$.successItems[1].teacherName").value("李老师"))
                .andExpect(jsonPath("$.successItems[1].classroom").value("B202"));

        mockMvc.perform(get("/api/schedules/statistics/teacher"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void testGetSchedules_AfterAddWithWhitespace_CanFindByTrimmedName() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("  高等数学  ", "  张教授  ", "  教学楼A101  ", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules")
                        .param("teacherName", "张")
                        .param("classroom", "教学楼"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].courseName").value("高等数学"))
                .andExpect(jsonPath("$[0].teacherName").value("张教授"))
                .andExpect(jsonPath("$[0].classroom").value("教学楼A101"));
    }

    @Test
    void testGetClassroomCourseStatistics_EmptyData() throws Exception {
        mockMvc.perform(get("/api/schedules/statistics/classroom"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void testGetClassroomCourseStatistics_MultipleClassrooms_SortedByCountDesc() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("物理", "李老师", "A101", "周一 10:00-12:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("化学", "王老师", "A101", "周二 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("英语", "赵老师", "B202", "周三 14:00-16:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("生物", "钱老师", "B202", "周四 09:00-11:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("历史", "孙老师", "C303", "周五 08:00-10:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules/statistics/classroom"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].classroom").value("A101"))
                .andExpect(jsonPath("$[0].courseCount").value(3))
                .andExpect(jsonPath("$[1].classroom").value("B202"))
                .andExpect(jsonPath("$[1].courseCount").value(2))
                .andExpect(jsonPath("$[2].classroom").value("C303"))
                .andExpect(jsonPath("$[2].courseCount").value(1));
    }

    @Test
    void testGetClassroomCourseStatistics_SameCount_SortedByClassroomNameAsc() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张老师", "C303", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("物理", "李老师", "A101", "周二 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("化学", "王老师", "B202", "周三 08:00-10:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules/statistics/classroom"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].classroom").value("A101"))
                .andExpect(jsonPath("$[0].courseCount").value(1))
                .andExpect(jsonPath("$[1].classroom").value("B202"))
                .andExpect(jsonPath("$[1].courseCount").value(1))
                .andExpect(jsonPath("$[2].classroom").value("C303"))
                .andExpect(jsonPath("$[2].courseCount").value(1));
    }

    @Test
    void testGetClassroomCourseStatistics_AfterUpdateClassroom_ChangesAccordingly() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("物理", "李老师", "B202", "周二 08:00-10:00"))))
                .andExpect(status().isCreated());

        CourseScheduleUpdateRequest updateRequest = createUpdateRequest("数学", "张老师", "B202", "周一 08:00-10:00");
        mockMvc.perform(put("/api/schedules/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/schedules/statistics/classroom"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].classroom").value("B202"))
                .andExpect(jsonPath("$[0].courseCount").value(2));
    }

    @Test
    void testGetClassroomCourseStatistics_AfterDelete_ChangesAccordingly() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("物理", "李老师", "B202", "周二 08:00-10:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/schedules/2"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/schedules/statistics/classroom"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].classroom").value("A101"))
                .andExpect(jsonPath("$[0].courseCount").value(1));
    }

    @Test
    void testGetClassroomCourseStatistics_AfterBatchDelete_ChangesAccordingly() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("物理", "李老师", "B202", "周二 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("化学", "张老师", "A101", "周三 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("英语", "王老师", "C303", "周四 08:00-10:00"))))
                .andExpect(status().isCreated());

        List<Long> ids = new java.util.ArrayList<>();
        ids.add(1L);
        ids.add(2L);
        ids.add(4L);

        mockMvc.perform(delete("/api/schedules/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ids)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.successCount").value(3));

        mockMvc.perform(get("/api/schedules/statistics/classroom"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].classroom").value("A101"))
                .andExpect(jsonPath("$[0].courseCount").value(1));
    }

    @Test
    void testGetClassroomCourseStatistics_WithWhitespaceClassroom_Merged() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("物理", "李老师", "  A101  ", "周一 10:00-12:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("化学", "王老师", "  A101", "周二 08:00-10:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules/statistics/classroom"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].classroom").value("A101"))
                .andExpect(jsonPath("$[0].courseCount").value(3));
    }

    @Test
    void testExportSchedules_EmptyList_OnlyHeader() throws Exception {
        byte[] result = mockMvc.perform(get("/api/schedules/export"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("text/csv")))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        String csv = new String(result, java.nio.charset.StandardCharsets.UTF_8);
        String csvWithoutBom = csv.startsWith("\uFEFF") ? csv.substring(1) : csv;
        String[] lines = csvWithoutBom.split("\n");
        org.junit.jupiter.api.Assertions.assertEquals(1, lines.length);
        org.junit.jupiter.api.Assertions.assertEquals("课程名称,老师,教室,时间段", lines[0].trim());
    }

    @Test
    void testExportSchedules_WithData_AllRowsExported() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("高等数学", "王教授", "教学楼 301", "周二 14:00-16:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("线性代数", "李老师", "A101", "周三 08:00-10:00"))))
                .andExpect(status().isCreated());

        byte[] result = mockMvc.perform(get("/api/schedules/export"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        String csv = new String(result, java.nio.charset.StandardCharsets.UTF_8);
        String csvWithoutBom = csv.startsWith("\uFEFF") ? csv.substring(1) : csv;
        String[] lines = csvWithoutBom.split("\n");
        org.junit.jupiter.api.Assertions.assertEquals(3, lines.length);
        org.junit.jupiter.api.Assertions.assertEquals("课程名称,老师,教室,时间段", lines[0].trim());
        org.junit.jupiter.api.Assertions.assertEquals("高等数学,王教授,教学楼 301,周二 14:00-16:00", lines[1].trim());
        org.junit.jupiter.api.Assertions.assertEquals("线性代数,李老师,A101,周三 08:00-10:00", lines[2].trim());
    }

    @Test
    void testExportSchedules_FilterByTeacher_OnlyMatching() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张教授", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("物理", "李副教授", "教学楼B202", "周二 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("化学", "张教授", "C303", "周三 09:00-11:00"))))
                .andExpect(status().isCreated());

        byte[] result = mockMvc.perform(get("/api/schedules/export")
                        .param("teacherName", "张"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        String csv = new String(result, java.nio.charset.StandardCharsets.UTF_8);
        String csvWithoutBom = csv.startsWith("\uFEFF") ? csv.substring(1) : csv;
        String[] lines = csvWithoutBom.split("\n");
        org.junit.jupiter.api.Assertions.assertEquals(3, lines.length);
        org.junit.jupiter.api.Assertions.assertTrue(lines[1].contains("张教授"));
        org.junit.jupiter.api.Assertions.assertTrue(lines[2].contains("张教授"));
    }

    @Test
    void testExportSchedules_FilterByClassroomAndCourseName() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("高等数学", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("线性代数", "李老师", "B202", "周一 10:00-12:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学分析", "张老师", "A102", "周二 08:00-10:00"))))
                .andExpect(status().isCreated());

        byte[] result = mockMvc.perform(get("/api/schedules/export")
                        .param("courseName", "数学")
                        .param("classroom", "A10"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        String csv = new String(result, java.nio.charset.StandardCharsets.UTF_8);
        String csvWithoutBom = csv.startsWith("\uFEFF") ? csv.substring(1) : csv;
        String[] lines = csvWithoutBom.split("\n");
        org.junit.jupiter.api.Assertions.assertEquals(3, lines.length);
        org.junit.jupiter.api.Assertions.assertTrue(lines[1].contains("高等数学"));
        org.junit.jupiter.api.Assertions.assertTrue(lines[1].contains("A101"));
        org.junit.jupiter.api.Assertions.assertTrue(lines[2].contains("数学分析"));
        org.junit.jupiter.api.Assertions.assertTrue(lines[2].contains("A102"));
    }

    @Test
    void testExportSchedules_SpecialCharacters_CommaQuotedAndEscaped() throws Exception {
        CourseScheduleCreateRequest request1 = createRequest(
                "数学,高等", "王,教授", "教室,301", "周二 14:00-16:00");
        CourseScheduleCreateRequest request2 = createRequest(
                "物理\"实验\"", "李\"老师\"", "B\"202\"", "周三 08:00-10:00");
        CourseScheduleCreateRequest request3 = createRequest(
                "化学\n实验班", "赵\n老师", "C\n303", "周四 09:00-11:00");

        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request3)))
                .andExpect(status().isCreated());

        byte[] result = mockMvc.perform(get("/api/schedules/export"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        String csv = new String(result, java.nio.charset.StandardCharsets.UTF_8);
        String csvWithoutBom = csv.startsWith("\uFEFF") ? csv.substring(1) : csv;

        org.junit.jupiter.api.Assertions.assertTrue(csvWithoutBom.contains("\"数学,高等\""));
        org.junit.jupiter.api.Assertions.assertTrue(csvWithoutBom.contains("\"王,教授\""));
        org.junit.jupiter.api.Assertions.assertTrue(csvWithoutBom.contains("\"教室,301\""));
        org.junit.jupiter.api.Assertions.assertTrue(csvWithoutBom.contains("\"物理\"\"实验\"\"\""));
        org.junit.jupiter.api.Assertions.assertTrue(csvWithoutBom.contains("\"李\"\"老师\"\"\""));
        org.junit.jupiter.api.Assertions.assertTrue(csvWithoutBom.contains("\"B\"\"202\"\"\""));
        org.junit.jupiter.api.Assertions.assertTrue(csvWithoutBom.contains("\"化学\n实验班\""));
        org.junit.jupiter.api.Assertions.assertTrue(csvWithoutBom.contains("\"赵\n老师\""));
        org.junit.jupiter.api.Assertions.assertTrue(csvWithoutBom.contains("\"C\n303\""));
    }

    @Test
    void testExportSchedules_SortByCourseNameAscending_MatchesListOrder() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("物理", "李副教授", "教学楼B202", "周二 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张教授", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("化学", "王教授", "C303", "周三 09:00-11:00"))))
                .andExpect(status().isCreated());

        byte[] result = mockMvc.perform(get("/api/schedules/export")
                        .param("sortBy", "courseName")
                        .param("sortDirection", "asc"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        String csv = new String(result, java.nio.charset.StandardCharsets.UTF_8);
        String csvWithoutBom = csv.startsWith("\uFEFF") ? csv.substring(1) : csv;
        String[] lines = csvWithoutBom.split("\n");
        org.junit.jupiter.api.Assertions.assertEquals(4, lines.length);
        org.junit.jupiter.api.Assertions.assertTrue(lines[1].startsWith("化学"));
        org.junit.jupiter.api.Assertions.assertTrue(lines[2].startsWith("数学"));
        org.junit.jupiter.api.Assertions.assertTrue(lines[3].startsWith("物理"));
    }

    @Test
    void testExportSchedules_SortByClassroomDescending_MatchesListOrder() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("物理", "张教授", "B202", "周二 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "李副教授", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("化学", "王教授", "C303", "周三 09:00-11:00"))))
                .andExpect(status().isCreated());

        byte[] result = mockMvc.perform(get("/api/schedules/export")
                        .param("sortBy", "classroom")
                        .param("sortDirection", "desc"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        String csv = new String(result, java.nio.charset.StandardCharsets.UTF_8);
        String csvWithoutBom = csv.startsWith("\uFEFF") ? csv.substring(1) : csv;
        String[] lines = csvWithoutBom.split("\n");
        org.junit.jupiter.api.Assertions.assertEquals(4, lines.length);
        org.junit.jupiter.api.Assertions.assertTrue(lines[1].contains("C303"));
        org.junit.jupiter.api.Assertions.assertTrue(lines[2].contains("B202"));
        org.junit.jupiter.api.Assertions.assertTrue(lines[3].contains("A101"));
    }

    @Test
    void testExportSchedules_FilterThenSort_ByTeacherThenCourseName() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("物理", "张教授", "B202", "周二 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张教授", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("化学", "李副教授", "C303", "周三 09:00-11:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("生物", "张教授", "D404", "周四 10:00-12:00"))))
                .andExpect(status().isCreated());

        byte[] result = mockMvc.perform(get("/api/schedules/export")
                        .param("teacherName", "张")
                        .param("sortBy", "courseName")
                        .param("sortDirection", "asc"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        String csv = new String(result, java.nio.charset.StandardCharsets.UTF_8);
        String csvWithoutBom = csv.startsWith("\uFEFF") ? csv.substring(1) : csv;
        String[] lines = csvWithoutBom.split("\n");
        org.junit.jupiter.api.Assertions.assertEquals(4, lines.length);
        org.junit.jupiter.api.Assertions.assertTrue(lines[1].startsWith("数学"));
        org.junit.jupiter.api.Assertions.assertTrue(lines[1].contains("张教授"));
        org.junit.jupiter.api.Assertions.assertTrue(lines[2].startsWith("物理"));
        org.junit.jupiter.api.Assertions.assertTrue(lines[2].contains("张教授"));
        org.junit.jupiter.api.Assertions.assertTrue(lines[3].startsWith("生物"));
        org.junit.jupiter.api.Assertions.assertTrue(lines[3].contains("张教授"));
    }

    @Test
    void testExportSchedules_InvalidSortField_Returns400() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张教授", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules/export")
                        .param("sortBy", "invalidField"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.error").value("不支持的排序字段: invalidField。支持的字段有: [courseName, teacherName, classroom, timeSlot]"));
    }

    @Test
    void testExportSchedules_FilterNoMatch_OnlyHeader() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张教授", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        byte[] result = mockMvc.perform(get("/api/schedules/export")
                        .param("teacherName", "赵"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        String csv = new String(result, java.nio.charset.StandardCharsets.UTF_8);
        String csvWithoutBom = csv.startsWith("\uFEFF") ? csv.substring(1) : csv;
        String[] lines = csvWithoutBom.split("\n");
        org.junit.jupiter.api.Assertions.assertEquals(1, lines.length);
        org.junit.jupiter.api.Assertions.assertEquals("课程名称,老师,教室,时间段", lines[0].trim());
    }

    @Test
    void testExportSchedules_DefaultOrder_ByCreation() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("物理", "李副教授", "教学楼B202", "周二 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张教授", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("化学", "王教授", "C303", "周三 09:00-11:00"))))
                .andExpect(status().isCreated());

        byte[] result = mockMvc.perform(get("/api/schedules/export"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        String csv = new String(result, java.nio.charset.StandardCharsets.UTF_8);
        String csvWithoutBom = csv.startsWith("\uFEFF") ? csv.substring(1) : csv;
        String[] lines = csvWithoutBom.split("\n");
        org.junit.jupiter.api.Assertions.assertEquals(4, lines.length);
        org.junit.jupiter.api.Assertions.assertTrue(lines[1].startsWith("物理"));
        org.junit.jupiter.api.Assertions.assertTrue(lines[2].startsWith("数学"));
        org.junit.jupiter.api.Assertions.assertTrue(lines[3].startsWith("化学"));
    }

    private CourseScheduleBatchTimeSlotUpdateRequest createTimeSlotUpdateRequest(String fromTimeSlot, String toTimeSlot) {
        CourseScheduleBatchTimeSlotUpdateRequest request = new CourseScheduleBatchTimeSlotUpdateRequest();
        request.setFromTimeSlot(fromTimeSlot);
        request.setToTimeSlot(toTimeSlot);
        return request;
    }

    @Test
    void testBatchUpdateTimeSlot_SingleCourse_Success() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张教授", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        CourseScheduleBatchTimeSlotUpdateRequest request = createTimeSlotUpdateRequest("周一 08:00-10:00", "周二 14:00-16:00");

        mockMvc.perform(put("/api/schedules/batch/time-slot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matchedCount").value(1))
                .andExpect(jsonPath("$.updatedCount").value(1))
                .andExpect(jsonPath("$.fromTimeSlot").value("周一 08:00-10:00"))
                .andExpect(jsonPath("$.toTimeSlot").value("周二 14:00-16:00"))
                .andExpect(jsonPath("$.updatedItems").isArray())
                .andExpect(jsonPath("$.updatedItems.length()").value(1))
                .andExpect(jsonPath("$.updatedItems[0].timeSlot").value("周二 14:00-16:00"));

        mockMvc.perform(get("/api/schedules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].timeSlot").value("周二 14:00-16:00"));
    }

    @Test
    void testBatchUpdateTimeSlot_MultipleCourses_Success() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张教授", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("物理", "李教授", "B202", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("化学", "王教授", "C303", "周三 09:00-11:00"))))
                .andExpect(status().isCreated());

        CourseScheduleBatchTimeSlotUpdateRequest request = createTimeSlotUpdateRequest("周一 08:00-10:00", "周五 14:00-16:00");

        mockMvc.perform(put("/api/schedules/batch/time-slot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matchedCount").value(2))
                .andExpect(jsonPath("$.updatedCount").value(2));

        mockMvc.perform(get("/api/schedules").param("timeSlot", "周五 14:00-16:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void testBatchUpdateTimeSlot_NoMatch_ReturnsZero() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张教授", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        CourseScheduleBatchTimeSlotUpdateRequest request = createTimeSlotUpdateRequest("周二 14:00-16:00", "周三 09:00-11:00");

        mockMvc.perform(put("/api/schedules/batch/time-slot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matchedCount").value(0))
                .andExpect(jsonPath("$.updatedCount").value(0))
                .andExpect(jsonPath("$.updatedItems").isArray())
                .andExpect(jsonPath("$.updatedItems.length()").value(0));

        mockMvc.perform(get("/api/schedules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].timeSlot").value("周一 08:00-10:00"));
    }

    @Test
    void testBatchUpdateTimeSlot_EmptyData_ReturnsZero() throws Exception {
        CourseScheduleBatchTimeSlotUpdateRequest request = createTimeSlotUpdateRequest("周一 08:00-10:00", "周二 14:00-16:00");

        mockMvc.perform(put("/api/schedules/batch/time-slot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matchedCount").value(0))
                .andExpect(jsonPath("$.updatedCount").value(0));
    }

    @Test
    void testBatchUpdateTimeSlot_NonStandardTimeFormat_Normalized() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张教授", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        CourseScheduleBatchTimeSlotUpdateRequest request = createTimeSlotUpdateRequest("星期一 8:00-10:00", "星期2 2:00-4:00");

        mockMvc.perform(put("/api/schedules/batch/time-slot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matchedCount").value(1))
                .andExpect(jsonPath("$.updatedCount").value(1))
                .andExpect(jsonPath("$.fromTimeSlot").value("周一 08:00-10:00"))
                .andExpect(jsonPath("$.toTimeSlot").value("周二 02:00-04:00"));
    }

    @Test
    void testBatchUpdateTimeSlot_TeacherConflict_Returns409() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张教授", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("物理", "张教授", "B202", "周二 14:00-16:00"))))
                .andExpect(status().isCreated());

        CourseScheduleBatchTimeSlotUpdateRequest request = createTimeSlotUpdateRequest("周一 08:00-10:00", "周二 14:00-16:00");

        mockMvc.perform(put("/api/schedules/batch/time-slot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("张教授")))
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("周二 14:00-16:00")));

        mockMvc.perform(get("/api/schedules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].timeSlot").value("周一 08:00-10:00"))
                .andExpect(jsonPath("$[1].timeSlot").value("周二 14:00-16:00"));
    }

    @Test
    void testBatchUpdateTimeSlot_ClassroomConflict_Returns409() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张教授", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("物理", "李教授", "A101", "周三 09:00-11:00"))))
                .andExpect(status().isCreated());

        CourseScheduleBatchTimeSlotUpdateRequest request = createTimeSlotUpdateRequest("周一 08:00-10:00", "周三 09:00-11:00");

        mockMvc.perform(put("/api/schedules/batch/time-slot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("A101")));
    }

    @Test
    void testBatchUpdateTimeSlot_InvalidFromTimeSlot_Returns400() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张教授", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        CourseScheduleBatchTimeSlotUpdateRequest request = createTimeSlotUpdateRequest("不是合法时间段", "周二 14:00-16:00");

        mockMvc.perform(put("/api/schedules/batch/time-slot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("格式不合法")));
    }

    @Test
    void testBatchUpdateTimeSlot_InvalidToTimeSlot_Returns400() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张教授", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        CourseScheduleBatchTimeSlotUpdateRequest request = createTimeSlotUpdateRequest("周一 08:00-10:00", "随便写");

        mockMvc.perform(put("/api/schedules/batch/time-slot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("格式不合法")));
    }

    @Test
    void testBatchUpdateTimeSlot_EmptyFromTimeSlot_Returns400() throws Exception {
        CourseScheduleBatchTimeSlotUpdateRequest request = createTimeSlotUpdateRequest("", "周二 14:00-16:00");

        mockMvc.perform(put("/api/schedules/batch/time-slot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("原时间段不能为空"));
    }

    @Test
    void testBatchUpdateTimeSlot_EmptyToTimeSlot_Returns400() throws Exception {
        CourseScheduleBatchTimeSlotUpdateRequest request = createTimeSlotUpdateRequest("周一 08:00-10:00", "");

        mockMvc.perform(put("/api/schedules/batch/time-slot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("目标时间段不能为空"));
    }

    @Test
    void testBatchUpdateTimeSlot_BlankFromTimeSlot_Returns400() throws Exception {
        CourseScheduleBatchTimeSlotUpdateRequest request = createTimeSlotUpdateRequest("   ", "周二 14:00-16:00");

        mockMvc.perform(put("/api/schedules/batch/time-slot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("原时间段不能为空"));
    }

    @Test
    void testBatchUpdateTimeSlot_BlankToTimeSlot_Returns400() throws Exception {
        CourseScheduleBatchTimeSlotUpdateRequest request = createTimeSlotUpdateRequest("周一 08:00-10:00", "   ");

        mockMvc.perform(put("/api/schedules/batch/time-slot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("目标时间段不能为空"));
    }

    @Test
    void testBatchUpdateTimeSlot_MissingFromTimeSlot_Returns400() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("toTimeSlot", "周二 14:00-16:00");

        mockMvc.perform(put("/api/schedules/batch/time-slot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("原时间段不能为空"));
    }

    @Test
    void testBatchUpdateTimeSlot_MissingToTimeSlot_Returns400() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("fromTimeSlot", "周一 08:00-10:00");

        mockMvc.perform(put("/api/schedules/batch/time-slot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("目标时间段不能为空"));
    }

    @Test
    void testBatchUpdateTimeSlot_AllFieldsMissing_Returns400() throws Exception {
        Map<String, Object> body = new HashMap<>();

        mockMvc.perform(put("/api/schedules/batch/time-slot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void testBatchUpdateTimeSlot_StartAfterEnd_Returns400() throws Exception {
        CourseScheduleBatchTimeSlotUpdateRequest request = createTimeSlotUpdateRequest("周一 08:00-10:00", "周二 16:00-14:00");

        mockMvc.perform(put("/api/schedules/batch/time-slot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("必须早于")));
    }

    @Test
    void testBatchUpdateTimeSlot_SameTimeSlot_NoChange() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张教授", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        CourseScheduleBatchTimeSlotUpdateRequest request = createTimeSlotUpdateRequest("周一 08:00-10:00", "周一 08:00-10:00");

        mockMvc.perform(put("/api/schedules/batch/time-slot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matchedCount").value(0))
                .andExpect(jsonPath("$.updatedCount").value(0));
    }

    @Test
    void testBatchUpdateTimeSlot_InternalTeacherConflict_Returns409() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张教授", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("物理", "李教授", "B202", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("化学", "张教授", "C303", "周二 14:00-16:00"))))
                .andExpect(status().isCreated());

        CourseScheduleBatchTimeSlotUpdateRequest request = createTimeSlotUpdateRequest("周一 08:00-10:00", "周二 14:00-16:00");

        mockMvc.perform(put("/api/schedules/batch/time-slot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("张教授")));

        mockMvc.perform(get("/api/schedules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    void testUndo_AfterAdd_ReturnsRestoredList() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张教授", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/schedules/undo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());

        mockMvc.perform(get("/api/schedules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void testUndo_AfterUpdate_RestoresPreviousState() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张教授", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        CourseScheduleUpdateRequest updateRequest = createUpdateRequest("高等数学", "李教授", "B202", "周二 14:00-16:00");
        mockMvc.perform(put("/api/schedules/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/schedules/undo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].courseName").value("数学"))
                .andExpect(jsonPath("$[0].teacherName").value("张教授"))
                .andExpect(jsonPath("$[0].classroom").value("A101"));
    }

    @Test
    void testUndo_AfterDelete_RestoresDeletedSchedule() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张教授", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/schedules/1"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/schedules/undo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].courseName").value("数学"));

        mockMvc.perform(get("/api/schedules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void testUndo_AfterBatchAdd_RestoresPreviousState() throws Exception {
        List<CourseScheduleCreateRequest> requests = new java.util.ArrayList<>();
        requests.add(createRequest("数学", "张教授", "A101", "周一 08:00-10:00"));
        requests.add(createRequest("物理", "李教授", "B202", "周二 14:00-16:00"));

        mockMvc.perform(post("/api/schedules/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requests)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/schedules/undo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        mockMvc.perform(get("/api/schedules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void testUndo_AfterBatchDelete_RestoresPreviousState() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张教授", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("物理", "李教授", "B202", "周二 14:00-16:00"))))
                .andExpect(status().isCreated());

        List<Long> ids = new java.util.ArrayList<>();
        ids.add(1L);
        ids.add(2L);
        mockMvc.perform(delete("/api/schedules/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ids)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/schedules/undo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        mockMvc.perform(get("/api/schedules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void testUndo_AfterBatchUpdateTimeSlot_RestoresPreviousState() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张教授", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        CourseScheduleBatchTimeSlotUpdateRequest request = createTimeSlotUpdateRequest("周一 08:00-10:00", "周五 14:00-16:00");
        mockMvc.perform(put("/api/schedules/batch/time-slot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/schedules/undo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].timeSlot").value("周一 08:00-10:00"));

        mockMvc.perform(get("/api/schedules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].timeSlot").value("周一 08:00-10:00"));
    }

    @Test
    void testUndo_NoOperationAvailable_Returns409() throws Exception {
        mockMvc.perform(post("/api/schedules/undo"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("没有可撤销的操作"));
    }

    @Test
    void testUndo_DoubleUndo_Returns409OnSecond() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张教授", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/schedules/undo"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/schedules/undo"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("没有可撤销的操作"));
    }

    @Test
    void testGetClassroomDailySchedule_NormalQuery_ReturnsMatchedList() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("高等数学", "王教授", "A101", "周一 14:00-16:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("线性代数", "李老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("概率论", "张教授", "A101", "周一 10:00-12:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules/classroom-daily")
                        .param("classroom", "A101")
                        .param("weekday", "周一"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].classroom").value("A101"))
                .andExpect(jsonPath("$[1].classroom").value("A101"))
                .andExpect(jsonPath("$[2].classroom").value("A101"))
                .andExpect(jsonPath("$[*].courseName").exists());
    }

    @Test
    void testGetClassroomDailySchedule_ClassroomWithSpaces_TrimmedAndMatched() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("大学英语", "刘老师", "教学楼 301", "周二 09:00-11:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules/classroom-daily")
                        .param("classroom", "  教学楼 301  ")
                        .param("weekday", "周二"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].courseName").value("大学英语"))
                .andExpect(jsonPath("$[0].classroom").value("教学楼 301"));
    }

    @Test
    void testGetClassroomDailySchedule_NoResult_ReturnsEmptyList() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules/classroom-daily")
                        .param("classroom", "A101")
                        .param("weekday", "周二"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void testGetClassroomDailySchedule_EmptyData_ReturnsEmptyList() throws Exception {
        mockMvc.perform(get("/api/schedules/classroom-daily")
                        .param("classroom", "A101")
                        .param("weekday", "周一"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void testGetClassroomDailySchedule_UsesExistingCourseScheduleResponseDto() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("高等数学", "王教授", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules/classroom-daily")
                        .param("classroom", "A101")
                        .param("weekday", "周一"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").exists())
                .andExpect(jsonPath("$[0].courseName").exists())
                .andExpect(jsonPath("$[0].teacherName").exists())
                .andExpect(jsonPath("$[0].classroom").exists())
                .andExpect(jsonPath("$[0].timeSlot").exists());
    }

    @Test
    void testGetClassroomDailySchedule_WrongClassroom_ReturnsEmptyList() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules/classroom-daily")
                        .param("classroom", "B202")
                        .param("weekday", "周一"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void testGetClassroomDailySchedule_NullClassroom_Returns400() throws Exception {
        mockMvc.perform(get("/api/schedules/classroom-daily")
                        .param("weekday", "周一"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testGetClassroomDailySchedule_NullWeekday_Returns400() throws Exception {
        mockMvc.perform(get("/api/schedules/classroom-daily")
                        .param("classroom", "A101"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testGetClassroomDailySchedule_InvalidWeekday_Returns400() throws Exception {
        mockMvc.perform(get("/api/schedules/classroom-daily")
                        .param("classroom", "A101")
                        .param("weekday", "星期一 08:00"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testGetClassroomDailySchedule_EmptyClassroom_Returns400() throws Exception {
        mockMvc.perform(get("/api/schedules/classroom-daily")
                        .param("classroom", "")
                        .param("weekday", "周一"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testGetClassroomDailySchedule_WhitespaceClassroom_Returns400() throws Exception {
        mockMvc.perform(get("/api/schedules/classroom-daily")
                        .param("classroom", "   ")
                        .param("weekday", "周一"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testGetTeacherDailySchedule_NormalQuery_ReturnsMatchedCourses() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("高等数学", "王教授", "A101", "周一 14:00-16:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("线性代数", "王教授", "B202", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("概率论", "王教授", "C303", "周一 10:00-12:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules/teacher-daily")
                        .param("teacherName", "王教授")
                        .param("weekday", "周一"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].teacherName").value("王教授"))
                .andExpect(jsonPath("$[1].teacherName").value("王教授"))
                .andExpect(jsonPath("$[2].teacherName").value("王教授"))
                .andExpect(jsonPath("$[*].courseName").exists());
    }

    @Test
    void testGetTeacherDailySchedule_TeacherNameWithSpaces_TrimmedAndMatched() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("大学英语", "刘老师", "教学楼 301", "周二 09:00-11:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules/teacher-daily")
                        .param("teacherName", "  刘老师  ")
                        .param("weekday", "周二"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].courseName").value("大学英语"));
    }

    @Test
    void testGetTeacherDailySchedule_NoResult_ReturnsEmptyList() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules/teacher-daily")
                        .param("teacherName", "张老师")
                        .param("weekday", "周二"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void testGetTeacherDailySchedule_WrongTeacher_ReturnsEmptyList() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules/teacher-daily")
                        .param("teacherName", "李老师")
                        .param("weekday", "周一"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void testGetTeacherDailySchedule_EmptyData_ReturnsEmptyList() throws Exception {
        mockMvc.perform(get("/api/schedules/teacher-daily")
                        .param("teacherName", "王教授")
                        .param("weekday", "周一"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void testGetTeacherDailySchedule_OnlyFiltersCorrectTeacherAndWeekday() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("课程1", "老师1", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("课程2", "老师1", "A102", "周二 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("课程3", "老师2", "B202", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("课程4", "老师1", "C303", "周一 14:00-16:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules/teacher-daily")
                        .param("teacherName", "老师1")
                        .param("weekday", "周一"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void testGetTeacherDailySchedule_NullTeacherName_Returns400() throws Exception {
        mockMvc.perform(get("/api/schedules/teacher-daily")
                        .param("weekday", "周一"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testGetTeacherDailySchedule_NullWeekday_Returns400() throws Exception {
        mockMvc.perform(get("/api/schedules/teacher-daily")
                        .param("teacherName", "张老师"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testGetTeacherDailySchedule_InvalidWeekday_Returns400() throws Exception {
        mockMvc.perform(get("/api/schedules/teacher-daily")
                        .param("teacherName", "张老师")
                        .param("weekday", "星期一 08:00"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testGetTeacherDailySchedule_EmptyTeacherName_Returns400() throws Exception {
        mockMvc.perform(get("/api/schedules/teacher-daily")
                        .param("teacherName", "")
                        .param("weekday", "周一"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testGetTeacherDailySchedule_WhitespaceTeacherName_Returns400() throws Exception {
        mockMvc.perform(get("/api/schedules/teacher-daily")
                        .param("teacherName", "   ")
                        .param("weekday", "周一"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testGetSchedules_ByWeekdayOnly_ReturnsMatchingCourses() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张教授", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("物理", "李教授", "B202", "周二 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("化学", "王教授", "C303", "周一 14:00-16:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules")
                        .param("weekday", "周一"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].courseName").value("数学"))
                .andExpect(jsonPath("$[1].courseName").value("化学"));
    }

    @Test
    void testGetSchedules_ByFullTimeRange_ReturnsMatchingCourses() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张教授", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("物理", "李教授", "B202", "周一 10:00-12:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("化学", "王教授", "C303", "周一 14:00-16:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("生物", "赵教授", "D404", "周二 09:00-11:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules")
                        .param("weekday", "周一")
                        .param("startTimeFrom", "08:00")
                        .param("startTimeTo", "12:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].courseName").value("数学"))
                .andExpect(jsonPath("$[1].courseName").value("物理"));
    }

    @Test
    void testGetSchedules_ByTimeRangeWithWeekdayNumber_WorksCorrectly() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张教授", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("物理", "李教授", "B202", "周二 08:00-10:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules")
                        .param("weekday", "1")
                        .param("startTimeFrom", "08:00")
                        .param("startTimeTo", "12:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].courseName").value("数学"));
    }

    @Test
    void testGetSchedules_ByStartTimeFromOnly_ReturnsMatchingCourses() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张教授", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("物理", "李教授", "B202", "周一 10:00-12:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("化学", "王教授", "C303", "周一 07:00-09:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules")
                        .param("weekday", "周一")
                        .param("startTimeFrom", "08:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].courseName").value("数学"))
                .andExpect(jsonPath("$[1].courseName").value("物理"));
    }

    @Test
    void testGetSchedules_ByStartTimeToOnly_ReturnsMatchingCourses() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张教授", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("物理", "李教授", "B202", "周一 14:00-16:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules")
                        .param("weekday", "周一")
                        .param("startTimeTo", "12:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].courseName").value("数学"));
    }

    @Test
    void testGetSchedules_TimeRangeCombinedWithOtherFilters_WorksCorrectly() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("高等数学", "张教授", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("线性代数", "张教授", "A102", "周一 10:00-12:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("大学物理", "李教授", "B202", "周一 09:00-11:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules")
                        .param("weekday", "周一")
                        .param("startTimeFrom", "08:00")
                        .param("startTimeTo", "12:00")
                        .param("teacherName", "张"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].courseName").value("高等数学"))
                .andExpect(jsonPath("$[1].courseName").value("线性代数"));
    }

    @Test
    void testGetSchedules_TimeRangeBoundary_StartTimeEqualsFrom_ReturnsMatch() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张教授", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules")
                        .param("weekday", "周一")
                        .param("startTimeFrom", "08:00")
                        .param("startTimeTo", "08:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].courseName").value("数学"));
    }

    @Test
    void testGetSchedules_TimeRangeNoMatch_ReturnsEmpty() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张教授", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules")
                        .param("weekday", "周二")
                        .param("startTimeFrom", "08:00")
                        .param("startTimeTo", "12:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void testGetSchedules_TimeRangeWithSorting_PreservesSortOrder() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("物理", "李教授", "B202", "周一 10:00-12:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张教授", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("化学", "王教授", "C303", "周一 09:00-11:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules")
                        .param("weekday", "周一")
                        .param("startTimeFrom", "08:00")
                        .param("startTimeTo", "12:00")
                        .param("sortBy", "courseName")
                        .param("sortDirection", "asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].courseName").value("化学"))
                .andExpect(jsonPath("$[1].courseName").value("数学"))
                .andExpect(jsonPath("$[2].courseName").value("物理"));
    }

    @Test
    void testGetSchedules_InvalidTimePointFormat_Returns400() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张教授", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules")
                        .param("weekday", "周一")
                        .param("startTimeFrom", "invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("格式不合法")));
    }

    @Test
    void testExportSchedules_WithTimeRangeFilter_ExportsMatchingCourses() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张教授", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("物理", "李教授", "B202", "周一 14:00-16:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("化学", "王教授", "C303", "周二 08:00-10:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules/export")
                        .param("weekday", "周一")
                        .param("startTimeFrom", "08:00")
                        .param("startTimeTo", "12:00"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("text/csv")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("数学")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("物理"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("化学"))));
    }

    @Test
    void testGetSchedules_BlankTimeRangeParams_NoEffect() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张教授", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("物理", "李教授", "B202", "周二 08:00-10:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules")
                        .param("weekday", "   ")
                        .param("startTimeFrom", "   ")
                        .param("startTimeTo", "   "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void testGetSchedules_StartTimeFromGreaterThanTo_Returns400() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张教授", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules")
                        .param("weekday", "周一")
                        .param("startTimeFrom", "12:00")
                        .param("startTimeTo", "08:00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("startTimeFrom 必须早于或等于 startTimeTo")))
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("12:00")))
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("08:00")));
    }

    @Test
    void testExportSchedules_StartTimeFromGreaterThanTo_Returns400() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张教授", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules/export")
                        .param("weekday", "周一")
                        .param("startTimeFrom", "12:00")
                        .param("startTimeTo", "08:00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("startTimeFrom 必须早于或等于 startTimeTo")));
    }

    @Test
    void testGetSchedules_EqualStartAndEndTime_Allowed() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张教授", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("物理", "李教授", "B202", "周一 09:00-11:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules")
                        .param("weekday", "周一")
                        .param("startTimeFrom", "08:00")
                        .param("startTimeTo", "08:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].courseName").value("数学"));
    }

    @Test
    void testGetSchedules_TimeRangeWithOtherFilters_CombinedCorrectly() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张教授", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("物理", "张教授", "A102", "周一 10:00-12:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("化学", "李教授", "B202", "周一 14:00-16:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules")
                        .param("weekday", "周一")
                        .param("startTimeFrom", "08:00")
                        .param("startTimeTo", "12:00")
                        .param("teacherName", "张"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].teacherName").value("张教授"))
                .andExpect(jsonPath("$[1].teacherName").value("张教授"));
    }

    @Test
    void testGetTeacherFreeTime_TeacherHasCourses_PartiallyAvailable() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张教授", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("物理", "张教授", "B202", "周一 14:00-16:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules/teacher-free-time")
                        .param("teacherName", "张教授")
                        .param("weekday", "周一")
                        .param("startTimeFrom", "08:00")
                        .param("startTimeTo", "18:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teacherName").value("张教授"))
                .andExpect(jsonPath("$.weekday").value("周一"))
                .andExpect(jsonPath("$.fullyAvailable").value(false))
                .andExpect(jsonPath("$.occupiedSlots.length()").value(2))
                .andExpect(jsonPath("$.occupiedSlots[0].courseName").value("数学"))
                .andExpect(jsonPath("$.occupiedSlots[1].courseName").value("物理"))
                .andExpect(jsonPath("$.freeTimeWindows.length()").value(2))
                .andExpect(jsonPath("$.freeTimeWindows[0].startTime").value("10:00"))
                .andExpect(jsonPath("$.freeTimeWindows[0].endTime").value("14:00"));
    }

    @Test
    void testGetTeacherFreeTime_TeacherNoCourses_FullyAvailable() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张教授", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules/teacher-free-time")
                        .param("teacherName", "李教授")
                        .param("weekday", "周一")
                        .param("startTimeFrom", "08:00")
                        .param("startTimeTo", "16:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullyAvailable").value(true))
                .andExpect(jsonPath("$.occupiedSlots").isEmpty())
                .andExpect(jsonPath("$.freeTimeWindows.length()").value(1))
                .andExpect(jsonPath("$.freeTimeWindows[0].startTime").value("08:00"))
                .andExpect(jsonPath("$.freeTimeWindows[0].endTime").value("16:00"));
    }

    @Test
    void testGetTeacherFreeTime_MissingTeacherName_Returns400() throws Exception {
        mockMvc.perform(get("/api/schedules/teacher-free-time")
                        .param("weekday", "周一")
                        .param("startTimeFrom", "08:00")
                        .param("startTimeTo", "16:00"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testGetTeacherFreeTime_MissingWeekday_Returns400() throws Exception {
        mockMvc.perform(get("/api/schedules/teacher-free-time")
                        .param("teacherName", "张教授")
                        .param("startTimeFrom", "08:00")
                        .param("startTimeTo", "16:00"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testGetTeacherFreeTime_MissingStartTimeFrom_Returns400() throws Exception {
        mockMvc.perform(get("/api/schedules/teacher-free-time")
                        .param("teacherName", "张教授")
                        .param("weekday", "周一")
                        .param("startTimeTo", "16:00"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testGetTeacherFreeTime_MissingStartTimeTo_Returns400() throws Exception {
        mockMvc.perform(get("/api/schedules/teacher-free-time")
                        .param("teacherName", "张教授")
                        .param("weekday", "周一")
                        .param("startTimeFrom", "08:00"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testGetTeacherFreeTime_InvalidWeekday_Returns400() throws Exception {
        mockMvc.perform(get("/api/schedules/teacher-free-time")
                        .param("teacherName", "张教授")
                        .param("weekday", "周八")
                        .param("startTimeFrom", "08:00")
                        .param("startTimeTo", "16:00"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testGetTeacherFreeTime_InvalidTimeFormat_Returns400() throws Exception {
        mockMvc.perform(get("/api/schedules/teacher-free-time")
                        .param("teacherName", "张教授")
                        .param("weekday", "周一")
                        .param("startTimeFrom", "abc")
                        .param("startTimeTo", "16:00"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testGetTeacherFreeTime_StartAfterEnd_Returns400() throws Exception {
        mockMvc.perform(get("/api/schedules/teacher-free-time")
                        .param("teacherName", "张教授")
                        .param("weekday", "周一")
                        .param("startTimeFrom", "16:00")
                        .param("startTimeTo", "08:00"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testGetTeacherFreeTime_NormalizedWeekdayInput() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张教授", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules/teacher-free-time")
                        .param("teacherName", "张教授")
                        .param("weekday", "星期一")
                        .param("startTimeFrom", "08:00")
                        .param("startTimeTo", "16:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weekday").value("周一"))
                .andExpect(jsonPath("$.occupiedSlots.length()").value(1));
    }

    @Test
    void testExportTeacherFreeTime_HasCourses_ReturnsCsv() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张教授", "A101", "周一 10:00-12:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules/teacher-free-time/export")
                        .param("teacherName", "张教授")
                        .param("weekday", "周一")
                        .param("startTimeFrom", "08:00")
                        .param("startTimeTo", "16:00"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("text/csv")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("张教授")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("数学")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("有课")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("空闲")));
    }

    @Test
    void testExportTeacherFreeTime_FullyAvailable_ReturnsCsv() throws Exception {
        mockMvc.perform(get("/api/schedules/teacher-free-time/export")
                        .param("teacherName", "张教授")
                        .param("weekday", "周一")
                        .param("startTimeFrom", "08:00")
                        .param("startTimeTo", "16:00"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("text/csv")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("空闲")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("有课"))));
    }

    @Test
    void testExportTeacherFreeTime_MissingParams_Returns400() throws Exception {
        mockMvc.perform(get("/api/schedules/teacher-free-time/export")
                        .param("weekday", "周一")
                        .param("startTimeFrom", "08:00")
                        .param("startTimeTo", "16:00"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testExportTeacherFreeTime_ChronologicalOrder() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张教授", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("物理", "张教授", "B202", "周一 14:00-16:00"))))
                .andExpect(status().isCreated());

        String csvContent = mockMvc.perform(get("/api/schedules/teacher-free-time/export")
                        .param("teacherName", "张教授")
                        .param("weekday", "周一")
                        .param("startTimeFrom", "08:00")
                        .param("startTimeTo", "18:00"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String[] lines = csvContent.split("\n");
        java.util.List<String> dataLines = new java.util.ArrayList<>();
        for (String line : lines) {
            if (!line.trim().isEmpty() && !line.contains("时间段类型")) {
                dataLines.add(line);
            }
        }

        assertEquals(4, dataLines.size());
        assertTrue(dataLines.get(0).contains("有课"));
        assertTrue(dataLines.get(0).contains("08:00"));
        assertTrue(dataLines.get(1).contains("空闲"));
        assertTrue(dataLines.get(1).contains("10:00"));
        assertTrue(dataLines.get(2).contains("有课"));
        assertTrue(dataLines.get(2).contains("14:00"));
        assertTrue(dataLines.get(3).contains("空闲"));
        assertTrue(dataLines.get(3).contains("16:00"));
    }

    @Test
    void testExportTeacherFreeTime_ChronologicalOrder_FreeFirst() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张教授", "A101", "周一 10:00-12:00"))))
                .andExpect(status().isCreated());

        String csvContent = mockMvc.perform(get("/api/schedules/teacher-free-time/export")
                        .param("teacherName", "张教授")
                        .param("weekday", "周一")
                        .param("startTimeFrom", "08:00")
                        .param("startTimeTo", "16:00"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String[] lines = csvContent.split("\n");
        java.util.List<String> dataLines = new java.util.ArrayList<>();
        for (String line : lines) {
            if (!line.trim().isEmpty() && !line.contains("时间段类型")) {
                dataLines.add(line);
            }
        }

        assertEquals(3, dataLines.size());
        assertTrue(dataLines.get(0).contains("空闲"));
        assertTrue(dataLines.get(0).contains("08:00"));
        assertTrue(dataLines.get(1).contains("有课"));
        assertTrue(dataLines.get(1).contains("10:00"));
        assertTrue(dataLines.get(2).contains("空闲"));
        assertTrue(dataLines.get(2).contains("12:00"));
    }

    @Test
    void testGetAuditLogs_EmptyList() throws Exception {
        mockMvc.perform(get("/api/schedules/audit-logs"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void testGetAuditLogs_AfterCreateSchedule() throws Exception {
        CourseScheduleCreateRequest request = createRequest("数学", "张教授", "A101", "周一 08:00-10:00");

        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules/audit-logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].operationType").value("CREATE"))
                .andExpect(jsonPath("$[0].courseId").value(1))
                .andExpect(jsonPath("$[0].courseName").value("数学"))
                .andExpect(jsonPath("$[0].teacherName").value("张教授"))
                .andExpect(jsonPath("$[0].classroom").value("A101"))
                .andExpect(jsonPath("$[0].timeSlot").value("周一 08:00-10:00"))
                .andExpect(jsonPath("$[0].success").value(true))
                .andExpect(jsonPath("$[0].timestamp").exists());
    }

    @Test
    void testGetAuditLogs_FilterByOperationType() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张教授", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("物理", "李教授", "B202", "周二 14:00-16:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/schedules/1"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/schedules/audit-logs")
                        .param("operationType", "DELETE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].operationType").value("DELETE"))
                .andExpect(jsonPath("$[0].courseId").value(1));
    }

    @Test
    void testGetAuditLogs_FilterBySuccess_True() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张教授", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("物理", "张教授", "B202", "周一 08:00-10:00"))))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/schedules/audit-logs")
                        .param("success", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].success").value(true))
                .andExpect(jsonPath("$[0].courseName").value("数学"));
    }

    @Test
    void testGetAuditLogs_FilterBySuccess_False() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张教授", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("物理", "张教授", "B202", "周一 08:00-10:00"))))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/schedules/audit-logs")
                        .param("success", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].success").value(false))
                .andExpect(jsonPath("$[0].courseName").value("物理"))
                .andExpect(jsonPath("$[0].errorMessage").exists());
    }

    @Test
    void testGetAuditLogs_FilterByOperationTypeAndSuccess() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张教授", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/schedules/999"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/schedules/audit-logs")
                        .param("operationType", "DELETE")
                        .param("success", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].operationType").value("DELETE"))
                .andExpect(jsonPath("$[0].success").value(false))
                .andExpect(jsonPath("$[0].courseId").value(999));
    }

    @Test
    void testGetAuditLogs_IncludesFailureLogs() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张教授", "A101", "非法时间段"))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/schedules/audit-logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].operationType").value("CREATE"))
                .andExpect(jsonPath("$[0].success").value(false))
                .andExpect(jsonPath("$[0].courseName").value("数学"))
                .andExpect(jsonPath("$[0].errorMessage").exists());
    }

    @Test
    void testGetAuditLogs_BatchOperations_RecordedPerCourse() throws Exception {
        List<CourseScheduleCreateRequest> batchRequests = new java.util.ArrayList<>();
        batchRequests.add(createRequest("数学", "张教授", "A101", "周一 08:00-10:00"));
        batchRequests.add(createRequest("", "李教授", "B202", "周二 14:00-16:00"));
        batchRequests.add(createRequest("化学", "张教授", "A101", "周一 08:00-10:00"));

        mockMvc.perform(post("/api/schedules/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(batchRequests)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/schedules/audit-logs")
                        .param("operationType", "BATCH_CREATE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    void testGetAuditLogs_UndoOperation_Recorded() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张教授", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/schedules/undo"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/schedules/audit-logs")
                        .param("operationType", "UNDO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].operationType").value("UNDO"))
                .andExpect(jsonPath("$[0].success").value(true));
    }

    @Test
    void testGetAuditLogs_InvalidOperationType_ReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/schedules/audit-logs")
                        .param("operationType", "INVALID_TYPE"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testGetAuditLogs_OrderedByTimestampDescending() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张教授", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        TestClockConfig.TEST_CLOCK.advanceSeconds(60);

        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("物理", "李教授", "B202", "周二 14:00-16:00"))))
                .andExpect(status().isCreated());

        TestClockConfig.TEST_CLOCK.advanceSeconds(60);

        mockMvc.perform(delete("/api/schedules/1"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/schedules/audit-logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].operationType").value("DELETE"))
                .andExpect(jsonPath("$[1].operationType").value("CREATE"))
                .andExpect(jsonPath("$[1].courseName").value("物理"))
                .andExpect(jsonPath("$[2].operationType").value("CREATE"))
                .andExpect(jsonPath("$[2].courseName").value("数学"));
    }

    @Test
    void testGetAuditLogs_UpdateOperation_Recorded() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张教授", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        CourseScheduleUpdateRequest updateRequest = new CourseScheduleUpdateRequest();
        updateRequest.setCourseName("高等数学");
        updateRequest.setTeacherName("李教授");
        updateRequest.setClassroom("B202");
        updateRequest.setTimeSlot("周二 14:00-16:00");

        mockMvc.perform(put("/api/schedules/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/schedules/audit-logs")
                        .param("operationType", "UPDATE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].operationType").value("UPDATE"))
                .andExpect(jsonPath("$[0].courseId").value(1))
                .andExpect(jsonPath("$[0].courseName").value("高等数学"))
                .andExpect(jsonPath("$[0].teacherName").value("李教授"))
                .andExpect(jsonPath("$[0].classroom").value("B202"))
                .andExpect(jsonPath("$[0].timeSlot").value("周二 14:00-16:00"))
                .andExpect(jsonPath("$[0].success").value(true));
    }

    @Test
    void testGetAuditLogs_BatchCreate_NullList_RecordsFailure() throws Exception {
        mockMvc.perform(post("/api/schedules/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(null)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/schedules/audit-logs")
                        .param("operationType", "BATCH_CREATE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].operationType").value("BATCH_CREATE"))
                .andExpect(jsonPath("$[0].success").value(false))
                .andExpect(jsonPath("$[0].errorMessage").value("请求列表不能为空"));
    }

    @Test
    void testGetAuditLogs_BatchUpdateTimeSlot_NullRequest_RecordsFailure() throws Exception {
        mockMvc.perform(put("/api/schedules/batch/time-slot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(null)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/schedules/audit-logs")
                        .param("operationType", "BATCH_UPDATE_TIME_SLOT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].operationType").value("BATCH_UPDATE_TIME_SLOT"))
                .andExpect(jsonPath("$[0].success").value(false))
                .andExpect(jsonPath("$[0].errorMessage").value("请求不能为空"));
    }

    @Test
    void testGetAuditLogs_BatchUpdateTimeSlot_EmptyFromTimeSlot_RecordsFailure() throws Exception {
        CourseScheduleBatchTimeSlotUpdateRequest request = new CourseScheduleBatchTimeSlotUpdateRequest();
        request.setFromTimeSlot("");
        request.setToTimeSlot("周二 14:00-16:00");

        mockMvc.perform(put("/api/schedules/batch/time-slot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/schedules/audit-logs")
                        .param("operationType", "BATCH_UPDATE_TIME_SLOT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].operationType").value("BATCH_UPDATE_TIME_SLOT"))
                .andExpect(jsonPath("$[0].success").value(false))
                .andExpect(jsonPath("$[0].errorMessage").value("原时间段不能为空"));
    }

    @Test
    void testGetAuditLogs_BatchUpdateTimeSlot_InvalidFromTimeSlot_RecordsFailure() throws Exception {
        CourseScheduleBatchTimeSlotUpdateRequest request = new CourseScheduleBatchTimeSlotUpdateRequest();
        request.setFromTimeSlot("非法时间段");
        request.setToTimeSlot("周二 14:00-16:00");

        mockMvc.perform(put("/api/schedules/batch/time-slot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/schedules/audit-logs")
                        .param("operationType", "BATCH_UPDATE_TIME_SLOT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].operationType").value("BATCH_UPDATE_TIME_SLOT"))
                .andExpect(jsonPath("$[0].success").value(false))
                .andExpect(jsonPath("$[0].timeSlot").value("非法时间段"))
                .andExpect(jsonPath("$[0].errorMessage").exists());
    }

    @Test
    void testGetAuditLogs_BatchUpdateTimeSlot_SameFromAndTo_RecordsFailure() throws Exception {
        CourseScheduleBatchTimeSlotUpdateRequest request = new CourseScheduleBatchTimeSlotUpdateRequest();
        request.setFromTimeSlot("周一 08:00-10:00");
        request.setToTimeSlot("周一 08:00-10:00");

        mockMvc.perform(put("/api/schedules/batch/time-slot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/schedules/audit-logs")
                        .param("operationType", "BATCH_UPDATE_TIME_SLOT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].operationType").value("BATCH_UPDATE_TIME_SLOT"))
                .andExpect(jsonPath("$[0].success").value(false))
                .andExpect(jsonPath("$[0].errorMessage").value("原时间段与目标时间段相同"));
    }

    @Test
    void testGetAuditLogs_BatchUpdateTimeSlot_NoMatch_RecordsFailure() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("数学", "张教授", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        CourseScheduleBatchTimeSlotUpdateRequest request = new CourseScheduleBatchTimeSlotUpdateRequest();
        request.setFromTimeSlot("周二 10:00-12:00");
        request.setToTimeSlot("周三 14:00-16:00");

        mockMvc.perform(put("/api/schedules/batch/time-slot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/schedules/audit-logs")
                        .param("operationType", "BATCH_UPDATE_TIME_SLOT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].operationType").value("BATCH_UPDATE_TIME_SLOT"))
                .andExpect(jsonPath("$[0].success").value(false))
                .andExpect(jsonPath("$[0].errorMessage").value("没有匹配的课程"))
                .andExpect(jsonPath("$[0].timeSlot").value("周二 10:00-12:00"));

        mockMvc.perform(get("/api/schedules/audit-logs")
                        .param("operationType", "BATCH_UPDATE_TIME_SLOT")
                        .param("success", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(get("/api/schedules/audit-logs")
                        .param("operationType", "BATCH_UPDATE_TIME_SLOT")
                        .param("success", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void testGetAuditLogs_DeleteOperation_RecordsFailure() throws Exception {
        mockMvc.perform(delete("/api/schedules/999"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/schedules/audit-logs")
                        .param("operationType", "DELETE")
                        .param("success", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].operationType").value("DELETE"))
                .andExpect(jsonPath("$[0].courseId").value(999))
                .andExpect(jsonPath("$[0].success").value(false))
                .andExpect(jsonPath("$[0].errorMessage").exists());
    }

    @Test
    void testGetChangeSummary_ReturnsSummaryDTO_NoAuditFields() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules/change-summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].operationType").value("CREATE"))
                .andExpect(jsonPath("$[0].courseName").value("数学"))
                .andExpect(jsonPath("$[0].teacherName").value("张老师"))
                .andExpect(jsonPath("$[0].classroom").value("A101"))
                .andExpect(jsonPath("$[0].timeSlot").value("周一 08:00-10:00"))
                .andExpect(jsonPath("$[0].timestamp").exists())
                .andExpect(jsonPath("$[0].id").doesNotExist())
                .andExpect(jsonPath("$[0].courseId").doesNotExist())
                .andExpect(jsonPath("$[0].success").doesNotExist())
                .andExpect(jsonPath("$[0].errorMessage").doesNotExist());
    }

    @Test
    void testGetChangeSummary_ExcludesFailureLogs_OnlySuccessReturned() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("物理", "张老师", "B202", "周一 08:00-10:00"))))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/schedules/change-summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].courseName").value("数学"));
    }

    @Test
    void testGetChangeSummary_InvalidLimit_Returns400() throws Exception {
        mockMvc.perform(get("/api/schedules/change-summary")
                        .param("limit", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("limit 必须为正整数，当前值: 0"));

        mockMvc.perform(get("/api/schedules/change-summary")
                        .param("limit", "51"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("limit 最大值为 50，当前值: 51"));

        mockMvc.perform(get("/api/schedules/change-summary")
                        .param("limit", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void testGetChangeSummary_EmptyData_ReturnsEmptyArray() throws Exception {
        mockMvc.perform(get("/api/schedules/change-summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void testGetChangeSummary_MultipleOps_OrderedByTimeDescending() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());
        TestClockConfig.TEST_CLOCK.advanceSeconds(60);
        mockMvc.perform(put("/api/schedules/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createUpdateRequest("高等数学", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isOk());
        TestClockConfig.TEST_CLOCK.advanceSeconds(60);
        mockMvc.perform(delete("/api/schedules/1"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/schedules/change-summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].operationType").value("DELETE"))
                .andExpect(jsonPath("$[1].operationType").value("UPDATE"))
                .andExpect(jsonPath("$[2].operationType").value("CREATE"));
    }

    @Test
    void testGetChangeSummaryByCourse_EmptyData_ReturnsEmptyList() throws Exception {
        mockMvc.perform(get("/api/schedules/change-summary/by-course"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void testGetChangeSummaryByCourse_AfterCreateUpdateDelete_CorrectCounts() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("数学", "张教授", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());
        TestClockConfig.TEST_CLOCK.advanceSeconds(60);
        mockMvc.perform(put("/api/schedules/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createUpdateRequest("数学", "张教授", "B102", "周一 10:00-12:00"))))
                .andExpect(status().isOk());
        TestClockConfig.TEST_CLOCK.advanceSeconds(60);
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("物理", "李教授", "B202", "周二 14:00-16:00"))))
                .andExpect(status().isCreated());
        TestClockConfig.TEST_CLOCK.advanceSeconds(60);
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("物理", "李教授", "A101", "周二 14:00-16:00"))))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/schedules/change-summary/by-course"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].courseName").value("数学"))
                .andExpect(jsonPath("$[0].changeCount").value(2))
                .andExpect(jsonPath("$[1].courseName").value("物理"))
                .andExpect(jsonPath("$[1].changeCount").value(1));
    }

    @Test
    void testGetChangeSummaryByCourse_Sorting_CountDescThenCourseNameAsc() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("语文", "老师A", "教室A", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());
        TestClockConfig.TEST_CLOCK.advanceSeconds(1);
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("数学", "老师B", "教室B", "周二 10:00-12:00"))))
                .andExpect(status().isCreated());
        TestClockConfig.TEST_CLOCK.advanceSeconds(1);
        mockMvc.perform(put("/api/schedules/2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createUpdateRequest("数学", "老师B", "教室B2", "周二 10:00-12:00"))))
                .andExpect(status().isOk());
        TestClockConfig.TEST_CLOCK.advanceSeconds(1);
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("英语", "老师C", "教室C", "周三 14:00-16:00"))))
                .andExpect(status().isCreated());
        TestClockConfig.TEST_CLOCK.advanceSeconds(1);
        mockMvc.perform(put("/api/schedules/3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createUpdateRequest("英语", "老师C", "教室C2", "周三 14:00-16:00"))))
                .andExpect(status().isOk());
        TestClockConfig.TEST_CLOCK.advanceSeconds(1);
        mockMvc.perform(delete("/api/schedules/3"))
                .andExpect(status().isOk());
        TestClockConfig.TEST_CLOCK.advanceSeconds(1);
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("物理", "老师D", "教室D", "周四 09:00-11:00"))))
                .andExpect(status().isCreated());
        TestClockConfig.TEST_CLOCK.advanceSeconds(1);
        mockMvc.perform(put("/api/schedules/4")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createUpdateRequest("物理", "老师D", "教室D2", "周四 09:00-11:00"))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/schedules/change-summary/by-course"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].courseName").value("英语"))
                .andExpect(jsonPath("$[0].changeCount").value(3))
                .andExpect(jsonPath("$[1].courseName").value("数学"))
                .andExpect(jsonPath("$[1].changeCount").value(2))
                .andExpect(jsonPath("$[2].courseName").value("物理"))
                .andExpect(jsonPath("$[2].changeCount").value(2))
                .andExpect(jsonPath("$[3].courseName").value("语文"))
                .andExpect(jsonPath("$[3].changeCount").value(1));
    }

    @Test
    void testGetChangeSummaryByCourse_UndoWithNullCourseName_Skipped() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("数学", "张教授", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());
        TestClockConfig.TEST_CLOCK.advanceSeconds(60);
        mockMvc.perform(post("/api/schedules/undo"))
                .andExpect(status().isOk());
        TestClockConfig.TEST_CLOCK.advanceSeconds(60);
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("物理", "李教授", "B202", "周二 14:00-16:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules/change-summary/by-course"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void testGetChangeSummaryByOperation_EmptyData_ReturnsEmptyList() throws Exception {
        mockMvc.perform(get("/api/schedules/change-summary/by-operation"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void testGetChangeSummaryByOperation_MixedSuccessFailure_CorrectCounts() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("数学", "张教授", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("物理", "李教授", "B202", "周二 14:00-16:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("化学", "张教授", "C303", "周一 08:00-10:00"))))
                .andExpect(status().isConflict());

        mockMvc.perform(delete("/api/schedules/1"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/schedules/change-summary/by-operation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].operationType").value("CREATE"))
                .andExpect(jsonPath("$[0].successCount").value(2))
                .andExpect(jsonPath("$[0].failureCount").value(1))
                .andExpect(jsonPath("$[0].totalCount").value(3))
                .andExpect(jsonPath("$[1].operationType").value("DELETE"))
                .andExpect(jsonPath("$[1].successCount").value(1))
                .andExpect(jsonPath("$[1].failureCount").value(0))
                .andExpect(jsonPath("$[1].totalCount").value(1));
    }

    @Test
    void testGetChangeSummaryByOperation_SortedByTotalCountDescThenNameAsc() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("数学", "张教授", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("物理", "李教授", "B202", "周二 14:00-16:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("化学", "王教授", "C303", "周三 10:00-12:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(put("/api/schedules/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createUpdateRequest("高等数学", "张教授", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/schedules/2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createUpdateRequest("大学物理", "李教授", "B202", "周二 14:00-16:00"))))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/schedules/3"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/schedules/change-summary/by-operation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].operationType").value("CREATE"))
                .andExpect(jsonPath("$[0].totalCount").value(3))
                .andExpect(jsonPath("$[1].operationType").value("UPDATE"))
                .andExpect(jsonPath("$[1].totalCount").value(2))
                .andExpect(jsonPath("$[2].operationType").value("DELETE"))
                .andExpect(jsonPath("$[2].totalCount").value(1));
    }

    @Test
    void testGetClassroomWeeklySchedule_ReturnsSchedules() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张老师", "A101", "周一 10:00-12:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("物理", "李老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules/classroom-weekly")
                        .param("classroom", "A101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void testGetClassroomWeeklySchedule_EmptyResult() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules/classroom-weekly")
                        .param("classroom", "B202"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void testGetClassroomWeeklySchedule_NoDataAtAll() throws Exception {
        mockMvc.perform(get("/api/schedules/classroom-weekly")
                        .param("classroom", "A101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void testGetClassroomWeeklySchedule_MissingClassroomParam_Returns400() throws Exception {
        mockMvc.perform(get("/api/schedules/classroom-weekly"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testGetClassroomWeeklySchedule_EmptyClassroom_Returns400() throws Exception {
        mockMvc.perform(get("/api/schedules/classroom-weekly")
                        .param("classroom", ""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testGetClassroomWeeklySchedule_BlankClassroom_Returns400() throws Exception {
        mockMvc.perform(get("/api/schedules/classroom-weekly")
                        .param("classroom", "   "))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testGetClassroomWeeklySchedule_TrimmedClassroom() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules/classroom-weekly")
                        .param("classroom", "  A101  "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].courseName").value("数学"));
    }

    @Test
    void testGetClassroomWeeklySchedule_ResponseStructure() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules/classroom-weekly")
                        .param("classroom", "A101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").exists())
                .andExpect(jsonPath("$[0].courseName").value("数学"))
                .andExpect(jsonPath("$[0].teacherName").value("张老师"))
                .andExpect(jsonPath("$[0].classroom").value("A101"))
                .andExpect(jsonPath("$[0].timeSlot").value("周一 08:00-10:00"));
    }

    @Test
    void testGetTeacherWeeklySchedule_ReturnsSchedules() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张老师", "A101", "周一 10:00-12:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("物理", "张老师", "B202", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules/teacher-weekly")
                        .param("teacherName", "张老师"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].teacherName").value("张老师"))
                .andExpect(jsonPath("$[0].courseName").value("物理"))
                .andExpect(jsonPath("$[0].classroom").value("B202"))
                .andExpect(jsonPath("$[0].timeSlot").value("周一 08:00-10:00"))
                .andExpect(jsonPath("$[1].teacherName").value("张老师"))
                .andExpect(jsonPath("$[1].courseName").value("数学"))
                .andExpect(jsonPath("$[1].classroom").value("A101"))
                .andExpect(jsonPath("$[1].timeSlot").value("周一 10:00-12:00"));
    }

    @Test
    void testGetTeacherWeeklySchedule_EmptyResult() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules/teacher-weekly")
                        .param("teacherName", "李老师"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void testGetTeacherWeeklySchedule_NoDataAtAll() throws Exception {
        mockMvc.perform(get("/api/schedules/teacher-weekly")
                        .param("teacherName", "张老师"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void testGetTeacherWeeklySchedule_MissingTeacherNameParam_Returns400() throws Exception {
        mockMvc.perform(get("/api/schedules/teacher-weekly"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testGetTeacherWeeklySchedule_EmptyTeacherName_Returns400() throws Exception {
        mockMvc.perform(get("/api/schedules/teacher-weekly")
                        .param("teacherName", ""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testGetTeacherWeeklySchedule_BlankTeacherName_Returns400() throws Exception {
        mockMvc.perform(get("/api/schedules/teacher-weekly")
                        .param("teacherName", "   "))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testGetTeacherWeeklySchedule_TrimmedTeacherName() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules/teacher-weekly")
                        .param("teacherName", "  张老师  "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].courseName").value("数学"));
    }

    @Test
    void testGetTeacherWeeklySchedule_ResponseStructure() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules/teacher-weekly")
                        .param("teacherName", "张老师"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").exists())
                .andExpect(jsonPath("$[0].courseName").value("数学"))
                .andExpect(jsonPath("$[0].teacherName").value("张老师"))
                .andExpect(jsonPath("$[0].classroom").value("A101"))
                .andExpect(jsonPath("$[0].timeSlot").value("周一 08:00-10:00"));
    }

    @Test
    void testExportTeacherWeeklySchedule_ExportOrder_WeekdayThenStartTime() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("周日课", "张老师", "A101", "周日 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("周一下午", "张老师", "B202", "周一 14:00-16:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("周一上午", "张老师", "C303", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("周三课", "张老师", "D404", "周三 10:00-12:00"))))
                .andExpect(status().isCreated());

        byte[] result = mockMvc.perform(get("/api/schedules/teacher-weekly/export")
                        .param("teacherName", "张老师"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("text/csv")))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        String csv = new String(result, java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(csv.startsWith("\uFEFF"));
        String csvWithoutBom = csv.startsWith("\uFEFF") ? csv.substring(1) : csv;
        String[] lines = csvWithoutBom.split("\n");

        assertEquals("课程名称,老师,教室,时间段", lines[0].trim());
        assertEquals(5, lines.length);
        assertTrue(lines[1].contains("周一上午"));
        assertTrue(lines[1].contains("周一 08:00-10:00"));
        assertTrue(lines[2].contains("周一下午"));
        assertTrue(lines[2].contains("周一 14:00-16:00"));
        assertTrue(lines[3].contains("周三课"));
        assertTrue(lines[3].contains("周三 10:00-12:00"));
        assertTrue(lines[4].contains("周日课"));
        assertTrue(lines[4].contains("周日 08:00-10:00"));
    }

    @Test
    void testExportTeacherWeeklySchedule_EmptyResult_OnlyHeader() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        byte[] result = mockMvc.perform(get("/api/schedules/teacher-weekly/export")
                        .param("teacherName", "李老师"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("text/csv")))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        String csv = new String(result, java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(csv.startsWith("\uFEFF"));
        String csvWithoutBom = csv.startsWith("\uFEFF") ? csv.substring(1) : csv;
        String[] lines = csvWithoutBom.split("\n");
        assertEquals(1, lines.length);
        assertEquals("课程名称,老师,教室,时间段", lines[0].trim());
    }

    @Test
    void testExportTeacherWeeklySchedule_MissingTeacherNameParam_Returns400() throws Exception {
        mockMvc.perform(get("/api/schedules/teacher-weekly/export"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testExportTeacherWeeklySchedule_EmptyTeacherName_Returns400() throws Exception {
        mockMvc.perform(get("/api/schedules/teacher-weekly/export")
                        .param("teacherName", ""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testExportTeacherWeeklySchedule_BlankTeacherName_Returns400() throws Exception {
        mockMvc.perform(get("/api/schedules/teacher-weekly/export")
                        .param("teacherName", "   "))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testExportTeacherWeeklySchedule_ChineseFileNameInResponseHeader() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules/teacher-weekly/export")
                        .param("teacherName", "张老师"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString(java.net.URLEncoder.encode("老师周课表.csv", "UTF-8").replace("+", "%20"))));
    }

    @Test
    void testExportTeacherWeeklySchedule_FieldEscaping() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学,高等", "张\"教授\"", "教,室 101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        byte[] result = mockMvc.perform(get("/api/schedules/teacher-weekly/export")
                        .param("teacherName", "张\"教授\""))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        String csv = new String(result, java.nio.charset.StandardCharsets.UTF_8);
        String csvWithoutBom = csv.startsWith("\uFEFF") ? csv.substring(1) : csv;
        String[] lines = csvWithoutBom.split("\n");

        assertEquals(2, lines.length);
        String dataLine = lines[1];
        assertTrue(dataLine.contains("\"数学,高等\""));
        assertTrue(dataLine.contains("\"张\"\"教授\"\"\""));
        assertTrue(dataLine.contains("\"教,室 101\""));
    }

    @Test
    void testExportClassroomWeeklySchedule_ExportOrder_WeekdayThenStartTime() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("周日课", "张老师", "A101", "周日 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("周一下午", "李老师", "A101", "周一 14:00-16:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("周一上午", "王老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("周三课", "赵老师", "A101", "周三 10:00-12:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("其他教室课", "孙老师", "B202", "周一 09:00-11:00"))))
                .andExpect(status().isCreated());

        byte[] result = mockMvc.perform(get("/api/schedules/classroom-weekly/export")
                        .param("classroom", "A101"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("text/csv")))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString(java.net.URLEncoder.encode("教室周课表.csv", "UTF-8").replace("+", "%20"))))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("must-revalidate")))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        String csv = new String(result, java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(csv.startsWith("\uFEFF"));
        assertFalse(csv.contains("其他教室课"));
        assertFalse(csv.contains("B202"));
        assertFalse(csv.contains("孙老师"));
        String csvWithoutBom = csv.startsWith("\uFEFF") ? csv.substring(1) : csv;
        String[] lines = csvWithoutBom.split("\n");

        assertEquals("课程名称,老师,教室,时间段", lines[0].trim());
        assertEquals(5, lines.length);
        assertEquals("周一上午,王老师,A101,周一 08:00-10:00", lines[1].trim());
        assertEquals("周一下午,李老师,A101,周一 14:00-16:00", lines[2].trim());
        assertEquals("周三课,赵老师,A101,周三 10:00-12:00", lines[3].trim());
        assertEquals("周日课,张老师,A101,周日 08:00-10:00", lines[4].trim());
    }

    @Test
    void testExportClassroomWeeklySchedule_EmptyResult_OnlyHeader() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        byte[] result = mockMvc.perform(get("/api/schedules/classroom-weekly/export")
                        .param("classroom", "B202"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("text/csv")))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        String csv = new String(result, java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(csv.startsWith("\uFEFF"));
        String csvWithoutBom = csv.startsWith("\uFEFF") ? csv.substring(1) : csv;
        String[] lines = csvWithoutBom.split("\n");
        assertEquals(1, lines.length);
        assertEquals("课程名称,老师,教室,时间段", lines[0].trim());
    }

    @Test
    void testExportClassroomWeeklySchedule_MissingClassroomParam_Returns400() throws Exception {
        mockMvc.perform(get("/api/schedules/classroom-weekly/export"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testExportClassroomWeeklySchedule_EmptyClassroom_Returns400() throws Exception {
        mockMvc.perform(get("/api/schedules/classroom-weekly/export")
                        .param("classroom", ""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testExportClassroomWeeklySchedule_BlankClassroom_Returns400() throws Exception {
        mockMvc.perform(get("/api/schedules/classroom-weekly/export")
                        .param("classroom", "   "))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testExportClassroomWeeklySchedule_ChineseFileNameInResponseHeader() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules/classroom-weekly/export")
                        .param("classroom", "A101"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString(java.net.URLEncoder.encode("教室周课表.csv", "UTF-8").replace("+", "%20"))));
    }

    @Test
    void testGetRecentActiveCourses_EmptyLogs_ReturnsEmpty() throws Exception {
        mockMvc.perform(get("/api/schedules/recent-active-courses"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void testGetRecentActiveCourses_DefaultLimit10_MoreThan10_ReturnsOnly10() throws Exception {
        for (int i = 1; i <= 15; i++) {
            TestClockConfig.TEST_CLOCK.advanceSeconds(60);
            mockMvc.perform(post("/api/schedules")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    createRequest("课程" + i, "老师" + i, "教室" + i, "周一 08:00-10:00"))))
                    .andExpect(status().isCreated());
        }

        mockMvc.perform(get("/api/schedules/recent-active-courses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(10))
                .andExpect(jsonPath("$[0].courseName").value("课程15"))
                .andExpect(jsonPath("$[1].courseName").value("课程14"))
                .andExpect(jsonPath("$[9].courseName").value("课程6"));
    }

    @Test
    void testGetRecentActiveCourses_CustomLimit5_ReturnsTop5() throws Exception {
        for (int i = 1; i <= 20; i++) {
            TestClockConfig.TEST_CLOCK.advanceSeconds(60);
            mockMvc.perform(post("/api/schedules")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    createRequest("课程" + i, "老师" + i, "教室" + i, "周一 08:00-10:00"))))
                    .andExpect(status().isCreated());
        }

        mockMvc.perform(get("/api/schedules/recent-active-courses")
                        .param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[0].courseName").value("课程20"))
                .andExpect(jsonPath("$[1].courseName").value("课程19"))
                .andExpect(jsonPath("$[2].courseName").value("课程18"))
                .andExpect(jsonPath("$[3].courseName").value("课程17"))
                .andExpect(jsonPath("$[4].courseName").value("课程16"));
    }

    @Test
    void testGetRecentActiveCourses_MixedSuccessFailure_CorrectCounts() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("数学", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("占位课", "李老师", "B202", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        TestClockConfig.TEST_CLOCK.advanceSeconds(10);
        mockMvc.perform(put("/api/schedules/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createUpdateRequest("数学", "张老师", "B202", "周一 08:00-10:00"))))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("物理", "王老师", "C303", "周二 10:00-12:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("另一占位", "赵老师", "D404", "周二 10:00-12:00"))))
                .andExpect(status().isCreated());

        TestClockConfig.TEST_CLOCK.advanceSeconds(10);
        mockMvc.perform(put("/api/schedules/3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createUpdateRequest("物理", "王老师", "D404", "周二 10:00-12:00"))))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/schedules/recent-active-courses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4));

        String mathJsonPath = "$[?(@.courseName == '数学')]";
        mockMvc.perform(get("/api/schedules/recent-active-courses"))
                .andExpect(jsonPath(mathJsonPath + ".successCount").value(org.hamcrest.Matchers.hasItem(1)))
                .andExpect(jsonPath(mathJsonPath + ".failureCount").value(org.hamcrest.Matchers.hasItem(1)))
                .andExpect(jsonPath(mathJsonPath + ".totalCount").value(org.hamcrest.Matchers.hasItem(2)));

        String physicsJsonPath = "$[?(@.courseName == '物理')]";
        mockMvc.perform(get("/api/schedules/recent-active-courses"))
                .andExpect(jsonPath(physicsJsonPath + ".successCount").value(org.hamcrest.Matchers.hasItem(1)))
                .andExpect(jsonPath(physicsJsonPath + ".failureCount").value(org.hamcrest.Matchers.hasItem(1)))
                .andExpect(jsonPath(physicsJsonPath + ".totalCount").value(org.hamcrest.Matchers.hasItem(2)));
    }

    @Test
    void testGetRecentActiveCourses_SortedByLastChangeTimeDescending() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("数学", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());
        TestClockConfig.TEST_CLOCK.advanceSeconds(60);

        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("物理", "李老师", "B202", "周二 14:00-16:00"))))
                .andExpect(status().isCreated());
        TestClockConfig.TEST_CLOCK.advanceSeconds(60);

        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("化学", "王老师", "C303", "周三 10:00-12:00"))))
                .andExpect(status().isCreated());
        TestClockConfig.TEST_CLOCK.advanceSeconds(30);

        mockMvc.perform(put("/api/schedules/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createUpdateRequest("数学", "张教授", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/schedules/recent-active-courses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].courseName").value("数学"))
                .andExpect(jsonPath("$[1].courseName").value("化学"))
                .andExpect(jsonPath("$[2].courseName").value("物理"));
    }

    @Test
    void testGetRecentActiveCourses_UnparseableCourseNames_Skipped() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("数学", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/schedules/undo"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("物理", "李老师", "B202", "周二 14:00-16:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules/recent-active-courses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.courseName == '数学')]").exists())
                .andExpect(jsonPath("$[?(@.courseName == '物理')]").exists());
    }

    @Test
    void testGetRecentActiveCourses_ResponseStructure_CorrectFields() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("数学", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules/recent-active-courses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].courseName").value("数学"))
                .andExpect(jsonPath("$[0].lastChangeTime").exists())
                .andExpect(jsonPath("$[0].successCount").value(1))
                .andExpect(jsonPath("$[0].failureCount").value(0))
                .andExpect(jsonPath("$[0].totalCount").value(1));
    }

    @Test
    void testGetRecentActiveCourses_ZeroLimit_Returns400() throws Exception {
        mockMvc.perform(get("/api/schedules/recent-active-courses")
                        .param("limit", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void testGetRecentActiveCourses_NegativeLimit_Returns400() throws Exception {
        mockMvc.perform(get("/api/schedules/recent-active-courses")
                        .param("limit", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void testGetRecentActiveCourses_LimitExceeds50_Returns400() throws Exception {
        mockMvc.perform(get("/api/schedules/recent-active-courses")
                        .param("limit", "51"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void testGetRecentActiveCourses_Limit50_IsAllowed() throws Exception {
        for (int i = 1; i <= 60; i++) {
            TestClockConfig.TEST_CLOCK.advanceSeconds(60);
            mockMvc.perform(post("/api/schedules")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    createRequest("课程" + i, "老师" + i, "教室" + i, "周一 08:00-10:00"))))
                    .andExpect(status().isCreated());
        }

        mockMvc.perform(get("/api/schedules/recent-active-courses")
                        .param("limit", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(50))
                .andExpect(jsonPath("$[0].courseName").value("课程60"));
    }

    @Test
    void testGetCourseChangeTrend_EmptyLogs_ReturnsEmpty() throws Exception {
        mockMvc.perform(get("/api/schedules/course-change-trend"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void testGetCourseChangeTrend_DefaultDays7_MoreThan7Days_ReturnsOnlyLast7() throws Exception {
        for (int i = 1; i <= 10; i++) {
            TestClockConfig.TEST_CLOCK.advanceSeconds(60 * 60 * 24);
            mockMvc.perform(post("/api/schedules")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    createRequest("课程" + i, "老师" + i, "教室" + i, "周一 08:00-10:00"))))
                    .andExpect(status().isCreated());
        }

        mockMvc.perform(get("/api/schedules/course-change-trend"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(7));
    }

    @Test
    void testGetCourseChangeTrend_CustomDays3_ReturnsOnlyLast3() throws Exception {
        for (int i = 1; i <= 5; i++) {
            TestClockConfig.TEST_CLOCK.advanceSeconds(60 * 60 * 24);
            mockMvc.perform(post("/api/schedules")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    createRequest("课程" + i, "老师" + i, "教室" + i, "周一 08:00-10:00"))))
                    .andExpect(status().isCreated());
        }

        mockMvc.perform(get("/api/schedules/course-change-trend")
                        .param("days", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    void testGetCourseChangeTrend_MixedSuccessFailure_CorrectCounts() throws Exception {
        createScheduleAndReturnId("语文", "张老师", "A101", "周一 08:00-10:00");
        int shuxueId = createScheduleAndReturnId("数学", "李老师", "A102", "周一 14:00-16:00");

        TestClockConfig.TEST_CLOCK.advanceSeconds(60 * 60 * 24);

        int yingyuId = createScheduleAndReturnId("英语", "王老师", "B201", "周二 10:00-12:00");

        mockMvc.perform(put("/api/schedules/" + shuxueId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createUpdateRequest("数学", "李老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isConflict());

        TestClockConfig.TEST_CLOCK.advanceSeconds(60 * 60 * 24);

        mockMvc.perform(delete("/api/schedules/" + yingyuId))
                .andExpect(status().isOk());

        String response = mockMvc.perform(get("/api/schedules/course-change-trend"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andReturn()
                .getResponse()
                .getContentAsString();

        com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(response);

        com.fasterxml.jackson.databind.JsonNode day0 = root.get(0);
        assertEquals(2, day0.get("successCount").asInt());
        assertEquals(0, day0.get("failureCount").asInt());
        assertEquals(2, day0.get("totalCount").asInt());

        com.fasterxml.jackson.databind.JsonNode day1 = root.get(1);
        assertEquals(1, day1.get("successCount").asInt());
        assertEquals(1, day1.get("failureCount").asInt());
        assertEquals(2, day1.get("totalCount").asInt());

        com.fasterxml.jackson.databind.JsonNode day2 = root.get(2);
        assertEquals(1, day2.get("successCount").asInt());
        assertEquals(0, day2.get("failureCount").asInt());
        assertEquals(1, day2.get("totalCount").asInt());
    }

    @Test
    void testGetCourseChangeTrend_SortedByDateAscending() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("数学", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());
        TestClockConfig.TEST_CLOCK.advanceSeconds(60 * 60 * 24);

        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("物理", "李老师", "B202", "周二 14:00-16:00"))))
                .andExpect(status().isCreated());
        TestClockConfig.TEST_CLOCK.advanceSeconds(60 * 60 * 24);

        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("化学", "王老师", "C303", "周三 10:00-12:00"))))
                .andExpect(status().isCreated());

        String response = mockMvc.perform(get("/api/schedules/course-change-trend"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andReturn()
                .getResponse()
                .getContentAsString();

        com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(response);
        String date0 = root.get(0).get("date").asText();
        String date1 = root.get(1).get("date").asText();
        String date2 = root.get(2).get("date").asText();
        assertTrue(date0.compareTo(date1) < 0);
        assertTrue(date1.compareTo(date2) < 0);
    }

    @Test
    void testGetCourseChangeTrend_UnparseableCourseNames_Skipped() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("数学", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/schedules/undo"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("物理", "李老师", "B202", "周二 14:00-16:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules/course-change-trend"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].totalCount").value(2));
    }

    @Test
    void testGetCourseChangeTrend_ZeroDays_Returns400() throws Exception {
        mockMvc.perform(get("/api/schedules/course-change-trend")
                        .param("days", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void testGetCourseChangeTrend_NegativeDays_Returns400() throws Exception {
        mockMvc.perform(get("/api/schedules/course-change-trend")
                        .param("days", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void testGetCourseChangeTrend_DaysExceeds365_Returns400() throws Exception {
        mockMvc.perform(get("/api/schedules/course-change-trend")
                        .param("days", "366"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void testGetCourseChangeTrend_Days365_IsAllowed() throws Exception {
        mockMvc.perform(get("/api/schedules/course-change-trend")
                        .param("days", "365"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void testGetCourseChangeTrend_InvalidDaysParam_Returns400() throws Exception {
        mockMvc.perform(get("/api/schedules/course-change-trend")
                        .param("days", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void testGetCourseChangeTrend_ResponseStructure_CorrectFields() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("数学", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules/course-change-trend"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].date").exists())
                .andExpect(jsonPath("$[0].successCount").value(1))
                .andExpect(jsonPath("$[0].failureCount").value(0))
                .andExpect(jsonPath("$[0].totalCount").value(1));
    }

    private int createScheduleAndReturnId(String courseName, String teacherName, String classroom, String timeSlot) throws Exception {
        String resp = mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest(courseName, teacherName, classroom, timeSlot))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resp).get("id").asInt();
    }

    @Test
    void testGetCourseChangeAbnormalDays_DefaultDays7_NoFailures_ReturnsEmptyList() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("数学", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules/course-change-abnormal-days"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void testGetCourseChangeAbnormalDays_WithFailures_ReturnsAbnormalDays() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("数学", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("物理", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/schedules/course-change-abnormal-days"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].successCount").value(1))
                .andExpect(jsonPath("$[0].failureCount").value(1))
                .andExpect(jsonPath("$[0].totalCount").value(2))
                .andExpect(jsonPath("$[0].failureRate").value(0.5));
    }

    @Test
    void testGetCourseChangeAbnormalDays_SpecifiedDays() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("数学", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules/course-change-abnormal-days")
                        .param("days", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void testGetCourseChangeAbnormalDays_EmptyLogs_ReturnsEmptyList() throws Exception {
        mockMvc.perform(get("/api/schedules/course-change-abnormal-days"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void testGetCourseChangeAbnormalDays_InvalidDaysParam_Returns400() throws Exception {
        mockMvc.perform(get("/api/schedules/course-change-abnormal-days")
                        .param("days", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void testGetCourseChangeAbnormalDays_NegativeDaysParam_Returns400() throws Exception {
        mockMvc.perform(get("/api/schedules/course-change-abnormal-days")
                        .param("days", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void testGetCourseChangeAbnormalDays_ResponseStructure_CorrectFields() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("数学", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("物理", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/schedules/course-change-abnormal-days"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].date").exists())
                .andExpect(jsonPath("$[0].successCount").exists())
                .andExpect(jsonPath("$[0].failureCount").exists())
                .andExpect(jsonPath("$[0].totalCount").exists())
                .andExpect(jsonPath("$[0].failureRate").exists());
    }

    @Test
    void testGetCourseChangeAbnormalDays_SortedByFailureRateDescending() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("数学", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("物理", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isConflict());

        TestClockConfig.TEST_CLOCK.advanceSeconds(60 * 60 * 24);

        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("化学", "李老师", "B202", "周二 14:00-16:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("英语", "王老师", "C303", "周三 09:00-11:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("历史", "李老师", "B202", "周二 14:00-16:00"))))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/schedules/course-change-abnormal-days"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].failureRate").value(0.5))
                .andExpect(jsonPath("$[0].failureCount").value(1))
                .andExpect(jsonPath("$[1].failureRate").value(1.0 / 3))
                .andExpect(jsonPath("$[1].failureCount").value(1));
    }

    @Test
    void testGetFailureReasonSummary_EmptyLogs_ReturnsEmptyList() throws Exception {
        mockMvc.perform(get("/api/schedules/failure-reason-summary"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void testGetFailureReasonSummary_DefaultLimit10_MoreThan10Reasons_ReturnsOnly10() throws Exception {
        for (int i = 1; i <= 15; i++) {
            TestClockConfig.TEST_CLOCK.advanceSeconds(60);
            auditLogService.recordLog(
                    OperationType.CREATE, (long) i, "课程" + i, "老师" + i, "教室" + i, "周一 08:00-10:00",
                    false, "错误原因" + i
            );
        }

        mockMvc.perform(get("/api/schedules/failure-reason-summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(10));
    }

    @Test
    void testGetFailureReasonSummary_CustomLimit5_ReturnsTop5() throws Exception {
        for (int i = 1; i <= 20; i++) {
            TestClockConfig.TEST_CLOCK.advanceSeconds(60);
            auditLogService.recordLog(
                    OperationType.CREATE, (long) i, "课程" + i, "老师" + i, "教室" + i, "周一 08:00-10:00",
                    false, "错误原因" + i
            );
        }

        mockMvc.perform(get("/api/schedules/failure-reason-summary")
                        .param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5));
    }

    @Test
    void testGetFailureReasonSummary_SortedByCountDescending() throws Exception {
        auditLogService.recordLog(OperationType.CREATE, 1L, "数学", "张老师", "A101", "周一 08:00-10:00", false, "老师冲突");
        auditLogService.recordLog(OperationType.UPDATE, 2L, "物理", "李老师", "B202", "周二 14:00-16:00", false, "教室冲突");
        auditLogService.recordLog(OperationType.DELETE, 3L, "化学", "王老师", "C303", "周三 10:00-12:00", false, "教室冲突");
        auditLogService.recordLog(OperationType.CREATE, 4L, "生物", "赵老师", "D404", "周四 09:00-11:00", false, "时间冲突");
        auditLogService.recordLog(OperationType.UPDATE, 5L, "历史", "刘老师", "E505", "周五 14:00-16:00", false, "时间冲突");
        auditLogService.recordLog(OperationType.DELETE, 6L, "地理", "孙老师", "F606", "周六 08:00-10:00", false, "时间冲突");

        mockMvc.perform(get("/api/schedules/failure-reason-summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].failureReason").value("时间冲突"))
                .andExpect(jsonPath("$[0].count").value(3))
                .andExpect(jsonPath("$[1].failureReason").value("教室冲突"))
                .andExpect(jsonPath("$[1].count").value(2))
                .andExpect(jsonPath("$[2].failureReason").value("老师冲突"))
                .andExpect(jsonPath("$[2].count").value(1));
    }

    @Test
    void testGetFailureReasonSummary_SameCount_SortedByLastOccurrenceTimeDescending() throws Exception {
        auditLogService.recordLog(OperationType.CREATE, 1L, "数学", "张老师", "A101", "周一 08:00-10:00", false, "老师冲突");
        TestClockConfig.TEST_CLOCK.advanceSeconds(1800);
        auditLogService.recordLog(OperationType.UPDATE, 2L, "物理", "李老师", "B202", "周二 14:00-16:00", false, "教室冲突");
        TestClockConfig.TEST_CLOCK.advanceSeconds(1800);
        auditLogService.recordLog(OperationType.DELETE, 3L, "化学", "王老师", "C303", "周三 10:00-12:00", false, "老师冲突");
        TestClockConfig.TEST_CLOCK.advanceSeconds(1800);
        auditLogService.recordLog(OperationType.CREATE, 4L, "生物", "赵老师", "D404", "周四 09:00-11:00", false, "教室冲突");

        mockMvc.perform(get("/api/schedules/failure-reason-summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].failureReason").value("教室冲突"))
                .andExpect(jsonPath("$[1].failureReason").value("老师冲突"));
    }

    @Test
    void testGetFailureReasonSummary_SameCountAndTime_SortedByFailureReasonAscending() throws Exception {
        auditLogService.recordLog(OperationType.CREATE, 1L, "数学", "张老师", "A101", "周一 08:00-10:00", false, "时间冲突");
        auditLogService.recordLog(OperationType.UPDATE, 2L, "物理", "李老师", "B202", "周二 14:00-16:00", false, "教室冲突");
        auditLogService.recordLog(OperationType.DELETE, 3L, "化学", "王老师", "C303", "周三 10:00-12:00", false, "老师冲突");

        mockMvc.perform(get("/api/schedules/failure-reason-summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].failureReason").value("教室冲突"))
                .andExpect(jsonPath("$[1].failureReason").value("时间冲突"))
                .andExpect(jsonPath("$[2].failureReason").value("老师冲突"));
    }

    @Test
    void testGetFailureReasonSummary_OnlySuccessLogs_ReturnsEmpty() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("数学", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules/failure-reason-summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void testGetFailureReasonSummary_NullErrorMessage_Skipped() throws Exception {
        auditLogService.recordLog(OperationType.CREATE, 1L, "数学", "张老师", "A101", "周一 08:00-10:00", false, "老师冲突");
        auditLogService.recordLog(OperationType.UPDATE, 2L, "物理", "李老师", "B202", "周二 14:00-16:00", false, null);
        auditLogService.recordLog(OperationType.DELETE, 3L, "化学", "王老师", "C303", "周三 10:00-12:00", false, "教室冲突");

        mockMvc.perform(get("/api/schedules/failure-reason-summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.failureReason == '老师冲突')]").exists())
                .andExpect(jsonPath("$[?(@.failureReason == '教室冲突')]").exists());
    }

    @Test
    void testGetFailureReasonSummary_EmptyErrorMessage_Skipped() throws Exception {
        auditLogService.recordLog(OperationType.CREATE, 1L, "数学", "张老师", "A101", "周一 08:00-10:00", false, "老师冲突");
        auditLogService.recordLog(OperationType.UPDATE, 2L, "物理", "李老师", "B202", "周二 14:00-16:00", false, "");
        auditLogService.recordLog(OperationType.DELETE, 3L, "化学", "王老师", "C303", "周三 10:00-12:00", false, "教室冲突");

        mockMvc.perform(get("/api/schedules/failure-reason-summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void testGetFailureReasonSummary_WhitespaceOnlyErrorMessage_Skipped() throws Exception {
        auditLogService.recordLog(OperationType.CREATE, 1L, "数学", "张老师", "A101", "周一 08:00-10:00", false, "老师冲突");
        auditLogService.recordLog(OperationType.UPDATE, 2L, "物理", "李老师", "B202", "周二 14:00-16:00", false, "   ");
        auditLogService.recordLog(OperationType.DELETE, 3L, "化学", "王老师", "C303", "周三 10:00-12:00", false, "  \t  ");
        auditLogService.recordLog(OperationType.CREATE, 4L, "生物", "赵老师", "D404", "周四 09:00-11:00", false, "教室冲突");

        mockMvc.perform(get("/api/schedules/failure-reason-summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void testGetFailureReasonSummary_ZeroLimit_Returns400() throws Exception {
        mockMvc.perform(get("/api/schedules/failure-reason-summary")
                        .param("limit", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void testGetFailureReasonSummary_NegativeLimit_Returns400() throws Exception {
        mockMvc.perform(get("/api/schedules/failure-reason-summary")
                        .param("limit", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void testGetFailureReasonSummary_LimitExceedsMax_Returns400() throws Exception {
        mockMvc.perform(get("/api/schedules/failure-reason-summary")
                        .param("limit", "51"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void testGetFailureReasonSummary_Limit50_IsAllowed() throws Exception {
        for (int i = 1; i <= 60; i++) {
            TestClockConfig.TEST_CLOCK.advanceSeconds(60);
            auditLogService.recordLog(
                    OperationType.CREATE, (long) i, "课程" + i, "老师" + i, "教室" + i, "周一 08:00-10:00",
                    false, "错误原因" + i
            );
        }

        mockMvc.perform(get("/api/schedules/failure-reason-summary")
                        .param("limit", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(50));
    }

    @Test
    void testGetFailureReasonSummary_ResponseStructure_CorrectFields() throws Exception {
        auditLogService.recordLog(OperationType.CREATE, 1L, "数学", "张老师", "A101", "周一 08:00-10:00", false, "老师冲突");

        mockMvc.perform(get("/api/schedules/failure-reason-summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].failureReason").exists())
                .andExpect(jsonPath("$[0].count").exists())
                .andExpect(jsonPath("$[0].lastOccurrenceTime").exists());
    }

    @Test
    void testGetFailureReasonSummary_MixedSuccessFailure_CorrectCounts() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("数学", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("物理", "张老师", "B202", "周一 08:00-10:00"))))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/schedules/failure-reason-summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].failureReason").exists())
                .andExpect(jsonPath("$[0].count").value(1));
    }

    @Test
    void testGetFailureReasonSummary_RealApiChain_CreateConflictThenQuery() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("数学", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("英语", "张老师", "C303", "周一 08:00-10:00"))))
                .andExpect(status().isConflict());

        String firstSummary = mockMvc.perform(get("/api/schedules/failure-reason-summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].failureReason").value(org.hamcrest.Matchers.containsString("张老师")))
                .andExpect(jsonPath("$[0].failureReason").value(org.hamcrest.Matchers.containsString("周一 08:00-10:00")))
                .andExpect(jsonPath("$[0].count").value(1))
                .andExpect(jsonPath("$[0].lastOccurrenceTime").exists())
                .andReturn()
                .getResponse()
                .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        List<Map<String, Object>> firstList = objectMapper.readValue(firstSummary,
                objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class));
        assertEquals(1, firstList.size());
        Map<String, Object> firstItem = firstList.get(0);
        String reason = String.valueOf(firstItem.get("failureReason"));
        assertTrue(reason.contains("张老师"), "原因应包含老师名 张老师，实际: " + reason);
        assertTrue(reason.contains("周一 08:00-10:00"), "原因应包含时间段，实际: " + reason);

        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("物理", "李老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/schedules/failure-reason-summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("化学", "张老师", "D404", "周一 08:00-10:00"))))
                .andExpect(status().isConflict());

        String thirdSummary = mockMvc.perform(get("/api/schedules/failure-reason-summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andReturn()
                .getResponse()
                .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        List<Map<String, Object>> thirdList = objectMapper.readValue(thirdSummary,
                objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class));

        Map<String, Object> teacherConflict = thirdList.stream()
                .filter(item -> ((String) item.get("failureReason")).contains("张老师"))
                .findFirst()
                .orElse(null);
        assertNotNull(teacherConflict, "应包含张老师相关的失败原因");
        assertEquals(2, teacherConflict.get("count"), "张老师冲突次数应为 2");
        assertTrue(((String) teacherConflict.get("failureReason")).contains("周一 08:00-10:00"),
                "失败原因应包含时间段 周一 08:00-10:00");

        Map<String, Object> classroomConflict = thirdList.stream()
                .filter(item -> ((String) item.get("failureReason")).contains("A101"))
                .findFirst()
                .orElse(null);
        assertNotNull(classroomConflict, "应包含教室 A101 相关的失败原因");
        assertEquals(1, classroomConflict.get("count"), "A101 教室冲突次数应为 1");
    }

    @Test
    void testGetOperationFailureSummary_EmptyLogs_ReturnsEmptyList() throws Exception {
        mockMvc.perform(get("/api/schedules/operation-failure-summary"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void testGetOperationFailureSummary_DefaultLimit10_MoreThan10Types_ReturnsOnly10() throws Exception {
        OperationType[] types = OperationType.values();
        for (int i = 0; i < Math.max(types.length, 15); i++) {
            OperationType op = types[i % types.length];
            TestClockConfig.TEST_CLOCK.advanceSeconds(60);
            auditLogService.recordLog(
                    op, (long) i, "课程" + i, "老师" + i, "教室" + i, "周一 08:00-10:00",
                    false, "错误" + i
            );
        }

        mockMvc.perform(get("/api/schedules/operation-failure-summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(Math.min(10, types.length)));
    }

    @Test
    void testGetOperationFailureSummary_CustomLimit5_ReturnsTop5() throws Exception {
        OperationType[] types = OperationType.values();
        for (int i = 0; i < types.length; i++) {
            TestClockConfig.TEST_CLOCK.advanceSeconds(60);
            auditLogService.recordLog(
                    types[i], (long) i, "课程" + i, "老师" + i, "教室" + i, "周一 08:00-10:00",
                    false, "错误" + i
            );
        }

        mockMvc.perform(get("/api/schedules/operation-failure-summary")
                        .param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5));
    }

    @Test
    void testGetOperationFailureSummary_SortedByFailureCountDescending() throws Exception {
        for (int i = 0; i < 5; i++) {
            TestClockConfig.TEST_CLOCK.advanceSeconds(10);
            auditLogService.recordLog(OperationType.CREATE, 1L, "数学", "张老师", "A101", "周一 08:00-10:00", false, "错误");
        }
        for (int i = 0; i < 3; i++) {
            TestClockConfig.TEST_CLOCK.advanceSeconds(10);
            auditLogService.recordLog(OperationType.UPDATE, 2L, "物理", "李老师", "B202", "周二 14:00-16:00", false, "错误");
        }
        for (int i = 0; i < 1; i++) {
            TestClockConfig.TEST_CLOCK.advanceSeconds(10);
            auditLogService.recordLog(OperationType.DELETE, 3L, "化学", "王老师", "C303", "周三 10:00-12:00", false, "错误");
        }

        mockMvc.perform(get("/api/schedules/operation-failure-summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].operationType").value("CREATE"))
                .andExpect(jsonPath("$[0].failureCount").value(5))
                .andExpect(jsonPath("$[1].operationType").value("UPDATE"))
                .andExpect(jsonPath("$[1].failureCount").value(3))
                .andExpect(jsonPath("$[2].operationType").value("DELETE"))
                .andExpect(jsonPath("$[2].failureCount").value(1));
    }

    @Test
    void testGetOperationFailureSummary_SameCount_SortedByLastFailureTimeDescending() throws Exception {
        auditLogService.recordLog(OperationType.CREATE, 1L, "数学", "张老师", "A101", "周一 08:00-10:00", false, "错误");
        TestClockConfig.TEST_CLOCK.advanceSeconds(1800);
        auditLogService.recordLog(OperationType.UPDATE, 2L, "物理", "李老师", "B202", "周二 14:00-16:00", false, "错误");
        TestClockConfig.TEST_CLOCK.advanceSeconds(1800);
        auditLogService.recordLog(OperationType.CREATE, 1L, "数学", "张老师", "A101", "周一 08:00-10:00", false, "错误");
        TestClockConfig.TEST_CLOCK.advanceSeconds(1800);
        auditLogService.recordLog(OperationType.UPDATE, 2L, "物理", "李老师", "B202", "周二 14:00-16:00", false, "错误");

        mockMvc.perform(get("/api/schedules/operation-failure-summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].operationType").value("UPDATE"))
                .andExpect(jsonPath("$[1].operationType").value("CREATE"));
    }

    @Test
    void testGetOperationFailureSummary_SameCountAndTime_SortedByOperationTypeNameAscending() throws Exception {
        auditLogService.recordLog(OperationType.UPDATE, 1L, "数学", "张老师", "A101", "周一 08:00-10:00", false, "错误");
        auditLogService.recordLog(OperationType.DELETE, 2L, "物理", "李老师", "B202", "周二 14:00-16:00", false, "错误");
        auditLogService.recordLog(OperationType.CREATE, 3L, "化学", "王老师", "C303", "周三 10:00-12:00", false, "错误");

        mockMvc.perform(get("/api/schedules/operation-failure-summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].operationType").value("CREATE"))
                .andExpect(jsonPath("$[1].operationType").value("DELETE"))
                .andExpect(jsonPath("$[2].operationType").value("UPDATE"));
    }

    @Test
    void testGetOperationFailureSummary_OnlySuccessLogs_ReturnsEmpty() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("数学", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules/operation-failure-summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void testGetOperationFailureSummary_ResponseStructure_CorrectFields() throws Exception {
        auditLogService.recordLog(OperationType.CREATE, 1L, "数学", "张老师", "A101", "周一 08:00-10:00", false, "错误");

        mockMvc.perform(get("/api/schedules/operation-failure-summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].operationType").exists())
                .andExpect(jsonPath("$[0].failureCount").exists())
                .andExpect(jsonPath("$[0].lastFailureTime").exists());
    }

    @Test
    void testGetOperationFailureSummary_ZeroLimit_Returns400() throws Exception {
        mockMvc.perform(get("/api/schedules/operation-failure-summary")
                        .param("limit", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void testGetOperationFailureSummary_NegativeLimit_Returns400() throws Exception {
        mockMvc.perform(get("/api/schedules/operation-failure-summary")
                        .param("limit", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void testGetOperationFailureSummary_LimitExceedsMax_Returns400() throws Exception {
        mockMvc.perform(get("/api/schedules/operation-failure-summary")
                        .param("limit", "51"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void testGetOperationFailureSummary_Limit50_IsAllowed() throws Exception {
        OperationType[] types = OperationType.values();
        for (int i = 0; i < types.length; i++) {
            for (int j = 0; j < 10; j++) {
                TestClockConfig.TEST_CLOCK.advanceSeconds(10);
                auditLogService.recordLog(
                        types[i], (long) (i * 10 + j), "课程" + i + j, "老师" + i, "教室" + i, "周一 08:00-10:00",
                        false, "错误"
                );
            }
        }

        mockMvc.perform(get("/api/schedules/operation-failure-summary")
                        .param("limit", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(types.length));
    }

    @Test
    void testGetOperationFailureSummary_RealApiChain_CreateConflictThenQuery() throws Exception {
        mockMvc.perform(get("/api/schedules/operation-failure-summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("数学", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules/operation-failure-summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("英语", "张老师", "C303", "周一 08:00-10:00"))))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/schedules/operation-failure-summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].operationType").value("CREATE"))
                .andExpect(jsonPath("$[0].failureCount").value(1))
                .andExpect(jsonPath("$[0].lastFailureTime").exists());

        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("物理", "李老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/schedules/operation-failure-summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].operationType").value("CREATE"))
                .andExpect(jsonPath("$[0].failureCount").value(2))
                .andExpect(jsonPath("$[0].lastFailureTime").exists());
    }

    private CourseScheduleConflictPreCheckRequest createPreCheckRequest(
            String courseName, String teacherName, String classroom, String timeSlot) {
        CourseScheduleConflictPreCheckRequest request = new CourseScheduleConflictPreCheckRequest();
        request.setCourseName(courseName);
        request.setTeacherName(teacherName);
        request.setClassroom(classroom);
        request.setTimeSlot(timeSlot);
        return request;
    }

    @Test
    void testPreCheckConflicts_NoConflict_Returns200() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("数学", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        CourseScheduleConflictPreCheckRequest request = createPreCheckRequest(
                "物理", "李老师", "B202", "周二 14:00-16:00");

        mockMvc.perform(post("/api/schedules/pre-check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canSchedule").value(true))
                .andExpect(jsonPath("$.conflictCount").value(0))
                .andExpect(jsonPath("$.conflictDetails").isArray())
                .andExpect(jsonPath("$.conflictDetails").isEmpty());
    }

    @Test
    void testPreCheckConflicts_TeacherConflict_Returns200WithDetails() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("数学", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        CourseScheduleConflictPreCheckRequest request = createPreCheckRequest(
                "物理", "张老师", "B202", "周一 08:00-10:00");

        mockMvc.perform(post("/api/schedules/pre-check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canSchedule").value(false))
                .andExpect(jsonPath("$.conflictCount").value(1))
                .andExpect(jsonPath("$.conflictDetails[0].conflictType").value("TEACHER"))
                .andExpect(jsonPath("$.conflictDetails[0].sourceType").value("EXISTING_COURSE"))
                .andExpect(jsonPath("$.conflictDetails[0].courseId").value(1))
                .andExpect(jsonPath("$.conflictDetails[0].courseName").value("数学"))
                .andExpect(jsonPath("$.conflictDetails[0].teacherName").value("张老师"))
                .andExpect(jsonPath("$.conflictDetails[0].classroom").value("A101"))
                .andExpect(jsonPath("$.conflictDetails[0].timeSlot").value("周一 08:00-10:00"))
                .andExpect(jsonPath("$.conflictDetails[0].reason").value("老师 张老师 在时间段 周一 08:00-10:00 已有课程安排"));
    }

    @Test
    void testPreCheckConflicts_ClassroomConflict_Returns200WithDetails() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("数学", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        CourseScheduleConflictPreCheckRequest request = createPreCheckRequest(
                "物理", "李老师", "A101", "周一 08:00-10:00");

        mockMvc.perform(post("/api/schedules/pre-check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canSchedule").value(false))
                .andExpect(jsonPath("$.conflictCount").value(1))
                .andExpect(jsonPath("$.conflictDetails[0].conflictType").value("CLASSROOM"))
                .andExpect(jsonPath("$.conflictDetails[0].sourceType").value("EXISTING_COURSE"))
                .andExpect(jsonPath("$.conflictDetails[0].courseId").value(1))
                .andExpect(jsonPath("$.conflictDetails[0].courseName").value("数学"))
                .andExpect(jsonPath("$.conflictDetails[0].teacherName").value("张老师"))
                .andExpect(jsonPath("$.conflictDetails[0].classroom").value("A101"))
                .andExpect(jsonPath("$.conflictDetails[0].timeSlot").value("周一 08:00-10:00"))
                .andExpect(jsonPath("$.conflictDetails[0].reason").value("教室 A101 在时间段 周一 08:00-10:00 已有课程安排"));
    }

    @Test
    void testPreCheckConflicts_BothTeacherAndClassroomConflict_Returns200WithBothDetails() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("数学", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        CourseScheduleConflictPreCheckRequest request = createPreCheckRequest(
                "物理", "张老师", "A101", "周一 08:00-10:00");

        mockMvc.perform(post("/api/schedules/pre-check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canSchedule").value(false))
                .andExpect(jsonPath("$.conflictCount").value(2))
                .andExpect(jsonPath("$.conflictDetails[0].conflictType").value("TEACHER"))
                .andExpect(jsonPath("$.conflictDetails[0].sourceType").value("EXISTING_COURSE"))
                .andExpect(jsonPath("$.conflictDetails[1].conflictType").value("CLASSROOM"))
                .andExpect(jsonPath("$.conflictDetails[1].sourceType").value("EXISTING_COURSE"));
    }

    @Test
    void testPreCheckConflicts_EmptyCourseName_Returns400() throws Exception {
        CourseScheduleConflictPreCheckRequest request = createPreCheckRequest(
                "", "张老师", "A101", "周一 08:00-10:00");

        mockMvc.perform(post("/api/schedules/pre-check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("课程名不能为空"));
    }

    @Test
    void testPreCheckConflicts_EmptyTeacherName_Returns400() throws Exception {
        CourseScheduleConflictPreCheckRequest request = createPreCheckRequest(
                "数学", "", "A101", "周一 08:00-10:00");

        mockMvc.perform(post("/api/schedules/pre-check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("老师名不能为空"));
    }

    @Test
    void testPreCheckConflicts_EmptyClassroom_Returns400() throws Exception {
        CourseScheduleConflictPreCheckRequest request = createPreCheckRequest(
                "数学", "张老师", "", "周一 08:00-10:00");

        mockMvc.perform(post("/api/schedules/pre-check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("教室名不能为空"));
    }

    @Test
    void testPreCheckConflicts_EmptyTimeSlot_Returns400() throws Exception {
        CourseScheduleConflictPreCheckRequest request = createPreCheckRequest(
                "数学", "张老师", "A101", "");

        mockMvc.perform(post("/api/schedules/pre-check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("时间段不能为空"));
    }

    @Test
    void testPreCheckConflicts_NullRequestBody_Returns400() throws Exception {
        mockMvc.perform(post("/api/schedules/pre-check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("课程名不能为空"));
    }

    @Test
    void testPreCheckConflicts_InvalidTimeSlotFormat_Returns400() throws Exception {
        CourseScheduleConflictPreCheckRequest request = createPreCheckRequest(
                "数学", "张老师", "A101", "明天上午");

        mockMvc.perform(post("/api/schedules/pre-check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void testPreCheckConflicts_InvalidTimeSlotStartAfterEnd_Returns400() throws Exception {
        CourseScheduleConflictPreCheckRequest request = createPreCheckRequest(
                "数学", "张老师", "A101", "周一 10:00-08:00");

        mockMvc.perform(post("/api/schedules/pre-check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void testPreCheckConflicts_NoAuditLogCreated() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("数学", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        List<AuditLogResponse> logsBefore = auditLogService.getAllLogs();
        int logCountBefore = logsBefore.size();

        CourseScheduleConflictPreCheckRequest request = createPreCheckRequest(
                "物理", "张老师", "B202", "周一 08:00-10:00");

        mockMvc.perform(post("/api/schedules/pre-check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        List<AuditLogResponse> logsAfter = auditLogService.getAllLogs();
        assertEquals(logCountBefore, logsAfter.size());
    }

    @Test
    void testPreCheckConflicts_NoCourseCreated() throws Exception {
        CourseScheduleConflictPreCheckRequest request = createPreCheckRequest(
                "数学", "张老师", "A101", "周一 08:00-10:00");

        mockMvc.perform(post("/api/schedules/pre-check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/schedules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void testPreCheckConflicts_SortingByTypeWeekdayStartTimeCourseId() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("课程1", "张老师", "A101", "周二 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("课程2", "李老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("课程3", "张老师", "B202", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        CourseScheduleConflictPreCheckRequest request = createPreCheckRequest(
                "新课程", "张老师", "A101", "周一 08:00-10:00");

        mockMvc.perform(post("/api/schedules/pre-check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conflictCount").value(2))
                .andExpect(jsonPath("$.conflictDetails[0].conflictType").value("TEACHER"))
                .andExpect(jsonPath("$.conflictDetails[0].sourceType").value("EXISTING_COURSE"))
                .andExpect(jsonPath("$.conflictDetails[0].courseId").value(3))
                .andExpect(jsonPath("$.conflictDetails[1].conflictType").value("CLASSROOM"))
                .andExpect(jsonPath("$.conflictDetails[1].sourceType").value("EXISTING_COURSE"))
                .andExpect(jsonPath("$.conflictDetails[1].courseId").value(2));
    }

    @Test
    void testPreCheckConflicts_TimeSlotNormalization() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("数学", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        CourseScheduleConflictPreCheckRequest request = createPreCheckRequest(
                "物理", "张老师", "B202", "星期一 8:00-10:00");

        mockMvc.perform(post("/api/schedules/pre-check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canSchedule").value(false))
                .andExpect(jsonPath("$.conflictCount").value(1));
    }

    @Test
    void testAddSchedule_TeacherOverlap_PartialTime_Returns409() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("数学", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("物理", "张老师", "B202", "周一 09:00-11:00"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void testAddSchedule_ClassroomOverlap_PartialTime_Returns409() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("数学", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("物理", "李老师", "A101", "周一 09:00-11:00"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void testAddSchedule_BoundaryTouching_Returns201() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("数学", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("物理", "张老师", "A101", "周一 10:00-12:00"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(2));
    }

    @Test
    void testAddSchedule_DifferentWeekday_Returns201() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("数学", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("物理", "张老师", "A101", "周二 08:00-10:00"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(2));
    }

    @Test
    void testPreCheckConflicts_TeacherOverlap_PartialTime() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("数学", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        CourseScheduleConflictPreCheckRequest request = createPreCheckRequest(
                "物理", "张老师", "B202", "周一 09:00-11:00");

        mockMvc.perform(post("/api/schedules/pre-check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canSchedule").value(false))
                .andExpect(jsonPath("$.conflictCount").value(1))
                .andExpect(jsonPath("$.conflictDetails[0].conflictType").value("TEACHER"))
                .andExpect(jsonPath("$.conflictDetails[0].sourceType").value("EXISTING_COURSE"))
                .andExpect(jsonPath("$.conflictDetails[0].courseId").value(1));
    }

    @Test
    void testPreCheckConflicts_ClassroomOverlap_PartialTime() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("数学", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        CourseScheduleConflictPreCheckRequest request = createPreCheckRequest(
                "物理", "李老师", "A101", "周一 09:00-11:00");

        mockMvc.perform(post("/api/schedules/pre-check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canSchedule").value(false))
                .andExpect(jsonPath("$.conflictCount").value(1))
                .andExpect(jsonPath("$.conflictDetails[0].conflictType").value("CLASSROOM"))
                .andExpect(jsonPath("$.conflictDetails[0].sourceType").value("EXISTING_COURSE"))
                .andExpect(jsonPath("$.conflictDetails[0].courseId").value(1));
    }

    @Test
    void testPreCheckConflicts_BoundaryTouching_CanSchedule() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("数学", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        CourseScheduleConflictPreCheckRequest request = createPreCheckRequest(
                "物理", "张老师", "A101", "周一 10:00-12:00");

        mockMvc.perform(post("/api/schedules/pre-check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canSchedule").value(true))
                .andExpect(jsonPath("$.conflictCount").value(0));
    }

    @Test
    void testPreCheckConflicts_DifferentWeekday_CanSchedule() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("数学", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        CourseScheduleConflictPreCheckRequest request = createPreCheckRequest(
                "物理", "张老师", "A101", "周二 08:00-10:00");

        mockMvc.perform(post("/api/schedules/pre-check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canSchedule").value(true))
                .andExpect(jsonPath("$.conflictCount").value(0));
    }

    private TeacherBatchPreCheckItemRequest createBatchItem(String courseName, String classroom, String timeSlot) {
        TeacherBatchPreCheckItemRequest item = new TeacherBatchPreCheckItemRequest();
        item.setCourseName(courseName);
        item.setClassroom(classroom);
        item.setTimeSlot(timeSlot);
        return item;
    }

    private TeacherBatchPreCheckRequest createBatchRequest(String teacherName, List<TeacherBatchPreCheckItemRequest> items) {
        TeacherBatchPreCheckRequest request = new TeacherBatchPreCheckRequest();
        request.setTeacherName(teacherName);
        request.setItems(items);
        return request;
    }

    @Test
    void testPreCheckByTeacherBatch_AllCanSchedule_Returns200() throws Exception {
        List<TeacherBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        items.add(createBatchItem("数学", "A101", "周一 08:00-10:00"));
        items.add(createBatchItem("物理", "A101", "周一 10:00-12:00"));

        TeacherBatchPreCheckRequest request = createBatchRequest("张老师", items);

        mockMvc.perform(post("/api/schedules/pre-check/teacher-batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canSchedule").value(true))
                .andExpect(jsonPath("$.totalConflictCount").value(0))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].originalIndex").value(0))
                .andExpect(jsonPath("$.items[0].canSchedule").value(true))
                .andExpect(jsonPath("$.items[0].conflictCount").value(0))
                .andExpect(jsonPath("$.items[1].originalIndex").value(1))
                .andExpect(jsonPath("$.items[1].canSchedule").value(true))
                .andExpect(jsonPath("$.items[1].conflictCount").value(0));

        mockMvc.perform(get("/api/schedules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void testPreCheckByTeacherBatch_ConflictWithExisting_Returns200WithConflicts() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("已有课程", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        List<TeacherBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        items.add(createBatchItem("数学", "B202", "周一 08:00-10:00"));
        items.add(createBatchItem("物理", "C303", "周二 08:00-10:00"));

        TeacherBatchPreCheckRequest request = createBatchRequest("张老师", items);

        mockMvc.perform(post("/api/schedules/pre-check/teacher-batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canSchedule").value(false))
                .andExpect(jsonPath("$.totalConflictCount").value(1))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].originalIndex").value(0))
                .andExpect(jsonPath("$.items[0].canSchedule").value(false))
                .andExpect(jsonPath("$.items[0].conflictCount").value(1))
                .andExpect(jsonPath("$.items[0].conflictDetails[0].conflictType").value("TEACHER"))
                .andExpect(jsonPath("$.items[0].conflictDetails[0].sourceType").value("EXISTING_COURSE"))
                .andExpect(jsonPath("$.items[0].conflictDetails[0].courseId").value(1))
                .andExpect(jsonPath("$.items[0].conflictDetails[0].pendingIndex").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.items[0].conflictDetails[0].courseName").value("已有课程"))
                .andExpect(jsonPath("$.items[0].conflictDetails[0].reason").value("老师 张老师 在时间段 周一 08:00-10:00 已有课程安排"))
                .andExpect(jsonPath("$.items[1].originalIndex").value(1))
                .andExpect(jsonPath("$.items[1].canSchedule").value(true))
                .andExpect(jsonPath("$.items[1].conflictCount").value(0));

        mockMvc.perform(get("/api/schedules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void testPreCheckByTeacherBatch_InternalConflict_Returns200WithConflicts() throws Exception {
        List<TeacherBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        items.add(createBatchItem("数学", "A101", "周一 08:00-10:00"));
        items.add(createBatchItem("物理", "A101", "周一 08:00-10:00"));

        TeacherBatchPreCheckRequest request = createBatchRequest("张老师", items);

        mockMvc.perform(post("/api/schedules/pre-check/teacher-batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canSchedule").value(false))
                .andExpect(jsonPath("$.totalConflictCount").value(4))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].originalIndex").value(0))
                .andExpect(jsonPath("$.items[0].canSchedule").value(false))
                .andExpect(jsonPath("$.items[0].conflictCount").value(2))
                .andExpect(jsonPath("$.items[0].conflictDetails[0].conflictType").value("TEACHER"))
                .andExpect(jsonPath("$.items[0].conflictDetails[0].sourceType").value("PENDING_ITEM"))
                .andExpect(jsonPath("$.items[0].conflictDetails[0].courseId").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.items[0].conflictDetails[0].pendingIndex").value(1))
                .andExpect(jsonPath("$.items[0].conflictDetails[0].courseName").value("物理"))
                .andExpect(jsonPath("$.items[0].conflictDetails[0].reason").value("老师 张老师 在时间段 周一 08:00-10:00 与本次批量待排课程冲突"))
                .andExpect(jsonPath("$.items[0].conflictDetails[1].conflictType").value("CLASSROOM"))
                .andExpect(jsonPath("$.items[0].conflictDetails[1].sourceType").value("PENDING_ITEM"))
                .andExpect(jsonPath("$.items[0].conflictDetails[1].courseId").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.items[0].conflictDetails[1].pendingIndex").value(1))
                .andExpect(jsonPath("$.items[1].originalIndex").value(1))
                .andExpect(jsonPath("$.items[1].canSchedule").value(false))
                .andExpect(jsonPath("$.items[1].conflictCount").value(2));

        mockMvc.perform(get("/api/schedules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void testPreCheckByTeacherBatch_MixedConflicts_Returns200WithConflicts() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("已有课程", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        List<TeacherBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        items.add(createBatchItem("数学", "B202", "周一 08:00-10:00"));
        items.add(createBatchItem("物理", "C303", "周一 08:00-10:00"));
        items.add(createBatchItem("化学", "D404", "周二 08:00-10:00"));

        TeacherBatchPreCheckRequest request = createBatchRequest("张老师", items);

        mockMvc.perform(post("/api/schedules/pre-check/teacher-batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canSchedule").value(false))
                .andExpect(jsonPath("$.totalConflictCount").value(4))
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[0].originalIndex").value(0))
                .andExpect(jsonPath("$.items[0].canSchedule").value(false))
                .andExpect(jsonPath("$.items[0].conflictCount").value(2))
                .andExpect(jsonPath("$.items[1].originalIndex").value(1))
                .andExpect(jsonPath("$.items[1].canSchedule").value(false))
                .andExpect(jsonPath("$.items[1].conflictCount").value(2))
                .andExpect(jsonPath("$.items[2].originalIndex").value(2))
                .andExpect(jsonPath("$.items[2].canSchedule").value(true))
                .andExpect(jsonPath("$.items[2].conflictCount").value(0));

        mockMvc.perform(get("/api/schedules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void testPreCheckByTeacherBatch_EmptyList_Returns200() throws Exception {
        List<TeacherBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        TeacherBatchPreCheckRequest request = createBatchRequest("张老师", items);

        mockMvc.perform(post("/api/schedules/pre-check/teacher-batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canSchedule").value(true))
                .andExpect(jsonPath("$.totalConflictCount").value(0))
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void testPreCheckByTeacherBatch_NullRequest_Returns400() throws Exception {
        mockMvc.perform(post("/api/schedules/pre-check/teacher-batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("null"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("请求不能为空"));
    }

    @Test
    void testPreCheckByTeacherBatch_NullRequestBody_Returns400() throws Exception {
        mockMvc.perform(post("/api/schedules/pre-check/teacher-batch")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testPreCheckByTeacherBatch_EmptyTeacherName_Returns400() throws Exception {
        List<TeacherBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        items.add(createBatchItem("数学", "A101", "周一 08:00-10:00"));

        TeacherBatchPreCheckRequest request = createBatchRequest("", items);

        mockMvc.perform(post("/api/schedules/pre-check/teacher-batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("老师名不能为空"));
    }

    @Test
    void testPreCheckByTeacherBatch_NullItems_Returns400() throws Exception {
        TeacherBatchPreCheckRequest request = createBatchRequest("张老师", null);

        mockMvc.perform(post("/api/schedules/pre-check/teacher-batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("待排课程列表不能为空"));
    }

    @Test
    void testPreCheckByTeacherBatch_EmptyCourseName_Returns400() throws Exception {
        List<TeacherBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        items.add(createBatchItem("", "A101", "周一 08:00-10:00"));

        TeacherBatchPreCheckRequest request = createBatchRequest("张老师", items);

        mockMvc.perform(post("/api/schedules/pre-check/teacher-batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("第 1 项课程名不能为空"));
    }

    @Test
    void testPreCheckByTeacherBatch_EmptyClassroom_Returns400() throws Exception {
        List<TeacherBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        items.add(createBatchItem("数学", "", "周一 08:00-10:00"));

        TeacherBatchPreCheckRequest request = createBatchRequest("张老师", items);

        mockMvc.perform(post("/api/schedules/pre-check/teacher-batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("第 1 项教室名不能为空"));
    }

    @Test
    void testPreCheckByTeacherBatch_EmptyTimeSlot_Returns400() throws Exception {
        List<TeacherBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        items.add(createBatchItem("数学", "A101", ""));

        TeacherBatchPreCheckRequest request = createBatchRequest("张老师", items);

        mockMvc.perform(post("/api/schedules/pre-check/teacher-batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("第 1 项时间段不能为空"));
    }

    @Test
    void testPreCheckByTeacherBatch_InvalidTimeSlotFormat_Returns400() throws Exception {
        List<TeacherBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        items.add(createBatchItem("数学", "A101", "明天上午"));

        TeacherBatchPreCheckRequest request = createBatchRequest("张老师", items);

        mockMvc.perform(post("/api/schedules/pre-check/teacher-batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void testPreCheckByTeacherBatch_InvalidTimeSlotStartAfterEnd_Returns400() throws Exception {
        List<TeacherBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        items.add(createBatchItem("数学", "A101", "周一 10:00-08:00"));

        TeacherBatchPreCheckRequest request = createBatchRequest("张老师", items);

        mockMvc.perform(post("/api/schedules/pre-check/teacher-batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void testPreCheckByTeacherBatch_NoCourseCreated() throws Exception {
        List<TeacherBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        items.add(createBatchItem("数学", "A101", "周一 08:00-10:00"));
        items.add(createBatchItem("物理", "B202", "周二 08:00-10:00"));

        TeacherBatchPreCheckRequest request = createBatchRequest("张老师", items);

        mockMvc.perform(post("/api/schedules/pre-check/teacher-batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/schedules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void testPreCheckByTeacherBatch_NoAuditLogCreated() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("已有课程", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        String logsBefore = mockMvc.perform(get("/api/schedules/audit-logs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        List<Map<String, Object>> logsBeforeList = objectMapper.readValue(
                logsBefore,
                new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() {});
        int logCountBefore = logsBeforeList.size();

        List<TeacherBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        items.add(createBatchItem("数学", "B202", "周一 08:00-10:00"));
        items.add(createBatchItem("物理", "C303", "周一 08:00-10:00"));

        TeacherBatchPreCheckRequest request = createBatchRequest("张老师", items);

        mockMvc.perform(post("/api/schedules/pre-check/teacher-batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        String logsAfter = mockMvc.perform(get("/api/schedules/audit-logs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        List<Map<String, Object>> logsAfterList = objectMapper.readValue(
                logsAfter,
                new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() {});
        assertEquals(logCountBefore, logsAfterList.size());
    }

    @Test
    void testPreCheckByTeacherBatch_ResultsInInputOrder() throws Exception {
        List<TeacherBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        items.add(createBatchItem("课程A", "A101", "周三 08:00-10:00"));
        items.add(createBatchItem("课程B", "B202", "周一 08:00-10:00"));
        items.add(createBatchItem("课程C", "C303", "周二 08:00-10:00"));

        TeacherBatchPreCheckRequest request = createBatchRequest("张老师", items);

        mockMvc.perform(post("/api/schedules/pre-check/teacher-batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[0].originalIndex").value(0))
                .andExpect(jsonPath("$.items[0].courseName").value("课程A"))
                .andExpect(jsonPath("$.items[1].originalIndex").value(1))
                .andExpect(jsonPath("$.items[1].courseName").value("课程B"))
                .andExpect(jsonPath("$.items[2].originalIndex").value(2))
                .andExpect(jsonPath("$.items[2].courseName").value("课程C"));
    }

    @Test
    void testPreCheckByTeacherBatch_ConflictDetailsSortedCorrectly() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("课程1", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("课程2", "李老师", "B202", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        List<TeacherBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        items.add(createBatchItem("数学", "B202", "周一 08:00-10:00"));

        TeacherBatchPreCheckRequest request = createBatchRequest("张老师", items);

        mockMvc.perform(post("/api/schedules/pre-check/teacher-batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].conflictCount").value(2))
                .andExpect(jsonPath("$.items[0].conflictDetails[0].conflictType").value("TEACHER"))
                .andExpect(jsonPath("$.items[0].conflictDetails[1].conflictType").value("CLASSROOM"));
    }

    @Test
    void testPreCheckByTeacherBatch_InvalidJson_Returns400() throws Exception {
        mockMvc.perform(post("/api/schedules/pre-check/teacher-batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("invalid json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testPreCheckByTeacherBatch_EmptyRequestBody_Returns400() throws Exception {
        mockMvc.perform(post("/api/schedules/pre-check/teacher-batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("老师名不能为空"));
    }

    private ClassroomBatchPreCheckItemRequest createClassroomBatchItem(String courseName, String teacherName, String timeSlot) {
        ClassroomBatchPreCheckItemRequest item = new ClassroomBatchPreCheckItemRequest();
        item.setCourseName(courseName);
        item.setTeacherName(teacherName);
        item.setTimeSlot(timeSlot);
        return item;
    }

    private ClassroomBatchPreCheckRequest createClassroomBatchRequest(String classroom, List<ClassroomBatchPreCheckItemRequest> items) {
        ClassroomBatchPreCheckRequest request = new ClassroomBatchPreCheckRequest();
        request.setClassroom(classroom);
        request.setItems(items);
        return request;
    }

    @Test
    void testPreCheckByClassroomBatch_AllCanSchedule_Returns200() throws Exception {
        List<ClassroomBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        items.add(createClassroomBatchItem("数学", "张老师", "周一 08:00-10:00"));
        items.add(createClassroomBatchItem("物理", "李老师", "周一 10:00-12:00"));

        ClassroomBatchPreCheckRequest request = createClassroomBatchRequest("A101", items);

        mockMvc.perform(post("/api/schedules/pre-check/classroom-batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canSchedule").value(true))
                .andExpect(jsonPath("$.totalConflictCount").value(0))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].originalIndex").value(0))
                .andExpect(jsonPath("$.items[0].canSchedule").value(true))
                .andExpect(jsonPath("$.items[0].conflictCount").value(0))
                .andExpect(jsonPath("$.items[1].originalIndex").value(1))
                .andExpect(jsonPath("$.items[1].canSchedule").value(true))
                .andExpect(jsonPath("$.items[1].conflictCount").value(0));

        mockMvc.perform(get("/api/schedules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void testPreCheckByClassroomBatch_ConflictWithExisting_Returns200WithConflicts() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("已有课程", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        List<ClassroomBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        items.add(createClassroomBatchItem("数学", "张老师", "周一 08:00-10:00"));
        items.add(createClassroomBatchItem("物理", "李老师", "周二 08:00-10:00"));

        ClassroomBatchPreCheckRequest request = createClassroomBatchRequest("A101", items);

        mockMvc.perform(post("/api/schedules/pre-check/classroom-batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canSchedule").value(false))
                .andExpect(jsonPath("$.totalConflictCount").value(2))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].originalIndex").value(0))
                .andExpect(jsonPath("$.items[0].canSchedule").value(false))
                .andExpect(jsonPath("$.items[0].conflictCount").value(2))
                .andExpect(jsonPath("$.items[0].conflictDetails[0].conflictType").value("TEACHER"))
                .andExpect(jsonPath("$.items[0].conflictDetails[0].sourceType").value("EXISTING_COURSE"))
                .andExpect(jsonPath("$.items[0].conflictDetails[0].courseId").value(1))
                .andExpect(jsonPath("$.items[0].conflictDetails[0].pendingIndex").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.items[0].conflictDetails[0].courseName").value("已有课程"))
                .andExpect(jsonPath("$.items[0].conflictDetails[0].reason").value("老师 张老师 在时间段 周一 08:00-10:00 已有课程安排"))
                .andExpect(jsonPath("$.items[1].originalIndex").value(1))
                .andExpect(jsonPath("$.items[1].canSchedule").value(true))
                .andExpect(jsonPath("$.items[1].conflictCount").value(0));

        mockMvc.perform(get("/api/schedules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void testPreCheckByClassroomBatch_InternalConflict_Returns200WithConflicts() throws Exception {
        List<ClassroomBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        items.add(createClassroomBatchItem("数学", "张老师", "周一 08:00-10:00"));
        items.add(createClassroomBatchItem("物理", "李老师", "周一 08:00-10:00"));

        ClassroomBatchPreCheckRequest request = createClassroomBatchRequest("A101", items);

        mockMvc.perform(post("/api/schedules/pre-check/classroom-batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canSchedule").value(false))
                .andExpect(jsonPath("$.totalConflictCount").value(2))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].originalIndex").value(0))
                .andExpect(jsonPath("$.items[0].canSchedule").value(false))
                .andExpect(jsonPath("$.items[0].conflictCount").value(1))
                .andExpect(jsonPath("$.items[0].conflictDetails[0].conflictType").value("CLASSROOM"))
                .andExpect(jsonPath("$.items[0].conflictDetails[0].sourceType").value("PENDING_ITEM"))
                .andExpect(jsonPath("$.items[0].conflictDetails[0].courseId").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.items[0].conflictDetails[0].pendingIndex").value(1))
                .andExpect(jsonPath("$.items[1].originalIndex").value(1))
                .andExpect(jsonPath("$.items[1].canSchedule").value(false))
                .andExpect(jsonPath("$.items[1].conflictCount").value(1));

        mockMvc.perform(get("/api/schedules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void testPreCheckByClassroomBatch_EmptyList_Returns200() throws Exception {
        List<ClassroomBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        ClassroomBatchPreCheckRequest request = createClassroomBatchRequest("A101", items);

        mockMvc.perform(post("/api/schedules/pre-check/classroom-batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canSchedule").value(true))
                .andExpect(jsonPath("$.totalConflictCount").value(0))
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void testPreCheckByClassroomBatch_NullRequest_Returns400() throws Exception {
        mockMvc.perform(post("/api/schedules/pre-check/classroom-batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("null"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("请求不能为空"));
    }

    @Test
    void testPreCheckByClassroomBatch_NullRequestBody_Returns400() throws Exception {
        mockMvc.perform(post("/api/schedules/pre-check/classroom-batch")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testPreCheckByClassroomBatch_EmptyClassroom_Returns400() throws Exception {
        List<ClassroomBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        items.add(createClassroomBatchItem("数学", "张老师", "周一 08:00-10:00"));

        ClassroomBatchPreCheckRequest request = createClassroomBatchRequest("", items);

        mockMvc.perform(post("/api/schedules/pre-check/classroom-batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("教室名不能为空"));
    }

    @Test
    void testPreCheckByClassroomBatch_NullItems_Returns400() throws Exception {
        ClassroomBatchPreCheckRequest request = createClassroomBatchRequest("A101", null);

        mockMvc.perform(post("/api/schedules/pre-check/classroom-batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("待排课程列表不能为空"));
    }

    @Test
    void testPreCheckByClassroomBatch_EmptyCourseName_Returns400() throws Exception {
        List<ClassroomBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        items.add(createClassroomBatchItem("", "张老师", "周一 08:00-10:00"));

        ClassroomBatchPreCheckRequest request = createClassroomBatchRequest("A101", items);

        mockMvc.perform(post("/api/schedules/pre-check/classroom-batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("第 1 项课程名不能为空"));
    }

    @Test
    void testPreCheckByClassroomBatch_EmptyTeacherName_Returns400() throws Exception {
        List<ClassroomBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        items.add(createClassroomBatchItem("数学", "", "周一 08:00-10:00"));

        ClassroomBatchPreCheckRequest request = createClassroomBatchRequest("A101", items);

        mockMvc.perform(post("/api/schedules/pre-check/classroom-batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("第 1 项老师名不能为空"));
    }

    @Test
    void testPreCheckByClassroomBatch_EmptyTimeSlot_Returns400() throws Exception {
        List<ClassroomBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        items.add(createClassroomBatchItem("数学", "张老师", ""));

        ClassroomBatchPreCheckRequest request = createClassroomBatchRequest("A101", items);

        mockMvc.perform(post("/api/schedules/pre-check/classroom-batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("第 1 项时间段不能为空"));
    }

    @Test
    void testPreCheckByClassroomBatch_InvalidTimeSlotFormat_Returns400() throws Exception {
        List<ClassroomBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        items.add(createClassroomBatchItem("数学", "张老师", "明天上午"));

        ClassroomBatchPreCheckRequest request = createClassroomBatchRequest("A101", items);

        mockMvc.perform(post("/api/schedules/pre-check/classroom-batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void testPreCheckByClassroomBatch_InvalidTimeSlotStartAfterEnd_Returns400() throws Exception {
        List<ClassroomBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        items.add(createClassroomBatchItem("数学", "张老师", "周一 10:00-08:00"));

        ClassroomBatchPreCheckRequest request = createClassroomBatchRequest("A101", items);

        mockMvc.perform(post("/api/schedules/pre-check/classroom-batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void testPreCheckByClassroomBatch_NoCourseCreated() throws Exception {
        List<ClassroomBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        items.add(createClassroomBatchItem("数学", "张老师", "周一 08:00-10:00"));
        items.add(createClassroomBatchItem("物理", "李老师", "周二 08:00-10:00"));

        ClassroomBatchPreCheckRequest request = createClassroomBatchRequest("A101", items);

        mockMvc.perform(post("/api/schedules/pre-check/classroom-batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/schedules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void testPreCheckByClassroomBatch_NoAuditLogCreated() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("已有课程", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        String logsBefore = mockMvc.perform(get("/api/schedules/audit-logs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        List<Map<String, Object>> logsBeforeList = objectMapper.readValue(
                logsBefore,
                new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() {});
        int logCountBefore = logsBeforeList.size();

        List<ClassroomBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        items.add(createClassroomBatchItem("数学", "李老师", "周一 08:00-10:00"));
        items.add(createClassroomBatchItem("物理", "王老师", "周一 08:00-10:00"));

        ClassroomBatchPreCheckRequest request = createClassroomBatchRequest("A101", items);

        mockMvc.perform(post("/api/schedules/pre-check/classroom-batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        String logsAfter = mockMvc.perform(get("/api/schedules/audit-logs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        List<Map<String, Object>> logsAfterList = objectMapper.readValue(
                logsAfter,
                new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() {});
        assertEquals(logCountBefore, logsAfterList.size());
    }

    @Test
    void testPreCheckByClassroomBatch_ResultsInInputOrder() throws Exception {
        List<ClassroomBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        items.add(createClassroomBatchItem("课程A", "张老师", "周三 08:00-10:00"));
        items.add(createClassroomBatchItem("课程B", "李老师", "周一 08:00-10:00"));
        items.add(createClassroomBatchItem("课程C", "王老师", "周二 08:00-10:00"));

        ClassroomBatchPreCheckRequest request = createClassroomBatchRequest("A101", items);

        mockMvc.perform(post("/api/schedules/pre-check/classroom-batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[0].originalIndex").value(0))
                .andExpect(jsonPath("$.items[0].courseName").value("课程A"))
                .andExpect(jsonPath("$.items[1].originalIndex").value(1))
                .andExpect(jsonPath("$.items[1].courseName").value("课程B"))
                .andExpect(jsonPath("$.items[2].originalIndex").value(2))
                .andExpect(jsonPath("$.items[2].courseName").value("课程C"));
    }

    @Test
    void testPreCheckByClassroomBatch_ConflictDetailsSortedCorrectly() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("课程1", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("课程2", "李老师", "B202", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        List<ClassroomBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        items.add(createClassroomBatchItem("数学", "张老师", "周一 08:00-10:00"));

        ClassroomBatchPreCheckRequest request = createClassroomBatchRequest("B202", items);

        mockMvc.perform(post("/api/schedules/pre-check/classroom-batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].conflictCount").value(2))
                .andExpect(jsonPath("$.items[0].conflictDetails[0].conflictType").value("TEACHER"))
                .andExpect(jsonPath("$.items[0].conflictDetails[1].conflictType").value("CLASSROOM"));
    }

    @Test
    void testPreCheckByClassroomBatch_InvalidJson_Returns400() throws Exception {
        mockMvc.perform(post("/api/schedules/pre-check/classroom-batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("invalid json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testPreCheckByClassroomBatch_EmptyRequestBody_Returns400() throws Exception {
        mockMvc.perform(post("/api/schedules/pre-check/classroom-batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("教室名不能为空"));
    }

    @Test
    void testExportPreCheck_BasicLink_ReturnsCsv() throws Exception {
        CourseScheduleConflictPreCheckRequest request = createPreCheckRequest(
                "物理", "李老师", "B202", "周二 14:00-16:00");

        mockMvc.perform(post("/api/schedules/pre-check/export")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("text/csv")))
                .andExpect(header().exists("Content-Disposition"));
    }

    @Test
    void testExportPreCheck_NoAuditLogCreated() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("数学", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        int logCountBefore = auditLogService.getAllLogs().size();

        CourseScheduleConflictPreCheckRequest request = createPreCheckRequest(
                "物理", "张老师", "B202", "周一 08:00-10:00");

        mockMvc.perform(post("/api/schedules/pre-check/export")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        int logCountAfter = auditLogService.getAllLogs().size();
        assertEquals(logCountBefore, logCountAfter);
    }

    @Test
    void testExportPreCheck_NoCourseCreated() throws Exception {
        CourseScheduleConflictPreCheckRequest request = createPreCheckRequest(
                "数学", "张老师", "A101", "周一 08:00-10:00");

        mockMvc.perform(post("/api/schedules/pre-check/export")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        assertEquals(0, scheduleService.findSchedules(null).size());
    }

    @Test
    void testExportTeacherBatchPreCheck_BasicLink_ReturnsCsv() throws Exception {
        List<TeacherBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        items.add(createBatchItem("数学", "A101", "周一 08:00-10:00"));
        items.add(createBatchItem("物理", "A101", "周二 08:00-10:00"));

        TeacherBatchPreCheckRequest request = createBatchRequest("张老师", items);

        mockMvc.perform(post("/api/schedules/pre-check/teacher-batch/export")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("text/csv")))
                .andExpect(header().exists("Content-Disposition"));
    }

    @Test
    void testExportTeacherBatchPreCheck_NoAuditLogCreated() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("已有课程", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        int logCountBefore = auditLogService.getAllLogs().size();

        List<TeacherBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        items.add(createBatchItem("数学", "B202", "周一 08:00-10:00"));

        TeacherBatchPreCheckRequest request = createBatchRequest("张老师", items);

        mockMvc.perform(post("/api/schedules/pre-check/teacher-batch/export")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        int logCountAfter = auditLogService.getAllLogs().size();
        assertEquals(logCountBefore, logCountAfter);
    }

    @Test
    void testExportTeacherBatchPreCheck_NoCourseCreated() throws Exception {
        List<TeacherBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        items.add(createBatchItem("数学", "A101", "周一 08:00-10:00"));

        TeacherBatchPreCheckRequest request = createBatchRequest("张老师", items);

        mockMvc.perform(post("/api/schedules/pre-check/teacher-batch/export")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        assertEquals(0, scheduleService.findSchedules(null).size());
    }

    @Test
    void testExportClassroomBatchPreCheck_BasicLink_ReturnsCsv() throws Exception {
        List<ClassroomBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        items.add(createClassroomBatchItem("数学", "张老师", "周一 08:00-10:00"));
        items.add(createClassroomBatchItem("物理", "李老师", "周二 08:00-10:00"));

        ClassroomBatchPreCheckRequest request = createClassroomBatchRequest("A101", items);

        mockMvc.perform(post("/api/schedules/pre-check/classroom-batch/export")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("text/csv")))
                .andExpect(header().exists("Content-Disposition"));
    }

    @Test
    void testExportClassroomBatchPreCheck_NoAuditLogCreated() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("已有课程", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        int logCountBefore = auditLogService.getAllLogs().size();

        List<ClassroomBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        items.add(createClassroomBatchItem("数学", "李老师", "周一 08:00-10:00"));

        ClassroomBatchPreCheckRequest request = createClassroomBatchRequest("A101", items);

        mockMvc.perform(post("/api/schedules/pre-check/classroom-batch/export")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        int logCountAfter = auditLogService.getAllLogs().size();
        assertEquals(logCountBefore, logCountAfter);
    }

    @Test
    void testExportClassroomBatchPreCheck_NoCourseCreated() throws Exception {
        List<ClassroomBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        items.add(createClassroomBatchItem("数学", "张老师", "周一 08:00-10:00"));

        ClassroomBatchPreCheckRequest request = createClassroomBatchRequest("A101", items);

        mockMvc.perform(post("/api/schedules/pre-check/classroom-batch/export")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        assertEquals(0, scheduleService.findSchedules(null).size());
    }

    private TeacherBatchPreCheckItemRequest createTeacherBatchItem(
            String courseName, String classroom, String timeSlot) {
        TeacherBatchPreCheckItemRequest item = new TeacherBatchPreCheckItemRequest();
        item.setCourseName(courseName);
        item.setClassroom(classroom);
        item.setTimeSlot(timeSlot);
        return item;
    }

    private TeacherBatchPreCheckRequest createTeacherBatchRequest(
            String teacherName, java.util.List<TeacherBatchPreCheckItemRequest> items) {
        TeacherBatchPreCheckRequest request = new TeacherBatchPreCheckRequest();
        request.setTeacherName(teacherName);
        request.setItems(items);
        return request;
    }

    @Test
    void testPreCheckConflictsSummary_NoConflict() throws Exception {
        CourseScheduleConflictPreCheckRequest request = createPreCheckRequest(
                "数学", "张老师", "A101", "周一 08:00-10:00");

        mockMvc.perform(post("/api/schedules/pre-check/summary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preCheckType").value("SINGLE"))
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.schedulableItems").value(1))
                .andExpect(jsonPath("$.unschedulableItems").value(0))
                .andExpect(jsonPath("$.totalConflicts").value(0))
                .andExpect(jsonPath("$.teacherConflicts").value(0))
                .andExpect(jsonPath("$.classroomConflicts").value(0))
                .andExpect(jsonPath("$.existingCourseConflicts").value(0))
                .andExpect(jsonPath("$.pendingItemConflicts").value(0));
    }

    @Test
    void testPreCheckConflictsSummary_WithConflict() throws Exception {
        CourseScheduleCreateRequest existingRequest = createRequest("已有课程", "张老师", "B202", "周一 08:00-10:00");
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(existingRequest)))
                .andExpect(status().isCreated());

        CourseScheduleConflictPreCheckRequest request = createPreCheckRequest(
                "数学", "张老师", "A101", "周一 08:00-10:00");

        mockMvc.perform(post("/api/schedules/pre-check/summary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preCheckType").value("SINGLE"))
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.schedulableItems").value(0))
                .andExpect(jsonPath("$.unschedulableItems").value(1))
                .andExpect(jsonPath("$.totalConflicts").value(1))
                .andExpect(jsonPath("$.teacherConflicts").value(1))
                .andExpect(jsonPath("$.classroomConflicts").value(0))
                .andExpect(jsonPath("$.existingCourseConflicts").value(1))
                .andExpect(jsonPath("$.pendingItemConflicts").value(0));
    }

    @Test
    void testPreCheckConflictsSummary_NoAuditLogCreated() throws Exception {
        java.util.List<AuditLogResponse> logsBefore = auditLogService.getAllLogs();
        int logCountBefore = logsBefore.size();

        CourseScheduleConflictPreCheckRequest request = createPreCheckRequest(
                "数学", "张老师", "A101", "周一 08:00-10:00");

        mockMvc.perform(post("/api/schedules/pre-check/summary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        java.util.List<AuditLogResponse> logsAfter = auditLogService.getAllLogs();
        assertEquals(logCountBefore, logsAfter.size());
    }

    @Test
    void testPreCheckConflictsSummary_NoCourseCreated() throws Exception {
        CourseScheduleConflictPreCheckRequest request = createPreCheckRequest(
                "数学", "张老师", "A101", "周一 08:00-10:00");

        mockMvc.perform(post("/api/schedules/pre-check/summary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        assertEquals(0, scheduleService.findSchedules(null).size());
    }

    @Test
    void testPreCheckConflictsByTeacherBatchSummary_AllCanSchedule() throws Exception {
        java.util.List<TeacherBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        items.add(createTeacherBatchItem("数学", "A101", "周一 08:00-10:00"));
        items.add(createTeacherBatchItem("物理", "B202", "周二 08:00-10:00"));

        TeacherBatchPreCheckRequest request = createTeacherBatchRequest("张老师", items);

        mockMvc.perform(post("/api/schedules/pre-check/teacher-batch/summary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preCheckType").value("TEACHER_BATCH"))
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.schedulableItems").value(2))
                .andExpect(jsonPath("$.unschedulableItems").value(0))
                .andExpect(jsonPath("$.totalConflicts").value(0))
                .andExpect(jsonPath("$.teacherConflicts").value(0))
                .andExpect(jsonPath("$.classroomConflicts").value(0))
                .andExpect(jsonPath("$.existingCourseConflicts").value(0))
                .andExpect(jsonPath("$.pendingItemConflicts").value(0));
    }

    @Test
    void testPreCheckConflictsByTeacherBatchSummary_MixedResults() throws Exception {
        CourseScheduleCreateRequest existingRequest = createRequest("已有课程", "张老师", "A101", "周一 08:00-10:00");
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(existingRequest)))
                .andExpect(status().isCreated());

        java.util.List<TeacherBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        items.add(createTeacherBatchItem("数学", "B202", "周一 08:00-10:00"));
        items.add(createTeacherBatchItem("物理", "C303", "周二 08:00-10:00"));

        TeacherBatchPreCheckRequest request = createTeacherBatchRequest("张老师", items);

        mockMvc.perform(post("/api/schedules/pre-check/teacher-batch/summary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preCheckType").value("TEACHER_BATCH"))
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.schedulableItems").value(1))
                .andExpect(jsonPath("$.unschedulableItems").value(1))
                .andExpect(jsonPath("$.totalConflicts").value(1))
                .andExpect(jsonPath("$.teacherConflicts").value(1))
                .andExpect(jsonPath("$.classroomConflicts").value(0))
                .andExpect(jsonPath("$.existingCourseConflicts").value(1))
                .andExpect(jsonPath("$.pendingItemConflicts").value(0));
    }

    @Test
    void testPreCheckConflictsByTeacherBatchSummary_SourceTypeStatistics() throws Exception {
        CourseScheduleCreateRequest existingRequest = createRequest("已有课程", "张老师", "A101", "周一 08:00-10:00");
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(existingRequest)))
                .andExpect(status().isCreated());

        java.util.List<TeacherBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        items.add(createTeacherBatchItem("数学", "B202", "周一 08:00-10:00"));
        items.add(createTeacherBatchItem("物理", "C303", "周一 08:00-10:00"));
        items.add(createTeacherBatchItem("化学", "D404", "周二 08:00-10:00"));

        TeacherBatchPreCheckRequest request = createTeacherBatchRequest("张老师", items);

        mockMvc.perform(post("/api/schedules/pre-check/teacher-batch/summary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preCheckType").value("TEACHER_BATCH"))
                .andExpect(jsonPath("$.totalItems").value(3))
                .andExpect(jsonPath("$.schedulableItems").value(1))
                .andExpect(jsonPath("$.unschedulableItems").value(2))
                .andExpect(jsonPath("$.totalConflicts").value(4))
                .andExpect(jsonPath("$.teacherConflicts").value(4))
                .andExpect(jsonPath("$.classroomConflicts").value(0))
                .andExpect(jsonPath("$.existingCourseConflicts").value(2))
                .andExpect(jsonPath("$.pendingItemConflicts").value(2));
    }

    @Test
    void testPreCheckConflictsByTeacherBatchSummary_NoAuditLogCreated() throws Exception {
        CourseScheduleCreateRequest existingRequest = createRequest("已有课程", "张老师", "A101", "周一 08:00-10:00");
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(existingRequest)))
                .andExpect(status().isCreated());

        java.util.List<AuditLogResponse> logsBefore = auditLogService.getAllLogs();
        int logCountBefore = logsBefore.size();

        java.util.List<TeacherBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        items.add(createTeacherBatchItem("数学", "B202", "周一 08:00-10:00"));

        TeacherBatchPreCheckRequest request = createTeacherBatchRequest("张老师", items);

        mockMvc.perform(post("/api/schedules/pre-check/teacher-batch/summary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        java.util.List<AuditLogResponse> logsAfter = auditLogService.getAllLogs();
        assertEquals(logCountBefore, logsAfter.size());
    }

    @Test
    void testPreCheckConflictsByClassroomBatchSummary_AllCanSchedule() throws Exception {
        java.util.List<ClassroomBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        items.add(createClassroomBatchItem("数学", "张老师", "周一 08:00-10:00"));
        items.add(createClassroomBatchItem("物理", "李老师", "周二 08:00-10:00"));

        ClassroomBatchPreCheckRequest request = createClassroomBatchRequest("A101", items);

        mockMvc.perform(post("/api/schedules/pre-check/classroom-batch/summary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preCheckType").value("CLASSROOM_BATCH"))
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.schedulableItems").value(2))
                .andExpect(jsonPath("$.unschedulableItems").value(0))
                .andExpect(jsonPath("$.totalConflicts").value(0))
                .andExpect(jsonPath("$.teacherConflicts").value(0))
                .andExpect(jsonPath("$.classroomConflicts").value(0))
                .andExpect(jsonPath("$.existingCourseConflicts").value(0))
                .andExpect(jsonPath("$.pendingItemConflicts").value(0));
    }

    @Test
    void testPreCheckConflictsByClassroomBatchSummary_MixedResults() throws Exception {
        CourseScheduleCreateRequest existingRequest = createRequest("已有课程", "赵老师", "A101", "周一 08:00-10:00");
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(existingRequest)))
                .andExpect(status().isCreated());

        java.util.List<ClassroomBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        items.add(createClassroomBatchItem("数学", "张老师", "周一 08:00-10:00"));
        items.add(createClassroomBatchItem("物理", "李老师", "周二 08:00-10:00"));

        ClassroomBatchPreCheckRequest request = createClassroomBatchRequest("A101", items);

        mockMvc.perform(post("/api/schedules/pre-check/classroom-batch/summary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preCheckType").value("CLASSROOM_BATCH"))
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.schedulableItems").value(1))
                .andExpect(jsonPath("$.unschedulableItems").value(1))
                .andExpect(jsonPath("$.totalConflicts").value(1))
                .andExpect(jsonPath("$.teacherConflicts").value(0))
                .andExpect(jsonPath("$.classroomConflicts").value(1))
                .andExpect(jsonPath("$.existingCourseConflicts").value(1))
                .andExpect(jsonPath("$.pendingItemConflicts").value(0));
    }

    @Test
    void testPreCheckConflictsByClassroomBatchSummary_SourceTypeStatistics() throws Exception {
        CourseScheduleCreateRequest existingRequest = createRequest("已有课程", "赵老师", "A101", "周一 08:00-10:00");
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(existingRequest)))
                .andExpect(status().isCreated());

        java.util.List<ClassroomBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        items.add(createClassroomBatchItem("数学", "张老师", "周一 08:00-10:00"));
        items.add(createClassroomBatchItem("物理", "张老师", "周一 08:00-10:00"));
        items.add(createClassroomBatchItem("化学", "王老师", "周二 08:00-10:00"));

        ClassroomBatchPreCheckRequest request = createClassroomBatchRequest("A101", items);

        mockMvc.perform(post("/api/schedules/pre-check/classroom-batch/summary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preCheckType").value("CLASSROOM_BATCH"))
                .andExpect(jsonPath("$.totalItems").value(3))
                .andExpect(jsonPath("$.schedulableItems").value(1))
                .andExpect(jsonPath("$.unschedulableItems").value(2))
                .andExpect(jsonPath("$.totalConflicts").value(6))
                .andExpect(jsonPath("$.teacherConflicts").value(2))
                .andExpect(jsonPath("$.classroomConflicts").value(4))
                .andExpect(jsonPath("$.existingCourseConflicts").value(2))
                .andExpect(jsonPath("$.pendingItemConflicts").value(4));
    }

    @Test
    void testPreCheckConflictsByClassroomBatchSummary_NoAuditLogCreated() throws Exception {
        CourseScheduleCreateRequest existingRequest = createRequest("已有课程", "张老师", "A101", "周一 08:00-10:00");
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(existingRequest)))
                .andExpect(status().isCreated());

        java.util.List<AuditLogResponse> logsBefore = auditLogService.getAllLogs();
        int logCountBefore = logsBefore.size();

        java.util.List<ClassroomBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        items.add(createClassroomBatchItem("数学", "李老师", "周一 08:00-10:00"));

        ClassroomBatchPreCheckRequest request = createClassroomBatchRequest("A101", items);

        mockMvc.perform(post("/api/schedules/pre-check/classroom-batch/summary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        java.util.List<AuditLogResponse> logsAfter = auditLogService.getAllLogs();
        assertEquals(logCountBefore, logsAfter.size());
    }

    @Test
    void testGroupConflictsBySource_NoConflicts_ReturnsEmptyList() throws Exception {
        CourseScheduleConflictPreCheckRequest request = new CourseScheduleConflictPreCheckRequest();
        request.setCourseName("数学");
        request.setTeacherName("张老师");
        request.setClassroom("A101");
        request.setTimeSlot("周一 08:00-10:00");

        mockMvc.perform(post("/api/schedules/pre-check/grouped-by-source")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void testGroupConflictsBySource_OnlyExistingCourseConflicts() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        CourseScheduleConflictPreCheckRequest request = new CourseScheduleConflictPreCheckRequest();
        request.setCourseName("物理");
        request.setTeacherName("张老师");
        request.setClassroom("A101");
        request.setTimeSlot("周一 08:00-10:00");

        mockMvc.perform(post("/api/schedules/pre-check/grouped-by-source")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].sourceType").value("EXISTING_COURSE"))
                .andExpect(jsonPath("$[0].conflictCount").value(2))
                .andExpect(jsonPath("$[0].teacherConflictCount").value(1))
                .andExpect(jsonPath("$[0].classroomConflictCount").value(1))
                .andExpect(jsonPath("$[0].courseNames").isArray())
                .andExpect(jsonPath("$[0].courseNames.length()").value(1))
                .andExpect(jsonPath("$[0].courseNames[0]").value("数学"));
    }

    @Test
    void testGroupConflictsBySource_OnlyTeacherConflict() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        CourseScheduleConflictPreCheckRequest request = new CourseScheduleConflictPreCheckRequest();
        request.setCourseName("物理");
        request.setTeacherName("张老师");
        request.setClassroom("B202");
        request.setTimeSlot("周一 08:00-10:00");

        mockMvc.perform(post("/api/schedules/pre-check/grouped-by-source")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].sourceType").value("EXISTING_COURSE"))
                .andExpect(jsonPath("$[0].conflictCount").value(1))
                .andExpect(jsonPath("$[0].teacherConflictCount").value(1))
                .andExpect(jsonPath("$[0].classroomConflictCount").value(0));
    }

    @Test
    void testGroupConflictsBySource_OnlyClassroomConflict() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        CourseScheduleConflictPreCheckRequest request = new CourseScheduleConflictPreCheckRequest();
        request.setCourseName("物理");
        request.setTeacherName("李老师");
        request.setClassroom("A101");
        request.setTimeSlot("周一 08:00-10:00");

        mockMvc.perform(post("/api/schedules/pre-check/grouped-by-source")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].sourceType").value("EXISTING_COURSE"))
                .andExpect(jsonPath("$[0].conflictCount").value(1))
                .andExpect(jsonPath("$[0].teacherConflictCount").value(0))
                .andExpect(jsonPath("$[0].classroomConflictCount").value(1));
    }

    @Test
    void testGroupConflictsBySource_MultipleExistingCoursesWithDifferentConflictTypes() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张老师", "B202", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("英语", "李老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        CourseScheduleConflictPreCheckRequest request = new CourseScheduleConflictPreCheckRequest();
        request.setCourseName("物理");
        request.setTeacherName("张老师");
        request.setClassroom("A101");
        request.setTimeSlot("周一 08:00-10:00");

        mockMvc.perform(post("/api/schedules/pre-check/grouped-by-source")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].sourceType").value("EXISTING_COURSE"))
                .andExpect(jsonPath("$[0].conflictCount").value(2))
                .andExpect(jsonPath("$[0].teacherConflictCount").value(1))
                .andExpect(jsonPath("$[0].classroomConflictCount").value(1))
                .andExpect(jsonPath("$[0].courseNames.length()").value(2));
    }

    @Test
    void testGroupConflictsBySource_NoCourseCreated() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        CourseScheduleConflictPreCheckRequest request = new CourseScheduleConflictPreCheckRequest();
        request.setCourseName("物理");
        request.setTeacherName("张老师");
        request.setClassroom("A101");
        request.setTimeSlot("周一 08:00-10:00");

        mockMvc.perform(post("/api/schedules/pre-check/grouped-by-source")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/schedules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void testGroupConflictsBySourceForTeacherBatch_NoConflicts_ReturnsEmptyList() throws Exception {
        TeacherBatchPreCheckRequest request = new TeacherBatchPreCheckRequest();
        request.setTeacherName("张老师");
        java.util.List<TeacherBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        TeacherBatchPreCheckItemRequest item1 = new TeacherBatchPreCheckItemRequest();
        item1.setCourseName("数学");
        item1.setClassroom("A101");
        item1.setTimeSlot("周一 08:00-10:00");
        items.add(item1);
        TeacherBatchPreCheckItemRequest item2 = new TeacherBatchPreCheckItemRequest();
        item2.setCourseName("物理");
        item2.setClassroom("B202");
        item2.setTimeSlot("周二 08:00-10:00");
        items.add(item2);
        request.setItems(items);

        mockMvc.perform(post("/api/schedules/pre-check/teacher-batch/grouped-by-source")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void testGroupConflictsBySourceForTeacherBatch_BothSourceTypesPresent() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("已有课", "张老师", "C303", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        TeacherBatchPreCheckRequest request = new TeacherBatchPreCheckRequest();
        request.setTeacherName("张老师");
        java.util.List<TeacherBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        TeacherBatchPreCheckItemRequest item1 = new TeacherBatchPreCheckItemRequest();
        item1.setCourseName("数学");
        item1.setClassroom("A101");
        item1.setTimeSlot("周一 08:00-10:00");
        items.add(item1);
        TeacherBatchPreCheckItemRequest item2 = new TeacherBatchPreCheckItemRequest();
        item2.setCourseName("物理");
        item2.setClassroom("B202");
        item2.setTimeSlot("周一 08:00-10:00");
        items.add(item2);
        request.setItems(items);

        mockMvc.perform(post("/api/schedules/pre-check/teacher-batch/grouped-by-source")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].sourceType").value("EXISTING_COURSE"))
                .andExpect(jsonPath("$[0].conflictCount").isNumber())
                .andExpect(jsonPath("$[0].teacherConflictCount").isNumber())
                .andExpect(jsonPath("$[0].classroomConflictCount").isNumber())
                .andExpect(jsonPath("$[0].courseNames").isArray())
                .andExpect(jsonPath("$[1].sourceType").value("PENDING_ITEM"))
                .andExpect(jsonPath("$[1].conflictCount").isNumber())
                .andExpect(jsonPath("$[1].teacherConflictCount").isNumber())
                .andExpect(jsonPath("$[1].classroomConflictCount").isNumber())
                .andExpect(jsonPath("$[1].courseNames").isArray());
    }

    @Test
    void testGroupConflictsBySourceForTeacherBatch_OnlyPendingItemConflicts() throws Exception {
        TeacherBatchPreCheckRequest request = new TeacherBatchPreCheckRequest();
        request.setTeacherName("张老师");
        java.util.List<TeacherBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        TeacherBatchPreCheckItemRequest item1 = new TeacherBatchPreCheckItemRequest();
        item1.setCourseName("数学");
        item1.setClassroom("A101");
        item1.setTimeSlot("周一 08:00-10:00");
        items.add(item1);
        TeacherBatchPreCheckItemRequest item2 = new TeacherBatchPreCheckItemRequest();
        item2.setCourseName("物理");
        item2.setClassroom("B202");
        item2.setTimeSlot("周一 08:00-10:00");
        items.add(item2);
        request.setItems(items);

        mockMvc.perform(post("/api/schedules/pre-check/teacher-batch/grouped-by-source")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].sourceType").value("PENDING_ITEM"))
                .andExpect(jsonPath("$[0].conflictCount").value(1))
                .andExpect(jsonPath("$[0].teacherConflictCount").value(1))
                .andExpect(jsonPath("$[0].classroomConflictCount").value(0))
                .andExpect(jsonPath("$[0].courseNames.length()").value(2));
    }

    @Test
    void testGroupConflictsBySourceForClassroomBatch_NoConflicts_ReturnsEmptyList() throws Exception {
        ClassroomBatchPreCheckRequest request = new ClassroomBatchPreCheckRequest();
        request.setClassroom("A101");
        java.util.List<ClassroomBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        ClassroomBatchPreCheckItemRequest item1 = new ClassroomBatchPreCheckItemRequest();
        item1.setCourseName("数学");
        item1.setTeacherName("张老师");
        item1.setTimeSlot("周一 08:00-10:00");
        items.add(item1);
        ClassroomBatchPreCheckItemRequest item2 = new ClassroomBatchPreCheckItemRequest();
        item2.setCourseName("物理");
        item2.setTeacherName("李老师");
        item2.setTimeSlot("周二 08:00-10:00");
        items.add(item2);
        request.setItems(items);

        mockMvc.perform(post("/api/schedules/pre-check/classroom-batch/grouped-by-source")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void testGroupConflictsBySourceForClassroomBatch_BothSourceTypesPresent() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("已有课", "王老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        ClassroomBatchPreCheckRequest request = new ClassroomBatchPreCheckRequest();
        request.setClassroom("A101");
        java.util.List<ClassroomBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        ClassroomBatchPreCheckItemRequest item1 = new ClassroomBatchPreCheckItemRequest();
        item1.setCourseName("数学");
        item1.setTeacherName("张老师");
        item1.setTimeSlot("周一 08:00-10:00");
        items.add(item1);
        ClassroomBatchPreCheckItemRequest item2 = new ClassroomBatchPreCheckItemRequest();
        item2.setCourseName("物理");
        item2.setTeacherName("李老师");
        item2.setTimeSlot("周一 08:00-10:00");
        items.add(item2);
        request.setItems(items);

        mockMvc.perform(post("/api/schedules/pre-check/classroom-batch/grouped-by-source")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].sourceType").value("EXISTING_COURSE"))
                .andExpect(jsonPath("$[0].conflictCount").isNumber())
                .andExpect(jsonPath("$[0].teacherConflictCount").isNumber())
                .andExpect(jsonPath("$[0].classroomConflictCount").isNumber())
                .andExpect(jsonPath("$[0].courseNames").isArray())
                .andExpect(jsonPath("$[1].sourceType").value("PENDING_ITEM"))
                .andExpect(jsonPath("$[1].conflictCount").isNumber())
                .andExpect(jsonPath("$[1].teacherConflictCount").isNumber())
                .andExpect(jsonPath("$[1].classroomConflictCount").isNumber())
                .andExpect(jsonPath("$[1].courseNames").isArray());
    }

    @Test
    void testGroupConflictsBySourceForClassroomBatch_OnlyPendingItemConflicts() throws Exception {
        ClassroomBatchPreCheckRequest request = new ClassroomBatchPreCheckRequest();
        request.setClassroom("A101");
        java.util.List<ClassroomBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        ClassroomBatchPreCheckItemRequest item1 = new ClassroomBatchPreCheckItemRequest();
        item1.setCourseName("数学");
        item1.setTeacherName("张老师");
        item1.setTimeSlot("周一 08:00-10:00");
        items.add(item1);
        ClassroomBatchPreCheckItemRequest item2 = new ClassroomBatchPreCheckItemRequest();
        item2.setCourseName("物理");
        item2.setTeacherName("李老师");
        item2.setTimeSlot("周一 08:00-10:00");
        items.add(item2);
        request.setItems(items);

        mockMvc.perform(post("/api/schedules/pre-check/classroom-batch/grouped-by-source")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].sourceType").value("PENDING_ITEM"))
                .andExpect(jsonPath("$[0].conflictCount").value(1))
                .andExpect(jsonPath("$[0].teacherConflictCount").value(0))
                .andExpect(jsonPath("$[0].classroomConflictCount").value(1))
                .andExpect(jsonPath("$[0].courseNames.length()").value(2));
    }

    @Test
    void testGroupConflictsBySourceForTeacherBatch_NoCoursesCreated() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        TeacherBatchPreCheckRequest request = new TeacherBatchPreCheckRequest();
        request.setTeacherName("张老师");
        java.util.List<TeacherBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        TeacherBatchPreCheckItemRequest item = new TeacherBatchPreCheckItemRequest();
        item.setCourseName("物理");
        item.setClassroom("A101");
        item.setTimeSlot("周一 08:00-10:00");
        items.add(item);
        request.setItems(items);

        mockMvc.perform(post("/api/schedules/pre-check/teacher-batch/grouped-by-source")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/schedules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void testGroupConflictsByConflictType_NoConflicts_ReturnsEmptyList() throws Exception {
        CourseScheduleConflictPreCheckRequest request = new CourseScheduleConflictPreCheckRequest();
        request.setCourseName("物理");
        request.setTeacherName("李老师");
        request.setClassroom("B202");
        request.setTimeSlot("周一 08:00-10:00");

        mockMvc.perform(post("/api/schedules/pre-check/grouped-by-conflict-type")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void testGroupConflictsByConflictType_OnlyTeacherConflict() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        CourseScheduleConflictPreCheckRequest request = new CourseScheduleConflictPreCheckRequest();
        request.setCourseName("物理");
        request.setTeacherName("张老师");
        request.setClassroom("B202");
        request.setTimeSlot("周一 08:00-10:00");

        mockMvc.perform(post("/api/schedules/pre-check/grouped-by-conflict-type")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].conflictType").value("TEACHER"))
                .andExpect(jsonPath("$[0].conflictCount").value(1))
                .andExpect(jsonPath("$[0].existingCourseConflictCount").value(1))
                .andExpect(jsonPath("$[0].pendingItemConflictCount").value(0))
                .andExpect(jsonPath("$[0].courseNames").isArray());
    }

    @Test
    void testGroupConflictsByConflictType_OnlyClassroomConflict() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "李老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        CourseScheduleConflictPreCheckRequest request = new CourseScheduleConflictPreCheckRequest();
        request.setCourseName("物理");
        request.setTeacherName("张老师");
        request.setClassroom("A101");
        request.setTimeSlot("周一 08:00-10:00");

        mockMvc.perform(post("/api/schedules/pre-check/grouped-by-conflict-type")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].conflictType").value("CLASSROOM"))
                .andExpect(jsonPath("$[0].conflictCount").value(1))
                .andExpect(jsonPath("$[0].existingCourseConflictCount").value(1))
                .andExpect(jsonPath("$[0].pendingItemConflictCount").value(0))
                .andExpect(jsonPath("$[0].courseNames").isArray());
    }

    @Test
    void testGroupConflictsByConflictType_BothConflictTypes() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        CourseScheduleConflictPreCheckRequest request = new CourseScheduleConflictPreCheckRequest();
        request.setCourseName("物理");
        request.setTeacherName("张老师");
        request.setClassroom("A101");
        request.setTimeSlot("周一 08:00-10:00");

        mockMvc.perform(post("/api/schedules/pre-check/grouped-by-conflict-type")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].conflictType").value("TEACHER"))
                .andExpect(jsonPath("$[0].conflictCount").value(1))
                .andExpect(jsonPath("$[0].existingCourseConflictCount").value(1))
                .andExpect(jsonPath("$[0].pendingItemConflictCount").value(0))
                .andExpect(jsonPath("$[1].conflictType").value("CLASSROOM"))
                .andExpect(jsonPath("$[1].conflictCount").value(1))
                .andExpect(jsonPath("$[1].existingCourseConflictCount").value(1))
                .andExpect(jsonPath("$[1].pendingItemConflictCount").value(0));
    }

    @Test
    void testGroupConflictsByConflictType_NoCoursesCreated() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        CourseScheduleConflictPreCheckRequest request = new CourseScheduleConflictPreCheckRequest();
        request.setCourseName("物理");
        request.setTeacherName("张老师");
        request.setClassroom("A101");
        request.setTimeSlot("周一 08:00-10:00");

        mockMvc.perform(post("/api/schedules/pre-check/grouped-by-conflict-type")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/schedules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void testGroupConflictsByConflictTypeForTeacherBatch_NoConflicts_ReturnsEmptyList() throws Exception {
        TeacherBatchPreCheckRequest request = new TeacherBatchPreCheckRequest();
        request.setTeacherName("张老师");
        java.util.List<TeacherBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        TeacherBatchPreCheckItemRequest item1 = new TeacherBatchPreCheckItemRequest();
        item1.setCourseName("数学");
        item1.setClassroom("A101");
        item1.setTimeSlot("周一 08:00-10:00");
        items.add(item1);
        TeacherBatchPreCheckItemRequest item2 = new TeacherBatchPreCheckItemRequest();
        item2.setCourseName("物理");
        item2.setClassroom("B202");
        item2.setTimeSlot("周二 08:00-10:00");
        items.add(item2);
        request.setItems(items);

        mockMvc.perform(post("/api/schedules/pre-check/teacher-batch/grouped-by-conflict-type")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void testGroupConflictsByConflictTypeForTeacherBatch_OnlyTeacherConflictFromExistingCourse() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("已有课", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        TeacherBatchPreCheckRequest request = new TeacherBatchPreCheckRequest();
        request.setTeacherName("张老师");
        java.util.List<TeacherBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        TeacherBatchPreCheckItemRequest item = new TeacherBatchPreCheckItemRequest();
        item.setCourseName("新课");
        item.setClassroom("B202");
        item.setTimeSlot("周一 08:00-10:00");
        items.add(item);
        request.setItems(items);

        mockMvc.perform(post("/api/schedules/pre-check/teacher-batch/grouped-by-conflict-type")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].conflictType").value("TEACHER"))
                .andExpect(jsonPath("$[0].conflictCount").value(1))
                .andExpect(jsonPath("$[0].existingCourseConflictCount").value(1))
                .andExpect(jsonPath("$[0].pendingItemConflictCount").value(0))
                .andExpect(jsonPath("$[0].courseNames").isArray());
    }

    @Test
    void testGroupConflictsByConflictTypeForTeacherBatch_OnlyClassroomConflictFromExistingCourse() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("已有课", "李老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        TeacherBatchPreCheckRequest request = new TeacherBatchPreCheckRequest();
        request.setTeacherName("张老师");
        java.util.List<TeacherBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        TeacherBatchPreCheckItemRequest item = new TeacherBatchPreCheckItemRequest();
        item.setCourseName("新课");
        item.setClassroom("A101");
        item.setTimeSlot("周一 08:00-10:00");
        items.add(item);
        request.setItems(items);

        mockMvc.perform(post("/api/schedules/pre-check/teacher-batch/grouped-by-conflict-type")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].conflictType").value("CLASSROOM"))
                .andExpect(jsonPath("$[0].conflictCount").value(1))
                .andExpect(jsonPath("$[0].existingCourseConflictCount").value(1))
                .andExpect(jsonPath("$[0].pendingItemConflictCount").value(0))
                .andExpect(jsonPath("$[0].courseNames").isArray());
    }

    @Test
    void testGroupConflictsByConflictTypeForTeacherBatch_PendingItemDedup() throws Exception {
        TeacherBatchPreCheckRequest request = new TeacherBatchPreCheckRequest();
        request.setTeacherName("张老师");
        java.util.List<TeacherBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        TeacherBatchPreCheckItemRequest item1 = new TeacherBatchPreCheckItemRequest();
        item1.setCourseName("数学");
        item1.setClassroom("A101");
        item1.setTimeSlot("周一 08:00-10:00");
        items.add(item1);
        TeacherBatchPreCheckItemRequest item2 = new TeacherBatchPreCheckItemRequest();
        item2.setCourseName("物理");
        item2.setClassroom("B202");
        item2.setTimeSlot("周一 08:00-10:00");
        items.add(item2);
        request.setItems(items);

        mockMvc.perform(post("/api/schedules/pre-check/teacher-batch/grouped-by-conflict-type")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].conflictType").value("TEACHER"))
                .andExpect(jsonPath("$[0].conflictCount").value(1))
                .andExpect(jsonPath("$[0].existingCourseConflictCount").value(0))
                .andExpect(jsonPath("$[0].pendingItemConflictCount").value(1))
                .andExpect(jsonPath("$[0].courseNames.length()").value(2));
    }

    @Test
    void testGroupConflictsByConflictTypeForTeacherBatch_MixedExistingAndPending() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("已有课", "张老师", "C303", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        TeacherBatchPreCheckRequest request = new TeacherBatchPreCheckRequest();
        request.setTeacherName("张老师");
        java.util.List<TeacherBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        TeacherBatchPreCheckItemRequest item1 = new TeacherBatchPreCheckItemRequest();
        item1.setCourseName("数学");
        item1.setClassroom("A101");
        item1.setTimeSlot("周一 08:00-10:00");
        items.add(item1);
        TeacherBatchPreCheckItemRequest item2 = new TeacherBatchPreCheckItemRequest();
        item2.setCourseName("物理");
        item2.setClassroom("B202");
        item2.setTimeSlot("周一 08:00-10:00");
        items.add(item2);
        request.setItems(items);

        mockMvc.perform(post("/api/schedules/pre-check/teacher-batch/grouped-by-conflict-type")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].conflictType").value("TEACHER"))
                .andExpect(jsonPath("$[0].conflictCount").value(3))
                .andExpect(jsonPath("$[0].existingCourseConflictCount").value(2))
                .andExpect(jsonPath("$[0].pendingItemConflictCount").value(1))
                .andExpect(jsonPath("$[0].courseNames.length()").value(3));
    }

    @Test
    void testGroupConflictsByConflictTypeForTeacherBatch_NoCoursesCreated() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        TeacherBatchPreCheckRequest request = new TeacherBatchPreCheckRequest();
        request.setTeacherName("张老师");
        java.util.List<TeacherBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        TeacherBatchPreCheckItemRequest item = new TeacherBatchPreCheckItemRequest();
        item.setCourseName("物理");
        item.setClassroom("A101");
        item.setTimeSlot("周一 08:00-10:00");
        items.add(item);
        request.setItems(items);

        mockMvc.perform(post("/api/schedules/pre-check/teacher-batch/grouped-by-conflict-type")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/schedules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void testGroupConflictsByConflictTypeForClassroomBatch_NoConflicts_ReturnsEmptyList() throws Exception {
        ClassroomBatchPreCheckRequest request = new ClassroomBatchPreCheckRequest();
        request.setClassroom("A101");
        java.util.List<ClassroomBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        ClassroomBatchPreCheckItemRequest item1 = new ClassroomBatchPreCheckItemRequest();
        item1.setCourseName("数学");
        item1.setTeacherName("张老师");
        item1.setTimeSlot("周一 08:00-10:00");
        items.add(item1);
        ClassroomBatchPreCheckItemRequest item2 = new ClassroomBatchPreCheckItemRequest();
        item2.setCourseName("物理");
        item2.setTeacherName("李老师");
        item2.setTimeSlot("周二 08:00-10:00");
        items.add(item2);
        request.setItems(items);

        mockMvc.perform(post("/api/schedules/pre-check/classroom-batch/grouped-by-conflict-type")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void testGroupConflictsByConflictTypeForClassroomBatch_OnlyTeacherConflictFromExistingCourse() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("已有课", "张老师", "B202", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        ClassroomBatchPreCheckRequest request = new ClassroomBatchPreCheckRequest();
        request.setClassroom("A101");
        java.util.List<ClassroomBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        ClassroomBatchPreCheckItemRequest item = new ClassroomBatchPreCheckItemRequest();
        item.setCourseName("新课");
        item.setTeacherName("张老师");
        item.setTimeSlot("周一 08:00-10:00");
        items.add(item);
        request.setItems(items);

        mockMvc.perform(post("/api/schedules/pre-check/classroom-batch/grouped-by-conflict-type")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].conflictType").value("TEACHER"))
                .andExpect(jsonPath("$[0].conflictCount").value(1))
                .andExpect(jsonPath("$[0].existingCourseConflictCount").value(1))
                .andExpect(jsonPath("$[0].pendingItemConflictCount").value(0))
                .andExpect(jsonPath("$[0].courseNames").isArray());
    }

    @Test
    void testGroupConflictsByConflictTypeForClassroomBatch_OnlyClassroomConflictFromExistingCourse() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("已有课", "李老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        ClassroomBatchPreCheckRequest request = new ClassroomBatchPreCheckRequest();
        request.setClassroom("A101");
        java.util.List<ClassroomBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        ClassroomBatchPreCheckItemRequest item = new ClassroomBatchPreCheckItemRequest();
        item.setCourseName("新课");
        item.setTeacherName("张老师");
        item.setTimeSlot("周一 08:00-10:00");
        items.add(item);
        request.setItems(items);

        mockMvc.perform(post("/api/schedules/pre-check/classroom-batch/grouped-by-conflict-type")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].conflictType").value("CLASSROOM"))
                .andExpect(jsonPath("$[0].conflictCount").value(1))
                .andExpect(jsonPath("$[0].existingCourseConflictCount").value(1))
                .andExpect(jsonPath("$[0].pendingItemConflictCount").value(0))
                .andExpect(jsonPath("$[0].courseNames").isArray());
    }

    @Test
    void testGroupConflictsByConflictTypeForClassroomBatch_PendingItemDedup() throws Exception {
        ClassroomBatchPreCheckRequest request = new ClassroomBatchPreCheckRequest();
        request.setClassroom("A101");
        java.util.List<ClassroomBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        ClassroomBatchPreCheckItemRequest item1 = new ClassroomBatchPreCheckItemRequest();
        item1.setCourseName("数学");
        item1.setTeacherName("张老师");
        item1.setTimeSlot("周一 08:00-10:00");
        items.add(item1);
        ClassroomBatchPreCheckItemRequest item2 = new ClassroomBatchPreCheckItemRequest();
        item2.setCourseName("物理");
        item2.setTeacherName("李老师");
        item2.setTimeSlot("周一 08:00-10:00");
        items.add(item2);
        request.setItems(items);

        mockMvc.perform(post("/api/schedules/pre-check/classroom-batch/grouped-by-conflict-type")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].conflictType").value("CLASSROOM"))
                .andExpect(jsonPath("$[0].conflictCount").value(1))
                .andExpect(jsonPath("$[0].existingCourseConflictCount").value(0))
                .andExpect(jsonPath("$[0].pendingItemConflictCount").value(1))
                .andExpect(jsonPath("$[0].courseNames.length()").value(2));
    }

    @Test
    void testGroupConflictsByConflictTypeForClassroomBatch_MixedExistingAndPending() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("已有课", "李老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        ClassroomBatchPreCheckRequest request = new ClassroomBatchPreCheckRequest();
        request.setClassroom("A101");
        java.util.List<ClassroomBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        ClassroomBatchPreCheckItemRequest item1 = new ClassroomBatchPreCheckItemRequest();
        item1.setCourseName("数学");
        item1.setTeacherName("张老师");
        item1.setTimeSlot("周一 08:00-10:00");
        items.add(item1);
        ClassroomBatchPreCheckItemRequest item2 = new ClassroomBatchPreCheckItemRequest();
        item2.setCourseName("物理");
        item2.setTeacherName("王老师");
        item2.setTimeSlot("周一 08:00-10:00");
        items.add(item2);
        request.setItems(items);

        mockMvc.perform(post("/api/schedules/pre-check/classroom-batch/grouped-by-conflict-type")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].conflictType").value("CLASSROOM"))
                .andExpect(jsonPath("$[0].conflictCount").value(3))
                .andExpect(jsonPath("$[0].existingCourseConflictCount").value(2))
                .andExpect(jsonPath("$[0].pendingItemConflictCount").value(1))
                .andExpect(jsonPath("$[0].courseNames.length()").value(3));
    }

    @Test
    void testGroupConflictsByConflictTypeForClassroomBatch_NoCoursesCreated() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        ClassroomBatchPreCheckRequest request = new ClassroomBatchPreCheckRequest();
        request.setClassroom("A101");
        java.util.List<ClassroomBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        ClassroomBatchPreCheckItemRequest item = new ClassroomBatchPreCheckItemRequest();
        item.setCourseName("物理");
        item.setTeacherName("李老师");
        item.setTimeSlot("周一 08:00-10:00");
        items.add(item);
        request.setItems(items);

        mockMvc.perform(post("/api/schedules/pre-check/classroom-batch/grouped-by-conflict-type")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/schedules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void testGroupConflictsByConflictTypeForTeacherBatch_TeacherAndClassroomCourseNamesNotMixed() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("已有老师课", "张老师", "X001", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("已有教室课", "王老师", "A101", "周二 14:00-16:00"))))
                .andExpect(status().isCreated());

        TeacherBatchPreCheckRequest request = new TeacherBatchPreCheckRequest();
        request.setTeacherName("张老师");
        java.util.List<TeacherBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        TeacherBatchPreCheckItemRequest item0 = new TeacherBatchPreCheckItemRequest();
        item0.setCourseName("待排老师A");
        item0.setClassroom("A101");
        item0.setTimeSlot("周一 08:00-10:00");
        items.add(item0);
        TeacherBatchPreCheckItemRequest item1 = new TeacherBatchPreCheckItemRequest();
        item1.setCourseName("待排教室B");
        item1.setClassroom("A101");
        item1.setTimeSlot("周二 14:00-16:00");
        items.add(item1);
        request.setItems(items);

        String json = mockMvc.perform(post("/api/schedules/pre-check/teacher-batch/grouped-by-conflict-type")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(json);
        com.fasterxml.jackson.databind.JsonNode teacherGroup = null;
        com.fasterxml.jackson.databind.JsonNode classroomGroup = null;
        for (com.fasterxml.jackson.databind.JsonNode n : root) {
            if ("TEACHER".equals(n.get("conflictType").asText())) {
                teacherGroup = n;
            } else if ("CLASSROOM".equals(n.get("conflictType").asText())) {
                classroomGroup = n;
            }
        }
        assertNotNull(teacherGroup);
        assertNotNull(classroomGroup);

        java.util.List<String> teacherNames = new java.util.ArrayList<>();
        for (com.fasterxml.jackson.databind.JsonNode n : teacherGroup.get("courseNames")) {
            teacherNames.add(n.asText());
        }
        assertTrue(teacherNames.contains("已有老师课"));
        assertTrue(teacherNames.contains("待排老师A"));
        assertFalse(teacherNames.contains("已有教室课"));
        assertFalse(teacherNames.contains("待排教室B"));

        java.util.List<String> classroomNames = new java.util.ArrayList<>();
        for (com.fasterxml.jackson.databind.JsonNode n : classroomGroup.get("courseNames")) {
            classroomNames.add(n.asText());
        }
        assertTrue(classroomNames.contains("已有教室课"));
        assertTrue(classroomNames.contains("待排教室B"));
        assertFalse(classroomNames.contains("已有老师课"));
        assertFalse(classroomNames.contains("待排老师A"));
    }

    @Test
    void testGroupConflictsByConflictTypeForClassroomBatch_TeacherAndClassroomCourseNamesNotMixed() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("已有老师课", "张老师", "X001", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("已有教室课", "王老师", "A101", "周二 14:00-16:00"))))
                .andExpect(status().isCreated());

        ClassroomBatchPreCheckRequest request = new ClassroomBatchPreCheckRequest();
        request.setClassroom("A101");
        java.util.List<ClassroomBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        ClassroomBatchPreCheckItemRequest item0 = new ClassroomBatchPreCheckItemRequest();
        item0.setCourseName("待排教室A");
        item0.setTeacherName("张老师");
        item0.setTimeSlot("周二 14:00-16:00");
        items.add(item0);
        ClassroomBatchPreCheckItemRequest item1 = new ClassroomBatchPreCheckItemRequest();
        item1.setCourseName("待排老师B");
        item1.setTeacherName("张老师");
        item1.setTimeSlot("周一 08:00-10:00");
        items.add(item1);
        request.setItems(items);

        String json = mockMvc.perform(post("/api/schedules/pre-check/classroom-batch/grouped-by-conflict-type")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(json);
        com.fasterxml.jackson.databind.JsonNode teacherGroup = null;
        com.fasterxml.jackson.databind.JsonNode classroomGroup = null;
        for (com.fasterxml.jackson.databind.JsonNode n : root) {
            if ("TEACHER".equals(n.get("conflictType").asText())) {
                teacherGroup = n;
            } else if ("CLASSROOM".equals(n.get("conflictType").asText())) {
                classroomGroup = n;
            }
        }
        assertNotNull(teacherGroup);
        assertNotNull(classroomGroup);

        java.util.List<String> teacherNames = new java.util.ArrayList<>();
        for (com.fasterxml.jackson.databind.JsonNode n : teacherGroup.get("courseNames")) {
            teacherNames.add(n.asText());
        }
        assertTrue(teacherNames.contains("已有老师课"));
        assertTrue(teacherNames.contains("待排老师B"));
        assertFalse(teacherNames.contains("已有教室课"));
        assertFalse(teacherNames.contains("待排教室A"));

        java.util.List<String> classroomNames = new java.util.ArrayList<>();
        for (com.fasterxml.jackson.databind.JsonNode n : classroomGroup.get("courseNames")) {
            classroomNames.add(n.asText());
        }
        assertTrue(classroomNames.contains("已有教室课"));
        assertTrue(classroomNames.contains("待排教室A"));
        assertFalse(classroomNames.contains("已有老师课"));
        assertFalse(classroomNames.contains("待排老师B"));
    }

    @Test
    void testGetConflictTargetDetails_NoConflict_ReturnsEmptyList() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("数学", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        CourseScheduleConflictPreCheckRequest request = createPreCheckRequest(
                "物理", "李老师", "B202", "周二 14:00-16:00");

        mockMvc.perform(post("/api/schedules/pre-check/target-details")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void testGetConflictTargetDetails_SingleExistingCourseTarget() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("数学", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        CourseScheduleConflictPreCheckRequest request = createPreCheckRequest(
                "物理", "张老师", "B202", "周一 08:00-10:00");

        mockMvc.perform(post("/api/schedules/pre-check/target-details")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].sourceType").value("EXISTING_COURSE"))
                .andExpect(jsonPath("$[0].courseId").value(1))
                .andExpect(jsonPath("$[0].courseName").value("数学"))
                .andExpect(jsonPath("$[0].teacherName").value("张老师"))
                .andExpect(jsonPath("$[0].classroom").value("A101"))
                .andExpect(jsonPath("$[0].timeSlot").value("周一 08:00-10:00"))
                .andExpect(jsonPath("$[0].conflictTypes").isArray())
                .andExpect(jsonPath("$[0].conflictTypes.length()").value(1))
                .andExpect(jsonPath("$[0].conflictTypes[0]").value("TEACHER"))
                .andExpect(jsonPath("$[0].conflictCount").value(1));
    }

    @Test
    void testGetConflictTargetDetails_SameTarget_TeacherAndClassroomMerged() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("数学", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        CourseScheduleConflictPreCheckRequest request = createPreCheckRequest(
                "物理", "张老师", "A101", "周一 08:00-10:00");

        mockMvc.perform(post("/api/schedules/pre-check/target-details")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].sourceType").value("EXISTING_COURSE"))
                .andExpect(jsonPath("$[0].courseId").value(1))
                .andExpect(jsonPath("$[0].conflictTypes.length()").value(2))
                .andExpect(jsonPath("$[0].conflictTypes[0]").value("TEACHER"))
                .andExpect(jsonPath("$[0].conflictTypes[1]").value("CLASSROOM"))
                .andExpect(jsonPath("$[0].conflictCount").value(2));
    }

    @Test
    void testGetConflictTargetDetails_NullRequestBody_Returns400() throws Exception {
        mockMvc.perform(post("/api/schedules/pre-check/target-details")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("null"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testGetConflictTargetDetailsForTeacherBatch_NoConflict_ReturnsEmptyList() throws Exception {
        List<TeacherBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        TeacherBatchPreCheckItemRequest item0 = new TeacherBatchPreCheckItemRequest();
        item0.setCourseName("数学");
        item0.setClassroom("A101");
        item0.setTimeSlot("周一 08:00-10:00");
        items.add(item0);
        TeacherBatchPreCheckItemRequest item1 = new TeacherBatchPreCheckItemRequest();
        item1.setCourseName("物理");
        item1.setClassroom("B202");
        item1.setTimeSlot("周二 14:00-16:00");
        items.add(item1);
        TeacherBatchPreCheckRequest request = new TeacherBatchPreCheckRequest();
        request.setTeacherName("张老师");
        request.setItems(items);

        mockMvc.perform(post("/api/schedules/pre-check/teacher-batch/target-details")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void testGetConflictTargetDetailsForTeacherBatch_ExistingCourseTarget() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("已有数学课", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        List<TeacherBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        TeacherBatchPreCheckItemRequest item0 = new TeacherBatchPreCheckItemRequest();
        item0.setCourseName("新排物理");
        item0.setClassroom("B202");
        item0.setTimeSlot("周一 08:00-10:00");
        items.add(item0);
        TeacherBatchPreCheckRequest request = new TeacherBatchPreCheckRequest();
        request.setTeacherName("张老师");
        request.setItems(items);

        mockMvc.perform(post("/api/schedules/pre-check/teacher-batch/target-details")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].sourceType").value("EXISTING_COURSE"))
                .andExpect(jsonPath("$[0].courseName").value("已有数学课"))
                .andExpect(jsonPath("$[0].conflictTypes[0]").value("TEACHER"))
                .andExpect(jsonPath("$[0].conflictCount").value(1));
    }

    @Test
    void testGetConflictTargetDetailsForTeacherBatch_PendingItemTarget() throws Exception {
        List<TeacherBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        TeacherBatchPreCheckItemRequest item0 = new TeacherBatchPreCheckItemRequest();
        item0.setCourseName("数学");
        item0.setClassroom("A101");
        item0.setTimeSlot("周一 08:00-10:00");
        items.add(item0);
        TeacherBatchPreCheckItemRequest item1 = new TeacherBatchPreCheckItemRequest();
        item1.setCourseName("物理");
        item1.setClassroom("B202");
        item1.setTimeSlot("周一 08:00-10:00");
        items.add(item1);
        TeacherBatchPreCheckRequest request = new TeacherBatchPreCheckRequest();
        request.setTeacherName("张老师");
        request.setItems(items);

        String json = mockMvc.perform(post("/api/schedules/pre-check/teacher-batch/target-details")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(json);
        com.fasterxml.jackson.databind.JsonNode target = root.get(0);
        assertEquals("PENDING_ITEM", target.get("sourceType").asText());
        assertEquals("数学", target.get("courseName").asText());
        assertEquals("张老师", target.get("teacherName").asText());
        assertEquals("A101", target.get("classroom").asText());
        assertEquals("周一 08:00-10:00", target.get("timeSlot").asText());
        assertEquals(0, target.get("pendingIndex").asInt());
        assertNotNull(target.get("relatedPendingIndexes"));
        assertEquals(2, target.get("relatedPendingIndexes").size());
        assertEquals(0, target.get("relatedPendingIndexes").get(0).asInt());
        assertEquals(1, target.get("relatedPendingIndexes").get(1).asInt());
        assertNotNull(target.get("relatedCourseNames"));
        assertEquals(2, target.get("relatedCourseNames").size());
        assertEquals("数学", target.get("relatedCourseNames").get(0).asText());
        assertEquals("物理", target.get("relatedCourseNames").get(1).asText());
        assertEquals(1, target.get("conflictCount").asInt());
        assertEquals("TEACHER", target.get("conflictTypes").get(0).asText());
    }

    @Test
    void testGetConflictTargetDetailsForTeacherBatch_DuplicateTargetDeduplicated() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("已有数学", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        List<TeacherBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        TeacherBatchPreCheckItemRequest item0 = new TeacherBatchPreCheckItemRequest();
        item0.setCourseName("新排物理");
        item0.setClassroom("B202");
        item0.setTimeSlot("周一 08:00-10:00");
        items.add(item0);
        TeacherBatchPreCheckItemRequest item1 = new TeacherBatchPreCheckItemRequest();
        item1.setCourseName("新排化学");
        item1.setClassroom("C303");
        item1.setTimeSlot("周一 08:00-10:00");
        items.add(item1);
        TeacherBatchPreCheckRequest request = new TeacherBatchPreCheckRequest();
        request.setTeacherName("张老师");
        request.setItems(items);

        String json = mockMvc.perform(post("/api/schedules/pre-check/teacher-batch/target-details")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(json);
        int existingCount = 0;
        int pendingPairCount = 0;
        for (com.fasterxml.jackson.databind.JsonNode n : root) {
            if ("EXISTING_COURSE".equals(n.get("sourceType").asText())) {
                existingCount++;
                assertEquals("已有数学", n.get("courseName").asText());
                assertEquals(1, n.get("conflictCount").asInt());
                assertNotNull(n.get("relatedPendingIndexes"));
                assertTrue(n.get("relatedPendingIndexes").isArray());
                assertEquals(2, n.get("relatedPendingIndexes").size());
                assertTrue(n.get("relatedCourseNames").isArray());
                assertEquals(2, n.get("relatedCourseNames").size());
                assertTrue(n.get("relatedCourseNames").get(0).asText().contains("新排"));
            } else if ("PENDING_ITEM".equals(n.get("sourceType").asText())) {
                pendingPairCount++;
                assertEquals("新排物理", n.get("courseName").asText());
                assertEquals(0, n.get("pendingIndex").asInt());
                assertNotNull(n.get("relatedPendingIndexes"));
                assertEquals(2, n.get("relatedPendingIndexes").size());
                assertEquals(0, n.get("relatedPendingIndexes").get(0).asInt());
                assertEquals(1, n.get("relatedPendingIndexes").get(1).asInt());
                assertEquals(1, n.get("conflictCount").asInt());
            }
        }
        assertEquals(1, existingCount);
        assertEquals(1, pendingPairCount);
    }

    @Test
    void testGetConflictTargetDetailsForTeacherBatch_SameTargetMergedConflictTypes() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("已有数学", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        List<TeacherBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        TeacherBatchPreCheckItemRequest item0 = new TeacherBatchPreCheckItemRequest();
        item0.setCourseName("新排物理");
        item0.setClassroom("A101");
        item0.setTimeSlot("周一 08:00-10:00");
        items.add(item0);
        TeacherBatchPreCheckRequest request = new TeacherBatchPreCheckRequest();
        request.setTeacherName("张老师");
        request.setItems(items);

        mockMvc.perform(post("/api/schedules/pre-check/teacher-batch/target-details")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].sourceType").value("EXISTING_COURSE"))
                .andExpect(jsonPath("$[0].conflictTypes.length()").value(2))
                .andExpect(jsonPath("$[0].conflictCount").value(2));
    }

    @Test
    void testGetConflictTargetDetailsForClassroomBatch_NoConflict_ReturnsEmptyList() throws Exception {
        List<ClassroomBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        ClassroomBatchPreCheckItemRequest item0 = new ClassroomBatchPreCheckItemRequest();
        item0.setCourseName("数学");
        item0.setTeacherName("张老师");
        item0.setTimeSlot("周一 08:00-10:00");
        items.add(item0);
        ClassroomBatchPreCheckItemRequest item1 = new ClassroomBatchPreCheckItemRequest();
        item1.setCourseName("物理");
        item1.setTeacherName("李老师");
        item1.setTimeSlot("周二 14:00-16:00");
        items.add(item1);
        ClassroomBatchPreCheckRequest request = new ClassroomBatchPreCheckRequest();
        request.setClassroom("A101");
        request.setItems(items);

        mockMvc.perform(post("/api/schedules/pre-check/classroom-batch/target-details")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void testGetConflictTargetDetailsForClassroomBatch_ExistingCourseTarget() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("已有数学", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        List<ClassroomBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        ClassroomBatchPreCheckItemRequest item0 = new ClassroomBatchPreCheckItemRequest();
        item0.setCourseName("新排物理");
        item0.setTeacherName("李老师");
        item0.setTimeSlot("周一 08:00-10:00");
        items.add(item0);
        ClassroomBatchPreCheckRequest request = new ClassroomBatchPreCheckRequest();
        request.setClassroom("A101");
        request.setItems(items);

        mockMvc.perform(post("/api/schedules/pre-check/classroom-batch/target-details")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].sourceType").value("EXISTING_COURSE"))
                .andExpect(jsonPath("$[0].courseName").value("已有数学"))
                .andExpect(jsonPath("$[0].conflictTypes[0]").value("CLASSROOM"))
                .andExpect(jsonPath("$[0].conflictCount").value(1));
    }

    @Test
    void testGetConflictTargetDetailsForClassroomBatch_PendingItemTarget() throws Exception {
        List<ClassroomBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        ClassroomBatchPreCheckItemRequest item0 = new ClassroomBatchPreCheckItemRequest();
        item0.setCourseName("数学");
        item0.setTeacherName("张老师");
        item0.setTimeSlot("周一 08:00-10:00");
        items.add(item0);
        ClassroomBatchPreCheckItemRequest item1 = new ClassroomBatchPreCheckItemRequest();
        item1.setCourseName("物理");
        item1.setTeacherName("李老师");
        item1.setTimeSlot("周一 08:00-10:00");
        items.add(item1);
        ClassroomBatchPreCheckRequest request = new ClassroomBatchPreCheckRequest();
        request.setClassroom("A101");
        request.setItems(items);

        String json = mockMvc.perform(post("/api/schedules/pre-check/classroom-batch/target-details")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(json);
        com.fasterxml.jackson.databind.JsonNode target = root.get(0);
        assertEquals("PENDING_ITEM", target.get("sourceType").asText());
        assertEquals("数学", target.get("courseName").asText());
        assertEquals("张老师", target.get("teacherName").asText());
        assertEquals("A101", target.get("classroom").asText());
        assertEquals("周一 08:00-10:00", target.get("timeSlot").asText());
        assertEquals(0, target.get("pendingIndex").asInt());
        assertNotNull(target.get("relatedPendingIndexes"));
        assertEquals(2, target.get("relatedPendingIndexes").size());
        assertEquals(0, target.get("relatedPendingIndexes").get(0).asInt());
        assertEquals(1, target.get("relatedPendingIndexes").get(1).asInt());
        assertNotNull(target.get("relatedCourseNames"));
        assertEquals(2, target.get("relatedCourseNames").size());
        assertEquals("数学", target.get("relatedCourseNames").get(0).asText());
        assertEquals("物理", target.get("relatedCourseNames").get(1).asText());
        assertEquals(1, target.get("conflictCount").asInt());
        assertEquals("CLASSROOM", target.get("conflictTypes").get(0).asText());
    }

    @Test
    void testGetConflictTargetDetailsForClassroomBatch_DuplicateTargetDeduplicated() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("已有数学", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        List<ClassroomBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        ClassroomBatchPreCheckItemRequest item0 = new ClassroomBatchPreCheckItemRequest();
        item0.setCourseName("新排物理");
        item0.setTeacherName("李老师");
        item0.setTimeSlot("周一 08:00-10:00");
        items.add(item0);
        ClassroomBatchPreCheckItemRequest item1 = new ClassroomBatchPreCheckItemRequest();
        item1.setCourseName("新排化学");
        item1.setTeacherName("王老师");
        item1.setTimeSlot("周一 08:00-10:00");
        items.add(item1);
        ClassroomBatchPreCheckRequest request = new ClassroomBatchPreCheckRequest();
        request.setClassroom("A101");
        request.setItems(items);

        String json = mockMvc.perform(post("/api/schedules/pre-check/classroom-batch/target-details")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(json);

        int existingCount = 0;
        int pendingPairCount = 0;
        for (com.fasterxml.jackson.databind.JsonNode n : root) {
            if ("EXISTING_COURSE".equals(n.get("sourceType").asText())) {
                existingCount++;
                assertEquals("已有数学", n.get("courseName").asText());
                assertEquals(1, n.get("conflictCount").asInt());
                assertNotNull(n.get("relatedPendingIndexes"));
                assertEquals(2, n.get("relatedPendingIndexes").size());
                assertNotNull(n.get("relatedCourseNames"));
                assertEquals(2, n.get("relatedCourseNames").size());
            } else if ("PENDING_ITEM".equals(n.get("sourceType").asText())) {
                pendingPairCount++;
                assertEquals("新排物理", n.get("courseName").asText());
                assertEquals(0, n.get("pendingIndex").asInt());
                assertNotNull(n.get("relatedPendingIndexes"));
                assertEquals(2, n.get("relatedPendingIndexes").size());
                assertEquals(0, n.get("relatedPendingIndexes").get(0).asInt());
                assertEquals(1, n.get("relatedPendingIndexes").get(1).asInt());
                assertEquals(1, n.get("conflictCount").asInt());
            }
        }
        assertEquals(1, existingCount);
        assertEquals(1, pendingPairCount);
    }

    @Test
    void testSeveritySummary_Single_NoConflict_ReturnsEmptyList() throws Exception {
        CourseScheduleConflictPreCheckRequest request = createPreCheckRequest(
                "数学", "张老师", "A101", "周一 08:00-10:00");

        mockMvc.perform(post("/api/schedules/pre-check/severity-summary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void testSeveritySummary_Single_Blocker_BothTeacherAndClassroom() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("已有课", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        CourseScheduleConflictPreCheckRequest request = createPreCheckRequest(
                "物理", "张老师", "A101", "周一 08:00-10:00");

        mockMvc.perform(post("/api/schedules/pre-check/severity-summary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].severity").value("BLOCKER"))
                .andExpect(jsonPath("$[0].targetCount").value(1))
                .andExpect(jsonPath("$[0].conflictTypeCount").value(2))
                .andExpect(jsonPath("$[0].existingCourseTargetCount").value(1))
                .andExpect(jsonPath("$[0].pendingItemTargetCount").value(0))
                .andExpect(jsonPath("$[0].courseNames").isArray());
    }

    @Test
    void testSeveritySummary_Single_Warning_OnlyTeacher() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("已有课", "张老师", "B202", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        CourseScheduleConflictPreCheckRequest request = createPreCheckRequest(
                "物理", "张老师", "A101", "周一 08:00-10:00");

        mockMvc.perform(post("/api/schedules/pre-check/severity-summary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].severity").value("WARNING"))
                .andExpect(jsonPath("$[0].targetCount").value(1))
                .andExpect(jsonPath("$[0].conflictTypeCount").value(1))
                .andExpect(jsonPath("$[0].existingCourseTargetCount").value(1))
                .andExpect(jsonPath("$[0].pendingItemTargetCount").value(0));
    }

    @Test
    void testSeveritySummary_Single_Warning_OnlyClassroom() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("已有课", "李老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        CourseScheduleConflictPreCheckRequest request = createPreCheckRequest(
                "物理", "张老师", "A101", "周一 08:00-10:00");

        mockMvc.perform(post("/api/schedules/pre-check/severity-summary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].severity").value("WARNING"))
                .andExpect(jsonPath("$[0].targetCount").value(1))
                .andExpect(jsonPath("$[0].conflictTypeCount").value(1))
                .andExpect(jsonPath("$[0].existingCourseTargetCount").value(1))
                .andExpect(jsonPath("$[0].pendingItemTargetCount").value(0));
    }

    @Test
    void testSeveritySummary_Single_MixedBlockerAndWarning() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("课1", "张老师", "A101", "周一 09:00-11:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("课2", "李老师", "A101", "周一 11:00-13:00"))))
                .andExpect(status().isCreated());

        CourseScheduleConflictPreCheckRequest request = createPreCheckRequest(
                "物理", "张老师", "A101", "周一 10:00-12:00");

        mockMvc.perform(post("/api/schedules/pre-check/severity-summary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].severity").value("BLOCKER"))
                .andExpect(jsonPath("$[0].targetCount").value(1))
                .andExpect(jsonPath("$[0].conflictTypeCount").value(2))
                .andExpect(jsonPath("$[1].severity").value("WARNING"))
                .andExpect(jsonPath("$[1].targetCount").value(1))
                .andExpect(jsonPath("$[1].conflictTypeCount").value(1));
    }

    @Test
    void testSeveritySummary_Single_NoCourseCreated() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("数学", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        CourseScheduleConflictPreCheckRequest request = createPreCheckRequest(
                "物理", "张老师", "A101", "周一 08:00-10:00");

        mockMvc.perform(post("/api/schedules/pre-check/severity-summary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/schedules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void testSeveritySummary_TeacherBatch_NoConflict_ReturnsEmptyList() throws Exception {
        java.util.List<TeacherBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        TeacherBatchPreCheckItemRequest item1 = new TeacherBatchPreCheckItemRequest();
        item1.setCourseName("数学");
        item1.setClassroom("A101");
        item1.setTimeSlot("周一 08:00-10:00");
        items.add(item1);
        TeacherBatchPreCheckRequest request = createTeacherBatchRequest("张老师", items);

        mockMvc.perform(post("/api/schedules/pre-check/teacher-batch/severity-summary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void testSeveritySummary_TeacherBatch_Blocker_ExistingCourseBothConflictTypes() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("已有课", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        java.util.List<TeacherBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        TeacherBatchPreCheckItemRequest item1 = new TeacherBatchPreCheckItemRequest();
        item1.setCourseName("新排课");
        item1.setClassroom("A101");
        item1.setTimeSlot("周一 08:00-10:00");
        items.add(item1);
        TeacherBatchPreCheckRequest request = createTeacherBatchRequest("张老师", items);

        mockMvc.perform(post("/api/schedules/pre-check/teacher-batch/severity-summary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].severity").value("BLOCKER"))
                .andExpect(jsonPath("$[0].targetCount").value(1))
                .andExpect(jsonPath("$[0].conflictTypeCount").value(2))
                .andExpect(jsonPath("$[0].existingCourseTargetCount").value(1))
                .andExpect(jsonPath("$[0].pendingItemTargetCount").value(0));
    }

    @Test
    void testSeveritySummary_TeacherBatch_Warning_OnlyPendingConflict() throws Exception {
        java.util.List<TeacherBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        TeacherBatchPreCheckItemRequest item1 = new TeacherBatchPreCheckItemRequest();
        item1.setCourseName("数学");
        item1.setClassroom("A101");
        item1.setTimeSlot("周一 08:00-10:00");
        items.add(item1);
        TeacherBatchPreCheckItemRequest item2 = new TeacherBatchPreCheckItemRequest();
        item2.setCourseName("物理");
        item2.setClassroom("B202");
        item2.setTimeSlot("周一 08:00-10:00");
        items.add(item2);
        TeacherBatchPreCheckRequest request = createTeacherBatchRequest("张老师", items);

        mockMvc.perform(post("/api/schedules/pre-check/teacher-batch/severity-summary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].severity").value("WARNING"))
                .andExpect(jsonPath("$[0].targetCount").value(1))
                .andExpect(jsonPath("$[0].existingCourseTargetCount").value(0))
                .andExpect(jsonPath("$[0].pendingItemTargetCount").value(1))
                .andExpect(jsonPath("$[0].courseNames.length()").value(2));
    }

    @Test
    void testSeveritySummary_TeacherBatch_PendingPairDedup_AB_BA_SameTarget() throws Exception {
        java.util.List<TeacherBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        TeacherBatchPreCheckItemRequest item1 = new TeacherBatchPreCheckItemRequest();
        item1.setCourseName("数学");
        item1.setClassroom("A101");
        item1.setTimeSlot("周一 08:00-10:00");
        items.add(item1);
        TeacherBatchPreCheckItemRequest item2 = new TeacherBatchPreCheckItemRequest();
        item2.setCourseName("物理");
        item2.setClassroom("B202");
        item2.setTimeSlot("周一 08:00-10:00");
        items.add(item2);
        TeacherBatchPreCheckRequest request = createTeacherBatchRequest("张老师", items);

        mockMvc.perform(post("/api/schedules/pre-check/teacher-batch/severity-summary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].targetCount").value(1));
    }

    @Test
    void testSeveritySummary_ClassroomBatch_NoConflict_ReturnsEmptyList() throws Exception {
        java.util.List<ClassroomBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        items.add(createClassroomBatchItem("数学", "张老师", "周一 08:00-10:00"));
        ClassroomBatchPreCheckRequest request = createClassroomBatchRequest("A101", items);

        mockMvc.perform(post("/api/schedules/pre-check/classroom-batch/severity-summary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void testSeveritySummary_ClassroomBatch_Blocker_ExistingCourseBothConflictTypes() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("已有课", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        java.util.List<ClassroomBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        items.add(createClassroomBatchItem("新排课", "张老师", "周一 08:00-10:00"));
        ClassroomBatchPreCheckRequest request = createClassroomBatchRequest("A101", items);

        mockMvc.perform(post("/api/schedules/pre-check/classroom-batch/severity-summary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].severity").value("BLOCKER"))
                .andExpect(jsonPath("$[0].targetCount").value(1))
                .andExpect(jsonPath("$[0].conflictTypeCount").value(2))
                .andExpect(jsonPath("$[0].existingCourseTargetCount").value(1))
                .andExpect(jsonPath("$[0].pendingItemTargetCount").value(0));
    }

    @Test
    void testSeveritySummary_ClassroomBatch_Warning_OnlyPendingConflict() throws Exception {
        java.util.List<ClassroomBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        items.add(createClassroomBatchItem("数学", "张老师", "周一 08:00-10:00"));
        items.add(createClassroomBatchItem("物理", "李老师", "周一 08:00-10:00"));
        ClassroomBatchPreCheckRequest request = createClassroomBatchRequest("A101", items);

        mockMvc.perform(post("/api/schedules/pre-check/classroom-batch/severity-summary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].severity").value("WARNING"))
                .andExpect(jsonPath("$[0].targetCount").value(1))
                .andExpect(jsonPath("$[0].existingCourseTargetCount").value(0))
                .andExpect(jsonPath("$[0].pendingItemTargetCount").value(1));
    }

    @Test
    void testSeveritySummary_ClassroomBatch_NoCourseCreated() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("数学", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        java.util.List<ClassroomBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        items.add(createClassroomBatchItem("物理", "李老师", "周一 08:00-10:00"));
        ClassroomBatchPreCheckRequest request = createClassroomBatchRequest("A101", items);

        mockMvc.perform(post("/api/schedules/pre-check/classroom-batch/severity-summary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/schedules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void testSeveritySummary_Single_NullRequestBody_Returns400() throws Exception {
        mockMvc.perform(post("/api/schedules/pre-check/severity-summary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("null"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testSeveritySummary_TeacherBatch_NullRequestBody_Returns400() throws Exception {
        mockMvc.perform(post("/api/schedules/pre-check/teacher-batch/severity-summary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("null"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testSeveritySummary_ClassroomBatch_NullRequestBody_Returns400() throws Exception {
        mockMvc.perform(post("/api/schedules/pre-check/classroom-batch/severity-summary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("null"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testSeveritySummary_Single_MultipleWarningTargets_ConflictTypeCountSummed() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("课1", "张老师", "A101", "周一 08:00-09:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("课2", "张老师", "B202", "周一 09:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("课3", "张老师", "C303", "周一 10:00-11:00"))))
                .andExpect(status().isCreated());

        CourseScheduleConflictPreCheckRequest request = createPreCheckRequest(
                "新课", "张老师", "D404", "周一 08:00-11:00");

        mockMvc.perform(post("/api/schedules/pre-check/severity-summary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].severity").value("WARNING"))
                .andExpect(jsonPath("$[0].targetCount").value(3))
                .andExpect(jsonPath("$[0].conflictTypeCount").value(3))
                .andExpect(jsonPath("$[0].existingCourseTargetCount").value(3))
                .andExpect(jsonPath("$[0].pendingItemTargetCount").value(0));
    }

    @Test
    void testSeveritySummary_Single_MultipleBlockerTargets_ConflictTypeCountSummed() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("课1", "张老师", "A101", "周一 08:00-09:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("课2", "张老师", "A101", "周一 09:00-10:00"))))
                .andExpect(status().isCreated());

        CourseScheduleConflictPreCheckRequest request = createPreCheckRequest(
                "新课", "张老师", "A101", "周一 08:00-10:00");

        mockMvc.perform(post("/api/schedules/pre-check/severity-summary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].severity").value("BLOCKER"))
                .andExpect(jsonPath("$[0].targetCount").value(2))
                .andExpect(jsonPath("$[0].conflictTypeCount").value(4))
                .andExpect(jsonPath("$[0].existingCourseTargetCount").value(2))
                .andExpect(jsonPath("$[0].pendingItemTargetCount").value(0));
    }

    @Test
    void testSeveritySummary_Single_MixedBlockerAndWarning_MultipleTargets() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("课1", "张老师", "A101", "周一 08:00-09:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("课2", "张老师", "B202", "周一 09:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("课3", "李老师", "A101", "周一 09:00-10:00"))))
                .andExpect(status().isCreated());

        CourseScheduleConflictPreCheckRequest request = createPreCheckRequest(
                "新课", "张老师", "A101", "周一 08:00-10:00");

        mockMvc.perform(post("/api/schedules/pre-check/severity-summary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].severity").value("BLOCKER"))
                .andExpect(jsonPath("$[0].targetCount").value(1))
                .andExpect(jsonPath("$[0].conflictTypeCount").value(2))
                .andExpect(jsonPath("$[1].severity").value("WARNING"))
                .andExpect(jsonPath("$[1].targetCount").value(2))
                .andExpect(jsonPath("$[1].conflictTypeCount").value(2));
    }

    @Test
    void testSeveritySummary_TeacherBatch_MultipleWarningTargets() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("课1", "张老师", "A101", "周一 08:00-09:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("课2", "张老师", "B202", "周一 09:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("课3", "张老师", "C303", "周一 10:00-11:00"))))
                .andExpect(status().isCreated());

        java.util.List<TeacherBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        items.add(createBatchItem("新排课1", "D404", "周一 08:00-11:00"));

        TeacherBatchPreCheckRequest request = createBatchRequest("张老师", items);

        mockMvc.perform(post("/api/schedules/pre-check/teacher-batch/severity-summary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].severity").value("WARNING"))
                .andExpect(jsonPath("$[0].targetCount").value(3))
                .andExpect(jsonPath("$[0].conflictTypeCount").value(3))
                .andExpect(jsonPath("$[0].existingCourseTargetCount").value(3))
                .andExpect(jsonPath("$[0].pendingItemTargetCount").value(0));
    }

    @Test
    void testSeveritySummary_ClassroomBatch_MultipleWarningTargets() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("课1", "张老师", "A101", "周一 08:00-09:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("课2", "张老师", "A101", "周一 09:00-10:00"))))
                .andExpect(status().isCreated());

        java.util.List<ClassroomBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        items.add(createClassroomBatchItem("新排课1", "张老师", "周一 08:00-10:00"));

        ClassroomBatchPreCheckRequest request = createClassroomBatchRequest("A101", items);

        mockMvc.perform(post("/api/schedules/pre-check/classroom-batch/severity-summary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].severity").value("BLOCKER"))
                .andExpect(jsonPath("$[0].targetCount").value(2))
                .andExpect(jsonPath("$[0].conflictTypeCount").value(4));
    }

    @Test
    void testSeveritySummary_Single_IncludesRequestCourseName() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("已有数学", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        CourseScheduleConflictPreCheckRequest request = createPreCheckRequest(
                "新排物理", "张老师", "B202", "周一 08:00-10:00");

        mockMvc.perform(post("/api/schedules/pre-check/severity-summary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].severity").value("WARNING"))
                .andExpect(jsonPath("$[0].courseNames").isArray())
                .andExpect(jsonPath("$[0].courseNames.length()").value(2))
                .andExpect(jsonPath("$[0].courseNames").value(org.hamcrest.Matchers.containsInAnyOrder("已有数学", "新排物理")));
    }

    @Test
    void testSeveritySummary_Single_BlockerIncludesRequestCourseName() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("已有数学", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        CourseScheduleConflictPreCheckRequest request = createPreCheckRequest(
                "新排物理", "张老师", "A101", "周一 08:00-10:00");

        mockMvc.perform(post("/api/schedules/pre-check/severity-summary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].severity").value("BLOCKER"))
                .andExpect(jsonPath("$[0].courseNames").isArray())
                .andExpect(jsonPath("$[0].courseNames.length()").value(2))
                .andExpect(jsonPath("$[0].courseNames").value(org.hamcrest.Matchers.containsInAnyOrder("已有数学", "新排物理")));
    }

    @Test
    void testSeveritySummary_Single_MixedGroupsBothIncludeRequestCourseName() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("课1", "张老师", "A101", "周一 08:00-09:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("课2", "李老师", "A101", "周一 09:00-10:00"))))
                .andExpect(status().isCreated());

        CourseScheduleConflictPreCheckRequest request = createPreCheckRequest(
                "新排课", "张老师", "A101", "周一 08:00-10:00");

        mockMvc.perform(post("/api/schedules/pre-check/severity-summary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].severity").value("BLOCKER"))
                .andExpect(jsonPath("$[0].courseNames").value(org.hamcrest.Matchers.hasItem("新排课")))
                .andExpect(jsonPath("$[1].severity").value("WARNING"))
                .andExpect(jsonPath("$[1].courseNames").value(org.hamcrest.Matchers.hasItem("新排课")));
    }

    @Test
    void testPendingConflictPairs_TeacherBatch_NoPendingConflicts_ReturnsEmptyArray() throws Exception {
        List<TeacherBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        items.add(createBatchItem("数学", "A101", "周一 08:00-10:00"));
        items.add(createBatchItem("物理", "B202", "周一 10:00-12:00"));

        TeacherBatchPreCheckRequest request = createBatchRequest("张老师", items);

        mockMvc.perform(post("/api/schedules/pre-check/teacher-batch/pending-conflict-pairs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void testPendingConflictPairs_TeacherBatch_SinglePair_TeacherAndClassroomConflict() throws Exception {
        List<TeacherBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        items.add(createBatchItem("数学", "A101", "周一 08:00-10:00"));
        items.add(createBatchItem("物理", "A101", "周一 08:00-10:00"));

        TeacherBatchPreCheckRequest request = createBatchRequest("张老师", items);

        mockMvc.perform(post("/api/schedules/pre-check/teacher-batch/pending-conflict-pairs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].left.index").value(0))
                .andExpect(jsonPath("$[0].left.courseName").value("数学"))
                .andExpect(jsonPath("$[0].left.teacherName").value("张老师"))
                .andExpect(jsonPath("$[0].left.classroom").value("A101"))
                .andExpect(jsonPath("$[0].left.timeSlot").value("周一 08:00-10:00"))
                .andExpect(jsonPath("$[0].right.index").value(1))
                .andExpect(jsonPath("$[0].right.courseName").value("物理"))
                .andExpect(jsonPath("$[0].right.teacherName").value("张老师"))
                .andExpect(jsonPath("$[0].right.classroom").value("A101"))
                .andExpect(jsonPath("$[0].right.timeSlot").value("周一 08:00-10:00"))
                .andExpect(jsonPath("$[0].conflictCount").value(2))
                .andExpect(jsonPath("$[0].conflictTypes.length()").value(2))
                .andExpect(jsonPath("$[0].conflictTypes[0]").value("TEACHER"))
                .andExpect(jsonPath("$[0].conflictTypes[1]").value("CLASSROOM"));
    }

    @Test
    void testPendingConflictPairs_TeacherBatch_MultiplePairs_Sorted() throws Exception {
        List<TeacherBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        items.add(createBatchItem("课0", "A101", "周一 08:00-10:00"));
        items.add(createBatchItem("课1", "A101", "周二 08:00-10:00"));
        items.add(createBatchItem("课2", "A101", "周一 08:00-10:00"));
        items.add(createBatchItem("课3", "A101", "周二 08:00-10:00"));

        TeacherBatchPreCheckRequest request = createBatchRequest("张老师", items);

        mockMvc.perform(post("/api/schedules/pre-check/teacher-batch/pending-conflict-pairs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].left.index").value(0))
                .andExpect(jsonPath("$[0].right.index").value(2))
                .andExpect(jsonPath("$[1].left.index").value(1))
                .andExpect(jsonPath("$[1].right.index").value(3));
    }

    @Test
    void testPendingConflictPairs_TeacherBatch_OnlyExternalConflict_ReturnsEmpty() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("已有课程", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        List<TeacherBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        items.add(createBatchItem("数学", "B202", "周一 08:00-10:00"));
        items.add(createBatchItem("物理", "C303", "周二 08:00-10:00"));

        TeacherBatchPreCheckRequest request = createBatchRequest("张老师", items);

        mockMvc.perform(post("/api/schedules/pre-check/teacher-batch/pending-conflict-pairs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void testPendingConflictPairs_TeacherBatch_NullRequest_Returns400() throws Exception {
        mockMvc.perform(post("/api/schedules/pre-check/teacher-batch/pending-conflict-pairs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("null"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testPendingConflictPairs_ClassroomBatch_NoPendingConflicts_ReturnsEmptyArray() throws Exception {
        List<ClassroomBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        items.add(createClassroomBatchItem("数学", "张老师", "周一 08:00-10:00"));
        items.add(createClassroomBatchItem("物理", "李老师", "周一 10:00-12:00"));

        ClassroomBatchPreCheckRequest request = createClassroomBatchRequest("A101", items);

        mockMvc.perform(post("/api/schedules/pre-check/classroom-batch/pending-conflict-pairs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void testPendingConflictPairs_ClassroomBatch_SinglePair_OnlyClassroomConflict() throws Exception {
        List<ClassroomBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        items.add(createClassroomBatchItem("数学", "张老师", "周一 08:00-10:00"));
        items.add(createClassroomBatchItem("物理", "李老师", "周一 08:00-10:00"));

        ClassroomBatchPreCheckRequest request = createClassroomBatchRequest("A101", items);

        mockMvc.perform(post("/api/schedules/pre-check/classroom-batch/pending-conflict-pairs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].left.index").value(0))
                .andExpect(jsonPath("$[0].left.teacherName").value("张老师"))
                .andExpect(jsonPath("$[0].right.index").value(1))
                .andExpect(jsonPath("$[0].right.teacherName").value("李老师"))
                .andExpect(jsonPath("$[0].conflictCount").value(1))
                .andExpect(jsonPath("$[0].conflictTypes[0]").value("CLASSROOM"));
    }

    @Test
    void testPendingConflictPairs_ClassroomBatch_SinglePair_BothTeacherAndClassroom() throws Exception {
        List<ClassroomBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        items.add(createClassroomBatchItem("数学", "张老师", "周一 08:00-10:00"));
        items.add(createClassroomBatchItem("物理", "张老师", "周一 08:00-10:00"));

        ClassroomBatchPreCheckRequest request = createClassroomBatchRequest("A101", items);

        mockMvc.perform(post("/api/schedules/pre-check/classroom-batch/pending-conflict-pairs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].conflictCount").value(2))
                .andExpect(jsonPath("$[0].conflictTypes[0]").value("TEACHER"))
                .andExpect(jsonPath("$[0].conflictTypes[1]").value("CLASSROOM"));
    }

    @Test
    void testPendingConflictPairs_ClassroomBatch_ThreeItems_CorrectPairsAndOrder() throws Exception {
        List<ClassroomBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        items.add(createClassroomBatchItem("课0", "张老师", "周一 08:00-10:00"));
        items.add(createClassroomBatchItem("课1", "李老师", "周一 08:00-10:00"));
        items.add(createClassroomBatchItem("课2", "王老师", "周一 08:00-10:00"));

        ClassroomBatchPreCheckRequest request = createClassroomBatchRequest("A101", items);

        mockMvc.perform(post("/api/schedules/pre-check/classroom-batch/pending-conflict-pairs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].left.index").value(0))
                .andExpect(jsonPath("$[0].right.index").value(1))
                .andExpect(jsonPath("$[1].left.index").value(0))
                .andExpect(jsonPath("$[1].right.index").value(2))
                .andExpect(jsonPath("$[2].left.index").value(1))
                .andExpect(jsonPath("$[2].right.index").value(2));
    }

    @Test
    void testPendingConflictPairs_ClassroomBatch_MixedExternalAndInternal() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequest("已有课程", "赵老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        List<ClassroomBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        items.add(createClassroomBatchItem("数学", "张老师", "周一 08:00-10:00"));
        items.add(createClassroomBatchItem("物理", "李老师", "周一 08:00-10:00"));
        items.add(createClassroomBatchItem("化学", "王老师", "周二 08:00-10:00"));

        ClassroomBatchPreCheckRequest request = createClassroomBatchRequest("A101", items);

        mockMvc.perform(post("/api/schedules/pre-check/classroom-batch/pending-conflict-pairs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].left.index").value(0))
                .andExpect(jsonPath("$[0].right.index").value(1))
                .andExpect(jsonPath("$[0].conflictTypes[0]").value("CLASSROOM"));
    }

    @Test
    void testPendingConflictPairs_ClassroomBatch_NullRequest_Returns400() throws Exception {
        mockMvc.perform(post("/api/schedules/pre-check/classroom-batch/pending-conflict-pairs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("null"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testPendingConflictPairs_TeacherBatch_EmptyList_ReturnsEmpty() throws Exception {
        List<TeacherBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        TeacherBatchPreCheckRequest request = createBatchRequest("张老师", items);

        mockMvc.perform(post("/api/schedules/pre-check/teacher-batch/pending-conflict-pairs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void testPendingConflictPairs_ClassroomBatch_EmptyList_ReturnsEmpty() throws Exception {
        List<ClassroomBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        ClassroomBatchPreCheckRequest request = createClassroomBatchRequest("A101", items);

        mockMvc.perform(post("/api/schedules/pre-check/classroom-batch/pending-conflict-pairs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void testPendingConflictConnectedGroups_TeacherBatch_NoConflicts_ReturnsEmpty() throws Exception {
        List<TeacherBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        items.add(createBatchItem("数学", "A101", "周一 08:00-10:00"));
        items.add(createBatchItem("物理", "B202", "周一 10:00-12:00"));

        TeacherBatchPreCheckRequest request = createBatchRequest("张老师", items);

        mockMvc.perform(post("/api/schedules/pre-check/teacher-batch/pending-conflict-connected-groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void testPendingConflictConnectedGroups_TeacherBatch_SingleGroup() throws Exception {
        List<TeacherBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        items.add(createBatchItem("数学", "A101", "周一 08:00-10:00"));
        items.add(createBatchItem("物理", "A101", "周一 08:00-10:00"));

        TeacherBatchPreCheckRequest request = createBatchRequest("张老师", items);

        mockMvc.perform(post("/api/schedules/pre-check/teacher-batch/pending-conflict-connected-groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].indexes").value(org.hamcrest.Matchers.contains(0, 1)))
                .andExpect(jsonPath("$[0].courseNames").value(org.hamcrest.Matchers.contains("数学", "物理")))
                .andExpect(jsonPath("$[0].conflictTypes.length()").value(2))
                .andExpect(jsonPath("$[0].conflictTypes[0]").value("TEACHER"))
                .andExpect(jsonPath("$[0].conflictTypes[1]").value("CLASSROOM"))
                .andExpect(jsonPath("$[0].pairCount").value(1))
                .andExpect(jsonPath("$[0].itemCount").value(2));
    }

    @Test
    void testPendingConflictConnectedGroups_TeacherBatch_ChainConnected_SingleGroup() throws Exception {
        List<TeacherBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        items.add(createBatchItem("课0", "A101", "周一 08:00-10:00"));
        items.add(createBatchItem("课1", "A102", "周一 08:00-10:00"));
        items.add(createBatchItem("课2", "A102", "周一 08:00-10:00"));

        TeacherBatchPreCheckRequest request = createBatchRequest("张老师", items);

        mockMvc.perform(post("/api/schedules/pre-check/teacher-batch/pending-conflict-connected-groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].indexes").value(org.hamcrest.Matchers.contains(0, 1, 2)))
                .andExpect(jsonPath("$[0].courseNames").value(org.hamcrest.Matchers.contains("课0", "课1", "课2")))
                .andExpect(jsonPath("$[0].itemCount").value(3))
                .andExpect(jsonPath("$[0].pairCount").value(3));
    }

    @Test
    void testPendingConflictConnectedGroups_TeacherBatch_MultipleGroups_Sorted() throws Exception {
        List<TeacherBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        items.add(createBatchItem("课0", "A101", "周一 08:00-10:00"));
        items.add(createBatchItem("课1", "A101", "周一 08:00-10:00"));
        items.add(createBatchItem("课2", "B202", "周二 08:00-10:00"));
        items.add(createBatchItem("课3", "B202", "周二 08:00-10:00"));
        items.add(createBatchItem("课4", "C303", "周三 08:00-10:00"));

        TeacherBatchPreCheckRequest request = createBatchRequest("张老师", items);

        mockMvc.perform(post("/api/schedules/pre-check/teacher-batch/pending-conflict-connected-groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].indexes").value(org.hamcrest.Matchers.contains(0, 1)))
                .andExpect(jsonPath("$[0].pairCount").value(1))
                .andExpect(jsonPath("$[1].indexes").value(org.hamcrest.Matchers.contains(2, 3)))
                .andExpect(jsonPath("$[1].pairCount").value(1));
    }

    @Test
    void testPendingConflictConnectedGroups_TeacherBatch_NullRequest_Returns400() throws Exception {
        mockMvc.perform(post("/api/schedules/pre-check/teacher-batch/pending-conflict-connected-groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("null"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testPendingConflictConnectedGroups_ClassroomBatch_NoConflicts_ReturnsEmpty() throws Exception {
        List<ClassroomBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        items.add(createClassroomBatchItem("数学", "张老师", "周一 08:00-10:00"));
        items.add(createClassroomBatchItem("物理", "李老师", "周一 10:00-12:00"));

        ClassroomBatchPreCheckRequest request = createClassroomBatchRequest("A101", items);

        mockMvc.perform(post("/api/schedules/pre-check/classroom-batch/pending-conflict-connected-groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void testPendingConflictConnectedGroups_ClassroomBatch_SingleGroup() throws Exception {
        List<ClassroomBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        items.add(createClassroomBatchItem("数学", "张老师", "周一 08:00-10:00"));
        items.add(createClassroomBatchItem("物理", "张老师", "周一 08:00-10:00"));

        ClassroomBatchPreCheckRequest request = createClassroomBatchRequest("A101", items);

        mockMvc.perform(post("/api/schedules/pre-check/classroom-batch/pending-conflict-connected-groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].indexes").value(org.hamcrest.Matchers.contains(0, 1)))
                .andExpect(jsonPath("$[0].courseNames").value(org.hamcrest.Matchers.contains("数学", "物理")))
                .andExpect(jsonPath("$[0].conflictTypes.length()").value(2))
                .andExpect(jsonPath("$[0].conflictTypes[0]").value("TEACHER"))
                .andExpect(jsonPath("$[0].conflictTypes[1]").value("CLASSROOM"))
                .andExpect(jsonPath("$[0].pairCount").value(1))
                .andExpect(jsonPath("$[0].itemCount").value(2));
    }

    @Test
    void testPendingConflictConnectedGroups_ClassroomBatch_ChainConnected_SingleGroup() throws Exception {
        List<ClassroomBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        items.add(createClassroomBatchItem("课0", "张老师", "周一 08:00-10:00"));
        items.add(createClassroomBatchItem("课1", "李老师", "周一 08:00-10:00"));
        items.add(createClassroomBatchItem("课2", "王老师", "周一 08:00-10:00"));

        ClassroomBatchPreCheckRequest request = createClassroomBatchRequest("A101", items);

        mockMvc.perform(post("/api/schedules/pre-check/classroom-batch/pending-conflict-connected-groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].indexes").value(org.hamcrest.Matchers.contains(0, 1, 2)))
                .andExpect(jsonPath("$[0].courseNames").value(org.hamcrest.Matchers.contains("课0", "课1", "课2")))
                .andExpect(jsonPath("$[0].itemCount").value(3))
                .andExpect(jsonPath("$[0].pairCount").value(3));
    }

    @Test
    void testPendingConflictConnectedGroups_ClassroomBatch_MultipleGroups_Sorted() throws Exception {
        List<ClassroomBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        items.add(createClassroomBatchItem("课0", "张老师", "周一 08:00-10:00"));
        items.add(createClassroomBatchItem("课1", "张老师", "周一 08:00-10:00"));
        items.add(createClassroomBatchItem("课2", "李老师", "周二 08:00-10:00"));
        items.add(createClassroomBatchItem("课3", "李老师", "周二 08:00-10:00"));
        items.add(createClassroomBatchItem("课4", "王老师", "周三 08:00-10:00"));

        ClassroomBatchPreCheckRequest request = createClassroomBatchRequest("A101", items);

        mockMvc.perform(post("/api/schedules/pre-check/classroom-batch/pending-conflict-connected-groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].indexes").value(org.hamcrest.Matchers.contains(0, 1)))
                .andExpect(jsonPath("$[0].pairCount").value(1))
                .andExpect(jsonPath("$[1].indexes").value(org.hamcrest.Matchers.contains(2, 3)))
                .andExpect(jsonPath("$[1].pairCount").value(1));
    }

    @Test
    void testPendingConflictConnectedGroups_ClassroomBatch_NullRequest_Returns400() throws Exception {
        mockMvc.perform(post("/api/schedules/pre-check/classroom-batch/pending-conflict-connected-groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("null"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testPendingConflictImpactSummary_TeacherBatch_NoConflicts_ReturnsEmpty() throws Exception {
        List<TeacherBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        items.add(createBatchItem("数学", "A101", "周一 08:00-10:00"));
        items.add(createBatchItem("物理", "B202", "周一 10:00-12:00"));

        TeacherBatchPreCheckRequest request = createBatchRequest("张老师", items);

        mockMvc.perform(post("/api/schedules/pre-check/teacher-batch/pending-conflict-impact-summary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void testPendingConflictImpactSummary_TeacherBatch_SinglePair() throws Exception {
        List<TeacherBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        items.add(createBatchItem("数学", "A101", "周一 08:00-10:00"));
        items.add(createBatchItem("物理", "A101", "周一 08:00-10:00"));

        TeacherBatchPreCheckRequest request = createBatchRequest("张老师", items);

        mockMvc.perform(post("/api/schedules/pre-check/teacher-batch/pending-conflict-impact-summary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].index").value(0))
                .andExpect(jsonPath("$[0].courseName").value("数学"))
                .andExpect(jsonPath("$[0].directPairCount").value(1))
                .andExpect(jsonPath("$[0].connectedGroupSize").value(2))
                .andExpect(jsonPath("$[0].conflictTypes.length()").value(2))
                .andExpect(jsonPath("$[0].largestGroup").value(true))
                .andExpect(jsonPath("$[1].index").value(1))
                .andExpect(jsonPath("$[1].courseName").value("物理"))
                .andExpect(jsonPath("$[1].directPairCount").value(1))
                .andExpect(jsonPath("$[1].connectedGroupSize").value(2))
                .andExpect(jsonPath("$[1].largestGroup").value(true));
    }

    @Test
    void testPendingConflictImpactSummary_TeacherBatch_MultipleItems_SingleGroup() throws Exception {
        List<TeacherBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        items.add(createBatchItem("课0", "A101", "周一 08:00-10:00"));
        items.add(createBatchItem("课1", "A102", "周一 08:00-10:00"));
        items.add(createBatchItem("课2", "A102", "周一 08:00-10:00"));

        TeacherBatchPreCheckRequest request = createBatchRequest("张老师", items);

        mockMvc.perform(post("/api/schedules/pre-check/teacher-batch/pending-conflict-impact-summary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].index").value(0))
                .andExpect(jsonPath("$[0].courseName").value("课0"))
                .andExpect(jsonPath("$[0].directPairCount").value(2))
                .andExpect(jsonPath("$[0].connectedGroupSize").value(3))
                .andExpect(jsonPath("$[0].conflictTypes.length()").value(1))
                .andExpect(jsonPath("$[0].conflictTypes[0]").value("TEACHER"))
                .andExpect(jsonPath("$[0].largestGroup").value(true))
                .andExpect(jsonPath("$[1].index").value(1))
                .andExpect(jsonPath("$[1].directPairCount").value(2))
                .andExpect(jsonPath("$[1].connectedGroupSize").value(3))
                .andExpect(jsonPath("$[1].largestGroup").value(true))
                .andExpect(jsonPath("$[2].index").value(2))
                .andExpect(jsonPath("$[2].directPairCount").value(2))
                .andExpect(jsonPath("$[2].connectedGroupSize").value(3))
                .andExpect(jsonPath("$[2].largestGroup").value(true));
    }

    @Test
    void testPendingConflictImpactSummary_TeacherBatch_MultipleGroups_LargestMarked() throws Exception {
        List<TeacherBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        items.add(createBatchItem("课0", "A101", "周一 08:00-10:00"));
        items.add(createBatchItem("课1", "A101", "周一 08:00-10:00"));
        items.add(createBatchItem("课2", "B202", "周二 08:00-10:00"));
        items.add(createBatchItem("课3", "B202", "周二 08:00-10:00"));
        items.add(createBatchItem("课4", "B203", "周二 08:00-10:00"));
        items.add(createBatchItem("课5", "C303", "周三 08:00-10:00"));

        TeacherBatchPreCheckRequest request = createBatchRequest("张老师", items);

        mockMvc.perform(post("/api/schedules/pre-check/teacher-batch/pending-conflict-impact-summary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[0].index").value(0))
                .andExpect(jsonPath("$[0].connectedGroupSize").value(2))
                .andExpect(jsonPath("$[0].largestGroup").value(false))
                .andExpect(jsonPath("$[1].index").value(1))
                .andExpect(jsonPath("$[1].connectedGroupSize").value(2))
                .andExpect(jsonPath("$[1].largestGroup").value(false))
                .andExpect(jsonPath("$[2].index").value(2))
                .andExpect(jsonPath("$[2].connectedGroupSize").value(3))
                .andExpect(jsonPath("$[2].largestGroup").value(true))
                .andExpect(jsonPath("$[3].index").value(3))
                .andExpect(jsonPath("$[3].connectedGroupSize").value(3))
                .andExpect(jsonPath("$[3].largestGroup").value(true))
                .andExpect(jsonPath("$[4].index").value(4))
                .andExpect(jsonPath("$[4].connectedGroupSize").value(3))
                .andExpect(jsonPath("$[4].largestGroup").value(true));
    }

    @Test
    void testPendingConflictImpactSummary_TeacherBatch_NullRequest_Returns400() throws Exception {
        mockMvc.perform(post("/api/schedules/pre-check/teacher-batch/pending-conflict-impact-summary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("null"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testPendingConflictImpactSummary_ClassroomBatch_NoConflicts_ReturnsEmpty() throws Exception {
        List<ClassroomBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        items.add(createClassroomBatchItem("数学", "张老师", "周一 08:00-10:00"));
        items.add(createClassroomBatchItem("物理", "李老师", "周一 10:00-12:00"));

        ClassroomBatchPreCheckRequest request = createClassroomBatchRequest("A101", items);

        mockMvc.perform(post("/api/schedules/pre-check/classroom-batch/pending-conflict-impact-summary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void testPendingConflictImpactSummary_ClassroomBatch_SinglePair() throws Exception {
        List<ClassroomBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        items.add(createClassroomBatchItem("数学", "张老师", "周一 08:00-10:00"));
        items.add(createClassroomBatchItem("物理", "李老师", "周一 08:00-10:00"));

        ClassroomBatchPreCheckRequest request = createClassroomBatchRequest("A101", items);

        mockMvc.perform(post("/api/schedules/pre-check/classroom-batch/pending-conflict-impact-summary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].index").value(0))
                .andExpect(jsonPath("$[0].courseName").value("数学"))
                .andExpect(jsonPath("$[0].directPairCount").value(1))
                .andExpect(jsonPath("$[0].connectedGroupSize").value(2))
                .andExpect(jsonPath("$[0].conflictTypes.length()").value(1))
                .andExpect(jsonPath("$[0].conflictTypes[0]").value("CLASSROOM"))
                .andExpect(jsonPath("$[0].largestGroup").value(true))
                .andExpect(jsonPath("$[1].index").value(1))
                .andExpect(jsonPath("$[1].courseName").value("物理"))
                .andExpect(jsonPath("$[1].directPairCount").value(1))
                .andExpect(jsonPath("$[1].connectedGroupSize").value(2))
                .andExpect(jsonPath("$[1].largestGroup").value(true));
    }

    @Test
    void testPendingConflictImpactSummary_ClassroomBatch_MultipleItems_SingleGroup() throws Exception {
        List<ClassroomBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        items.add(createClassroomBatchItem("课0", "张老师", "周一 08:00-10:00"));
        items.add(createClassroomBatchItem("课1", "李老师", "周一 08:00-10:00"));
        items.add(createClassroomBatchItem("课2", "王老师", "周一 08:00-10:00"));

        ClassroomBatchPreCheckRequest request = createClassroomBatchRequest("A101", items);

        mockMvc.perform(post("/api/schedules/pre-check/classroom-batch/pending-conflict-impact-summary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].index").value(0))
                .andExpect(jsonPath("$[0].courseName").value("课0"))
                .andExpect(jsonPath("$[0].directPairCount").value(2))
                .andExpect(jsonPath("$[0].connectedGroupSize").value(3))
                .andExpect(jsonPath("$[0].conflictTypes.length()").value(1))
                .andExpect(jsonPath("$[0].conflictTypes[0]").value("CLASSROOM"))
                .andExpect(jsonPath("$[0].largestGroup").value(true))
                .andExpect(jsonPath("$[1].index").value(1))
                .andExpect(jsonPath("$[1].directPairCount").value(2))
                .andExpect(jsonPath("$[1].connectedGroupSize").value(3))
                .andExpect(jsonPath("$[1].largestGroup").value(true))
                .andExpect(jsonPath("$[2].index").value(2))
                .andExpect(jsonPath("$[2].directPairCount").value(2))
                .andExpect(jsonPath("$[2].connectedGroupSize").value(3))
                .andExpect(jsonPath("$[2].largestGroup").value(true));
    }

    @Test
    void testPendingConflictImpactSummary_ClassroomBatch_MultipleGroups_LargestMarked() throws Exception {
        List<ClassroomBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        items.add(createClassroomBatchItem("课0", "张老师", "周一 08:00-10:00"));
        items.add(createClassroomBatchItem("课1", "李老师", "周一 08:00-10:00"));
        items.add(createClassroomBatchItem("课2", "王老师", "周二 08:00-10:00"));
        items.add(createClassroomBatchItem("课3", "赵老师", "周二 08:00-10:00"));
        items.add(createClassroomBatchItem("课4", "钱老师", "周二 10:00-12:00"));
        items.add(createClassroomBatchItem("课5", "孙老师", "周三 08:00-10:00"));

        ClassroomBatchPreCheckRequest request = createClassroomBatchRequest("A101", items);

        mockMvc.perform(post("/api/schedules/pre-check/classroom-batch/pending-conflict-impact-summary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].index").value(0))
                .andExpect(jsonPath("$[0].connectedGroupSize").value(2))
                .andExpect(jsonPath("$[0].largestGroup").value(true))
                .andExpect(jsonPath("$[1].index").value(1))
                .andExpect(jsonPath("$[1].connectedGroupSize").value(2))
                .andExpect(jsonPath("$[1].largestGroup").value(true))
                .andExpect(jsonPath("$[2].index").value(2))
                .andExpect(jsonPath("$[2].connectedGroupSize").value(2))
                .andExpect(jsonPath("$[2].largestGroup").value(true))
                .andExpect(jsonPath("$[3].index").value(3))
                .andExpect(jsonPath("$[3].connectedGroupSize").value(2))
                .andExpect(jsonPath("$[3].largestGroup").value(true));
    }

    @Test
    void testPendingConflictImpactSummary_ClassroomBatch_NullRequest_Returns400() throws Exception {
        mockMvc.perform(post("/api/schedules/pre-check/classroom-batch/pending-conflict-impact-summary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("null"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testManualReviewSummary_TeacherBatch_NoConflicts() throws Exception {
        java.util.List<TeacherBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        items.add(createTeacherBatchItem("数学", "A101", "周一 08:00-10:00"));
        items.add(createTeacherBatchItem("物理", "B202", "周二 08:00-10:00"));

        TeacherBatchPreCheckRequest request = createTeacherBatchRequest("张老师", items);

        mockMvc.perform(post("/api/schedules/pre-check/teacher-batch/manual-review-summary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.conflictItems").value(0))
                .andExpect(jsonPath("$.manualReviewRequired").value(false))
                .andExpect(jsonPath("$.reason").isEmpty());
    }

    @Test
    void testManualReviewSummary_TeacherBatch_SingleConflict_ReturnsFalse() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("已有课", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        java.util.List<TeacherBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        items.add(createTeacherBatchItem("数学", "B202", "周一 08:00-10:00"));
        items.add(createTeacherBatchItem("物理", "C303", "周二 08:00-10:00"));

        TeacherBatchPreCheckRequest request = createTeacherBatchRequest("张老师", items);

        mockMvc.perform(post("/api/schedules/pre-check/teacher-batch/manual-review-summary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.conflictItems").value(1))
                .andExpect(jsonPath("$.manualReviewRequired").value(false))
                .andExpect(jsonPath("$.reason").isEmpty());
    }

    @Test
    void testManualReviewSummary_TeacherBatch_DualTypeConflict_ReturnsTrue() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("已有课", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        java.util.List<TeacherBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        items.add(createTeacherBatchItem("数学", "A101", "周一 08:00-10:00"));
        items.add(createTeacherBatchItem("物理", "B202", "周二 08:00-10:00"));

        TeacherBatchPreCheckRequest request = createTeacherBatchRequest("张老师", items);

        mockMvc.perform(post("/api/schedules/pre-check/teacher-batch/manual-review-summary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.conflictItems").value(1))
                .andExpect(jsonPath("$.manualReviewRequired").value(true))
                .andExpect(jsonPath("$.reason").value("待排项同时存在老师和教室冲突，需要人工处理"));
    }

    @Test
    void testManualReviewSummary_TeacherBatch_ThreeItemChain_ReturnsTrue() throws Exception {
        java.util.List<TeacherBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        items.add(createTeacherBatchItem("数学", "A101", "周一 08:00-10:00"));
        items.add(createTeacherBatchItem("物理", "B202", "周一 08:00-10:00"));
        items.add(createTeacherBatchItem("化学", "C303", "周一 08:00-10:00"));

        TeacherBatchPreCheckRequest request = createTeacherBatchRequest("张老师", items);

        mockMvc.perform(post("/api/schedules/pre-check/teacher-batch/manual-review-summary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(3))
                .andExpect(jsonPath("$.manualReviewRequired").value(true))
                .andExpect(jsonPath("$.reason").value("待排项之间形成3个及以上链式冲突，需要人工处理"));
    }

    @Test
    void testManualReviewSummary_ClassroomBatch_NoConflicts() throws Exception {
        java.util.List<ClassroomBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        items.add(createClassroomBatchItem("数学", "张老师", "周一 08:00-10:00"));
        items.add(createClassroomBatchItem("物理", "李老师", "周二 08:00-10:00"));

        ClassroomBatchPreCheckRequest request = createClassroomBatchRequest("A101", items);

        mockMvc.perform(post("/api/schedules/pre-check/classroom-batch/manual-review-summary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.conflictItems").value(0))
                .andExpect(jsonPath("$.manualReviewRequired").value(false));
    }

    @Test
    void testManualReviewSummary_ClassroomBatch_SingleConflict_ReturnsFalse() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("已有课", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        java.util.List<ClassroomBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        items.add(createClassroomBatchItem("数学", "李老师", "周一 08:00-10:00"));
        items.add(createClassroomBatchItem("物理", "王老师", "周二 08:00-10:00"));

        ClassroomBatchPreCheckRequest request = createClassroomBatchRequest("A101", items);

        mockMvc.perform(post("/api/schedules/pre-check/classroom-batch/manual-review-summary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.conflictItems").value(1))
                .andExpect(jsonPath("$.manualReviewRequired").value(false));
    }

    @Test
    void testManualReviewSummary_ClassroomBatch_DualTypeConflict_ReturnsTrue() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("已有课", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        java.util.List<ClassroomBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        items.add(createClassroomBatchItem("数学", "张老师", "周一 08:00-10:00"));
        items.add(createClassroomBatchItem("物理", "李老师", "周二 08:00-10:00"));

        ClassroomBatchPreCheckRequest request = createClassroomBatchRequest("A101", items);

        mockMvc.perform(post("/api/schedules/pre-check/classroom-batch/manual-review-summary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.conflictItems").value(1))
                .andExpect(jsonPath("$.manualReviewRequired").value(true))
                .andExpect(jsonPath("$.reason").value("待排项同时存在老师和教室冲突，需要人工处理"));
    }

    @Test
    void testManualReviewSummary_ClassroomBatch_ThreeItemChain_ReturnsTrue() throws Exception {
        java.util.List<ClassroomBatchPreCheckItemRequest> items = new java.util.ArrayList<>();
        items.add(createClassroomBatchItem("数学", "张老师", "周一 08:00-10:00"));
        items.add(createClassroomBatchItem("物理", "李老师", "周一 08:00-10:00"));
        items.add(createClassroomBatchItem("化学", "王老师", "周一 08:00-10:00"));

        ClassroomBatchPreCheckRequest request = createClassroomBatchRequest("A101", items);

        mockMvc.perform(post("/api/schedules/pre-check/classroom-batch/manual-review-summary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(3))
                .andExpect(jsonPath("$.manualReviewRequired").value(true))
                .andExpect(jsonPath("$.reason").value("待排项之间形成3个及以上链式冲突，需要人工处理"));
    }

    @Test
    void testConflictRiskPreview_NoConflict_ReturnsLOW() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张教授", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        ConflictRiskPreviewRequest request = new ConflictRiskPreviewRequest();
        request.setTeacherName("李教授");
        request.setClassroom("B202");
        request.setTimeSlot("周二 08:00-10:00");

        mockMvc.perform(post("/api/schedules/conflict-risk-preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teacherConflictCount").value(0))
                .andExpect(jsonPath("$.classroomConflictCount").value(0))
                .andExpect(jsonPath("$.conflictCourseNames").isEmpty())
                .andExpect(jsonPath("$.riskLevel").value("LOW"));
    }

    @Test
    void testConflictRiskPreview_TeacherConflictOnly_ReturnsMEDIUM() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张教授", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        ConflictRiskPreviewRequest request = new ConflictRiskPreviewRequest();
        request.setTeacherName("张教授");
        request.setClassroom("B202");
        request.setTimeSlot("周一 08:00-10:00");

        mockMvc.perform(post("/api/schedules/conflict-risk-preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teacherConflictCount").value(1))
                .andExpect(jsonPath("$.classroomConflictCount").value(0))
                .andExpect(jsonPath("$.conflictCourseNames[0]").value("数学"))
                .andExpect(jsonPath("$.riskLevel").value("MEDIUM"));
    }

    @Test
    void testConflictRiskPreview_ClassroomConflictOnly_ReturnsMEDIUM() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张教授", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        ConflictRiskPreviewRequest request = new ConflictRiskPreviewRequest();
        request.setTeacherName("李教授");
        request.setClassroom("A101");
        request.setTimeSlot("周一 08:00-10:00");

        mockMvc.perform(post("/api/schedules/conflict-risk-preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teacherConflictCount").value(0))
                .andExpect(jsonPath("$.classroomConflictCount").value(1))
                .andExpect(jsonPath("$.conflictCourseNames[0]").value("数学"))
                .andExpect(jsonPath("$.riskLevel").value("MEDIUM"));
    }

    @Test
    void testConflictRiskPreview_BothConflicts_ReturnsHIGH() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张教授", "B202", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("英语", "李教授", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        ConflictRiskPreviewRequest request = new ConflictRiskPreviewRequest();
        request.setTeacherName("张教授");
        request.setClassroom("A101");
        request.setTimeSlot("周一 08:00-10:00");

        mockMvc.perform(post("/api/schedules/conflict-risk-preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teacherConflictCount").value(1))
                .andExpect(jsonPath("$.classroomConflictCount").value(1))
                .andExpect(jsonPath("$.riskLevel").value("HIGH"));
    }

    @Test
    void testConflictRiskPreview_NullRequest_Returns400() throws Exception {
        mockMvc.perform(post("/api/schedules/conflict-risk-preview")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testConflictRiskPreview_EmptyTeacherName_Returns400() throws Exception {
        ConflictRiskPreviewRequest request = new ConflictRiskPreviewRequest();
        request.setTeacherName("");
        request.setClassroom("A101");
        request.setTimeSlot("周一 08:00-10:00");

        mockMvc.perform(post("/api/schedules/conflict-risk-preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("老师名不能为空"));
    }

    @Test
    void testConflictRiskPreview_EmptyClassroom_Returns400() throws Exception {
        ConflictRiskPreviewRequest request = new ConflictRiskPreviewRequest();
        request.setTeacherName("张教授");
        request.setClassroom("");
        request.setTimeSlot("周一 08:00-10:00");

        mockMvc.perform(post("/api/schedules/conflict-risk-preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("教室名不能为空"));
    }

    @Test
    void testConflictRiskPreview_EmptyTimeSlot_Returns400() throws Exception {
        ConflictRiskPreviewRequest request = new ConflictRiskPreviewRequest();
        request.setTeacherName("张教授");
        request.setClassroom("A101");
        request.setTimeSlot("");

        mockMvc.perform(post("/api/schedules/conflict-risk-preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("时间段不能为空"));
    }

    @Test
    void testGetTeacherWeeklySummary_EmptyTeacherName_Returns400() throws Exception {
        mockMvc.perform(get("/api/schedules/teacher-weekly-summary")
                        .param("teacherName", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("老师名不能为空"));
    }

    @Test
    void testGetTeacherWeeklySummary_NoCourses_ReturnsZeroSummary() throws Exception {
        mockMvc.perform(get("/api/schedules/teacher-weekly-summary")
                        .param("teacherName", "张老师"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teacherName").value("张老师"))
                .andExpect(jsonPath("$.totalCourses").value(0))
                .andExpect(jsonPath("$.occupiedDays").value(0))
                .andExpect(jsonPath("$.busiestDay").doesNotExist())
                .andExpect(jsonPath("$.courseNames").isArray())
                .andExpect(jsonPath("$.courseNames").isEmpty());
    }

    @Test
    void testGetTeacherWeeklySummary_MultipleDays_CorrectSummary() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("物理", "张老师", "B202", "周一 10:00-12:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("化学", "张老师", "C303", "周二 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("英语", "张老师", "D404", "周三 14:00-16:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules/teacher-weekly-summary")
                        .param("teacherName", "张老师"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teacherName").value("张老师"))
                .andExpect(jsonPath("$.totalCourses").value(4))
                .andExpect(jsonPath("$.occupiedDays").value(3))
                .andExpect(jsonPath("$.busiestDay").value("周一"))
                .andExpect(jsonPath("$.courseNames[0]").value("数学"))
                .andExpect(jsonPath("$.courseNames[1]").value("物理"))
                .andExpect(jsonPath("$.courseNames[2]").value("化学"))
                .andExpect(jsonPath("$.courseNames[3]").value("英语"));
    }

    @Test
    void testGetTeacherWeeklySummary_TieBusiestDay_PicksEarlierWeekday() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张老师", "A101", "周三 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("物理", "张老师", "B202", "周三 10:00-12:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("化学", "张老师", "C303", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("英语", "张老师", "D404", "周一 10:00-12:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules/teacher-weekly-summary")
                        .param("teacherName", "张老师"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.busiestDay").value("周一"));
    }

    @Test
    void testGetTeacherWeeklySummary_CourseNamesDeduped_KeepCreationOrder() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("物理", "张老师", "B202", "周二 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张老师", "A101", "周三 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("化学", "张老师", "C303", "周四 08:00-10:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules/teacher-weekly-summary")
                        .param("teacherName", "张老师"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCourses").value(4))
                .andExpect(jsonPath("$.courseNames.length()").value(3))
                .andExpect(jsonPath("$.courseNames[0]").value("数学"))
                .andExpect(jsonPath("$.courseNames[1]").value("物理"))
                .andExpect(jsonPath("$.courseNames[2]").value("化学"));
    }

    @Test
    void testGetTeacherWeeklySummary_OnlyCountsSpecifiedTeacher() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("物理", "李老师", "B202", "周二 08:00-10:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules/teacher-weekly-summary")
                        .param("teacherName", "张老师"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCourses").value(1))
                .andExpect(jsonPath("$.occupiedDays").value(1))
                .andExpect(jsonPath("$.busiestDay").value("周一"))
                .andExpect(jsonPath("$.courseNames[0]").value("数学"));
    }

    @Test
    void testGetClassroomWeeklySummary_EmptyClassroom_Returns400() throws Exception {
        mockMvc.perform(get("/api/schedules/classroom-weekly-summary")
                        .param("classroom", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("教室名不能为空"));
    }

    @Test
    void testGetClassroomWeeklySummary_NullClassroom_Returns400() throws Exception {
        mockMvc.perform(get("/api/schedules/classroom-weekly-summary"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testGetClassroomWeeklySummary_WhitespaceClassroom_Returns400() throws Exception {
        mockMvc.perform(get("/api/schedules/classroom-weekly-summary")
                        .param("classroom", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("教室名不能为空"));
    }

    @Test
    void testGetClassroomWeeklySummary_NoCourses_ReturnsZeroSummary() throws Exception {
        mockMvc.perform(get("/api/schedules/classroom-weekly-summary")
                        .param("classroom", "A101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.classroom").value("A101"))
                .andExpect(jsonPath("$.totalCourses").value(0))
                .andExpect(jsonPath("$.occupiedDays").value(0))
                .andExpect(jsonPath("$.busiestDay").doesNotExist())
                .andExpect(jsonPath("$.teacherNames").isArray())
                .andExpect(jsonPath("$.teacherNames").isEmpty());
    }

    @Test
    void testGetClassroomWeeklySummary_MultipleDays_CorrectSummary() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("物理", "李老师", "A101", "周一 10:00-12:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("化学", "王老师", "A101", "周二 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("英语", "赵老师", "A101", "周三 14:00-16:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules/classroom-weekly-summary")
                        .param("classroom", "A101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.classroom").value("A101"))
                .andExpect(jsonPath("$.totalCourses").value(4))
                .andExpect(jsonPath("$.occupiedDays").value(3))
                .andExpect(jsonPath("$.busiestDay").value("周一"))
                .andExpect(jsonPath("$.teacherNames[0]").value("张老师"))
                .andExpect(jsonPath("$.teacherNames[1]").value("李老师"))
                .andExpect(jsonPath("$.teacherNames[2]").value("王老师"))
                .andExpect(jsonPath("$.teacherNames[3]").value("赵老师"));
    }

    @Test
    void testGetClassroomWeeklySummary_TieBusiestDay_PicksEarlierWeekday() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张老师", "A101", "周三 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("物理", "李老师", "A101", "周三 10:00-12:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("化学", "王老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("英语", "赵老师", "A101", "周一 10:00-12:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules/classroom-weekly-summary")
                        .param("classroom", "A101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.busiestDay").value("周一"));
    }

    @Test
    void testGetClassroomWeeklySummary_TeacherNamesDeduped_KeepCreationOrder() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("物理", "李老师", "A101", "周二 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("化学", "张老师", "A101", "周三 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("英语", "王老师", "A101", "周四 08:00-10:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules/classroom-weekly-summary")
                        .param("classroom", "A101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCourses").value(4))
                .andExpect(jsonPath("$.teacherNames.length()").value(3))
                .andExpect(jsonPath("$.teacherNames[0]").value("张老师"))
                .andExpect(jsonPath("$.teacherNames[1]").value("李老师"))
                .andExpect(jsonPath("$.teacherNames[2]").value("王老师"));
    }

    @Test
    void testGetClassroomWeeklySummary_OnlyCountsSpecifiedClassroom() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("物理", "李老师", "B202", "周二 08:00-10:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules/classroom-weekly-summary")
                        .param("classroom", "A101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCourses").value(1))
                .andExpect(jsonPath("$.occupiedDays").value(1))
                .andExpect(jsonPath("$.busiestDay").value("周一"))
                .andExpect(jsonPath("$.teacherNames[0]").value("张老师"));
    }

    @Test
    void testGetClassroomWeeklySummary_ClassroomNameTrimmed() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules/classroom-weekly-summary")
                        .param("classroom", "  A101  "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.classroom").value("A101"))
                .andExpect(jsonPath("$.totalCourses").value(1));
    }

    @Test
    void testGetTeacherFreeDaySummary_NoCourses_AllDaysFree() throws Exception {
        mockMvc.perform(get("/api/schedules/teacher-free-day-summary")
                        .param("teacherName", "张老师"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teacherName").value("张老师"))
                .andExpect(jsonPath("$.busyDays").isArray())
                .andExpect(jsonPath("$.busyDays").isEmpty())
                .andExpect(jsonPath("$.freeDays.length()").value(7))
                .andExpect(jsonPath("$.freeDayCount").value(7))
                .andExpect(jsonPath("$.freeDays[0]").value("周一"))
                .andExpect(jsonPath("$.freeDays[6]").value("周日"));
    }

    @Test
    void testGetTeacherFreeDaySummary_PartialDays_CorrectBusyAndFree() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("物理", "张老师", "B202", "周三 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("化学", "李老师", "C303", "周二 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("英语", "张老师", "D404", "周五 14:00-16:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules/teacher-free-day-summary")
                        .param("teacherName", "张老师"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teacherName").value("张老师"))
                .andExpect(jsonPath("$.busyDays.length()").value(3))
                .andExpect(jsonPath("$.busyDays[0]").value("周一"))
                .andExpect(jsonPath("$.busyDays[1]").value("周三"))
                .andExpect(jsonPath("$.busyDays[2]").value("周五"))
                .andExpect(jsonPath("$.freeDays.length()").value(4))
                .andExpect(jsonPath("$.freeDays[0]").value("周二"))
                .andExpect(jsonPath("$.freeDays[1]").value("周四"))
                .andExpect(jsonPath("$.freeDays[2]").value("周六"))
                .andExpect(jsonPath("$.freeDays[3]").value("周日"))
                .andExpect(jsonPath("$.freeDayCount").value(4));
    }

    @Test
    void testGetTeacherFreeDaySummary_AllSevenDaysOccupied_NoFreeDays() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("周一", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("周二", "张老师", "A101", "周二 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("周三", "张老师", "A101", "周三 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("周四", "张老师", "A101", "周四 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("周五", "张老师", "A101", "周五 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("周六", "张老师", "A101", "周六 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("周日", "张老师", "A101", "周日 08:00-10:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules/teacher-free-day-summary")
                        .param("teacherName", "张老师"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.busyDays.length()").value(7))
                .andExpect(jsonPath("$.freeDays").isArray())
                .andExpect(jsonPath("$.freeDays").isEmpty())
                .andExpect(jsonPath("$.freeDayCount").value(0));
    }

    @Test
    void testGetTeacherFreeDaySummary_EmptyTeacherName_Returns400() throws Exception {
        mockMvc.perform(get("/api/schedules/teacher-free-day-summary")
                        .param("teacherName", ""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testGetTeacherFreeDaySummary_WhitespaceTeacherName_Returns400() throws Exception {
        mockMvc.perform(get("/api/schedules/teacher-free-day-summary")
                        .param("teacherName", "   "))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testGetTeacherFreeDaySummary_InvalidTimeSlotCourses_NotCounted() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("物理", "张老师", "B202", "周八 08:00-10:00"))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("生物", "张老师", "D404", "周四 09:00-11:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules/teacher-free-day-summary")
                        .param("teacherName", "张老师"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.busyDays.length()").value(2))
                .andExpect(jsonPath("$.busyDays[0]").value("周一"))
                .andExpect(jsonPath("$.busyDays[1]").value("周四"))
                .andExpect(jsonPath("$.freeDayCount").value(5));
    }

    @Test
    void testGetTeacherFreeDaySummary_TrimmedTeacherName_CorrectResult() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张老师", "A101", "周二 08:00-10:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules/teacher-free-day-summary")
                        .param("teacherName", "  张老师  "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teacherName").value("张老师"))
                .andExpect(jsonPath("$.busyDays.length()").value(1))
                .andExpect(jsonPath("$.busyDays[0]").value("周二"))
                .andExpect(jsonPath("$.freeDayCount").value(6));
    }

    @Test
    void testGetTeacherFreeDaySummary_BusyDaysSorted_MondayToSundayOrder() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("周日", "张老师", "A101", "周日 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("周三", "张老师", "B202", "周三 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("周一", "张老师", "C303", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules/teacher-free-day-summary")
                        .param("teacherName", "张老师"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.busyDays[0]").value("周一"))
                .andExpect(jsonPath("$.busyDays[1]").value("周三"))
                .andExpect(jsonPath("$.busyDays[2]").value("周日"));
    }

    @Test
    void testGetTeacherFreeDaySummary_MultipleCoursesSameDay_CountedOnce() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("物理", "张老师", "B202", "周一 10:00-12:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("化学", "张老师", "C303", "周一 14:00-16:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules/teacher-free-day-summary")
                        .param("teacherName", "张老师"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.busyDays.length()").value(1))
                .andExpect(jsonPath("$.busyDays[0]").value("周一"))
                .andExpect(jsonPath("$.freeDayCount").value(6));
    }

    @Test
    void testGetTeacherFreeDaySummary_SundayCourses_CountedAsLast() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("周六课", "张老师", "A101", "周六 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("周日课", "张老师", "B202", "周日 10:00-12:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules/teacher-free-day-summary")
                        .param("teacherName", "张老师"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.busyDays[0]").value("周六"))
                .andExpect(jsonPath("$.busyDays[1]").value("周日"))
                .andExpect(jsonPath("$.freeDayCount").value(5));
    }

    @Test
    void testGetTeacherFreeDaySummary_OtherTeachersCourses_NotIncluded() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("物理", "李老师", "B202", "周二 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("化学", "王老师", "C303", "周三 08:00-10:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules/teacher-free-day-summary")
                        .param("teacherName", "张老师"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.busyDays.length()").value(1))
                .andExpect(jsonPath("$.busyDays[0]").value("周一"))
                .andExpect(jsonPath("$.freeDayCount").value(6));
    }

    @Test
    void testGetClassroomFreeDaySummary_NoCourses_AllDaysFree() throws Exception {
        mockMvc.perform(get("/api/schedules/classroom-free-day-summary")
                        .param("classroom", "A101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.classroom").value("A101"))
                .andExpect(jsonPath("$.busyDays").isArray())
                .andExpect(jsonPath("$.busyDays").isEmpty())
                .andExpect(jsonPath("$.freeDays.length()").value(7))
                .andExpect(jsonPath("$.freeDayCount").value(7))
                .andExpect(jsonPath("$.freeDays[0]").value("周一"))
                .andExpect(jsonPath("$.freeDays[6]").value("周日"));
    }

    @Test
    void testGetClassroomFreeDaySummary_PartialDays_CorrectBusyAndFree() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("物理", "张老师", "A101", "周一 10:00-12:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("化学", "李老师", "A101", "周三 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("英语", "王老师", "A101", "周五 14:00-16:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules/classroom-free-day-summary")
                        .param("classroom", "A101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.classroom").value("A101"))
                .andExpect(jsonPath("$.busyDays.length()").value(3))
                .andExpect(jsonPath("$.busyDays[0]").value("周一"))
                .andExpect(jsonPath("$.busyDays[1]").value("周三"))
                .andExpect(jsonPath("$.busyDays[2]").value("周五"))
                .andExpect(jsonPath("$.freeDays.length()").value(4))
                .andExpect(jsonPath("$.freeDays[0]").value("周二"))
                .andExpect(jsonPath("$.freeDays[1]").value("周四"))
                .andExpect(jsonPath("$.freeDays[2]").value("周六"))
                .andExpect(jsonPath("$.freeDays[3]").value("周日"))
                .andExpect(jsonPath("$.freeDayCount").value(4));
    }

    @Test
    void testGetClassroomFreeDaySummary_AllSevenDaysOccupied_NoFreeDays() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("周一", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("周二", "李老师", "A101", "周二 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("周三", "王老师", "A101", "周三 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("周四", "赵老师", "A101", "周四 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("周五", "钱老师", "A101", "周五 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("周六", "孙老师", "A101", "周六 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("周日", "周老师", "A101", "周日 08:00-10:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules/classroom-free-day-summary")
                        .param("classroom", "A101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.busyDays.length()").value(7))
                .andExpect(jsonPath("$.freeDays").isArray())
                .andExpect(jsonPath("$.freeDays").isEmpty())
                .andExpect(jsonPath("$.freeDayCount").value(0));
    }

    @Test
    void testGetClassroomFreeDaySummary_EmptyClassroom_Returns400() throws Exception {
        mockMvc.perform(get("/api/schedules/classroom-free-day-summary")
                        .param("classroom", ""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testGetClassroomFreeDaySummary_WhitespaceClassroom_Returns400() throws Exception {
        mockMvc.perform(get("/api/schedules/classroom-free-day-summary")
                        .param("classroom", "   "))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testGetClassroomFreeDaySummary_InvalidTimeSlotCourses_NotCounted() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("物理", "李老师", "A101", "周八 08:00-10:00"))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("生物", "王老师", "A101", "周四 09:00-11:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules/classroom-free-day-summary")
                        .param("classroom", "A101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.busyDays.length()").value(2))
                .andExpect(jsonPath("$.busyDays[0]").value("周一"))
                .andExpect(jsonPath("$.busyDays[1]").value("周四"))
                .andExpect(jsonPath("$.freeDayCount").value(5));
    }

    @Test
    void testGetClassroomFreeDaySummary_TrimmedClassroom_CorrectResult() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张老师", "A101", "周二 08:00-10:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules/classroom-free-day-summary")
                        .param("classroom", "  A101  "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.classroom").value("A101"))
                .andExpect(jsonPath("$.busyDays.length()").value(1))
                .andExpect(jsonPath("$.busyDays[0]").value("周二"))
                .andExpect(jsonPath("$.freeDayCount").value(6));
    }

    @Test
    void testGetClassroomFreeDaySummary_BusyDaysSorted_MondayToSundayOrder() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("周日", "张老师", "A101", "周日 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("周三", "李老师", "A101", "周三 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("周一", "王老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("周六", "赵老师", "A101", "周六 08:00-10:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules/classroom-free-day-summary")
                        .param("classroom", "A101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.busyDays.length()").value(4))
                .andExpect(jsonPath("$.busyDays[0]").value("周一"))
                .andExpect(jsonPath("$.busyDays[1]").value("周三"))
                .andExpect(jsonPath("$.busyDays[2]").value("周六"))
                .andExpect(jsonPath("$.busyDays[3]").value("周日"))
                .andExpect(jsonPath("$.freeDays[0]").value("周二"))
                .andExpect(jsonPath("$.freeDays[1]").value("周四"))
                .andExpect(jsonPath("$.freeDays[2]").value("周五"));
    }

    @Test
    void testGetClassroomFreeDaySummary_MultipleCoursesSameDay_CountedOnce() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("物理", "李老师", "A101", "周一 10:00-12:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("化学", "王老师", "A101", "周一 14:00-16:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules/classroom-free-day-summary")
                        .param("classroom", "A101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.busyDays.length()").value(1))
                .andExpect(jsonPath("$.busyDays[0]").value("周一"))
                .andExpect(jsonPath("$.freeDayCount").value(6));
    }

    @Test
    void testGetClassroomFreeDaySummary_SundayCourses_CountedAsLast() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("周六课", "张老师", "A101", "周六 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("周日课", "李老师", "A101", "周日 08:00-10:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules/classroom-free-day-summary")
                        .param("classroom", "A101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.busyDays.length()").value(2))
                .andExpect(jsonPath("$.busyDays[0]").value("周六"))
                .andExpect(jsonPath("$.busyDays[1]").value("周日"))
                .andExpect(jsonPath("$.freeDayCount").value(5));
    }

    @Test
    void testGetClassroomFreeDaySummary_OtherClassroomsCourses_NotIncluded() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("物理", "李老师", "B202", "周二 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("化学", "王老师", "C303", "周三 08:00-10:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules/classroom-free-day-summary")
                        .param("classroom", "A101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.busyDays.length()").value(1))
                .andExpect(jsonPath("$.busyDays[0]").value("周一"))
                .andExpect(jsonPath("$.freeDayCount").value(6));
    }

    @Test
    void testGetTeacherConsecutiveBusyDays_NoCourses_ReturnsZeroResult() throws Exception {
        mockMvc.perform(get("/api/schedules/teacher-consecutive-busy-days")
                        .param("teacherName", "张老师"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teacherName").value("张老师"))
                .andExpect(jsonPath("$.busyDays").isArray())
                .andExpect(jsonPath("$.busyDays").isEmpty())
                .andExpect(jsonPath("$.maxConsecutiveBusyDays").value(0))
                .andExpect(jsonPath("$.longestBusyStreak").isArray())
                .andExpect(jsonPath("$.longestBusyStreak").isEmpty());
    }

    @Test
    void testGetTeacherConsecutiveBusyDays_ThreeConsecutiveDays_CorrectStreak() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("物理", "张老师", "B202", "周二 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("化学", "张老师", "C303", "周三 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("英语", "张老师", "D404", "周五 14:00-16:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules/teacher-consecutive-busy-days")
                        .param("teacherName", "张老师"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teacherName").value("张老师"))
                .andExpect(jsonPath("$.busyDays.length()").value(4))
                .andExpect(jsonPath("$.busyDays[0]").value("周一"))
                .andExpect(jsonPath("$.busyDays[1]").value("周二"))
                .andExpect(jsonPath("$.busyDays[2]").value("周三"))
                .andExpect(jsonPath("$.busyDays[3]").value("周五"))
                .andExpect(jsonPath("$.maxConsecutiveBusyDays").value(3))
                .andExpect(jsonPath("$.longestBusyStreak.length()").value(3))
                .andExpect(jsonPath("$.longestBusyStreak[0]").value("周一"))
                .andExpect(jsonPath("$.longestBusyStreak[1]").value("周二"))
                .andExpect(jsonPath("$.longestBusyStreak[2]").value("周三"));
    }

    @Test
    void testGetTeacherConsecutiveBusyDays_TwoEqualStreaks_PicksEarlier() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("物理", "张老师", "B202", "周二 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("化学", "张老师", "C303", "周四 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("英语", "张老师", "D404", "周五 14:00-16:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules/teacher-consecutive-busy-days")
                        .param("teacherName", "张老师"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxConsecutiveBusyDays").value(2))
                .andExpect(jsonPath("$.longestBusyStreak[0]").value("周一"))
                .andExpect(jsonPath("$.longestBusyStreak[1]").value("周二"));
    }

    @Test
    void testGetTeacherConsecutiveBusyDays_EmptyTeacherName_Returns400() throws Exception {
        mockMvc.perform(get("/api/schedules/teacher-consecutive-busy-days")
                        .param("teacherName", ""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testGetTeacherConsecutiveBusyDays_SingleDay_ReturnsOne() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张老师", "A101", "周三 08:00-10:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules/teacher-consecutive-busy-days")
                        .param("teacherName", "张老师"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.busyDays.length()").value(1))
                .andExpect(jsonPath("$.busyDays[0]").value("周三"))
                .andExpect(jsonPath("$.maxConsecutiveBusyDays").value(1))
                .andExpect(jsonPath("$.longestBusyStreak.length()").value(1))
                .andExpect(jsonPath("$.longestBusyStreak[0]").value("周三"));
    }

    @Test
    void testGetTeacherConsecutiveBusyDays_OtherTeachersNotIncluded() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("物理", "李老师", "B202", "周二 08:00-10:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules/teacher-consecutive-busy-days")
                        .param("teacherName", "张老师"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.busyDays.length()").value(1))
                .andExpect(jsonPath("$.busyDays[0]").value("周一"))
                .andExpect(jsonPath("$.maxConsecutiveBusyDays").value(1))
                .andExpect(jsonPath("$.longestBusyStreak[0]").value("周一"));
    }

    @Test
    void testGetTeacherConsecutiveBusyDays_TrimmedTeacherName() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("数学", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("物理", "张老师", "B202", "周二 08:00-10:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules/teacher-consecutive-busy-days")
                        .param("teacherName", "  张老师  "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teacherName").value("张老师"))
                .andExpect(jsonPath("$.maxConsecutiveBusyDays").value(2))
                .andExpect(jsonPath("$.longestBusyStreak[0]").value("周一"))
                .andExpect(jsonPath("$.longestBusyStreak[1]").value("周二"));
    }

    @Test
    void testGetTeacherConsecutiveBusyDays_AllSevenDaysConsecutive() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("周一", "张老师", "A101", "周一 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("周二", "张老师", "A101", "周二 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("周三", "张老师", "A101", "周三 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("周四", "张老师", "A101", "周四 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("周五", "张老师", "A101", "周五 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("周六", "张老师", "A101", "周六 08:00-10:00"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("周日", "张老师", "A101", "周日 08:00-10:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedules/teacher-consecutive-busy-days")
                        .param("teacherName", "张老师"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.busyDays.length()").value(7))
                .andExpect(jsonPath("$.maxConsecutiveBusyDays").value(7))
                .andExpect(jsonPath("$.longestBusyStreak.length()").value(7));
    }
}
