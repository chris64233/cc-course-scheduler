package com.coursescheduler.service;

import com.coursescheduler.model.RepairTask;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 停用修复任务存储。
 *
 * <p>所有访问必须在 {@link SchedulingLocks} 的保护下进行。
 */
@Component
public class RepairTaskRegistry {

    private final Map<Long, RepairTask> tasksById = new LinkedHashMap<>();
    private long idGenerator = 1;

    public long nextId() {
        return idGenerator++;
    }

    public void put(RepairTask task) {
        tasksById.put(task.getId(), task);
    }

    public RepairTask getById(Long id) {
        return tasksById.get(id);
    }

    public List<RepairTask> listAll() {
        return new ArrayList<>(tasksById.values());
    }

    public void reset() {
        tasksById.clear();
        idGenerator = 1;
    }
}
