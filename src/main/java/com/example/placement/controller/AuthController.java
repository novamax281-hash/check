package com.example.placement.controller;

import com.example.placement.dto.LoginRequest;
import com.example.placement.dto.LoginResponse;
import com.example.placement.dto.RegisterRequest;
import com.example.placement.entity.UsersEntries;
import com.example.placement.repository.UsersRepository;
import com.example.placement.service.OtpService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
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
import java.util.concurrent.RejectedExecutionException;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

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
    public ResponseEntity<LoginResponse> sendOtp(@Valid @RequestBody LoginRequest loginRequest,
                                                 HttpServletRequest request) {
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

            if (otpService.isRateLimited(clientIp(request))) {
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .body(new LoginResponse("Too many OTP requests right now. Please try again later.", ""));
            }

            String otp = otpService.generateOtp();
            otpService.storeOtp(loginRequest.getEnrollmentno(), otp);

            try {
                otpService.sendOtp(loginRequest.getEnrollmentno(), email, otp);
            } catch (RejectedExecutionException rejected) {
                if (!otpService.sendOtpSync(email, otp)) {
                    otpService.removeOtp(loginRequest.getEnrollmentno(), otp);
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(new LoginResponse("Could not send the OTP email right now. Please try again.", ""));
                }
            }

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
            if (user != null && user.getRole() != null
                    && (user.getRole().equalsIgnoreCase("admin")
                    || user.getRole().equalsIgnoreCase("coordinator"))) {
                role = "ADMIN";
            }
            return ResponseEntity.ok(new LoginResponse("Login Successful", role));
        } catch (RuntimeException exception) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new LoginResponse("Login failed, please try again", ""));
        }
    }

    @PostMapping("/register/send-otp")
    public ResponseEntity<LoginResponse> sendRegisterOtp(@Valid @RequestBody RegisterRequest registerRequest,
                                                         HttpServletRequest request) {
        String email = registerRequest.getEmail().trim().toLowerCase();
        if (!isLietEmail(email)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new LoginResponse("Registration is open only for @liet.in email addresses.", ""));
        }
        if (usersRepository.findByEnrollmentno(registerRequest.getEnrollmentno().trim()) != null) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new LoginResponse("This enrollment number is already registered. Sign in instead.", ""));
        }
        if (usersRepository.findByEmail(email) != null) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new LoginResponse("This email address is already registered. Sign in instead.", ""));
        }
        if (otpService.isRateLimited(clientIp(request))) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(new LoginResponse("Too many OTP requests right now. Please try again later.", ""));
        }
        String key = registerOtpKey(email);
        if (!otpService.canResend(key)) {
            long wait = otpService.resendWaitSeconds(key);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(new LoginResponse("Please wait " + wait + " seconds before requesting another OTP.", ""));
        }

        String otp = otpService.generateOtp();
        otpService.storeOtp(key, otp);

        try {
            otpService.sendOtp(key, email, otp);
        } catch (RejectedExecutionException rejected) {
            if (!otpService.sendOtpSync(email, otp)) {
                otpService.removeOtp(key, otp);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(new LoginResponse("Could not send the OTP email right now. Please try again.", ""));
            }
        }

        return ResponseEntity.ok(new LoginResponse("OTP sent to your college email", "", true, maskEmail(email)));
    }

    @PostMapping("/register/verify")
    public ResponseEntity<LoginResponse> verifyRegisterOtp(@Valid @RequestBody RegisterRequest registerRequest) {
        String email = registerRequest.getEmail().trim().toLowerCase();
        if (!isLietEmail(email)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new LoginResponse("Registration is open only for @liet.in email addresses.", ""));
        }
        if (!otpService.validateOtp(registerOtpKey(email), registerRequest.getOtp())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new LoginResponse("Invalid or expired OTP", ""));
        }
        if (usersRepository.findByEnrollmentno(registerRequest.getEnrollmentno().trim()) != null
                || usersRepository.findByEmail(email) != null) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new LoginResponse("Looks like this student is already registered. Sign in instead.", ""));
        }

        UsersEntries student = new UsersEntries(registerRequest.getEnrollmentno().trim(),
                registerRequest.getMobile().trim());
        student.setName(registerRequest.getName().trim());
        student.setEmail(email);
        student.setBatch(registerRequest.getBatch().trim());
        student.setTenthPercentage(registerRequest.getTenthPercentage());
        student.setTwelfthPercentage(registerRequest.getTwelfthPercentage());
        student.setRole("Student");
        usersRepository.save(student);

        return ResponseEntity.ok(new LoginResponse(
                "Registration successful. Sign in with your enrollment number and mobile.", "STUDENT"));
    }

    static boolean isLietEmail(String email) {
        return email != null && !email.isBlank() && email.trim().toLowerCase().endsWith("@liet.in");
    }

    private String registerOtpKey(String email) {
        return "reg:" + email;
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

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private boolean isMissing(String value) {
        return value == null || value.trim().isEmpty();
    }
}
