package com.example.placement.controller;

import com.example.placement.dto.LoginRequest;
import com.example.placement.dto.LoginResponse;
import com.example.placement.entity.UsersEntries;
import com.example.placement.repository.UsersRepository;
import com.example.placement.service.OtpService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthenticationManager authenticationManager;
    private final UsersRepository usersRepository;
    private final OtpService otpService;

    public AuthController(AuthenticationManager authenticationManager,
                          UsersRepository usersRepository,
                          OtpService otpService) {
        this.authenticationManager = authenticationManager;
        this.usersRepository = usersRepository;
        this.otpService = otpService;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(". "));
        return ResponseEntity.badRequest().body(Map.of("message", message));
    }

    @PostMapping("/send-otp")
    public ResponseEntity<LoginResponse> sendOtp(@Valid @RequestBody LoginRequest loginRequest) {
        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(
                    loginRequest.getEnrollmentno(), loginRequest.getMobile()));

            UsersEntries user = usersRepository.findByEnrollmentno(loginRequest.getEnrollmentno());
            String email = user != null ? user.getEmail() : null;
            if (isMissing(email)) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(new LoginResponse("No email address registered for this account. Contact your placement coordinator.", ""));
            }

            if (!otpService.canResend(loginRequest.getEnrollmentno())) {
                long wait = otpService.resendWaitSeconds(loginRequest.getEnrollmentno());
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .body(new LoginResponse("Please wait " + wait + " seconds before requesting another OTP.", ""));
            }

            String otp = otpService.generateOtp();
            System.out.println(otp);
            try {
                otpService.sendOtp(email, otp);
            } catch (RuntimeException mailException) {
                log.warn("Could not deliver OTP email to {}", maskEmail(email), mailException);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(new LoginResponse("Could not send the OTP email right now. Please try again.", ""));
            }
            otpService.storeOtp(loginRequest.getEnrollmentno(), otp);

            return ResponseEntity.ok(new LoginResponse("OTP sent to your registered email", "", true, maskEmail(email)));
        } catch (RuntimeException exception) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new LoginResponse("Invalid enrollment number or mobile number", ""));
        }
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<LoginResponse> verifyOtp(@Valid @RequestBody LoginRequest loginRequest, HttpServletRequest request) {
        if (!otpService.validateOtp(loginRequest.getEnrollmentno(), loginRequest.getOtp())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new LoginResponse("Invalid or expired OTP", ""));
        }

        try {
            Authentication authentication = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(
                    loginRequest.getEnrollmentno(), loginRequest.getMobile()));

            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            request.getSession(true).setAttribute(
                    HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);

            UsersEntries user = usersRepository.findByEnrollmentno(loginRequest.getEnrollmentno());
            String role = "STUDENT";
            if (user != null) {
                if ("admin".equalsIgnoreCase(user.getRole())) {
                    role = "ADMIN";
                } else if ("coordinator".equalsIgnoreCase(user.getRole())) {
                    role = "COORDINATOR";
                }
            }
            return ResponseEntity.ok(new LoginResponse("Login Successful", role));
        } catch (RuntimeException exception) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new LoginResponse("Login failed, please try again", ""));
        }
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

    private boolean isMissing(String value) {
        return value == null || value.trim().isEmpty();
    }
}
