package com.coursescheduler.util;

import com.coursescheduler.exception.InvalidTimeSlotException;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TimeSlotUtils {

    private static final Pattern WEEKDAY_PATTERN = Pattern.compile(
            "^\\s*(星期|周)([一二三四五六日1-7])\\s+(\\d{1,2}):(\\d{1,2})\\s*-\\s*(\\d{1,2}):(\\d{1,2})\\s*$"
    );

    private static final Pattern WEEKDAY_ONLY_PATTERN = Pattern.compile(
            "^\\s*(星期|周)?([一二三四五六日1-7])\\s*$"
    );

    private static final Pattern TIME_POINT_PATTERN = Pattern.compile(
            "^\\s*(\\d{1,2}):(\\d{1,2})\\s*$"
    );

    public static final int MONDAY = 0;
    public static final int TUESDAY = 1;
    public static final int WEDNESDAY = 2;
    public static final int THURSDAY = 3;
    public static final int FRIDAY = 4;
    public static final int SATURDAY = 5;
    public static final int SUNDAY = 6;
    public static final int WEEKDAY_COUNT = 7;

    private static final String[] WEEKDAY_MAP = {"一", "二", "三", "四", "五", "六", "日"};
    private static final java.util.Map<String, Integer> WEEKDAY_TO_INDEX = new java.util.HashMap<>();

    static {
        for (int i = 0; i < WEEKDAY_MAP.length; i++) {
            WEEKDAY_TO_INDEX.put(WEEKDAY_MAP[i], i);
        }
    }

    private TimeSlotUtils() {
    }

    public static int parseWeekdayInput(String weekdayInput) {
        if (weekdayInput == null || weekdayInput.trim().isEmpty()) {
            throw new InvalidTimeSlotException("周几不能为空");
        }
        Matcher matcher = WEEKDAY_ONLY_PATTERN.matcher(weekdayInput);
        if (!matcher.matches()) {
            throw new InvalidTimeSlotException(
                    "周几格式不合法，应为：周X、星期X或数字1-7（如：周一、星期一、1），实际输入：" + weekdayInput);
        }
        String weekdayChar = matcher.group(2);
        if (weekdayChar.matches("[1-7]")) {
            return Integer.parseInt(weekdayChar) - 1;
        }
        Integer index = WEEKDAY_TO_INDEX.get(weekdayChar);
        if (index == null) {
            throw new InvalidTimeSlotException("无法识别的周几：" + weekdayChar);
        }
        return index;
    }

    public static int extractWeekdayIndex(String timeSlot) {
        if (timeSlot == null) {
            throw new InvalidTimeSlotException("时间段不能为空");
        }
        String normalized = normalize(timeSlot);
        return extractWeekdayIndexFromNormalized(normalized);
    }

    public static int extractWeekdayIndexFromNormalized(String normalizedTimeSlot) {
        requireNormalizedFormat(normalizedTimeSlot);
        String weekdayChar = normalizedTimeSlot.substring(1, 2);
        Integer index = WEEKDAY_TO_INDEX.get(weekdayChar);
        if (index == null) {
            throw new InvalidTimeSlotException("无法识别的周几：" + weekdayChar);
        }
        return index;
    }

    /**
     * 规范化时间段格式。
     *
     * <p>支持的输入格式：
     * <ul>
     *   <li>周X HH:mm-HH:mm（如：周一 08:00-10:00）</li>
     *   <li>周X H:mm-H:mm（如：周一 8:00-10:00）</li>
     *   <li>星期X HH:mm-HH:mm（如：星期一 08:00-10:00）</li>
     *   <li>允许任意多余空格</li>
     * </ul>
     *
     * <p>规范化后的统一格式：周X HH:mm-HH:mm（如：周一 08:00-10:00）
     *
     * @param input 输入的时间段字符串
     * @return 规范化后的时间段字符串
     * @throws InvalidTimeSlotException 如果输入格式不合法或时间段无效
     */
    public static String normalize(String input) {
        if (input == null) {
            throw new InvalidTimeSlotException("时间段不能为空");
        }

        Matcher matcher = WEEKDAY_PATTERN.matcher(input);
        if (!matcher.matches()) {
            throw new InvalidTimeSlotException(
                    "时间段格式不合法，应为：周X HH:mm-HH:mm（如：周一 08:00-10:00），实际输入：" + input);
        }

        String weekdayPrefix = matcher.group(1);
        String weekdayChar = matcher.group(2);
        int startHour = Integer.parseInt(matcher.group(3));
        int startMinute = Integer.parseInt(matcher.group(4));
        int endHour = Integer.parseInt(matcher.group(5));
        int endMinute = Integer.parseInt(matcher.group(6));

        String normalizedWeekday = normalizeWeekday(weekdayPrefix, weekdayChar);

        validateTimeRange(startHour, startMinute, endHour, endMinute, input);

        return String.format("%s %02d:%02d-%02d:%02d",
                normalizedWeekday, startHour, startMinute, endHour, endMinute);
    }

    private static String normalizeWeekday(String prefix, String dayChar) {
        if (dayChar.matches("[1-7]")) {
            int dayIndex = Integer.parseInt(dayChar) - 1;
            return "周" + WEEKDAY_MAP[dayIndex];
        }
        return "周" + dayChar;
    }

    public static int extractStartTimeMinutes(String timeSlot) {
        if (timeSlot == null) {
            throw new InvalidTimeSlotException("时间段不能为空");
        }
        String normalized = normalize(timeSlot);
        return extractStartTimeMinutesFromNormalized(normalized);
    }

    public static int extractStartTimeMinutesFromNormalized(String normalizedTimeSlot) {
        requireNormalizedFormat(normalizedTimeSlot);
        int startHour = Integer.parseInt(normalizedTimeSlot.substring(3, 5));
        int startMinute = Integer.parseInt(normalizedTimeSlot.substring(6, 8));
        return startHour * 60 + startMinute;
    }

    public static int extractEndTimeMinutes(String timeSlot) {
        if (timeSlot == null) {
            throw new InvalidTimeSlotException("时间段不能为空");
        }
        String normalized = normalize(timeSlot);
        return extractEndTimeMinutesFromNormalized(normalized);
    }

    public static int extractEndTimeMinutesFromNormalized(String normalizedTimeSlot) {
        requireNormalizedFormat(normalizedTimeSlot);
        int endHour = Integer.parseInt(normalizedTimeSlot.substring(9, 11));
        int endMinute = Integer.parseInt(normalizedTimeSlot.substring(12, 14));
        return endHour * 60 + endMinute;
    }

    public static String minutesToTimePoint(int minutes) {
        if (minutes < 0 || minutes > 24 * 60) {
            throw new InvalidTimeSlotException("时间分钟数不合法，应在 0-1440 之间，实际值：" + minutes);
        }
        int hour = minutes / 60;
        int minute = minutes % 60;
        return String.format("%02d:%02d", hour, minute);
    }

    public static String getWeekdayName(int weekdayIndex) {
        if (weekdayIndex < 0 || weekdayIndex >= WEEKDAY_MAP.length) {
            throw new InvalidTimeSlotException("无效的周几索引：" + weekdayIndex);
        }
        return "周" + WEEKDAY_MAP[weekdayIndex];
    }

    private static void requireNormalizedFormat(String normalizedTimeSlot) {
        if (normalizedTimeSlot == null) {
            throw new InvalidTimeSlotException("时间段不能为空");
        }
        if (normalizedTimeSlot.length() != 14
                || normalizedTimeSlot.charAt(0) != '周'
                || normalizedTimeSlot.charAt(2) != ' '
                || normalizedTimeSlot.charAt(5) != ':'
                || normalizedTimeSlot.charAt(8) != '-'
                || normalizedTimeSlot.charAt(11) != ':'
                || !Character.isDigit(normalizedTimeSlot.charAt(3))
                || !Character.isDigit(normalizedTimeSlot.charAt(4))
                || !Character.isDigit(normalizedTimeSlot.charAt(6))
                || !Character.isDigit(normalizedTimeSlot.charAt(7))
                || !Character.isDigit(normalizedTimeSlot.charAt(9))
                || !Character.isDigit(normalizedTimeSlot.charAt(10))
                || !Character.isDigit(normalizedTimeSlot.charAt(12))
                || !Character.isDigit(normalizedTimeSlot.charAt(13))) {
            throw new InvalidTimeSlotException(
                    "时间段格式不合法，应为已规范化的周X HH:mm-HH:mm（如：周一 08:00-10:00），实际输入：" + normalizedTimeSlot);
        }

        int startHour = (normalizedTimeSlot.charAt(3) - '0') * 10 + (normalizedTimeSlot.charAt(4) - '0');
        int startMinute = (normalizedTimeSlot.charAt(6) - '0') * 10 + (normalizedTimeSlot.charAt(7) - '0');
        int endHour = (normalizedTimeSlot.charAt(9) - '0') * 10 + (normalizedTimeSlot.charAt(10) - '0');
        int endMinute = (normalizedTimeSlot.charAt(12) - '0') * 10 + (normalizedTimeSlot.charAt(13) - '0');

        if (startHour < 0 || startHour > 23 || startMinute < 0 || startMinute > 59) {
            throw new InvalidTimeSlotException(
                    "开始时间不合法，应为 00:00-23:59，实际输入：" + normalizedTimeSlot);
        }
        if (endHour < 0 || endHour > 23 || endMinute < 0 || endMinute > 59) {
            throw new InvalidTimeSlotException(
                    "结束时间不合法，应为 00:00-23:59，实际输入：" + normalizedTimeSlot);
        }

        int startMinutes = startHour * 60 + startMinute;
        int endMinutes = endHour * 60 + endMinute;
        if (startMinutes >= endMinutes) {
            throw new InvalidTimeSlotException(
                    "开始时间必须早于结束时间，实际输入：" + normalizedTimeSlot);
        }
    }

    private static void validateTimeRange(int startHour, int startMinute, int endHour, int endMinute, String originalInput) {
        if (startHour < 0 || startHour > 23 || startMinute < 0 || startMinute > 59) {
            throw new InvalidTimeSlotException(
                    "开始时间不合法，应为 00:00-23:59，实际输入：" + originalInput);
        }
        if (endHour < 0 || endHour > 23 || endMinute < 0 || endMinute > 59) {
            throw new InvalidTimeSlotException(
                    "结束时间不合法，应为 00:00-23:59，实际输入：" + originalInput);
        }

        int startMinutes = startHour * 60 + startMinute;
        int endMinutes = endHour * 60 + endMinute;

        if (startMinutes >= endMinutes) {
            throw new InvalidTimeSlotException(
                    "开始时间必须早于结束时间，实际输入：" + originalInput);
        }
    }

    public static int parseTimePoint(String timePoint) {
        if (timePoint == null || timePoint.trim().isEmpty()) {
            throw new InvalidTimeSlotException("时间点不能为空");
        }
        String trimmed = timePoint.trim();
        Matcher matcher = TIME_POINT_PATTERN.matcher(trimmed);
        if (!matcher.matches()) {
            throw new InvalidTimeSlotException(
                    "时间点格式不合法，应为 HH:mm（如：08:00），实际输入：" + timePoint);
        }
        int hour = Integer.parseInt(matcher.group(1));
        int minute = Integer.parseInt(matcher.group(2));
        if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
            throw new InvalidTimeSlotException(
                    "时间点不合法，应为 00:00-23:59，实际输入：" + timePoint);
        }
        return hour * 60 + minute;
    }
}
