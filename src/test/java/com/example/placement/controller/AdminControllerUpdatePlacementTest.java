package com.example.placement.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import com.example.placement.entity.Placement;
import com.example.placement.repository.PlacementRepository;
import com.example.placement.repository.UsersRepository;
import com.example.placement.service.ApplicationService;
import com.example.placement.service.CloudinaryService;
import com.example.placement.service.CsvExportService;
import com.example.placement.service.CsvImportService;
import com.example.placement.service.EmailCsvService;

class AdminControllerUpdatePlacementTest {

    private PlacementRepository placementRepository;
    private AdminController controller;

    @BeforeEach
    void setUp() {
        placementRepository = mock(PlacementRepository.class);
        controller = new AdminController(placementRepository, mock(ApplicationService.class),
                mock(CsvExportService.class), mock(UsersRepository.class), mock(CloudinaryService.class),
                mock(CsvImportService.class), mock(EmailCsvService.class));
        when(placementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private Placement stored(String batch) {
        Placement existing = new Placement();
        existing.setId("job1");
        existing.setCompany("Acme");
        existing.setJobType("Full Time");
        existing.setJobRole("Backend Developer, Gen AI");
        existing.setBatch(batch);
        existing.setCourses("B.Tech");
        existing.setLocation("Pune");
        existing.setSkills("Java");
        existing.setEligibility("80%");
        existing.setOpenings("5");
        existing.setWebsite("https://acme.test");
        existing.setSalaryLpa("5.7");
        // Not present on the placement form - must survive the update untouched.
        existing.setStipend("25000");
        existing.setProgramFee("0");
        existing.setDuration("6 months");
        existing.setApplicationLink("https://acme.test/apply");
        existing.setCtc("12 LPA");
        return existing;
    }

    private Placement incoming() {
        Placement patch = new Placement();
        patch.setCompany("Acme");
        patch.setJobType("Full Time");
        return patch;
    }

    @Test
    void updateReturnsNotFoundForUnknownPlacement() {
        when(placementRepository.findById("missing")).thenReturn(Optional.empty());

        ResponseEntity<?> response = controller.updateJob("missing", incoming());

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void updateAcceptsMultipleEligibleBatchRanges() {
        when(placementRepository.findById("job1")).thenReturn(Optional.of(stored("2022-2026")));
        Placement patch = incoming();
        patch.setBatch("2022-2024, 2026-2028");

        controller.updateJob("job1", patch);

        assertEquals("2022-2024, 2026-2028", capture().getBatch());
    }

    @Test
    void updateRejectsReversedBatchRange() {
        when(placementRepository.findById("job1")).thenReturn(Optional.of(stored("2022-2026")));
        Placement patch = incoming();
        patch.setBatch("2028-2026");

        assertThrows(ResponseStatusException.class, () -> controller.updateJob("job1", patch));
    }

    @Test
    void updateRejectsSecondBatchRangeThatIsInvalid() {
        when(placementRepository.findById("job1")).thenReturn(Optional.of(stored("2022-2026")));
        Placement patch = incoming();
        patch.setBatch("2022-2024, nonsense");

        assertThrows(ResponseStatusException.class, () -> controller.updateJob("job1", patch));
    }

    @Test
    void clearingFormFieldsPersistsInsteadOfKeepingOldValues() {
        when(placementRepository.findById("job1")).thenReturn(Optional.of(stored("2022-2026")));
        Placement patch = incoming();
        patch.setJobRole("");
        patch.setCourses("");
        patch.setLocation("");
        patch.setSkills("");
        patch.setEligibility("");
        patch.setOpenings("");
        patch.setWebsite("");
        patch.setSalaryLpa("");
        patch.setBatch("");

        controller.updateJob("job1", patch);

        Placement result = capture();
        assertEquals("", result.getJobRole());
        assertEquals("", result.getCourses());
        assertEquals("", result.getLocation());
        assertEquals("", result.getSkills());
        assertEquals("", result.getEligibility());
        assertEquals("", result.getOpenings());
        assertEquals("", result.getWebsite());
        assertEquals("", result.getSalaryLpa());
        assertEquals("", result.getBatch());
    }

    @Test
    void fieldsAbsentFromTheFormAreNotWipedByAnUpdate() {
        when(placementRepository.findById("job1")).thenReturn(Optional.of(stored("2022-2026")));
        Placement patch = incoming();
        patch.setBatch("2023-2025");

        controller.updateJob("job1", patch);
        Placement result = capture();

        assertEquals("25000", result.getStipend());
        assertEquals("6 months", result.getDuration());
        assertEquals("https://acme.test/apply", result.getApplicationLink());
        assertEquals("12 LPA", result.getCtc());
        assertEquals("2023-2025", result.getBatch());
    }

    @Test
    void updateAppliesChangedValues() {
        when(placementRepository.findById("job1")).thenReturn(Optional.of(stored("2022-2026")));
        Placement patch = incoming();
        patch.setJobRole("Data Analyst");
        patch.setCourses("B.Tech, MCA");
        patch.setLocation("Bengaluru");
        patch.setBatch("2022-2024, 2026-2028");

        controller.updateJob("job1", patch);
        Placement result = capture();

        assertEquals("Data Analyst", result.getJobRole());
        assertEquals("B.Tech, MCA", result.getCourses());
        assertEquals("Bengaluru", result.getLocation());
        assertEquals("2022-2024, 2026-2028", result.getBatch());
    }

    private final ArgumentCaptor<Placement> savedPlacement = ArgumentCaptor.forClass(Placement.class);

    private Placement capture() {
        verify(placementRepository).save(savedPlacement.capture());
        return savedPlacement.getValue();
    }
}
