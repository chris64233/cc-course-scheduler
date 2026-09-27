package com.coursescheduler.controller;

import com.coursescheduler.dto.ReschedulePlanResponse;
import com.coursescheduler.dto.ReschedulePlanSubmitRequest;
import com.coursescheduler.dto.ReschedulePreCheckResponse;
import com.coursescheduler.service.ReschedulePlanService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/reschedule-plans")
public class ReschedulePlanController {

    private final ReschedulePlanService reschedulePlanService;

    public ReschedulePlanController(ReschedulePlanService reschedulePlanService) {
        this.reschedulePlanService = reschedulePlanService;
    }

    @PostMapping
    public ResponseEntity<ReschedulePlanResponse> submit(
            @RequestBody(required = false) ReschedulePlanSubmitRequest request) {
        ReschedulePlanService.SubmitOutcome outcome = reschedulePlanService.submit(request);
        HttpStatus status = outcome.isReplayed() ? HttpStatus.OK : HttpStatus.CREATED;
        return new ResponseEntity<>(outcome.getResponse(), status);
    }

    @GetMapping
    public ResponseEntity<List<ReschedulePlanResponse>> listPlans() {
        return ResponseEntity.ok(reschedulePlanService.listPlans());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ReschedulePlanResponse> getPlan(@PathVariable Long id) {
        return ResponseEntity.ok(reschedulePlanService.getPlan(id));
    }

    @PostMapping("/{id}/pre-check")
    public ResponseEntity<ReschedulePreCheckResponse> preCheck(@PathVariable Long id) {
        return ResponseEntity.ok(reschedulePlanService.preCheck(id));
    }

    @PostMapping("/{id}/confirm")
    public ResponseEntity<ReschedulePlanResponse> confirm(@PathVariable Long id) {
        return ResponseEntity.ok(reschedulePlanService.confirm(id));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<ReschedulePlanResponse> reject(@PathVariable Long id) {
        return ResponseEntity.ok(reschedulePlanService.reject(id));
    }
}
