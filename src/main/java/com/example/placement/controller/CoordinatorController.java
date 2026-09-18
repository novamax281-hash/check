package com.example.placement.controller;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
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

import com.example.placement.dto.ApplicationDetail;
import com.example.placement.dto.EvaluationRequest;
import com.example.placement.dto.RoundRequest;
import com.example.placement.dto.StudentDetail;
import com.example.placement.dto.StudentListResponse;
import com.example.placement.dto.StudentSelectionRequest;
import com.example.placement.entity.Application;
import com.example.placement.entity.Placement;
import com.example.placement.entity.Round;
import com.example.placement.entity.UsersEntries;
import com.example.placement.repository.ApplicationRepository;
import com.example.placement.repository.PlacementRepository;
import com.example.placement.repository.UsersRepository;
import com.example.placement.service.ApplicationService;
import com.example.placement.service.ApplicationService.ApplicationGuard;
import com.example.placement.service.JobSelectionService;
import com.example.placement.service.RoundService;
import com.example.placement.service.StudentService;

@RestController
@RequestMapping("/coordinator")
public class CoordinatorController {

    private final UsersRepository usersRepository;
    private final ApplicationRepository applicationRepository;
    private final PlacementRepository placementRepository;
    private final StudentService studentService;
    private final JobSelectionService jobSelectionService;
    private final ApplicationService applicationService;
    private final RoundService roundService;

