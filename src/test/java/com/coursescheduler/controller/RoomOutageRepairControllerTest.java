package com.coursescheduler.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.coursescheduler.dto.CourseScheduleCreateRequest;
import com.coursescheduler.dto.RepairPlanActionRequest;
import com.coursescheduler.dto.RepairPlanSubmitRequest;
import com.coursescheduler.dto.ReschedulePlanItemRequest;
import com.coursescheduler.dto.RoomOutageCancelRequest;
import com.coursescheduler.dto.RoomOutageCreateRequest;
import com.coursescheduler.service.CourseScheduleService;
import com.coursescheduler.service.RepairPlanService;
import com.coursescheduler.service.ReschedulePlanService;
import com.coursescheduler.service.RoomOutageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RoomOutageRepairControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private CourseScheduleService scheduleService;
    @Autowired
    private RoomOutageService outageService;
    @Autowired
    private RepairPlanService repairPlanService;
    @Autowired
    private ReschedulePlanService reschedulePlanService;

    @BeforeEach
    void setUp() {
        scheduleService.resetForTesting();
        reschedulePlanService.resetForTesting();
        repairPlanService.resetForTesting();
        outageService.resetForTesting();
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

    private RoomOutageCreateRequest outage(String eventNo, String classroom, String reason, String slot) {
        RoomOutageCreateRequest request = new RoomOutageCreateRequest();
        request.setEventNo(eventNo);
        request.setClassroom(classroom);
        request.setReason(reason);
        request.setTimeSlots(Collections.singletonList(slot));
        request.setOperator("管理员");
        return request;
    }

    private String postJson(String url, Object body) throws Exception {
        return mockMvc.perform(post(url)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void testFullOutageRepairFlow_FreezeToRepairToQueries() throws Exception {
        Long hitId = addSchedule("数学", "张老师", "A101", "周三 10:00-12:00");
        addSchedule("化学", "王老师", "A101", "周四 10:00-12:00");

        // 1. 停用生效：201，冻结 1 门冲突课程
        String created = postJson("/api/room-outages", outage("EVT-1", "A101", "设备检修", "周三 09:00-12:00"));
        Long outageId = objectMapper.readTree(created).get("id").asLong();
        Long taskId = objectMapper.readTree(created).get("repairTaskId").asLong();
        assertEquals(Long.valueOf(1L), outageId);

        // 2. 重复提交幂等 200；同号异内容 409
        mockMvc.perform(post("/api/room-outages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(outage("EVT-1", "A101", "设备检修", "周三 09:00-12:00"))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/room-outages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(outage("EVT-1", "A101", "突发停电", "周三 09:00-12:00"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error", containsString("EVT-1")));

        // 3. 停用影响范围查询
        mockMvc.perform(get("/api/room-outages/repair-tasks/" + taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.affectedCount").value(1))
                .andExpect(jsonPath("$.affectedCourses[0].scheduleId").value(hitId));

        // 4. 后来新增课程不得排入停用时段（取窗内不与既有课重叠的时段，确保命中停用而非教室冲突）
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                addScheduleReq("物理", "李老师", "A101", "周三 09:00-10:00"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error", containsString("EVT-1")));

        // 5. 覆盖不全的修复方案：409 + missingScheduleIds
        RepairPlanSubmitRequest bad = new RepairPlanSubmitRequest();
        bad.setBizKey("FIX-BAD");
        bad.setTaskId(taskId);
        // 另一门不在任务中的课程，制造越界 + 缺覆盖
        Long otherId = addSchedule("英语", "赵老师", "C303", "周五 10:00-12:00");
        bad.setItems(Collections.singletonList(new ReschedulePlanItemRequest(otherId, "D404", "周五 14:00-16:00")));
        bad.setOperator("教务处");
        mockMvc.perform(post("/api/repair-plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bad)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.missingScheduleIds[0]").value(hitId))
                .andExpect(jsonPath("$.extraScheduleIds[0]").value(otherId));

        // 6. 完整修复方案：提交 201、预检查通过、确认成功
        RepairPlanSubmitRequest good = new RepairPlanSubmitRequest();
        good.setBizKey("FIX-1");
        good.setTaskId(taskId);
        good.setItems(Collections.singletonList(new ReschedulePlanItemRequest(hitId, "B202", "周四 08:00-10:00")));
        good.setOperator("教务处");
        String planJson = postJson("/api/repair-plans", good);
        Long planId = objectMapper.readTree(planJson).get("id").asLong();

        mockMvc.perform(post("/api/repair-plans/" + planId + "/pre-check"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canReschedule").value(true))
                .andExpect(jsonPath("$.conflictCount").value(0));

        RepairPlanActionRequest confirm = new RepairPlanActionRequest();
        confirm.setOperator("李教务");
        mockMvc.perform(post("/api/repair-plans/" + planId + "/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(confirm)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.processedBy").value("李教务"));

        // 7. 课程已调整；任务完成
        mockMvc.perform(get("/api/schedules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id==" + hitId + ")].classroom").value(org.hamcrest.Matchers.contains("B202")));
        mockMvc.perform(get("/api/room-outages/repair-tasks/" + taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.confirmedRepairPlanId").value(planId));

        // 8. 课程变更链查询：含停用冻结与修复确认
        mockMvc.perform(get("/api/schedules/" + hitId + "/change-chain"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events", hasSize(org.hamcrest.Matchers.greaterThanOrEqualTo(2))))
                .andExpect(jsonPath("$.events[?(@.operationType=='REPAIR_CONFIRM')].refNo")
                        .value(org.hamcrest.Matchers.contains("FIX-1")))
                .andExpect(jsonPath("$.events[?(@.operationType=='REPAIR_CONFIRM')].operator")
                        .value(org.hamcrest.Matchers.contains("李教务")));

        // 9. 取消停用：已完成的修复保留
        RoomOutageCancelRequest cancel = new RoomOutageCancelRequest();
        cancel.setOperator("管理员");
        mockMvc.perform(post("/api/room-outages/" + outageId + "/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cancel)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        mockMvc.perform(get("/api/room-outages/repair-tasks/" + taskId))
                .andExpect(jsonPath("$.status").value("RESOLVED"));
    }

    @Test
    void testRepairConfirm_Conflict_Returns409WithDetailsAndAppliesNothing() throws Exception {
        Long hitId = addSchedule("数学", "张老师", "A101", "周三 10:00-12:00");
        // 张老师目标时段有课
        addSchedule("英语", "张老师", "X999", "周四 08:00-10:00");
        String outageJson = postJson("/api/room-outages",
                outage("EVT-1", "A101", "设备检修", "周三 10:00-12:00"));
        Long taskId = objectMapper.readTree(outageJson).get("repairTaskId").asLong();

        RepairPlanSubmitRequest request = new RepairPlanSubmitRequest();
        request.setBizKey("FIX-1");
        request.setTaskId(taskId);
        request.setItems(Collections.singletonList(new ReschedulePlanItemRequest(hitId, "B202", "周四 08:00-10:00")));
        Long planId = objectMapper.readTree(postJson("/api/repair-plans", request)).get("id").asLong();

        mockMvc.perform(post("/api/repair-plans/" + planId + "/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.conflicts[0].conflictType").value("TEACHER"));

        // 整份修复未生效
        mockMvc.perform(get("/api/schedules"))
                .andExpect(jsonPath("$[?(@.id==" + hitId + ")].classroom").value(org.hamcrest.Matchers.contains("A101")));
        mockMvc.perform(get("/api/repair-plans/" + planId))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    private CourseScheduleCreateRequest addScheduleReq(String courseName, String teacherName,
                                                       String classroom, String timeSlot) {
        CourseScheduleCreateRequest request = new CourseScheduleCreateRequest();
        request.setCourseName(courseName);
        request.setTeacherName(teacherName);
        request.setClassroom(classroom);
        request.setTimeSlot(timeSlot);
        return request;
    }
}
