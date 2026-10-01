package com.example.placement.service;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import com.example.placement.dto.StudentDetail;
import com.example.placement.dto.StudentListResponse;
import com.example.placement.entity.Application;
import com.example.placement.entity.ApplicationRound;
import com.example.placement.entity.Placement;
import com.example.placement.entity.UsersEntries;
import com.example.placement.repository.ApplicationRepository;
import com.example.placement.repository.PlacementRepository;
import com.example.placement.repository.UsersRepository;

class StudentServiceTest {

    private UsersRepository usersRepository;
    private ApplicationRepository applicationRepository;
    private PlacementRepository placementRepository;
    private ApplicationService applicationService;
    private MongoTemplate mongoTemplate;
    private PasswordEncoder passwordEncoder;
    private StudentService studentService;

    @BeforeEach
    void setUp() {
        usersRepository = mock(UsersRepository.class);
        applicationRepository = mock(ApplicationRepository.class);
        placementRepository = mock(PlacementRepository.class);
        applicationService = mock(ApplicationService.class);
        mongoTemplate = mock(MongoTemplate.class);
        passwordEncoder = mock(PasswordEncoder.class);
        studentService = new StudentService(usersRepository, applicationRepository, placementRepository,
            applicationService, mongoTemplate, passwordEncoder);
    }

    private UsersEntries student(String id, String enrollment, String name, String email, String branch,
                                 String batch, String status) {
        UsersEntries user = new UsersEntries(enrollment, "9000000000");
        user.setId(id);
        user.setName(name);
        user.setEmail(email);
        user.setBranch(branch);
        user.setBatch(batch);
        user.setStudentStatus(status);
        return user;
    }

    @Test
    void listStudentsReturnsPaginatedResults() {
        UsersEntries s1 = student("u1", "2023CS101", "Vikram", "v@mail.com", "CSE", "2023-27", "Active");
        UsersEntries s2 = student("u2", "2023CS102", "Rahul", "r@mail.com", "CSE", "2023-27", "Placed");
        UsersEntries s3 = student("u3", "2023IT103", "Ankit", "a@mail.com", "IT", "2023-27", "Active");
        UsersEntries admin = student("u9", "ADMIN1", "Admin", "admin@gmail.com", null, null, null);
        admin.setRole("admin");

        when(usersRepository.findAll()).thenReturn(List.of(admin, s1, s2, s3));
        when(applicationRepository.findAll()).thenReturn(List.of());

        StudentListResponse response = studentService.listStudents(null, null, null, null, null,
                null, null, null, null, null, null, 1, 25);

        assertThat(response.getTotal()).isEqualTo(3);
        assertThat(response.getStudents()).hasSize(3);
        assertThat(response.getTotalPages()).isEqualTo(1);
    }

    @Test
    void listStudentsExcludesAdmins() {
        UsersEntries admin = student("u9", "ADMIN1", "Admin", "admin@gmail.com", null, null, null);
        admin.setRole("admin");
        when(usersRepository.findAll()).thenReturn(List.of(admin));
        when(applicationRepository.findAll()).thenReturn(List.of());

        StudentListResponse response = studentService.listStudents(null, null, null, null, null,
                null, null, null, null, null, null, 1, 25);

        assertThat(response.getTotal()).isZero();
    }

    @Test
    void searchByEnrollmentNumberWorks() {
        UsersEntries s1 = student("u1", "2023CS101", "Vikram", "v@mail.com", "CSE", "2023-27", "Active");
        UsersEntries s2 = student("u2", "2023CS102", "Rahul", "r@mail.com", "CSE", "2023-27", "Placed");
        when(usersRepository.findAll()).thenReturn(List.of(s1, s2));
        when(applicationRepository.findAll()).thenReturn(List.of());

        StudentListResponse response = studentService.listStudents("2023CS102", null, null, null, null,
                null, null, null, null, null, null, 1, 25);

        assertThat(response.getTotal()).isEqualTo(1);
        assertThat(response.getStudents().get(0).getEnrollmentno()).isEqualTo("2023CS102");
    }

