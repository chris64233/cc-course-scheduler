package com.coursescheduler.controller;

import com.coursescheduler.dto.RepairTaskResponse;
import com.coursescheduler.dto.RoomOutageAdjustRequest;
import com.coursescheduler.dto.RoomOutageCancelRequest;
import com.coursescheduler.dto.RoomOutageCreateRequest;
import com.coursescheduler.dto.RoomOutageResponse;
import com.coursescheduler.service.RoomOutageService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 教室临时停用与停用影响范围查询。
 */
@RestController
@RequestMapping("/api/room-outages")
public class RoomOutageController {

    private final RoomOutageService outageService;

    public RoomOutageController(RoomOutageService outageService) {
        this.outageService = outageService;
    }

    /** 停用生效并冻结修复任务（按外部事件号幂等；新建 201，重复提交 200） */
    @PostMapping
    public ResponseEntity<RoomOutageResponse> createOutage(
            @RequestBody(required = false) RoomOutageCreateRequest request) {
        RoomOutageService.CreateOutcome outcome = outageService.createOutage(request);
        return new ResponseEntity<>(outcome.getResponse(),
                outcome.isReplayed() ? HttpStatus.OK : HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<RoomOutageResponse>> listOutages() {
        return ResponseEntity.ok(outageService.listOutages());
    }

    @GetMapping("/{id}")
    public ResponseEntity<RoomOutageResponse> getOutage(@PathVariable Long id) {
        return ResponseEntity.ok(outageService.getOutage(id));
    }

    /** 调整停用范围/原因（携带 expectedVersion 做乐观并发控制） */
    @PutMapping("/{id}")
    public ResponseEntity<RoomOutageResponse> adjustOutage(
            @PathVariable Long id,
            @RequestBody(required = false) RoomOutageAdjustRequest request) {
        return ResponseEntity.ok(outageService.adjustOutage(id, request));
    }

    /** 取消停用；已完成的修复不自动回退 */
    @PostMapping("/{id}/cancel")
    public ResponseEntity<RoomOutageResponse> cancelOutage(
            @PathVariable Long id,
            @RequestBody(required = false) RoomOutageCancelRequest request) {
        return ResponseEntity.ok(outageService.cancelOutage(id, request));
    }

    /** 查询停用冻结的修复任务（受影响课程范围与修复状态） */
    @GetMapping("/repair-tasks")
    public ResponseEntity<List<RepairTaskResponse>> listRepairTasks() {
        return ResponseEntity.ok(outageService.listRepairTasks());
    }

    @GetMapping("/repair-tasks/{taskId}")
    public ResponseEntity<RepairTaskResponse> getRepairTask(@PathVariable Long taskId) {
        return ResponseEntity.ok(outageService.getRepairTask(taskId));
    }
}
