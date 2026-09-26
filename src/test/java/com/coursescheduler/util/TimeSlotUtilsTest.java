package com.coursescheduler.util;

import com.coursescheduler.exception.InvalidTimeSlotException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TimeSlotUtilsTest {

    @Test
    void normalize_StandardFormat_NoChange() {
        assertEquals("周一 08:00-10:00", TimeSlotUtils.normalize("周一 08:00-10:00"));
    }

    @Test
    void normalize_SingleDigitStartHour_PaddedWithZero() {
        assertEquals("周一 08:00-10:00", TimeSlotUtils.normalize("周一 8:00-10:00"));
    }

    @Test
    void normalize_SingleDigitEndHour_PaddedWithZero() {
        assertEquals("周一 08:00-09:00", TimeSlotUtils.normalize("周一 08:00-9:00"));
    }

    @Test
    void normalize_BothSingleDigitHours_BothPadded() {
        assertEquals("周一 08:00-09:30", TimeSlotUtils.normalize("周一 8:00-9:30"));
    }

    @Test
    void normalize_ExtraSpaces_Trimmed() {
        assertEquals("周一 08:00-10:00", TimeSlotUtils.normalize("  周一   08:00-10:00  "));
    }

    @Test
    void normalize_WeekdayPrefix_Normalized() {
        assertEquals("周一 08:00-10:00", TimeSlotUtils.normalize("星期一 08:00-10:00"));
    }

    @Test
    void normalize_NumericWeekday_ConvertedToChinese() {
        assertEquals("周一 08:00-10:00", TimeSlotUtils.normalize("周1 08:00-10:00"));
        assertEquals("周二 08:00-10:00", TimeSlotUtils.normalize("周2 08:00-10:00"));
        assertEquals("周日 08:00-10:00", TimeSlotUtils.normalize("周7 08:00-10:00"));
    }

    @Test
    void normalize_NumericWeekdayWithXingqi_Converted() {
        assertEquals("周三 14:00-16:00", TimeSlotUtils.normalize("星期3 14:00-16:00"));
    }

    @Test
    void normalize_SingleDigitMinute_Padded() {
        assertEquals("周一 08:05-10:09", TimeSlotUtils.normalize("周一 8:5-10:9"));
    }

    @Test
    void normalize_AllWeekdays_Supported() {
        assertEquals("周一 08:00-10:00", TimeSlotUtils.normalize("周一 8:00-10:00"));
        assertEquals("周二 08:00-10:00", TimeSlotUtils.normalize("周二 8:00-10:00"));
        assertEquals("周三 08:00-10:00", TimeSlotUtils.normalize("周三 8:00-10:00"));
        assertEquals("周四 08:00-10:00", TimeSlotUtils.normalize("周四 8:00-10:00"));
        assertEquals("周五 08:00-10:00", TimeSlotUtils.normalize("周五 8:00-10:00"));
        assertEquals("周六 08:00-10:00", TimeSlotUtils.normalize("周六 8:00-10:00"));
        assertEquals("周日 08:00-10:00", TimeSlotUtils.normalize("周日 8:00-10:00"));
    }

    @Test
    void normalize_NullInput_ThrowsException() {
        InvalidTimeSlotException exception = assertThrows(InvalidTimeSlotException.class, () -> {
            TimeSlotUtils.normalize(null);
        });
        assertTrue(exception.getMessage().contains("不能为空"));
    }

    @Test
    void normalize_EmptyString_ThrowsException() {
        InvalidTimeSlotException exception = assertThrows(InvalidTimeSlotException.class, () -> {
            TimeSlotUtils.normalize("");
        });
        assertTrue(exception.getMessage().contains("格式不合法"));
    }

    @Test
    void normalize_NoWeekday_ThrowsException() {
        InvalidTimeSlotException exception = assertThrows(InvalidTimeSlotException.class, () -> {
            TimeSlotUtils.normalize("08:00-10:00");
        });
        assertTrue(exception.getMessage().contains("格式不合法"));
    }

    @Test
    void normalize_NoDash_ThrowsException() {
        InvalidTimeSlotException exception = assertThrows(InvalidTimeSlotException.class, () -> {
            TimeSlotUtils.normalize("周一 08:00 10:00");
        });
        assertTrue(exception.getMessage().contains("格式不合法"));
    }

    @Test
    void normalize_InvalidHour_ThrowsException() {
        InvalidTimeSlotException exception = assertThrows(InvalidTimeSlotException.class, () -> {
            TimeSlotUtils.normalize("周一 25:00-10:00");
        });
        assertTrue(exception.getMessage().contains("不合法"));
    }

    @Test
    void normalize_InvalidMinute_ThrowsException() {
        InvalidTimeSlotException exception = assertThrows(InvalidTimeSlotException.class, () -> {
            TimeSlotUtils.normalize("周一 08:60-10:00");
        });
        assertTrue(exception.getMessage().contains("不合法"));
    }

    @Test
    void normalize_StartEqualsEnd_ThrowsException() {
        InvalidTimeSlotException exception = assertThrows(InvalidTimeSlotException.class, () -> {
            TimeSlotUtils.normalize("周一 08:00-08:00");
        });
        assertTrue(exception.getMessage().contains("必须早于"));
    }

    @Test
    void normalize_StartAfterEnd_ThrowsException() {
        InvalidTimeSlotException exception = assertThrows(InvalidTimeSlotException.class, () -> {
            TimeSlotUtils.normalize("周一 10:00-08:00");
        });
        assertTrue(exception.getMessage().contains("必须早于"));
    }

    @Test
    void normalize_RandomString_ThrowsException() {
        InvalidTimeSlotException exception = assertThrows(InvalidTimeSlotException.class, () -> {
            TimeSlotUtils.normalize("随便写点什么");
        });
        assertTrue(exception.getMessage().contains("格式不合法"));
    }

    @Test
    void normalize_EnglishWeekday_ThrowsException() {
        InvalidTimeSlotException exception = assertThrows(InvalidTimeSlotException.class, () -> {
            TimeSlotUtils.normalize("Monday 08:00-10:00");
        });
        assertTrue(exception.getMessage().contains("格式不合法"));
    }

    @Test
    void extractWeekdayIndex_Monday_Returns0() {
        assertEquals(0, TimeSlotUtils.extractWeekdayIndex("周一 08:00-10:00"));
    }

    @Test
    void extractWeekdayIndex_Tuesday_Returns1() {
        assertEquals(1, TimeSlotUtils.extractWeekdayIndex("周二 08:00-10:00"));
    }

    @Test
    void extractWeekdayIndex_Wednesday_Returns2() {
        assertEquals(2, TimeSlotUtils.extractWeekdayIndex("周三 08:00-10:00"));
    }

    @Test
    void extractWeekdayIndex_Thursday_Returns3() {
        assertEquals(3, TimeSlotUtils.extractWeekdayIndex("周四 08:00-10:00"));
    }

    @Test
    void extractWeekdayIndex_Friday_Returns4() {
        assertEquals(4, TimeSlotUtils.extractWeekdayIndex("周五 08:00-10:00"));
    }

    @Test
    void extractWeekdayIndex_Saturday_Returns5() {
        assertEquals(5, TimeSlotUtils.extractWeekdayIndex("周六 08:00-10:00"));
    }

    @Test
    void extractWeekdayIndex_Sunday_Returns6() {
        assertEquals(6, TimeSlotUtils.extractWeekdayIndex("周日 08:00-10:00"));
    }

    @Test
    void extractWeekdayIndex_NonStandardFormat_NormalizedFirst() {
        assertEquals(0, TimeSlotUtils.extractWeekdayIndex("星期一 8:00-10:00"));
        assertEquals(0, TimeSlotUtils.extractWeekdayIndex("周1 08:00-10:00"));
        assertEquals(1, TimeSlotUtils.extractWeekdayIndex("星期2 08:00-10:00"));
    }

    @Test
    void extractWeekdayIndex_NullInput_ThrowsException() {
        InvalidTimeSlotException exception = assertThrows(InvalidTimeSlotException.class, () -> {
            TimeSlotUtils.extractWeekdayIndex(null);
        });
        assertTrue(exception.getMessage().contains("不能为空"));
    }

    @Test
    void extractWeekdayIndex_InvalidFormat_ThrowsException() {
        InvalidTimeSlotException exception = assertThrows(InvalidTimeSlotException.class, () -> {
            TimeSlotUtils.extractWeekdayIndex("invalid");
        });
        assertTrue(exception.getMessage().contains("格式不合法"));
    }

    @Test
    void weekdayConstants_OrderIsMondayToSunday() {
        assertEquals(0, TimeSlotUtils.MONDAY);
        assertEquals(1, TimeSlotUtils.TUESDAY);
        assertEquals(2, TimeSlotUtils.WEDNESDAY);
        assertEquals(3, TimeSlotUtils.THURSDAY);
        assertEquals(4, TimeSlotUtils.FRIDAY);
        assertEquals(5, TimeSlotUtils.SATURDAY);
        assertEquals(6, TimeSlotUtils.SUNDAY);
        assertEquals(7, TimeSlotUtils.WEEKDAY_COUNT);
    }

    @Test
    void extractWeekdayIndex_AllWeekdays_MatchExpectedConstants() {
        assertEquals(TimeSlotUtils.MONDAY, TimeSlotUtils.extractWeekdayIndex("周一 08:00-10:00"));
        assertEquals(TimeSlotUtils.TUESDAY, TimeSlotUtils.extractWeekdayIndex("周二 08:00-10:00"));
        assertEquals(TimeSlotUtils.WEDNESDAY, TimeSlotUtils.extractWeekdayIndex("周三 08:00-10:00"));
        assertEquals(TimeSlotUtils.THURSDAY, TimeSlotUtils.extractWeekdayIndex("周四 08:00-10:00"));
        assertEquals(TimeSlotUtils.FRIDAY, TimeSlotUtils.extractWeekdayIndex("周五 08:00-10:00"));
        assertEquals(TimeSlotUtils.SATURDAY, TimeSlotUtils.extractWeekdayIndex("周六 08:00-10:00"));
        assertEquals(TimeSlotUtils.SUNDAY, TimeSlotUtils.extractWeekdayIndex("周日 08:00-10:00"));
    }

    @Test
    void parseWeekdayInput_ZhouYi_Returns0() {
        assertEquals(0, TimeSlotUtils.parseWeekdayInput("周一"));
    }

    @Test
    void parseWeekdayInput_XingQiYi_Returns0() {
        assertEquals(0, TimeSlotUtils.parseWeekdayInput("星期一"));
    }

    @Test
    void parseWeekdayInput_Number1_Returns0() {
        assertEquals(0, TimeSlotUtils.parseWeekdayInput("1"));
    }

    @Test
    void parseWeekdayInput_Zhou1_Returns0() {
        assertEquals(0, TimeSlotUtils.parseWeekdayInput("周1"));
    }

    @Test
    void parseWeekdayInput_XingQi1_Returns0() {
        assertEquals(0, TimeSlotUtils.parseWeekdayInput("星期1"));
    }

    @Test
    void parseWeekdayInput_AllWeekdays_WithZhou_ReturnCorrectIndices() {
        assertEquals(TimeSlotUtils.MONDAY, TimeSlotUtils.parseWeekdayInput("周一"));
        assertEquals(TimeSlotUtils.TUESDAY, TimeSlotUtils.parseWeekdayInput("周二"));
        assertEquals(TimeSlotUtils.WEDNESDAY, TimeSlotUtils.parseWeekdayInput("周三"));
        assertEquals(TimeSlotUtils.THURSDAY, TimeSlotUtils.parseWeekdayInput("周四"));
        assertEquals(TimeSlotUtils.FRIDAY, TimeSlotUtils.parseWeekdayInput("周五"));
        assertEquals(TimeSlotUtils.SATURDAY, TimeSlotUtils.parseWeekdayInput("周六"));
        assertEquals(TimeSlotUtils.SUNDAY, TimeSlotUtils.parseWeekdayInput("周日"));
    }

    @Test
    void parseWeekdayInput_AllWeekdays_WithXingQi_ReturnCorrectIndices() {
        assertEquals(TimeSlotUtils.MONDAY, TimeSlotUtils.parseWeekdayInput("星期一"));
        assertEquals(TimeSlotUtils.TUESDAY, TimeSlotUtils.parseWeekdayInput("星期二"));
        assertEquals(TimeSlotUtils.WEDNESDAY, TimeSlotUtils.parseWeekdayInput("星期三"));
        assertEquals(TimeSlotUtils.THURSDAY, TimeSlotUtils.parseWeekdayInput("星期四"));
        assertEquals(TimeSlotUtils.FRIDAY, TimeSlotUtils.parseWeekdayInput("星期五"));
        assertEquals(TimeSlotUtils.SATURDAY, TimeSlotUtils.parseWeekdayInput("星期六"));
        assertEquals(TimeSlotUtils.SUNDAY, TimeSlotUtils.parseWeekdayInput("星期日"));
    }

    @Test
    void parseWeekdayInput_AllNumbers_ReturnCorrectIndices() {
        assertEquals(TimeSlotUtils.MONDAY, TimeSlotUtils.parseWeekdayInput("1"));
        assertEquals(TimeSlotUtils.TUESDAY, TimeSlotUtils.parseWeekdayInput("2"));
        assertEquals(TimeSlotUtils.WEDNESDAY, TimeSlotUtils.parseWeekdayInput("3"));
        assertEquals(TimeSlotUtils.THURSDAY, TimeSlotUtils.parseWeekdayInput("4"));
        assertEquals(TimeSlotUtils.FRIDAY, TimeSlotUtils.parseWeekdayInput("5"));
        assertEquals(TimeSlotUtils.SATURDAY, TimeSlotUtils.parseWeekdayInput("6"));
        assertEquals(TimeSlotUtils.SUNDAY, TimeSlotUtils.parseWeekdayInput("7"));
    }

    @Test
    void parseWeekdayInput_ExtraSpaces_Trimmed() {
        assertEquals(0, TimeSlotUtils.parseWeekdayInput("  周一  "));
        assertEquals(0, TimeSlotUtils.parseWeekdayInput("  星期一  "));
        assertEquals(0, TimeSlotUtils.parseWeekdayInput("  1  "));
    }

    @Test
    void parseWeekdayInput_NullInput_ThrowsException() {
        InvalidTimeSlotException exception = assertThrows(InvalidTimeSlotException.class, () -> {
            TimeSlotUtils.parseWeekdayInput(null);
        });
        assertTrue(exception.getMessage().contains("不能为空"));
    }

    @Test
    void parseWeekdayInput_EmptyString_ThrowsException() {
        InvalidTimeSlotException exception = assertThrows(InvalidTimeSlotException.class, () -> {
            TimeSlotUtils.parseWeekdayInput("");
        });
        assertTrue(exception.getMessage().contains("不能为空"));
    }

    @Test
    void parseWeekdayInput_WhitespaceOnly_ThrowsException() {
        InvalidTimeSlotException exception = assertThrows(InvalidTimeSlotException.class, () -> {
            TimeSlotUtils.parseWeekdayInput("   ");
        });
        assertTrue(exception.getMessage().contains("不能为空"));
    }

    @Test
    void parseWeekdayInput_InvalidFormat_ThrowsException() {
        InvalidTimeSlotException exception = assertThrows(InvalidTimeSlotException.class, () -> {
            TimeSlotUtils.parseWeekdayInput("星期一 08:00");
        });
        assertTrue(exception.getMessage().contains("格式不合法"));
    }

    @Test
    void parseWeekdayInput_EnglishWeekday_ThrowsException() {
        InvalidTimeSlotException exception = assertThrows(InvalidTimeSlotException.class, () -> {
            TimeSlotUtils.parseWeekdayInput("Monday");
        });
        assertTrue(exception.getMessage().contains("格式不合法"));
    }

    @Test
    void extractStartTimeMinutes_EightOClock_Returns480() {
        assertEquals(8 * 60, TimeSlotUtils.extractStartTimeMinutes("周一 08:00-10:00"));
    }

    @Test
    void extractStartTimeMinutes_NineThirty_Returns570() {
        assertEquals(9 * 60 + 30, TimeSlotUtils.extractStartTimeMinutes("周二 09:30-11:00"));
    }

    @Test
    void extractStartTimeMinutes_NonStandardFormat_NormalizedFirst() {
        assertEquals(8 * 60, TimeSlotUtils.extractStartTimeMinutes("星期一 8:00-10:00"));
        assertEquals(14 * 60, TimeSlotUtils.extractStartTimeMinutes("周1 14:00-16:00"));
    }

    @Test
    void extractStartTimeMinutes_NullInput_ThrowsException() {
        InvalidTimeSlotException exception = assertThrows(InvalidTimeSlotException.class, () -> {
            TimeSlotUtils.extractStartTimeMinutes(null);
        });
        assertTrue(exception.getMessage().contains("不能为空"));
    }

    @Test
    void extractWeekdayIndexFromNormalized_AllWeekdays_ReturnCorrectIndices() {
        assertEquals(TimeSlotUtils.MONDAY, TimeSlotUtils.extractWeekdayIndexFromNormalized("周一 08:00-10:00"));
        assertEquals(TimeSlotUtils.TUESDAY, TimeSlotUtils.extractWeekdayIndexFromNormalized("周二 08:00-10:00"));
        assertEquals(TimeSlotUtils.WEDNESDAY, TimeSlotUtils.extractWeekdayIndexFromNormalized("周三 08:00-10:00"));
        assertEquals(TimeSlotUtils.THURSDAY, TimeSlotUtils.extractWeekdayIndexFromNormalized("周四 08:00-10:00"));
        assertEquals(TimeSlotUtils.FRIDAY, TimeSlotUtils.extractWeekdayIndexFromNormalized("周五 08:00-10:00"));
        assertEquals(TimeSlotUtils.SATURDAY, TimeSlotUtils.extractWeekdayIndexFromNormalized("周六 08:00-10:00"));
        assertEquals(TimeSlotUtils.SUNDAY, TimeSlotUtils.extractWeekdayIndexFromNormalized("周日 08:00-10:00"));
    }

    @Test
    void extractWeekdayIndexFromNormalized_DifferentTimes_SameWeekday() {
        assertEquals(TimeSlotUtils.MONDAY, TimeSlotUtils.extractWeekdayIndexFromNormalized("周一 08:00-10:00"));
        assertEquals(TimeSlotUtils.MONDAY, TimeSlotUtils.extractWeekdayIndexFromNormalized("周一 14:00-16:00"));
        assertEquals(TimeSlotUtils.MONDAY, TimeSlotUtils.extractWeekdayIndexFromNormalized("周一 22:00-23:00"));
    }

    @Test
    void extractStartTimeMinutesFromNormalized_EightOClock_Returns480() {
        assertEquals(8 * 60, TimeSlotUtils.extractStartTimeMinutesFromNormalized("周一 08:00-10:00"));
    }

    @Test
    void extractStartTimeMinutesFromNormalized_NineThirty_Returns570() {
        assertEquals(9 * 60 + 30, TimeSlotUtils.extractStartTimeMinutesFromNormalized("周二 09:30-11:00"));
    }

    @Test
    void extractStartTimeMinutesFromNormalized_Midnight_Returns0() {
        assertEquals(0, TimeSlotUtils.extractStartTimeMinutesFromNormalized("周三 00:00-01:00"));
    }

    @Test
    void extractStartTimeMinutesFromNormalized_LateEvening_ReturnsCorrect() {
        assertEquals(22 * 60 + 30, TimeSlotUtils.extractStartTimeMinutesFromNormalized("周四 22:30-23:30"));
    }

    @Test
    void extractStartTimeMinutesFromNormalized_DifferentWeekdaySameTime_SameResult() {
        int monday = TimeSlotUtils.extractStartTimeMinutesFromNormalized("周一 08:00-10:00");
        int friday = TimeSlotUtils.extractStartTimeMinutesFromNormalized("周五 08:00-10:00");
        assertEquals(monday, friday);
        assertEquals(8 * 60, monday);
    }

    @Test
    void extractWeekdayIndexFromNormalized_NullInput_ThrowsInvalidTimeSlotException() {
        InvalidTimeSlotException exception = assertThrows(InvalidTimeSlotException.class, () -> {
            TimeSlotUtils.extractWeekdayIndexFromNormalized(null);
        });
        assertTrue(exception.getMessage().contains("不能为空"));
    }

    @Test
    void extractWeekdayIndexFromNormalized_EmptyString_ThrowsInvalidTimeSlotException() {
        InvalidTimeSlotException exception = assertThrows(InvalidTimeSlotException.class, () -> {
            TimeSlotUtils.extractWeekdayIndexFromNormalized("");
        });
        assertTrue(exception.getMessage().contains("格式不合法"));
    }

    @Test
    void extractWeekdayIndexFromNormalized_TooShort_ThrowsInvalidTimeSlotException() {
        InvalidTimeSlotException exception = assertThrows(InvalidTimeSlotException.class, () -> {
            TimeSlotUtils.extractWeekdayIndexFromNormalized("周一 08:00");
        });
        assertTrue(exception.getMessage().contains("格式不合法"));
    }

    @Test
    void extractWeekdayIndexFromNormalized_WrongFirstChar_ThrowsInvalidTimeSlotException() {
        InvalidTimeSlotException exception = assertThrows(InvalidTimeSlotException.class, () -> {
            TimeSlotUtils.extractWeekdayIndexFromNormalized("星期一 08:00-10:00");
        });
        assertTrue(exception.getMessage().contains("格式不合法"));
    }

    @Test
    void extractWeekdayIndexFromNormalized_NoSpaceAfterWeekday_ThrowsInvalidTimeSlotException() {
        InvalidTimeSlotException exception = assertThrows(InvalidTimeSlotException.class, () -> {
            TimeSlotUtils.extractWeekdayIndexFromNormalized("周一008:00-10:00");
        });
        assertTrue(exception.getMessage().contains("格式不合法"));
    }

    @Test
    void extractWeekdayIndexFromNormalized_NoColonAfterHour_ThrowsInvalidTimeSlotException() {
        InvalidTimeSlotException exception = assertThrows(InvalidTimeSlotException.class, () -> {
            TimeSlotUtils.extractWeekdayIndexFromNormalized("周一 08000-10:00");
        });
        assertTrue(exception.getMessage().contains("格式不合法"));
    }

    @Test
    void extractWeekdayIndexFromNormalized_NoDashBetweenTimes_ThrowsInvalidTimeSlotException() {
        InvalidTimeSlotException exception = assertThrows(InvalidTimeSlotException.class, () -> {
            TimeSlotUtils.extractWeekdayIndexFromNormalized("周一 08:00010:00");
        });
        assertTrue(exception.getMessage().contains("格式不合法"));
    }

    @Test
    void extractWeekdayIndexFromNormalized_NoColonAfterEndHour_ThrowsInvalidTimeSlotException() {
        InvalidTimeSlotException exception = assertThrows(InvalidTimeSlotException.class, () -> {
            TimeSlotUtils.extractWeekdayIndexFromNormalized("周一 08:00-10000");
        });
        assertTrue(exception.getMessage().contains("格式不合法"));
    }

    @Test
    void extractWeekdayIndexFromNormalized_UnnormalizedNumericWeekday_ThrowsInvalidTimeSlotException() {
        InvalidTimeSlotException exception = assertThrows(InvalidTimeSlotException.class, () -> {
            TimeSlotUtils.extractWeekdayIndexFromNormalized("周1 08:00-10:00");
        });
        assertTrue(exception.getMessage().contains("无法识别"));
    }

    @Test
    void extractStartTimeMinutesFromNormalized_NullInput_ThrowsInvalidTimeSlotException() {
        InvalidTimeSlotException exception = assertThrows(InvalidTimeSlotException.class, () -> {
            TimeSlotUtils.extractStartTimeMinutesFromNormalized(null);
        });
        assertTrue(exception.getMessage().contains("不能为空"));
    }

    @Test
    void extractStartTimeMinutesFromNormalized_InvalidFormat_ThrowsInvalidTimeSlotException() {
        InvalidTimeSlotException exception = assertThrows(InvalidTimeSlotException.class, () -> {
            TimeSlotUtils.extractStartTimeMinutesFromNormalized("bad input");
        });
        assertTrue(exception.getMessage().contains("格式不合法"));
    }

    @Test
    void extractStartTimeMinutesFromNormalized_UnnormalizedFormat_ThrowsInvalidTimeSlotException() {
        InvalidTimeSlotException exception = assertThrows(InvalidTimeSlotException.class, () -> {
            TimeSlotUtils.extractStartTimeMinutesFromNormalized("星期一 8:00-10:00");
        });
        assertTrue(exception.getMessage().contains("格式不合法"));
    }

    @Test
    void extractWeekdayIndexFromNormalized_NonDigitStartHour_ThrowsInvalidTimeSlotException() {
        InvalidTimeSlotException exception = assertThrows(InvalidTimeSlotException.class, () -> {
            TimeSlotUtils.extractWeekdayIndexFromNormalized("周一 aa:00-10:00");
        });
        assertTrue(exception.getMessage().contains("格式不合法"));
    }

    @Test
    void extractWeekdayIndexFromNormalized_NonDigitStartMinute_ThrowsInvalidTimeSlotException() {
        InvalidTimeSlotException exception = assertThrows(InvalidTimeSlotException.class, () -> {
            TimeSlotUtils.extractWeekdayIndexFromNormalized("周一 08:xx-10:00");
        });
        assertTrue(exception.getMessage().contains("格式不合法"));
    }

    @Test
    void extractWeekdayIndexFromNormalized_NonDigitEndHour_ThrowsInvalidTimeSlotException() {
        InvalidTimeSlotException exception = assertThrows(InvalidTimeSlotException.class, () -> {
            TimeSlotUtils.extractWeekdayIndexFromNormalized("周一 08:00-yy:00");
        });
        assertTrue(exception.getMessage().contains("格式不合法"));
    }

    @Test
    void extractWeekdayIndexFromNormalized_NonDigitEndMinute_ThrowsInvalidTimeSlotException() {
        InvalidTimeSlotException exception = assertThrows(InvalidTimeSlotException.class, () -> {
            TimeSlotUtils.extractWeekdayIndexFromNormalized("周一 08:00-10:zz");
        });
        assertTrue(exception.getMessage().contains("格式不合法"));
    }

    @Test
    void extractStartTimeMinutesFromNormalized_NonDigitStartHour_ThrowsInvalidTimeSlotException() {
        InvalidTimeSlotException exception = assertThrows(InvalidTimeSlotException.class, () -> {
            TimeSlotUtils.extractStartTimeMinutesFromNormalized("周一 9a:00-10:00");
        });
        assertTrue(exception.getMessage().contains("格式不合法"));
    }

    @Test
    void extractStartTimeMinutesFromNormalized_NonDigitStartMinute_ThrowsInvalidTimeSlotException() {
        InvalidTimeSlotException exception = assertThrows(InvalidTimeSlotException.class, () -> {
            TimeSlotUtils.extractStartTimeMinutesFromNormalized("周一 08:0b-10:00");
        });
        assertTrue(exception.getMessage().contains("格式不合法"));
    }

    @Test
    void extractWeekdayIndexFromNormalized_InvalidStartHour_ThrowsInvalidTimeSlotException() {
        InvalidTimeSlotException exception = assertThrows(InvalidTimeSlotException.class, () -> {
            TimeSlotUtils.extractWeekdayIndexFromNormalized("周一 24:00-10:00");
        });
        assertTrue(exception.getMessage().contains("开始时间不合法"));
    }

    @Test
    void extractWeekdayIndexFromNormalized_InvalidStartMinute_ThrowsInvalidTimeSlotException() {
        InvalidTimeSlotException exception = assertThrows(InvalidTimeSlotException.class, () -> {
            TimeSlotUtils.extractWeekdayIndexFromNormalized("周一 08:60-10:00");
        });
        assertTrue(exception.getMessage().contains("开始时间不合法"));
    }

    @Test
    void extractWeekdayIndexFromNormalized_InvalidEndHour_ThrowsInvalidTimeSlotException() {
        InvalidTimeSlotException exception = assertThrows(InvalidTimeSlotException.class, () -> {
            TimeSlotUtils.extractWeekdayIndexFromNormalized("周一 08:00-25:00");
        });
        assertTrue(exception.getMessage().contains("结束时间不合法"));
    }

    @Test
    void extractWeekdayIndexFromNormalized_InvalidEndMinute_ThrowsInvalidTimeSlotException() {
        InvalidTimeSlotException exception = assertThrows(InvalidTimeSlotException.class, () -> {
            TimeSlotUtils.extractWeekdayIndexFromNormalized("周一 08:00-10:60");
        });
        assertTrue(exception.getMessage().contains("结束时间不合法"));
    }

    @Test
    void extractWeekdayIndexFromNormalized_StartEqualsEnd_ThrowsInvalidTimeSlotException() {
        InvalidTimeSlotException exception = assertThrows(InvalidTimeSlotException.class, () -> {
            TimeSlotUtils.extractWeekdayIndexFromNormalized("周一 08:00-08:00");
        });
        assertTrue(exception.getMessage().contains("必须早于"));
    }

    @Test
    void extractWeekdayIndexFromNormalized_StartAfterEnd_ThrowsInvalidTimeSlotException() {
        InvalidTimeSlotException exception = assertThrows(InvalidTimeSlotException.class, () -> {
            TimeSlotUtils.extractWeekdayIndexFromNormalized("周一 10:00-08:00");
        });
        assertTrue(exception.getMessage().contains("必须早于"));
    }

    @Test
    void extractStartTimeMinutesFromNormalized_InvalidHour_ThrowsInvalidTimeSlotException() {
        InvalidTimeSlotException exception = assertThrows(InvalidTimeSlotException.class, () -> {
            TimeSlotUtils.extractStartTimeMinutesFromNormalized("周五 99:99-10:00");
        });
        assertTrue(exception.getMessage().contains("开始时间不合法"));
    }

    @Test
    void extractStartTimeMinutesFromNormalized_StartAfterEnd_ThrowsInvalidTimeSlotException() {
        InvalidTimeSlotException exception = assertThrows(InvalidTimeSlotException.class, () -> {
            TimeSlotUtils.extractStartTimeMinutesFromNormalized("周三 14:00-10:00");
        });
        assertTrue(exception.getMessage().contains("必须早于"));
    }

    @Test
    void extractWeekdayIndexFromNormalized_ValidBoundaryTime_Works() {
        assertEquals(TimeSlotUtils.MONDAY, TimeSlotUtils.extractWeekdayIndexFromNormalized("周一 00:00-23:59"));
    }

    @Test
    void extractStartTimeMinutesFromNormalized_ValidBoundaryTime_Works() {
        assertEquals(23 * 60 + 58, TimeSlotUtils.extractStartTimeMinutesFromNormalized("周四 23:58-23:59"));
    }

    @Test
    void extractStartTimeMinutesFromNormalized_MidnightStart_Works() {
        assertEquals(0, TimeSlotUtils.extractStartTimeMinutesFromNormalized("周六 00:00-01:00"));
    }

    @Test
    void extractEndTimeMinutes_NormalizedInput_ReturnsCorrectMinutes() {
        assertEquals(10 * 60, TimeSlotUtils.extractEndTimeMinutes("周一 08:00-10:00"));
    }

    @Test
    void extractEndTimeMinutes_NonStandardFormat_NormalizedFirst() {
        assertEquals(10 * 60, TimeSlotUtils.extractEndTimeMinutes("星期一 8:00-10:00"));
    }

    @Test
    void extractEndTimeMinutes_NullInput_ThrowsException() {
        InvalidTimeSlotException exception = assertThrows(InvalidTimeSlotException.class, () -> {
            TimeSlotUtils.extractEndTimeMinutes(null);
        });
        assertTrue(exception.getMessage().contains("不能为空"));
    }

    @Test
    void extractEndTimeMinutesFromNormalized_TenOClock_Returns600() {
        assertEquals(10 * 60, TimeSlotUtils.extractEndTimeMinutesFromNormalized("周一 08:00-10:00"));
    }

    @Test
    void extractEndTimeMinutesFromNormalized_ElevenThirty_Returns690() {
        assertEquals(11 * 60 + 30, TimeSlotUtils.extractEndTimeMinutesFromNormalized("周二 09:30-11:30"));
    }

    @Test
    void extractEndTimeMinutesFromNormalized_NullInput_ThrowsException() {
        InvalidTimeSlotException exception = assertThrows(InvalidTimeSlotException.class, () -> {
            TimeSlotUtils.extractEndTimeMinutesFromNormalized(null);
        });
        assertTrue(exception.getMessage().contains("不能为空"));
    }

    @Test
    void extractEndTimeMinutesFromNormalized_InvalidFormat_ThrowsException() {
        InvalidTimeSlotException exception = assertThrows(InvalidTimeSlotException.class, () -> {
            TimeSlotUtils.extractEndTimeMinutesFromNormalized("bad input");
        });
        assertTrue(exception.getMessage().contains("格式不合法"));
    }

    @Test
    void minutesToTimePoint_ZeroMinutes_Returns0000() {
        assertEquals("00:00", TimeSlotUtils.minutesToTimePoint(0));
    }

    @Test
    void minutesToTimePoint_EightOClock_Returns0800() {
        assertEquals("08:00", TimeSlotUtils.minutesToTimePoint(8 * 60));
    }

    @Test
    void minutesToTimePoint_NineThirty_Returns0930() {
        assertEquals("09:30", TimeSlotUtils.minutesToTimePoint(9 * 60 + 30));
    }

    @Test
    void minutesToTimePoint_Midnight1440_Returns2400() {
        assertEquals("24:00", TimeSlotUtils.minutesToTimePoint(24 * 60));
    }

    @Test
    void minutesToTimePoint_NegativeValue_ThrowsException() {
        InvalidTimeSlotException exception = assertThrows(InvalidTimeSlotException.class, () -> {
            TimeSlotUtils.minutesToTimePoint(-1);
        });
        assertTrue(exception.getMessage().contains("不合法"));
    }

    @Test
    void minutesToTimePoint_TooLarge_ThrowsException() {
        InvalidTimeSlotException exception = assertThrows(InvalidTimeSlotException.class, () -> {
            TimeSlotUtils.minutesToTimePoint(24 * 60 + 1);
        });
        assertTrue(exception.getMessage().contains("不合法"));
    }

    @Test
    void getWeekdayName_AllIndices_ReturnCorrectNames() {
        assertEquals("周一", TimeSlotUtils.getWeekdayName(0));
        assertEquals("周二", TimeSlotUtils.getWeekdayName(1));
        assertEquals("周三", TimeSlotUtils.getWeekdayName(2));
        assertEquals("周四", TimeSlotUtils.getWeekdayName(3));
        assertEquals("周五", TimeSlotUtils.getWeekdayName(4));
        assertEquals("周六", TimeSlotUtils.getWeekdayName(5));
        assertEquals("周日", TimeSlotUtils.getWeekdayName(6));
    }

    @Test
    void getWeekdayName_NegativeIndex_ThrowsException() {
        InvalidTimeSlotException exception = assertThrows(InvalidTimeSlotException.class, () -> {
            TimeSlotUtils.getWeekdayName(-1);
        });
        assertTrue(exception.getMessage().contains("无效的周几索引"));
    }

    @Test
    void getWeekdayName_IndexTooLarge_ThrowsException() {
        InvalidTimeSlotException exception = assertThrows(InvalidTimeSlotException.class, () -> {
            TimeSlotUtils.getWeekdayName(7);
        });
        assertTrue(exception.getMessage().contains("无效的周几索引"));
    }
}
