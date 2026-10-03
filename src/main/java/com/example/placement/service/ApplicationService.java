package com.example.placement.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.aggregation.AggregationUpdate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.mongodb.core.query.UpdateDefinition;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.example.placement.dto.ApplicationDetail;
import com.example.placement.dto.RoundDetail;
import com.example.placement.entity.Application;
import com.example.placement.entity.ApplicationRound;
import com.example.placement.entity.ApplicationStatus;
import com.example.placement.entity.Placement;
import com.example.placement.entity.PlacementRound;
import com.example.placement.entity.RoundStatus;
import com.example.placement.entity.UsersEntries;
import com.example.placement.repository.ApplicationRepository;
import com.example.placement.repository.PlacementRepository;
import com.example.placement.repository.UsersRepository;

@Service
public class ApplicationService {

    private static final Logger log = LoggerFactory.getLogger(ApplicationService.class);

    public static final String STATUS_IN_PROGRESS = ApplicationStatus.IN_PROGRESS.name();
    public static final String STATUS_SELECTED = ApplicationStatus.SELECTED.name();
    public static final String STATUS_REJECTED = ApplicationStatus.REJECTED.name();
    public static final String STATUS_WITHDRAWN = "Withdrawn";

    private static final List<Pattern> TERMINAL_STATUS_PATTERNS = Stream.of(
                    STATUS_SELECTED, STATUS_REJECTED, STATUS_WITHDRAWN)
            .map(status -> Pattern.compile("^" + status + "$", Pattern.CASE_INSENSITIVE))
            .collect(Collectors.toList());

    private static final List<String> ALLOWED_LEGACY_STATUSES = List.of(
            STATUS_IN_PROGRESS, STATUS_SELECTED, STATUS_REJECTED, "Withdrawn",
            "Applied", "In Process", "Under Review", "Shortlisted", "Interview Scheduled", "Placed");

    private final ApplicationRepository applicationRepository;
    private final PlacementRepository placementRepository;
    private final UsersRepository usersRepository;
    private final MongoTemplate mongoTemplate;

    public ApplicationService(ApplicationRepository applicationRepository,
                              PlacementRepository placementRepository,
                              UsersRepository usersRepository,
                              MongoTemplate mongoTemplate) {
        this.applicationRepository = applicationRepository;
        this.placementRepository = placementRepository;
        this.usersRepository = usersRepository;
        this.mongoTemplate = mongoTemplate;
    }

    public String apply(String studentId, String placementId, String resumeUrl) {
        return apply(studentId, placementId, resumeUrl, null, null);
    }

    public String apply(String studentId, String placementId, String resumeUrl, String selectedCourse) {
        return apply(studentId, placementId, resumeUrl, selectedCourse, null);
    }

    public String apply(String studentId, String placementId, String resumeUrl, String selectedCourse,
                        String selectedRole) {
        Optional<Application> existing = applicationRepository.findByStudentIdAndPlacementId(studentId, placementId);
        if (existing.isPresent()) {
            return "already";
        }
        Application application = new Application(studentId, placementId);
        application.setResumeUrl(resumeUrl);
        application.setSelectedCourse(selectedCourse);
        application.setSelectedRole(selectedRole);
        applicationRepository.save(application);
        return "applied";
    }

    public boolean isEligible(Placement job, UsersEntries student) {
        if (job == null || student == null) {
            return false;
        }
        if (!batchAllows(job.getBatch(), student.getBatch())) {
            return false;
        }
        return courseAllows(job.getCourses(), student.getCourse())
                && meetsCutoff(job.getMinTenth(), student.getTenthPercentage())
                && meetsCutoff(job.getMinTwelfth(), student.getTwelfthPercentage())
                && meetsCutoff(job.getMinGraduation(), student.getGraduationPercentage())
                && meetsCutoff(job.getMinCgpa(), student.getCgpa());
    }

    private static boolean courseAllows(String jobCourses, String studentCourse) {
        if (jobCourses == null || jobCourses.trim().isEmpty()) {
            return true;
        }
        if (studentCourse == null || studentCourse.trim().isEmpty()) {
            return false;
        }
        List<String> configured = Arrays.stream(jobCourses.split(","))
                .map(String::trim)
                .filter(course -> !course.isEmpty())
                .collect(Collectors.toList());
        return configured.stream().anyMatch(course -> course.equalsIgnoreCase(studentCourse.trim()));
    }

    private static boolean meetsCutoff(Double requirement, Double value) {
        if (requirement == null) {
            return true;
        }
        return value != null && value >= requirement;
    }

    private static boolean batchAllows(String jobBatch, String studentBatch) {
        if (jobBatch == null || jobBatch.trim().isEmpty()) {
            return true;
        }
        if (studentBatch == null || studentBatch.trim().isEmpty()) {
            return false;
        }
        List<BatchRanges.Range> eligibleBatches = BatchRanges.parse(jobBatch);
        List<BatchRanges.Range> studentBatches = BatchRanges.parse(studentBatch);
        if (eligibleBatches.isEmpty() || studentBatches.isEmpty()) {
            return false;
        }
        return studentBatches.stream().anyMatch(eligibleBatches::contains);
    }

