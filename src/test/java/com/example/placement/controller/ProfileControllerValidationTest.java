package com.example.placement.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import com.example.placement.entity.UsersEntries;
import com.example.placement.repository.UsersRepository;

/**
 * Batch/CGPA/percentage decide placement eligibility, so the self-service profile
 * endpoint must reject values outside the ranges accepted at registration.
 */
class ProfileControllerValidationTest {

    private UsersRepository usersRepository;
    private ProfileController controller;
    private Authentication authentication;
    private UsersEntries stored;

    @BeforeEach
    void setUp() {
        usersRepository = mock(UsersRepository.class);
        controller = new ProfileController(usersRepository);
        authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("ENR001");
        stored = new UsersEntries();
        stored.setId("student-1");
        stored.setEnrollmentno("ENR001");
        stored.setRole("Student");
        when(usersRepository.findByEnrollmentno("ENR001")).thenReturn(stored);
        when(usersRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private void assertRejected(UsersEntries update, String expectedFragment) {
        ResponseStatusException thrown = assertThrows(ResponseStatusException.class,
                () -> controller.updateProfile(authentication, update));
        assertEquals(HttpStatus.BAD_REQUEST, thrown.getStatusCode());
        org.junit.jupiter.api.Assertions.assertTrue(
                thrown.getReason().contains(expectedFragment),
                "reason was: " + thrown.getReason());
        verify(usersRepository, never()).save(any());
    }

    @Test
    void cgpaAboveTenIsRejected() {
        UsersEntries update = new UsersEntries();
        update.setCgpa(10.5);
        assertRejected(update, "CGPA");
    }

    @Test
    void negativeCgpaIsRejected() {
        UsersEntries update = new UsersEntries();
        update.setCgpa(-1.0);
        assertRejected(update, "CGPA");
    }

    @Test
    void percentageAboveHundredIsRejected() {
        UsersEntries update = new UsersEntries();
        update.setTenthPercentage(150.0);
        assertRejected(update, "10th percentage");

        update = new UsersEntries();
        update.setTwelfthPercentage(101.0);
        assertRejected(update, "12th percentage");

        update = new UsersEntries();
        update.setGraduationPercentage(999.0);
        assertRejected(update, "graduation percentage");
    }

    @Test
    void outOfRangeSemesterSgpaIsRejected() {
        UsersEntries update = new UsersEntries();
        update.setSgpas(List.of(8.5, 42.0));
        assertRejected(update, "Semester SGPA");
    }

    @Test
    void malformedBatchIsRejected() {
        UsersEntries update = new UsersEntries();
        update.setBatch("not-a-batch");
        assertRejected(update, "batch");
    }

    @Test
    void notANumberIsRejected() {
        UsersEntries update = new UsersEntries();
        update.setCgpa(Double.NaN);
        assertRejected(update, "CGPA");
    }

    @Test
    void studentCannotModifyAdminManagedFields() {
        UsersEntries studentUpdate = new UsersEntries();
        studentUpdate.setEmail("hacker@mail.com");
        studentUpdate.setPhoneno("1234567890");
        studentUpdate.setTenthPercentage(99.0);
        studentUpdate.setTwelfthPercentage(98.0);

        ResponseStatusException thrown = assertThrows(ResponseStatusException.class,
                () -> controller.updateProfile(authentication, studentUpdate));
        assertEquals(HttpStatus.FORBIDDEN, thrown.getStatusCode());
        assertEquals("Admin-managed fields cannot be changed by students", thrown.getReason());
        verify(usersRepository, never()).save(any());
    }

    @Test
    void validValuesAreStillAccepted() {
        UsersEntries update = new UsersEntries();
        update.setName("Asha");
        update.setCgpa(8.75);
        update.setTenthPercentage(78.0);
        update.setTwelfthPercentage(82.0);
        update.setGraduationPercentage(70.0);
        update.setBatch("2022-2026");
        update.setSgpas(List.of(8.5, 9.0));

        UsersEntries saved = controller.updateProfile(authentication, update);

        assertEquals("Asha", saved.getName());
        assertEquals(8.75, saved.getCgpa(), 0.001);
        assertEquals(8.75, saved.getCgpa(), 0.001);
        verify(usersRepository).save(stored);
    }
}
