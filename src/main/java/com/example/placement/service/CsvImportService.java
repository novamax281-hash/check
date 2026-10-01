package com.example.placement.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.example.placement.dto.CsvImportResult;
import com.example.placement.dto.CsvValidationResult;
import com.example.placement.entity.Application;
import com.example.placement.entity.ApplicationRound;
import com.example.placement.entity.ApplicationStatus;
import com.example.placement.entity.RoundStatus;
import com.example.placement.entity.UsersEntries;
import com.example.placement.repository.ApplicationRepository;
import com.example.placement.repository.UsersRepository;

@Service
public class CsvImportService {

    private static final List<String> STUDENT_ADD_HEADERS = List.of("enrollmentno", "name", "email", "password",
            "phone", "batch", "branch", "section", "studentstatus", "tenthpercentage", "twelfthpercentage");

    private final UsersRepository usersRepository;
    private final ApplicationRepository applicationRepository;
    private final StudentService studentService;

    public CsvImportService(UsersRepository usersRepository, ApplicationRepository applicationRepository,
                            StudentService studentService) {
        this.usersRepository = usersRepository;
        this.applicationRepository = applicationRepository;
        this.studentService = studentService;
    }

    public List<String> supportedOperations() {
        return List.of("ADD", "UPDATE", "STATUS", "APPLICATION_STATUS", "APPLICATION_ROUND", "ACADEMIC");
    }

    public List<String> templateColumns(String operation) {
        switch (operation == null ? "ADD" : operation.toUpperCase()) {
            case "STATUS":
                return List.of("enrollmentno", "email", "status");
            case "APPLICATION_STATUS":
                return List.of("enrollmentno", "email", "jobId", "status");
            case "APPLICATION_ROUND":
                return List.of("enrollmentno", "email", "jobId", "round");
            case "ACADEMIC":
                return List.of("enrollmentno", "email", "batch", "branch", "section", "course", "cgpa",
                        "tenthPercentage", "twelfthPercentage", "graduationPercentage");
            case "ADD":
                return List.of("enrollmentNo", "name", "email", "password", "phone", "batch", "branch",
                        "section", "studentStatus", "tenthPercentage", "twelfthPercentage");
            case "UPDATE":
            default:
                return List.of("enrollmentno", "name", "email", "phoneno", "batch", "branch", "section",
                        "course", "college", "cgpa", "tenthPercentage", "twelfthPercentage",
                        "graduationPercentage", "dob", "gender", "city", "state", "password", "studentStatus");
        }
    }

    public List<Map<String, String>> parseCsv(List<String> lines) {
        return parseCsv(lines, null);
    }

    public List<Map<String, String>> parseCsv(List<String> lines, String operation) {
        return parseCsvContent(lines == null ? "" : String.join("\n", lines), operation);
    }

