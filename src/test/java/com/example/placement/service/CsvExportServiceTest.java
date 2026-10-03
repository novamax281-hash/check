package com.example.placement.service;

import java.io.IOException;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.example.placement.dto.ApplicationDetail;
import com.example.placement.dto.RoundDetail;
import com.example.placement.dto.StudentDetail;
import com.example.placement.entity.Placement;
import com.example.placement.entity.PlacementRound;

class CsvExportServiceTest {

    private final CsvExportService csvExportService = new CsvExportService();

    @Test
    void studentCsvIncludesUsefulStudentAndApplicationDetails() throws IOException {
        ApplicationDetail application = new ApplicationDetail();
        application.setCompany("Northwind");
        application.setRole("Software Engineer");
        application.setSelectedCourse("B.Tech");

        StudentDetail student = new StudentDetail();
        student.setName("Asha Rao");
        student.setCourse("Computer Science");
        student.setApplications(List.of(application));

        String csv = csvExportService.studentsCsv(List.of(student), null);
        String[] lines = csv.split("\\R");

        assertTrue(lines[0].contains("Course"));
        assertTrue(lines[0].contains("Applied Companies"));
        assertTrue(lines[0].contains("Applied Roles"));
        assertTrue(lines[0].contains("Selected Courses"));
        assertTrue(lines[1].contains("Asha Rao"));
        assertTrue(lines[1].contains("Northwind"));
        assertTrue(lines[1].contains("Software Engineer"));
        assertTrue(lines[1].contains("B.Tech"));
    }

    @Test
    void applicationsCsvContainsHeaderAndFields() throws IOException {
        ApplicationDetail application = new ApplicationDetail();
        application.setApplicationId("app1");
        application.setEnrollmentno("ENR001");
        application.setStudentName("Rahul Sharma");
        application.setStudentEmail("rahul@example.com");
        application.setPhoneNo("9999999999");
        application.setStudentCourse("Computer Science");
        application.setSelectedCourse("Data Science");
        application.setStudentBatch("2022-2026");
        application.setStudentId("student1");
        application.setCompany("ABC, Technologies");
        application.setRole("Intern");
        application.setStatus("IN_PROGRESS");
        application.setCurrentRound(2);
        application.setTotalRounds(3);
        application.setResumeUrl("https://files.example.com/resumes/rahul.pdf");

        RoundDetail round = new RoundDetail();
        round.setRound(1);
        round.setStatus("QUALIFIED");
        round.setScore(85.0);
        application.setRounds(List.of(round));
        application.setApplicationDate(LocalDateTime.of(2026, 8, 20, 10, 30));

        StringWriter out = new StringWriter();
        csvExportService.writeApplications(out, List.of(application),
                List.of("Application ID", "Enrollment No", "Student Name", "Email", "Phone",
                        "Student Course", "Selected Course", "Student Batch", "Student ID",
                        "Company", "Job Role", "Selected Job Role", "Status", "Current Round",
                        "Total Rounds", "Round History", "Current Round Score", "Resume URL"));
        String csv = out.toString();
        String[] lines = csv.split("\n", -1);

        assertTrue(lines[0].startsWith("Application ID,Enrollment No,Student Name,Email,Phone"));
        assertTrue(lines[0].trim().endsWith("Resume URL"));
        assertTrue(lines[0].contains("Selected Course"));
        assertTrue(lines[0].contains("Student Course"));
        assertTrue(lines[0].contains("Selected Job Role"));
        assertTrue(csv.contains("ENR001"));
        assertTrue(csv.contains("https://files.example.com/resumes/rahul.pdf"));
        assertTrue(csv.contains("Data Science"));
        assertTrue(csv.contains("Computer Science"));
        assertTrue(csv.contains("\"ABC, Technologies\""));
        assertTrue(csv.contains("Round 1: QUALIFIED (85.0)"));
        assertEquals("", lines[lines.length - 1]);
    }

    @Test
    void applicationsCsvDefaultsWithoutDepartment() throws IOException {
        StringWriter out = new StringWriter();
        csvExportService.writeApplications(out, List.of(new ApplicationDetail()));
        String header = out.toString().split("\\R", -1)[0];

        assertEquals(19, CsvExportService.APPLICATION_DEFAULT_COLUMNS.size());
        assertEquals("Application ID,Student ID,Enrollment No,Student Name,Email,Phone,"
                + "Student Course,Student Batch,Branch,CGPA,Company,Job Role,Salary / CTC,"
                + "Application Status,Current Round,Current Round Score,Admin Remark,Applied On,Last Updated",
                header);
        assertFalse(CsvExportService.APPLICATION_COLUMNS.contains("Department"));
        assertFalse(header.contains("Department"));
    }

