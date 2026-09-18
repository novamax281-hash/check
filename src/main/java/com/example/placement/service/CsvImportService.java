package com.example.placement.service;

import com.example.placement.dto.CsvImportResult;
import com.example.placement.dto.CsvValidationResult;
import com.example.placement.entity.Application;
import com.example.placement.entity.UsersEntries;
import com.example.placement.repository.ApplicationRepository;
import com.example.placement.repository.UsersRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class CsvImportService {

    private final UsersRepository usersRepository;
    private final ApplicationRepository applicationRepository;

    public CsvImportService(UsersRepository usersRepository, ApplicationRepository applicationRepository) {
        this.usersRepository = usersRepository;
        this.applicationRepository = applicationRepository;
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
            case "UPDATE":
            case "ADD":
            default:
                return List.of("enrollmentno", "name", "email", "phoneno", "batch", "branch", "section",
                        "course", "college", "cgpa", "tenthPercentage", "twelfthPercentage",
                        "graduationPercentage", "dob", "gender", "city", "state", "studentStatus");
        }
    }

    public List<Map<String, String>> parseCsv(List<String> lines) {
        List<Map<String, String>> rows = new ArrayList<>();
        if (lines == null || lines.isEmpty()) {
            return rows;
        }
        String header = lines.get(0);
        List<String> headers = splitLine(header);
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line == null || line.isBlank()) {
                continue;
            }
            List<String> values = splitLine(line);
            Map<String, String> row = new LinkedHashMap<>();
            for (int c = 0; c < headers.size(); c++) {
                String headerName = headers.get(c).trim().toLowerCase();
                if (headerName.isEmpty()) continue;
                String value = c < values.size() ? values.get(c) : "";
                row.put(headerName, value.trim());
            }
            rows.add(row);
        }
        return rows;
    }

    private List<String> splitLine(String line) {
        List<String> values = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (ch == ',' && !inQuotes) {
                values.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        values.add(current.toString());
        return values;
    }

    public CsvValidationResult validate(String operation, List<Map<String, String>> rows, boolean previewMode) {
        CsvValidationResult result = new CsvValidationResult();
        String op = operation == null ? "ADD" : operation.toUpperCase();
        if (!supportedOperations().contains(op)) {
            result.getGlobalErrors().add("Unsupported operation: " + operation);
            return result;
        }
        result.setTotalRows(rows.size());

        Map<String, Integer> seenIdentifiers = new HashMap<>();

        for (int i = 0; i < rows.size(); i++) {
            Map<String, String> row = rows.get(i);
            int rowNumber = i + 2;
            String enrollment = row.get("enrollmentno");
            String email = row.get("email");
            String name = row.get("name");
            CsvValidationResult.CsvRowResult rowResult =
                    new CsvValidationResult.CsvRowResult(rowNumber, enrollment, name, email);

            List<String> errors = new ArrayList<>();

            if (op.equals("ADD")) {
                if (isBlank(enrollment)) {
                    errors.add("Enrollment number is required");
                } else {
                    if (usersRepository.findByEnrollmentno(enrollment) != null) {
                        errors.add("Student with this enrollment already exists");
                    }
                }
                if (isBlank(email)) {
                    errors.add("Email is required");
                } else if (!email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
                    errors.add("Invalid email format");
                }
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

            if ("ACADEMIC".equals(op) || "ADD".equals(op) || "UPDATE".equals(op)) {
                validatePercentage(row.get("cgpa"), "CGPA", errors);
                validatePercentage(row.get("tenthPercentage"), "10th Percentage", errors);
                validatePercentage(row.get("twelfthPercentage"), "12th Percentage", errors);
                validatePercentage(row.get("graduationPercentage"), "Graduation Percentage", errors);
            }

            String identifier = !isBlank(enrollment) ? enrollment : (isBlank(email) ? "" : email);
            if (!identifier.isEmpty()) {
                Integer firstSeen = seenIdentifiers.putIfAbsent(identifier.toLowerCase(), rowNumber);
                if (firstSeen != null) {
                    errors.add("Duplicate identifier, first seen on row " + firstSeen);
                }
            }

            if (!errors.isEmpty()) {
                rowResult.setValid(false);
                rowResult.setError(String.join("; ", errors));
                result.setErrorRows(result.getErrorRows() + 1);
            } else {
                result.setValidRows(result.getValidRows() + 1);
                rowResult.setStatus(row.get("status") != null ? row.get("status") : "OK");
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

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
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
            int rowNumber = i + 2;
            String enrollment = row.get("enrollmentno");
            String email = row.get("email");
            String identifier = !isBlank(enrollment) ? enrollment : email;

            CsvValidationResult.CsvRowResult validationRow = validation.getRows().size() > i
                    ? validation.getRows().get(i) : null;
            if (validationRow != null && !validationRow.isValid()) {
                result.getErrors().add(new CsvImportResult.CsvError(rowNumber, identifier,
                        "VALIDATION", validationRow.getError()));
                continue;
            }

            try {
                switch (op) {
                    case "ADD": {
                        UsersEntries student = new UsersEntries(enrollment, row.get("phoneno"));
                        student.setName(row.get("name"));
                        student.setEmail(email);
                        applySharedFields(student, row);
                        usersRepository.save(student);
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
                        application.setCurrentRound(targetRound);
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
                result.getErrors().add(new CsvImportResult.CsvError(rowNumber, identifier,
                        "ERROR", e.getReason() != null ? e.getReason() : "Could not process row"));
            } catch (Exception e) {
                result.setFailedRows(result.getFailedRows() + 1);
                result.getErrors().add(new CsvImportResult.CsvError(rowNumber, identifier,
                        "ERROR", "Unexpected error processing row"));
            }
        }
        result.setCreatedRows(created);
        result.setUpdatedRows(updated);
        result.setSkippedRows(skipped + validation.getErrorRows());
        result.setSuccessfulRows(created + updated);
        return result;
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
        StringBuilder csv = new StringBuilder("row_number,identifier,error,reason\n");
        for (CsvImportResult.CsvError error : result.getErrors()) {
            csv.append(error.getRowNumber()).append(',')
                    .append(quote(error.getIdentifier())).append(',')
                    .append(quote(error.getError())).append(',')
                    .append(quote(error.getReason())).append('\n');
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