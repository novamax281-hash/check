package com.example.placement.controller;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.example.placement.dto.ApplicationDetail;
import com.example.placement.dto.BulkRequest;
import com.example.placement.dto.EvaluationRequest;
import com.example.placement.dto.RoundRequest;
import com.example.placement.entity.JobDocument;
import com.example.placement.entity.Placement;
import com.example.placement.entity.PlacementRound;
import com.example.placement.entity.UsersEntries;
import com.example.placement.repository.PlacementRepository;
import com.example.placement.repository.UsersRepository;
import com.example.placement.service.ApplicationService;
import com.example.placement.service.CloudinaryService;
import com.example.placement.service.CsvExportService;

import jakarta.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/admin")
public class AdminController {

    private final PlacementRepository placementRepository;
    private final ApplicationService applicationService;
    private final CsvExportService csvExportService;
    private final UsersRepository usersRepository;
    private final CloudinaryService cloudinaryService;

    public AdminController(PlacementRepository placementRepository, ApplicationService applicationService,
                           CsvExportService csvExportService, UsersRepository usersRepository,
                           CloudinaryService cloudinaryService) {
        this.placementRepository = placementRepository;
        this.applicationService = applicationService;
        this.csvExportService = csvExportService;
        this.usersRepository = usersRepository;
        this.cloudinaryService = cloudinaryService;
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
        copyIfPresent(incoming.getJobRole(), existing::setJobRole);
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
        if (incoming.getBatch() != null) {
            existing.setBatch(incoming.getBatch().trim());
        }
        if (incoming.getCourses() != null) {
            existing.setCourses(incoming.getCourses().trim());
        }
        copyIfPresent(incoming.getBranch(), existing::setBranch);
        copyIfPresent(incoming.getApplication_date(), existing::setApplication_date);
        copyIfPresent(incoming.getWebsite(), existing::setWebsite);
        copyIfPresent(incoming.getDriveType(), existing::setDriveType);
        copyIfPresent(incoming.getMode(), existing::setMode);
        copyIfPresent(incoming.getCtc(), existing::setCtc);
        copyIfPresent(incoming.getSalaryLpa(), existing::setSalaryLpa);
        if (incoming.getMinTenth() != null) {
            existing.setMinTenth(incoming.getMinTenth());
        }
        if (incoming.getMinTwelfth() != null) {
            existing.setMinTwelfth(incoming.getMinTwelfth());
        }
        if (incoming.getMinGraduation() != null) {
            existing.setMinGraduation(incoming.getMinGraduation());
        }
        if (incoming.getMinCgpa() != null) {
            existing.setMinCgpa(incoming.getMinCgpa());
        }
        copyIfPresent(incoming.getLikelyDateOfJoining(), existing::setLikelyDateOfJoining);
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
        applicationService.deleteApplicationsForJob(id);
        placementRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "Job deleted"));
    }

    private void validateJob(Placement job) {
        if (job == null || isBlank(job.getCompany()) || isBlank(job.getJobType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Company and role are required");
        }
        if (job.getRounds() == null) {
            job.setRounds(new ArrayList<>());
        }
        if (job.getBatch() != null && !job.getBatch().isBlank()) {
            String[] years = job.getBatch().trim().split("-", -1);
            if (years.length != 2 || !years[0].matches("\\d{4}") || !years[1].matches("\\d{4}")
                    || Integer.parseInt(years[0]) > Integer.parseInt(years[1])) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Eligible batch must have a valid From Year and To Year");
            }
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    @PostMapping("/jobs/{jobId}/rounds")
    public ResponseEntity<Map<String, Object>> addRound(@PathVariable String jobId,
                                                        @RequestBody(required = false) RoundRequest request) {
        Placement job = requireJob(jobId);
        Integer number = request == null ? null : request.getRoundNumber();
        if (number == null || number < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Round number must be a positive integer");
        }
        List<PlacementRound> rounds = job.getRounds() == null ? new ArrayList<>() : new ArrayList<>(job.getRounds());
        if (rounds.stream().anyMatch(round -> round != null && number.equals(round.getRound()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Round " + number + " already exists for this job");
        }
        String name = request.getRoundName() == null || request.getRoundName().isBlank()
                ? "Round " + number : request.getRoundName().trim();
        PlacementRound created = new PlacementRound(number, name, cleanDescription(request.getDescription()));
        rounds.add(created);
        job.setRounds(rounds);
        placementRepository.save(job);
        return ResponseEntity.status(HttpStatus.CREATED).body(roundInfo(created));
    }

    @GetMapping("/jobs/{jobId}/rounds")
    public List<Map<String, Object>> listRounds(@PathVariable String jobId) {
        Placement job = requireJob(jobId);
        return job.getRounds() == null ? List.of() : job.getRounds().stream()
                .filter(round -> round != null)
                .sorted(Comparator.comparing(round -> round.getRound() == null ? 0 : round.getRound()))
                .map(this::roundInfo)
                .collect(java.util.stream.Collectors.toList());
    }

    @PutMapping("/jobs/{jobId}/rounds/{roundNumber}")
    public Map<String, Object> updateRound(@PathVariable String jobId,
                                           @PathVariable int roundNumber,
                                           @RequestBody(required = false) RoundRequest request) {
        Placement job = requireJob(jobId);
        PlacementRound target = requireRound(job, roundNumber);
        if (request != null && request.getRoundName() != null) {
            String name = request.getRoundName().isBlank()
                    ? "Round " + roundNumber : request.getRoundName().trim();
            target.setName(name);
            target.setRoundType(name);
        }
        if (request != null && request.getDescription() != null) {
            target.setDescription(cleanDescription(request.getDescription()));
        }
        placementRepository.save(job);
        return roundInfo(target);
    }

    @DeleteMapping("/jobs/{jobId}/rounds/{roundNumber}")
    public Map<String, String> deleteRound(@PathVariable String jobId, @PathVariable int roundNumber) {
        Placement job = requireJob(jobId);
        List<PlacementRound> rounds = new ArrayList<>(job.getRounds() == null ? List.of() : job.getRounds());
        boolean removed = rounds.removeIf(round -> round != null && roundNumber == round.getRound().intValue());
        if (!removed) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Round " + roundNumber + " does not exist for this job");
        }
        job.setRounds(rounds);
        placementRepository.save(job);
        return Map.of("message", "Round " + roundNumber + " deleted");
    }

    private Placement requireJob(String jobId) {
        return placementRepository.findById(jobId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found"));
    }

    private PlacementRound requireRound(Placement job, int roundNumber) {
        if (job.getRounds() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Round " + roundNumber + " does not exist for this job");
        }
        return job.getRounds().stream()
                .filter(round -> round != null && round.getRound() != null
                        && round.getRound() == roundNumber)
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Round " + roundNumber + " does not exist for this job"));
    }

    private Map<String, Object> roundInfo(PlacementRound round) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("roundNumber", round.getRound());
        info.put("roundName", round.getName() == null ? "" : round.getName());
        info.put("description", round.getDescription() == null ? "" : round.getDescription());
        return info;
    }

    private String cleanDescription(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    @PostMapping(value = "/jobs/{jobId}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadDocument(@PathVariable String jobId,
                                            @RequestParam("file") MultipartFile file) {
        Placement job = requireJob(jobId);
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a file to upload");
        }
        try {
            JobDocument document = cloudinaryService.upload(file, jobId);
            List<JobDocument> documents = job.getDocuments() == null ? new ArrayList<>() : job.getDocuments();
            documents.add(document);
            job.setDocuments(documents);
            placementRepository.save(job);
            return ResponseEntity.status(HttpStatus.CREATED).body(document);
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
        }
    }

    @PutMapping(value = "/jobs/{jobId}/documents/{publicId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> replaceDocument(@PathVariable String jobId,
                                             @PathVariable String publicId,
                                             @RequestParam("file") MultipartFile file) {
        Placement job = requireJob(jobId);
        JobDocument existing = findDocument(job, publicId);
        if (existing == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found");
        }
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a file to upload");
        }
        try {
            JobDocument replacement = cloudinaryService.upload(file, jobId);
            List<JobDocument> documents = job.getDocuments() == null ? new ArrayList<>() : job.getDocuments();
            documents.remove(existing);
            documents.add(replacement);
            job.setDocuments(documents);
            placementRepository.save(job);
            cloudinaryService.delete(existing.getPublicId());
            return ResponseEntity.ok(replacement);
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
        }
    }

    @DeleteMapping("/jobs/{jobId}/documents/{publicId}")
    public ResponseEntity<Map<String, String>> deleteDocument(@PathVariable String jobId,
                                                              @PathVariable String publicId) {
        Placement job = requireJob(jobId);
        JobDocument existing = findDocument(job, publicId);
        if (existing == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found");
        }
        List<JobDocument> documents = new ArrayList<>(job.getDocuments() == null ? List.of() : job.getDocuments());
        documents.remove(existing);
        job.setDocuments(documents);
        placementRepository.save(job);
        cloudinaryService.delete(existing.getPublicId());
        return ResponseEntity.ok(Map.of("message", "Document deleted"));
    }

    private JobDocument findDocument(Placement job, String publicId) {
        if (job.getDocuments() == null) {
            return null;
        }
        return job.getDocuments().stream()
                .filter(document -> document != null && publicId.equals(document.getPublicId()))
                .findFirst()
                .orElse(null);
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
    public Map<String, Object> getApplications(@RequestParam(required = false) Integer page,
                                               @RequestParam(required = false) Integer pageSize,
                                               @RequestParam(required = false) String search,
                                               @RequestParam(required = false) String status,
                                               @RequestParam(required = false) String jobId) {
        return applicationService.getApplicationsPage(page, pageSize, search, status, jobId);
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
