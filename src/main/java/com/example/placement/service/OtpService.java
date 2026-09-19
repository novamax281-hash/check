package com.example.placement.service;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OtpService {

    private static final int OTP_EXPIRY_SECONDS = 300;
    private static final int OTP_LENGTH = 6;
    private static final int RESEND_COOLDOWN_SECONDS = 30;
    static final int MAX_VERIFY_ATTEMPTS = 5;

    private final JavaMailSender mailSender;
    private final SecureRandom random = new SecureRandom();
    private final ConcurrentHashMap<String, OtpEntry> otpStore = new ConcurrentHashMap<>();

    public OtpService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendOtp(String to, String otp) {
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
                        + "⏱  Valid for: " + (OTP_EXPIRY_SECONDS / 60) + " minutes\n"
                        + "🔒  Please do not share this code with anyone.\n\n"
                        + "If you did not request this OTP, please ignore this email.\n\n"
                        + "Thank you,\n"
                        + "Placement Portal Team\n"
                        + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        );
        mailSender.send(message);
    }

    public String generateOtp() {
        StringBuilder builder = new StringBuilder(OTP_LENGTH);
        for (int i = 0; i < OTP_LENGTH; i++) {
            builder.append(random.nextInt(10));
        }
        return builder.toString();
    }

    public void storeOtp(String key, String otp) {
        otpStore.put(key, new OtpEntry(otp, Instant.now().plusSeconds(OTP_EXPIRY_SECONDS), Instant.now(), 0));
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

    private record OtpEntry(String otp, Instant expiresAt, Instant requestedAt, int failedAttempts) {
    }
}
