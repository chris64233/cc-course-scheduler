package com.coursescheduler.dto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class AbstractFreeDaySummaryResponse {

    private final List<String> busyDays;
    private final List<String> freeDays;

    protected AbstractFreeDaySummaryResponse() {
        this.busyDays = Collections.emptyList();
        this.freeDays = Collections.emptyList();
    }

    protected AbstractFreeDaySummaryResponse(List<String> busyDays, List<String> freeDays) {
        this.busyDays = immutableCopy(busyDays);
        this.freeDays = immutableCopy(freeDays);
    }

    public List<String> getBusyDays() {
        return busyDays;
    }

    public List<String> getFreeDays() {
        return freeDays;
    }

    public int getFreeDayCount() {
        return freeDays.size();
    }

    protected static List<String> immutableCopy(List<String> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<>(source));
    }
}
