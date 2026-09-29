package com.example.placement.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

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
    void acceptedMailStoresAnOtpAndReturnsRemainingAttempts() {
        JavaMailSender sender = mock(JavaMailSender.class);
        OtpService service = new OtpService(sender);

        OtpService.OtpRequestResult result = service.requestOtp("reset:user@liet.in", "user@liet.in");

        assertThat(result.status()).isEqualTo(OtpService.RequestStatus.SENT);
        assertThat(result.attemptsRemaining()).isEqualTo(4);
        assertThat(service.storedOtpCount()).isEqualTo(1);
        verify(sender).send(any(SimpleMailMessage.class));
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
    void successfulSendsEnforcePerUserDailyLimitAndCooldown() {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-29T10:00:00Z"));
        JavaMailSender sender = mock(JavaMailSender.class);
        OtpService service = new OtpService(sender, OtpService.OTP_EXPIRY_SECONDS, Runnable::run, clock);

        for (int sent = 0; sent < OtpService.MAX_OTP_REQUESTS_PER_24_HOURS; sent++) {
            OtpService.OtpRequestResult result = service.requestOtp("login:user", "user@liet.in");
            assertThat(result.status()).isEqualTo(OtpService.RequestStatus.SENT);
            assertThat(result.attemptsRemaining()).isEqualTo(4 - sent);
            if (sent == OtpService.MAX_OTP_REQUESTS_PER_24_HOURS - 1) {
                assertThat(result.retryAfterSeconds()).isEqualTo(
                        86400 - (4 * OtpService.RESEND_COOLDOWN_SECONDS));
            }
            if (sent == 0) {
                OtpService.OtpRequestResult cooldown = service.requestOtp("login:user", "user@liet.in");
                assertThat(cooldown.status()).isEqualTo(OtpService.RequestStatus.COOLDOWN);
                assertThat(cooldown.retryAfterSeconds()).isEqualTo(60);
            }
            if (sent < OtpService.MAX_OTP_REQUESTS_PER_24_HOURS - 1) {
                clock.advanceSeconds(OtpService.RESEND_COOLDOWN_SECONDS);
            }
        }

        OtpService.OtpRequestResult limited = service.requestOtp("login:user", "user@liet.in");
        assertThat(limited.status()).isEqualTo(OtpService.RequestStatus.DAILY_LIMIT);
        assertThat(limited.attemptsRemaining()).isZero();
        verify(sender, times(5)).send(any(SimpleMailMessage.class));
    }

    @Test
    void rollingWindowReleasesAnAttemptAfterTwentyFourHours() {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-29T10:00:00Z"));
        OtpService service = new OtpService(mock(JavaMailSender.class), OtpService.OTP_EXPIRY_SECONDS,
                Runnable::run, clock);
        for (int sent = 0; sent < OtpService.MAX_OTP_REQUESTS_PER_24_HOURS; sent++) {
            assertThat(service.requestOtp("login:user", "user@liet.in").status())
                    .isEqualTo(OtpService.RequestStatus.SENT);
            if (sent < OtpService.MAX_OTP_REQUESTS_PER_24_HOURS - 1) {
                clock.advanceSeconds(OtpService.RESEND_COOLDOWN_SECONDS);
            }
        }

        clock.advanceSeconds(86400 - (4 * OtpService.RESEND_COOLDOWN_SECONDS));

        OtpService.OtpRequestResult result = service.requestOtp("login:user", "user@liet.in");
        assertThat(result.status()).isEqualTo(OtpService.RequestStatus.SENT);
        assertThat(result.attemptsRemaining()).isZero();
    }

    @Test
    void failedDeliveryDoesNotCountAndCanBeRetriedImmediately() {
        JavaMailSender sender = mock(JavaMailSender.class);
        doThrow(new MailSendException("smtp down")).doNothing()
                .when(sender).send(any(SimpleMailMessage.class));
        OtpService service = new OtpService(sender);

        OtpService.OtpRequestResult failed = service.requestOtp("reset:user", "user@liet.in");
        assertThat(service.storedOtpCount()).isZero();
        OtpService.OtpRequestResult retried = service.requestOtp("reset:user", "user@liet.in");

        assertThat(failed.status()).isEqualTo(OtpService.RequestStatus.TEMPORARY_FAILURE);
        assertThat(failed.attemptsRemaining()).isEqualTo(5);
        assertThat(service.storedOtpCount()).isEqualTo(1);
        assertThat(retried.status()).isEqualTo(OtpService.RequestStatus.SENT);
        assertThat(retried.attemptsRemaining()).isEqualTo(4);
    }

    @Test
    void gmailDailyLimitStopsFurtherSmtpAttempts() {
        JavaMailSender sender = mock(JavaMailSender.class);
        doThrow(new MailSendException("550 5.4.5 Daily user sending limit exceeded"))
                .when(sender).send(any(SimpleMailMessage.class));
        OtpService service = new OtpService(sender);

        assertThat(service.requestOtp("login:first", "first@liet.in").status())
                .isEqualTo(OtpService.RequestStatus.TEMPORARY_FAILURE);
        assertThat(service.requestOtp("login:second", "second@liet.in").status())
                .isEqualTo(OtpService.RequestStatus.TEMPORARY_FAILURE);

        verify(sender, times(1)).send(any(SimpleMailMessage.class));
        assertThat(service.storedOtpCount()).isZero();
    }

    @Test
    void gmailDailyLimitSkipsAlreadyQueuedMailTasks() throws Exception {
        JavaMailSender sender = mock(JavaMailSender.class);
        CountDownLatch firstSendStarted = new CountDownLatch(1);
        CountDownLatch releaseFirstSend = new CountDownLatch(1);
        CountDownLatch bothTasksSubmitted = new CountDownLatch(2);
        AtomicInteger submittedTasks = new AtomicInteger();
        doAnswer(invocation -> {
            firstSendStarted.countDown();
            releaseFirstSend.await(2, TimeUnit.SECONDS);
            throw new MailSendException("550 5.4.5 Daily user sending limit exceeded");
        }).when(sender).send(any(SimpleMailMessage.class));
        ThreadPoolExecutor mailExecutor = new ThreadPoolExecutor(
                1, 1, 0, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
        OtpService service = new OtpService(sender, OtpService.OTP_EXPIRY_SECONDS, command -> {
            mailExecutor.execute(command);
            if (submittedTasks.incrementAndGet() <= 2) bothTasksSubmitted.countDown();
        }, Clock.systemUTC());
        ExecutorService callers = Executors.newFixedThreadPool(2);
        try {
            Future<OtpService.OtpRequestResult> first = callers.submit(
                    () -> service.requestOtp("login:first", "first@liet.in"));
            assertThat(firstSendStarted.await(2, TimeUnit.SECONDS)).isTrue();
            Future<OtpService.OtpRequestResult> second = callers.submit(
                    () -> service.requestOtp("login:second", "second@liet.in"));
            assertThat(bothTasksSubmitted.await(2, TimeUnit.SECONDS)).isTrue();
            releaseFirstSend.countDown();

            assertThat(first.get(2, TimeUnit.SECONDS).status())
                    .isEqualTo(OtpService.RequestStatus.TEMPORARY_FAILURE);
            assertThat(second.get(2, TimeUnit.SECONDS).status())
                    .isEqualTo(OtpService.RequestStatus.TEMPORARY_FAILURE);
            verify(sender, times(1)).send(any(SimpleMailMessage.class));
        } finally {
            releaseFirstSend.countDown();
            callers.shutdownNow();
            mailExecutor.shutdownNow();
        }
    }

    @Test
    void concurrentRequestsForOneUserOnlyStartOneEmail() throws Exception {
        JavaMailSender sender = mock(JavaMailSender.class);
        CountDownLatch sending = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(invocation -> {
            sending.countDown();
            release.await(2, TimeUnit.SECONDS);
            return null;
        }).when(sender).send(any(SimpleMailMessage.class));
        OtpService service = new OtpService(sender);
        ExecutorService callers = Executors.newSingleThreadExecutor();
        try {
            var first = callers.submit(() -> service.requestOtp("login:user", "user@liet.in"));
            assertThat(sending.await(2, TimeUnit.SECONDS)).isTrue();
            OtpService.OtpRequestResult concurrent = service.requestOtp("login:user", "USER@LIET.IN");
            assertThat(concurrent.status()).isEqualTo(OtpService.RequestStatus.IN_PROGRESS);
            release.countDown();
            assertThat(first.get(2, TimeUnit.SECONDS).status()).isEqualTo(OtpService.RequestStatus.SENT);
            verify(sender, times(1)).send(any(SimpleMailMessage.class));
        } finally {
            release.countDown();
            callers.shutdownNow();
        }
    }

    @Test
    void handlesOneThousandConcurrentDistinctUsers() throws Exception {
        JavaMailSender sender = mock(JavaMailSender.class);
        OtpService service = new OtpService(sender);
        ExecutorService callers = Executors.newFixedThreadPool(64);
        List<Future<OtpService.OtpRequestResult>> results = new ArrayList<>();
        try {
            for (int user = 0; user < 1000; user++) {
                int userNumber = user;
                results.add(callers.submit(() -> service.requestOtp(
                        "login:user" + userNumber, "user" + userNumber + "@liet.in")));
            }
            for (Future<OtpService.OtpRequestResult> result : results) {
                assertThat(result.get(10, TimeUnit.SECONDS).status()).isEqualTo(OtpService.RequestStatus.SENT);
            }
            verify(sender, times(1000)).send(any(SimpleMailMessage.class));
        } finally {
            callers.shutdownNow();
        }
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

    private static final class MutableClock extends Clock {
        private final AtomicReference<Instant> current;

        private MutableClock(Instant initial) {
            current = new AtomicReference<>(initial);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current.get();
        }

        private void advanceSeconds(long seconds) {
            current.updateAndGet(instant -> instant.plusSeconds(seconds));
        }
    }
}