package com.coursescheduler.service;

import com.coursescheduler.dto.ClassroomBatchPreCheckItemRequest;
import com.coursescheduler.dto.ClassroomBatchPreCheckRequest;
import com.coursescheduler.dto.TeacherBatchPreCheckItemRequest;
import com.coursescheduler.dto.TeacherBatchPreCheckRequest;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BatchDisplayContextSupport {

    private BatchDisplayContextSupport() {
    }

    public static Map<Integer, ConflictTargetDetailSupport.ItemDisplayInfo> buildTeacherBatchDisplayContext(
            TeacherBatchPreCheckRequest request) {
        if (request == null || request.getItems() == null) {
            return Collections.emptyMap();
        }

        Map<Integer, ConflictTargetDetailSupport.ItemDisplayInfo> context = new LinkedHashMap<>();
        List<TeacherBatchPreCheckItemRequest> items = request.getItems();
        for (int i = 0; i < items.size(); i++) {
            TeacherBatchPreCheckItemRequest item = items.get(i);
            if (item == null) {
                continue;
            }
            context.put(i, new ConflictTargetDetailSupport.ItemDisplayInfo(
                    item.getCourseName(),
                    request.getTeacherName(),
                    item.getClassroom(),
                    item.getTimeSlot()
            ));
        }
        return context;
    }

    public static Map<Integer, ConflictTargetDetailSupport.ItemDisplayInfo> buildClassroomBatchDisplayContext(
            ClassroomBatchPreCheckRequest request) {
        if (request == null || request.getItems() == null) {
            return Collections.emptyMap();
        }

        Map<Integer, ConflictTargetDetailSupport.ItemDisplayInfo> context = new LinkedHashMap<>();
        List<ClassroomBatchPreCheckItemRequest> items = request.getItems();
        for (int i = 0; i < items.size(); i++) {
            ClassroomBatchPreCheckItemRequest item = items.get(i);
            if (item == null) {
                continue;
            }
            context.put(i, new ConflictTargetDetailSupport.ItemDisplayInfo(
                    item.getCourseName(),
                    item.getTeacherName(),
                    request.getClassroom(),
                    item.getTimeSlot()
            ));
        }
        return context;
    }
}
