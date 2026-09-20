package com.example.placement.service;

import com.example.placement.dto.ApplicationDetail;
import com.example.placement.dto.RoundDetail;
import com.example.placement.dto.StudentDetail;
import com.example.placement.entity.Placement;
import com.example.placement.entity.PlacementRound;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
public class CsvExportService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public static final List<String> STUDENT_COLUMNS = List.of(
            "Name", "Email", "Phone", "Enrollment", "Branch", "Section", "Batch", "CGPA",
            "Student Status", "Applications", "Application Status", "Current Round",
            "Address", "DOB", "Course", "College", "10th %", "12th %", "Graduation %",
            "City", "State");

    public String studentsCsv(List<StudentDetail> students, List<String> columns) throws IOException {
        StringWriter buffer = new StringWriter();
        writeStudents(buffer, students, columns);
        return buffer.toString();
    }

    public void writeStudents(Writer destination, List<StudentDetail> students, List<String> columns)
            throws IOException {
        BufferedWriter out = new BufferedWriter(destination);
        List<String> cols = columns == null || columns.isEmpty() ? STUDENT_COLUMNS : columns;
        writeRow(out, cols.toArray(new String[0]));
        for (StudentDetail student : students) {
            List<String> values = new ArrayList<>();
            for (String column : cols) {
                values.add(studentField(student, column));
            }
            writeRow(out, values.toArray(new String[0]));
        }
        out.flush();
    }

    private String studentField(StudentDetail student, String column) {
        if (column == null) return "";
        switch (column.trim()) {
            case "Name": return text(student.getName());
            case "Email": return text(student.getEmail());
            case "Phone": return "\"" + text(student.getPhoneno()) + "\"";
            case "Enrollment": return "\"" + text(student.getEnrollmentno()) + "\"";
            case "Branch": return text(student.getBranch());
            case "Section": return text(student.getSection());
            case "Batch": return text(student.getBatch());
            case "CGPA": return student.getCgpa() == null ? "" : String.valueOf(student.getCgpa());
            case "Student Status": return text(student.getStudentStatus());
            case "Applications": return String.valueOf(student.getApplicationCount());
            case "Application Status": return text(student.getApplicationStatus());
            case "Current Round": return currentRound(student);
            case "Address": return text(student.getAddress());
            case "DOB": return text(student.getDob());
            case "Course": return text(student.getCourse());
            case "College": return text(student.getCollege());
            case "10th %": return student.getTenthPercentage() == null ? "" : String.valueOf(student.getTenthPercentage());
            case "12th %": return student.getTwelfthPercentage() == null ? "" : String.valueOf(student.getTwelfthPercentage());
            case "Graduation %": return student.getGraduationPercentage() == null ? "" : String.valueOf(student.getGraduationPercentage());
            case "City": return text(student.getCity());
            case "State": return text(student.getState());
            default: return "";
        }
    }

    private String currentRound(StudentDetail student) {
        if (student.getApplications() == null || student.getApplications().isEmpty()) {
            return "";
        }
        StringBuilder value = new StringBuilder();
        for (ApplicationDetail app : student.getApplications()) {
            if (value.length() > 0) value.append("; ");
            value.append(app.getCompany()).append(":");
            value.append(app.getCurrentRound() == null ? "1" : app.getCurrentRound());
        }
        return value.toString();
    }

    public void writeApplications(Writer destination, List<ApplicationDetail> applications) throws IOException {
        BufferedWriter out = new BufferedWriter(destination);
        writeRow(out, "Application ID", "Enrollment No", "Student Name", "Email", "Phone",
                "Company", "Role", "Salary", "Skills", "Eligibility", "Location",
                "Status", "Current Round", "Total Rounds", "Round History",
                "Current Round Score", "Admin Remark", "Applied On", "Last Updated");
        for (ApplicationDetail application : applications) {
            writeRow(out,
                    text(application.getApplicationId()),
                    text(application.getEnrollmentno()),
                    text(application.getStudentName()),
                    text(application.getStudentEmail()),
                    text(application.getPhoneNo()),
                    text(application.getCompany()),
                    text(application.getRole()),
                    text(application.getSalary()),
                    text(application.getSkills()),
                    text(application.getEligibility()),
                    text(application.getLocation()),
                    text(application.getStatus()),
                    number(application.getCurrentRound()),
                    number(application.getTotalRounds()),
                    roundHistory(application),
                    currentRoundScore(application),
                    text(application.getAdminRemark()),
                    dateTime(application.getApplicationDate()),
                    dateTime(application.getLastUpdated()));
        }
        out.flush();
    }

    public void writePlacements(Writer destination, List<Placement> placements) throws IOException {
        BufferedWriter out = new BufferedWriter(destination);
        writeRow(out, "Job ID", "Company", "Role", "Stipend", "Program Fee", "Duration",
                "Skills", "Eligibility", "Location", "Openings", "Application Deadline",
                "Application Link", "Status", "Rounds");
        for (Placement placement : placements) {
            writeRow(out,
                    text(placement.getId()),
                    text(placement.getCompany()),
                    text(placement.getJobType()),
                    text(placement.getStipend()),
                    text(placement.getProgramFee()),
                    text(placement.getDuration()),
                    text(placement.getSkills()),
                    text(placement.getEligibility()),
                    text(placement.getLocation()),
                    text(placement.getOpenings()),
                    text(placement.getApplicationDeadline()),
                    text(placement.getApplicationLink()),
                    text(placement.getStatus()),
                    configuredRounds(placement.getRounds()));
        }
        out.flush();
    }

    private String roundHistory(ApplicationDetail application) {
        if (application.getRounds() == null || application.getRounds().isEmpty()) {
            return "";
        }
        StringBuilder history = new StringBuilder();
        for (com.example.placement.dto.RoundDetail round : application.getRounds()) {
            if (history.length() > 0) {
                history.append("; ");
            }
            history.append("Round ").append(round.getRound())
                    .append(": ").append(round.getStatus());
            if (round.getScore() != null) {
                history.append(" (").append(round.getScore()).append(")");
            }
        }
        return history.toString();
    }

    private String currentRoundScore(ApplicationDetail application) {
        if (application.getRounds() == null || application.getCurrentRound() == null) {
            return "";
        }
        return application.getRounds().stream()
                .filter(round -> application.getCurrentRound().equals(round.getRound()))
                .map(round -> round.getScore() == null ? "" : String.valueOf(round.getScore()))
                .findFirst()
                .orElse("");
    }

    private String configuredRounds(List<PlacementRound> rounds) {
        if (rounds == null || rounds.isEmpty()) {
            return "";
        }
        StringBuilder names = new StringBuilder();
        for (PlacementRound round : rounds) {
            if (round == null || round.getRound() == null) {
                continue;
            }
            if (names.length() > 0) {
                names.append("; ");
            }
            names.append(round.getRound());
            if (round.getName() != null && !round.getName().isBlank()) {
                names.append(": ").append(round.getName());
            }
        }
        return names.toString();
    }

    private void writeRow(BufferedWriter out, String... values) throws IOException {
        StringBuilder row = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                row.append(',');
            }
            row.append(escape(values[i]));
        }
        out.write(row.toString());
        out.newLine();
    }

    private String escape(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        String protectedValue = protect(value);
        boolean needsQuotes = protectedValue.indexOf(',') >= 0 || protectedValue.indexOf('"') >= 0
                || protectedValue.indexOf('\n') >= 0 || protectedValue.indexOf('\r') >= 0;
        if (!needsQuotes) {
            return protectedValue;
        }
        return '"' + protectedValue.replace("\"", "\"\"") + '"';
    }

    private String protect(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        char first = value.charAt(0);
        if (first == '=' || first == '+' || first == '-' || first == '@') {
            return "'" + value;
        }
        return value;
    }

    private String text(String value) {
        return value == null ? "" : value;
    }

    private String number(Integer value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String dateTime(java.time.LocalDateTime value) {
        return value == null ? "" : DATE_FORMAT.format(value);
    }
}
