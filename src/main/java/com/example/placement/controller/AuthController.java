package com.example.placement.controller;

import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.validation.FieldError;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.example.placement.dto.EmailRequest;
import com.example.placement.dto.LoginRequest;
import com.example.placement.dto.LoginResponse;
import com.example.placement.dto.OtpVerifyRequest;
import com.example.placement.dto.PasswordResetRequest;
import com.example.placement.dto.RegisterRequest;
import com.example.placement.dto.StudentLoginRequest;
import com.example.placement.entity.UsersEntries;
import com.example.placement.repository.UsersRepository;
import com.example.placement.service.AuthenticationService;
import com.example.placement.service.OtpService;
import com.example.placement.service.RecaptchaService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UsersRepository usersRepository;
    private final OtpService otpService;
    private final AuthenticationService authenticationService;
    private final RecaptchaService recaptchaService;
    private final String recaptchaSiteKey;

    public AuthController(AuthenticationManager authenticationManager,
                          UsersRepository usersRepository,
                          OtpService otpService,
                          AuthenticationService authenticationService,
                          RecaptchaService recaptchaService,
                          @Value("${google.recaptcha.site-key:}") String recaptchaSiteKey) {
        this.authenticationManager = authenticationManager;
        this.usersRepository = usersRepository;
        this.otpService = otpService;
        this.authenticationService = authenticationService;
        this.recaptchaService = recaptchaService;
        this.recaptchaSiteKey = recaptchaSiteKey;
    }

    @GetMapping("/recaptcha-config")
    public Map<String, String> recaptchaConfig() {
        return Map.of("siteKey", recaptchaSiteKey == null ? "" : recaptchaSiteKey);
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
        requireCaptcha(loginRequest.getCaptchaResponse());
        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(
                    loginRequest.getEnrollmentno(), loginRequest.getMobile()));
        } catch (AuthenticationException badCredentials) {
            // Only credential failures are a 401. Previously the catch covered every
            // RuntimeException, so a Mongo or SMTP outage was reported to the user
            // as "Invalid enrollment number or mobile number" and the 503 handler
            // in GlobalExceptionHandler was never reached.
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new LoginResponse("Invalid enrollment number or mobile number", ""));
        }

        UsersEntries user = usersRepository.findByEnrollmentno(loginRequest.getEnrollmentno());
        if (!isStaff(user)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new LoginResponse("Use your student email and password to sign in.", ""));
        }
        String email = user != null ? user.getEmail() : null;
        if (isMissing(email)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new LoginResponse("No email address registered for this account. Contact your placement coordinator.", ""));
        }

        OtpService.OtpRequestResult result = otpService.requestOtp(loginRequest.getEnrollmentno(), email);
        return otpResponse(result, "OTP sent to your registered email", email);
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<LoginResponse> verifyOtp(@Valid @RequestBody LoginRequest loginRequest, HttpServletRequest request) {
        requireCaptcha(loginRequest.getCaptchaResponse());
        if (!otpService.validateOtp(loginRequest.getEnrollmentno(), loginRequest.getOtp())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new LoginResponse("Invalid or expired OTP", ""));
        }

        try {
            Authentication authentication = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(
                    loginRequest.getEnrollmentno(), loginRequest.getMobile()));
                UsersEntries user = usersRepository.findByEnrollmentno(loginRequest.getEnrollmentno());
                if (!isStaff(user)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new LoginResponse("Invalid or expired OTP", ""));
                }

                establishSession(authentication, request);
                String role = "ADMIN";
            return ResponseEntity.ok(new LoginResponse("Login Successful", role));
        } catch (RuntimeException exception) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new LoginResponse("Login failed, please try again", ""));
        }
    }

        @PostMapping("/student/login")
        public ResponseEntity<LoginResponse> studentLogin(@Valid @RequestBody StudentLoginRequest loginRequest,
                                   HttpServletRequest request) {
        requireCaptcha(loginRequest.getCaptchaResponse());
        UsersEntries user = authenticationService.authenticateStudent(
            loginRequest.getEmail().trim().toLowerCase(), loginRequest.getPassword());
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new LoginResponse("Invalid email or password", ""));
        }
        UserDetails principal = User.withUsername(user.getEnrollmentno())
            .password("")
            .authorities("ROLE_STUDENT")
            .build();
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
            principal, null, principal.getAuthorities());
        establishSession(authentication, request);
        return ResponseEntity.ok(new LoginResponse("Login successful", "STUDENT"));
        }

        @PostMapping("/staff/login")
        public ResponseEntity<LoginResponse> staffLogin(@Valid @RequestBody StudentLoginRequest loginRequest,
                                HttpServletRequest request) {
        requireCaptcha(loginRequest.getCaptchaResponse());
        UsersEntries user = authenticationService.authenticateStaff(
            loginRequest.getEmail().trim().toLowerCase(), loginRequest.getPassword());
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new LoginResponse("Invalid email or password", ""));
        }
        UserDetails principal = User.withUsername(user.getEnrollmentno())
            .password("")
            .authorities("ROLE_ADMIN")
            .build();
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
            principal, null, principal.getAuthorities());
        establishSession(authentication, request);
        return ResponseEntity.ok(new LoginResponse("Login successful", "ADMIN"));
        }

    @PostMapping("/register/send-otp")
    public ResponseEntity<LoginResponse> sendRegisterOtp(@Valid @RequestBody RegisterRequest registerRequest,
                                                         HttpServletRequest request) {
        requireCaptcha(registerRequest.getCaptchaResponse());
        String email = registerRequest.getEmail().trim().toLowerCase();
        if (!isLietEmail(email)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new LoginResponse("Registration is open only for @liet.in email addresses.", ""));
        }
        if (usersRepository.findByEnrollmentno(registerRequest.getEnrollmentno().trim()) != null) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new LoginResponse("This enrollment number is already registered. Sign in instead.", ""));
        }
        if (usersRepository.findByEmailIgnoreCase(email) != null) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new LoginResponse("This email address is already registered. Sign in instead.", ""));
        }
        String key = registerOtpKey(email);
        OtpService.OtpRequestResult result = otpService.requestOtp(key, email);
        return otpResponse(result, "OTP sent to your college email", email);
    }

    @PostMapping("/register/verify")
    public ResponseEntity<LoginResponse> verifyRegisterOtp(@Valid @RequestBody RegisterRequest registerRequest) {
        requireCaptcha(registerRequest.getCaptchaResponse());
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
                || usersRepository.findByEmailIgnoreCase(email) != null) {
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
        try {
            authenticationService.saveStudentPassword(student, registerRequest.getPassword());
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest()
                    .body(new LoginResponse("Password must be at most 72 UTF-8 bytes", ""));
        }

        return ResponseEntity.ok(new LoginResponse(
                "Registration successful. Sign in with your email and password.", "STUDENT"));
    }

    @PostMapping("/forgot-password/send-otp")
    public ResponseEntity<LoginResponse> sendPasswordResetOtp(@Valid @RequestBody EmailRequest emailRequest,
                                                               HttpServletRequest request) {
        requireCaptcha(emailRequest.getCaptchaResponse());
        String email = emailRequest.getEmail().trim().toLowerCase();
        String key = passwordResetKey(email);
        UsersEntries user = usersRepository.findByEmailIgnoreCase(email);
        if (!canResetPassword(user)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new LoginResponse("No account is registered with that email address.", ""));
        }

        OtpService.OtpRequestResult result = otpService.requestOtp(key, email);
        return otpResponse(result, "Password reset code sent to " + maskEmail(email), email);
    }

    @PostMapping("/forgot-password/reset")
    public ResponseEntity<LoginResponse> resetPassword(@Valid @RequestBody PasswordResetRequest resetRequest) {
        requireCaptcha(resetRequest.getCaptchaResponse());
        String email = resetRequest.getEmail().trim().toLowerCase();
        String key = passwordResetKey(email);
        if (!otpService.validateOtp(key, resetRequest.getOtp())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new LoginResponse("Invalid or expired OTP", ""));
        }
        UsersEntries user = usersRepository.findByEmailIgnoreCase(email);
        if (!canResetPassword(user)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new LoginResponse("Invalid or expired OTP", ""));
        }
        try {
            authenticationService.savePassword(user, resetRequest.getNewPassword());
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest()
                    .body(new LoginResponse("Password must be at most 72 UTF-8 bytes", ""));
        }
        return ResponseEntity.ok(new LoginResponse(
            "Password reset successfully. Sign in with your new password.",
            isStaff(user) ? "ADMIN" : "STUDENT"));
    }

    @PostMapping("/forgot-password/verify-otp")
    public ResponseEntity<LoginResponse> verifyPasswordResetOtp(@Valid @RequestBody OtpVerifyRequest verifyRequest) {
        requireCaptcha(verifyRequest.getCaptchaResponse());
        String email = verifyRequest.getEmail().trim().toLowerCase();
        String key = passwordResetKey(email);
        UsersEntries user = usersRepository.findByEmailIgnoreCase(email);
        if (!canResetPassword(user) || !otpService.matchesOtp(key, verifyRequest.getOtp())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new LoginResponse("Invalid or expired OTP", ""));
        }
        return ResponseEntity.ok(new LoginResponse("Code verified. Create your new password.",
            isStaff(user) ? "ADMIN" : "STUDENT"));
    }

    private ResponseEntity<LoginResponse> otpResponse(OtpService.OtpRequestResult result,
                                                       String successMessage, String email) {
        HttpStatus status;
        String message;
        boolean otpRequired = false;
        switch (result.status()) {
            case SENT -> {
                status = HttpStatus.OK;
                message = successMessage;
                otpRequired = true;
            }
            case COOLDOWN -> {
                status = HttpStatus.TOO_MANY_REQUESTS;
                message = "Please wait " + result.retryAfterSeconds()
                        + " seconds before requesting another OTP.";
            }
            case DAILY_LIMIT -> {
                status = HttpStatus.TOO_MANY_REQUESTS;
                message = "Daily OTP limit reached. Please try again tomorrow.";
            }
            case IN_PROGRESS -> {
                status = HttpStatus.TOO_MANY_REQUESTS;
                message = "An OTP request is already being processed. Please wait.";
            }
            case TEMPORARY_FAILURE -> {
                status = HttpStatus.SERVICE_UNAVAILABLE;
                message = "OTP could not be sent. Please try again after some time.";
            }
            default -> throw new IllegalStateException("Unhandled OTP request status");
        }

        LoginResponse response = new LoginResponse(message, "", otpRequired,
                email == null ? null : maskEmail(email));
        response.setOtpAttemptsRemaining(result.attemptsRemaining());
        response.setRetryAfterSeconds(result.retryAfterSeconds());
        return ResponseEntity.status(status).body(response);
    }

    private void establishSession(Authentication authentication, HttpServletRequest request) {
        // Logging in inside the controller bypasses Spring's SessionManagementFilter,
        // so the pre-authentication session is discarded here. Creating the session
        // only after that guarantees the client never keeps a session id it chose,
        // which is the classic session-fixation case.
        HttpSession preAuth = request.getSession(false);
        if (preAuth != null) {
            preAuth.invalidate();
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        request.getSession(true).setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
    }

    private boolean isStaff(UsersEntries user) {
        return user != null && user.getRole() != null
                && (user.getRole().equalsIgnoreCase("admin") || user.getRole().equalsIgnoreCase("coordinator"));
    }

    private boolean canResetPassword(UsersEntries user) {
        return authenticationService.isStudent(user) || isStaff(user);
    }

    private void requireCaptcha(String responseToken) {
        if (!recaptchaService.verify(responseToken)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "CAPTCHA verification failed. Please complete the CAPTCHA and try again.");
        }
    }

    static boolean isLietEmail(String email) {
        return email != null && !email.isBlank() && email.trim().toLowerCase().endsWith("@liet.in");
    }

    private String registerOtpKey(String email) {
        return "reg:" + email;
    }

    private String passwordResetKey(String email) {
        return "password-reset:" + email;
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
