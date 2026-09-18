package com.example.placement.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.example.placement.dto.ApplicationDetail;
import com.example.placement.dto.StudentDetail;
import com.example.placement.dto.StudentListResponse;
import com.example.placement.entity.Application;
import com.example.placement.entity.ApplicationStatus;
import com.example.placement.entity.Placement;
import com.example.placement.entity.PlacementRound;
import com.example.placement.entity.UsersEntries;
import com.example.placement.repository.ApplicationRepository;
import com.example.placement.repository.PlacementRepository;
import com.example.placement.repository.UsersRepository;

@Service
public class StudentService {

    private final UsersRepository usersRepository;
    private final ApplicationRepository applicationRepository;
    private final PlacementRepository placementRepository;
    private final ApplicationService applicationService;
    private final MongoTemplate mongoTemplate;

    public StudentService(UsersRepository usersRepository,
                          ApplicationRepository applicationRepository,
                          PlacementRepository placementRepository,
                          ApplicationService applicationService,
                          MongoTemplate mongoTemplate) {
        this.usersRepository = usersRepository;
        this.applicationRepository = applicationRepository;
        this.placementRepository = placementRepository;
        this.applicationService = applicationService;
        this.mongoTemplate = mongoTemplate;
    }

    public List<String> knownStatuses() {
        return List.of("Active", "Inactive", "Placed", "Not Placed");
    }

    public Map<String, Object> filterOptions() {
        List<UsersEntries> students = usersRepository.findAll().stream()
                .filter(this::isStudentRole)
                .collect(Collectors.toList());
        return filterOptions(students, placementRepository.findAll());
    }

    private Map<String, Object> filterOptions(List<UsersEntries> students, List<Placement> placements) {
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("batches", distinctStrings(students, UsersEntries::getBatch));
        options.put("branches", distinctStrings(students, UsersEntries::getBranch));
        options.put("sections", distinctStrings(students, UsersEntries::getSection));
        options.put("studentStatuses", knownStatuses());
        options.put("applicationStatuses", List.of("IN_PROGRESS", "SELECTED", "REJECTED", "WITHDRAWN",
                "Applied", "Under Review", "Shortlisted", "Interview Scheduled"));
        options.put("companies", placements.stream()
                .map(Placement::getCompany)
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .sorted()
                .collect(Collectors.toList()));
        options.put("jobs", placements.stream()
                .map(Placement::getJobType)
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .sorted()
                .collect(Collectors.toList()));
        options.put("rounds", List.of("1", "2", "3", "4", "5", "6"));
        return options;
    }

