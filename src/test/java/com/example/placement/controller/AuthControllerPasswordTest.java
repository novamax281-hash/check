package com.example.placement.controller;

import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

import com.example.placement.dto.EmailRequest;
import com.example.placement.dto.LoginRequest;
import com.example.placement.dto.OtpVerifyRequest;
import com.example.placement.dto.PasswordResetRequest;
import com.example.placement.dto.RegisterRequest;
import com.example.placement.dto.StudentLoginRequest;
import com.example.placement.entity.UsersEntries;
import com.example.placement.repository.UsersRepository;
import com.example.placement.service.AuthenticationService;
import com.example.placement.service.OtpService;
import com.example.placement.service.RecaptchaService;

import jakarta.servlet.http.HttpSession;

class AuthControllerPasswordTest {

    private final AuthenticationManager authenticationManager = mock(AuthenticationManager.class);
    private final UsersRepository usersRepository = mock(UsersRepository.class);
    private final OtpService otpService = mock(OtpService.class);
    private final AuthenticationService authenticationService = mock(AuthenticationService.class);
    private final RecaptchaService recaptchaService = mock(RecaptchaService.class);
    private final AuthController controller = new AuthController(
            authenticationManager, usersRepository, otpService, authenticationService, recaptchaService, "test-site-key");

