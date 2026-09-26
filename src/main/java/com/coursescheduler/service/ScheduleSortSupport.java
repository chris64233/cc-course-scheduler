package com.coursescheduler.service;

import com.coursescheduler.exception.InvalidSortParameterException;
import com.coursescheduler.model.CourseSchedule;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Stream;

public class ScheduleSortSupport {

    public enum SortField {
        COURSE_NAME("courseName", Comparator.comparing(CourseSchedule::getCourseName)),
        TEACHER_NAME("teacherName", Comparator.comparing(CourseSchedule::getTeacherName)),
        CLASSROOM("classroom", Comparator.comparing(CourseSchedule::getClassroom)),
        TIME_SLOT("timeSlot", Comparator.comparing(CourseSchedule::getTimeSlot));

        private final String paramName;
        private final Comparator<CourseSchedule> comparator;

        SortField(String paramName, Comparator<CourseSchedule> comparator) {
            this.paramName = paramName;
            this.comparator = comparator;
        }

        public String getParamName() {
            return paramName;
        }

        public Comparator<CourseSchedule> getComparator() {
            return comparator;
        }

        static SortField fromParamName(String name) {
            for (SortField f : values()) {
                if (f.paramName.equals(name)) {
                    return f;
                }
            }
            return null;
        }

        static Set<String> allowedParamNames() {
            Set<String> names = new LinkedHashSet<>();
            for (SortField f : values()) {
                names.add(f.paramName);
            }
            return names;
        }
    }

    public static class SortCondition {
        private static final Comparator<CourseSchedule> DEFAULT_COMPARATOR =
                Comparator.comparing(CourseSchedule::getId);

        private final SortField sortField;
        private final boolean ascending;

        SortCondition(SortField sortField, boolean ascending) {
            this.sortField = sortField;
            this.ascending = ascending;
        }

        public Comparator<CourseSchedule> comparator() {
            if (sortField == null) {
                return DEFAULT_COMPARATOR;
            }
            Comparator<CourseSchedule> c = sortField.getComparator();
            return ascending ? c : c.reversed();
        }

        public Stream<CourseSchedule> apply(Stream<CourseSchedule> stream) {
            return stream.sorted(comparator());
        }
    }

    private static final Set<String> ALLOWED_DIRECTIONS = new LinkedHashSet<>(
            java.util.Arrays.asList("asc", "desc")
    );

    private ScheduleSortSupport() {
    }

    public static SortCondition parse(String sortBy, String sortDirection) {
        SortField field = parseField(sortBy);
        boolean ascending = parseDirection(sortDirection);
        return new SortCondition(field, ascending);
    }

    private static SortField parseField(String sortBy) {
        if (!StringUtils.hasText(sortBy)) {
            return null;
        }
        SortField field = SortField.fromParamName(sortBy);
        if (field == null) {
            throw new InvalidSortParameterException(
                    "不支持的排序字段: " + sortBy + "。支持的字段有: " + SortField.allowedParamNames()
            );
        }
        return field;
    }

    private static boolean parseDirection(String sortDirection) {
        if (!StringUtils.hasText(sortDirection)) {
            return true;
        }
        String lower = sortDirection.toLowerCase();
        if ("asc".equals(lower)) {
            return true;
        }
        if ("desc".equals(lower)) {
            return false;
        }
        throw new InvalidSortParameterException(
                "不支持的排序方向: " + sortDirection + "。支持的方向有: " + ALLOWED_DIRECTIONS
        );
    }
}
