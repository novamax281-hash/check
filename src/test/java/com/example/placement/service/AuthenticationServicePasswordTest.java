package com.example.placement.service;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.example.placement.entity.UsersEntries;
import com.example.placement.repository.UsersRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

class AuthenticationServicePasswordTest {

    private final UsersRepository usersRepository = mock(UsersRepository.class);
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private final AuthenticationService authenticationService =
            new AuthenticationService(usersRepository, passwordEncoder);
    private UsersEntries student;

    @BeforeEach
    void setUp() {
        student = new UsersEntries("2024CS01", "9000000000");
        student.setEmail("student@liet.in");
        student.setRole("Student");
        student.setPasswordHash(passwordEncoder.encode("StudentPass123"));
        when(usersRepository.findByEmailIgnoreCase("student@liet.in")).thenReturn(student);
    }

    @Test
    void authenticatesStudentByCaseInsensitiveEmailAndPassword() {
        assertThat(authenticationService.authenticateStudent(" Student@LIET.IN ", "StudentPass123"))
                .isSameAs(student);
    }

    @Test
    void rejectsIncorrectPasswordAndStaffAccounts() {
        assertThat(authenticationService.authenticateStudent("student@liet.in", "wrong-pass")).isNull();

        UsersEntries admin = new UsersEntries("ADMIN", "9000000001");
        admin.setEmail("admin@liet.in");
        admin.setRole("Admin");
        admin.setPasswordHash(passwordEncoder.encode("StudentPass123"));
        when(usersRepository.findByEmailIgnoreCase("admin@liet.in")).thenReturn(admin);

        assertThat(authenticationService.authenticateStudent("admin@liet.in", "StudentPass123")).isNull();
        assertThat(authenticationService.authenticateStaff("admin@liet.in", "StudentPass123")).isSameAs(admin);
        assertThat(authenticationService.authenticateStaff("student@liet.in", "StudentPass123")).isNull();
    }

    @Test
    void savesOnlyBcryptHashAndDoesNotSerializeIt() throws Exception {
        when(usersRepository.save(student)).thenReturn(student);

        authenticationService.saveStudentPassword(student, "NewStudentPass123");

        assertThat(student.getPasswordHash()).startsWith("$2a$").isNotEqualTo("NewStudentPass123");
        assertThat(passwordEncoder.matches("NewStudentPass123", student.getPasswordHash())).isTrue();
        assertThat(new ObjectMapper().writeValueAsString(student)).doesNotContain("passwordHash", "NewStudentPass123");
        verify(usersRepository).save(student);
    }
}