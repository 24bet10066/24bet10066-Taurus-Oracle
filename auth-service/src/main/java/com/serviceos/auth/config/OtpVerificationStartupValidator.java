package com.serviceos.auth.config;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("production")
public class OtpVerificationStartupValidator {

    private final AuthProperties properties;

    public OtpVerificationStartupValidator(AuthProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void validate() {
        if (!properties.otp().verificationRequired()) {
            throw new IllegalStateException(
                    "OTP verification cannot be disabled with the production profile");
        }
    }
}
