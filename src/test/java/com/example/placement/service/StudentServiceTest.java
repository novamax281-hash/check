package com.example.placement.service;

import com.example.placement.entity.UsersEntries;
import com.example.placement.entity.Placement;
import com.example.placement.entity.Application;
import com.example.placement.entity.ApplicationRound;
import com.example.placement.dto.StudentDetail;
import com.example.placement.dto.StudentListResponse;
import com.example.placement.repository.ApplicationRepository;
import com.example.placement.repository.PlacementRepository;
import com.example.placement.repository.UsersRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class StudentServiceTest {

    private UsersRepository usersRepository;
    private ApplicationRepository applicationRepository;
    private PlacementRepository placementRepository;
    private ApplicationService applicationService;
    private MongoTemplate mongoTemplate;
    private StudentService studentService;

    @BeforeEach
    void setUp() {
        usersRepository = mock(UsersRepository.class);
        applicationRepository = mock(ApplicationRepository.class);
        placementRepository = mock(PlacementRepository.class);
        applicationService = mock(ApplicationService.class);
        mongoTemplate = mock(MongoTemplate.class);
        studentService = new StudentService(usersRepository, applicationRepository, placementRepository,
                applicationService, mongoTemplate);
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
    void assignToCoordinatorMatchesCriteriaAndAvoidsDuplicates() {
        UsersEntries coordinator = coordinator("c1", "COORD1", "Ram", "ram@mail.com");
        UsersEntries cseB = student("u1", "2023CS101", "Vikram", "v@mail.com", "CSE", "2023-27", "Active");
        cseB.setSection("B");
        UsersEntries cseA = student("u2", "2023CS102", "Rahul", "r@mail.com", "CSE", "2023-27", "Active");
        cseA.setSection("A");
        UsersEntries itB = student("u3", "2023IT103", "Ankit", "a@mail.com", "IT", "2023-27", "Active");
        itB.setSection("B");

        when(usersRepository.findById("c1")).thenReturn(java.util.Optional.of(coordinator));
        when(usersRepository.findById("u1")).thenReturn(java.util.Optional.of(cseB));
        when(usersRepository.findAll()).thenReturn(List.of(coordinator, cseB, cseA, itB));
        when(usersRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = studentService.assignToCoordinator("c1", "CSE", null, "2023-27", null);

        assertThat(result.get("assigned")).isEqualTo(2);
        assertThat(cseB.getCoordinatorIds()).containsExactly("c1");
        assertThat(cseA.getCoordinatorIds()).containsExactly("c1");
        assertThat(itB.getCoordinatorIds()).isEmpty();

        var secondRun = studentService.assignToCoordinator("c1", "CSE", null, "2023-27", null);
        assertThat(secondRun.get("assigned")).isEqualTo(0);
        assertThat(secondRun.get("alreadyAssigned")).isEqualTo(2);
    }

    @Test
    void assignToCoordinatorByExplicitStudentsIgnoresCriteria() {
        UsersEntries coordinator = coordinator("c1", "COORD1", "Ram", "ram@mail.com");
        UsersEntries notMatching = student("u5", "2023ME105", "Meera", "meera@mail.com", "ME", "2022-26", "Active");
        when(usersRepository.findById("c1")).thenReturn(java.util.Optional.of(coordinator));
        when(usersRepository.findById("u5")).thenReturn(java.util.Optional.of(notMatching));
        when(usersRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = studentService.assignToCoordinator("c1", "CSE", "X", "9999", List.of("u5"));

        assertThat(result.get("assigned")).isEqualTo(1);
        assertThat(notMatching.getCoordinatorIds()).containsExactly("c1");
    }

    @Test
    void listStudentsForCoordinatorReturnsOnlyAssigned() {
        UsersEntries coordinator = coordinator("c1", "COORD1", "Ram", "ram@mail.com");
        UsersEntries mine = student("u1", "2023CS101", "Vikram", "v@mail.com", "CSE", "2023-27", "Active");
        mine.setSection("B");
        mine.setCoordinatorIds(List.of("c1"));
        UsersEntries other = student("u2", "2023CS102", "Rahul", "r@mail.com", "CSE", "2023-27", "Active");
        other.setSection("B");
        other.setCoordinatorIds(List.of("c2"));

        when(usersRepository.findById("c1")).thenReturn(java.util.Optional.of(coordinator));
        when(usersRepository.findByCoordinatorIdsContaining("c1")).thenReturn(List.of(mine));
        when(applicationRepository.findByStudentIdIn(any())).thenReturn(List.of());

        StudentListResponse response = studentService.listStudentsForCoordinator("c1", null,
                null, List.of("CSE"), null, 1, 25);

        assertThat(response.getTotal()).isEqualTo(1);
        assertThat(response.getStudents().get(0).getEnrollmentno()).isEqualTo("2023CS101");
        assertThat(response.getStats().get("total")).isEqualTo(1L);
    }

    @Test
    void getAssignedStudentDetailRejectsUnassignedStudent() {
        UsersEntries coordinator = coordinator("c1", "COORD1", "Ram", "ram@mail.com");
        UsersEntries other = student("u2", "2023CS102", "Rahul", "r@mail.com", "CSE", "2023-27", "Active");
        other.setCoordinatorIds(List.of("c2"));

        when(usersRepository.findById("c1")).thenReturn(java.util.Optional.of(coordinator));
        when(usersRepository.findById("u2")).thenReturn(java.util.Optional.of(other));
        when(applicationRepository.findByStudentId("u2")).thenReturn(List.of());
        when(placementRepository.findAllById(any())).thenReturn(List.of());

        assertThatThrownBy(() -> studentService.getAssignedStudentDetail("c1", "u2"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
    }

    @Test
    void getAssignedStudentDetailReturnsAssignedStudent() {
        UsersEntries coordinator = coordinator("c1", "COORD1", "Ram", "ram@mail.com");
        UsersEntries mine = student("u1", "2023CS101", "Vikram", "v@mail.com", "CSE", "2023-27", "Active");
        mine.setCoordinatorIds(List.of("c1"));

        when(usersRepository.findById("c1")).thenReturn(java.util.Optional.of(coordinator));
        when(usersRepository.findById("u1")).thenReturn(java.util.Optional.of(mine));
        when(applicationRepository.findByStudentId("u1")).thenReturn(List.of());
        when(placementRepository.findAllById(any())).thenReturn(List.of());

        StudentDetail detail = studentService.getAssignedStudentDetail("c1", "u1");
        assertThat(detail.getName()).isEqualTo("Vikram");
        assertThat(detail.getCoordinatorIds()).containsExactly("c1");
    }

    @Test
    void addCoordinatorSetsRoleAndValidatesDuplicate() {
        when(usersRepository.findByEnrollmentno("COORD1")).thenReturn(coordinator("c1", "COORD1", "Ram", "ram@mail.com"));
        UsersEntries duplicate = coordinator(null, "COORD1", "Ram2", "ram2@mail.com");
        assertThatThrownBy(() -> studentService.addCoordinator(duplicate))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("already exists");

        when(usersRepository.findByEnrollmentno("COORD2")).thenReturn(null);
        when(usersRepository.save(any())).thenAnswer(invocation -> {
            UsersEntries saved = invocation.getArgument(0);
            saved.setId("c2");
            return saved;
        });
        UsersEntries newCoordinator = coordinator(null, "COORD2", "Ram2", "coord@mail.com");
        UsersEntries saved = studentService.addCoordinator(newCoordinator);
        assertThat(saved.getRole()).isEqualTo("Coordinator");
        assertThat(saved.getName()).isEqualTo("Ram2");
    }

    @Test
    void unassignStudentsRemovesCoordinatorOnly() {
        UsersEntries coordinator = coordinator("c1", "COORD1", "Ram", "ram@mail.com");
        UsersEntries mine = student("u1", "2023CS101", "Vikram", "v@mail.com", "CSE", "2023-27", "Active");
        mine.setCoordinatorIds(new java.util.ArrayList<>(List.of("c1", "c2")));
        UsersEntries other = student("u2", "2023CS102", "Rahul", "r@mail.com", "CSE", "2023-27", "Active");
        other.setCoordinatorIds(new java.util.ArrayList<>(List.of("c2")));

        when(usersRepository.findById("c1")).thenReturn(java.util.Optional.of(coordinator));
        when(usersRepository.findById("u1")).thenReturn(java.util.Optional.of(mine));
        when(usersRepository.findById("u2")).thenReturn(java.util.Optional.of(other));
        when(usersRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = studentService.unassignStudents("c1", List.of("u1", "u2"));

        assertThat(result.get("removed")).isEqualTo(1);
        assertThat(mine.getCoordinatorIds()).containsExactly("c2");
        assertThat(other.getCoordinatorIds()).containsExactly("c2");
    }

    @Test
    void removeCoordinatorUnassignsAllAssignedStudents() {
        UsersEntries coordinator = coordinator("c1", "COORD1", "Ram", "ram@mail.com");
        UsersEntries mine = student("u1", "2023CS101", "Vikram", "v@mail.com", "CSE", "2023-27", "Active");
        mine.setCoordinatorIds(new java.util.ArrayList<>(List.of("c1")));

        when(usersRepository.findById("c1")).thenReturn(java.util.Optional.of(coordinator));
        when(usersRepository.findByCoordinatorIdsContaining("c1")).thenReturn(List.of(mine));
        when(usersRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = studentService.removeCoordinator("c1");

        assertThat(mine.getCoordinatorIds()).isEmpty();
        assertThat(result.get("message")).asString().contains("1");
        verify(usersRepository).deleteById("c1");
    }
}