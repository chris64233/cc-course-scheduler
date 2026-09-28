package com.coursescheduler.controller;

import com.coursescheduler.dto.AuditLogResponse;
import com.coursescheduler.dto.RepairPlanResponse;
import com.coursescheduler.dto.RepairPlanSubmitRequest;
import com.coursescheduler.dto.RepairTaskResponse;
import com.coursescheduler.dto.ReschedulePreCheckResponse;
import com.coursescheduler.dto.RoomOutageAdjustRequest;
import com.coursescheduler.dto.RoomOutageRequest;
import com.coursescheduler.dto.RoomOutageResponse;
import com.coursescheduler.service.AuditLogService;
import com.coursescheduler.service.RoomOutageService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 教室临时停用与成组修复排课接口。
 */
@RestController
@RequestMapping("/api/room-outages")
public class RoomOutageController {

    private final RoomOutageService outageService;
    private final AuditLogService auditLogService;

    public RoomOutageController(RoomOutageService outageService, AuditLogService auditLogService) {
        this.outageService = outageService;
        this.auditLogService = auditLogService;
    }

    @PostMapping
    public ResponseEntity<RoomOutageResponse> register(
            @RequestBody(required = false) RoomOutageRequest request) {
        RoomOutageService.RegisterOutcome outcome = outageService.register(request);
        return new ResponseEntity<>(outcome.getResponse(),
                outcome.isReplayed() ? HttpStatus.OK : HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<RoomOutageResponse>> listOutages() {
        return ResponseEntity.ok(outageService.listOutages());
    }

    @GetMapping("/by-event/{eventNo}")
    public ResponseEntity<RoomOutageResponse> getByEventNo(@PathVariable String eventNo) {
        return ResponseEntity.ok(outageService.getOutageByEventNo(eventNo));
    }

    @GetMapping("/{id}")
    public ResponseEntity<RoomOutageResponse> getOutage(@PathVariable Long id) {
        return ResponseEntity.ok(outageService.getOutage(id));
    }

    @PostMapping("/by-event/{eventNo}/adjust")
    public ResponseEntity<RoomOutageResponse> adjust(
            @PathVariable String eventNo,
            @RequestBody(required = false) RoomOutageAdjustRequest request) {
        return ResponseEntity.ok(outageService.adjust(eventNo, request));
    }

    @PostMapping("/by-event/{eventNo}/cancel")
    public ResponseEntity<RoomOutageResponse> cancel(
            @PathVariable String eventNo,
            @RequestParam(required = false) String operator,
            @RequestBody(required = false) RoomOutageRequest body) {
        String op = operator != null && !operator.trim().isEmpty()
                ? operator
                : (body != null ? body.getOperator() : null);
        return ResponseEntity.ok(outageService.cancel(eventNo, op));
    }

    // ----- 修复任务：停用影响范围 -----

    @GetMapping("/tasks")
    public ResponseEntity<List<RepairTaskResponse>> listTasks(
            @RequestParam(required = false) String eventNo) {
        return ResponseEntity.ok(outageService.listTasks(eventNo));
    }

    @GetMapping("/tasks/{taskId}")
    public ResponseEntity<RepairTaskResponse> getTask(@PathVariable Long taskId) {
        return ResponseEntity.ok(outageService.getTask(taskId));
    }

    // ----- 修复方案 -----

    @PostMapping("/tasks/{taskId}/repair-plans")
    public ResponseEntity<RepairPlanResponse> submitRepairPlan(
            @PathVariable Long taskId,
            @RequestBody(required = false) RepairPlanSubmitRequest request) {
        RoomOutageService.SubmitPlanOutcome outcome = outageService.submitRepairPlan(taskId, request);
        return new ResponseEntity<>(outcome.getResponse(),
                outcome.isReplayed() ? HttpStatus.OK : HttpStatus.CREATED);
    }

    @GetMapping("/repair-plans")
    public ResponseEntity<List<RepairPlanResponse>> listRepairPlans(
            @RequestParam(required = false) String eventNo) {
        return ResponseEntity.ok(outageService.listRepairPlans(eventNo));
    }

    @GetMapping("/repair-plans/{planId}")
    public ResponseEntity<RepairPlanResponse> getRepairPlan(@PathVariable Long planId) {
        return ResponseEntity.ok(outageService.getRepairPlan(planId));
    }

    @PostMapping("/repair-plans/{planId}/pre-check")
    public ResponseEntity<ReschedulePreCheckResponse> preCheck(@PathVariable Long planId) {
        return ResponseEntity.ok(outageService.preCheckRepairPlan(planId));
    }

    @PostMapping("/repair-plans/{planId}/confirm")
    public ResponseEntity<RepairPlanResponse> confirm(@PathVariable Long planId) {
        return ResponseEntity.ok(outageService.confirmRepairPlan(planId));
    }

    @PostMapping("/repair-plans/{planId}/reject")
    public ResponseEntity<RepairPlanResponse> reject(@PathVariable Long planId) {
        return ResponseEntity.ok(outageService.rejectRepairPlan(planId));
    }

    // ----- 课程变更链 -----

    @GetMapping("/courses/{scheduleId}/change-chain")
    public ResponseEntity<List<AuditLogResponse>> courseChangeChain(@PathVariable Long scheduleId) {
        return ResponseEntity.ok(auditLogService.getCourseChangeChain(scheduleId));
    }
}
