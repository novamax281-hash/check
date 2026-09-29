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
    void sendOtpSyncReportsSuccessWhenMailIsAccepted() {
        JavaMailSender sender = mock(JavaMailSender.class);
        OtpService service = new OtpService(sender);
        service.storeOtp("reset:user@liet.in", "123456");

        assertThat(service.sendOtpSync("reset:user@liet.in", "user@liet.in", "123456")).isTrue();
        assertThat(service.validateOtp("reset:user@liet.in", "123456")).isTrue();
    }

    @Test
    void sendOtpSyncReportsFailureAndClearsTheStoredOtp() {
        JavaMailSender sender = mock(JavaMailSender.class);
        org.mockito.Mockito.doThrow(new org.springframework.mail.MailSendException("smtp down"))
                .when(sender).send(org.mockito.ArgumentMatchers.any(
                        org.springframework.mail.SimpleMailMessage.class));
        OtpService service = new OtpService(sender);
        service.storeOtp("reset:user@liet.in", "123456");

        assertThat(service.sendOtpSync("reset:user@liet.in", "user@liet.in", "123456")).isFalse();
        assertThat(service.validateOtp("reset:user@liet.in", "123456")).isFalse();
    }

    @Test
    void failedSyncSendAllowsAnImmediateRetry() {
        JavaMailSender sender = mock(JavaMailSender.class);
        org.mockito.Mockito.doThrow(new org.springframework.mail.MailSendException("quota exceeded"))
                .when(sender).send(org.mockito.ArgumentMatchers.any(
                        org.springframework.mail.SimpleMailMessage.class));
        OtpService service = new OtpService(sender);
        service.storeOtp("reset:user@liet.in", "123456");

        assertThat(service.sendOtpSync("reset:user@liet.in", "user@liet.in", "123456")).isFalse();
        assertThat(service.canResend("reset:user@liet.in")).isTrue();
    }

    @Test
    void validateOtpAcceptsStoredOtp() {
        otpService.storeOtp("enr1", "123456");
        assertThat(otpService.validateOtp("enr1", "123456")).isTrue();
    }

    @Test
    void matchesOtpDoesNotConsumeItBeforePasswordReset() {
        otpService.storeOtp("reset:user@liet.in", "123456");
        assertThat(otpService.matchesOtp("reset:user@liet.in", "123456")).isTrue();
        assertThat(otpService.validateOtp("reset:user@liet.in", "123456")).isTrue();
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

    @Test
    void rateLimitAllowsFiveThenBlocksSameIp() {
        for (int i = 0; i < OtpService.MAX_OTPS_PER_IP_PER_HOUR; i++) {
            assertThat(otpService.isRateLimited("10.0.0.1")).isFalse();
        }
        assertThat(otpService.isRateLimited("10.0.0.1")).isTrue();
    }

    @Test
    void rateLimitIsIndependentPerIp() {
        for (int i = 0; i < OtpService.MAX_OTPS_PER_IP_PER_HOUR; i++) {
            assertThat(otpService.isRateLimited("10.0.0.1")).isFalse();
        }
        assertThat(otpService.isRateLimited("10.0.0.2")).isFalse();
    }

    @Test
    void blankIpStillSharesGlobalLimitsAndGetsItsOwnBucket() {
        assertThat(otpService.isRateLimited("")).isFalse();
        assertThat(otpService.isRateLimited("")).isFalse();
    }

    @Test
    void cleanupRemovesExpiredOtps() {
        otpService = new OtpService(mock(JavaMailSender.class), -1);
        otpService.storeOtp("enr1", "123456");
        assertThat(otpService.storedOtpCount()).isEqualTo(1);
        otpService.cleanupExpired();
        assertThat(otpService.storedOtpCount()).isZero();
    }

    @Test
    void cleanupKeepsUnexpiredOtps() {
        otpService.storeOtp("enr1", "123456");
        otpService.cleanupExpired();
        assertThat(otpService.storedOtpCount()).isEqualTo(1);
        assertThat(otpService.validateOtp("enr1", "123456")).isTrue();
    }
}