package com.coursescheduler.service;

public final class CsvSupport {

    private CsvSupport() {
    }

    public static String escapeField(String field) {
        if (field == null) {
            return "";
        }
        boolean needsQuote = field.contains(",") || field.contains("\"")
                || field.contains("\n") || field.contains("\r");
        if (needsQuote) {
            String escaped = field.replace("\"", "\"\"");
            return "\"" + escaped + "\"";
        }
        return field;
    }
}
