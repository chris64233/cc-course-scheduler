package com.coursescheduler.controller;

import com.coursescheduler.dto.CourseScheduleCreateRequest;
import com.coursescheduler.dto.RepairPlanSubmitRequest;
import com.coursescheduler.dto.ReschedulePlanItemRequest;
import com.coursescheduler.dto.RoomOutageAdjustRequest;
import com.coursescheduler.dto.RoomOutageRequest;
import com.coursescheduler.service.CourseScheduleService;
import com.coursescheduler.service.ReschedulePlanService;
import com.coursescheduler.service.RoomOutageService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RoomOutageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CourseScheduleService scheduleService;

    @Autowired
    private ReschedulePlanService reschedulePlanService;

    @Autowired
    private RoomOutageService roomOutageService;

    @BeforeEach
    void setUp() {
        scheduleService.resetForTesting();
        reschedulePlanService.resetForTesting();
        roomOutageService.resetForTesting();
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

    private RoomOutageRequest outageRequest(String eventNo, String classroom, String timeSlot) {
        RoomOutageRequest request = new RoomOutageRequest();
        request.setEventNo(eventNo);
        request.setClassroom(classroom);
        request.setTimeSlot(timeSlot);
        request.setReason("设备检修");
        request.setOperator("管理员");
        return request;
    }

    @Test
    void testRegister_FreezesTask_AndQueriesWork() throws Exception {
        Long s1 = addSchedule("数学", "张老师", "A101", "周三 10:00-12:00");

        mockMvc.perform(post("/api/room-outages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                outageRequest("EVT-1", "A101", "周三 09:00-12:30"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.eventNo").value("EVT-1"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.taskIds", hasSize(1)));

        // 幂等重复登记 -> 200
        mockMvc.perform(post("/api/room-outages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                outageRequest("EVT-1", "A101", "周三 09:00-12:30"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventNo").value("EVT-1"));

        // 同号异内容 -> 409
        mockMvc.perform(post("/api/room-outages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                outageRequest("EVT-1", "A101", "周四 09:00-12:30"))))
                .andExpect(status().isConflict());

        // 影响范围查询
        mockMvc.perform(get("/api/room-outages/tasks").param("eventNo", "EVT-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].status").value("PENDING"))
                .andExpect(jsonPath("$[0].affectedCount").value(1))
                .andExpect(jsonPath("$[0].affectedCourses[0].scheduleId").value(s1))
                .andExpect(jsonPath("$[0].affectedCourses[0].state").value("ACTIVE"));

        mockMvc.perform(get("/api/room-outages/by-event/EVT-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.classroom").value("A101"));
    }

    @Test
    void testNewScheduleIntoOutage_Returns409() throws Exception {
        addSchedule("数学", "张老师", "A101", "周一 10:00-12:00");
        mockMvc.perform(post("/api/room-outages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                outageRequest("EVT-1", "A101", "周三 09:00-12:30"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new com.coursescheduler.dto.CourseScheduleCreateRequest() {{
                                    setCourseName("物理");
                                    setTeacherName("李老师");
                                    setClassroom("A101");
                                    setTimeSlot("周三 10:00-11:00");
                                }})))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error", containsString("已临时停用")));
    }

    @Test
    void testRepairFlow_PreCheckConflictThenConfirm() throws Exception {
        Long s1 = addSchedule("数学", "张老师", "A101", "周三 10:00-12:00");

        String registerResp = mockMvc.perform(post("/api/room-outages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                outageRequest("EVT-1", "A101", "周三 09:00-12:30"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long taskId = objectMapper.readTree(registerResp).get("taskIds").get(0).asLong();

        // 先提交一个换教室的方案，随后该目标教室也被停用，预检给出 ROOM_OUTAGE 具体原因
        RepairPlanSubmitRequest badRequest = new RepairPlanSubmitRequest("REP-BAD", "教务员",
                Arrays.asList(new ReschedulePlanItemRequest(s1, "B202", "周三 10:00-12:00")));
        String badPlanResp = mockMvc.perform(post("/api/room-outages/tasks/" + taskId + "/repair-plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badRequest)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long badPlanId = objectMapper.readTree(badPlanResp).get("id").asLong();

        mockMvc.perform(post("/api/room-outages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                outageRequest("EVT-2", "B202", "周三 09:00-12:30"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/room-outages/repair-plans/" + badPlanId + "/pre-check"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canReschedule").value(false))
                .andExpect(jsonPath("$.conflicts[?(@.conflictType=='ROOM_OUTAGE')]").exists());

        // 缺少覆盖：空 items 的方案 400
        mockMvc.perform(post("/api/room-outages/tasks/" + taskId + "/repair-plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bizKey\":\"REP-EMPTY\",\"operator\":\"教务员\",\"items\":[]}"))
                .andExpect(status().isBadRequest());

        // 可行方案：换到未停用的 C303
        RepairPlanSubmitRequest goodRequest = new RepairPlanSubmitRequest("REP-OK", "教务员",
                Arrays.asList(new ReschedulePlanItemRequest(s1, "C303", "周三 10:00-12:00")));
        String goodPlanResp = mockMvc.perform(post("/api/room-outages/tasks/" + taskId + "/repair-plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(goodRequest)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long goodPlanId = objectMapper.readTree(goodPlanResp).get("id").asLong();

        mockMvc.perform(post("/api/room-outages/repair-plans/" + goodPlanId + "/pre-check"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canReschedule").value(true));

        mockMvc.perform(post("/api/room-outages/repair-plans/" + goodPlanId + "/confirm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.items[0].newClassroom").value("C303"));

        // 任务已修复
        mockMvc.perform(get("/api/room-outages/tasks/" + taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REPAIRED"))
                .andExpect(jsonPath("$.resolvedCount").value(1))
                .andExpect(jsonPath("$.affectedCourses[0].state").value("RESOLVED"));

        // 变更链：包含创建与修复两条记录，修复记录带有处理人员和业务号
        mockMvc.perform(get("/api/room-outages/courses/" + s1 + "/change-chain"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.operationType=='CREATE')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.operationType=='REPAIR_RESCHEDULE' && @.referenceNo=='REP-OK' && @.operator=='教务员')]")
                        .isNotEmpty());
    }

    @Test
    void testAdjustRange_InvalidatesPlan_AndCancelKeepsRepair() throws Exception {
        Long s1 = addSchedule("数学", "张老师", "A101", "周三 10:00-12:00");

        String registerResp = mockMvc.perform(post("/api/room-outages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                outageRequest("EVT-1", "A101", "周三 09:00-10:30"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode outage = objectMapper.readTree(registerResp);
        long taskId = outage.get("taskIds").get(0).asLong();

        RepairPlanSubmitRequest repairRequest = new RepairPlanSubmitRequest("REP-1", "教务员",
                Arrays.asList(new ReschedulePlanItemRequest(s1, "B202", "周三 10:00-12:00")));
        String planResp = mockMvc.perform(post("/api/room-outages/tasks/" + taskId + "/repair-plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(repairRequest)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long planId = objectMapper.readTree(planResp).get("id").asLong();

        // 扩大停用范围到覆盖修复目标
        RoomOutageAdjustRequest adjust = new RoomOutageAdjustRequest();
        adjust.setTimeSlot("周三 09:00-12:30");
        adjust.setOperator("管理员");
        mockMvc.perform(post("/api/room-outages/by-event/EVT-1/adjust")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(adjust)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timeSlot").value("周三 09:00-12:30"));

        // 旧方案确认失败：任务版本变化
        mockMvc.perform(post("/api/room-outages/repair-plans/" + planId + "/confirm"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.conflicts[0].conflictType").value("TASK_VERSION_CHANGED"));

        // 取消停用
        mockMvc.perform(post("/api/room-outages/by-event/EVT-1/cancel")
                        .param("operator", "管理员"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        // 取消后任务为已取消，方案无法确认
        mockMvc.perform(post("/api/room-outages/repair-plans/" + planId + "/confirm"))
                .andExpect(status().isConflict());

        // 事件与方案记录仍可查询
        mockMvc.perform(get("/api/room-outages/repair-plans/" + planId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventNo").value("EVT-1"));
    }
}
