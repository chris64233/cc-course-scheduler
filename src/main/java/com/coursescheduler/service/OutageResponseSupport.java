package com.coursescheduler.service;

import com.coursescheduler.dto.AffectedCourseDTO;
import com.coursescheduler.dto.OutageRangeHistoryDTO;
import com.coursescheduler.dto.RepairTaskResponse;
import com.coursescheduler.dto.RoomOutageResponse;
import com.coursescheduler.model.AffectedCourse;
import com.coursescheduler.model.OutageRangeHistory;
import com.coursescheduler.model.RepairTask;
import com.coursescheduler.model.RoomOutage;

import java.util.ArrayList;
import java.util.List;

/**
 * 停用事件与修复任务的响应映射。
 */
public final class OutageResponseSupport {

    private OutageResponseSupport() {
    }

    public static RoomOutageResponse toOutageResponse(RoomOutage outage, RepairTask task) {
        RoomOutageResponse response = new RoomOutageResponse();
        response.setId(outage.getId());
        response.setEventNo(outage.getEventNo());
        response.setClassroom(outage.getClassroom());
        response.setReason(outage.getReason());
        response.setTimeSlots(new ArrayList<>(outage.getTimeSlots()));
        response.setStatus(outage.getStatus());
        response.setVersion(outage.getVersion());
        response.setCreatedBy(outage.getCreatedBy());
        response.setCreatedAt(outage.getCreatedAt());
        response.setUpdatedBy(outage.getUpdatedBy());
        response.setUpdatedAt(outage.getUpdatedAt());
        response.setCancelledBy(outage.getCancelledBy());
        response.setCancelledAt(outage.getCancelledAt());
        response.setRepairTaskId(task != null ? task.getId() : null);
        List<OutageRangeHistoryDTO> history = new ArrayList<>();
        for (OutageRangeHistory entry : outage.getRangeHistory()) {
            history.add(new OutageRangeHistoryDTO(
                    entry.getVersion(),
                    new ArrayList<>(entry.getTimeSlots()),
                    entry.getReason(),
                    entry.getChangedAt(),
                    entry.getChangedBy()));
        }
        response.setRangeHistory(history);
        return response;
    }

    public static RepairTaskResponse toTaskResponse(RepairTask task) {
        RepairTaskResponse response = new RepairTaskResponse();
        response.setId(task.getId());
        response.setOutageId(task.getOutageId());
        response.setEventNo(task.getEventNo());
        response.setStatus(task.getStatus());
        response.setAffectedCount(task.getAffectedCourses().size());
        int resolvedCount = 0;
        List<AffectedCourseDTO> courseDTOs = new ArrayList<>();
        for (AffectedCourse course : task.getAffectedCourses()) {
            if (course.isResolved()) {
                resolvedCount++;
            }
            courseDTOs.add(new AffectedCourseDTO(
                    course.getScheduleId(),
                    course.getCourseName(),
                    course.getTeacherName(),
                    course.getClassroom(),
                    course.getTimeSlot(),
                    course.getVersion(),
                    course.isResolved(),
                    course.getResolvedReason()));
        }
        response.setResolvedCount(resolvedCount);
        response.setAffectedCourses(courseDTOs);
        response.setCreatedAt(task.getCreatedAt());
        response.setResolvedAt(task.getResolvedAt());
        response.setConfirmedRepairPlanId(task.getConfirmedRepairPlanId());
        return response;
    }
}
