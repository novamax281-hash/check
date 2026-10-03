package com.example.placement.service;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.example.placement.dto.CsvImportResult;
import com.example.placement.dto.CsvValidationResult;
import com.example.placement.entity.Application;
import com.example.placement.entity.RoundStatus;
import com.example.placement.entity.UsersEntries;
import com.example.placement.repository.ApplicationRepository;
import com.example.placement.repository.PlacementRepository;
import com.example.placement.repository.UsersRepository;

class CsvImportServiceTest {

    private UsersRepository usersRepository;
    private ApplicationRepository applicationRepository;
    private StudentService studentService;
    private CsvImportService csvImportService;

    @BeforeEach
    void setUp() {
        usersRepository = mock(UsersRepository.class);
        applicationRepository = mock(ApplicationRepository.class);
        studentService = new StudentService(usersRepository, applicationRepository, mock(PlacementRepository.class),
            mock(ApplicationService.class), mock(MongoTemplate.class), new BCryptPasswordEncoder());
        csvImportService = new CsvImportService(usersRepository, applicationRepository, studentService);
    }

    private Map<String, String> row(String... pairs) {
        Map<String, String> map = new java.util.LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            map.put(pairs[i], pairs[i + 1]);
        }
        return map;
    }

    @Test
    void addValidationIgnoresDatabaseOnlyCgpaField() {
        when(usersRepository.findByEnrollmentno("2023CS101")).thenReturn(null);
        List<Map<String, String>> rows = List.of(
                row("enrollmentno", "2023CS101", "email", "vikram@gmail.com", "course", "B.Tech", "cgpa", "85"));

        CsvValidationResult result = csvImportService.validate("ADD", rows, true);
        assertThat(result.getTotalRows()).isEqualTo(1);
        assertThat(result.getValidRows()).isEqualTo(1);
        assertThat(result.getErrorRows()).isZero();
        assertThat(result.getRows()).hasSize(1);
    }

    @Test
    void addValidationAcceptsValidCgpaScale() {
        when(usersRepository.findByEnrollmentno("2023CS101")).thenReturn(null);
        List<Map<String, String>> rows = List.of(
                row("enrollmentno", "2023CS101", "email", "vikram@gmail.com", "course", "B.Tech", "cgpa", "8.5"));

        CsvValidationResult result = csvImportService.validate("ADD", rows, true);
        assertThat(result.getValidRows()).isEqualTo(1);
        assertThat(result.getErrorRows()).isZero();
    }

    @Test
    void addValidateAcceptsNewStudents() {
        when(usersRepository.findByEnrollmentno("2023CS101")).thenReturn(null);
        List<Map<String, String>> rows = List.of(
                row("enrollmentno", "2023CS101", "name", "Vikram", "email", "vikram@gmail.com",
                        "course", "B.Tech"));

        CsvValidationResult result = csvImportService.validate("ADD", rows, true);
        assertThat(result.getTotalRows()).isEqualTo(1);
        assertThat(result.getValidRows()).isEqualTo(1);
        assertThat(result.getErrorRows()).isZero();
    }

    @Test
    void addValidationRequiresCourseFromSupportedValues() {
        List<Map<String, String>> rows = List.of(
                row("enrollmentno", "E1", "email", "one@mail.com"),
                row("enrollmentno", "E2", "email", "two@mail.com", "course", "M.Tech"));

        CsvValidationResult result = csvImportService.validate("ADD", rows, true);

        assertThat(result.getRows().get(0).getError()).contains("Course is required");
        assertThat(result.getRows().get(1).getError()).contains("Course must be one of: B.Tech, MCA, BCA");
    }

    @Test
    void addValidationAcceptsSupportedCoursesIgnoringCase() {
        List<Map<String, String>> rows = List.of(
                row("enrollmentno", "E1", "email", "one@mail.com", "course", "mca"),
                row("enrollmentno", "E2", "email", "two@mail.com", "course", "BCA"));

        CsvValidationResult result = csvImportService.validate("ADD", rows, true);

        assertThat(result.getValidRows()).isEqualTo(2);
        assertThat(result.getErrorRows()).isZero();
    }

    @Test
    void addCsvDoesNotRequireBranchForMcaOrBcaOrBTech() {
        List<Map<String, String>> rows = List.of(
                row("enrollmentno", "E1", "name", "MCA Student", "email", "mca@mail.com",
                        "password", "Pass@123", "phone", "9000000001", "batch", "2023-2027",
                        "section", "A", "studentstatus", "Active", "course", "MCA"),
                row("enrollmentno", "E2", "name", "BCA Student", "email", "bca@mail.com",
                        "password", "Pass@123", "phone", "9000000002", "batch", "2023-2027",
                        "section", "A", "studentstatus", "Active", "course", "BCA"),
                row("enrollmentno", "E3", "name", "BTech Student", "email", "btech@mail.com",
                        "password", "Pass@123", "phone", "9000000003", "batch", "2023-2027",
                        "section", "A", "studentstatus", "Active", "course", "B.Tech"));

        CsvValidationResult result = csvImportService.validate("ADD", rows, true);

        assertThat(result.getRows().get(0).isValid()).isTrue();
        assertThat(result.getRows().get(1).isValid()).isTrue();
        assertThat(result.getRows().get(2).isValid()).isTrue();
        assertThat(result.getErrorRows()).isZero();
    }

    @Test
    void mixedCourseCsvImportsBTechMcaAndBcaTogetherAndLeavesBranchNull() {
        when(usersRepository.findByEnrollmentno(org.mockito.ArgumentMatchers.anyString())).thenReturn(null);
        when(usersRepository.findByEmailIgnoreCase(org.mockito.ArgumentMatchers.anyString())).thenReturn(null);
        when(usersRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        String csv = "enrollmentNo,name,email,password,phone,batch,section,studentStatus,"
                + "tenthPercentage,twelfthPercentage,Course\n"
                + "E1,BTech Student,btech@mail.com,Pass@123,9000000001,2023-2027,A,Active,,,B.Tech\n"
                + "E2,MCA Student,mca@mail.com,Pass@123,9000000002,2023-2027,A,Active,,,MCA\n"
                + "E3,BCA Student,bca@mail.com,Pass@123,9000000003,2023-2027,A,Active,,,BCA";
        List<Map<String, String>> rows = csvImportService.parseCsvContent(csv, "ADD");

        CsvValidationResult validation = csvImportService.validate("ADD", rows, true);
        CsvImportResult imported = csvImportService.commit("ADD", rows);

        assertThat(validation.getValidRows()).isEqualTo(3);
        assertThat(validation.getErrorRows()).isZero();
        assertThat(imported.getCreatedRows()).isEqualTo(3);
        assertThat(imported.getFailedRows()).isZero();
        org.mockito.ArgumentCaptor<UsersEntries> saved = org.mockito.ArgumentCaptor.forClass(UsersEntries.class);
        verify(usersRepository, org.mockito.Mockito.times(3)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(UsersEntries::getCourse)
                .containsExactly("B.Tech", "MCA", "BCA");
        assertThat(saved.getAllValues()).extracting(UsersEntries::getBranch)
                .containsExactly((String) null, (String) null, (String) null);
    }

    @Test
    void addValidationRejectsDuplicateEnrollment() {
        when(usersRepository.findByEnrollmentno("2023CS101"))
                .thenReturn(new UsersEntries("2023CS101", "9000000000"));
        List<Map<String, String>> rows = List.of(
                row("enrollmentno", "2023CS101", "name", "Vikram", "email", "vikram@gmail.com", "course", "B.Tech"));

        CsvValidationResult result = csvImportService.validate("ADD", rows, true);
        assertThat(result.getErrorRows()).isEqualTo(1);
        assertThat(result.getRows().get(0).isValid()).isFalse();
        assertThat(result.getRows().get(0).getError()).contains("already exists");
    }

    @Test
    void addValidationRejectsBadEmail() {
        when(usersRepository.findByEnrollmentno("2023CS101")).thenReturn(null);
        List<Map<String, String>> rows = List.of(
                row("enrollmentno", "2023CS101", "name", "Vikram", "email", "not-an-email", "course", "B.Tech"));

        CsvValidationResult result = csvImportService.validate("ADD", rows, true);
        assertThat(result.getErrorRows()).isEqualTo(1);
        assertThat(result.getRows().get(0).getError()).contains("Invalid email");
    }

    @Test
    void duplicateRowsAreReported() {
        when(usersRepository.findByEnrollmentno("2023CS101")).thenReturn(null);
        List<Map<String, String>> rows = List.of(
                row("enrollmentno", "2023CS101", "name", "Vikram", "email", "vikram@gmail.com", "course", "B.Tech"),
                row("enrollmentno", "2023CS101", "name", "Vikram", "email", "vikram@gmail.com", "course", "B.Tech"));

        CsvValidationResult result = csvImportService.validate("ADD", rows, true);
        assertThat(result.getTotalRows()).isEqualTo(2);
        assertThat(result.getErrorRows()).isEqualTo(1);
        assertThat(result.getRows().get(1).getError()).contains("Duplicate");
    }

    @Test
    void duplicateEmailsAreDetectedIndependentlyOfEnrollmentNumbers() {
        List<Map<String, String>> rows = List.of(
                row("enrollmentno", "E1", "email", "same@mail.com", "course", "B.Tech"),
                row("enrollmentno", "E2", "email", "SAME@mail.com", "course", "B.Tech"));

        CsvValidationResult result = csvImportService.validate("ADD", rows, true);

        assertThat(result.getRows().get(0).isValid()).isTrue();
        assertThat(result.getRows().get(1).getError()).contains("Duplicate email");
    }

    @Test
    void duplicateEnrollmentNumbersAreDetectedIndependentlyOfEmails() {
        List<Map<String, String>> rows = List.of(
                row("enrollmentno", "E1", "email", "first@mail.com", "course", "B.Tech"),
                row("enrollmentno", "e1", "email", "second@mail.com", "course", "B.Tech"));

        CsvValidationResult result = csvImportService.validate("ADD", rows, true);

        assertThat(result.getRows().get(0).isValid()).isTrue();
        assertThat(result.getRows().get(1).getError()).contains("Duplicate enrollment number");
    }

    @Test
    void statusValidationRejectsInvalidStatus() {
        when(usersRepository.findByEnrollmentno("2023CS101")).thenReturn(new UsersEntries("2023CS101", "9000000000"));
        List<Map<String, String>> rows = List.of(
                row("enrollmentno", "2023CS101", "email", "vikram@gmail.com", "status", "BAD_STATUS"));

        CsvValidationResult result = csvImportService.validate("STATUS", rows, true);
        assertThat(result.getErrorRows()).isEqualTo(1);
        assertThat(result.getRows().get(0).getError()).contains("Invalid status");
    }

    @Test
    void statusValidationAcceptsKnownStatus() {
        when(usersRepository.findByEnrollmentno("2023CS101")).thenReturn(new UsersEntries("2023CS101", "9000000000"));
        List<Map<String, String>> rows = List.of(
                row("enrollmentno", "2023CS101", "email", "vikram@gmail.com", "status", "Placed"));

        CsvValidationResult result = csvImportService.validate("STATUS", rows, true);
        assertThat(result.getValidRows()).isEqualTo(1);
    }

    @Test
    void updateOperationRejectsMissingStudent() {
        when(usersRepository.findByEnrollmentno("MISSING")).thenReturn(null);
        when(usersRepository.findAll()).thenReturn(List.of());
        List<Map<String, String>> rows = List.of(
                row("enrollmentno", "MISSING", "email", "x@mail.com"));

        CsvValidationResult result = csvImportService.validate("UPDATE", rows, true);
        assertThat(result.getErrorRows()).isEqualTo(1);
        assertThat(result.getRows().get(0).getError()).contains("Student not found");
    }

    @Test
    void validationDoesNotModifyDatabase() {
        when(usersRepository.findByEnrollmentno("2023CS101")).thenReturn(null);
        List<Map<String, String>> rows = List.of(
                row("enrollmentno", "2023CS101", "name", "Vikram", "email", "vikram@gmail.com", "status", "Placed",
                        "course", "B.Tech"));

        CsvValidationResult result = csvImportService.validate("ADD", rows, true);
        assertThat(result.getValidRows()).isEqualTo(1);
        verify(usersRepository, never()).save(any());
    }

    @Test
    void commitCreatesNewStudents() {
        when(usersRepository.findByEnrollmentno("2023CS101")).thenReturn(null);
        when(usersRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        List<Map<String, String>> rows = List.of(
                row("enrollmentno", "2023CS101", "name", "Vikram", "email", "vikram@gmail.com",
                        "course", "B.Tech", "password", "Pass@123", "phone", "9000000000",
                        "batch", "2023-2027", "section", "A", "studentstatus", "Active"));

        CsvImportResult result = csvImportService.commit("ADD", rows);
        assertThat(result.getCreatedRows()).isEqualTo(1);
        assertThat(result.getFailedRows()).isZero();
        verify(usersRepository).save(any());
    }

    @Test
    void csvCommitHashesPasswordAndContinuesAfterInvalidRows() {
        when(usersRepository.findByEnrollmentno("DUP")).thenReturn(new UsersEntries("DUP", "9000000000"));
        when(usersRepository.findByEnrollmentno("NEW")).thenReturn(null);
        when(usersRepository.findByEmailIgnoreCase("new@mail.com")).thenReturn(null);
        when(usersRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        List<Map<String, String>> rows = List.of(
                row("enrollmentno", "DUP", "name", "Duplicate", "email", "duplicate@mail.com"),
                row("enrollmentno", "NEW", "name", "New Student", "email", "new@mail.com",
                        "password", "Pass@123", "studentstatus", "Active"));

        CsvImportResult result = csvImportService.commit("ADD", rows);

        assertThat(result.getTotalRows()).isEqualTo(2);
        assertThat(result.getCreatedRows()).isEqualTo(1);
        assertThat(result.getFailedRows()).isEqualTo(1);
        assertThat(result.getErrors().get(0).getEnrollmentNo()).isEqualTo("DUP");
        assertThat(result.getErrors().get(0).getReason()).contains("already exists");
        org.mockito.ArgumentCaptor<UsersEntries> saved = org.mockito.ArgumentCaptor.forClass(UsersEntries.class);
        verify(usersRepository).save(saved.capture());
        assertThat(new BCryptPasswordEncoder().matches("Pass@123", saved.getValue().getPasswordHash())).isTrue();
        assertThat(saved.getValue().getPassword()).isNull();
    }

    @Test
    void commitUpdatesStudentStatus() {
        UsersEntries student = new UsersEntries("2023CS101", "9000000000");
        student.setId("u1");
        student.setStudentStatus("Active");
        when(usersRepository.findByEnrollmentno("2023CS101")).thenReturn(student);
        when(usersRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        List<Map<String, String>> rows = List.of(
                row("enrollmentno", "2023CS101", "email", "vikram@gmail.com", "status", "Placed"));

        CsvImportResult result = csvImportService.commit("STATUS", rows);
        assertThat(result.getUpdatedRows()).isEqualTo(1);
        assertThat(student.getStudentStatus()).isEqualTo("Placed");
    }

    @Test
    void commitApplicationRound() {
        UsersEntries student = new UsersEntries("2023CS101", "9000000000");
        student.setId("u1");
        when(usersRepository.findByEnrollmentno("2023CS101")).thenReturn(student);
        Application app = new Application("u1", "j1");
        app.setId("a1");
        when(applicationRepository.findByStudentIdAndPlacementId("u1", "j1"))
                .thenReturn(java.util.Optional.of(app));
        when(applicationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        List<Map<String, String>> rows = List.of(
                row("enrollmentno", "2023CS101", "email", "vikram@gmail.com", "jobId", "j1", "round", "3"));

        CsvImportResult result = csvImportService.commit("APPLICATION_ROUND", rows);
        assertThat(result.getUpdatedRows()).isEqualTo(1);
        assertThat(app.getCurrentRound()).isEqualTo(3);
        assertThat(app.getStatus()).isEqualTo("IN_PROGRESS");
        assertThat(app.getRounds()).extracting(r -> r.getRound()).containsExactly(1, 3);
        assertThat(app.getRounds()).allMatch(r -> r.getStatus() == RoundStatus.PENDING);
    }

    @Test
    void commitApplicationRoundSkipsTerminalApplications() {
        UsersEntries student = new UsersEntries("2023CS101", "9000000000");
        student.setId("u1");
        when(usersRepository.findByEnrollmentno("2023CS101")).thenReturn(student);
        Application app = new Application("u1", "j1");
        app.setId("a1");
        app.setStatus("SELECTED");
        when(applicationRepository.findByStudentIdAndPlacementId("u1", "j1"))
                .thenReturn(java.util.Optional.of(app));
        when(applicationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        List<Map<String, String>> rows = List.of(
                row("enrollmentno", "2023CS101", "email", "vikram@gmail.com", "jobId", "j1", "round", "3"));

        CsvImportResult result = csvImportService.commit("APPLICATION_ROUND", rows);
        assertThat(result.getUpdatedRows()).isEqualTo(0);
        assertThat(app.getCurrentRound()).isEqualTo(1);
        assertThat(result.getErrors()).anyMatch(e -> "TERMINAL".equals(e.getError()));
    }

    @Test
    void commitApplicationRoundWithoutApplicationSkipped() {
        UsersEntries student = new UsersEntries("2023CS101", "9000000000");
        student.setId("u1");
        when(usersRepository.findByEnrollmentno("2023CS101")).thenReturn(student);
        when(applicationRepository.findByStudentIdAndPlacementId("u1", "j1")).thenReturn(java.util.Optional.empty());

        List<Map<String, String>> rows = List.of(
                row("enrollmentno", "2023CS101", "email", "vikram@gmail.com", "jobId", "j1", "round", "2"));

        CsvImportResult result = csvImportService.commit("APPLICATION_ROUND", rows);
        assertThat(result.getSkippedRows()).isGreaterThanOrEqualTo(1);
        assertThat(result.getErrors()).isNotEmpty();
    }

    @Test
    void invalidStatusProducesErrorReport() {
        when(usersRepository.findByEnrollmentno("2023CS101")).thenReturn(new UsersEntries("2023CS101", "9000000000"));
        List<Map<String, String>> rows = List.of(
                row("enrollmentno", "2023CS101", "email", "vikram@gmail.com", "status", "BAD_STATUS"));

        CsvImportResult result = csvImportService.commit("STATUS", rows);
        assertThat(result.getFailedRows()).isGreaterThanOrEqualTo(1);
        String report = csvImportService.buildErrorReport("STATUS", rows, result);
        assertThat(report).contains("row_number,identifier,error,reason");
        assertThat(report).contains("BAD_STATUS");
    }

    @Test
    void unsupportedOperationRejected() {
        List<Map<String, String>> rows = List.of(row("enrollmentno", "E1"));
        CsvValidationResult result = csvImportService.validate("NONSENSE", rows, true);
        assertThat(result.getGlobalErrors()).isNotEmpty();
        assertThat(result.getGlobalErrors().get(0)).contains("Unsupported");
    }

    @Test
    void csvParsingHandlesQuotedCommas() {
        String header = "enrollmentno,name,email";
        String line = "2023CS101,\"Sharma, Vikram\",vikram@gmail.com";
        var rows = csvImportService.parseCsv(List.of(header, line));
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("name")).isEqualTo("Sharma, Vikram");
    }

    @Test
    void addTemplateContainsStudentAcademicFields() {
        assertThat(csvImportService.templateColumns("ADD")).containsExactly(
                "enrollmentNo", "name", "email", "password", "phone", "batch", "section",
                "studentStatus", "tenthPercentage", "twelfthPercentage", "Course");
    }

    @Test
    void addParserRequiresExactHeadersAndPreservesQuotedMultilineValues() {
        String headers = "enrollmentNo,name,email,password,phone,batch,section,studentStatus,"
                + "tenthPercentage,twelfthPercentage,Course";
        String content = headers + "\r\n2023CS101,\"Doe, John\r\nJr.\",john@example.com,,9876543210,"
                + "2023-2027,A,Active,,,B.Tech";

        var rows = csvImportService.parseCsvContent(content, "ADD");

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("name")).isEqualTo("Doe, John\r\nJr.");
        assertThat(rows.get(0).get("__rownumber")).isEqualTo("2");
        assertThatThrownBy(() -> csvImportService.parseCsvContent(
                "enrollmentNo,name,email,course", "ADD"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exactly the student fields");
    }

    @Test
    void addParserIgnoresEmptyRecordsAndRejectsRowsWithMissingColumns() {
        String headers = "enrollmentNo,name,email,password,phone,batch,section,studentStatus,"
                + "tenthPercentage,twelfthPercentage,Course";
        var rows = csvImportService.parseCsvContent(headers + "\n,,,,,,,,,\n"
                + "E1,Jane,jane@mail.com,Pass@123,9000000000,2023-2027,A,Active,,,B.Tech\n"
                + "E2,John,john@mail.com,,9000000001,2023-2027", "ADD");

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).get("__rownumber")).isEqualTo("3");
        assertThat(rows.get(1).get("__rownumber")).isEqualTo("4");
        CsvValidationResult result = csvImportService.validate("ADD", rows, true);
        assertThat(result.getRows().get(0).isValid()).isTrue();
        assertThat(result.getRows().get(1).isValid()).isFalse();
        assertThat(result.getRows().get(1).getError()).contains("expected 11");
    }

    @Test
    void negativeEnrollmentRequiredForAdd() {
        when(usersRepository.findByEnrollmentno(" ")).thenReturn(null);
        List<Map<String, String>> rows = List.of(
                row("enrollmentno", "", "name", "Vikram", "email", "vikram@gmail.com"));

        CsvValidationResult result = csvImportService.validate("ADD", rows, true);
        assertThat(result.getErrorRows()).isEqualTo(1);
        assertThat(result.getRows().get(0).getError()).contains("Enrollment number is required");
    }
}