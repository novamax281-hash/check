package com.example.placement.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import com.example.placement.dto.ApplicationDetail;
import com.example.placement.repository.PlacementRepository;
import com.example.placement.repository.UsersRepository;
import com.example.placement.service.ApplicationService;
import com.example.placement.service.CloudinaryService;
import com.example.placement.service.CsvExportService;

class AdminControllerApplicationExportTest {

    private ApplicationService applicationService;
    private CsvExportService csvExportService;
    private AdminController controller;

    @BeforeEach
    void setUp() {
        applicationService = mock(ApplicationService.class);
        csvExportService = mock(CsvExportService.class);
        controller = new AdminController(mock(PlacementRepository.class), applicationService,
                csvExportService, mock(UsersRepository.class), mock(CloudinaryService.class));
    }

    @Test
    void exportFieldsExposesDefaultsOptionalsAndGroups() {
        Map<String, Object> payload = controller.applicationExportFields();

        assertEquals(CsvExportService.APPLICATION_DEFAULT_COLUMNS, payload.get("defaults"));
        assertEquals(CsvExportService.APPLICATION_OPTIONAL_COLUMNS, payload.get("optional"));

        @SuppressWarnings("unchecked")
        Map<String, List<String>> groups = (Map<String, List<String>>) payload.get("groups");
        assertEquals(List.of("Application Information", "Student Information", "Placement Information"),
                List.copyOf(groups.keySet()));
        assertTrue(groups.get("Student Information").contains("Selected Job Role"));
        assertTrue(groups.get("Placement Information").contains("Job Role"));
    }

    @Test
    void exportForwardsEveryTableFilterToTheService() throws Exception {
        when(applicationService.getAllApplications()).thenReturn(List.of());
        when(applicationService.filterDetails(anyList(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of());

        controller.exportApplications("job1", "IN_PROGRESS", 2, "rahul", "Northwind", "2022-2026",
                "CSE", java.time.LocalDate.of(2026, 1, 1), java.time.LocalDate.of(2026, 8, 31),
                null, new MockHttpServletResponse());

        verify(applicationService).filterDetails(anyList(), eq("rahul"), eq("IN_PROGRESS"), eq("job1"),
                eq("Northwind"), eq("2022-2026"), eq("CSE"),
                eq(java.time.LocalDate.of(2026, 1, 1)), eq(java.time.LocalDate.of(2026, 8, 31)));
    }

    @Test
    void exportPassesRequestedColumnsThrough() throws Exception {
        ApplicationDetail row = new ApplicationDetail();
        row.setApplicationId("app1");
        when(applicationService.getAllApplications()).thenReturn(List.of(row));
        when(applicationService.filterDetails(anyList(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(row));

        controller.exportApplications(null, null, null, null, null, null, null, null, null,
                "Company,Selected Job Role", new MockHttpServletResponse());

        verify(csvExportService).writeApplications(any(), eq(List.of(row)),
                eq(List.of("Company", "Selected Job Role")));
    }

    @Test
    void exportFallsBackToDefaultsForUnknownColumns() throws Exception {
        ApplicationDetail row = new ApplicationDetail();
        row.setApplicationId("app1");
        when(applicationService.getAllApplications()).thenReturn(List.of(row));
        when(applicationService.filterDetails(anyList(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(row));

        controller.exportApplications(null, null, null, null, null, null, null, null, null,
                "Totally Unknown", new MockHttpServletResponse());

        verify(csvExportService).writeApplications(any(), eq(List.of(row)),
                eq(CsvExportService.APPLICATION_DEFAULT_COLUMNS));
    }

    @Test
    void exportFiltersRowsByRound() throws Exception {
        ApplicationDetail first = new ApplicationDetail();
        first.setApplicationId("app1");
        first.setCurrentRound(1);
        ApplicationDetail second = new ApplicationDetail();
        second.setApplicationId("app2");
        second.setCurrentRound(2);
        when(applicationService.getAllApplications()).thenReturn(List.of(first, second));
        when(applicationService.filterDetails(anyList(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(first, second));

        controller.exportApplications(null, null, 2, null, null, null, null, null, null,
                null, new MockHttpServletResponse());

        verify(csvExportService).writeApplications(any(), eq(List.of(second)),
                eq(CsvExportService.APPLICATION_DEFAULT_COLUMNS));
    }
}