    /**
     * Mirrors UI.batchMatches: exact match, or the student's starting year falls inside a selected
     * range. This filters the applications table by the student's own batch, which is a different
     * question from placement eligibility in {@link #batchAllows}.
     */
    private static boolean batchMatches(String batch, String requested) {
        if (batch == null || batch.isBlank() || requested == null || requested.isBlank()) {
            return false;
        }
        if (batch.trim().equalsIgnoreCase(requested.trim())) {
            return true;
        }
        List<BatchRanges.Range> batchRanges = BatchRanges.parse(batch);
        List<BatchRanges.Range> requestedRanges = BatchRanges.parse(requested);
        if (batchRanges.isEmpty() || requestedRanges.isEmpty()) {
            return false;
        }
        int start = batchRanges.get(0).start();
        return requestedRanges.stream().anyMatch(range -> start >= range.start() && start <= range.end());
    }

    public List<ApplicationDetail> getStudentApplications(String studentId) {
        List<Application> applications = applicationRepository.findByStudentId(studentId);
        return buildDetails(applications);
    }

    /**
     * Dashboard variant that caps the result before detail mapping, so the recent-apps
     * table does not pay to build every application a student has ever submitted.
     */
    public List<ApplicationDetail> getRecentStudentApplications(String studentId, int limit) {
        if (limit <= 0) {
            return getStudentApplications(studentId);
        }
        List<Application> applications = applicationRepository.findByStudentId(studentId);
        List<Application> sorted = new ArrayList<>(applications);
        sorted.sort(Comparator.comparing(
                (Application application) -> application.getApplicationDate(),
                Comparator.nullsLast(Comparator.reverseOrder())));
        return buildDetails(sorted.subList(0, Math.min(limit, sorted.size())));
    }

    public List<ApplicationDetail> getAllApplications() {
        return buildDetails(applicationRepository.findAll());
    }

    public Map<String, Object> getApplicationsPage(Integer page, Integer pageSize,
                                                   String search, String status, String jobId) {
        List<ApplicationDetail> details = buildDetails(applicationRepository.findAll());
        details = filterDetails(details, search, status, jobId);

        long total = details.size();
        int safeSize = pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, 100);
        int safePage = page == null || page < 1 ? 1 : page;
        List<ApplicationDetail> sorted = details.stream()
                .sorted(Comparator.comparing(ApplicationDetail::getApplicationDate,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
        int fromIndex = (int) Math.min((long) (safePage - 1) * safeSize, sorted.size());
        int toIndex = Math.min(fromIndex + safeSize, sorted.size());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("total", total);
        response.put("page", safePage);
        response.put("pageSize", safeSize);
        response.put("applications", sorted.subList(fromIndex, toIndex));
        return response;
    }

    public List<ApplicationDetail> filterDetails(List<ApplicationDetail> details, String search,
                                                 String status, String jobId) {
        if (details == null || details.isEmpty()) {
            return List.of();
        }
        List<ApplicationDetail> filtered = details;
        if (status != null && !status.isBlank()) {
            String requested = status.trim();
            filtered = filtered.stream()
                    .filter(detail -> requested.equalsIgnoreCase(detail.getStatus()))
                    .collect(Collectors.toList());
        }
        if (jobId != null && !jobId.isBlank()) {
            filtered = filtered.stream()
                    .filter(detail -> jobId.equals(detail.getPlacementId()))
                    .collect(Collectors.toList());
        }
        String query = search == null ? null : search.trim().toLowerCase();
        if (query != null && !query.isEmpty()) {
            filtered = filtered.stream().filter(detail -> matchesSearch(detail, query))
                    .collect(Collectors.toList());
        }
        return filtered;
    }

    /**
     * Applies the remaining applications-table filters (company, batch, branch, applied-date range)
     * so an export honours exactly what the admin sees on screen.
     */
    public List<ApplicationDetail> filterDetails(List<ApplicationDetail> details, String search,
                                                 String status, String jobId, String company,
                                                 String batch, String branch,
                                                 LocalDate appliedFrom, LocalDate appliedTo) {
        List<ApplicationDetail> filtered = filterDetails(details, search, status, jobId);
        if (filtered.isEmpty()) {
            return filtered;
        }
        if (company != null && !company.isBlank()) {
            String requested = company.trim();
            filtered = filtered.stream()
                    .filter(detail -> requested.equalsIgnoreCase(detail.getCompany()))
                    .collect(Collectors.toList());
        }
        if (batch != null && !batch.isBlank()) {
            String requested = batch.trim();
            filtered = filtered.stream()
                    .filter(detail -> batchMatches(detail.getBatch(), requested))
                    .collect(Collectors.toList());
        }
        if (branch != null && !branch.isBlank()) {
            String requested = branch.trim();
            filtered = filtered.stream()
                    .filter(detail -> requested.equalsIgnoreCase(detail.getBranch()))
                    .collect(Collectors.toList());
        }
        if (appliedFrom != null) {
            filtered = filtered.stream()
                    .filter(detail -> detail.getApplicationDate() != null
                            && !detail.getApplicationDate().toLocalDate().isBefore(appliedFrom))
                    .collect(Collectors.toList());
        }
        if (appliedTo != null) {
            filtered = filtered.stream()
                    .filter(detail -> detail.getApplicationDate() != null
                            && !detail.getApplicationDate().toLocalDate().isAfter(appliedTo))
                    .collect(Collectors.toList());
        }
        return filtered;
    }

    private static boolean matchesSearch(ApplicationDetail detail, String query) {
        return Stream.of(detail.getEnrollmentno(), detail.getStudentName(), detail.getStudentEmail(),
                        detail.getCompany(), detail.getEffectiveRole(), detail.getSelectedCourse(),
                        detail.getStudentCourse())
                .filter(value -> value != null && !value.isBlank())
                .anyMatch(value -> value.toLowerCase().contains(query));
    }

    public List<ApplicationDetail> getApplicationsForPlacement(String placementId) {
        if (placementId == null || placementId.isBlank()) {
            return List.of();
        }
        return buildDetails(applicationRepository.findByPlacementId(placementId));
    }

    public List<ApplicationDetail> getApplicationsForPlacements(Collection<String> placementIds) {
        if (placementIds == null || placementIds.isEmpty()) {
            return List.of();
        }
        return buildDetails(applicationRepository.findByPlacementIdIn(placementIds));
    }

    public boolean updateStatus(String applicationId, String status, String remark) {
        String requested = status == null ? "" : status.trim();
        boolean known = ALLOWED_LEGACY_STATUSES.stream().anyMatch(allowed -> allowed.equalsIgnoreCase(requested));
        if (!known) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Status must be one of: " + String.join(", ", ALLOWED_LEGACY_STATUSES));
        }
        Optional<Application> optional = applicationRepository.findById(applicationId);
        if (optional.isEmpty()) {
            return false;
        }
        Application application = optional.get();
        application.setStatus(requested);
        application.setLastUpdated(LocalDateTime.now());
        if (remark != null && !remark.trim().isEmpty()) {
            application.setAdminRemark(remark.trim());
        }
        applicationRepository.save(application);
        return true;
    }

