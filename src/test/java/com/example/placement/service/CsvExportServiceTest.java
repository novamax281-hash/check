package com.example.placement.service;

import com.example.placement.dto.ApplicationDetail;
import com.example.placement.dto.RoundDetail;
import com.example.placement.entity.Placement;
import com.example.placement.entity.PlacementRound;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CsvExportServiceTest {

    private final CsvExportService csvExportService = new CsvExportService();

    @Test
    void applicationsCsvContainsHeaderAndFields() throws IOException {
        ApplicationDetail application = new ApplicationDetail();
        application.setApplicationId("app1");
        application.setEnrollmentno("ENR001");
        application.setStudentName("Rahul Sharma");
        application.setStudentEmail("rahul@example.com");
        application.setPhoneNo("9999999999");
        application.setCompany("ABC, Technologies");
        application.setRole("Intern");
        application.setStatus("IN_PROGRESS");
        application.setCurrentRound(2);
        application.setTotalRounds(3);

        RoundDetail round = new RoundDetail();
        round.setRound(1);
        round.setStatus("QUALIFIED");
        round.setScore(85.0);
        application.setRounds(List.of(round));
        application.setApplicationDate(LocalDateTime.of(2026, 8, 20, 10, 30));

        StringWriter out = new StringWriter();
        csvExportService.writeApplications(out, List.of(application));
        String csv = out.toString();
        String[] lines = csv.split("\n", -1);

        assertTrue(lines[0].startsWith("Application ID,Enrollment No,Student Name"));
        assertTrue(csv.contains("ENR001"));
        assertTrue(csv.contains("\"ABC, Technologies\""));
        assertTrue(csv.contains("Round 1: QUALIFIED (85.0)"));
        assertEquals("", lines[lines.length - 1]);
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