    private List<String> distinctStrings(List<UsersEntries> students,
                                         java.util.function.Function<UsersEntries, String> getter) {
        return students.stream()
                .map(getter)
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    public StudentListResponse listStudents(String search, List<String> batches, List<String> branches,
                                        List<String> sections, String studentStatus, String applicationStatus,
                                        String company, String job, Integer round, String appliedFrom,
                                        String appliedTo, int page, int pageSize) {
        return doListStudents(search, batches, branches, sections, studentStatus, applicationStatus, company,
                job, round, appliedFrom, appliedTo, page, pageSize, false);
    }

    public StudentListResponse listStudentsForReport(String search, List<String> batches, List<String> branches,
                                                     List<String> sections, String studentStatus,
                                                     String applicationStatus, String company, String job,
                                                     Integer round, String appliedFrom, String appliedTo,
                                                     int page, int pageSize) {
        return doListStudents(search, batches, branches, sections, studentStatus, applicationStatus, company,
                job, round, appliedFrom, appliedTo, page, pageSize, true);
    }

    public StudentListResponse doListStudents(String search, List<String> batches, List<String> branches,
                                              List<String> sections, String studentStatus,
                                              String applicationStatus, String company, String job,
                                              Integer round, String appliedFrom, String appliedTo,
                                              int page, int pageSize, boolean includeApplications) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, pageSize));

        List<UsersEntries> allStudents = usersRepository.findAll().stream()
                .filter(this::isStudentRole)
                .collect(Collectors.toList());

        List<Application> allApplications = applicationRepository.findAll();
        Map<String, List<Application>> appsByStudent = allApplications.stream()
                .collect(Collectors.groupingBy(app -> app.getStudentId(),
                        Collectors.toCollection(ArrayList::new)));

        ApplicationDetailsResolver resolver = new ApplicationDetailsResolver();

        boolean needApps = includeApplications
                || (applicationStatus != null && !applicationStatus.isBlank())
                || (company != null && !company.isBlank())
                || (job != null && !job.isBlank())
                || round != null
                || (appliedFrom != null && !appliedFrom.isBlank())
                || (appliedTo != null && !appliedTo.isBlank());

        List<StudentDetail> filtered = allStudents.stream()
                .map(student -> toDetail(student, appsByStudent.getOrDefault(student.getId(), List.of()), resolver, needApps))
                .filter(detail -> matches(detail, search, batches, branches, sections, studentStatus,
                        applicationStatus, company, job, round, appliedFrom, appliedTo))
                .collect(Collectors.toList());

        int total = filtered.size();
        int totalPages = (int) Math.ceil((double) total / safeSize);
        int fromIndex = Math.min((safePage - 1) * safeSize, total);
        int toIndex = Math.min(fromIndex + safeSize, total);
        List<StudentDetail> pageStudents = fromIndex < toIndex ? filtered.subList(fromIndex, toIndex) : List.of();

        Map<String, Object> stats = Map.of(
                "total", (long) total,
                "active", filtered.stream().filter(s -> "Active".equalsIgnoreCase(s.getStudentStatus())).count(),
                "placed", filtered.stream().filter(s -> "Placed".equalsIgnoreCase(s.getStudentStatus())).count(),
                "notPlaced", filtered.stream().filter(s -> "Not Placed".equalsIgnoreCase(s.getStudentStatus())).count());

        return new StudentListResponse(total, safePage, safeSize, totalPages, pageStudents, stats,
                filterOptions(allStudents, placementRepository.findAll()));
    }

    private boolean matches(StudentDetail detail, String search, List<String> batches, List<String> branches,
                        List<String> sections, String studentStatus, String applicationStatus, String company,
                        String job, Integer round, String appliedFrom, String appliedTo) {
        if (search != null && !search.isBlank()) {
            String needle = search.trim().toLowerCase();
            boolean searchHit = contains(detail.getName(), needle)
                    || contains(detail.getEmail(), needle)
                    || contains(detail.getEnrollmentno(), needle)
                    || contains(detail.getPhoneno(), needle);
            if (!searchHit) {
                return false;
            }
        }
        if (batches != null && !batches.isEmpty() && !matchesAny(detail.getBatch(), batches)) {
            return false;
        }
        if (branches != null && !branches.isEmpty() && !matchesAny(detail.getBranch(), branches)) {
            return false;
        }
        if (sections != null && !sections.isEmpty() && !matchesAny(detail.getSection(), sections)) {
            return false;
        }
        if (studentStatus != null && !studentStatus.isBlank()
                && !studentStatus.equalsIgnoreCase(detail.getStudentStatus())
                && !(studentStatus.equalsIgnoreCase("Not Placed")
                && detail.getStudentStatus() == null)) {
            return false;
        }
        boolean dateFilterActive = (appliedFrom != null && !appliedFrom.isBlank())
                || (appliedTo != null && !appliedTo.isBlank());
        if (dateFilterActive) {
            LocalDateTime from = parseDate(appliedFrom);
            LocalDateTime to = parseDate(appliedTo);
            boolean anyInRange = false;
            for (ApplicationDetail app : detail.getApplications() == null
                    ? List.<ApplicationDetail>of() : detail.getApplications()) {
                if (app.getApplicationDate() == null) continue;
                if (from != null && app.getApplicationDate().isBefore(from)) continue;
                if (to != null && app.getApplicationDate().isAfter(to.plusDays(1))) continue;
                anyInRange = true;
                break;
            }
            if (!anyInRange) {
                return false;
            }
        }
        if (applicationStatus != null || company != null || job != null || round != null) {
            List<ApplicationDetail> apps = detail.getApplications();
            boolean hasMatch = false;
            Set<String> seen = new HashSet<>();
            for (ApplicationDetail app : apps == null ? List.<ApplicationDetail>of() : apps) {
                String applicationId = app.getApplicationId();
                if (seen.contains(applicationId)) continue;
                seen.add(applicationId);
                if (applicationStatus != null && !applicationStatus.isBlank()
                        && !applicationStatus.equalsIgnoreCase(app.getStatus())) continue;
                if (company != null && !company.isBlank()
                        && !company.equalsIgnoreCase(app.getCompany())) continue;
                if (job != null && !job.isBlank() && !job.equalsIgnoreCase(app.getRole())) continue;
                if (round != null && (app.getCurrentRound() == null || !round.equals(app.getCurrentRound()))) continue;
                hasMatch = true;
                break;
            }
            if (!hasMatch) {
                return false;
            }
        }
        return true;
    }

    private boolean matchesAny(String value, List<String> candidates) {
        for (String candidate : candidates) {
            if (candidate != null && candidate.equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }

    private boolean contains(String value, String needle) {
        return value != null && value.toLowerCase().contains(needle);
    }

    private LocalDateTime parseDate(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDateTime.parse(value);
        } catch (Exception ignored) {
            try {
                return java.time.LocalDate.parse(value).atStartOfDay();
            } catch (Exception ignored2) {
                return null;
            }
        }
    }

    public StudentDetail getStudentDetail(String id) {
        UsersEntries student = requireStudent(id);
        List<Application> applications = applicationRepository.findByStudentId(id);
        return toDetail(student, applications, new ApplicationDetailsResolver(), true);
    }

    private UsersEntries requireStudent(String id) {
        Optional<UsersEntries> optional = usersRepository.findById(id);
        if (optional.isEmpty() && isObjectIdHex(id)) {
            Optional<UsersEntries> byObjectId = Optional.ofNullable(mongoTemplate.findOne(
                    Query.query(Criteria.where("_id").is(new ObjectId(id))), UsersEntries.class));
            if (byObjectId.isPresent()) {
                optional = byObjectId;
            }
        }
        if (optional.isEmpty() || !isStudentRole(optional.get())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Student not found");
        }
        return optional.get();
    }

    public boolean isStudentRole(UsersEntries user) {
        String role = user.getRole();
        return role == null
                || (!role.equalsIgnoreCase("admin") && !role.equalsIgnoreCase("coordinator"));
    }

    public boolean isCoordinatorRole(UsersEntries user) {
        return user.getRole() != null && user.getRole().equalsIgnoreCase("coordinator");
    }

    private UsersEntries requireCoordinator(String id) {
        Optional<UsersEntries> optional = usersRepository.findById(id);
        if (optional.isEmpty() && isObjectIdHex(id)) {
            Optional<UsersEntries> byObjectId = Optional.ofNullable(mongoTemplate.findOne(
                    Query.query(Criteria.where("_id").is(new ObjectId(id))), UsersEntries.class));
            if (byObjectId.isPresent()) {
                optional = byObjectId;
            }
        }
        if (optional.isEmpty() || !isCoordinatorRole(optional.get())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Coordinator not found");
        }
        return optional.get();
    }

    private boolean isObjectIdHex(String value) {
        if (value == null || !value.matches("[0-9a-fA-F]{24}")) return false;
        try {
            new ObjectId(value);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public UsersEntries updateStudent(String id, UsersEntries update) {
        UsersEntries existing = requireStudent(id);
        if (update.getName() != null && !update.getName().isBlank()) existing.setName(update.getName().trim());
        if (update.getEmail() != null && !update.getEmail().isBlank()) existing.setEmail(update.getEmail().trim());
        if (update.getPhoneno() != null && !update.getPhoneno().isBlank()) existing.setPhoneno(update.getPhoneno().trim());
        if (update.getStudentStatus() != null && !update.getStudentStatus().isBlank()) existing.setStudentStatus(update.getStudentStatus().trim());
        if (update.getBatch() != null && !update.getBatch().isBlank()) existing.setBatch(update.getBatch().trim());
        if (update.getBranch() != null && !update.getBranch().isBlank()) existing.setBranch(update.getBranch().trim());
        if (update.getSection() != null && !update.getSection().isBlank()) existing.setSection(update.getSection().trim());
        if (update.getCourse() != null && !update.getCourse().isBlank()) existing.setCourse(update.getCourse().trim());
        if (update.getCollege() != null && !update.getCollege().isBlank()) existing.setCollege(update.getCollege().trim());
        if (update.getDob() != null && !update.getDob().isBlank()) existing.setDob(update.getDob().trim());
        if (update.getGender() != null && !update.getGender().isBlank()) existing.setGender(update.getGender().trim());
        if (update.getAddress() != null && !update.getAddress().isBlank()) existing.setAddress(update.getAddress().trim());
        if (update.getCity() != null && !update.getCity().isBlank()) existing.setCity(update.getCity().trim());
        if (update.getState() != null && !update.getState().isBlank()) existing.setState(update.getState().trim());
        if (update.getCgpa() != null) existing.setCgpa(update.getCgpa());
        if (update.getTenthPercentage() != null) existing.setTenthPercentage(update.getTenthPercentage());
        if (update.getTwelfthPercentage() != null) existing.setTwelfthPercentage(update.getTwelfthPercentage());
        if (update.getGraduationPercentage() != null) existing.setGraduationPercentage(update.getGraduationPercentage());
        return usersRepository.save(existing);
    }

    public UsersEntries addStudent(UsersEntries student) {
        if (student.getEnrollmentno() == null || student.getEnrollmentno().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enrollment number is required");
        }
        if (student.getEmail() == null || student.getEmail().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email is required");
        }
        UsersEntries existing = usersRepository.findByEnrollmentno(student.getEnrollmentno().trim());
        if (existing != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A student with this enrollment number already exists");
        }
        student.setEnrollmentno(student.getEnrollmentno().trim());
        student.setRole("Student");
        if (student.getStudentStatus() == null || student.getStudentStatus().isBlank()) {
            student.setStudentStatus("Active");
        }
        return usersRepository.save(student);
    }

    public Map<String, Object> bulkUpdateStatus(List<String> studentIds, String status) {
        Map<String, Object> result = new LinkedHashMap<>();
        int updated = 0;
        int failed = 0;
        List<Map<String, Object>> failures = new ArrayList<>();
        for (String id : studentIds) {
            try {
                UsersEntries student = requireStudent(id);
                student.setStudentStatus(status.trim());
                usersRepository.save(student);
                updated++;
            } catch (ResponseStatusException e) {
                failed++;
                failures.add(Map.of("id", id, "message", e.getReason()));
            }
        }
        result.put("updated", updated);
        result.put("failed", failed);
        result.put("total", studentIds.size());
        result.put("failures", failures);
        result.put("message", updated + " student" + (updated == 1 ? "" : "s") + " updated");
        return result;
    }

    public Map<String, Object> bulkMoveRound(List<String> applicationIds, Integer targetRound, String adminRemark) {
        return applicationService.bulkSetRound(applicationIds, targetRound, adminRemark);
    }

    public List<UsersEntries> assignedStudents(UsersEntries coordinator) {
        String coordinatorId = coordinator.getId();
        return usersRepository.findByCoordinatorIdsContaining(coordinatorId).stream()
                .filter(this::isStudentRole)
                .collect(Collectors.toList());
    }

    public List<UsersEntries> assignedStudents(String coordinatorId) {
        UsersEntries coordinator = requireCoordinator(coordinatorId);
        return assignedStudents(coordinator);
    }

    public boolean isStudentAssignedToCoordinator(String coordinatorId, String studentId) {
        if (coordinatorId == null || studentId == null) {
            return false;
        }
        Optional<UsersEntries> optional = usersRepository.findById(studentId);
        if (optional.isEmpty() || !isStudentRole(optional.get())) {
            return false;
        }
        List<String> coordinatorIds = optional.get().getCoordinatorIds();
        return coordinatorIds != null && coordinatorIds.contains(coordinatorId);
    }

    public Map<String, Object> coordinatorStats(String coordinatorId) {
        List<UsersEntries> assigned = assignedStudents(coordinatorId);
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", (long) assigned.size());
        stats.put("branches", assigned.stream().map(UsersEntries::getBranch)
                .filter(value -> value != null && !value.isBlank()).distinct().sorted().collect(Collectors.toList()));
        stats.put("sections", assigned.stream().map(UsersEntries::getSection)
                .filter(value -> value != null && !value.isBlank()).distinct().sorted().collect(Collectors.toList()));
        stats.put("batches", assigned.stream().map(UsersEntries::getBatch)
                .filter(value -> value != null && !value.isBlank()).distinct().sorted().collect(Collectors.toList()));
        return stats;
    }

    public List<Map<String, Object>> listCoordinators() {
        List<UsersEntries> allUsers = usersRepository.findAll();
        Map<String, Long> assignedCounts = assignedCounts(allUsers);

        return allUsers.stream()
                .filter(this::isCoordinatorRole)
                .map(coordinator -> toCoordinatorInfo(coordinator, assignedCounts))
                .collect(Collectors.toList());
    }

    private Map<String, Long> assignedCounts(List<UsersEntries> allUsers) {
        return allUsers.stream()
                .filter(this::isStudentRole)
                .filter(student -> student.getCoordinatorIds() != null)
                .flatMap(student -> student.getCoordinatorIds().stream())
                .collect(Collectors.groupingBy(id -> id, Collectors.counting()));
    }

    private Map<String, Object> toCoordinatorInfo(UsersEntries coordinator, Map<String, Long> assignedCounts) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("id", coordinator.getId());
        info.put("enrollmentno", coordinator.getEnrollmentno());
        info.put("name", coordinator.getName());
        info.put("email", coordinator.getEmail());
        info.put("phoneno", coordinator.getPhoneno());
        info.put("department", coordinator.getDepartment());
        info.put("status", coordinator.getStudentStatus());
        info.put("assignedCount", assignedCounts.getOrDefault(coordinator.getId(), 0L));
        return info;
    }

    public Map<String, Object> getCoordinator(String coordinatorId) {
        UsersEntries coordinator = requireCoordinator(coordinatorId);
        return toCoordinatorInfo(coordinator, assignedCounts(usersRepository.findAll()));
    }

    public Map<String, Object> coordinatorProfile(String coordinatorId) {
        return getCoordinator(coordinatorId);
    }

    public Map<String, Object> updateCoordinatorProfile(String coordinatorId, UsersEntries update) {
        UsersEntries coordinator = requireCoordinator(coordinatorId);
        applyCoordinatorFields(coordinator, update, false);
        usersRepository.save(coordinator);
        return toCoordinatorInfo(coordinator, assignedCounts(usersRepository.findAll()));
    }

    public Map<String, Object> updateCoordinator(String coordinatorId, UsersEntries update) {
        UsersEntries coordinator = requireCoordinator(coordinatorId);
        applyCoordinatorFields(coordinator, update, true);
        usersRepository.save(coordinator);
        return toCoordinatorInfo(coordinator, assignedCounts(usersRepository.findAll()));
    }

    private void applyCoordinatorFields(UsersEntries coordinator, UsersEntries update, boolean includeStatus) {
        if (update == null) {
            return;
        }
        if (update.getName() != null && !update.getName().isBlank()) coordinator.setName(update.getName().trim());
        if (update.getEmail() != null && !update.getEmail().isBlank()) coordinator.setEmail(update.getEmail().trim());
        if (update.getPhoneno() != null && !update.getPhoneno().isBlank()) coordinator.setPhoneno(update.getPhoneno().trim());
        if (update.getDepartment() != null && !update.getDepartment().isBlank()) {
            coordinator.setDepartment(update.getDepartment().trim());
        }
        if (includeStatus && update.getStudentStatus() != null && !update.getStudentStatus().isBlank()) {
            coordinator.setStudentStatus(update.getStudentStatus().trim());
        }
    }

    public Map<String, Object> coordinatorProgress(String coordinatorId) {
        UsersEntries coordinator = requireCoordinator(coordinatorId);
        List<Placement> jobs = placementRepository.findByCoordinatorId(coordinator.getId());
        Set<String> jobIds = jobs.stream().map(Placement::getId).collect(Collectors.toSet());
        List<Application> applications = jobIds.isEmpty() ? List.of()
                : applicationRepository.findByPlacementIdIn(jobIds);

        long pending = applications.stream()
                .filter(app -> ApplicationStatus.IN_PROGRESS.name().equalsIgnoreCase(app.getStatus())).count();
        long selected = applications.stream()
                .filter(app -> ApplicationStatus.SELECTED.name().equalsIgnoreCase(app.getStatus())).count();
        long rejected = applications.stream()
                .filter(app -> ApplicationStatus.REJECTED.name().equalsIgnoreCase(app.getStatus())).count();

        Map<String, Long> roundWiseCounts = new java.util.TreeMap<>();
        for (Application application : applications) {
            int round = application.getCurrentRound() == null ? 1 : application.getCurrentRound();
            roundWiseCounts.merge("Round " + round, 1L, Long::sum);
        }

        Map<String, Object> progress = new LinkedHashMap<>();
        progress.put("coordinator", toCoordinatorInfo(coordinator, assignedCounts(usersRepository.findAll())));
        progress.put("totalJobs", (long) jobs.size());
        progress.put("activeJobs", jobs.stream()
                .filter(job -> !"Closed".equalsIgnoreCase(job.getStatus())).count());
        progress.put("completedJobs", jobs.stream()
                .filter(job -> "Closed".equalsIgnoreCase(job.getStatus())).count());
        progress.put("totalApplications", (long) applications.size());
        progress.put("pendingApplications", pending);
        progress.put("selectedApplications", selected);
        progress.put("rejectedApplications", rejected);
        progress.put("roundWiseCounts", roundWiseCounts);
        return progress;
    }

    public UsersEntries addCoordinator(UsersEntries coordinator) {
        if (coordinator.getEnrollmentno() == null || coordinator.getEnrollmentno().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enrollment number is required");
        }
        if (coordinator.getName() == null || coordinator.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name is required");
        }
        if (coordinator.getEmail() == null || coordinator.getEmail().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email is required");
        }
        if (coordinator.getPhoneno() == null || coordinator.getPhoneno().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mobile number is required");
        }
        UsersEntries existing = usersRepository.findByEnrollmentno(coordinator.getEnrollmentno().trim());
        if (existing != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A user with this enrollment number already exists");
        }
        coordinator.setEnrollmentno(coordinator.getEnrollmentno().trim());
        coordinator.setRole("Coordinator");
        if (coordinator.getStudentStatus() == null || coordinator.getStudentStatus().isBlank()) {
            coordinator.setStudentStatus("Active");
        }
        return usersRepository.save(coordinator);
    }

    public Map<String, Object> removeCoordinator(String coordinatorId) {
        UsersEntries coordinator = requireCoordinator(coordinatorId);
        List<UsersEntries> assigned = assignedStudents(coordinator);
        for (UsersEntries student : assigned) {
            student.getCoordinatorIds().remove(coordinator.getId());
            usersRepository.save(student);
        }
        usersRepository.deleteById(coordinator.getId());
        return Map.of("message", "Coordinator removed and " + assigned.size()
                + " student(s) unassigned");
    }

    public Map<String, Object> assignmentOptions() {
        List<UsersEntries> students = usersRepository.findAll().stream()
                .filter(this::isStudentRole)
                .collect(Collectors.toList());
        Map<String, Object> options = filterOptions(students, placementRepository.findAll());
        options.put("students", students.stream()
                .map(student -> {
                    Map<String, Object> info = new LinkedHashMap<>();
                    info.put("id", student.getId());
                    info.put("name", student.getName());
                    info.put("enrollmentno", student.getEnrollmentno());
                    info.put("branch", student.getBranch());
                    info.put("section", student.getSection());
                    info.put("batch", student.getBatch());
                    return info;
                })
                .collect(Collectors.toList()));
        return options;
    }

    public Map<String, Object> assignToCoordinator(String coordinatorId, String branch, String section,
                                                   String batch, List<String> studentIds) {
        UsersEntries coordinator = requireCoordinator(coordinatorId);

        List<UsersEntries> selected = new ArrayList<>();
        if (studentIds != null && !studentIds.isEmpty()) {
            Set<String> seen = new HashSet<>();
            for (String studentId : studentIds) {
                if (seen.add(studentId)) {
                    selected.add(requireStudent(studentId));
                }
            }
        } else {
            selected = usersRepository.findAll().stream()
                    .filter(this::isStudentRole)
                    .filter(student -> branch == null || branch.isBlank()
                            || branch.equalsIgnoreCase(student.getBranch()))
                    .filter(student -> section == null || section.isBlank()
                            || section.equalsIgnoreCase(student.getSection()))
                    .filter(student -> batch == null || batch.isBlank()
                            || batch.equalsIgnoreCase(student.getBatch()))
                    .collect(Collectors.toList());
        }

        if (selected.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "No students match the selected criteria");
        }

        int assigned = 0;
        int alreadyAssigned = 0;
        for (UsersEntries student : selected) {
            List<String> coordinatorIds = student.getCoordinatorIds();
            if (coordinatorIds == null) {
                coordinatorIds = new ArrayList<>();
                student.setCoordinatorIds(coordinatorIds);
            }
            if (coordinatorIds.contains(coordinator.getId())) {
                alreadyAssigned++;
            } else {
                coordinatorIds.add(coordinator.getId());
                usersRepository.save(student);
                assigned++;
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        String coordinatorName = coordinator.getName() != null && !coordinator.getName().isBlank()
                ? coordinator.getName() : coordinator.getEnrollmentno();
        result.put("message", assigned + " student(s) assigned to " + coordinatorName);
        result.put("assigned", assigned);
        result.put("alreadyAssigned", alreadyAssigned);
        result.put("totalSelected", selected.size());
        return result;
    }

    public Map<String, Object> unassignStudents(String coordinatorId, List<String> studentIds) {
        UsersEntries coordinator = requireCoordinator(coordinatorId);
        if (studentIds == null || studentIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one student is required");
        }
        int removed = 0;
        for (String studentId : studentIds) {
            UsersEntries student = requireStudent(studentId);
            List<String> coordinatorIds = student.getCoordinatorIds();
            if (coordinatorIds != null && coordinatorIds.remove(coordinator.getId())) {
                student.setCoordinatorIds(coordinatorIds);
                usersRepository.save(student);
                removed++;
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        String coordinatorName = coordinator.getName() != null && !coordinator.getName().isBlank()
                ? coordinator.getName() : coordinator.getEnrollmentno();
        result.put("message", removed + " student(s) removed from " + coordinatorName);
        result.put("removed", removed);
        return result;
    }

    public List<Map<String, Object>> listAssignments() {
        List<UsersEntries> allUsers = usersRepository.findAll();
        Map<String, String> coordinatorNames = allUsers.stream()
                .filter(this::isCoordinatorRole)
                .collect(Collectors.toMap(UsersEntries::getId, coordinator -> {
                    String name = coordinator.getName();
                    return name != null && !name.isBlank()
                            ? name : coordinator.getEnrollmentno();
                }));
        return allUsers.stream()
                .filter(this::isStudentRole)
                .filter(student -> student.getCoordinatorIds() != null
                        && !student.getCoordinatorIds().isEmpty())
                .map(student -> {
                    Map<String, Object> info = new LinkedHashMap<>();
                    info.put("studentId", student.getId());
                    info.put("name", student.getName());
                    info.put("enrollmentno", student.getEnrollmentno());
                    info.put("branch", student.getBranch());
                    info.put("section", student.getSection());
                    info.put("batch", student.getBatch());
                    info.put("coordinators", student.getCoordinatorIds().stream()
                            .map(id -> {
                                Map<String, Object> coordinator = new LinkedHashMap<>();
                                coordinator.put("id", id);
                                coordinator.put("name", coordinatorNames.getOrDefault(id, "Unknown"));
                                return coordinator;
                            })
                            .collect(Collectors.toList()));
                    return info;
                })
                .collect(Collectors.toList());
    }

    public StudentListResponse listStudentsForCoordinator(String coordinatorId, String search,
                                                          List<String> batches, List<String> branches,
                                                          List<String> sections, int page, int pageSize) {
        return listStudentsForCoordinator(coordinatorId, search, batches, branches, sections,
            null, null, null, null, null, page, pageSize);
        }

        public StudentListResponse listStudentsForCoordinator(String coordinatorId, String search,
                                  List<String> batches, List<String> branches,
                                  List<String> sections, String studentStatus,
                                  String applicationStatus, String company, String job,
                                  Integer round, int page, int pageSize) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, pageSize));

        UsersEntries coordinator = requireCoordinator(coordinatorId);
        List<UsersEntries> assigned = assignedStudents(coordinator);
        Set<String> assignedIds = assigned.stream().map(UsersEntries::getId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Map<String, List<Application>> appsByStudent = (assignedIds.isEmpty() ? List.<Application>of()
                : applicationRepository.findByStudentIdIn(assignedIds)).stream()
                .collect(Collectors.groupingBy(app -> app.getStudentId(),
                        Collectors.toCollection(ArrayList::new)));
        ApplicationDetailsResolver resolver = new ApplicationDetailsResolver();

        List<StudentDetail> filtered = assigned.stream()
                .map(student -> toDetail(student,
                appsByStudent.getOrDefault(student.getId(), List.of()), resolver,
                applicationStatus != null || company != null || job != null || round != null))
            .filter(detail -> matches(detail, search, batches, branches, sections, studentStatus,
                applicationStatus, company, job, round, null, null))
                .collect(Collectors.toList());

        int total = filtered.size();
        int totalPages = (int) Math.ceil((double) total / safeSize);
        int fromIndex = Math.min((safePage - 1) * safeSize, total);
        int toIndex = Math.min(fromIndex + safeSize, total);
        List<StudentDetail> pageStudents = fromIndex < toIndex
                ? filtered.subList(fromIndex, toIndex) : List.of();

        Map<String, Object> stats = Map.of(
                "total", (long) total,
                "active", filtered.stream().filter(s -> "Active".equalsIgnoreCase(s.getStudentStatus())).count(),
                "placed", filtered.stream().filter(s -> "Placed".equalsIgnoreCase(s.getStudentStatus())).count(),
                "notPlaced", filtered.stream().filter(s -> "Not Placed".equalsIgnoreCase(s.getStudentStatus())).count());

        return new StudentListResponse(total, safePage, safeSize, totalPages, pageStudents, stats,
            filterOptions(assigned, placementRepository.findAll()));
    }

    public StudentDetail getAssignedStudentDetail(String coordinatorId, String studentId) {
        UsersEntries coordinator = requireCoordinator(coordinatorId);
        UsersEntries student = requireStudent(studentId);
        List<String> coordinatorIds = student.getCoordinatorIds();
        if (coordinatorIds == null || !coordinatorIds.contains(coordinator.getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Student not found");
        }
        List<Application> applications = applicationRepository.findByStudentId(studentId);
        return toDetail(student, applications, new ApplicationDetailsResolver(), true);
    }

    public StudentDetail toDetail(UsersEntries student, List<Application> applications,
                                  ApplicationDetailsResolver resolver) {
        return toDetail(student, applications, resolver, false);
    }

    public StudentDetail toDetail(UsersEntries student, List<Application> applications,
                                  ApplicationDetailsResolver resolver, boolean withApplications) {
        StudentDetail detail = new StudentDetail();
        detail.setId(student.getId());
        detail.setEnrollmentno(student.getEnrollmentno());
        detail.setName(student.getName());
        detail.setEmail(student.getEmail());
        detail.setPhoneno(student.getPhoneno());
        detail.setRole(student.getRole());
        detail.setStudentStatus(student.getStudentStatus());
        detail.setBatch(student.getBatch());
        detail.setBranch(student.getBranch());
        detail.setSection(student.getSection());
        detail.setCoordinatorIds(student.getCoordinatorIds());
        detail.setCourse(student.getCourse());
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
        detail.setApplicationCount(applications.size());

        String bestStatus = summarizeStatus(applications);
        detail.setApplicationStatus(bestStatus);

        if (withApplications && applications != null && !applications.isEmpty()) {
            detail.setApplications(resolver.resolve(student, applications));
        }
        return detail;
    }

    private String summarizeStatus(List<Application> applications) {
        if (applications == null || applications.isEmpty()) {
            return "No Applications";
        }
        for (Application app : applications) {
            if (ApplicationStatus.SELECTED.name().equalsIgnoreCase(app.getStatus())
                    || "Selected".equalsIgnoreCase(app.getStatus())) {
                return "Selected";
            }
        }
        return "In Progress";
    }

    public class ApplicationDetailsResolver {
        public List<ApplicationDetail> resolve(UsersEntries student, List<Application> applications) {
            if (applications == null || applications.isEmpty()) {
                return List.of();
            }
            java.util.Set<String> jobIds = applications.stream()
                    .map(Application::getPlacementId)
                    .filter(id -> id != null)
                    .collect(Collectors.toSet());
            Map<String, Placement> jobMap = new HashMap<>();
            placementRepository.findAllById(jobIds).forEach(job -> jobMap.put(job.getId(), job));
            Map<String, List<PlacementRound>> configByJob = applicationService.roundConfigByJob(jobIds, jobMap);
            List<ApplicationDetail> details = new ArrayList<>();
            for (Application application : applications) {
                Placement job = jobMap.get(application.getPlacementId());
                List<PlacementRound> config = configByJob.getOrDefault(application.getPlacementId(), List.of());
                details.add(ApplicationService.mapDetail(application, student, job, config));
            }
            return details;
        }
    }
}