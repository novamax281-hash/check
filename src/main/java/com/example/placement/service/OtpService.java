package com.example.placement.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

@Service
public class OtpService {

    private static final Logger log = LoggerFactory.getLogger(OtpService.class);

    static final int OTP_EXPIRY_SECONDS = 300;
    private static final int OTP_LENGTH = 6;
    private static final int RESEND_COOLDOWN_SECONDS = 30;
    static final int MAX_VERIFY_ATTEMPTS = 5;

    static final int MAX_OTPS_PER_IP_PER_HOUR = 10;
    static final int MAX_GLOBAL_OTPS_PER_HOUR = 500;
    static final int MAX_GLOBAL_OTPS_PER_DAY = 450;

    private static final long HOUR_SECONDS = 3600L;
    private static final long DAY_SECONDS = 86400L;

    private final JavaMailSender mailSender;
    private final SecureRandom random = new SecureRandom();
    private final int otpExpirySeconds;
    private final ConcurrentHashMap<String, OtpEntry> otpStore = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ConcurrentLinkedDeque<Long>> otpRateBuckets = new ConcurrentHashMap<>();

    @Autowired
    public OtpService(JavaMailSender mailSender) {
        this(mailSender, OTP_EXPIRY_SECONDS);
    }

    OtpService(JavaMailSender mailSender, int otpExpirySeconds) {
        this.mailSender = mailSender;
        this.otpExpirySeconds = otpExpirySeconds;
    }

    @Async("mailExecutor")
    public void sendOtp(String key, String to, String otp) {
        try {
            deliverMail(to, otp);
        } catch (RuntimeException ex) {
            log.warn("Could not deliver OTP email for {}: {}", maskEmail(to), ex.getMessage());
            removeOtp(key, otp);
        }
    }

    public boolean sendOtpSync(String to, String otp) {
        try {
            deliverMail(to, otp);
            return true;
        } catch (RuntimeException ex) {
            log.warn("Could not deliver OTP email synchronously for {}: {}", maskEmail(to), ex.getMessage());
            return false;
        }
    }

    public String generateOtp() {
        StringBuilder builder = new StringBuilder(OTP_LENGTH);
        for (int i = 0; i < OTP_LENGTH; i++) {
            builder.append(random.nextInt(10));
        }
        return builder.toString();
    }

    public void storeOtp(String key, String otp) {
        otpStore.put(key, new OtpEntry(otp, Instant.now().plusSeconds(otpExpirySeconds), Instant.now(), 0));
    }

    public void removeOtp(String key, String otp) {
        otpStore.computeIfPresent(key, (k, entry) -> entry.otp().equals(otp) ? null : entry);
    }

    public boolean canResend(String key) {
        OtpEntry entry = otpStore.get(key);
        if (entry == null) {
            return true;
        }
        if (Instant.now().isAfter(entry.expiresAt())) {
            return true;
        }
        return entry.requestedAt().plusSeconds(RESEND_COOLDOWN_SECONDS).isBefore(Instant.now());
    }

    public long resendWaitSeconds(String key) {
        OtpEntry entry = otpStore.get(key);
        if (entry == null) {
            return 0;
        }
        Instant allowedAt = entry.requestedAt().plusSeconds(RESEND_COOLDOWN_SECONDS);
        long wait = Instant.now().until(allowedAt, java.time.temporal.ChronoUnit.SECONDS);
        return Math.max(0, wait + 1);
    }

    public boolean validateOtp(String key, String otp) {
        if (otp == null || otp.isBlank()) {
            return false;
        }
        OtpEntry entry = otpStore.get(key);
        if (entry == null) {
            return false;
        }
        if (Instant.now().isAfter(entry.expiresAt())) {
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
        otpStore.remove(key);
        return true;
    }

    public boolean isRateLimited(String ip) {
        long nowSeconds = Instant.now().getEpochSecond();
        if (hitRateLimit("g:hour", MAX_GLOBAL_OTPS_PER_HOUR, HOUR_SECONDS, nowSeconds)) {
            return true;
        }
        if (hitRateLimit("g:day", MAX_GLOBAL_OTPS_PER_DAY, DAY_SECONDS, nowSeconds)) {
            return true;
        }
        return hitRateLimit("ip:" + (ip == null || ip.isBlank() ? "unknown" : ip),
                MAX_OTPS_PER_IP_PER_HOUR, HOUR_SECONDS, nowSeconds);
    }

    @Scheduled(fixedDelay = 60000)
    void cleanupExpired() {
        long nowSeconds = Instant.now().getEpochSecond();
        long cutoff = nowSeconds - DAY_SECONDS;
        otpStore.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(Instant.now()));
        otpRateBuckets.values().forEach(bucket -> bucket.removeIf(timestamp -> timestamp < cutoff));
        otpRateBuckets.values().removeIf(ConcurrentLinkedDeque::isEmpty);
    }

    int storedOtpCount() {
        return otpStore.size();
    }

    private boolean hitRateLimit(String bucketKey, int max, long windowSeconds, long nowSeconds) {
        final boolean[] limited = { false };
        otpRateBuckets.compute(bucketKey, (key, bucket) -> {
            ConcurrentLinkedDeque<Long> timestamps = bucket != null ? bucket : new ConcurrentLinkedDeque<>();
            long cutoff = nowSeconds - windowSeconds;
            timestamps.removeIf(timestamp -> timestamp < cutoff);
            if (timestamps.size() >= max) {
                limited[0] = true;
                return timestamps;
            }
            timestamps.addLast(nowSeconds);
            return timestamps;
        });
        return limited[0];
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
}