package com.kinderp.global.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("fast")
@DisplayName("CSV 셀 인코딩")
class CsvCellTest {

    @Test
    @DisplayName("수식을 시작하는 문자(= + - @ 탭 CR)로 시작하면 작은따옴표를 붙여 실행되지 않게 한다")
    void formulaPrefix_IsNeutralized() {
        assertThat(CsvCell.quote("=HYPERLINK(\"http://evil\")")).isEqualTo("\"'=HYPERLINK(\"\"http://evil\"\")\"");
        assertThat(CsvCell.quote("+1+1")).isEqualTo("\"'+1+1\"");
        assertThat(CsvCell.quote("-2+3")).isEqualTo("\"'-2+3\"");
        assertThat(CsvCell.quote("@SUM(A1)")).isEqualTo("\"'@SUM(A1)\"");
        assertThat(CsvCell.quote("\t=1")).isEqualTo("\"'\t=1\"");
        assertThat(CsvCell.quote("\r=1")).isEqualTo("\"'\r=1\"");
    }

    @Test
    @DisplayName("일반 값은 그대로 두고 큰따옴표만 이스케이프한다")
    void plainValue_OnlyEscapesQuotes() {
        assertThat(CsvCell.quote("park@test.com")).isEqualTo("\"park@test.com\"");
        assertThat(CsvCell.quote("say \"hi\"")).isEqualTo("\"say \"\"hi\"\"\"");
        assertThat(CsvCell.quote(42L)).isEqualTo("\"42\"");
        assertThat(CsvCell.quote("a=b")).isEqualTo("\"a=b\"");
    }

    @Test
    @DisplayName("null은 빈 셀, 빈 문자열은 빈 따옴표 셀이다")
    void nullAndEmpty() {
        assertThat(CsvCell.quote(null)).isEmpty();
        assertThat(CsvCell.quote("")).isEqualTo("\"\"");
    }
}
