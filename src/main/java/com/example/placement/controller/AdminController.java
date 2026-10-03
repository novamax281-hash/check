package com.example.placement.controller;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.format.annotation.DateTimeFormat;
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
import com.example.placement.dto.ApplicationEmailRequest;
import com.example.placement.dto.BulkRequest;
import com.example.placement.dto.EvaluationRequest;
import com.example.placement.dto.RoundRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.example.placement.entity.JobDocument;
import com.example.placement.entity.Placement;
import com.example.placement.entity.PlacementRound;
import com.example.placement.entity.UsersEntries;
import com.example.placement.repository.PlacementRepository;
import com.example.placement.repository.UsersRepository;
import com.example.placement.service.ApplicationService;
import com.example.placement.service.BatchRanges;
import com.example.placement.service.CloudinaryService;
import com.example.placement.service.CsvExportService;
import com.example.placement.service.CsvImportService;
import com.example.placement.service.EmailCsvService;

import jakarta.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/admin")
public class AdminController {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AdminController.class);

    private final PlacementRepository placementRepository;
    private final ApplicationService applicationService;
    private final CsvExportService csvExportService;
    private final UsersRepository usersRepository;
    private final CloudinaryService cloudinaryService;
    private final CsvImportService csvImportService;
    private final EmailCsvService emailCsvService;

    public AdminController(PlacementRepository placementRepository, ApplicationService applicationService,
                           CsvExportService csvExportService, UsersRepository usersRepository,
                           CloudinaryService cloudinaryService, CsvImportService csvImportService,
                           EmailCsvService emailCsvService) {
        this.placementRepository = placementRepository;
        this.applicationService = applicationService;
        this.csvExportService = csvExportService;
        this.usersRepository = usersRepository;
        this.cloudinaryService = cloudinaryService;
        this.csvImportService = csvImportService;
        this.emailCsvService = emailCsvService;
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
        // POST always creates: a body-supplied _id must not overwrite an existing placement.
        job.setId(null);
        job.setStatus("Open");
        return placementRepository.save(job);
    }

    @PutMapping("/jobs/{id}")
    public ResponseEntity<?> updateJob(@PathVariable String id, @RequestBody com.fasterxml.jackson.databind.JsonNode payload) {
        Placement existing = placementRepository.findById(id)
                .orElse(null);
        if (existing == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Job not found"));
        }
        if (payload == null || !payload.isObject()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid request body");
        }
        applyExplicitUpdates(existing, payload);
        if (existing.getRounds() == null) {
            existing.setRounds(new ArrayList<>());
        }
        return ResponseEntity.ok(placementRepository.save(existing));
    }

    public ResponseEntity<?> updateJob(String id, Placement job) {
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

    private void applyExplicitUpdates(Placement existing, JsonNode payload) {
        setStringField(payload, "company", existing::setCompany);
        setStringField(payload, "jobRole", existing::setJobRole);
        setStringField(payload, "jobType", existing::setJobType);
        setStringField(payload, "location", existing::setLocation);
        setStringField(payload, "eligibility", existing::setEligibility);
        setStringField(payload, "skills", existing::setSkills);
        setStringField(payload, "applicationDeadline", existing::setApplicationDeadline);
        setStringField(payload, "openings", existing::setOpenings);
        setStringField(payload, "status", existing::setStatus);
        setStringField(payload, "jobDescription", existing::setJobDescription);
        setStringField(payload, "website", existing::setWebsite);
        setStringField(payload, "driveType", existing::setDriveType);
        setStringField(payload, "mode", existing::setMode);
        setStringField(payload, "likelyDateOfJoining", existing::setLikelyDateOfJoining);
        setStringField(payload, "salaryLpa", existing::setSalaryLpa);
        setStringField(payload, "batch", existing::setBatch);
        setStringField(payload, "courses", existing::setCourses);
        setStringField(payload, "branch", existing::setBranch);

        if (payload.has("minTenth")) {
            existing.setMinTenth(parseNullableDouble(payload.get("minTenth")));
        }
        if (payload.has("minTwelfth")) {
            existing.setMinTwelfth(parseNullableDouble(payload.get("minTwelfth")));
        }
        if (payload.has("minGraduation")) {
            existing.setMinGraduation(parseNullableDouble(payload.get("minGraduation")));
        }
        if (payload.has("minCgpa")) {
            existing.setMinCgpa(parseNullableDouble(payload.get("minCgpa")));
        }
        if (payload.has("rounds") && payload.get("rounds") != null && !payload.get("rounds").isNull()) {
            JsonNode roundsNode = payload.get("rounds");
            if (roundsNode.isArray() && roundsNode.size() > 0) {
                existing.setRounds(new ArrayList<>());
                for (JsonNode roundNode : roundsNode) {
                    if (roundNode == null || roundNode.isNull()) {
                        continue;
                    }
                    try {
                        existing.getRounds().add(new com.fasterxml.jackson.databind.ObjectMapper()
                                .treeToValue(roundNode, PlacementRound.class));
                    } catch (IOException exception) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid rounds payload");
                    }
                }
            }
        }
    }

    private void setStringField(JsonNode payload, String fieldName, java.util.function.Consumer<String> setter) {
        if (!payload.has(fieldName)) {
            return;
        }
        JsonNode valueNode = payload.get(fieldName);
        if (valueNode == null || valueNode.isNull()) {
            setter.accept(null);
            return;
        }
        setter.accept(valueNode.isTextual() ? valueNode.asText() : valueNode.toString());
    }

    private Double parseNullableDouble(JsonNode valueNode) {
        if (valueNode == null || valueNode.isNull()) {
            return null;
        }
        if (valueNode.isNumber()) {
            return valueNode.doubleValue();
        }
        String text = valueNode.asText();
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(text.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private void mergeJob(Placement existing, Placement incoming) {
        validateJob(incoming);
        // Fields owned by the placement form: clearing them must persist.
        copyFromForm(incoming.getCompany(), existing::setCompany);
        copyFromForm(incoming.getJobRole(), existing::setJobRole);
        copyFromForm(incoming.getJobType(), existing::setJobType);
        copyFromForm(incoming.getLocation(), existing::setLocation);
        copyFromForm(incoming.getEligibility(), existing::setEligibility);
        copyFromForm(incoming.getSkills(), existing::setSkills);
        copyFromForm(incoming.getApplicationDeadline(), existing::setApplicationDeadline);
        copyFromForm(incoming.getOpenings(), existing::setOpenings);
        copyFromForm(incoming.getJobDescription(), existing::setJobDescription);
        copyFromForm(incoming.getWebsite(), existing::setWebsite);
        copyFromForm(incoming.getDriveType(), existing::setDriveType);
        copyFromForm(incoming.getMode(), existing::setMode);
        copyFromForm(incoming.getLikelyDateOfJoining(), existing::setLikelyDateOfJoining);
        copyFromForm(incoming.getSalaryLpa(), existing::setSalaryLpa);
        copyFromForm(incoming.getBranch(), existing::setBranch);
        if (incoming.getBatch() != null) {
            existing.setBatch(incoming.getBatch().trim());
        }
        if (incoming.getCourses() != null) {
            existing.setCourses(incoming.getCourses().trim());
        }
        if (incoming.getStatus() != null && !isBlank(incoming.getStatus())) {
            existing.setStatus(incoming.getStatus());
        }
        // Fields not present on the placement form must survive an update untouched.
        copyIfPresent(incoming.getStipend(), existing::setStipend);
        copyIfPresent(incoming.getProgramFee(), existing::setProgramFee);
        copyIfPresent(incoming.getDuration(), existing::setDuration);
        copyIfPresent(incoming.getApplicationLink(), existing::setApplicationLink);
        copyIfPresent(incoming.getApplication_date(), existing::setApplication_date);
        copyIfPresent(incoming.getCtc(), existing::setCtc);
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
        if (incoming.getRounds() != null && !incoming.getRounds().isEmpty()) {
            existing.setRounds(incoming.getRounds());
        }
    }

    private void copyIfPresent(String incoming, java.util.function.Consumer<String> setter) {
        if (incoming != null && !incoming.isBlank() && !"None".equalsIgnoreCase(incoming.trim())) {
            setter.accept(incoming.trim());
        }
    }

    /**
     * Overwrites a field the placement form owns, including when the admin clears it, so that
     * emptying a field actually removes the stored value instead of silently keeping the old one.
     */
    private void copyFromForm(String incoming, java.util.function.Consumer<String> setter) {
        if (incoming == null || "None".equalsIgnoreCase(incoming.trim())) {
            return;
        }
        setter.accept(incoming.trim());
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
            if (!BatchRanges.isValidSpec(job.getBatch())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Each eligible batch must be a valid From Year and To Year, for example 2022-2026");
            }
            job.setBatch(BatchRanges.normalize(job.getBatch()));
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
        boolean removed = rounds.removeIf(round -> round != null && round.getRound() != null
                && roundNumber == round.getRound().intValue());
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
            // Log the provider/configuration detail server-side; the reason is
            // echoed to the client and would disclose internals.
            log.error("Document upload failed for job {}", jobId, e);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "The file could not be uploaded. Please try again.");
        }
    }

    @PutMapping(value = "/jobs/{jobId}/documents/{publicId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> replaceDocument(@PathVariable String jobId,
                                             @PathVariable String publicId,
                                             @RequestParam("file") MultipartFile file) {
        return replaceDocumentById(jobId, publicId, file);
        }

        @PutMapping(value = "/jobs/{jobId}/documents", params = "publicId",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        public ResponseEntity<?> replaceDocumentByQuery(@PathVariable String jobId,
                                    @RequestParam("publicId") String publicId,
                                @RequestParam("file") MultipartFile file) {
        return replaceDocumentById(jobId, publicId, file);
        }

        private ResponseEntity<?> replaceDocumentById(String jobId, String publicId, MultipartFile file) {
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
            // Log the provider/configuration detail server-side; the reason is
            // echoed to the client and would disclose internals.
            log.error("Document upload failed for job {}", jobId, e);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "The file could not be uploaded. Please try again.");
        }
    }

    @DeleteMapping("/jobs/{jobId}/documents/{publicId}")
    public ResponseEntity<Map<String, String>> deleteDocument(@PathVariable String jobId,
                                                              @PathVariable String publicId) {
        return deleteDocumentById(jobId, publicId);
    }

    @DeleteMapping(value = "/jobs/{jobId}/documents", params = "publicId")
    public ResponseEntity<Map<String, String>> deleteDocumentByQuery(@PathVariable String jobId,
                                                                     @RequestParam("publicId") String publicId) {
        return deleteDocumentById(jobId, publicId);
    }

    private ResponseEntity<Map<String, String>> deleteDocumentById(String jobId, String publicId) {
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

    @GetMapping("/applications/export-fields")
    public Map<String, Object> applicationExportFields() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("defaults", CsvExportService.APPLICATION_DEFAULT_COLUMNS);
        response.put("optional", CsvExportService.APPLICATION_OPTIONAL_COLUMNS);
        response.put("groups", CsvExportService.APPLICATION_FIELD_GROUPS);
        return response;
    }

    @GetMapping("/applications/export")
    public void exportApplications(@RequestParam(required = false) String jobId,
                                   @RequestParam(required = false) String status,
                                   @RequestParam(required = false) Integer round,
                                   @RequestParam(required = false) String search,
                                   @RequestParam(required = false) String company,
                                   @RequestParam(required = false) String batch,
                                   @RequestParam(required = false) String branch,
                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                                   LocalDate from,
                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                                   LocalDate to,
                                   @RequestParam(required = false) String fields,
                                   HttpServletResponse response) throws IOException {
        List<ApplicationDetail> rows = applicationService.filterDetails(
                        applicationService.getAllApplications(), search, status, jobId, company, batch, branch, from, to)
                .stream()
                .filter(application -> round == null || round.equals(application.getCurrentRound()))
                .toList();

        List<String> columns = CsvExportService.resolveApplicationColumns(
                fields == null || fields.isBlank() ? null : Arrays.asList(fields.split(",")));

        csvDownload(response, "applications", writer -> csvExportService.writeApplications(writer, rows, columns));
    }

    @GetMapping("/placements/export")
    public void exportPlacements(HttpServletResponse response) throws IOException {
        List<Placement> rows = placementRepository.findAll();
        csvDownload(response, "placements", writer -> csvExportService.writePlacements(writer, rows));
    }

    @GetMapping("/applications/import/status/template")
    public void applicationStatusTemplate(HttpServletResponse response) throws IOException {
        csvDownload(response, "applications_status_template", writer -> writer.write("Email,Status\n"));
    }

    /**
     * Bulk-updates student status from an uploaded CSV {email, status} rows. Students are
     * matched by email (case-insensitive); invalid/unknown rows are reported without
     * stopping valid ones. With apply=false this is a dry run so the UI can preview and
     * confirm before committing.
     */
    @PostMapping("/applications/import/status")
    public Map<String, Object> importApplicationStatus(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "false") boolean apply) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The uploaded file is empty");
        }
        if (file.getSize() > 5 * 1024 * 1024) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File exceeds the 5 MB size limit");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".csv")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only .csv files are accepted");
        }
        String content = new String(file.getBytes(), StandardCharsets.UTF_8);
        List<Map<String, String>> rows = csvImportService.parseCsv(Arrays.asList(content.split("\\r?\\n")));

        List<String> statuses = List.of("Active", "Inactive", "Placed", "Not Placed");
        int total = 0;
        int updated = 0;
        int failed = 0;
        List<Map<String, Object>> failures = new ArrayList<>();

        for (int i = 0; i < rows.size(); i++) {
            Map<String, String> row = rows.get(i);
            int rowNumber = i + 2;
            String email = row.get("email") == null ? "" : row.get("email").trim();
            String status = row.get("status") == null ? "" : row.get("status").trim();
            total++;
            List<String> reasons = new ArrayList<>();
            if (email.isEmpty() || !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
                reasons.add("Invalid or missing email");
            } else if (usersRepository.findByEmailIgnoreCase(email) == null) {
                reasons.add("No student found for this email");
            }
            if (status.isEmpty()) {
                reasons.add("Status is required");
            } else if (statuses.stream().noneMatch(s -> s.equalsIgnoreCase(status))) {
                reasons.add("Status must be one of: " + String.join(", ", statuses));
            }

            if (reasons.isEmpty() && apply) {
                UsersEntries student = usersRepository.findByEmailIgnoreCase(email);
                student.setStudentStatus(statuses.stream()
                        .filter(s -> s.equalsIgnoreCase(status)).findFirst().orElse(status));
                usersRepository.save(student);
            }
            if (reasons.isEmpty()) {
                updated++;
            } else {
                failed++;
                Map<String, Object> failure = new LinkedHashMap<>();
                failure.put("row", rowNumber);
                failure.put("email", email);
                failure.put("reason", String.join("; ", reasons));
                failures.add(failure);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("updated", updated);
        result.put("failed", failed);
        result.put("failures", failures);
        result.put("message", (apply ? "Import complete" : "Preview") + " — " + updated
                + " valid, " + failed + " failed.");
        return result;
    }

    /**
     * Emails a filtered applications CSV (selected columns) to a recipient with an
     * optional subject and file name, reusing the shared email/export services.
     */
    @PostMapping("/applications/email")
    public Map<String, Object> emailApplications(@RequestBody @jakarta.validation.Valid
            ApplicationEmailRequest request) throws IOException {
        List<ApplicationDetail> rows = applicationService.filterDetails(
                        applicationService.getAllApplications(), request.getSearch(),
                        request.getStatus(), request.getJobId(), request.getCompany(),
                        request.getBatch(), request.getBranch(), request.getAppliedFrom(),
                        request.getAppliedTo())
                .stream()
                .filter(application -> request.getRound() == null
                        || request.getRound().equals(application.getCurrentRound()))
                .toList();

        List<String> columns = CsvExportService.resolveApplicationColumns(request.getFields());
        StringWriter buffer = new StringWriter();
        try (Writer out = buffer) {
            out.write('\uFEFF');
            csvExportService.writeApplications(out, rows, columns);
        }
        String filename = request.getFileName();
        if (filename == null || filename.trim().isEmpty()) {
            filename = "applications_export_" + LocalDate.now().toString().replace("-", "_") + ".csv";
        } else {
            String cleaned = filename.trim().replaceAll("[^a-zA-Z0-9._-]", "_");
            filename = cleaned.toLowerCase().endsWith(".csv") ? cleaned : cleaned + ".csv";
        }
        emailCsvService.sendCsv(request.getRecipientEmail(), buffer.toString(), filename, request.getSubject());
        return Map.of(
                "message", "CSV emailed successfully",
                "recipient", request.getRecipientEmail(),
                "file", filename,
                "rows", rows.size());
    }

    @PostMapping("/applications/email-export")
    public Map<String, Object> emailApplicationsExport(@RequestBody @jakarta.validation.Valid
            ApplicationEmailRequest request) throws IOException {
        List<ApplicationDetail> allApplications = applicationService.getAllApplications();
        List<ApplicationDetail> rows;
        if (request.getScope() != null && request.getScope().equals("selected")
                && request.getApplicationIds() != null && !request.getApplicationIds().isEmpty()) {
            Set<String> selectedIds = new HashSet<>(request.getApplicationIds());
            rows = allApplications.stream()
                    .filter(a -> selectedIds.contains(a.getApplicationId()))
                    .toList();
        } else if (request.getScope() != null && request.getScope().equals("all")) {
            rows = allApplications;
        } else {
            rows = applicationService.filterDetails(
                            allApplications, request.getSearch(),
                            request.getStatus(), request.getJobId(), request.getCompany(),
                            request.getBatch(), request.getBranch(), request.getAppliedFrom(),
                            request.getAppliedTo())
                    .stream()
                    .filter(application -> request.getRound() == null
                            || request.getRound().equals(application.getCurrentRound()))
                    .toList();
        }

        List<String> columns = CsvExportService.resolveApplicationColumns(request.getFields());
        StringWriter buffer = new StringWriter();
        try (Writer out = buffer) {
            out.write('\uFEFF');
            csvExportService.writeApplications(out, rows, columns);
        }
        String filename = request.getFileName();
        if (filename == null || filename.trim().isEmpty()) {
            filename = "applications_export_" + LocalDate.now().toString().replace("-", "_") + ".csv";
        } else {
            String cleaned = filename.trim().replaceAll("[^a-zA-Z0-9._-]", "_");
            filename = cleaned.toLowerCase().endsWith(".csv") ? cleaned : cleaned + ".csv";
        }
        emailCsvService.sendCsv(request.getRecipientEmail(), buffer.toString(), filename, request.getSubject());
        return Map.of(
                "message", "CSV emailed successfully",
                "recipient", request.getRecipientEmail(),
                "file", filename,
                "rows", rows.size());
    }

    private void csvDownload(HttpServletResponse response, String entityName,
                             CsvWriter writer) throws IOException {
        String filename = entityName + "_" + LocalDate.now() + ".csv";
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        // try-with-resources is required, not cosmetic: OutputStreamWriter buffers
        // internally, so without the implicit close the CSV is never pushed to the
        // response and small exports download as empty or 8KB-truncated files.
        try (Writer out = new OutputStreamWriter(new BufferedOutputStream(response.getOutputStream()),
                StandardCharsets.UTF_8)) {
            out.write('\uFEFF');
            writer.write(out);
        } catch (IOException exportFailure) {
            log.error("Could not generate the {} CSV export", entityName, exportFailure);
            // The servlet output stream is already in use, so getWriter() is no longer
            // available. The client falls back to its generic message on a non-2xx status.
            if (!response.isCommitted()) {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }
        }
    }

    @FunctionalInterface
    private interface CsvWriter {
        void write(Writer writer) throws IOException;
    }
}