    public ApplicationDetail qualify(String applicationId, Integer roundNumber, Double score, String adminRemark) {
        return evaluate(applicationId, roundNumber, score, adminRemark, true);
    }

    public ApplicationDetail reject(String applicationId, Integer roundNumber, Double score, String adminRemark) {
        return evaluate(applicationId, roundNumber, score, adminRemark, false);
    }

    public ApplicationDetail advance(String applicationId, Double score, String adminRemark) {
        Application application = requireApplication(applicationId);
        return evaluate(applicationId, effectiveRound(application), score, adminRemark, true);
    }

    public ApplicationDetail rejectCurrent(String applicationId, Double score, String adminRemark) {
        Application application = requireApplication(applicationId);
        return evaluate(applicationId, effectiveRound(application), score, adminRemark, false);
    }

    public Map<String, Object> bulkAdvance(List<String> applicationIds, String adminRemark) {
        return bulkEvaluate(applicationIds, adminRemark, true, null);
    }

    public Map<String, Object> bulkAdvance(List<String> applicationIds, String adminRemark, ApplicationGuard guard) {
        return bulkEvaluate(applicationIds, adminRemark, true, guard);
    }

    public Map<String, Object> bulkReject(List<String> applicationIds, String adminRemark) {
        return bulkEvaluate(applicationIds, adminRemark, false, null);
    }

    public Map<String, Object> bulkReject(List<String> applicationIds, String adminRemark, ApplicationGuard guard) {
        return bulkEvaluate(applicationIds, adminRemark, false, guard);
    }

    @FunctionalInterface
    public interface ApplicationGuard {
        void check(Application application);
    }

    public ApplicationDetail setCurrentRound(String applicationId, Integer targetRound, String adminRemark) {
        if (targetRound == null || targetRound < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Round number must be a positive integer");
        }
        Application application = requireApplication(applicationId);
        String terminal = terminalBlock(application);
        if (terminal != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, terminal);
        }

