package com.kinderp.global.exception;

import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.exception.ConstraintViolationException.ConstraintKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("fast")
@DisplayName("GlobalExceptionHandler 무결성 위반 매핑")
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/attendance/upsert");

    @Test
    @DisplayName("유니크 제약 위반은 409 충돌로 응답하고 예외 원문을 노출하지 않는다")
    void uniqueViolation_Is409() {
        var cause = new ConstraintViolationException(
                "could not execute statement", new SQLException("Duplicate entry 'secret-value' for key 'uk_kid_date'"),
                "insert into attendance ...", ConstraintKind.UNIQUE, "uk_kid_date");

        var response = handler.handleDataIntegrityViolation(new DataIntegrityViolationException("dup", cause), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getCode()).isEqualTo(ErrorCode.DUPLICATE_RESOURCE.getCode());
        assertThat(String.valueOf(response.getBody().getMessage())).doesNotContain("secret-value");
    }

    @Test
    @DisplayName("유니크가 아닌 무결성 위반(FK, NOT NULL 등)은 서버 오류로 남는다")
    void otherIntegrityViolation_Stays500() {
        var cause = new ConstraintViolationException(
                "could not execute statement", new SQLException("Column 'x' cannot be null"),
                "insert into attendance ...", ConstraintKind.OTHER, "x");

        var response = handler.handleDataIntegrityViolation(new DataIntegrityViolationException("null", cause), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