    @BeforeEach
    void allowCaptchaForExistingTests() {
        when(recaptchaService.verify(nullable(String.class))).thenReturn(true);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void studentPasswordLoginCreatesStudentSession() {
        UsersEntries student = student();
        StudentLoginRequest login = new StudentLoginRequest();
        login.setEmail("STUDENT@LIET.IN");
        login.setPassword("StudentPass123");
        when(authenticationService.authenticateStudent("student@liet.in", "StudentPass123"))
                .thenReturn(student);
        MockHttpServletRequest request = new MockHttpServletRequest();

        var response = controller.studentLogin(login, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getRole()).isEqualTo("STUDENT");
        HttpSession session = request.getSession(false);
        var context = (org.springframework.security.core.context.SecurityContext) session.getAttribute(
            HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        assertThat(context.getAuthentication().getName()).isEqualTo("2024CS01");
    }

    @Test
    void studentPasswordLoginStopsWhenCaptchaIsRejected() {
        StudentLoginRequest login = new StudentLoginRequest();
        login.setEmail("student@liet.in");
        login.setPassword("StudentPass123");
        login.setCaptchaResponse("expired-token");
        when(recaptchaService.verify("expired-token")).thenReturn(false);

        org.springframework.web.server.ResponseStatusException exception = org.junit.jupiter.api.Assertions
                .assertThrows(org.springframework.web.server.ResponseStatusException.class,
                        () -> controller.studentLogin(login, new MockHttpServletRequest()));

        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(exception.getReason()).contains("CAPTCHA verification failed");
        verify(authenticationService, never()).authenticateStudent(any(), any());
    }

    @Test
    void staffPasswordLoginCreatesAdminSession() {
        UsersEntries admin = new UsersEntries("STAFF1", "9000000001");
        admin.setEmail("staff@liet.in");
        admin.setRole("Admin");
        StudentLoginRequest login = new StudentLoginRequest();
        login.setEmail("STAFF@LIET.IN");
        login.setPassword("StaffPass123");
        when(authenticationService.authenticateStaff("staff@liet.in", "StaffPass123")).thenReturn(admin);
        MockHttpServletRequest request = new MockHttpServletRequest();

        var response = controller.staffLogin(login, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(Objects.requireNonNull(response.getBody()).getRole()).isEqualTo("ADMIN");
        HttpSession session = Objects.requireNonNull(request.getSession(false));
        var context = (org.springframework.security.core.context.SecurityContext) Objects.requireNonNull(
            session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY));
        assertThat(context.getAuthentication().getAuthorities()).extracting("authority")
                .containsExactly("ROLE_ADMIN");
    }

    @Test
    void legacyOtpLoginRejectsStudentAccounts() {
        LoginRequest login = new LoginRequest();
        login.setEnrollmentno("2024CS01");
        login.setMobile("9000000000");
        when(authenticationManager.authenticate(any())).thenReturn(mock(Authentication.class));
        when(usersRepository.findByEnrollmentno("2024CS01")).thenReturn(student());

        var response = controller.sendOtp(login, new MockHttpServletRequest());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(otpService, never()).requestOtp(any(), any());
    }

    @Test
    void staffCanStillRequestLegacyOtp() {
        LoginRequest login = new LoginRequest();
        login.setEnrollmentno("STAFF1");
        login.setMobile("9000000001");
        UsersEntries staff = new UsersEntries("STAFF1", "9000000001");
        staff.setRole("Admin");
        staff.setEmail("staff@liet.in");
        when(authenticationManager.authenticate(any())).thenReturn(mock(Authentication.class));
        when(usersRepository.findByEnrollmentno("STAFF1")).thenReturn(staff);
        when(otpService.requestOtp("STAFF1", "staff@liet.in"))
            .thenReturn(new OtpService.OtpRequestResult(OtpService.RequestStatus.SENT, 4, 0));

        var response = controller.sendOtp(login, new MockHttpServletRequest());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().isOtpRequired()).isTrue();
        verify(otpService).requestOtp("STAFF1", "staff@liet.in");
    }

    @Test
    void registrationPersistsPasswordThroughHashingService() {
        RegisterRequest registration = new RegisterRequest();
        registration.setName("Test Student");
        registration.setEnrollmentno("2024CS02");
        registration.setEmail("student@liet.in");
        registration.setMobile("9000000000");
        registration.setBatch("2024-2028");
        registration.setTenthPercentage(80.0);
        registration.setTwelfthPercentage(82.0);
        registration.setPassword("StudentPass123");
        registration.setOtp("123456");
        when(otpService.validateOtp("reg:student@liet.in", "123456")).thenReturn(true);
        when(authenticationService.saveStudentPassword(any(UsersEntries.class), eq("StudentPass123")))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = controller.verifyRegisterOtp(registration);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(authenticationService).saveStudentPassword(
                org.mockito.ArgumentMatchers.argThat(user -> user.getEmail().equals("student@liet.in")
                        && user.getEnrollmentno().equals("2024CS02")), eq("StudentPass123"));
    }

    @Test
    void resetRequiresValidOtpBeforeSavingPassword() {
        PasswordResetRequest reset = new PasswordResetRequest();
        reset.setEmail("student@liet.in");
        reset.setOtp("123456");
        reset.setNewPassword("NewStudentPass123");
        when(otpService.validateOtp("password-reset:student@liet.in", "123456")).thenReturn(false);

        var response = controller.resetPassword(reset);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(authenticationService, never()).savePassword(any(), any());
    }

    @Test
    void verifiesResetOtpBeforeAllowingPasswordEntry() {
        OtpVerifyRequest verifyRequest = new OtpVerifyRequest();
        verifyRequest.setEmail("student@liet.in");
        verifyRequest.setOtp("123456");
        UsersEntries student = student();
        when(usersRepository.findByEmailIgnoreCase("student@liet.in")).thenReturn(student);
        when(authenticationService.isStudent(student)).thenReturn(true);
        when(otpService.matchesOtp("password-reset:student@liet.in", "123456")).thenReturn(true);

        var response = controller.verifyPasswordResetOtp(verifyRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getMessage()).contains("Code verified");
        verify(authenticationService, never()).savePassword(any(), any());
    }

    @Test
    void staffCanVerifyPasswordResetOtp() {
        OtpVerifyRequest verifyRequest = new OtpVerifyRequest();
        verifyRequest.setEmail("staff@liet.in");
        verifyRequest.setOtp("123456");
        UsersEntries staff = new UsersEntries("STAFF1", "9000000001");
        staff.setRole("Admin");
        when(usersRepository.findByEmailIgnoreCase("staff@liet.in")).thenReturn(staff);
        when(authenticationService.isStudent(staff)).thenReturn(false);
        when(otpService.matchesOtp("password-reset:staff@liet.in", "123456")).thenReturn(true);

        var response = controller.verifyPasswordResetOtp(verifyRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(Objects.requireNonNull(response.getBody()).getRole()).isEqualTo("ADMIN");
    }

    @Test
    void resetOtpVerificationRequiresStudentAndMatchingCode() {
        OtpVerifyRequest verifyRequest = new OtpVerifyRequest();
        verifyRequest.setEmail("student@liet.in");
        verifyRequest.setOtp("123456");
        UsersEntries student = student();
        when(usersRepository.findByEmailIgnoreCase("student@liet.in")).thenReturn(student);
        when(authenticationService.isStudent(student)).thenReturn(true);
        when(otpService.matchesOtp("password-reset:student@liet.in", "123456")).thenReturn(true);

        var response = controller.verifyPasswordResetOtp(verifyRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getMessage()).contains("Code verified");
        verify(otpService).matchesOtp("password-reset:student@liet.in", "123456");
        verify(authenticationService, never()).savePassword(any(), any());
    }

    @Test
    void resetSavesNewPasswordOnlyAfterOtpVerification() {
        PasswordResetRequest reset = new PasswordResetRequest();
        reset.setEmail("student@liet.in");
        reset.setOtp("123456");
        reset.setNewPassword("NewStudentPass123");
        UsersEntries student = student();
        when(otpService.validateOtp("password-reset:student@liet.in", "123456")).thenReturn(true);
        when(usersRepository.findByEmailIgnoreCase("student@liet.in")).thenReturn(student);
        when(authenticationService.isStudent(student)).thenReturn(true);
        when(authenticationService.savePassword(student, "NewStudentPass123")).thenReturn(student);

        var response = controller.resetPassword(reset);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(authenticationService).savePassword(student, "NewStudentPass123");
    }

    @Test
    void staffCanResetPasswordAfterEmailOtpVerification() {
        PasswordResetRequest reset = new PasswordResetRequest();
        reset.setEmail("staff@liet.in");
        reset.setOtp("123456");
        reset.setNewPassword("NewStaffPass123");
        UsersEntries staff = new UsersEntries("STAFF1", "9000000001");
        staff.setEmail("staff@liet.in");
        staff.setRole("Admin");
        when(otpService.validateOtp("password-reset:staff@liet.in", "123456")).thenReturn(true);
        when(usersRepository.findByEmailIgnoreCase("staff@liet.in")).thenReturn(staff);
        when(authenticationService.isStudent(staff)).thenReturn(false);
        when(authenticationService.savePassword(staff, "NewStaffPass123")).thenReturn(staff);

        var response = controller.resetPassword(reset);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(Objects.requireNonNull(response.getBody()).getRole()).isEqualTo("ADMIN");
        verify(authenticationService).savePassword(staff, "NewStaffPass123");
    }

    @Test
    void unknownEmailIsReportedInsteadOfPretendingToSend() {
        EmailRequest request = new EmailRequest();
        request.setEmail("unknown@liet.in");
        when(usersRepository.findByEmailIgnoreCase("unknown@liet.in")).thenReturn(null);
        when(authenticationService.isStudent(null)).thenReturn(false);

        var response = controller.sendPasswordResetOtp(request, new MockHttpServletRequest());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getMessage())
            .isEqualTo("No account is registered with that email address.");
        verify(otpService, never()).requestOtp(any(), any());
    }

    @Test
    void staffEmailCannotBeUsedForStudentPasswordReset() {
        EmailRequest request = new EmailRequest();
        request.setEmail("staff@liet.in");
        UsersEntries staff = new UsersEntries("STAFF1", "9000000001");
        staff.setRole("Admin");
        staff.setEmail("staff@liet.in");
        when(usersRepository.findByEmailIgnoreCase("staff@liet.in")).thenReturn(staff);
        when(authenticationService.isStudent(staff)).thenReturn(false);
        when(otpService.requestOtp("password-reset:staff@liet.in", "staff@liet.in"))
            .thenReturn(new OtpService.OtpRequestResult(OtpService.RequestStatus.SENT, 4, 0));

        var response = controller.sendPasswordResetOtp(request, new MockHttpServletRequest());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(otpService).requestOtp("password-reset:staff@liet.in", "staff@liet.in");
    }

    @Test
    void mailDeliveryFailureIsSurfacedWithoutCountingAnAttempt() {
        EmailRequest request = new EmailRequest();
        request.setEmail("student@liet.in");
        when(usersRepository.findByEmailIgnoreCase("student@liet.in")).thenReturn(student());
        when(authenticationService.isStudent(any(UsersEntries.class))).thenReturn(true);
        when(otpService.requestOtp("password-reset:student@liet.in", "student@liet.in"))
            .thenReturn(new OtpService.OtpRequestResult(
                OtpService.RequestStatus.TEMPORARY_FAILURE, 5, 0));

        var response = controller.sendPasswordResetOtp(request, new MockHttpServletRequest());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().getMessage())
            .isEqualTo("OTP could not be sent. Please try again after some time.");
        assertThat(response.getBody().getOtpAttemptsRemaining()).isEqualTo(5);
    }

