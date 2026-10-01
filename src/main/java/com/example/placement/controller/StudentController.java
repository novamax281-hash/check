package com.example.placement.controller;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.example.placement.dto.BulkRoundRequest;
import com.example.placement.dto.BulkStatusRequest;
import com.example.placement.dto.CsvImportRequest;
import com.example.placement.dto.CsvImportResult;
import com.example.placement.dto.CsvValidationResult;
import com.example.placement.dto.EmailCsvRequest;
import com.example.placement.dto.ExportRequest;
import com.example.placement.dto.StudentDetail;
import com.example.placement.dto.StudentListResponse;
import com.example.placement.entity.UsersEntries;
import com.example.placement.service.CsvExportService;
import com.example.placement.service.CsvImportService;
import com.example.placement.service.EmailCsvService;
import com.example.placement.service.StudentService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/admin/students")
public class StudentController {

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024;

    private final StudentService studentService;
    private final CsvImportService csvImportService;
    private final CsvExportService csvExportService;
    private final EmailCsvService emailCsvService;

    public StudentController(StudentService studentService, CsvImportService csvImportService,
                             CsvExportService csvExportService, EmailCsvService emailCsvService) {
        this.studentService = studentService;
        this.csvImportService = csvImportService;
        this.csvExportService = csvExportService;
        this.emailCsvService = emailCsvService;
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleStatusException(ResponseStatusException exception) {
        String message = exception.getReason() != null ? exception.getReason() : "Request failed";
        return ResponseEntity.status(exception.getStatusCode()).body(Map.of("message", message));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(". "));
        return ResponseEntity.badRequest().body(Map.of("message", message));
    }