    public List<Map<String, String>> parseCsvContent(String content, String operation) {
        List<Map<String, String>> rows = new ArrayList<>();
        List<String> headers = new ArrayList<>();
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("CSV file is empty");
        }
        CSVFormat format = CSVFormat.RFC4180.builder().setIgnoreEmptyLines(false).setTrim(false).get();
        try (CSVParser parser = CSVParser.parse(content, format)) {
            Iterator<CSVRecord> records = parser.iterator();
            if (!records.hasNext()) {
                throw new IllegalArgumentException("CSV header row is missing");
            }
            CSVRecord header = records.next();
            for (int index = 0; index < header.size(); index++) {
                String value = header.get(index).trim();
                if (index == 0) value = value.replaceFirst("^\\uFEFF", "");
                headers.add(value.toLowerCase(java.util.Locale.ROOT));
            }
            while (records.hasNext()) {
                CSVRecord record = records.next();
                if (isEmptyRecord(record)) continue;
                Map<String, String> row = new LinkedHashMap<>();
                row.put("__rownumber", Long.toString(record.getRecordNumber()));
                row.put("__columncount", Integer.toString(record.size()));
                for (int column = 0; column < headers.size(); column++) {
                    String headerName = headers.get(column);
                    if (!headerName.isEmpty()) {
                        row.put(headerName, column < record.size() ? record.get(column).trim() : "");
                    }
                }
                rows.add(row);
            }
        } catch (IllegalArgumentException exception) {
            if ("CSV header row is missing".equals(exception.getMessage())) {
                throw exception;
            }
            throw new IllegalArgumentException("Malformed CSV format");
        } catch (IOException | RuntimeException exception) {
            throw new IllegalArgumentException("Malformed CSV format");
        }
        if (headers.isEmpty()) {
            throw new IllegalArgumentException("CSV header row is missing");
        }
        if ("ADD".equalsIgnoreCase(operation)) {
            validateStudentHeaders(headers);
        }
        return rows;
    }

    private boolean isEmptyRecord(CSVRecord record) {
        for (String value : record) {
            if (!value.isBlank()) return false;
        }
        return true;
    }

    private void validateStudentHeaders(List<String> headers) {
        Set<String> actual = new HashSet<>(headers);
        Set<String> expected = new HashSet<>(STUDENT_ADD_HEADERS);
        List<String> missing = STUDENT_ADD_HEADERS.stream().filter(header -> !actual.contains(header)).toList();
        List<String> unexpected = headers.stream().filter(header -> !expected.contains(header)).distinct().toList();
        if (headers.size() != STUDENT_ADD_HEADERS.size() || actual.size() != headers.size()
                || !missing.isEmpty() || !unexpected.isEmpty()) {
            List<String> issues = new ArrayList<>();
            if (!missing.isEmpty()) issues.add("missing: " + String.join(", ", missing));
            if (!unexpected.isEmpty()) issues.add("unexpected: " + String.join(", ", unexpected));
            if (actual.size() != headers.size()) issues.add("duplicate column names");
            throw new IllegalArgumentException("CSV headers must contain exactly the student fields ("
                    + String.join("; ", issues) + ")");
        }
    }

    public CsvValidationResult validate(String operation, List<Map<String, String>> rows, boolean previewMode) {
        CsvValidationResult result = new CsvValidationResult();
        String op = operation == null ? "ADD" : operation.toUpperCase();
        if (!supportedOperations().contains(op)) {
            result.getGlobalErrors().add("Unsupported operation: " + operation);
            return result;
        }
        result.setTotalRows(rows.size());

        Map<String, Integer> seenEnrollments = new HashMap<>();
        Map<String, Integer> seenEmails = new HashMap<>();
        Map<String, Integer> seenIdentifiers = new HashMap<>();

        for (int i = 0; i < rows.size(); i++) {
            Map<String, String> row = rows.get(i);
            int rowNumber = csvRowNumber(row, i);
            String enrollment = row.get("enrollmentno");
            String email = row.get("email");
            String name = row.get("name");
            CsvValidationResult.CsvRowResult rowResult =
                    new CsvValidationResult.CsvRowResult(rowNumber, enrollment, name, email);

            List<String> errors = new ArrayList<>();

            if (op.equals("ADD")) {
                String columnCount = row.get("__columncount");
                if (columnCount != null && Integer.parseInt(columnCount) != STUDENT_ADD_HEADERS.size()) {
                    errors.add("Row has " + columnCount + " columns; expected " + STUDENT_ADD_HEADERS.size());
                } else {
                    UsersEntries student = studentFromAddRow(row);
                    try {
                        studentService.validateNewStudent(student);
                    } catch (ResponseStatusException exception) {
                        errors.add(exception.getReason() == null ? "Invalid student data" : exception.getReason());
                    } catch (DataAccessException | com.mongodb.MongoException exception) {
                        errors.add("Could not validate this student against the database");
                    } catch (RuntimeException exception) {
                        errors.add("Could not validate this student");
                    }
                }
                addDuplicateError(enrollment, rowNumber, seenEnrollments, "enrollment number", errors);
                addDuplicateError(email, rowNumber, seenEmails, "email", errors);
                rowResult.setStatus(row.get("studentstatus"));
            } else {
                boolean matched = false;
                if (!isBlank(enrollment) && usersRepository.findByEnrollmentno(enrollment) != null) {
                    matched = true;
                } else if (!isBlank(email)
                        && usersRepository.findAll().stream().anyMatch(u -> email.equalsIgnoreCase(u.getEmail()))) {
                    matched = true;
                }
                if (!matched) {
                    errors.add("Student not found");
                }
            }

            if ("STATUS".equals(op) || "APPLICATION_STATUS".equals(op)) {
                String status = row.get("status");
                if (isBlank(status)) {
                    errors.add("Status is required");
                } else if (!isValidStatus(op, status)) {
                    errors.add("Invalid status: " + status);
                }
            }

            if ("APPLICATION_ROUND".equals(op)) {
                String round = row.get("round");
                if (isBlank(round)) {
                    errors.add("Round is required");
                } else {
                    try {
                        int r = Integer.parseInt(round.trim());
                        if (r < 1) errors.add("Round must be at least 1");
                    } catch (NumberFormatException e) {
                        errors.add("Round must be a number");
                    }
                }
                String jobId = row.get("jobId");
                if (isBlank(jobId)) {
                    errors.add("jobId is required");
                }
            }

            if ("ACADEMIC".equals(op) || "UPDATE".equals(op)) {
                validateCgpa(row.get("cgpa"), errors);
                validatePercentage(row.get("tenthPercentage"), "10th Percentage", errors);
                validatePercentage(row.get("twelfthPercentage"), "12th Percentage", errors);
                validatePercentage(row.get("graduationPercentage"), "Graduation Percentage", errors);
            }

            if (!"ADD".equals(op)) {
                String identifier = !isBlank(enrollment) ? enrollment : (isBlank(email) ? "" : email);
                if (!identifier.isEmpty()) {
                    Integer firstSeen = seenIdentifiers.putIfAbsent(identifier.toLowerCase(), rowNumber);
                    if (firstSeen != null) {
                        errors.add("Duplicate identifier, first seen on row " + firstSeen);
                    }
                }
            }

            if (!errors.isEmpty()) {
                rowResult.setValid(false);
                rowResult.setError(String.join("; ", errors));
                result.setErrorRows(result.getErrorRows() + 1);
            } else {
                result.setValidRows(result.getValidRows() + 1);
                if (!"ADD".equals(op)) {
                    rowResult.setStatus(row.get("status") != null ? row.get("status") : "OK");
                }
            }
            result.getRows().add(rowResult);
        }
        return result;
    }

    private boolean isValidStatus(String op, String status) {
        if ("STATUS".equals(op)) {
            return List.of("Active", "Inactive", "Placed", "Not Placed").stream()
                    .anyMatch(s -> s.equalsIgnoreCase(status));
        }
        return List.of("IN_PROGRESS", "SELECTED", "REJECTED", "WITHDRAWN",
                "Applied", "Under Review", "Shortlisted", "Interview Scheduled").stream()
                .anyMatch(s -> s.equalsIgnoreCase(status));
    }

    private void validatePercentage(String value, String label, List<String> errors) {
        if (isBlank(value)) {
            return;
        }
        try {
            double parsed = Double.parseDouble(value.trim());
            if (parsed < 0 || parsed > 100) {
                errors.add(label + " must be between 0 and 100");
            }
        } catch (NumberFormatException ignored) {
            errors.add(label + " must be a number");
        }
    }

    /**
     * CGPA uses a 0-10 scale (matching registration and ProfileController), not the 0-100
     * percentage scale used by tenth/twelfth/graduation marks. Validating it against the
     * percentage range would silently accept impossible values such as 85.
     */
    private void validateCgpa(String value, List<String> errors) {
        if (isBlank(value)) {
            return;
        }
        try {
            double parsed = Double.parseDouble(value.trim());
            if (parsed < 0 || parsed > 10) {
                errors.add("CGPA must be between 0 and 10");
            }
        } catch (NumberFormatException ignored) {
            errors.add("CGPA must be a number");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private int csvRowNumber(Map<String, String> row, int index) {
        try {
            return Integer.parseInt(row.getOrDefault("__rownumber", Integer.toString(index + 2)));
        } catch (NumberFormatException ignored) {
            return index + 2;
        }
    }

    private UsersEntries studentFromAddRow(Map<String, String> row) {
        UsersEntries student = new UsersEntries(row.get("enrollmentno"),
                firstNonBlank(row.get("phone"), row.get("phoneno")));
        student.setName(row.get("name"));
        student.setEmail(row.get("email"));
        student.setPassword(row.get("password"));
        student.setStudentStatus(firstNonBlank(row.get("studentstatus"), row.get("status")));
        student.setBatch(row.get("batch"));
        student.setBranch(row.get("branch"));
        student.setSection(row.get("section"));
        student.setTenthPercentage(parseDouble(firstNonBlank(
                row.get("tenthpercentage"), row.get("tenth %"), row.get("10thpercentage"))));
        student.setTwelfthPercentage(parseDouble(firstNonBlank(
                row.get("twelfthpercentage"), row.get("12thpercentage"), row.get("12th %"))));
        return student;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value;
            }
        }
        return null;
    }

    private void addDuplicateError(String value, int rowNumber, Map<String, Integer> seen, String field,
                                   List<String> errors) {
        if (isBlank(value)) return;
        String key = value.trim().toLowerCase(java.util.Locale.ROOT);
        Integer firstSeen = seen.putIfAbsent(key, rowNumber);
        if (firstSeen != null) {
            errors.add("Duplicate " + field + " in CSV; first seen on row " + firstSeen);
        }
    }

    public CsvImportResult commit(String operation, List<Map<String, String>> rows) {
        String op = operation == null ? "ADD" : operation.toUpperCase();
        CsvValidationResult validation = validate(op, rows, false);
        CsvImportResult result = new CsvImportResult();
        result.setTotalRows(rows.size());
        result.setCreatedRows(0);
        result.setUpdatedRows(0);
        result.setSkippedRows(0);
        result.setErrors(new ArrayList<>());
        result.setSuccessfulRows(validation.getValidRows());
        result.setFailedRows(validation.getErrorRows());

        int created = 0;
        int updated = 0;
        int skipped = 0;

        for (int i = 0; i < rows.size(); i++) {
            Map<String, String> row = rows.get(i);
            int rowNumber = csvRowNumber(row, i);
            String enrollment = row.get("enrollmentno");
            String email = row.get("email");
            String identifier = !isBlank(enrollment) ? enrollment : email;

            CsvValidationResult.CsvRowResult validationRow = validation.getRows().size() > i
                    ? validation.getRows().get(i) : null;
            if (validationRow != null && !validationRow.isValid()) {
            result.getErrors().add(csvError(rowNumber, row, "VALIDATION", validationRow.getError()));
                continue;
            }

            try {
                switch (op) {
                    case "ADD": {
                        studentService.addStudent(studentFromAddRow(row));
                        created++;
                        break;
                    }
                    case "STATUS": {
                        UsersEntries student = findStudent(row);
                        student.setStudentStatus(row.get("status").trim());
                        usersRepository.save(student);
                        updated++;
                        break;
                    }
                    case "ACADEMIC":
                    case "UPDATE": {
                        UsersEntries student = findStudent(row);
                        applySharedFields(student, row);
                        usersRepository.save(student);
                        updated++;
                        break;
                    }
                    case "APPLICATION_STATUS": {
                        Application application = findApplication(row);
                        if (application == null) {
                            skipped++;
                            result.getErrors().add(new CsvImportResult.CsvError(rowNumber, identifier,
                                    "NO_APPLICATION", "No application found for jobId " + row.get("jobId")));
                            continue;
                        }
                        application.setStatus(row.get("status").trim());
                        application.setLastUpdated(java.time.LocalDateTime.now());
                        applicationRepository.save(application);
                        updated++;
                        break;
                    }
                    case "APPLICATION_ROUND": {
                        Application application = findApplication(row);
                        if (application == null) {
                            skipped++;
                            result.getErrors().add(new CsvImportResult.CsvError(rowNumber, identifier,
                                    "NO_APPLICATION", "No application found for jobId " + row.get("jobId")));
                            continue;
                        }
                        int targetRound = Integer.parseInt(row.get("round").trim());
                        String terminal = terminalBlock(application);
                        if (terminal != null) {
                            skipped++;
                            result.getErrors().add(new CsvImportResult.CsvError(rowNumber, identifier,
                                    "TERMINAL", terminal));
                            continue;
                        }
                        syncRoundEntry(application, targetRound);
                        application.setCurrentRound(targetRound);
                        application.setStatus(ApplicationStatus.IN_PROGRESS.name());
                        application.setLastUpdated(java.time.LocalDateTime.now());
                        applicationRepository.save(application);
                        updated++;
                        break;
                    }
                    default:
                        skipped++;
                }
            } catch (ResponseStatusException e) {
                result.setFailedRows(result.getFailedRows() + 1);
                result.getErrors().add(csvError(rowNumber, row, "ERROR",
                        e.getReason() != null ? e.getReason() : "Could not process row"));
            } catch (org.springframework.dao.DataIntegrityViolationException e) {
                result.setFailedRows(result.getFailedRows() + 1);
                result.getErrors().add(csvError(rowNumber, row, "ERROR",
                        "Enrollment number or email already exists"));
            } catch (DataAccessException | com.mongodb.MongoException e) {
                result.setFailedRows(result.getFailedRows() + 1);
                result.getErrors().add(csvError(rowNumber, row, "ERROR", "Database could not save this row"));
            } catch (Exception e) {
                result.setFailedRows(result.getFailedRows() + 1);
                result.getErrors().add(csvError(rowNumber, row, "ERROR", "Could not process this row"));
            }
        }
        result.setCreatedRows(created);
        result.setUpdatedRows(updated);
        result.setSkippedRows(skipped + validation.getErrorRows());
        result.setSuccessfulRows(created + updated);
        return result;
    }

    private CsvImportResult.CsvError csvError(int rowNumber, Map<String, String> row, String error, String reason) {
        String identifier = !isBlank(row.get("enrollmentno")) ? row.get("enrollmentno") : row.get("email");
        CsvImportResult.CsvError csvError = new CsvImportResult.CsvError(rowNumber, identifier, error, reason);
        csvError.setEnrollmentNo(row.get("enrollmentno"));
        csvError.setName(row.get("name"));
        csvError.setEmail(row.get("email"));
        return csvError;
    }

    private String terminalBlock(Application application) {
        String status = application.getStatus();
        if (status == null) {
            return null;
        }
        String trimmed = status.trim();
        if (trimmed.equalsIgnoreCase("SELECTED")) {
            return "Application is already selected";
        }
        if (trimmed.equalsIgnoreCase("REJECTED")) {
            return "Application is already rejected";
        }
        if (trimmed.equalsIgnoreCase("WITHDRAWN")) {
            return "Application was withdrawn";
        }
        return null;
    }

    private void syncRoundEntry(Application application, int targetRound) {
        List<ApplicationRound> rounds = application.getRounds() == null
                ? new ArrayList<>()
                : new ArrayList<>(application.getRounds());
        rounds.removeIf(round -> round == null || round.getRound() == null || round.getRound() > targetRound);
        for (ApplicationRound round : rounds) {
            round.setStatus(RoundStatus.PENDING);
            if (round.getRound() == targetRound) {
                round.setScore(null);
                round.setEvaluatedAt(null);
                round.setAdminRemark(null);
            }
        }
        boolean present = rounds.stream().anyMatch(round -> round.getRound() == targetRound);
        if (!present) {
            rounds.add(new ApplicationRound(targetRound));
        }
        rounds.sort(Comparator.comparing(ApplicationRound::getRound));
        application.setRounds(rounds);
    }

    private UsersEntries findStudent(Map<String, String> row) {
        String enrollment = row.get("enrollmentno");
        if (!isBlank(enrollment)) {
            UsersEntries byEnrollment = usersRepository.findByEnrollmentno(enrollment);
            if (byEnrollment != null) {
                return byEnrollment;
            }
        }
        String email = row.get("email");
        if (!isBlank(email)) {
            Optional<UsersEntries> byEmail = usersRepository.findAll().stream()
                    .filter(u -> email.equalsIgnoreCase(u.getEmail()))
                    .findFirst();
            if (byEmail.isPresent()) {
                return byEmail.get();
            }
        }
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Student not found");
    }

    private Application findApplication(Map<String, String> row) {
        UsersEntries student = findStudent(row);
        String jobId = row.get("jobId");
        if (isBlank(jobId)) {
            return null;
        }
        return applicationRepository.findByStudentIdAndPlacementId(student.getId(), jobId.trim()).orElse(null);
    }

    private void applySharedFields(UsersEntries student, Map<String, String> row) {
        if (row.get("batch") != null) student.setBatch(row.get("batch").trim());
        if (row.get("branch") != null) student.setBranch(row.get("branch").trim());
        if (row.get("section") != null) student.setSection(row.get("section").trim());
        if (row.get("course") != null) student.setCourse(row.get("course").trim());
        if (row.get("college") != null) student.setCollege(row.get("college").trim());
        if (row.get("city") != null) student.setCity(row.get("city").trim());
        if (row.get("state") != null) student.setState(row.get("state").trim());
        if (row.get("dob") != null) student.setDob(row.get("dob").trim());
        if (row.get("gender") != null) student.setGender(row.get("gender").trim());
        if (row.get("studentStatus") != null && !row.get("studentStatus").trim().isEmpty()) {
            student.setStudentStatus(row.get("studentStatus").trim());
        }
        student.setCgpa(parseDouble(row.get("cgpa")));
        student.setTenthPercentage(parseDouble(row.get("tenthPercentage")));
        student.setTwelfthPercentage(parseDouble(row.get("twelfthPercentage")));
        student.setGraduationPercentage(parseDouble(row.get("graduationPercentage")));
    }

    private Double parseDouble(String value) {
        if (isBlank(value)) {
            return null;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public String buildErrorReport(String operation, List<Map<String, String>> rows, CsvImportResult result) {
        StringBuilder csv = new StringBuilder("row_number,identifier,error,reason,enrollment_no,name,email\n");
        for (CsvImportResult.CsvError error : result.getErrors()) {
            csv.append(error.getRowNumber()).append(',')
                    .append(quote(error.getIdentifier())).append(',')
                    .append(quote(error.getError())).append(',')
                    .append(quote(error.getReason())).append(',')
                    .append(quote(error.getEnrollmentNo())).append(',')
                    .append(quote(error.getName())).append(',')
                    .append(quote(error.getEmail())).append('\n');
        }
        return csv.toString();
    }

    private String quote(String value) {
        if (value == null) return "";
        if (value.indexOf(',') >= 0 || value.indexOf('"') >= 0 || value.indexOf('\n') >= 0) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}