    @Test
    void knownStudentGetsResetCodeDeliveredSynchronously() {
        EmailRequest request = new EmailRequest();
        request.setEmail("STUDENT@LIET.IN");
        when(usersRepository.findByEmailIgnoreCase("student@liet.in")).thenReturn(student());
        when(authenticationService.isStudent(any(UsersEntries.class))).thenReturn(true);
        when(otpService.requestOtp("password-reset:student@liet.in", "student@liet.in"))
            .thenReturn(new OtpService.OtpRequestResult(OtpService.RequestStatus.SENT, 4, 0));

        var response = controller.sendPasswordResetOtp(request, new MockHttpServletRequest());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().isOtpRequired()).isTrue();
        assertThat(response.getBody().getMessage()).contains("st***@liet.in");
        assertThat(response.getBody().getOtpAttemptsRemaining()).isEqualTo(4);
        verify(otpService).requestOtp("password-reset:student@liet.in", "student@liet.in");
    }

    @Test
    void cooldownReturnsRetryAfterInsteadOfResending() {
        EmailRequest request = new EmailRequest();
        request.setEmail("student@liet.in");
        when(usersRepository.findByEmailIgnoreCase("student@liet.in")).thenReturn(student());
        when(authenticationService.isStudent(any(UsersEntries.class))).thenReturn(true);
        when(otpService.requestOtp("password-reset:student@liet.in", "student@liet.in"))
            .thenReturn(new OtpService.OtpRequestResult(OtpService.RequestStatus.COOLDOWN, 4, 42));

        var response = controller.sendPasswordResetOtp(request, new MockHttpServletRequest());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getBody().getMessage()).contains("42");
        assertThat(response.getBody().getRetryAfterSeconds()).isEqualTo(42);
    }

    private UsersEntries student() {
        UsersEntries student = new UsersEntries("2024CS01", "9000000000");
        student.setName("Test Student");
        student.setEmail("student@liet.in");
        student.setRole("Student");
        return student;
    }
}