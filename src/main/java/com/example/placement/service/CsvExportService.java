package com.example.placement.service;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;

import com.example.placement.dto.ApplicationDetail;
import com.example.placement.dto.StudentDetail;
import com.example.placement.entity.Placement;
import com.example.placement.entity.PlacementRound;

@Service
public class CsvExportService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public static final List<String> STUDENT_COLUMNS = List.of(
            "Name", "Email", "Phone", "Enrollment", "Branch", "Section", "Batch", "CGPA",
            "Course", "10th %", "12th %", "Graduation %", "Student Status", "Applications",
            "Applied Companies", "Applied Roles", "Selected Courses", "Application Status", "Current Round",
            "College");

    /** Application export columns enabled by default. */
    public static final List<String> APPLICATION_DEFAULT_COLUMNS = List.of(
            "Application ID", "Student ID", "Enrollment No", "Student Name", "Email", "Phone",
            "Student Course", "Student Batch", "Branch", "CGPA",
            "Company", "Job Role", "Salary / CTC",
            "Application Status", "Current Round", "Current Round Score", "Admin Remark",
            "Applied On", "Last Updated");

    public static final List<String> APPLICATION_OPTIONAL_COLUMNS = List.of(
            "Selected Job Role", "Selected Course", "Section", "College",
            "10th %", "12th %", "Graduation %", "DOB", "Gender", "Address", "City", "State",
            "Student Status", "Account Role",
            "Job ID", "Skills", "Eligibility", "Location", "Program Fee", "Duration",
            "Job Batch", "Eligible Branch", "Openings", "Application Deadline", "Application Link",
            "Job Description", "Total Rounds", "Round History", "Resume URL");

    public static final Map<String, List<String>> APPLICATION_FIELD_GROUPS = new LinkedHashMap<>();

    static {
        APPLICATION_FIELD_GROUPS.put("Application Information", List.of(
                "Application ID", "Application Status", "Current Round", "Current Round Score",
                "Applied On", "Last Updated", "Admin Remark"));
        APPLICATION_FIELD_GROUPS.put("Student Information", List.of(
                "Student ID", "Enrollment No", "Student Name", "Email", "Phone", "Student Course",
                "Selected Course", "Selected Job Role", "Student Batch", "Branch", "Section",
                "College", "CGPA", "10th %", "12th %", "Graduation %", "DOB", "Gender",
                "Address", "City", "State", "Student Status", "Account Role"));
        APPLICATION_FIELD_GROUPS.put("Placement Information", List.of(
                "Job ID", "Company", "Job Role", "Salary / CTC", "Skills", "Eligibility", "Location",
                "Program Fee", "Duration", "Job Batch", "Eligible Branch", "Openings",
                "Application Deadline", "Application Link", "Job Description", "Total Rounds",
                "Round History", "Resume URL"));
    }

    public static final List<String> APPLICATION_COLUMNS = Stream
            .concat(APPLICATION_DEFAULT_COLUMNS.stream(), APPLICATION_OPTIONAL_COLUMNS.stream())
            .distinct()
            .toList();

    /**
     * Resolves a requested column list against the known catalogue, preserving the caller's order
     * and dropping unknown/duplicate/blank entries. Falls back to the defaults when nothing valid remains.
     */
    public static List<String> resolveApplicationColumns(List<String> requested) {
        if (requested == null || requested.isEmpty()) {
            return APPLICATION_DEFAULT_COLUMNS;
        }
        Set<String> allowed = new LinkedHashSet<>(APPLICATION_COLUMNS);
        List<String> resolved = new ArrayList<>();
        for (String column : requested) {
            if (column == null) {
                continue;
            }
            String trimmed = column.trim();
            if (allowed.contains(trimmed) && !resolved.contains(trimmed)) {
                resolved.add(trimmed);
            }
        }
        return resolved.isEmpty() ? APPLICATION_DEFAULT_COLUMNS : resolved;
    }

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
                case "Applied Companies": return applicationValues(student, ApplicationDetail::getCompany);
                case "Applied Roles": return applicationValues(student, application -> firstNonBlank(
                    application.getRole(), application.getJobType()));
                case "Selected Courses": return applicationValues(student, ApplicationDetail::getSelectedCourse);
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

    private String applicationValues(StudentDetail student, Function<ApplicationDetail, String> field) {
        if (student.getApplications() == null || student.getApplications().isEmpty()) {
            return "";
        }
        return student.getApplications().stream()
                .map(field)
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.joining("; "));
    }

    private String firstNonBlank(String first, String second) {
        return first == null || first.isBlank() ? text(second) : first;
    }

    public void writeApplications(Writer destination, List<ApplicationDetail> applications) throws IOException {
        writeApplications(destination, applications, null);
    }

    public void writeApplications(Writer destination, List<ApplicationDetail> applications,
                                  List<String> columns) throws IOException {
        BufferedWriter out = new BufferedWriter(destination);
        List<String> cols = resolveApplicationColumns(columns);
        writeRow(out, cols.toArray(new String[0]));
        for (ApplicationDetail application : applications) {
            List<String> values = new ArrayList<>();
            for (String column : cols) {
                values.add(applicationField(application, column));
            }
            writeRow(out, values.toArray(new String[0]));
        }
        out.flush();
    }

    private String applicationField(ApplicationDetail application, String column) {
        if (application == null || column == null) return "";
        switch (column.trim()) {
            case "Application ID": return text(application.getApplicationId());
            case "Student ID": return text(application.getStudentId());
            case "Enrollment No": return text(application.getEnrollmentno());
            case "Student Name": return text(application.getStudentName());
            case "Email": return text(application.getStudentEmail());
            case "Phone": return text(application.getPhoneNo());
            case "Student Course": return text(application.getStudentCourse());
            case "Selected Course": return text(application.getSelectedCourse());
            case "Selected Job Role": return text(application.getEffectiveRole());
            case "Student Batch": return text(application.getStudentBatch());
            case "Branch": return text(application.getBranch());
            case "Section": return text(application.getSection());
            case "College": return text(application.getCollege());
            case "CGPA": return decimal(application.getCgpa());
            case "10th %": return decimal(application.getTenthPercentage());
            case "12th %": return decimal(application.getTwelfthPercentage());
            case "Graduation %": return decimal(application.getGraduationPercentage());
            case "DOB": return text(application.getDob());
            case "Gender": return text(application.getGender());
            case "Address": return text(application.getAddress());
            case "City": return text(application.getCity());
            case "State": return text(application.getState());
            case "Student Status": return text(application.getStudentStatus());
            case "Account Role": return text(application.getStudentRole());
            case "Job ID": return text(application.getJobId());
            case "Company": return text(application.getCompany());
            case "Job Role": return text(application.getRole());
            case "Salary / CTC": return text(application.getSalary());
            case "Skills": return text(application.getSkills());
            case "Eligibility": return text(application.getEligibility());
            case "Location": return text(application.getLocation());
            case "Program Fee": return text(application.getProgramFee());
            case "Duration": return text(application.getDuration());
            case "Job Batch": return text(application.getEligibleBatch());
            case "Eligible Branch": return text(application.getEligibleBranch());
            case "Openings": return text(application.getOpenings());
            case "Application Deadline": return text(application.getApplicationDeadline());
            case "Application Link": return text(application.getApplicationLink());
            case "Job Description": return text(application.getJobDescription());
            case "Application Status": return text(application.getStatus());
            case "Current Round": return number(application.getCurrentRound());
            case "Total Rounds": return number(application.getTotalRounds());
            case "Round History": return roundHistory(application);
            case "Current Round Score": return currentRoundScore(application);
            case "Admin Remark": return text(application.getAdminRemark());
            case "Applied On": return dateTime(application.getApplicationDate());
            case "Last Updated": return dateTime(application.getLastUpdated());
            case "Resume URL": return text(application.getResumeUrl());
            default: return "";
        }
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

    private String decimal(Double value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String dateTime(java.time.LocalDateTime value) {
        return value == null ? "" : DATE_FORMAT.format(value);
    }
}
