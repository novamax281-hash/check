package com.example.placement.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class OtpServiceTest {

    private OtpService otpService;

    @BeforeEach
    void setUp() {
        otpService = new OtpService(mock(JavaMailSender.class));
    }

    @Test
    void generatedOtpIsSixDigits() {
        assertThat(otpService.generateOtp()).matches("[0-9]{6}");
    }

    @Test
    void validateOtpAcceptsStoredOtp() {
        otpService.storeOtp("enr1", "123456");
        assertThat(otpService.validateOtp("enr1", "123456")).isTrue();
    }

    @Test
    void correctOtpStillValidatesAfterFewWrongAttempts() {
        otpService.storeOtp("enr1", "123456");
        assertThat(otpService.validateOtp("enr1", "999999")).isFalse();
        assertThat(otpService.validateOtp("enr1", "123456")).isTrue();
    }

    @Test
    void otpIsInvalidatedAfterMaxFailedAttempts() {
        otpService.storeOtp("enr1", "123456");
        for (int i = 0; i < OtpService.MAX_VERIFY_ATTEMPTS; i++) {
            assertThat(otpService.validateOtp("enr1", "999999")).isFalse();
        }
        assertThat(otpService.validateOtp("enr1", "123456")).isFalse();
    }

    @Test
    void resendIsBlockedAfterStoreUntilCooldown() {
        otpService.storeOtp("enr1", "123456");
        assertThat(otpService.canResend("enr1")).isFalse();
        assertThat(otpService.resendWaitSeconds("enr1")).isPositive();
    }

    @Test
    void resendIsAllowedWhenNoOtpStoredOrAfterExpiry() {
        assertThat(otpService.canResend("none")).isTrue();
        assertThat(otpService.resendWaitSeconds("none")).isZero();
    }
}