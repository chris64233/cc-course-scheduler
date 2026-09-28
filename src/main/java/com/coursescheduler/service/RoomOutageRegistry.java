package com.coursescheduler.service;

import com.coursescheduler.model.RoomOutage;
import com.coursescheduler.model.RoomOutageStatus;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 教室停用事件存储（主数据）。
 *
 * <p>所有访问必须在 {@link SchedulingLocks} 的保护下进行，
 * 与课程安排、修复任务/方案的读写互斥，保证并发场景下看到的停用范围一致。
 */
@Component
public class RoomOutageRegistry {

    private final Map<String, RoomOutage> outagesByEventNo = new LinkedHashMap<>();
    private long idGenerator = 1;

    public long nextId() {
        return idGenerator++;
    }

    public void put(RoomOutage outage) {
        outagesByEventNo.put(outage.getEventNo(), outage);
    }

    public RoomOutage getByEventNo(String eventNo) {
        return outagesByEventNo.get(eventNo);
    }

    public RoomOutage getById(Long id) {
        for (RoomOutage outage : outagesByEventNo.values()) {
            if (outage.getId().equals(id)) {
                return outage;
            }
        }
        return null;
    }

    public List<RoomOutage> listAll() {
        return new ArrayList<>(outagesByEventNo.values());
    }

    /** 当前生效中的停用事件（调用方只读）。 */
    public List<RoomOutage> activeOutages() {
        List<RoomOutage> active = new ArrayList<>();
        for (RoomOutage outage : outagesByEventNo.values()) {
            if (outage.getStatus() == RoomOutageStatus.ACTIVE) {
                active.add(outage);
            }
        }
        return active;
    }

    public void reset() {
        outagesByEventNo.clear();
        idGenerator = 1;
    }
}
