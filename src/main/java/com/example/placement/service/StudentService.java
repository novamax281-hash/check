package com.example.placement.service;

import java.nio.charset.StandardCharsets;
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
import org.springframework.security.crypto.password.PasswordEncoder;
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

    static final int MAX_PAGE_SIZE = 100;
    static final int MAX_REPORT_PAGE_SIZE = 100_000;

    private final UsersRepository usersRepository;
    private final ApplicationRepository applicationRepository;
    private final PlacementRepository placementRepository;
    private final ApplicationService applicationService;
    private final MongoTemplate mongoTemplate;
    private final PasswordEncoder passwordEncoder;

    public StudentService(UsersRepository usersRepository,
                          ApplicationRepository applicationRepository,
                          PlacementRepository placementRepository,
                          ApplicationService applicationService,
                          MongoTemplate mongoTemplate,
                          PasswordEncoder passwordEncoder) {
        this.usersRepository = usersRepository;
        this.applicationRepository = applicationRepository;
        this.placementRepository = placementRepository;
        this.applicationService = applicationService;
        this.mongoTemplate = mongoTemplate;
        this.passwordEncoder = passwordEncoder;
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
        int maxSize = includeApplications ? MAX_REPORT_PAGE_SIZE : MAX_PAGE_SIZE;
        int safeSize = Math.min(maxSize, Math.max(1, pageSize));

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
        if (update.getSgpas() != null) {
            List<Double> sgpas = new ArrayList<>();
            for (Double value : update.getSgpas()) {
                sgpas.add(value == null ? null : Math.round(value * 100.0) / 100.0);
            }
            existing.setSgpas(sgpas);
            double sum = 0;
            int counted = 0;
            for (Double value : sgpas) {
                if (value != null) {
                    sum += value;
                    counted++;
                }
            }
            existing.setCgpa(counted == 0 ? null : Math.round(sum / counted * 100.0) / 100.0);
        }
        if (update.getTenthPercentage() != null) existing.setTenthPercentage(update.getTenthPercentage());
        if (update.getTwelfthPercentage() != null) existing.setTwelfthPercentage(update.getTwelfthPercentage());
        if (update.getGraduationPercentage() != null) existing.setGraduationPercentage(update.getGraduationPercentage());
        return usersRepository.save(existing);
    }

    public UsersEntries addStudent(UsersEntries student) {
        validateNewStudent(student);
        String password = student.getPassword();
        student.setRole("Student");
        if (student.getStudentStatus() == null || student.getStudentStatus().isBlank()) {
            student.setStudentStatus("Active");
        } else {
            student.setStudentStatus(student.getStudentStatus().trim());
        }
        if (password != null && !password.isBlank()) {
            student.setPasswordHash(passwordEncoder.encode(password));
        }
        student.setPassword(null);
        return usersRepository.save(student);
    }

    public void validateNewStudent(UsersEntries student) {
        if (student == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Student data is required");
        }
        if (student.getEnrollmentno() == null || student.getEnrollmentno().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enrollment number is required");
        }
        if (student.getName() == null || student.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name is required");
        }
        if (student.getEmail() == null || student.getEmail().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email is required");
        }
        if (student.getPhoneno() == null || student.getPhoneno().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Phone number is required");
        }
        if (student.getBatch() == null || student.getBatch().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Batch is required");
        }
        if (student.getBranch() == null || student.getBranch().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Branch is required");
        }
        if (student.getSection() == null || student.getSection().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Section is required");
        }

        student.setEnrollmentno(student.getEnrollmentno().trim());
        student.setName(student.getName().trim());
        student.setEmail(student.getEmail().trim());
        student.setPhoneno(student.getPhoneno().trim());
        student.setBatch(student.getBatch().trim());
        student.setBranch(student.getBranch().trim());
        student.setSection(student.getSection().trim());
        if (!student.getEmail().matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid email format");
        }
        if (student.getTenthPercentage() != null && !isValidPercentage(student.getTenthPercentage())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "10th Percentage must be between 0 and 100");
        }
        if (student.getTwelfthPercentage() != null && !isValidPercentage(student.getTwelfthPercentage())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "12th Percentage must be between 0 and 100");
        }
        if (student.getPassword() == null || student.getPassword().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password is required");
        }
        String password = student.getPassword();
        if (password != null && password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Password must be at most 72 UTF-8 bytes");
        }
        if (student.getStudentStatus() != null && !student.getStudentStatus().isBlank()
                && knownStatuses().stream().noneMatch(status -> status.equalsIgnoreCase(student.getStudentStatus().trim()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid student status");
        }
        UsersEntries existing = usersRepository.findByEnrollmentno(student.getEnrollmentno());
        if (existing != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A student with this enrollment number already exists");
        }
        if (usersRepository.findByEmailIgnoreCase(student.getEmail()) != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A student with this email already exists");
        }
    }

    private boolean isValidPercentage(Double value) {
        return value != null && !value.isNaN() && !value.isInfinite() && value >= 0 && value <= 100;
    }

    public Map<String, Object> bulkUpdateStatus(List<String> studentIds, String status) {
        Map<String, Object> result = new LinkedHashMap<>();
        int updated = 0;
        int failed = 0;
        List<Map<String, Object>> failures = new ArrayList<>();
        if (status == null || status.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "New status is required");
        }
        String normalized = status.trim();
        boolean allowed = knownStatuses().stream().anyMatch(s -> s.equalsIgnoreCase(normalized));
        if (!allowed) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Status must be one of: " + String.join(", ", knownStatuses()));
        }
        List<String> targets = studentIds == null ? List.of() : studentIds;
        for (String id : targets) {
            try {
                UsersEntries student = requireStudent(id);
                student.setStudentStatus(normalized);
                usersRepository.save(student);
                updated++;
            } catch (ResponseStatusException e) {
                failed++;
                failures.add(Map.of("id", id, "message",
                        e.getReason() != null ? e.getReason() : "Could not be processed"));
            }
        }
        result.put("updated", updated);
        result.put("failed", failed);
        result.put("total", targets.size());
        result.put("failures", failures);
        result.put("message", updated + " student" + (updated == 1 ? "" : "s") + " updated");
        return result;
    }

    public Map<String, Object> bulkMoveRound(List<String> applicationIds, Integer targetRound, String adminRemark) {
        return applicationService.bulkSetRound(applicationIds, targetRound, adminRemark);
    }

    public Map<String, Object> bulkMoveRound(List<String> applicationIds, List<String> studentIds,
                                             Integer targetRound, String adminRemark) {
        List<String> resolved = new ArrayList<>(applicationIds == null ? List.of() : applicationIds);
        for (String studentId : studentIds == null ? List.<String>of() : studentIds) {
            if (studentId == null || studentId.isBlank()) {
                continue;
            }
            applicationRepository.findByStudentId(studentId).stream()
                    .map(Application::getId)
                    .filter(java.util.Objects::nonNull)
                    .forEach(resolved::add);
        }
        return applicationService.bulkSetRound(resolved.stream().distinct().collect(Collectors.toList()),
                targetRound, adminRemark);
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