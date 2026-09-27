package com.coursescheduler.controller;

import com.coursescheduler.dto.CourseScheduleCreateRequest;
import com.coursescheduler.dto.RescheduleItemRequest;
import com.coursescheduler.dto.ReschedulePlanCreateRequest;
import com.coursescheduler.service.AuditLogService;
import com.coursescheduler.service.CourseScheduleService;
import com.coursescheduler.service.RescheduleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RescheduleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @Autowired
    private CourseScheduleService scheduleService;

    @Autowired
    private RescheduleService rescheduleService;

    @Autowired
    private AuditLogService auditLogService;

    @BeforeEach
    void setUp() {
        scheduleService.resetForTesting();
        rescheduleService.resetForTesting();
    }

    private Long seed(String courseName, String teacher, String classroom, String timeSlot) {
        CourseScheduleCreateRequest request = new CourseScheduleCreateRequest();
        request.setCourseName(courseName);
        request.setTeacherName(teacher);
        request.setClassroom(classroom);
        request.setTimeSlot(timeSlot);
        return scheduleService.addSchedule(request).getId();
    }

    private String createPlanJson(String businessId, Object... itemTriplets) throws Exception {
        List<RescheduleItemRequest> items = new java.util.ArrayList<>();
        for (int i = 0; i < itemTriplets.length; i += 3) {
            items.add(new RescheduleItemRequest(
                    ((Number) itemTriplets[i]).longValue(),
                    (String) itemTriplets[i + 1],
                    (String) itemTriplets[i + 2]));
        }
        ReschedulePlanCreateRequest request = new ReschedulePlanCreateRequest();
        request.setBusinessId(businessId);
        request.setItems(items);
        return objectMapper.writeValueAsString(request);
    }

    @Test
    void createPlan_thenSwapConfirm_endToEnd() throws Exception {
        seed("数学", "张老师", "A101", "周一 08:00-10:00");
        seed("物理", "李老师", "B202", "周一 10:00-12:00");

        // 提交互换方案。
        String response = mockMvc.perform(post("/api/reschedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPlanJson("SWAP-WEB-1",
                                1L, "B202", "周一 10:00-12:00",
                                2L, "A101", "周一 08:00-10:00")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.businessId").value("SWAP-WEB-1"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].originalClassroom").value("A101"))
                .andReturn().getResponse().getContentAsString();

        String planId = objectMapper.readTree(response).get("planId").asText();

        // 预检通过。
        mockMvc.perform(post("/api/reschedules/" + planId + "/pre-check"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.conflictCount").value(0));

        // 确认成功。
        mockMvc.perform(post("/api/reschedules/" + planId + "/confirm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applied").value(true))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.changes", hasSize(2)));

        // 课程实际已交换。
        mockMvc.perform(get("/api/schedules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].classroom").value("B202"))
                .andExpect(jsonPath("$[1].classroom").value("A101"));

        // 变更明细可查。
        mockMvc.perform(get("/api/reschedules/" + planId + "/changes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].beforeClassroom").value("A101"))
                .andExpect(jsonPath("$[0].afterClassroom").value("B202"));

        // 处理结果可查（也支持业务号查询）。
        mockMvc.perform(get("/api/reschedules/SWAP-WEB-1/result"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.applied").value(true));
    }

    @Test
    void createPlan_withExternalConflict_preCheckAndConfirmReturnDetails() throws Exception {
        seed("数学", "张老师", "A101", "周一 08:00-10:00");
        seed("化学", "王老师", "C303", "周三 08:00-10:00");

        mockMvc.perform(post("/api/reschedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPlanJson("CONFLICT-WEB-1", 1L, "C303", "周三 08:00-10:00")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.issues[0].issueType").value("CLASSROOM"))
                .andExpect(jsonPath("$.issues[0].source").value("EXTERNAL_COURSE"));

        // 确认时整份方案不生效（业务结果 200，applied=false，不抛异常）。
        mockMvc.perform(post("/api/reschedules/CONFLICT-WEB-1/confirm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applied").value(false))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.conflictCount").value(1));

        mockMvc.perform(get("/api/reschedules/CONFLICT-WEB-1/issues"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].courseName").value("化学"));
    }

    @Test
    void createPlan_duplicateReferences_returns400() throws Exception {
        seed("数学", "张老师", "A101", "周一 08:00-10:00");
        mockMvc.perform(post("/api/reschedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPlanJson("DUP-WEB-1",
                                1L, "B202", "周三 10:00-12:00",
                                1L, "C303", "周四 10:00-12:00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("重复引用")));
    }

    @Test
    void createPlan_sameBusinessIdDifferentContent_returns409() throws Exception {
        seed("数学", "张老师", "A101", "周一 08:00-10:00");
        mockMvc.perform(post("/api/reschedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPlanJson("IDEM-WEB-1", 1L, "B202", "周三 10:00-12:00")))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/reschedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPlanJson("IDEM-WEB-1", 1L, "C303", "周四 10:00-12:00")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error", containsString("内容不同")));
    }

    @Test
    void rejectThenConfirm_returns409() throws Exception {
        seed("数学", "张老师", "A101", "周一 08:00-10:00");
        mockMvc.perform(post("/api/reschedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPlanJson("REJ-WEB-1", 1L, "B202", "周三 10:00-12:00")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/reschedules/REJ-WEB-1/reject"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
        mockMvc.perform(post("/api/reschedules/REJ-WEB-1/confirm"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error", containsString("已拒绝")));
    }

    @Test
    void getUnknownPlan_returns404() throws Exception {
        mockMvc.perform(get("/api/reschedules/UNKNOWN"))
                .andExpect(status().isNotFound());
    }

    @Test
    void listPlans_canFilterByStatus() throws Exception {
        seed("数学", "张老师", "A101", "周一 08:00-10:00");
        mockMvc.perform(post("/api/reschedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPlanJson("LIST-WEB-1", 1L, "B202", "周三 10:00-12:00")))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/reschedules").param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
        mockMvc.perform(get("/api/reschedules").param("status", "CONFIRMED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }
}
