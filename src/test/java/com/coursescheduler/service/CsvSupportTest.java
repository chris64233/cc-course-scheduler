package com.coursescheduler.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CsvSupportTest {

    @Test
    void escapeField_普通字段原样返回() {
        assertEquals("数学", CsvSupport.escapeField("数学"));
        assertEquals("A101", CsvSupport.escapeField("A101"));
        assertEquals("周一 08:00-10:00", CsvSupport.escapeField("周一 08:00-10:00"));
    }

    @Test
    void escapeField_null字段返回空字符串() {
        assertEquals("", CsvSupport.escapeField(null));
    }

    @Test
    void escapeField_含逗号的字段加双引号() {
        assertEquals("\"数学,高等\"", CsvSupport.escapeField("数学,高等"));
        assertEquals("\"教,室 101\"", CsvSupport.escapeField("教,室 101"));
    }

    @Test
    void escapeField_含双引号的字段转义并加引号() {
        assertEquals("\"张\"\"教授\"\"\"", CsvSupport.escapeField("张\"教授\""));
        assertEquals("\"\"\"quoted\"\"\"", CsvSupport.escapeField("\"quoted\""));
    }

    @Test
    void escapeField_含换行的字段加双引号() {
        assertEquals("\"数学\n高等数学\"", CsvSupport.escapeField("数学\n高等数学"));
        assertEquals("\"教室\n101\"", CsvSupport.escapeField("教室\n101"));
    }

    @Test
    void escapeField_含回车的字段加双引号() {
        assertEquals("\"张\r教授\"", CsvSupport.escapeField("张\r教授"));
        assertEquals("\"a\rb\"", CsvSupport.escapeField("a\rb"));
    }

    @Test
    void escapeField_同时含逗号引号换行_正确转义() {
        String input = "a,b\nc\"d";
        assertEquals("\"a,b\nc\"\"d\"", CsvSupport.escapeField(input));
    }
}