    @Test
    void applicationsCsvExportsOnlySelectedColumns() throws IOException {
        ApplicationDetail application = new ApplicationDetail();
        application.setApplicationId("app1");
        application.setCompany("Northwind");
        application.setRole("Software Engineer, Gen AI");
        application.setSelectedRole("Gen AI");
        application.setSelectedCourse("B.Tech");
        application.setStudentCourse("Computer Science");

        StringWriter out = new StringWriter();
        csvExportService.writeApplications(out, List.of(application),
                List.of("Company", "Job Role", "Selected Job Role", "Selected Course", "Student Course"));
        String[] lines = out.toString().split("\\R");

        assertEquals("Company,Job Role,Selected Job Role,Selected Course,Student Course", lines[0]);
        assertEquals("Northwind,\"Software Engineer, Gen AI\",Gen AI,B.Tech,Computer Science", lines[1]);
    }

    @Test
    void applicationsCsvKeepsConfiguredRoleListAndSelectedRoleSeparate() throws IOException {
        ApplicationDetail application = new ApplicationDetail();
        application.setRole("Backend Developer, Gen AI");
        application.setSelectedRole("Gen AI");

        StringWriter out = new StringWriter();
        csvExportService.writeApplications(out, List.of(application),
                List.of("Job Role", "Selected Job Role"));
        String[] lines = out.toString().split("\\R");

        assertEquals("Job Role,Selected Job Role", lines[0]);
        assertEquals("\"Backend Developer, Gen AI\",Gen AI", lines[1]);
    }

    @Test
    void applicationsCsvFallsBackToPlacementRoleForLegacyApplications() throws IOException {
        ApplicationDetail application = new ApplicationDetail();
        application.setRole("Backend Developer");

        StringWriter out = new StringWriter();
        csvExportService.writeApplications(out, List.of(application), List.of("Selected Job Role"));
        String[] lines = out.toString().split("\\R");

        assertEquals("Selected Job Role", lines[0]);
        assertEquals("Backend Developer", lines[1]);
    }

    @Test
    void resolveApplicationColumnsRejectsUnknownAndDuplicates() {
        assertEquals(CsvExportService.APPLICATION_DEFAULT_COLUMNS,
                CsvExportService.resolveApplicationColumns(null));
        assertEquals(CsvExportService.APPLICATION_DEFAULT_COLUMNS,
                CsvExportService.resolveApplicationColumns(List.of()));
        assertEquals(CsvExportService.APPLICATION_DEFAULT_COLUMNS,
                CsvExportService.resolveApplicationColumns(List.of("  ", "Not A Column")));
        assertEquals(List.of("Company", "Email", "Application ID"),
                CsvExportService.resolveApplicationColumns(
                        List.of("Company", "Email", "Company", "Application ID", "Nope")));
        assertFalse(CsvExportService.APPLICATION_COLUMNS.contains("Department"));
    }

    @Test
    void everyAdvertisedFieldIsResolvable() {
        for (String field : CsvExportService.APPLICATION_COLUMNS) {
            assertEquals(List.of(field), CsvExportService.resolveApplicationColumns(List.of(field)),
                    "field should resolve to itself: " + field);
        }
        assertEquals(CsvExportService.APPLICATION_COLUMNS.size(),
                CsvExportService.APPLICATION_FIELD_GROUPS.values().stream()
                        .flatMap(List::stream).distinct().count());
    }

    @Test
    void csvEscapesQuotesCommasAndNewlinesAndNulls() {
        String escaped = invokeEscape("He said \"hi\", then\nleft");
        assertEquals("\"He said \"\"hi\"\", then\nleft\"", escaped);
        assertEquals("", invokeEscape(null));
        assertEquals("", invokeEscape(""));
        assertFalse(invokeEscape("plain").contains("\""));
    }

    @Test
    void nullAndEmptyFieldsBecomeEmptyCells() throws IOException {
        ApplicationDetail application = new ApplicationDetail();
        application.setApplicationId("app2");

        StringWriter out = new StringWriter();
        csvExportService.writeApplications(out, List.of(application));
        String[] lines = out.toString().split("\n");

        long emptyTrailers = lines[1].chars().filter(ch -> ch == ',').count();
        assertEquals(18, emptyTrailers);
        assertTrue(lines[1].startsWith("app2,,,,,,,"));
    }

    @Test
    void placementsCsvIncludesRoundsAndDefaults() throws IOException {
        Placement placement = new Placement();
        placement.setId("job1");
        placement.setCompany("Tech Corp");
        placement.setJobType("Developer");
        placement.setRounds(List.of(new PlacementRound(1, "Online Assessment")));

        StringWriter out = new StringWriter();
        csvExportService.writePlacements(out, List.of(placement));
        String csv = out.toString();

        assertTrue(csv.startsWith("Job ID,Company,Role"));
        assertTrue(csv.contains("Tech Corp"));
        assertTrue(csv.contains("1: Online Assessment"));
    }

    private String invokeEscape(String value) {
        return org.springframework.test.util.ReflectionTestUtils.invokeMethod(csvExportService,
                "escape", value);
    }
}