        Query query = new Query(new Criteria().andOperator(
                Criteria.where("_id").is(applicationId),
                Criteria.where("status").nin(TERMINAL_STATUS_PATTERNS)));
        Application updated = mongoTemplate.findAndModify(query,
                setRoundUpdate(targetRound, adminRemark),
                FindAndModifyOptions.options().returnNew(true), Application.class);
        if (updated == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Application was updated concurrently, please refresh");
        }
        return buildDetails(List.of(updated)).get(0);
    }

    private UpdateDefinition setRoundUpdate(int targetRound, String adminRemark) {
        Document freshEntry = new Document("round", targetRound)
                .append("status", RoundStatus.PENDING.name());
        Document markedRounds = new Document("$map",
                new Document("input", "$rounds").append("as", "entry")
                        .append("in", new Document("$mergeObjects", Arrays.asList(
                                "$$entry",
                                new Document("$cond", Arrays.asList(
                                        new Document("$eq", Arrays.asList("$$entry.round", targetRound)),
                                        freshEntry,
                                        new Document()))))));
        Document entryExists = new Document("$in", Arrays.asList(targetRound,
                new Document("$ifNull", Arrays.asList("$rounds.round", List.of()))));
        List<Object> concatParts = new ArrayList<>();
        concatParts.add(markedRounds);
        concatParts.add(new Document("$cond", Arrays.asList(
                entryExists, List.of(), List.of(freshEntry))));

        Document finalState = new Document("lastUpdated", "$$NOW")
                .append("currentRound", targetRound)
                .append("status", STATUS_IN_PROGRESS);
        if (adminRemark != null && !adminRemark.trim().isEmpty()) {
            finalState.append("adminRemark", adminRemark.trim());
        }

        return AggregationUpdate.from(Arrays.asList(
                context -> new Document("$set",
                        new Document("rounds", new Document("$concatArrays", concatParts))),
                context -> new Document("$set", finalState)));
    }

    public Map<String, Object> bulkSetRound(List<String> applicationIds, Integer targetRound, String adminRemark) {
        return bulkSetRound(applicationIds, targetRound, adminRemark, null);
    }

    public Map<String, Object> bulkSetRound(List<String> applicationIds, Integer targetRound, String adminRemark,
                                            ApplicationGuard guard) {
        List<Map<String, Object>> results = new ArrayList<>();
        int moved = 0;
        int failed = 0;
        for (String applicationId : applicationIds == null ? List.<String>of() : applicationIds) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("applicationId", applicationId);
            try {
                Application application = requireApplication(applicationId);
                if (guard != null) {
                    guard.check(application);
                }
                if (targetRound == null || targetRound < 1) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Round number must be a positive integer");
                }
                int fromRound = effectiveRound(application);
                ApplicationDetail updated = setCurrentRound(applicationId, targetRound, adminRemark);
                item.put("status", "SET");
                item.put("fromRound", fromRound);
                item.put("toRound", targetRound);
                item.put("application", updated);
                moved++;
            } catch (ResponseStatusException exception) {
                item.put("status", "FAILED");
                item.put("message", exception.getReason() != null
                        ? exception.getReason() : "Could not be processed");
                failed++;
            } catch (Exception unexpected) {
                log.warn("Bulk set-round failed unexpectedly for application {}", applicationId, unexpected);
                item.put("status", "FAILED");
                item.put("message", "Unexpected error while processing this application");
                failed++;
            }
            results.add(item);
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("message", failed == 0
                ? "Candidates moved to Round " + targetRound
                : moved + " moved to Round " + targetRound + ", " + failed + " could not be updated");
        summary.put("moved", moved);
        summary.put("failed", failed);
        summary.put("results", results);
        return summary;
    }

    private Map<String, Object> bulkEvaluate(List<String> applicationIds, String adminRemark, boolean advancing,
                                             ApplicationGuard guard) {
        List<Map<String, Object>> results = new ArrayList<>();
        int moved = 0;
        int failed = 0;
        for (String applicationId : applicationIds == null ? List.<String>of() : applicationIds) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("applicationId", applicationId);
            try {
                Application application = requireApplication(applicationId);
                if (guard != null) {
                    guard.check(application);
                }
                int fromRound = effectiveRound(application);
                ApplicationDetail updated = evaluate(applicationId, fromRound, null, adminRemark, advancing);
                item.put("status", advancing ? "MOVED" : "REJECTED");
                item.put("fromRound", fromRound);
                item.put("toRound", updated.getCurrentRound());
                item.put("application", updated);
                moved++;
            } catch (ResponseStatusException exception) {
                item.put("status", "FAILED");
                item.put("message", exception.getReason() != null
                        ? exception.getReason() : "Could not be processed");
                failed++;
            } catch (Exception unexpected) {
                log.warn("Bulk {} failed unexpectedly for application {}", advancing ? "advance" : "reject",
                        applicationId, unexpected);
                item.put("status", "FAILED");
                item.put("message", "Unexpected error while processing this application");
                failed++;
            }
            results.add(item);
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("message", advancing
                ? (failed == 0 ? "Candidates moved successfully"
                        : moved + " moved, " + failed + " could not be moved")
                : (failed == 0 ? "Candidates rejected successfully"
                        : moved + " rejected, " + failed + " could not be rejected"));
        summary.put("moved", moved);
        summary.put("failed", failed);
        summary.put("results", results);
        return summary;
    }

    private ApplicationDetail evaluate(String applicationId, Integer requestedRound, Double score,
                                       String adminRemark, boolean qualifying) {
        Application application = requireApplication(applicationId);
        int currentRound = effectiveRound(application);
        int roundNumber = requireCurrentRound(currentRound, requestedRound);

        Placement placement = requirePlacement(application.getPlacementId());
        List<Integer> configuredRounds = configuredRounds(effectiveRoundConfig(placement));
        if (!configuredRounds.contains(roundNumber)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Round " + roundNumber + " is not configured for this placement");
        }

        String conflict = describeConflict(application, roundNumber);
        if (conflict != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, conflict);
        }
        validateScore(score);

        ensureInitialRound(application);

        Integer nextRound = qualifying
                ? configuredRounds.stream().filter(number -> number > roundNumber).findFirst().orElse(null)
                : null;

        UpdateDefinition update = progressionUpdate(roundNumber, score, adminRemark, qualifying, nextRound);

        Query query = evaluationQuery(applicationId, currentRound, roundNumber);
        Application updated = mongoTemplate.findAndModify(query, update,
                FindAndModifyOptions.options().returnNew(true), Application.class);
        if (updated == null) {
            throw evaluationConflict(applicationId, roundNumber, qualifying);
        }
        return buildDetails(List.of(updated)).get(0);
    }

    private UpdateDefinition progressionUpdate(int roundNumber, Double score, String adminRemark,
                                              boolean qualifying, Integer nextRound) {
        Document evaluatedPatch = new Document("status",
                (qualifying ? RoundStatus.QUALIFIED : RoundStatus.REJECTED).name());
        if (score != null) {
            evaluatedPatch.append("score", score);
        }
        if (adminRemark != null && !adminRemark.trim().isEmpty()) {
            evaluatedPatch.append("adminRemark", adminRemark.trim());
        }
        evaluatedPatch.append("evaluatedAt", "$$NOW");

        Document markedRounds = new Document("$map",
                new Document("input", "$rounds").append("as", "entry")
                        .append("in", new Document("$mergeObjects", Arrays.asList(
                                "$$entry",
                                new Document("$cond", Arrays.asList(
                                        new Document("$eq", Arrays.asList("$$entry.round", roundNumber)),
                                        evaluatedPatch,
                                        new Document()))))));

        List<Object> concatParts = new ArrayList<>();
        concatParts.add(markedRounds);
        if (qualifying && nextRound != null) {
            concatParts.add(List.of(new Document("round", nextRound)
                    .append("status", RoundStatus.PENDING.name())));
        } else {
            concatParts.add(List.of());
        }

        Document finalState = new Document("lastUpdated", "$$NOW");
        if (qualifying && nextRound != null) {
            finalState.append("status", STATUS_IN_PROGRESS).append("currentRound", nextRound);
        } else if (qualifying) {
            finalState.append("status", STATUS_SELECTED);
        } else {
            finalState.append("status", STATUS_REJECTED);
        }

        return AggregationUpdate.from(Arrays.asList(
                context -> new Document("$set", new Document("rounds", new Document("$concatArrays", concatParts))),
                context -> new Document("$set", finalState)));
    }

    private Application requireApplication(String applicationId) {
        Optional<Application> optional = applicationRepository.findById(applicationId);
        if (optional.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found");
        }
        return optional.get();
    }

    private static int effectiveRound(Application application) {
        return application.getCurrentRound() != null ? application.getCurrentRound() : 1;
    }

    private Placement requirePlacement(String placementId) {
        Optional<Placement> optional = placementRepository.findById(placementId);
        if (optional.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Associated placement does not exist");
        }
        return optional.get();
    }

    private int requireCurrentRound(int currentRound, Integer requestedRound) {
        if (requestedRound == null) {
            return currentRound;
        }
        if (requestedRound.intValue() != currentRound) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Round mismatch: application is currently on Round " + currentRound);
        }
        return requestedRound;
    }

    public List<PlacementRound> effectiveRoundConfig(Placement placement) {
        if (placement == null || placement.getRounds() == null) {
            return List.of();
        }
        return placement.getRounds().stream()
                .filter(round -> round != null)
                .sorted(Comparator.comparing(round -> round.getRound() == null ? 0 : round.getRound()))
                .collect(Collectors.toList());
    }

    public Map<String, List<PlacementRound>> roundConfigByJob(Set<String> jobIds, Map<String, Placement> jobMap) {
        if (jobIds == null || jobIds.isEmpty()) {
            return Map.of();
        }
        Map<String, List<PlacementRound>> configs = new HashMap<>();
        for (String jobId : jobIds) {
            Placement job = jobMap == null ? null : jobMap.get(jobId);
            configs.put(jobId, job == null ? List.of() : effectiveRoundConfig(job));
        }
        return configs;
    }

    private static List<Integer> configuredRounds(List<PlacementRound> config) {
        List<Integer> numbers = config == null ? List.of()
                : config.stream()
                        .filter(round -> round != null && round.getRound() != null && round.getRound() >= 1)
                        .map(PlacementRound::getRound)
                        .distinct()
                        .sorted()
                        .collect(Collectors.toList());
        return numbers.isEmpty() ? List.of(1) : numbers;
    }

    private void ensureInitialRound(Application application) {
        if (application.getRounds() != null && !application.getRounds().isEmpty()) {
            return;
        }
        if (effectiveRound(application) > 1) {
            return;
        }
        Query query = new Query(new Criteria().andOperator(
                Criteria.where("_id").is(application.getId()),
                new Criteria().orOperator(Criteria.where("currentRound").isNull(),
                        Criteria.where("currentRound").is(1)),
                new Criteria().orOperator(Criteria.where("rounds").exists(false),
                        Criteria.where("rounds").size(0))));
        mongoTemplate.updateFirst(query,
                new Update().push("rounds").value(new ApplicationRound(1)), Application.class);
    }

    private Query evaluationQuery(String applicationId, int expectedRound, int roundNumber) {
        Criteria idMatches = Criteria.where("_id").is(applicationId);
        Criteria roundMatches = expectedRound <= 1
                ? new Criteria().orOperator(Criteria.where("currentRound").is(1),
                        Criteria.where("currentRound").isNull())
                : Criteria.where("currentRound").is(expectedRound);
        Criteria notTerminal = Criteria.where("status").nin(TERMINAL_STATUS_PATTERNS);
        Criteria pendingEntry = Criteria.where("rounds").elemMatch(
                Criteria.where("round").is(roundNumber).and("status").is(RoundStatus.PENDING.name()));
        return new Query(new Criteria().andOperator(idMatches, roundMatches, notTerminal, pendingEntry));
    }

    private ResponseStatusException evaluationConflict(String applicationId, int roundNumber, boolean qualifying) {
        return applicationRepository.findById(applicationId)
                .<ResponseStatusException>map(latest -> new ResponseStatusException(HttpStatus.CONFLICT,
                        conflictMessage(latest, roundNumber)))
                .orElseGet(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found"));
    }

    private String describeConflict(Application application, int roundNumber) {
        String terminal = terminalBlock(application);
        if (terminal != null) {
            return terminal;
        }
        ApplicationRound existing = roundEntry(application, roundNumber);
        if (existing != null && existing.getStatus() != RoundStatus.PENDING) {
            return "Round " + roundNumber + " has already been evaluated (" + existing.getStatus() + ")";
        }
        return null;
    }

    private String terminalBlock(Application application) {
        String status = application.getStatus();
        if (STATUS_SELECTED.equalsIgnoreCase(status)) {
            return "Application is already selected";
        }
        if (STATUS_REJECTED.equalsIgnoreCase(status)) {
            return "Application is already rejected";
        }
        if (STATUS_WITHDRAWN.equalsIgnoreCase(status)) {
            return "Application was withdrawn";
        }
        return null;
    }

    private ApplicationRound roundEntry(Application application, int roundNumber) {
        if (application.getRounds() == null) {
            return null;
        }
        return application.getRounds().stream()
                .filter(entry -> entry != null && entry.getRound() != null && entry.getRound() == roundNumber)
                .findFirst()
                .orElse(null);
    }

    private String conflictMessage(Application application, int roundNumber) {
        String conflict = describeConflict(application, roundNumber);
        return conflict != null ? conflict : "Application was updated concurrently, please refresh";
    }

    private void validateScore(Double score) {
        if (score != null && score < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Score must not be negative");
        }
    }

    private List<ApplicationDetail> buildDetails(List<Application> applications) {
        if (applications.isEmpty()) {
            return List.of();
        }

        Set<String> userIds = applications.stream()
                .map(Application::getStudentId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        Set<String> jobIds = applications.stream()
                .map(Application::getPlacementId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());

        Map<String, UsersEntries> userById = new HashMap<>();
        Map<String, UsersEntries> userByEnrollment = new HashMap<>();
        usersRepository.findAllById(userIds).forEach(user -> {
            userById.put(user.getId(), user);
            String normalizedEnrollment = normalizeKey(user.getEnrollmentno());
            if (normalizedEnrollment != null) {
                userByEnrollment.put(normalizedEnrollment, user);
            }
        });
        Map<String, Placement> jobMap = new HashMap<>();
        placementRepository.findAllById(jobIds).forEach(job -> jobMap.put(job.getId(), job));
        Map<String, List<PlacementRound>> configByJob = roundConfigByJob(jobIds, jobMap);

        return applications.stream().map(application -> {
            UsersEntries user = resolveUser(application.getStudentId(), userById, userByEnrollment);
            Placement job = jobMap.get(application.getPlacementId());
            List<PlacementRound> config = configByJob.getOrDefault(application.getPlacementId(), List.of());
            return mapDetail(application, user, job, config);
        }).collect(Collectors.toList());
    }

    static ApplicationDetail mapDetail(Application application, UsersEntries student,
                                       Placement job, List<PlacementRound> config) {
        ApplicationDetail detail = new ApplicationDetail();
        detail.setApplicationId(application.getId());
        detail.setStudentId(application.getStudentId());
        detail.setPlacementId(application.getPlacementId());
        detail.setStatus(application.getStatus());
        detail.setCurrentRound(effectiveRound(application));
        detail.setTotalRounds(totalConfiguredRounds(job, config, application));
        detail.setRounds(mapRoundHistory(application, config));
        detail.setApplicationDate(application.getApplicationDate());
        detail.setLastUpdated(application.getLastUpdated());
        detail.setResumeUrl(application.getResumeUrl());
        detail.setSelectedCourse(application.getSelectedCourse());
        detail.setAdminRemark(application.getAdminRemark());

        if (student != null) {
            String enrollment = normalizeValue(student.getEnrollmentno());
            String name = normalizeValue(student.getName());
            String email = normalizeValue(student.getEmail());
            detail.setEnrollmentno(enrollment);
            detail.setPhoneNo(normalizeValue(student.getPhoneno()));
            detail.setStudentName(name != null && !name.isEmpty() ? name
                    : (enrollment != null && !enrollment.isEmpty() ? enrollment : ""));
            detail.setStudentEmail(email != null && !email.isEmpty() ? email : "");
            detail.setStudentCourse(student.getCourse());
            detail.setStudentBatch(student.getBatch());
            detail.setStudentStatus(student.getStudentStatus());
            detail.setStudentRole(student.getRole());
            detail.setBranch(student.getBranch());
            detail.setSection(student.getSection());
            detail.setDepartment(student.getDepartment());
            detail.setCollege(student.getCollege());
            detail.setCgpa(student.getCgpa());
            detail.setTenthPercentage(student.getTenthPercentage());
            detail.setTwelfthPercentage(student.getTwelfthPercentage());
            detail.setGraduationPercentage(student.getGraduationPercentage());
            detail.setDob(student.getDob());
            detail.setGender(student.getGender());
            detail.setAddress(student.getAddress());
            detail.setCity(student.getCity());
            detail.setState(student.getState());
            detail.setBatch(student.getBatch());
        }

        if (job != null) {
            detail.setJobId(job.getId());
            detail.setCompany(job.getCompany());
            detail.setRole(job.getJobRole() != null && !job.getJobRole().isBlank()
                    ? job.getJobRole() : job.getJobType());
            detail.setJobType(detail.getRole());
            detail.setSelectedRole(application.getSelectedRole());
                detail.setSalary(job.getSalaryLpa() != null && !job.getSalaryLpa().isBlank()
                    ? job.getSalaryLpa() : job.getStipend());
            detail.setSkills(job.getSkills());
            detail.setEligibility(job.getEligibility());
            detail.setLocation(job.getLocation());
            detail.setProgramFee(job.getProgramFee());
            detail.setDuration(job.getDuration());
            detail.setApplicationDeadline(job.getApplicationDeadline());
            detail.setApplicationLink(job.getApplicationLink());
            detail.setJobDescription(job.getJobDescription());
            detail.setOpenings(job.getOpenings());
            detail.setEligibleBatch(job.getBatch());
            detail.setEligibleBranch(job.getBranch());
        }
        return detail;
    }

    private static List<RoundDetail> mapRoundHistory(Application application, List<PlacementRound> config) {
        List<ApplicationRound> history = application.getRounds() == null ? List.of() : application.getRounds();
        return history.stream()
                .filter(entry -> entry != null)
                .sorted(Comparator.comparing(entry -> entry.getRound() == null ? 0 : entry.getRound()))
                .map(entry -> {
                    RoundDetail detail = new RoundDetail();
                    detail.setRound(entry.getRound());
                    detail.setName(roundName(config, entry.getRound()));
                    detail.setStatus(entry.getStatus() == null ? RoundStatus.PENDING.name() : entry.getStatus().name());
                    detail.setScore(entry.getScore());
                    detail.setAdminRemark(entry.getAdminRemark());
                    detail.setEvaluatedAt(entry.getEvaluatedAt());
                    return detail;
                })
                .collect(Collectors.toList());
    }

    private static String roundName(List<PlacementRound> config, Integer roundNumber) {
        if (config == null || roundNumber == null) {
            return null;
        }
        return config.stream()
                .filter(round -> round != null && roundNumber.equals(round.getRound()))
                .map(PlacementRound::getName)
                .filter(name -> name != null && !name.trim().isEmpty())
                .findFirst()
                .orElse(null);
    }

    private static Integer totalConfiguredRounds(Placement job, List<PlacementRound> config, Application application) {
        int configured = configuredRounds(config).size();
        if (configured > 1 || job != null) {
            return Math.max(configured, effectiveRound(application));
        }
        int reached = effectiveRound(application);
        if (application.getRounds() != null && !application.getRounds().isEmpty()) {
            reached = Math.max(reached, application.getRounds().size());
        }
        return reached;
    }

    private UsersEntries resolveUser(String studentId, Map<String, UsersEntries> userById,
                                     Map<String, UsersEntries> userByEnrollment) {
        if (studentId == null || studentId.trim().isEmpty()) {
            return null;
        }
        UsersEntries byId = userById.get(studentId);
        if (byId != null) {
            return byId;
        }
        String normalized = normalizeKey(studentId);
        if (normalized != null && userById.containsKey(normalized)) {
            return userById.get(normalized);
        }
        return normalized == null ? null : userByEnrollment.get(normalized);
    }

    private static String normalizeKey(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed.toLowerCase();
    }

    private static String normalizeValue(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public Map<String, Long> studentStats(String studentId) {
        List<Application> applications = applicationRepository.findByStudentId(studentId);
        return countStats(applications);
    }

    public Map<String, Long> adminStats() {
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.project().and("status").toUpper().as("normalizedStatus"),
                Aggregation.group("normalizedStatus").count().as("count"));
        AggregationResults<Document> results =
                mongoTemplate.aggregate(aggregation, "Application", Document.class);

        long total = 0;
        long underReview = 0;
        long shortlisted = 0;
        long selected = 0;
        long rejected = 0;
        long inProgress = 0;
        long withdrawn = 0;
        for (Document document : results) {
            Number count = document.get("count", Number.class);
            long value = count == null ? 0 : count.longValue();
            total += value;
            String status = document.getString("_id");
            if ("UNDER REVIEW".equals(status)) {
                underReview += value;
            } else if ("SHORTLISTED".equals(status)) {
                shortlisted += value;
            } else if (STATUS_SELECTED.equals(status)) {
                selected += value;
            } else if (STATUS_REJECTED.equals(status)) {
                rejected += value;
            } else if (STATUS_IN_PROGRESS.equals(status)) {
                inProgress += value;
            } else if (STATUS_WITHDRAWN.toUpperCase().equals(status)) {
                withdrawn += value;
            }
        }

        Map<String, Long> stats = new HashMap<>();
        stats.put("total", total);
        stats.put("underReview", underReview);
        stats.put("shortlisted", shortlisted);
        stats.put("selected", selected);
        stats.put("rejected", rejected);
        stats.put("inProgress", inProgress);
        stats.put("withdrawn", withdrawn);
        return stats;
    }

    public void deleteApplicationsForJob(String placementId) {
        applicationRepository.deleteByPlacementId(placementId);
    }

    private Map<String, Long> countStats(List<Application> applications) {
        Map<String, Long> stats = new HashMap<>();
        stats.put("total", (long) applications.size());
        long shortlisted = 0;
        long selected = 0;
        long rejected = 0;
        long inProgress = 0;
        long withdrawn = 0;
        long interviewScheduled = 0;
        for (Application application : applications) {
            String status = application.getStatus() == null ? ""
                    : application.getStatus().trim().toUpperCase(Locale.ROOT);
            if (STATUS_SELECTED.equals(status) || "PLACED".equals(status)) {
                selected++;
            } else if (STATUS_REJECTED.equals(status)) {
                rejected++;
            } else if (STATUS_WITHDRAWN.toUpperCase(Locale.ROOT).equals(status)) {
                withdrawn++;
            } else if ("SHORTLISTED".equals(status) || hasQualifiedRound(application)) {
                shortlisted++;
            } else if (STATUS_IN_PROGRESS.equals(status) || "APPLIED".equals(status)
                    || "IN PROCESS".equals(status) || "UNDER REVIEW".equals(status)) {
                inProgress++;
            } else if ("INTERVIEW SCHEDULED".equals(status)) {
                // Counted on its own so it is not double counted as in progress.
                interviewScheduled++;
            }
        }
        stats.put("underReview", inProgress);
        stats.put("shortlisted", shortlisted);
        stats.put("selected", selected);
        stats.put("rejected", rejected);
        stats.put("inProgress", inProgress);
        stats.put("interviewScheduled", interviewScheduled);
        stats.put("withdrawn", withdrawn);
        return stats;
    }

    private boolean hasQualifiedRound(Application application) {
        return application.getRounds() != null && application.getRounds().stream()
                .filter(round -> round != null)
                .anyMatch(round -> round.getStatus() == RoundStatus.QUALIFIED
                        || round.getStatus() == RoundStatus.CLEARED);
    }
}
