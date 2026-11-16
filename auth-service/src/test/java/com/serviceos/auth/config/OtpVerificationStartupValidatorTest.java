package com.serviceos.auth.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OtpVerificationStartupValidatorTest {

    @Test
    void productionRejectsDisabledOtpVerification() {
        AuthProperties properties = properties(false);

        assertThatThrownBy(() -> new OtpVerificationStartupValidator(properties).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("production profile");
    }

    @Test
    void productionAcceptsEnabledOtpVerification() {
        assertThatCode(() -> new OtpVerificationStartupValidator(properties(true)).validate())
                .doesNotThrowAnyException();
    }

    private static AuthProperties properties(boolean required) {
        return new AuthProperties(
                new AuthProperties.Jwt("test-secret-that-is-at-least-32-bytes-long", Duration.ofDays(7),
                        Duration.ofDays(30), "test"),
                new AuthProperties.Otp(6, Duration.ofMinutes(10), 3, 5, required),
                new AuthProperties.TwoFactor("https://example.test", "test"));
    }
}
