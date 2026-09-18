package com.example.placement.controller;

import com.example.placement.dto.ApplicationDetail;
import com.example.placement.dto.BulkRequest;
import com.example.placement.dto.CoordinatorAssignmentRequest;
import com.example.placement.dto.EvaluationRequest;
import com.example.placement.dto.RoundRequest;
import com.example.placement.entity.Placement;
import com.example.placement.entity.Round;
import com.example.placement.entity.UsersEntries;
import com.example.placement.repository.PlacementRepository;
import com.example.placement.repository.UsersRepository;
import com.example.placement.service.ApplicationService;
import com.example.placement.service.CsvExportService;
import com.example.placement.service.JobSelectionService;
import com.example.placement.service.RoundService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin")
public class AdminController {

    private final PlacementRepository placementRepository;
    private final ApplicationService applicationService;
    private final RoundService roundService;
    private final CsvExportService csvExportService;
    private final UsersRepository usersRepository;
    private final JobSelectionService jobSelectionService;

    public AdminController(PlacementRepository placementRepository, ApplicationService applicationService,
                           RoundService roundService, CsvExportService csvExportService,
                           UsersRepository usersRepository, JobSelectionService jobSelectionService) {
        this.placementRepository = placementRepository;
        this.applicationService = applicationService;
        this.roundService = roundService;
        this.csvExportService = csvExportService;
        this.usersRepository = usersRepository;
        this.jobSelectionService = jobSelectionService;
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleStatusException(ResponseStatusException exception) {
        String message = exception.getReason() != null ? exception.getReason() : "Request failed";
        return ResponseEntity.status(exception.getStatusCode()).body(Map.of("message", message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleUnreadableBody(HttpMessageNotReadableException exception) {
        return ResponseEntity.badRequest().body(Map.of("message", "Invalid request body"));
    }

    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(
            org.springframework.web.bind.MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(java.util.stream.Collectors.joining(". "));
        return ResponseEntity.badRequest().body(Map.of("message", message));
    }

    @GetMapping("/jobs")
    public List<Placement> getAllJobs() {
        return placementRepository.findAll();
    }

    @PostMapping("/jobs")
    public Placement addJob(@RequestBody Placement job) {
        validateJob(job);
        job.setStatus("Open");
        return placementRepository.save(job);
    }

    @PutMapping("/jobs/{id}")
    public ResponseEntity<?> updateJob(@PathVariable String id, @RequestBody Placement job) {
        Placement existing = placementRepository.findById(id)
                .orElse(null);
        if (existing == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Job not found"));
        }
        mergeJob(existing, job);
        if (existing.getRounds() == null) {
            existing.setRounds(new ArrayList<>());
        }
        return ResponseEntity.ok(placementRepository.save(existing));
    }

    private void mergeJob(Placement existing, Placement incoming) {
        validateJob(incoming);
        copyIfPresent(incoming.getCompany(), existing::setCompany);
        copyIfPresent(incoming.getJobType(), existing::setJobType);
        copyIfPresent(incoming.getStipend(), existing::setStipend);
        copyIfPresent(incoming.getProgramFee(), existing::setProgramFee);
        copyIfPresent(incoming.getDuration(), existing::setDuration);
        copyIfPresent(incoming.getSkills(), existing::setSkills);
        copyIfPresent(incoming.getEligibility(), existing::setEligibility);
        copyIfPresent(incoming.getApplicationDeadline(), existing::setApplicationDeadline);
        copyIfPresent(incoming.getApplicationLink(), existing::setApplicationLink);
        copyIfPresent(incoming.getJobDescription(), existing::setJobDescription);
        copyIfPresent(incoming.getLocation(), existing::setLocation);
        copyIfPresent(incoming.getOpenings(), existing::setOpenings);
        copyIfPresent(incoming.getBatch(), existing::setBatch);
        copyIfPresent(incoming.getBranch(), existing::setBranch);
        copyIfPresent(incoming.getApplication_date(), existing::setApplication_date);
        if (incoming.getStatus() != null && !isBlank(incoming.getStatus())) {
            existing.setStatus(incoming.getStatus());
        }
        if (incoming.getRounds() != null && !incoming.getRounds().isEmpty()) {
            existing.setRounds(incoming.getRounds());
        }
    }

    private void copyIfPresent(String incoming, java.util.function.Consumer<String> setter) {
        if (incoming != null && !incoming.isBlank() && !"None".equalsIgnoreCase(incoming.trim())) {
            setter.accept(incoming.trim());
        }
    }

    @DeleteMapping("/jobs/{id}")
    public ResponseEntity<?> deleteJob(@PathVariable String id) {
        if (!placementRepository.existsById(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Job not found"));
        }
        jobSelectionService.deleteSelectionsForJob(id);
        applicationService.deleteApplicationsForJob(id);
        placementRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "Job deleted"));
    }

    @GetMapping("/jobs/{jobId}/coordinators")
    public List<String> jobCoordinators(@PathVariable String jobId) {
        Placement job = placementRepository.findById(jobId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found"));
        return job.getCoordinatorIds() == null ? List.of() : job.getCoordinatorIds();
    }

    @PostMapping("/jobs/{jobId}/coordinators")
    public Placement assignCoordinators(@PathVariable String jobId,
                                        @RequestBody(required = false) CoordinatorAssignmentRequest request) {
        return jobSelectionService.assignCoordinatorsToJob(jobId,
                request == null ? null : request.getCoordinatorIds());
    }

    private void validateJob(Placement job) {
        if (job == null || isBlank(job.getCompany()) || isBlank(job.getJobType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Company and role are required");
        }
        if (job.getRounds() == null) {
            job.setRounds(new ArrayList<>());
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    @PostMapping("/jobs/{jobId}/rounds")
    public ResponseEntity<Round> addRound(@PathVariable String jobId, @RequestBody(required = false) RoundRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(roundService.addRound(jobId, request));
    }

    @GetMapping("/jobs/{jobId}/rounds")
    public List<Round> listRounds(@PathVariable String jobId) {
        return roundService.listRounds(jobId);
    }

    @PutMapping("/jobs/{jobId}/rounds/{roundNumber}")
    public Round updateRound(@PathVariable String jobId,
                             @PathVariable int roundNumber,
                             @RequestBody(required = false) RoundRequest request) {
        return roundService.updateRound(jobId, roundNumber, request);
    }

    @DeleteMapping("/jobs/{jobId}/rounds/{roundNumber}")
    public Map<String, String> deleteRound(@PathVariable String jobId, @PathVariable int roundNumber) {
        roundService.deleteRound(jobId, roundNumber);
        return Map.of("message", "Round " + roundNumber + " deleted");
    }

    @PostMapping("/applications/bulk/advance")
    public Map<String, Object> bulkAdvance(@RequestBody(required = false) BulkRequest request) {
        return applicationService.bulkAdvance(
                request == null ? null : request.getApplicationIds(),
                request == null ? null : request.getAdminRemark());
    }

    @PostMapping("/applications/bulk/reject")
    public Map<String, Object> bulkReject(@RequestBody(required = false) BulkRequest request) {
        return applicationService.bulkReject(
                request == null ? null : request.getApplicationIds(),
                request == null ? null : request.getAdminRemark());
    }

    @PostMapping("/applications/bulk/set-round")
    public Map<String, Object> bulkSetRound(@RequestBody(required = false) BulkRequest request) {
        return applicationService.bulkSetRound(
                request == null ? null : request.getApplicationIds(),
                request == null ? null : request.getTargetRound(),
                request == null ? null : request.getAdminRemark());
    }

    @GetMapping("/applications")
    public List<ApplicationDetail> getAllApplications() {
        return applicationService.getAllApplications();
    }

    @GetMapping("/users")
    public List<UsersEntries> getUsers() {
        return usersRepository.findAll().stream()
                .filter(user -> user.getRole() == null
                        || (!"admin".equalsIgnoreCase(user.getRole())
                        && !"coordinator".equalsIgnoreCase(user.getRole())))
                .collect(java.util.stream.Collectors.toList());
    }

    @PutMapping("/applications/{id}/status")
    public ResponseEntity<?> updateApplicationStatus(@PathVariable String id,
                                                     @RequestBody Map<String, String> body) {
        String status = body.get("status");
        if (status == null || status.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Status is required"));
        }
        if (!applicationService.updateStatus(id, status, body.get("remark"))) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Application not found"));
        }
        return ResponseEntity.ok(Map.of("message", "Application updated"));
    }

    @PostMapping("/applications/{id}/rounds/{roundNumber}/qualify")
    public ApplicationDetail qualify(@PathVariable String id,
                                     @PathVariable int roundNumber,
                                     @RequestBody(required = false) EvaluationRequest request) {
        return applicationService.qualify(id, roundNumber,
                request == null ? null : request.getScore(),
                request == null ? null : request.getAdminRemark());
    }

    @PostMapping("/applications/{id}/rounds/{roundNumber}/reject")
    public ApplicationDetail reject(@PathVariable String id,
                                    @PathVariable int roundNumber,
                                    @RequestBody(required = false) EvaluationRequest request) {
        return applicationService.reject(id, roundNumber,
                request == null ? null : request.getScore(),
                request == null ? null : request.getAdminRemark());
    }

    @PostMapping("/applications/{id}/advance")
    public ApplicationDetail advance(@PathVariable String id,
                                     @RequestBody(required = false) EvaluationRequest request) {
        return applicationService.advance(id,
                request == null ? null : request.getScore(),
                request == null ? null : request.getAdminRemark());
    }

    @PostMapping("/applications/{id}/reject")
    public ApplicationDetail rejectCurrent(@PathVariable String id,
                                           @RequestBody(required = false) EvaluationRequest request) {
        return applicationService.rejectCurrent(id,
                request == null ? null : request.getScore(),
                request == null ? null : request.getAdminRemark());
    }

    @GetMapping("/stats")
    public Map<String, Long> stats() {
        return applicationService.adminStats();
    }

    @GetMapping("/applications/export")
    public void exportApplications(@RequestParam(required = false) String jobId,
                                   @RequestParam(required = false) String status,
                                   @RequestParam(required = false) Integer round,
                                   @RequestParam(required = false) String search,
                                   HttpServletResponse response) throws IOException {
        List<ApplicationDetail> rows = applicationService.getAllApplications().stream()
                .filter(application -> jobId == null || jobId.isBlank()
                        || jobId.equals(application.getPlacementId()))
                .filter(application -> status == null || status.isBlank()
                        || status.equalsIgnoreCase(application.getStatus()))
                .filter(application -> round == null
                        || round.equals(application.getCurrentRound()))
                .filter(application -> matchesSearch(application, search))
                .toList();

        csvDownload(response, "applications", writer -> csvExportService.writeApplications(writer, rows));
    }

    @GetMapping("/placements/export")
    public void exportPlacements(HttpServletResponse response) throws IOException {
        List<Placement> rows = placementRepository.findAll();
        csvDownload(response, "placements", writer -> csvExportService.writePlacements(writer, rows));
    }

    private boolean matchesSearch(ApplicationDetail application, String search) {
        if (search == null || search.isBlank()) {
            return true;
        }
        String needle = search.toLowerCase();
        String[] fields = {application.getStudentName(), application.getStudentEmail(),
                application.getEnrollmentno(), application.getCompany(), application.getRole()};
        for (String field : fields) {
            if (field != null && field.toLowerCase().contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private void csvDownload(HttpServletResponse response, String entityName,
                             CsvWriter writer) throws IOException {
        String filename = entityName + "_" + LocalDate.now() + ".csv";
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        Writer out = new OutputStreamWriter(new BufferedOutputStream(response.getOutputStream()),
                StandardCharsets.UTF_8);
        out.write('\uFEFF');
        try {
            writer.write(out);
        } catch (IOException exportFailure) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.setContentType("application/json");
            response.getWriter().write("{\"message\":\"Could not generate the CSV export\"}");
        }
    }

    @FunctionalInterface
    private interface CsvWriter {
        void write(Writer writer) throws IOException;
    }
}