    @GetMapping
    public StudentListResponse listStudents(@RequestParam(required = false) String search,
                                            @RequestParam(required = false) List<String> batch,
                                            @RequestParam(required = false) List<String> branch,
                                            @RequestParam(required = false) List<String> section,
                                            @RequestParam(required = false) String studentStatus,
                                            @RequestParam(required = false) String applicationStatus,
                                            @RequestParam(required = false) String company,
                                            @RequestParam(required = false) String job,
                                            @RequestParam(required = false) Integer round,
                                            @RequestParam(required = false) String appliedFrom,
                                            @RequestParam(required = false) String appliedTo,
                                            @RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "25") int pageSize) {
        return studentService.listStudents(search, batch, branch, section, studentStatus, applicationStatus,
                company, job, round, appliedFrom, appliedTo, page, pageSize);
    }

    @GetMapping("/stats")
    public Map<String, Long> stats() {
        StudentListResponse response = studentService.listStudents(null, null, null, null, null,
                null, null, null, null, null, null, 1, 25);
        return Map.of(
                "total", response.getStats() == null ? 0 : (Long) response.getStats().getOrDefault("total", 0L),
                "active", response.getStats() == null ? 0 : (Long) response.getStats().getOrDefault("active", 0L),
                "placed", response.getStats() == null ? 0 : (Long) response.getStats().getOrDefault("placed", 0L),
                "notPlaced", response.getStats() == null ? 0 : (Long) response.getStats().getOrDefault("notPlaced", 0L));
    }

    @GetMapping("/filter-options")
    public Map<String, Object> filterOptions() {
        return studentService.filterOptions();
    }

    @GetMapping("/template")
    public ResponseEntity<byte[]> template(@RequestParam(defaultValue = "ADD") String operation) {
        List<String> columns = csvImportService.templateColumns(operation);
        StringBuilder csv = new StringBuilder(String.join(",", columns)).append('\n');
        return csvResponse(csv.toString().getBytes(StandardCharsets.UTF_8),
                "student_template_" + operation.toLowerCase() + ".csv");
    }

    @GetMapping("/{id}")
    public StudentDetail getStudent(@PathVariable String id) {
        return studentService.getStudentDetail(id);
    }

    @PatchMapping("/{id}")
    public UsersEntries updateStudent(@PathVariable String id, @RequestBody UsersEntries update) {
        return studentService.updateStudent(id, update);
    }

    @PostMapping
    public UsersEntries addStudent(@RequestBody UsersEntries student) {
        return studentService.addStudent(student);
    }

    @PostMapping("/bulk-status")
    public Map<String, Object> bulkStatus(@Valid @RequestBody BulkStatusRequest request) {
        return studentService.bulkUpdateStatus(request.getStudentIds(), request.getStatus());
    }

    @PostMapping("/bulk-round")
    public Map<String, Object> bulkRound(@Valid @RequestBody BulkRoundRequest request) {
        List<String> applicationIds = request.getApplicationIds() == null
                ? List.of() : request.getApplicationIds();
        List<String> studentIds = request.getStudentIds() == null
                ? List.of() : request.getStudentIds();
        String remark = request.getAdminRemark();
        if (applicationIds.isEmpty() && studentIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "At least one application ID or student ID is required");
        }
        return studentService.bulkMoveRound(applicationIds, studentIds, request.getTargetRound(), remark);
    }

    @PostMapping("/import/validate")
    public ResponseEntity<?> validateImport(@RequestParam("operation") String operation,
                                            @RequestParam("file") MultipartFile file) {
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("message", "The uploaded file is empty"));
            }
            if (file.getSize() > MAX_FILE_SIZE) {
                return ResponseEntity.badRequest().body(Map.of("message", "File exceeds the 5 MB size limit"));
            }
            String filename = file.getOriginalFilename();
            if (filename == null || !filename.toLowerCase().endsWith(".csv")) {
                return ResponseEntity.badRequest().body(Map.of("message", "Only .csv files are accepted"));
            }
            String content = new String(file.getBytes(), StandardCharsets.UTF_8);
            List<Map<String, String>> rows = csvImportService.parseCsvContent(content, operation);
            CsvValidationResult result = csvImportService.validate(operation, rows, true);
            return ResponseEntity.ok(result);
        } catch (IOException e) {
            return ResponseEntity.badRequest().body(Map.of("message", "Could not read the uploaded file"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/import/commit")
    public CsvImportResult commitImport(@RequestBody CsvImportRequest request) {
        if (request.getRows() == null || request.getRows().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No CSV rows provided");
        }
        try {
            List<Map<String, String>> rows = csvImportService.parseCsv(request.getRows(), request.getOperation());
            return csvImportService.commit(request.getOperation(), rows);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @PostMapping("/import/error-report")
    public ResponseEntity<byte[]> errorReport(@RequestBody CsvImportRequest request) {
        CsvImportResult result = new CsvImportResult();
        result.setErrors(request.getErrors() == null ? List.of() : request.getErrors());
        String report = csvImportService.buildErrorReport(request.getOperation(), List.of(), result);
        return csvResponse(report.getBytes(StandardCharsets.UTF_8),
                "import_error_report_" + LocalDate.now() + ".csv");
    }

    @PostMapping("/export")
    public ResponseEntity<byte[]> export(@RequestBody(required = false) ExportRequest request) throws IOException {
        List<StudentDetail> students = resolveStudents(request == null ? null : request.getScope(),
                request == null ? null : request);
        String content = csvExportService.studentsCsv(students,
                request == null ? null : request.getColumns());
        return csvResponse(content.getBytes(StandardCharsets.UTF_8),
                "students_export_" + LocalDate.now() + ".csv");
    }

    @PostMapping("/email-csv")
    public ResponseEntity<Map<String, Object>> emailCsv(@Valid @RequestBody EmailCsvRequest request)
            throws IOException {
        List<StudentDetail> students = resolveStudents(request.getScope(), request);
        String content = csvExportService.studentsCsv(students, request.getColumns());
        String filename = safeCsvFilename(request.getFileName(),
                "students_export_" + LocalDate.now().toString().replace("-", "_") + ".csv");
        emailCsvService.sendCsv(request.getRecipientEmail(), content, filename, request.getSubject());
        return ResponseEntity.ok(Map.of(
                "message", "CSV emailed successfully",
                "recipient", request.getRecipientEmail(),
                "file", filename,
                "students", students.size()));
    }

    private static String safeCsvFilename(String requested, String fallback) {
        if (requested == null || requested.isBlank()) {
            return fallback;
        }
        String cleaned = requested.trim().replaceAll("[^a-zA-Z0-9._-]", "_");
        if (!cleaned.toLowerCase().endsWith(".csv")) {
            cleaned = cleaned + ".csv";
        }
        return cleaned;
    }

    private List<StudentDetail> resolveStudents(String scope, ExportRequest request) {
        if (request != null && request.getScope() != null && request.getScope().equals("selected")
                && request.getStudentIds() != null && !request.getStudentIds().isEmpty()) {
            List<StudentDetail> all = studentService.listStudentsForReport(null, null, null, null, null,
                    null, null, null, null, null, null, 1, Integer.MAX_VALUE).getStudents();
            return all.stream()
                    .filter(student -> request.getStudentIds().contains(student.getId()))
                    .collect(Collectors.toList());
        }
        return studentService.listStudentsForReport(
                request == null ? null : request.getSearch(),
                request == null ? null : request.getBatch(),
                request == null ? null : request.getBranch(),
                request == null ? null : request.getSection(),
                request == null ? null : request.getStudentStatus(),
                request == null ? null : request.getApplicationStatus(),
                request == null ? null : request.getCompany(),
                request == null ? null : request.getJob(),
                request == null ? null : request.getRound(),
                request == null ? null : request.getAppliedFrom(),
                request == null ? null : request.getAppliedTo(),
                1, Integer.MAX_VALUE).getStudents();
    }

    private List<StudentDetail> resolveStudents(String scope, EmailCsvRequest request) {
        if (request != null && scope != null && scope.equals("selected")
                && request.getStudentIds() != null && !request.getStudentIds().isEmpty()) {
            List<StudentDetail> all = studentService.listStudentsForReport(null, null, null, null, null,
                    null, null, null, null, null, null, 1, Integer.MAX_VALUE).getStudents();
            return all.stream()
                    .filter(student -> request.getStudentIds().contains(student.getId()))
                    .collect(Collectors.toList());
        }
        return studentService.listStudentsForReport(
                request.getSearch(), request.getBatch(), request.getBranch(), request.getSection(),
                request.getStudentStatus(), request.getApplicationStatus(),
                request.getCompany(), request.getJob(), request.getRound(),
                request.getAppliedFrom(), request.getAppliedTo(),
                1, Integer.MAX_VALUE).getStudents();
    }

    private ResponseEntity<byte[]> csvResponse(byte[] content, String filename) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv;charset=UTF-8"));
        headers.setContentDispositionFormData("attachment", filename);
        byte[] withBom = new byte[content.length + 3];
        withBom[0] = (byte) 0xEF;
        withBom[1] = (byte) 0xBB;
        withBom[2] = (byte) 0xBF;
        System.arraycopy(content, 0, withBom, 3, content.length);
        return new ResponseEntity<>(withBom, headers, HttpStatus.OK);
    }
}