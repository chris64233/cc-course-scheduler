package com.coursescheduler.controller;

import com.coursescheduler.dto.RepairPlanActionRequest;
import com.coursescheduler.dto.RepairPlanResponse;
import com.coursescheduler.dto.RepairPlanSubmitRequest;
import com.coursescheduler.dto.ReschedulePreCheckResponse;
import com.coursescheduler.service.RepairPlanService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 教室停用后的成组修复方案。
 */
@RestController
@RequestMapping("/api/repair-plans")
public class RepairPlanController {

    private final RepairPlanService repairPlanService;

    public RepairPlanController(RepairPlanService repairPlanService) {
        this.repairPlanService = repairPlanService;
    }

    /** 提交修复方案（按 bizKey 幂等；新建 201，重复提交 200） */
    @PostMapping
    public ResponseEntity<RepairPlanResponse> submit(
            @RequestBody(required = false) RepairPlanSubmitRequest request) {
        RepairPlanService.SubmitOutcome outcome = repairPlanService.submit(request);
        return new ResponseEntity<>(outcome.getResponse(),
                outcome.isReplayed() ? HttpStatus.OK : HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<RepairPlanResponse>> listPlans() {
        return ResponseEntity.ok(repairPlanService.listPlans());
    }

    @GetMapping("/{id}")
    public ResponseEntity<RepairPlanResponse> getPlan(@PathVariable Long id) {
        return ResponseEntity.ok(repairPlanService.getPlan(id));
    }

    /** 预检查：逐门给出无法安排的具体原因，含未覆盖课程 */
    @PostMapping("/{id}/pre-check")
    public ResponseEntity<ReschedulePreCheckResponse> preCheck(@PathVariable Long id) {
        return ResponseEntity.ok(repairPlanService.preCheck(id));
    }

    /** 原子确认：重新检查课程版本、教师时间与目标教室停用状态，一次事务应用全部变更 */
    @PostMapping("/{id}/confirm")
    public ResponseEntity<RepairPlanResponse> confirm(
            @PathVariable Long id,
            @RequestBody(required = false) RepairPlanActionRequest request) {
        return ResponseEntity.ok(repairPlanService.confirm(id, request));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<RepairPlanResponse> reject(
            @PathVariable Long id,
            @RequestBody(required = false) RepairPlanActionRequest request) {
        return ResponseEntity.ok(repairPlanService.reject(id, request));
    }
}
