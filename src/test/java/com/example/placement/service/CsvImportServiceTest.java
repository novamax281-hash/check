package com.example.placement.service;

import com.example.placement.dto.CsvImportResult;
import com.example.placement.dto.CsvValidationResult;
import com.example.placement.entity.Application;
import com.example.placement.entity.RoundStatus;
import com.example.placement.entity.UsersEntries;
import com.example.placement.repository.ApplicationRepository;
import com.example.placement.repository.UsersRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CsvImportServiceTest {

    private UsersRepository usersRepository;
    private ApplicationRepository applicationRepository;
    private CsvImportService csvImportService;

    @BeforeEach
    void setUp() {
        usersRepository = mock(UsersRepository.class);
        applicationRepository = mock(ApplicationRepository.class);
        csvImportService = new CsvImportService(usersRepository, applicationRepository, new BCryptPasswordEncoder());
    }

    private Map<String, String> row(String... pairs) {
        Map<String, String> map = new java.util.LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            map.put(pairs[i], pairs[i + 1]);
        }
        return map;
    }

    @Test
    void addValidationRejectsCgpaOutsideZeroToTen() {
        when(usersRepository.findByEnrollmentno("2023CS101")).thenReturn(null);
        List<Map<String, String>> rows = List.of(
                row("enrollmentno", "2023CS101", "email", "vikram@gmail.com", "cgpa", "85"));

        CsvValidationResult result = csvImportService.validate("ADD", rows, true);
        assertThat(result.getTotalRows()).isEqualTo(1);
        assertThat(result.getValidRows()).isZero();
        assertThat(result.getErrorRows()).isEqualTo(1);
        assertThat(result.getRows()).hasSize(1);
        assertThat(result.getRows().get(0).getError()).contains("CGPA must be between 0 and 10");
    }

    @Test
    void addValidationAcceptsValidCgpaScale() {
        when(usersRepository.findByEnrollmentno("2023CS101")).thenReturn(null);
        List<Map<String, String>> rows = List.of(
                row("enrollmentno", "2023CS101", "email", "vikram@gmail.com", "cgpa", "8.5"));

        CsvValidationResult result = csvImportService.validate("ADD", rows, true);
        assertThat(result.getValidRows()).isEqualTo(1);
        assertThat(result.getErrorRows()).isZero();
    }

    @Test
    void addValidateAcceptsNewStudents() {
        when(usersRepository.findByEnrollmentno("2023CS101")).thenReturn(null);
        List<Map<String, String>> rows = List.of(
                row("enrollmentno", "2023CS101", "name", "Vikram", "email", "vikram@gmail.com", "branch", "CSE"));

        CsvValidationResult result = csvImportService.validate("ADD", rows, true);
        assertThat(result.getTotalRows()).isEqualTo(1);
        assertThat(result.getValidRows()).isEqualTo(1);
        assertThat(result.getErrorRows()).isZero();
    }

    @Test
    void addValidationRejectsDuplicateEnrollment() {
        when(usersRepository.findByEnrollmentno("2023CS101"))
                .thenReturn(new UsersEntries("2023CS101", "9000000000"));
        List<Map<String, String>> rows = List.of(
                row("enrollmentno", "2023CS101", "name", "Vikram", "email", "vikram@gmail.com"));

        CsvValidationResult result = csvImportService.validate("ADD", rows, true);
        assertThat(result.getErrorRows()).isEqualTo(1);
        assertThat(result.getRows().get(0).isValid()).isFalse();
        assertThat(result.getRows().get(0).getError()).contains("already exists");
    }

    @Test
    void addValidationRejectsBadEmail() {
        when(usersRepository.findByEnrollmentno("2023CS101")).thenReturn(null);
        List<Map<String, String>> rows = List.of(
                row("enrollmentno", "2023CS101", "name", "Vikram", "email", "not-an-email"));

        CsvValidationResult result = csvImportService.validate("ADD", rows, true);
        assertThat(result.getErrorRows()).isEqualTo(1);
        assertThat(result.getRows().get(0).getError()).contains("Invalid email");
    }

    @Test
    void duplicateRowsAreReported() {
        when(usersRepository.findByEnrollmentno("2023CS101")).thenReturn(null);
        List<Map<String, String>> rows = List.of(
                row("enrollmentno", "2023CS101", "name", "Vikram", "email", "vikram@gmail.com"),
                row("enrollmentno", "2023CS101", "name", "Vikram", "email", "vikram@gmail.com"));

        CsvValidationResult result = csvImportService.validate("ADD", rows, true);
        assertThat(result.getTotalRows()).isEqualTo(2);
        assertThat(result.getErrorRows()).isEqualTo(1);
        assertThat(result.getRows().get(1).getError()).contains("Duplicate");
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
                row("enrollmentno", "2023CS101", "name", "Vikram", "email", "vikram@gmail.com", "status", "Placed"));

        CsvValidationResult result = csvImportService.validate("ADD", rows, true);
        assertThat(result.getValidRows()).isEqualTo(1);
        verify(usersRepository, never()).save(any());
    }

    @Test
    void commitCreatesNewStudents() {
        when(usersRepository.findByEnrollmentno("2023CS101")).thenReturn(null);
        when(usersRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        List<Map<String, String>> rows = List.of(
                row("enrollmentno", "2023CS101", "name", "Vikram", "email", "vikram@gmail.com", "branch", "CSE"));

        CsvImportResult result = csvImportService.commit("ADD", rows);
        assertThat(result.getCreatedRows()).isEqualTo(1);
        assertThat(result.getFailedRows()).isZero();
        verify(usersRepository).save(any());
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
    void negativeEnrollmentRequiredForAdd() {
        when(usersRepository.findByEnrollmentno(" ")).thenReturn(null);
        List<Map<String, String>> rows = List.of(
                row("enrollmentno", "", "name", "Vikram", "email", "vikram@gmail.com"));

        CsvValidationResult result = csvImportService.validate("ADD", rows, true);
        assertThat(result.getErrorRows()).isEqualTo(1);
        assertThat(result.getRows().get(0).getError()).contains("Enrollment number is required");
    }
}