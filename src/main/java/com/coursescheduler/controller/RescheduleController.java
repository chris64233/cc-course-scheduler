package com.coursescheduler.controller;

import com.coursescheduler.dto.ReschedulePlanCreateRequest;
import com.coursescheduler.dto.ReschedulePlanResponse;
import com.coursescheduler.dto.ReschedulePreCheckResponse;
import com.coursescheduler.dto.RescheduleResultResponse;
import com.coursescheduler.model.ReschedulePlanStatus;
import com.coursescheduler.service.RescheduleService;
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
 * 多门课程原子调课接口。
 *
 * <p>方案标识既可以是系统生成的 planId（RP- 前缀），也可以是客户端提交的业务号 businessId。
 */
@RestController
@RequestMapping("/api/reschedules")
public class RescheduleController {

    private final RescheduleService rescheduleService;

    public RescheduleController(RescheduleService rescheduleService) {
        this.rescheduleService = rescheduleService;
    }

    /** 提交调课方案（业务号幂等：相同业务号+相同方案返回首次结果，内容不同返回 409）。 */
    @PostMapping
    public ResponseEntity<ReschedulePlanResponse> createPlan(
            @RequestBody(required = false) ReschedulePlanCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(rescheduleService.createPlan(request));
    }

    /** 查询方案列表，可按状态过滤。 */
    @GetMapping
    public ResponseEntity<List<ReschedulePlanResponse>> listPlans(
            @RequestParam(required = false) ReschedulePlanStatus status) {
        return ResponseEntity.ok(rescheduleService.listPlans(status));
    }

    /** 查询调课方案详情（含原始快照、目标排课、最近冲突、确认后变更明细）。 */
    @GetMapping("/{key}")
    public ResponseEntity<ReschedulePlanResponse> getPlan(@PathVariable String key) {
        return ResponseEntity.ok(rescheduleService.getPlan(key));
    }

    /** 预检查整份方案，返回整份方案的冲突明细。 */
    @PostMapping("/{key}/pre-check")
    public ResponseEntity<ReschedulePreCheckResponse> preCheck(@PathVariable String key) {
        return ResponseEntity.ok(rescheduleService.preCheck(key));
    }

    /** 确认调课：一次性应用全部变更；任一课程被修改/删除/目标资源被占用则整份不生效。 */
    @PostMapping("/{key}/confirm")
    public ResponseEntity<RescheduleResultResponse> confirm(@PathVariable String key) {
        return ResponseEntity.ok(rescheduleService.confirm(key));
    }

    /** 拒绝调课方案（终态，之后不可再处理）。 */
    @PostMapping("/{key}/reject")
    public ResponseEntity<RescheduleResultResponse> reject(@PathVariable String key) {
        return ResponseEntity.ok(rescheduleService.reject(key));
    }

    /** 查询方案最近一次处理结果。 */
    @GetMapping("/{key}/result")
    public ResponseEntity<RescheduleResultResponse> getResult(@PathVariable String key) {
        return ResponseEntity.ok(rescheduleService.getResult(key));
    }

    /** 查询方案冲突明细。 */
    @GetMapping("/{key}/issues")
    public ResponseEntity<List<com.coursescheduler.dto.RescheduleIssueDTO>> getIssues(@PathVariable String key) {
        return ResponseEntity.ok(rescheduleService.getIssues(key));
    }

    /** 查询确认后每条课程调整前后的变更明细。 */
    @GetMapping("/{key}/changes")
    public ResponseEntity<List<com.coursescheduler.dto.RescheduleChangeDTO>> getChanges(@PathVariable String key) {
        return ResponseEntity.ok(rescheduleService.getChanges(key));
    }
}