    @Test
    void searchByNameAndEmailAndPhoneWorks() {
        UsersEntries s1 = student("u1", "2023CS101", "Vikram", "vikram@mail.com", "CSE", "2023-27", "Active");
        UsersEntries s2 = student("u2", "2023CS102", "Rahul", "rahul@mail.com", "CSE", "2023-27", "Active");
        when(usersRepository.findAll()).thenReturn(List.of(s1, s2));
        when(applicationRepository.findAll()).thenReturn(List.of());

        assertThat(studentService.listStudents("vikram", null, null, null, null, null, null, null,
                null, null, null, 1, 25).getTotal()).isEqualTo(1);
        assertThat(studentService.listStudents("rahul@mail", null, null, null, null, null, null, null,
                null, null, null, 1, 25).getTotal()).isEqualTo(1);
        assertThat(studentService.listStudents("9000000000", null, null, null, null, null, null, null,
                null, null, null, 1, 25).getTotal()).isEqualTo(2);
    }

    @Test
    void batchAndBranchFiltersWork() {
        UsersEntries cse = student("u1", "2023CS101", "Vikram", "v@mail.com", "CSE", "2023-27", "Active");
        UsersEntries it = student("u2", "2023IT102", "Ankit", "a@mail.com", "IT", "2023-27", "Active");
        when(usersRepository.findAll()).thenReturn(List.of(cse, it));
        when(applicationRepository.findAll()).thenReturn(List.of());

        StudentListResponse byBranch = studentService.listStudents(null, null, List.of("IT"), null, null,
                null, null, null, null, null, null, 1, 25);
        assertThat(byBranch.getTotal()).isEqualTo(1);
        assertThat(byBranch.getStudents().get(0).getBranch()).isEqualTo("IT");

        StudentListResponse byBatch = studentService.listStudents(null, List.of("2023-27"), null, null, null,
                null, null, null, null, null, null, 1, 25);
        assertThat(byBatch.getTotal()).isEqualTo(2);
    }

    @Test
    void studentStatusFilterWorks() {
        UsersEntries active = student("u1", "2023CS101", "Vikram", "v@mail.com", "CSE", "2023-27", "Active");
        UsersEntries placed = student("u2", "2023CS102", "Ankit", "a@mail.com", "CSE", "2023-27", "Placed");
        when(usersRepository.findAll()).thenReturn(List.of(active, placed));
        when(applicationRepository.findAll()).thenReturn(List.of());

        StudentListResponse placedOnly = studentService.listStudents(null, null, null, null, "Placed",
                null, null, null, null, null, null, 1, 25);
        assertThat(placedOnly.getTotal()).isEqualTo(1);
        assertThat(placedOnly.getStudents().get(0).getStudentStatus()).isEqualTo("Placed");
    }

    @Test
    void paginationBoundsAreRespected() {
        java.util.List<UsersEntries> many = new java.util.ArrayList<>();
        for (int i = 0; i < 30; i++) {
            many.add(student("u" + i, "EN" + i, "S" + i, "s" + i + "@mail.com", "CSE", "2023", "Active"));
        }
        when(usersRepository.findAll()).thenReturn(many);
        when(applicationRepository.findAll()).thenReturn(List.of());

        StudentListResponse page1 = studentService.listStudents(null, null, null, null, null, null,
                null, null, null, null, null, 1, 25);
        assertThat(page1.getStudents()).hasSize(25);
        assertThat(page1.getTotalPages()).isEqualTo(2);

        StudentListResponse page2 = studentService.listStudents(null, null, null, null, null, null,
                null, null, null, null, null, 2, 25);
        assertThat(page2.getStudents()).hasSize(5);
    }

    @Test
    void filterOptionsComeFromDatabase() {
        UsersEntries s1 = student("u1", "E1", "S1", "s1@mail.com", "CSE", "2023-27", "Active");
        UsersEntries s2 = student("u2", "E2", "S2", "s2@mail.com", "IT", "2024-28", "Active");
        Placement job = new Placement();
        job.setId("j1");
        job.setCompany("TechNova");
        job.setJobType("SDE Intern");
        when(usersRepository.findAll()).thenReturn(List.of(s1, s2));
        when(placementRepository.findAll()).thenReturn(List.of(job));

        var options = studentService.filterOptions();
        assertThat((List<String>) options.get("branches")).containsExactlyInAnyOrder("CSE", "IT");
        assertThat((List<String>) options.get("batches")).containsExactlyInAnyOrder("2023-27", "2024-28");
        assertThat((java.util.List<String>) options.get("studentStatuses")).contains("Active", "Placed");
        assertThat((java.util.List<String>) options.get("companies")).containsExactly("TechNova");
    }