    public CoordinatorController(UsersRepository usersRepository, StudentService studentService,
                                 JobSelectionService jobSelectionService, ApplicationRepository applicationRepository,
                                 ApplicationService applicationService, PlacementRepository placementRepository,
                                 RoundService roundService) {
        this.usersRepository = usersRepository;
        this.applicationRepository = applicationRepository;
        this.studentService = studentService;
        this.jobSelectionService = jobSelectionService;
        this.applicationService = applicationService;
        this.placementRepository = placementRepository;
        this.roundService = roundService;
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleStatusException(ResponseStatusException exception) {
        String message = exception.getReason() != null ? exception.getReason() : "Request failed";
        return ResponseEntity.status(exception.getStatusCode()).body(Map.of("message", message));
    }

    private UsersEntries currentCoordinator(Authentication authentication) {
        UsersEntries coordinator = usersRepository.findByEnrollmentno(authentication.getName());
        if (coordinator == null || !studentService.isCoordinatorRole(coordinator)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Coordinator access required");
        }
        return coordinator;
    }

    // ------------------------------------------------------------------ profile

    @GetMapping("/profile")
    public Map<String, Object> profile(Authentication authentication) {
        return studentService.coordinatorProfile(currentCoordinator(authentication).getId());
    }

    @PutMapping("/profile")
    public Map<String, Object> updateProfile(Authentication authentication, @RequestBody UsersEntries update) {
        return studentService.updateCoordinatorProfile(currentCoordinator(authentication).getId(), update);
    }

    // ------------------------------------------------------------------ students

    @GetMapping("/stats")
    public Map<String, Object> stats(Authentication authentication) {
        UsersEntries coordinator = currentCoordinator(authentication);
        return studentService.coordinatorStats(coordinator.getId());
    }

    @GetMapping("/students")
    public StudentListResponse students(Authentication authentication,
                                        @RequestParam(required = false) String search,
                                        @RequestParam(required = false) List<String> batch,
                                        @RequestParam(required = false) List<String> branch,
                                        @RequestParam(required = false) List<String> section,
                                        @RequestParam(required = false) String studentStatus,
                                        @RequestParam(required = false) String applicationStatus,
                                        @RequestParam(required = false) String company,
                                        @RequestParam(required = false) String job,
                                        @RequestParam(required = false) Integer round,
                                        @RequestParam(defaultValue = "1") int page,
                                        @RequestParam(defaultValue = "25") int pageSize) {
        UsersEntries coordinator = currentCoordinator(authentication);
        return studentService.listStudentsForCoordinator(coordinator.getId(), search,
            batch, branch, section, studentStatus, applicationStatus, company, job, round, page, pageSize);
    }

    @GetMapping("/students/{id}")
    public StudentDetail studentDetail(Authentication authentication, @PathVariable String id) {
        UsersEntries coordinator = currentCoordinator(authentication);
        return studentService.getAssignedStudentDetail(coordinator.getId(), id);
    }

    // ------------------------------------------------------------------ jobs

    @GetMapping("/jobs")
    public List<Map<String, Object>> jobs(Authentication authentication) {
        return buildJobSummaries(currentCoordinator(authentication));
    }

    @PostMapping("/jobs")
    public Placement createJob(Authentication authentication, @RequestBody Placement job) {
        UsersEntries coordinator = currentCoordinator(authentication);
        validateJob(job);
        job.setId(null);
        job.setStatus(job.getStatus() == null || job.getStatus().isBlank() ? "Open" : job.getStatus());
        job.setCoordinatorId(coordinator.getId());
        job.setCreatedBy(coordinator.getId());
        job.setCreatedAt(LocalDateTime.now());
        job.setCoordinatorIds(new ArrayList<>(List.of(coordinator.getId())));
        return placementRepository.save(job);
    }

    @PutMapping("/jobs/{id}")
    public Placement updateJob(Authentication authentication, @PathVariable String id, @RequestBody Placement incoming) {
        requireOwnedJob(authentication, id);
        Placement existing = placementRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found"));
        mergeJob(existing, incoming);
        return placementRepository.save(existing);
    }

    @DeleteMapping("/jobs/{id}")
    public Map<String, String> deleteJob(Authentication authentication, @PathVariable String id) {
        requireOwnedJob(authentication, id);
        jobSelectionService.deleteSelectionsForJob(id);
        applicationService.deleteApplicationsForJob(id);
        placementRepository.deleteById(id);
        return Map.of("message", "Job deleted");
    }

    // ------------------------------------------------------------------ rounds

    @GetMapping("/jobs/{jobId}/rounds")
    public List<Round> listRounds(Authentication authentication, @PathVariable String jobId) {
        requireOwnedJob(authentication, jobId);
        return roundService.listRounds(jobId);
    }

    @PostMapping("/jobs/{jobId}/rounds")
    public Round addRound(Authentication authentication, @PathVariable String jobId,
                          @RequestBody(required = false) RoundRequest request) {
        requireOwnedJob(authentication, jobId);
        return roundService.addRound(jobId, request);
    }

    @PutMapping("/jobs/{jobId}/rounds/{roundNumber}")
    public Round updateRound(Authentication authentication, @PathVariable String jobId, @PathVariable int roundNumber,
                             @RequestBody(required = false) RoundRequest request) {
        requireOwnedJob(authentication, jobId);
        return roundService.updateRound(jobId, roundNumber, request);
    }

    @DeleteMapping("/jobs/{jobId}/rounds/{roundNumber}")
    public Map<String, String> deleteRound(Authentication authentication, @PathVariable String jobId,
                                           @PathVariable int roundNumber) {
        requireOwnedJob(authentication, jobId);
        roundService.deleteRound(jobId, roundNumber);
        return Map.of("message", "Round " + roundNumber + " deleted");
    }

    // ------------------------------------------------------------------ applications

    @GetMapping("/jobs/{jobId}/applications")
    public List<ApplicationDetail> jobApplications(Authentication authentication, @PathVariable String jobId) {
        requireOwnedJob(authentication, jobId);
        return applicationService.getApplicationsForPlacement(jobId);
    }

    @GetMapping("/jobs/{jobId}/applicants")
    public List<ApplicationDetail> applicants(Authentication authentication, @PathVariable String jobId) {
        return jobApplications(authentication, jobId);
    }

    @GetMapping("/applications")
    public List<ApplicationDetail> applications(Authentication authentication) {
        UsersEntries coordinator = currentCoordinator(authentication);
        Set<String> jobIds = ownedJobs(coordinator).stream()
            .map(Placement::getId).collect(Collectors.toSet());
        return applicationService.getApplicationsForPlacements(jobIds);
    }

    @PostMapping("/applications/{id}/rounds/{roundNumber}/qualify")
    public ApplicationDetail qualify(Authentication authentication, @PathVariable String id,
                                     @PathVariable int roundNumber,
                                     @RequestBody(required = false) EvaluationRequest request) {
        requireOwnedApplication(authentication, id);
        return applicationService.qualify(id, roundNumber,
            request == null ? null : request.getScore(),
            request == null ? null : request.getAdminRemark());
    }

    @PostMapping("/applications/{id}/rounds/{roundNumber}/reject")
    public ApplicationDetail reject(Authentication authentication, @PathVariable String id,
                                    @PathVariable int roundNumber,
                                    @RequestBody(required = false) EvaluationRequest request) {
        requireOwnedApplication(authentication, id);
        return applicationService.reject(id, roundNumber,
            request == null ? null : request.getScore(),
            request == null ? null : request.getAdminRemark());
    }

    @PostMapping("/applications/{id}/advance")
    public ApplicationDetail advance(Authentication authentication, @PathVariable String id,
                                     @RequestBody(required = false) EvaluationRequest request) {
        requireOwnedApplication(authentication, id);
        return applicationService.advance(id,
            request == null ? null : request.getScore(),
            request == null ? null : request.getAdminRemark());
    }

    @PostMapping("/applications/{id}/reject")
    public ApplicationDetail rejectCurrent(Authentication authentication, @PathVariable String id,
                                           @RequestBody(required = false) EvaluationRequest request) {
        requireOwnedApplication(authentication, id);
        return applicationService.rejectCurrent(id,
            request == null ? null : request.getScore(),
            request == null ? null : request.getAdminRemark());
    }

    @PutMapping("/applications/{id}/status")
    public Map<String, String> updateApplicationStatus(Authentication authentication, @PathVariable String id,
                                                       @RequestBody Map<String, String> body) {
        requireOwnedApplication(authentication, id);
        if (!applicationService.updateStatus(id, body == null ? null : body.get("status"),
            body == null ? null : body.get("remark"))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found");
        }
        return Map.of("message", "Application status updated");
    }

    @PostMapping("/applications/bulk/advance")
    public Map<String, Object> advanceApplications(Authentication authentication,
                                                   @RequestBody Map<String, Object> body) {
        UsersEntries coordinator = currentCoordinator(authentication);
        return applicationService.bulkAdvance(applicationIds(body),
            body == null ? null : (String) body.get("adminRemark"), ownedApplicationGuard(coordinator));
    }

    @PostMapping("/applications/bulk/reject")
    public Map<String, Object> rejectApplications(Authentication authentication,
                                                  @RequestBody Map<String, Object> body) {
        UsersEntries coordinator = currentCoordinator(authentication);
        return applicationService.bulkReject(applicationIds(body),
            body == null ? null : (String) body.get("adminRemark"), ownedApplicationGuard(coordinator));
    }

    @PostMapping("/applications/bulk/set-round")
    public Map<String, Object> setApplicationRounds(Authentication authentication,
                                                    @RequestBody Map<String, Object> body) {
        UsersEntries coordinator = currentCoordinator(authentication);
        Object rawRound = body == null ? null : body.get("targetRound");
        Integer targetRound = rawRound == null ? null : Integer.valueOf(String.valueOf(rawRound));
        return applicationService.bulkSetRound(applicationIds(body), targetRound,
            body == null ? null : (String) body.get("adminRemark"), ownedApplicationGuard(coordinator));
    }

    // ------------------------------------------------------------------ legacy student selection

    @GetMapping("/jobs/{jobId}/students")
    public List<Map<String, Object>> eligibleStudents(Authentication authentication, @PathVariable String jobId) {
        UsersEntries coordinator = currentCoordinator(authentication);
        return jobSelectionService.getEligibleStudentsForJob(coordinator.getId(), jobId);
    }

    @GetMapping("/jobs/{jobId}/selected-students")
    public List<Map<String, Object>> selectedStudents(Authentication authentication, @PathVariable String jobId) {
        UsersEntries coordinator = currentCoordinator(authentication);
        return jobSelectionService.getSelectedStudentsForJob(coordinator.getId(), jobId);
    }

    @PostMapping("/jobs/{jobId}/select-students")
    public Map<String, Object> selectStudents(Authentication authentication, @PathVariable String jobId,
                                              @RequestBody(required = false) StudentSelectionRequest request) {
        UsersEntries coordinator = currentCoordinator(authentication);
        return jobSelectionService.selectStudentsForJob(coordinator.getId(), jobId,
                request == null ? null : request.getStudentIds());
    }

    @DeleteMapping("/jobs/{jobId}/students/{studentId}")
    public Map<String, Object> removeStudent(Authentication authentication, @PathVariable String jobId,
                                             @PathVariable String studentId) {
        UsersEntries coordinator = currentCoordinator(authentication);
        return jobSelectionService.removeStudentFromJob(coordinator.getId(), jobId, studentId);
    }

    // ------------------------------------------------------------------ helpers

    private List<Map<String, Object>> buildJobSummaries(UsersEntries coordinator) {
        List<Placement> owned = ownedJobs(coordinator);
        Set<String> ownedJobIds = owned.stream().map(Placement::getId).collect(Collectors.toSet());
        Map<String, List<ApplicationDetail>> applicationsByJob =
            applicationService.getApplicationsForPlacements(ownedJobIds).stream()
                .filter(application -> application.getPlacementId() != null)
                .collect(Collectors.groupingBy(ApplicationDetail::getPlacementId));

        List<Map<String, Object>> summaries = new ArrayList<>();
        for (Placement job : owned) {
            List<ApplicationDetail> applications = applicationsByJob.getOrDefault(job.getId(), List.of());
            long selected = applications.stream()
                .filter(app -> "SELECTED".equalsIgnoreCase(app.getStatus())).count();
            long rejected = applications.stream()
                .filter(app -> "REJECTED".equalsIgnoreCase(app.getStatus())).count();
            long pending = applications.stream()
                .filter(app -> "IN_PROGRESS".equalsIgnoreCase(app.getStatus())).count();
            Integer activeRound = applications.stream()
                .filter(app -> "IN_PROGRESS".equalsIgnoreCase(app.getStatus()))
                .map(ApplicationDetail::getCurrentRound)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(null);

            Map<String, Object> info = new LinkedHashMap<>();
            info.put("id", job.getId());
            info.put("company", job.getCompany());
            info.put("jobType", job.getJobType());
            info.put("location", job.getLocation());
            info.put("applicationDeadline", job.getApplicationDeadline());
            info.put("status", job.getStatus());
            info.put("applicantCount", (long) applications.size());
            info.put("selectedCount", selected);
            info.put("rejectedCount", rejected);
            info.put("pendingCount", pending);
            info.put("currentRound", activeRound);
            info.put("rounds", applicationService.effectiveRoundConfig(job).stream()
                .map(round -> {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("round", round.getRound());
                    entry.put("name", round.getName() == null ? "" : round.getName());
                    return entry;
                })
                .collect(Collectors.toList()));
            summaries.add(info);
        }
        return summaries;
    }

    private List<Placement> ownedJobs(UsersEntries coordinator) {
        return placementRepository.findByCoordinatorId(coordinator.getId());
    }

    private Placement requireOwnedJob(Authentication authentication, String jobId) {
        UsersEntries coordinator = currentCoordinator(authentication);
        Placement job = placementRepository.findById(jobId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found"));
        if (!coordinator.getId().equals(job.getCoordinatorId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not own this job");
        }
        return job;
    }

    private Application requireOwnedApplication(Authentication authentication, String applicationId) {
        UsersEntries coordinator = currentCoordinator(authentication);
        Application application = applicationRepository.findById(applicationId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found"));
        Placement job = placementRepository.findById(application.getPlacementId()).orElse(null);
        if (job == null || !coordinator.getId().equals(job.getCoordinatorId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not own this job");
        }
        return application;
    }

    private ApplicationGuard ownedApplicationGuard(UsersEntries coordinator) {
        return application -> {
            Placement job = placementRepository.findById(application.getPlacementId()).orElse(null);
            if (job == null || !coordinator.getId().equals(job.getCoordinatorId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not own this job");
            }
        };
    }

    @SuppressWarnings("unchecked")
    private List<String> applicationIds(Map<String, Object> body) {
        Object rawIds = body == null ? null : body.get("applicationIds");
        if (!(rawIds instanceof List<?>)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one application ID is required");
        }
        List<String> ids = ((List<?>) rawIds).stream().map(String::valueOf).distinct()
            .collect(Collectors.toList());
        if (ids.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one application ID is required");
        }
        return ids;
    }

    private void mergeJob(Placement existing, Placement incoming) {
        if (incoming == null) {
            return;
        }
        if (!blank(incoming.getCompany()) && !blank(incoming.getJobType())) {
            validateJob(incoming);
        }
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
        if (!blank(incoming.getStatus())) {
            existing.setStatus(incoming.getStatus());
        }
        if (incoming.getRounds() != null && !incoming.getRounds().isEmpty()) {
            validateJob(incoming);
            existing.setRounds(incoming.getRounds());
        }
    }

    private void copyIfPresent(String incoming, java.util.function.Consumer<String> setter) {
        if (incoming != null && !incoming.isBlank() && !"None".equalsIgnoreCase(incoming.trim())) {
            setter.accept(incoming.trim());
        }
    }

    private void validateJob(Placement job) {
        if (job == null || blank(job.getCompany()) || blank(job.getJobType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Company and job role are required");
        }
        if (job.getRounds() == null) job.setRounds(new ArrayList<>());
        for (var round : job.getRounds()) {
            if (round == null || round.getRound() == null || round.getRound() < 1
                || blank(round.getName() != null ? round.getName() : round.getRoundType())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Each assessment round needs a number and round type");
            }
            if (round.getName() == null) round.setName(round.getRoundType());
        }
    }

    private boolean blank(String value) { return value == null || value.trim().isEmpty(); }
}
