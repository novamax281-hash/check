package com.example.placement.controller;

import java.util.List;
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

    private static final String RESUME = "https://example.com/resume.pdf";

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
        job.setJobRole("Backend Developer, Gen AI");
        when(placementRepository.findById("job-1")).thenReturn(Optional.of(job));
        when(applicationService.isEligible(job, student)).thenReturn(true);
    }

    @Test
    void apply_rejectsCourseNotConfiguredForPlacement() {
        var response = controller.apply(authentication, Map.of(
                "jobId", "job-1",
                "resumeUrl", RESUME,
                "selectedCourse", "Python",
                "selectedRole", "Gen AI"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(applicationService, never()).apply(any(), any(), any(), any(), any());
    }

    @Test
    void apply_rejectsRoleNotConfiguredForPlacement() {
        var response = controller.apply(authentication, Map.of(
                "jobId", "job-1",
                "resumeUrl", RESUME,
                "selectedCourse", "Java",
                "selectedRole", "Marketing"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(applicationService, never()).apply(any(), any(), any(), any(), any());
    }

    @Test
    void apply_requiresRoleWhenPlacementConfiguresRoles() {
        var response = controller.apply(authentication, Map.of(
                "jobId", "job-1",
                "resumeUrl", RESUME,
                "selectedCourse", "Java"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(applicationService, never()).apply(any(), any(), any(), any(), any());
    }

    @Test
    void apply_requiresCourseWhenPlacementConfiguresCourses() {
        var response = controller.apply(authentication, Map.of(
                "jobId", "job-1",
                "resumeUrl", RESUME,
                "selectedRole", "Gen AI"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(applicationService, never()).apply(any(), any(), any(), any(), any());
    }

    @Test
    void applySavesCanonicalConfiguredCourse() {
        when(applicationService.apply("student-1", "job-1", RESUME, "Java", "Gen AI"))
                .thenReturn("applied");

        var response = controller.apply(authentication, Map.of(
                "jobId", "job-1",
                "resumeUrl", RESUME,
                "selectedCourse", "java",
                "selectedRole", "gen ai"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(applicationService).apply(eq("student-1"), eq("job-1"),
                eq(RESUME), eq("Java"), eq("Gen AI"));
    }

    @Test
    void applyStoresRoleAndCourseIndependently() {
        when(applicationService.apply("student-1", "job-1", RESUME, "Data Science", "Backend Developer"))
                .thenReturn("applied");

        var response = controller.apply(authentication, Map.of(
                "jobId", "job-1",
                "resumeUrl", RESUME,
                "selectedCourse", "Data Science",
                "selectedRole", "Backend Developer"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(applicationService).apply(eq("student-1"), eq("job-1"),
                eq(RESUME), eq("Data Science"), eq("Backend Developer"));
    }

    @Test
    void applySkipsRoleValidationWhenPlacementHasNoConfiguredRoles() {
        job.setJobRole(null);
        when(applicationService.apply("student-1", "job-1", RESUME, "Java", null))
                .thenReturn("applied");

        var response = controller.apply(authentication, Map.of(
                "jobId", "job-1",
                "resumeUrl", RESUME,
                "selectedCourse", "Java"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(applicationService).apply(eq("student-1"), eq("job-1"),
                eq(RESUME), eq("Java"), eq((String) null));
    }

    @Test
    void placementsViewReturnsOnlyCapablePlacements() {
        Placement accessible = new Placement();
        accessible.setId("job-capable");
        accessible.setStatus("Open");
        Placement notCapable = new Placement();
        notCapable.setId("job-not-capable");
        notCapable.setStatus("Open");
        Placement closed = new Placement();
        closed.setId("job-closed");
        closed.setStatus("Closed");
        when(placementRepository.findVisibleJobs()).thenReturn(List.of(accessible, notCapable, closed));
        when(applicationService.isEligible(accessible, student)).thenReturn(true);
        when(applicationService.isEligible(notCapable, student)).thenReturn(false);

        var response = controller.getAllPlacements(authentication);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        List<String> ids = response.getBody().stream().map(Placement::getId).toList();
        assertEquals(List.of("job-capable"), ids);
    }

    @Test
    void placementsViewReturnsEmptyWhenStudentHasNoProfile() {
        when(usersRepository.findByEnrollmentno("ENR001")).thenReturn(null);

        var response = controller.getAllPlacements(authentication);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }
}