    @Test
    void studentDetailReturnsApplications() {
        UsersEntries s1 = student("u1", "2023CS101", "Vikram", "v@mail.com", "CSE", "2023-27", "Active");
        Placement job = new Placement();
        job.setId("j1");
        job.setCompany("TechNova");
        job.setJobType("SDE Intern");
        Application app = new Application("u1", "j1");
        app.setId("a1");
        app.getRounds().add(new ApplicationRound(1));

        when(usersRepository.findById("u1")).thenReturn(java.util.Optional.of(s1));
        when(applicationRepository.findByStudentId("u1")).thenReturn(List.of(app));
        when(placementRepository.findAllById(any())).thenReturn(List.of(job));

        StudentDetail detail = studentService.getStudentDetail("u1");
        assertThat(detail.getName()).isEqualTo("Vikram");
        assertThat(detail.getApplicationCount()).isEqualTo(1);
        assertThat(detail.getApplications()).hasSize(1);
        assertThat(detail.getApplications().get(0).getCompany()).isEqualTo("TechNova");
        assertThat(detail.getApplications().get(0).getStatus()).isEqualTo("IN_PROGRESS");
    }

    @Test
    void studentDetailResolvesObjectIdHexIdWhenStoredIdIsObjectId() {
        UsersEntries s1 = student("6a74a2b5613fb5e507f4547f", "2023CS101", "Vikram", "v@mail.com", "CSE", "2023-27", "Active");
        when(usersRepository.findById("6a74a2b5613fb5e507f4547f")).thenReturn(java.util.Optional.empty());
        when(mongoTemplate.findOne(any(org.springframework.data.mongodb.core.query.Query.class),
                eq(UsersEntries.class))).thenReturn(s1);
        when(applicationRepository.findByStudentId("6a74a2b5613fb5e507f4547f")).thenReturn(List.of());
        when(placementRepository.findAllById(any())).thenReturn(List.of());

        StudentDetail detail = studentService.getStudentDetail("6a74a2b5613fb5e507f4547f");

        assertThat(detail.getName()).isEqualTo("Vikram");
        assertThat(detail.getEnrollmentno()).isEqualTo("2023CS101");
        verify(mongoTemplate).findOne(any(org.springframework.data.mongodb.core.query.Query.class),
                eq(UsersEntries.class));
    }

