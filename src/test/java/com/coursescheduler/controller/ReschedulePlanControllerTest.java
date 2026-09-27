package com.coursescheduler.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.coursescheduler.dto.CourseScheduleCreateRequest;
import com.coursescheduler.dto.ReschedulePlanItemRequest;
import com.coursescheduler.dto.ReschedulePlanSubmitRequest;
import com.coursescheduler.service.CourseScheduleService;
import com.coursescheduler.service.ReschedulePlanService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.Collections;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ReschedulePlanControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CourseScheduleService scheduleService;

    @Autowired
    private ReschedulePlanService reschedulePlanService;

    @BeforeEach
    void setUp() {
        scheduleService.resetForTesting();
        reschedulePlanService.resetForTesting();
    }

    private Long addSchedule(String courseName, String teacherName, String classroom, String timeSlot) throws Exception {
        CourseScheduleCreateRequest request = new CourseScheduleCreateRequest();
        request.setCourseName(courseName);
        request.setTeacherName(teacherName);
        request.setClassroom(classroom);
        request.setTimeSlot(timeSlot);
        String response = mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private ReschedulePlanSubmitRequest submitRequest(String bizKey, ReschedulePlanItemRequest... items) {
        return new ReschedulePlanSubmitRequest(bizKey, Arrays.asList(items));
    }

    private ReschedulePlanItemRequest item(Long scheduleId, String newClassroom, String newTimeSlot) {
        return new ReschedulePlanItemRequest(scheduleId, newClassroom, newTimeSlot);
    }

    @Test
    void testSubmit_Success_Returns201WithSnapshot() throws Exception {
        Long id = addSchedule("数学", "张老师", "A101", "周一 08:00-10:00");

        mockMvc.perform(post("/api/reschedule-plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                submitRequest("BIZ-1", item(id, "C303", "周三 10:00-12:00")))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.bizKey").value("BIZ-1"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].scheduleId").value(id))
                .andExpect(jsonPath("$.items[0].originalClassroom").value("A101"))
                .andExpect(jsonPath("$.items[0].originalTimeSlot").value("周一 08:00-10:00"))
                .andExpect(jsonPath("$.items[0].newClassroom").value("C303"))
                .andExpect(jsonPath("$.items[0].newTimeSlot").value("周三 10:00-12:00"));
    }

    @Test
    void testSubmit_IdempotentReplay_Returns200WithFirstResult() throws Exception {
        Long id = addSchedule("数学", "张老师", "A101", "周一 08:00-10:00");
        ReschedulePlanSubmitRequest request = submitRequest("BIZ-1", item(id, "C303", "周三 10:00-12:00"));

        mockMvc.perform(post("/api/reschedule-plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1));

        mockMvc.perform(post("/api/reschedule-plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.bizKey").value("BIZ-1"));
    }

    @Test
    void testSubmit_SameBizKeyDifferentContent_Returns409() throws Exception {
        Long id = addSchedule("数学", "张老师", "A101", "周一 08:00-10:00");

        mockMvc.perform(post("/api/reschedule-plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                submitRequest("BIZ-1", item(id, "C303", "周三 10:00-12:00")))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/reschedule-plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                submitRequest("BIZ-1", item(id, "D404", "周四 08:00-10:00")))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error", containsString("业务号")));
    }

    @Test
    void testSubmit_MissingSchedule_Returns404() throws Exception {
        mockMvc.perform(post("/api/reschedule-plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                submitRequest("BIZ-1", item(999L, "C303", "周三 10:00-12:00")))))
                .andExpect(status().isNotFound());
    }

    @Test
    void testSubmit_DuplicateScheduleIds_Returns400() throws Exception {
        Long id = addSchedule("数学", "张老师", "A101", "周一 08:00-10:00");

        mockMvc.perform(post("/api/reschedule-plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                submitRequest("BIZ-1",
                                        item(id, "C303", "周三 10:00-12:00"),
                                        item(id, "D404", "周四 08:00-10:00")))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testPreCheck_SwapCourses_CanReschedule() throws Exception {
        Long id1 = addSchedule("数学", "张老师", "A101", "周一 08:00-10:00");
        Long id2 = addSchedule("物理", "李老师", "B202", "周二 14:00-16:00");

        mockMvc.perform(post("/api/reschedule-plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(submitRequest("BIZ-SWAP",
                                item(id1, "B202", "周二 14:00-16:00"),
                                item(id2, "A101", "周一 08:00-10:00")))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/reschedule-plans/1/pre-check"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canReschedule").value(true))
                .andExpect(jsonPath("$.conflictCount").value(0));
    }

    @Test
    void testPreCheck_ConflictWithOutsideCourse_ReturnsDetails() throws Exception {
        Long id1 = addSchedule("数学", "张老师", "A101", "周一 08:00-10:00");
        addSchedule("英语", "张老师", "B202", "周三 10:00-12:00");

        mockMvc.perform(post("/api/reschedule-plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                submitRequest("BIZ-1", item(id1, "C303", "周三 10:00-12:00")))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/reschedule-plans/1/pre-check"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canReschedule").value(false))
                .andExpect(jsonPath("$.conflictCount").value(1))
                .andExpect(jsonPath("$.conflicts[0].conflictType").value("TEACHER"));
    }

    @Test
    void testConfirm_Success_AppliesChangesAndQueryShowsDetails() throws Exception {
        Long id = addSchedule("数学", "张老师", "A101", "周一 08:00-10:00");

        mockMvc.perform(post("/api/reschedule-plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                submitRequest("BIZ-1", item(id, "C303", "周三 10:00-12:00")))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/reschedule-plans/1/confirm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.processedAt").exists());

        mockMvc.perform(get("/api/schedules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].classroom").value("C303"))
                .andExpect(jsonPath("$[0].timeSlot").value("周三 10:00-12:00"));

        mockMvc.perform(get("/api/reschedule-plans/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.items[0].originalClassroom").value("A101"))
                .andExpect(jsonPath("$.items[0].newClassroom").value("C303"));

        // 审计记录包含调整前后内容
        mockMvc.perform(get("/api/schedules/audit-logs")
                        .param("operationType", "RESCHEDULE")
                        .param("success", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].previousClassroom").value("A101"))
                .andExpect(jsonPath("$[0].previousTimeSlot").value("周一 08:00-10:00"))
                .andExpect(jsonPath("$[0].classroom").value("C303"))
                .andExpect(jsonPath("$[0].timeSlot").value("周三 10:00-12:00"));
    }

    @Test
    void testConfirm_Twice_Returns409() throws Exception {
        Long id = addSchedule("数学", "张老师", "A101", "周一 08:00-10:00");

        mockMvc.perform(post("/api/reschedule-plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                submitRequest("BIZ-1", item(id, "C303", "周三 10:00-12:00")))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/reschedule-plans/1/confirm"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/reschedule-plans/1/confirm"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error", containsString("不可再次")));
    }

    @Test
    void testConfirm_WithConflict_Returns409WithDetails() throws Exception {
        Long id1 = addSchedule("数学", "张老师", "A101", "周一 08:00-10:00");
        addSchedule("英语", "张老师", "B202", "周三 10:00-12:00");

        mockMvc.perform(post("/api/reschedule-plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                submitRequest("BIZ-1", item(id1, "C303", "周三 10:00-12:00")))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/reschedule-plans/1/confirm"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.conflicts", hasSize(1)))
                .andExpect(jsonPath("$.conflicts[0].conflictType").value("TEACHER"));

        // 整份方案未生效
        mockMvc.perform(get("/api/schedules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].classroom").value("A101"));
    }

    @Test
    void testReject_Success_ThenConfirmReturns409() throws Exception {
        Long id = addSchedule("数学", "张老师", "A101", "周一 08:00-10:00");

        mockMvc.perform(post("/api/reschedule-plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                submitRequest("BIZ-1", item(id, "C303", "周三 10:00-12:00")))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/reschedule-plans/1/reject"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        mockMvc.perform(post("/api/reschedule-plans/1/confirm"))
                .andExpect(status().isConflict());
    }

    @Test
    void testGetPlan_NotFound_Returns404() throws Exception {
        mockMvc.perform(get("/api/reschedule-plans/42"))
                .andExpect(status().isNotFound());
    }

    @Test
    void testListPlans_ReturnsAll() throws Exception {
        Long id = addSchedule("数学", "张老师", "A101", "周一 08:00-10:00");

        mockMvc.perform(post("/api/reschedule-plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                submitRequest("BIZ-1", item(id, "C303", "周三 10:00-12:00")))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/reschedule-plans"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].bizKey").value("BIZ-1"));
    }
}
