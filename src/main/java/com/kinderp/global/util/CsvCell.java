package com.kinderp.global.util;

/**
 * CSV 셀 값을 안전하게 만든다.
 * Excel·Sheets는 {@code = + - @} 로 시작하는 셀을 수식으로 실행하므로(CSV/수식 인젝션),
 * 사용자가 입력한 값이 export에 섞여 있어도 실행되지 않도록 앞에 작은따옴표를 붙인다.
 */
public final class CsvCell {

    private static final String FORMULA_TRIGGERS = "=+-@\t\r";

    private CsvCell() {
    }

    public static String quote(Object value) {
        if (value == null) {
            return "";
        }

        String raw = String.valueOf(value);
        if (!raw.isEmpty() && FORMULA_TRIGGERS.indexOf(raw.charAt(0)) >= 0) {
            raw = "'" + raw;
        }
        return "\"" + raw.replace("\"", "\"\"") + "\"";
    }
}