    @Test
    void studentDetailDoesNotFallbackForNonObjectIdId() {
        when(usersRepository.findById("u1")).thenReturn(java.util.Optional.empty());
        assertThatThrownBy(() -> studentService.getStudentDetail("u1"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
        verify(mongoTemplate, never()).findOne(any(), any());
    }

    @Test
    void studentDetailMissingStudentThrows404() {
        when(usersRepository.findById("missing")).thenReturn(java.util.Optional.empty());
        assertThatThrownBy(() -> studentService.getStudentDetail("missing"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
    }

    @Test
    void studentDetailRejectsAdmin() {
        UsersEntries admin = student("u9", "ADMIN1", "Admin", "admin@gmail.com", null, null, null);
        admin.setRole("admin");
        when(usersRepository.findById("u9")).thenReturn(java.util.Optional.of(admin));
        assertThatThrownBy(() -> studentService.getStudentDetail("u9"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
    }

    @Test
    void bulkStatusUpdatesOnlyStudents() {
        UsersEntries s1 = student("u1", "E1", "S1", "s1@mail.com", "CSE", "B", "Active");
        when(usersRepository.findById("u1")).thenReturn(java.util.Optional.of(s1));
        when(usersRepository.findById("u2")).thenReturn(java.util.Optional.empty());

        var result = studentService.bulkUpdateStatus(List.of("u1", "u2"), "Placed");
        assertThat(result.get("updated")).isEqualTo(1);
        assertThat(result.get("failed")).isEqualTo(1);
        assertThat(s1.getStudentStatus()).isEqualTo("Placed");
    }

    @Test
    void addStudentRejectsDuplicateEnrollment() {
        when(usersRepository.findByEnrollmentno("E1")).thenReturn(student("u1", "E1", "S1", "s1@mail.com", null, null, null));
        UsersEntries newStudent = student(null, "E1", "S2", "s2@mail.com", null, null, null);
        assertThatThrownBy(() -> studentService.addStudent(newStudent))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void addStudentSaves() {
        when(usersRepository.findByEnrollmentno("E1")).thenReturn(null);
        when(usersRepository.findByEmailIgnoreCase("s2@mail.com")).thenReturn(null);
        UsersEntries newStudent = student(null, "E1", "S2", "s2@mail.com", "CSE", "2023", null);
        when(usersRepository.save(any())).thenAnswer(invocation -> {
            UsersEntries saved = invocation.getArgument(0);
            saved.setId("new");
            return saved;
        });
        UsersEntries saved = studentService.addStudent(newStudent);
        assertThat(saved.getId()).isEqualTo("new");
        assertThat(saved.getRole()).isEqualTo("Student");
        assertThat(saved.getStudentStatus()).isEqualTo("Active");
    }

    @Test
    void addStudentHashesPasswordAndDoesNotPersistPlainText() {
        when(usersRepository.findByEnrollmentno("E1")).thenReturn(null);
        when(usersRepository.findByEmailIgnoreCase("s2@mail.com")).thenReturn(null);
        when(passwordEncoder.encode("Pass@123")).thenReturn("hashed-password");
        when(usersRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        UsersEntries newStudent = student(null, "E1", "S2", "s2@mail.com", null, null, null);
        newStudent.setPassword("Pass@123");

        UsersEntries saved = studentService.addStudent(newStudent);

        assertThat(saved.getPasswordHash()).isEqualTo("hashed-password");
        assertThat(saved.getPassword()).isNull();
    }

    @Test
    void addStudentRejectsDuplicateEmail() {
        when(usersRepository.findByEnrollmentno("E1")).thenReturn(null);
        when(usersRepository.findByEmailIgnoreCase("s2@mail.com"))
                .thenReturn(student("u1", "OTHER", "S1", "s2@mail.com", null, null, null));
        UsersEntries newStudent = student(null, "E1", "S2", "s2@mail.com", null, null, null);

        assertThatThrownBy(() -> studentService.addStudent(newStudent))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("email already exists");
    }

    @Test
    void addStudentRejectsInvalidPercentageValues() {
        when(usersRepository.findByEnrollmentno("E1")).thenReturn(null);
        when(usersRepository.findByEmailIgnoreCase("s2@mail.com")).thenReturn(null);

        UsersEntries invalidTenth = student(null, "E1", "S2", "s2@mail.com", null, null, null);
        invalidTenth.setTenthPercentage(101.0);
        assertThatThrownBy(() -> studentService.addStudent(invalidTenth))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("10th Percentage");

        UsersEntries invalidTwelfth = student(null, "E2", "S3", "s3@mail.com", null, null, null);
        invalidTwelfth.setTwelfthPercentage(-1.0);
        assertThatThrownBy(() -> studentService.addStudent(invalidTwelfth))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("12th Percentage");
    }

    private UsersEntries coordinator(String id, String enrollment, String name, String email) {
        UsersEntries user = new UsersEntries(enrollment, "9999999999");
        user.setId(id);
        user.setName(name);
        user.setEmail(email);
        user.setRole("Coordinator");
        return user;
    }

    @Test
    void listStudentsExcludesCoordinators() {
        UsersEntries coordinator = coordinator("c1", "COORD1", "Ram", "ram@mail.com");
        UsersEntries student1 = student("u1", "2023CS101", "Vikram", "v@mail.com", "CSE", "2023-27", "Active");
        when(usersRepository.findAll()).thenReturn(List.of(coordinator, student1));
        when(applicationRepository.findAll()).thenReturn(List.of());

        StudentListResponse response = studentService.listStudents(null, null, null, null, null,
                null, null, null, null, null, null, 1, 25);

        assertThat(response.getTotal()).isEqualTo(1);
        assertThat(response.getStudents().get(0).getEnrollmentno()).isEqualTo("2023CS101");
    }

    @Test
    void sectionFilterWorks() {
        UsersEntries sectionA = student("u1", "2023CS101", "Vikram", "v@mail.com", "CSE", "2023-27", "Active");
        sectionA.setSection("A");
        UsersEntries sectionB = student("u2", "2023CS102", "Rahul", "r@mail.com", "CSE", "2023-27", "Active");
        sectionB.setSection("B");
        when(usersRepository.findAll()).thenReturn(List.of(sectionA, sectionB));
        when(applicationRepository.findAll()).thenReturn(List.of());

        StudentListResponse response = studentService.listStudents(null, null, null, List.of("B"), null,
                null, null, null, null, null, null, 1, 25);

        assertThat(response.getTotal()).isEqualTo(1);
        assertThat(response.getStudents().get(0).getSection()).isEqualTo("B");
    }

    @Test
    void filterOptionsIncludeSections() {
        UsersEntries s1 = student("u1", "E1", "S1", "s1@mail.com", "CSE", "2023-27", "Active");
        s1.setSection("A");
        UsersEntries s2 = student("u2", "E2", "S2", "s2@mail.com", "IT", "2024-28", "Active");
        s2.setSection("B");
        when(usersRepository.findAll()).thenReturn(List.of(s1, s2));
        when(placementRepository.findAll()).thenReturn(List.of());

        var options = studentService.filterOptions();
        assertThat((List<String>) options.get("sections")).containsExactlyInAnyOrder("A", "B");
    }

    @Test
    void listStudentsCapsPageSizeAtOneHundred() {
        List<UsersEntries> many = new java.util.ArrayList<>();
        for (int i = 0; i < 150; i++) {
            many.add(student("u" + i, "E" + i, "S" + i, "s" + i + "@mail.com", "CSE", "2023-27", "Active"));
        }
        when(usersRepository.findAll()).thenReturn(many);
        when(applicationRepository.findAll()).thenReturn(List.of());

        StudentListResponse response = studentService.listStudents(null, null, null, null, null,
                null, null, null, null, null, null, 1, Integer.MAX_VALUE);

        assertThat(response.getPageSize()).isEqualTo(100);
        assertThat(response.getStudents()).hasSize(100);
        assertThat(response.getTotal()).isEqualTo(150);
    }

    @Test
    void listStudentsForReportIsNotTruncated() {
        List<UsersEntries> many = new java.util.ArrayList<>();
        for (int i = 0; i < 150; i++) {
            many.add(student("u" + i, "E" + i, "S" + i, "s" + i + "@mail.com", "CSE", "2023-27", "Active"));
        }
        when(usersRepository.findAll()).thenReturn(many);
        when(applicationRepository.findAll()).thenReturn(List.of());
        when(placementRepository.findAll()).thenReturn(List.of());

        StudentListResponse response = studentService.listStudentsForReport(null, null, null, null, null,
                null, null, null, null, null, null, 1, Integer.MAX_VALUE);

        assertThat(response.getStudents()).hasSize(150);
    }

    @Test
    void bulkUpdateStatusRejectsUnknownStatus() {
        assertThatThrownBy(() -> studentService.bulkUpdateStatus(List.of("u1"), "Hacked"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Status must be one of");
    }

    @Test
    void bulkUpdateStatusRejectsNullStatus() {
        assertThatThrownBy(() -> studentService.bulkUpdateStatus(List.of("u1"), null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("New status is required");
    }

    @Test
    void bulkMoveRoundExpandsStudentIdsIntoApplicationIds() {
        UsersEntries s1 = student("u1", "E1", "S1", "s1@mail.com", "CSE", "2023-27", "Active");
        Application first = new Application("u1", "job1");
        first.setId("app1");
        Application second = new Application("u1", "job2");
        second.setId("app2");
        when(applicationRepository.findByStudentId("u1")).thenReturn(List.of(first, second));
        Map<String, Object> summary = new java.util.HashMap<>();
        when(applicationService.bulkSetRound(anyList(), any(), anyString()))
                .thenAnswer(invocation -> summary);

        studentService.bulkMoveRound(List.of(), List.of("u1"), 2, "moved");

        verify(applicationService).bulkSetRound(argThat(ids -> ids.size() == 2), eq(2), eq("moved"));
    }

}
