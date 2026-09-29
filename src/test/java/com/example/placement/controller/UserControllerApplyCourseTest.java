package com.example.placement.controller;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;

import com.example.placement.entity.Placement;
import com.example.placement.entity.UsersEntries;
import com.example.placement.repository.PlacementRepository;
import com.example.placement.repository.UsersRepository;
import com.example.placement.service.ApplicationService;

class UserControllerApplyCourseTest {

    private UsersRepository usersRepository;
    private ApplicationService applicationService;
    private PlacementRepository placementRepository;
    private UserController controller;
    private Authentication authentication;
    private UsersEntries student;
    private Placement job;

    @BeforeEach
    void setUp() {
        usersRepository = mock(UsersRepository.class);
        applicationService = mock(ApplicationService.class);
        placementRepository = mock(PlacementRepository.class);
        controller = new UserController(usersRepository, applicationService, placementRepository);
        authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("ENR001");

        student = new UsersEntries();
        student.setId("student-1");
        student.setRole("Student");
        student.setStudentStatus("Active");
        when(usersRepository.findByEnrollmentno("ENR001")).thenReturn(student);

        job = new Placement();
        job.setId("job-1");
        job.setStatus("Open");
        job.setCourses("Full Stack, Data Science, Java");
        when(placementRepository.findById("job-1")).thenReturn(Optional.of(job));
        when(applicationService.isEligible(job, student)).thenReturn(true);
    }

    @Test
    void apply_rejectsCourseNotConfiguredForPlacement() {
        var response = controller.apply(authentication, Map.of(
                "jobId", "job-1",
                "resumeUrl", "https://example.com/resume.pdf",
                "selectedCourse", "Python"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(applicationService, never()).apply(any(), any(), any(), any());
    }

    @Test
    void applySavesCanonicalConfiguredCourse() {
        when(applicationService.apply("student-1", "job-1", "https://example.com/resume.pdf", "Java"))
                .thenReturn("applied");

        var response = controller.apply(authentication, Map.of(
                "jobId", "job-1",
                "resumeUrl", "https://example.com/resume.pdf",
                "selectedCourse", "java"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(applicationService).apply(eq("student-1"), eq("job-1"),
                eq("https://example.com/resume.pdf"), eq("Java"));
    }
}