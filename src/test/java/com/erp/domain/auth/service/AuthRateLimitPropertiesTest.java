package com.erp.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AuthRateLimitPropertiesTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void defaultsAreValid() {
        assertThat(validator.validate(new AuthRateLimitProperties())).isEmpty();
    }

    @Test
    void nonPositiveLimitIsRejected() {
        AuthRateLimitProperties properties = new AuthRateLimitProperties();
        properties.setSignupIpLimit(0);

        assertThat(validator.validate(properties))
                .anyMatch(violation -> violation.getPropertyPath().toString().equals("signupIpLimit"));
    }
}
