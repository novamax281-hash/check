package com.example.placement.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

import com.example.placement.entity.UsersEntries;
import com.example.placement.repository.UsersRepository;

class ProfileControllerSgpaTest {

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

    private UsersEntries payload(Double... sgpAs) {
        UsersEntries update = new UsersEntries();
        update.setSgpas(Arrays.asList(sgpAs));
        return update;
    }

    @Test
    void overallCgpaIsAverageOfEnteredSemesterSgpas() {
        controller.updateProfile(authentication, payload(8.0, 9.0, 7.5));

        assertEquals(Arrays.asList(8.0, 9.0, 7.5), stored.getSgpas());
        assertEquals(8.17, stored.getCgpa());
    }

    @Test
    void blankSemestersAreIgnoredInTheAverage() {
        controller.updateProfile(authentication, payload(8.0, null, 9.0, null));

        assertEquals(Arrays.asList(8.0, null, 9.0, null), stored.getSgpas());
        assertEquals(8.5, stored.getCgpa());
    }

    @Test
    void allSemesterValuesAreClearedWhenEveryInputIsBlank() {
        stored.setCgpa(8.9);
        controller.updateProfile(authentication, payload(null, null, null));

        assertEquals(Arrays.asList(null, null, null), stored.getSgpas());
        assertNull(stored.getCgpa());
    }

    @Test
    void existingOverallCgpaSurvivesWhenNoSgpasAreSubmitted() {
        stored.setCgpa(8.9);
        UsersEntries update = new UsersEntries();
        update.setBranch("CSE");

        controller.updateProfile(authentication, update);

        assertEquals(8.9, stored.getCgpa());
        assertEquals("CSE", stored.getBranch());
    }

    @Test
    void storedSgpAsRoundToTwoDecimals() {
        controller.updateProfile(authentication, payload(7.3333, 8.0));

        assertEquals(Arrays.asList(7.33, 8.0), stored.getSgpas());
        assertEquals(7.67, stored.getCgpa());
    }

    @Test
    void savedProfileIsReturnedToTheClient() {
        UsersEntries result = controller.updateProfile(authentication, payload(9.0, 9.0));

        assertEquals(9.0, result.getCgpa());
        verify(usersRepository).save(stored);
    }
}
