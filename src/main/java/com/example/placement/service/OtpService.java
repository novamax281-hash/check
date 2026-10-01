package com.example.placement.service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class OtpService {

    private static final Logger log = LoggerFactory.getLogger(OtpService.class);

    static final int OTP_EXPIRY_SECONDS = 300;
    private static final int OTP_LENGTH = 6;
    static final int RESEND_COOLDOWN_SECONDS = 60;
    static final int MAX_OTP_REQUESTS_PER_24_HOURS = 5;
    static final int MAX_VERIFY_ATTEMPTS = 5;
    private static final long DAY_SECONDS = 86400L;
    private final JavaMailSender mailSender;
    private final Executor mailExecutor;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final int otpExpirySeconds;
    private final ConcurrentHashMap<String, OtpEntry> otpStore = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, RequestState> requestStates = new ConcurrentHashMap<>();
    private final AtomicLong smtpBlockedUntil = new AtomicLong();

    @Autowired
    public OtpService(JavaMailSender mailSender, @Qualifier("mailExecutor") Executor mailExecutor) {
        this(mailSender, OTP_EXPIRY_SECONDS, mailExecutor, Clock.systemUTC());
    }

    OtpService(JavaMailSender mailSender) {
        this(mailSender, OTP_EXPIRY_SECONDS, Runnable::run, Clock.systemUTC());
    }

    OtpService(JavaMailSender mailSender, int otpExpirySeconds) {
        this(mailSender, otpExpirySeconds, Runnable::run, Clock.systemUTC());
    }

    OtpService(JavaMailSender mailSender, int otpExpirySeconds, Executor mailExecutor, Clock clock) {
        this.mailSender = mailSender;
        this.otpExpirySeconds = otpExpirySeconds;
        this.mailExecutor = mailExecutor;
        this.clock = clock;
    }

    public enum RequestStatus {
        SENT,
        COOLDOWN,
        DAILY_LIMIT,
        IN_PROGRESS,
        TEMPORARY_FAILURE
    }

    public record OtpRequestResult(RequestStatus status, int attemptsRemaining, long retryAfterSeconds) {
    }

    public OtpRequestResult requestOtp(String otpKey, String to) {
        String identity = to == null ? "" : to.trim().toLowerCase(Locale.ROOT);
        Instant now = clock.instant();
        AtomicReference<OtpRequestResult> rejected = new AtomicReference<>();
        RequestState state = requestStates.compute(identity, (ignored, existing) -> {
            RequestState current = existing == null ? new RequestState() : existing;
            synchronized (current) {
                pruneSuccessfulRequests(current, now);
                int remaining = MAX_OTP_REQUESTS_PER_24_HOURS - current.successfulSends.size();
                if (remaining <= 0) {
                    rejected.set(new OtpRequestResult(RequestStatus.DAILY_LIMIT, 0,
                            dailyRetrySeconds(current, now)));
                } else if (current.inFlight) {
                    rejected.set(new OtpRequestResult(RequestStatus.IN_PROGRESS, remaining, 0));
                } else if (current.lastSentAt != null) {
                    Instant allowedAt = current.lastSentAt.plusSeconds(RESEND_COOLDOWN_SECONDS);
                    if (now.isBefore(allowedAt)) {
                        rejected.set(new OtpRequestResult(RequestStatus.COOLDOWN, remaining,
                                secondsUntil(now, allowedAt)));
                    }
                }
                if (rejected.get() == null && smtpBlockedUntil.get() > now.getEpochSecond()) {
                    rejected.set(new OtpRequestResult(RequestStatus.TEMPORARY_FAILURE, remaining, 0));
                }
                if (rejected.get() == null) {
                    current.inFlight = true;
                }
            }
            return current;
        });
        if (rejected.get() != null) return rejected.get();

        String otp = generateOtp();
        CompletableFuture<OtpRequestResult> result = new CompletableFuture<>();
        try {
            mailExecutor.execute(() -> deliverOtp(otpKey, to, otp, state, result));
            return result.join();
        } catch (RejectedExecutionException ex) {
            releaseRequest(state);
            log.warn("OTP email executor rejected a message for {}", maskEmail(to));
            return temporaryFailure(state);
        } catch (CompletionException ex) {
            releaseRequest(state);
            log.warn("Could not complete OTP delivery for {}", maskEmail(to), ex);
            return temporaryFailure(state);
        }
    }

    private void deliverOtp(String otpKey, String to, String otp, RequestState state,
                            CompletableFuture<OtpRequestResult> result) {
        if (smtpBlockedUntil.get() > clock.instant().getEpochSecond()) {
            releaseRequest(state);
            result.complete(temporaryFailure(state));
            return;
        }
        try {
            deliverMail(to, otp);
        } catch (RuntimeException ex) {
            if (isGmailDailyLimit(ex)) {
                blockSmtpUntilNextUtcDay();
            }
            releaseRequest(state);
            log.warn("Could not deliver OTP email for {}: {}", maskEmail(to), ex.getMessage());
            result.complete(temporaryFailure(state));
            return;
        }

        Instant sentAt = clock.instant();
        otpStore.put(otpKey, new OtpEntry(otp, sentAt.plusSeconds(otpExpirySeconds), sentAt, 0));
        int remaining;
        long retryAfter = 0;
        synchronized (state) {
            pruneSuccessfulRequests(state, sentAt);
            state.successfulSends.addLast(sentAt);
            state.lastSentAt = sentAt;
            state.inFlight = false;
            remaining = MAX_OTP_REQUESTS_PER_24_HOURS - state.successfulSends.size();
            if (remaining == 0) retryAfter = dailyRetrySeconds(state, sentAt);
        }
        result.complete(new OtpRequestResult(RequestStatus.SENT, Math.max(0, remaining), retryAfter));
    }

    private OtpRequestResult temporaryFailure(RequestState state) {
        synchronized (state) {
            pruneSuccessfulRequests(state, clock.instant());
            return new OtpRequestResult(RequestStatus.TEMPORARY_FAILURE,
                    Math.max(0, MAX_OTP_REQUESTS_PER_24_HOURS - state.successfulSends.size()), 0);
        }
    }

    private void releaseRequest(RequestState state) {
        synchronized (state) {
            state.inFlight = false;
        }
    }

    private void pruneSuccessfulRequests(RequestState state, Instant now) {
        Instant cutoff = now.minusSeconds(DAY_SECONDS);
        while (!state.successfulSends.isEmpty() && !state.successfulSends.peekFirst().isAfter(cutoff)) {
            state.successfulSends.removeFirst();
        }
    }

    private long dailyRetrySeconds(RequestState state, Instant now) {
        Instant oldestExpiresAt = state.successfulSends.peekFirst().plusSeconds(DAY_SECONDS);
        return secondsUntil(now, oldestExpiresAt);
    }

    private long secondsUntil(Instant now, Instant target) {
        return Math.max(1, (Duration.between(now, target).toMillis() + 999) / 1000);
    }

    private boolean isGmailDailyLimit(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (containsGmailDailyLimit(current.getMessage())) {
                return true;
            }
            if (current instanceof MailSendException mailFailure && mailFailure.getFailedMessages() != null
                    && mailFailure.getFailedMessages().values().stream()
                    .anyMatch(this::hasGmailDailyLimitCause)) return true;
        }
        return false;
    }

    private boolean hasGmailDailyLimitCause(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (containsGmailDailyLimit(current.getMessage())) return true;
        }
        return false;
    }

    private boolean containsGmailDailyLimit(String message) {
        if (message == null) return false;
        String normalized = message.toLowerCase(Locale.ROOT);
        return normalized.contains("550 5.4.5") || normalized.contains("5.4.5")
                || normalized.contains("daily user sending limit exceeded");
    }

    private void blockSmtpUntilNextUtcDay() {
        long blockedUntil = LocalDate.now(clock.withZone(ZoneOffset.UTC)).plusDays(1)
                .atStartOfDay(ZoneOffset.UTC).toEpochSecond();
        smtpBlockedUntil.accumulateAndGet(blockedUntil, Math::max);
    }

    public String generateOtp() {
        StringBuilder builder = new StringBuilder(OTP_LENGTH);
        for (int i = 0; i < OTP_LENGTH; i++) {
            builder.append(random.nextInt(10));
        }
        return builder.toString();
    }

    public void storeOtp(String key, String otp) {
        Instant now = clock.instant();
        otpStore.put(key, new OtpEntry(otp, now.plusSeconds(otpExpirySeconds), now, 0));
    }

    public void removeOtp(String key, String otp) {
        otpStore.computeIfPresent(key, (k, entry) -> entry.otp().equals(otp) ? null : entry);
    }

    public boolean canResend(String key) {
        OtpEntry entry = otpStore.get(key);
        if (entry == null) {
            return true;
        }
        Instant now = clock.instant();
        if (now.isAfter(entry.expiresAt())) {
            return true;
        }
        return entry.requestedAt().plusSeconds(RESEND_COOLDOWN_SECONDS).isBefore(now);
    }

    public long resendWaitSeconds(String key) {
        OtpEntry entry = otpStore.get(key);
        if (entry == null) {
            return 0;
        }
        Instant allowedAt = entry.requestedAt().plusSeconds(RESEND_COOLDOWN_SECONDS);
        long wait = clock.instant().until(allowedAt, java.time.temporal.ChronoUnit.SECONDS);
        return Math.max(0, wait + 1);
    }

    public boolean validateOtp(String key, String otp) {
        return verifyOtp(key, otp, true);
    }

    public boolean matchesOtp(String key, String otp) {
        return verifyOtp(key, otp, false);
    }

    private boolean verifyOtp(String key, String otp, boolean consumeOnSuccess) {
        if (otp == null || otp.isBlank()) {
            return false;
        }
        OtpEntry entry = otpStore.get(key);
        if (entry == null) {
            return false;
        }
        if (clock.instant().isAfter(entry.expiresAt())) {
            otpStore.remove(key);
            return false;
        }
        if (!entry.otp().equals(otp.trim())) {
            int failed = entry.failedAttempts() + 1;
            if (failed >= MAX_VERIFY_ATTEMPTS) {
                otpStore.remove(key);
            } else {
                otpStore.put(key, new OtpEntry(entry.otp(), entry.expiresAt(), entry.requestedAt(), failed));
            }
            return false;
        }
        if (consumeOnSuccess) {
            otpStore.remove(key);
        }
        return true;
    }

    @Scheduled(fixedDelay = 60000)
    void cleanupExpired() {
        Instant now = clock.instant();
        otpStore.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
        requestStates.keySet().forEach(key -> requestStates.computeIfPresent(key, (ignored, state) -> {
            synchronized (state) {
                pruneSuccessfulRequests(state, now);
                return !state.inFlight && state.successfulSends.isEmpty() ? null : state;
            }
        }));
    }

    int storedOtpCount() {
        return otpStore.size();
    }

    private void deliverMail(String to, String otp) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject("Your OTP Code");
        message.setText(
                "━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
                        + "       PLACEMENT PORTAL\n"
                        + "       OTP VERIFICATION\n"
                        + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n"
                        + "Hello,\n\n"
                        + "Use the following One-Time Password (OTP) "
                        + "to complete your login:\n\n"
                        + "              " + otp + "\n\n"
                        + "⏱  Valid for: " + (otpExpirySeconds / 60) + " minutes\n"
                        + "🔒  Please do not share this code with anyone.\n\n"
                        + "If you did not request this OTP, please ignore this email.\n\n"
                        + "Thank you,\n"
                        + "Placement Portal Team\n"
                        + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        );
        mailSender.send(message);
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return email;
        }
        int at = email.indexOf('@');
        String local = email.substring(0, at);
        String domain = email.substring(at);
        if (local.length() <= 2) {
            return local.charAt(0) + "***" + domain;
        }
        return local.substring(0, 2) + "***" + domain;
    }

    private record OtpEntry(String otp, Instant expiresAt, Instant requestedAt, int failedAttempts) {
    }

    private static final class RequestState {
        private final Deque<Instant> successfulSends = new ArrayDeque<>();
        private Instant lastSentAt;
        private boolean inFlight;
    }
}