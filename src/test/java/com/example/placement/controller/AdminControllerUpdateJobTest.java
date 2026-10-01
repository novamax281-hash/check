package com.example.placement.controller;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import com.example.placement.entity.JobDocument;
import com.example.placement.entity.Placement;
import com.example.placement.entity.PlacementRound;
import com.example.placement.repository.PlacementRepository;
import com.example.placement.repository.UsersRepository;
import com.example.placement.service.ApplicationService;
import com.example.placement.service.CloudinaryService;
import com.example.placement.service.CsvExportService;

class AdminControllerUpdateJobTest {

    private PlacementRepository placementRepository;
    private AdminController controller;
    private CloudinaryService cloudinaryService;

    @BeforeEach
    void setUp() {
        placementRepository = mock(PlacementRepository.class);
        ApplicationService applicationService = mock(ApplicationService.class);
        CsvExportService csvExportService = mock(CsvExportService.class);
        UsersRepository usersRepository = mock(UsersRepository.class);
        cloudinaryService = mock(CloudinaryService.class);
        controller = new AdminController(placementRepository, applicationService,
                csvExportService, usersRepository, cloudinaryService,
                mock(com.example.placement.service.CsvImportService.class),
                mock(com.example.placement.service.EmailCsvService.class));
    }

    private Placement existingJob() {
        Placement existing = new Placement();
        existing.setId("6a91cb044747f808b0ffaa0a");
        existing.setGmail_message_id("1a04980596e185d6");
        existing.setCompany("TechNova Solutions Pvt. Ltd.");
        existing.setJobType("Internship / Full-Time");
        existing.setStipend("₹15,000–₹25,000 per month");
        existing.setProgramFee("None");
        existing.setDuration("3–6 months");
        existing.setSkills("None");
        existing.setEligibility("Final-year students and recent graduates");
        existing.setApplicationDeadline("None");
        existing.setApplicationLink("None");
        existing.setJobDescription("TechNova Solutions is inviting applications.");
        existing.setLocation("Noida / Hybrid");
        existing.setOpenings("None");
        existing.setBatch("2026");
        existing.setBranch("B.Tech/B.E./BCA/MCA");
        existing.setApplication_date("08/26");
        existing.setStatus("Open");
        existing.setRounds(new ArrayList<>(List.of(new PlacementRound(1, "Online Assessment"))));
        return existing;
    }

    @Test
    void updateJob_mergesOnlyChangedCompanyAndPreservesEverythingElse() {
        Placement existing = existingJob();
        when(placementRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(placementRepository.save(any(Placement.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Placement incoming = new Placement();
        incoming.setCompany("TechNova India Pvt. Ltd.");
        incoming.setJobType("Internship / Full-Time");
        incoming.setStipend("₹15,000–₹25,000 per month");
        incoming.setLocation("Noida / Hybrid");
        incoming.setEligibility("Final-year students and recent graduates");
        incoming.setApplicationDeadline("None");
        incoming.setJobDescription("None");
        incoming.setOpenings("None");
        incoming.setSkills("None");
        incoming.setSalaryLpa("12.5");
        incoming.setMinTenth(75.0);
        incoming.setMinTwelfth(78.0);
        incoming.setMinGraduation(70.0);
        incoming.setMinCgpa(7.5);
        incoming.setStatus("Open");

        ResponseEntity<?> response = controller.updateJob(existing.getId(), incoming);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Placement saved = (Placement) response.getBody();
        assertNotNull(saved);
        assertEquals("6a91cb044747f808b0ffaa0a", saved.getId());
        assertEquals("1a04980596e185d6", saved.getGmail_message_id());
        assertEquals("TechNova India Pvt. Ltd.", saved.getCompany());
        assertEquals("Internship / Full-Time", saved.getJobType());
        assertEquals("₹15,000–₹25,000 per month", saved.getStipend());
        assertEquals("Noida / Hybrid", saved.getLocation());
        assertEquals("Final-year students and recent graduates", saved.getEligibility());
        assertEquals("3–6 months", saved.getDuration());
        assertEquals("2026", saved.getBatch());
        assertEquals("B.Tech/B.E./BCA/MCA", saved.getBranch());
        assertEquals("08/26", saved.getApplication_date());
        assertEquals("12.5", saved.getSalaryLpa());
        assertEquals(75.0, saved.getMinTenth());
        assertEquals(78.0, saved.getMinTwelfth());
        assertEquals(70.0, saved.getMinGraduation());
        assertEquals(7.5, saved.getMinCgpa());
        assertEquals("Open", saved.getStatus());
        assertEquals(1, saved.getRounds().size());
        verify(placementRepository).save(any(Placement.class));
    }

    @Test
    void updateJob_ignoresNoneAndMissingFieldsSoExistingValuesStick() {
        Placement existing = existingJob();
        when(placementRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(placementRepository.save(any(Placement.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Placement incoming = new Placement();
        incoming.setCompany("TechNova India Pvt. Ltd.");
        incoming.setJobType("None");
        incoming.setStipend("None");
        incoming.setLocation("None");
        incoming.setEligibility("None");
        incoming.setStatus("Open");
        incoming.setRounds(null);

        Placement saved = (Placement) controller.updateJob(existing.getId(), incoming).getBody();

        assertEquals("TechNova India Pvt. Ltd.", saved.getCompany());
        assertEquals("Internship / Full-Time", saved.getJobType());
        assertEquals("₹15,000–₹25,000 per month", saved.getStipend());
        assertEquals("Noida / Hybrid", saved.getLocation());
        assertEquals("Final-year students and recent graduates", saved.getEligibility());
        assertEquals("1a04980596e185d6", saved.getGmail_message_id());
        assertEquals(1, saved.getRounds().size());
    }

    @Test
    void addJob_rejectsEligibleBatchWhenFromYearExceedsToYear() {
        Placement job = new Placement();
        job.setCompany("TechNova");
        job.setJobType("Developer");
        job.setBatch("2026-2022");

        assertThrows(ResponseStatusException.class, () -> controller.addJob(job));
    }

        @Test
        void replaceDocumentQueryRouteAcceptsSlashContainingCloudinaryPublicId() throws Exception {
        Placement job = existingJob();
        String publicId = "placement-portal/" + job.getId() + "/cc2b4de";
        JobDocument oldDocument = new JobDocument("old.pdf", "https://example.com/old.pdf",
            publicId, "raw", 10, LocalDateTime.now());
        JobDocument newDocument = new JobDocument("new.pdf", "https://example.com/new.pdf",
            "placement-portal/" + job.getId() + "/replacement", "raw", 20, LocalDateTime.now());
        job.setDocuments(new ArrayList<>(List.of(oldDocument)));
        when(placementRepository.findById(job.getId())).thenReturn(Optional.of(job));
        when(placementRepository.save(any(Placement.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(cloudinaryService.upload(any(), org.mockito.ArgumentMatchers.eq(job.getId())))
            .thenReturn(newDocument);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        MockMultipartFile file = new MockMultipartFile("file", "new.pdf", "application/pdf",
            new byte[] { 1, 2, 3 });

        mockMvc.perform(MockMvcRequestBuilders.multipart("/admin/jobs/" + job.getId() + "/documents")
                .file(file)
                .param("publicId", publicId)
                .with(request -> {
                    request.setMethod("PUT");
                    return request;
                }))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());

        assertEquals(List.of(newDocument), job.getDocuments());
        verify(cloudinaryService).delete(publicId);
        verify(placementRepository).save(job);
        }
}
