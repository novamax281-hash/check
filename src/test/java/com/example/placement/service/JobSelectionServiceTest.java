package com.example.placement.service;

import com.example.placement.entity.JobStudentSelection;
import com.example.placement.entity.Placement;
import com.example.placement.entity.UsersEntries;
import com.example.placement.repository.JobStudentSelectionRepository;
import com.example.placement.repository.PlacementRepository;
import com.example.placement.repository.UsersRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JobSelectionServiceTest {

    private JobStudentSelectionRepository selectionRepository;
    private PlacementRepository placementRepository;
    private UsersRepository usersRepository;
    private StudentService studentService;
    private JobSelectionService service;

    @BeforeEach
    void setUp() {
        selectionRepository = mock(JobStudentSelectionRepository.class);
        placementRepository = mock(PlacementRepository.class);
        usersRepository = mock(UsersRepository.class);
        studentService = mock(StudentService.class);
        service = new JobSelectionService(selectionRepository, placementRepository, usersRepository, studentService);
    }

    private Placement job(String id, String... coordinatorIds) {
        Placement placement = new Placement();
        placement.setId(id);
        placement.setCompany("Acme");
        placement.setJobType("Engineer");
        placement.setStatus("Open");
        placement.setCoordinatorIds(List.of(coordinatorIds));
        return placement;
    }

    private UsersEntries student(String id) {
        UsersEntries user = new UsersEntries();
        user.setId(id);
        user.setName("Student " + id);
        user.setEnrollmentno("ENR" + id);
        return user;
    }

    @Test
    void getJobsVisibleToStudent_queriesOnlyNonClosedJobs() {
        when(placementRepository.findVisibleJobs()).thenReturn(List.of(job("j1")));

        List<Placement> visible = service.getJobsVisibleToStudent("s1");

        assertEquals(1, visible.size());
        assertEquals("j1", visible.get(0).getId());
        verify(placementRepository).findVisibleJobs();
    }

    @Test
    void getJobsVisibleToStudent_returnsOpenJobsEvenWithoutSelections() {
        when(placementRepository.findVisibleJobs()).thenReturn(List.of(job("j1")));

        List<Placement> visible = service.getJobsVisibleToStudent("s1");

        assertEquals(1, visible.size());
        assertEquals("j1", visible.get(0).getId());
    }

    @Test
    void selectStudentsForJob_skipsUnassignedAndAlreadySelected() {
        when(placementRepository.findById("j1")).thenReturn(Optional.of(job("j1", "c1")));
        when(studentService.assignedStudents("c1")).thenReturn(List.of(student("s1"), student("s2")));
        when(selectionRepository.findByJobIdAndStudentIdIn(anyString(), anyList()))
                .thenReturn(List.of(new JobStudentSelection("j1", "s2", "c1")));
        when(selectionRepository.findByJobId("j1")).thenReturn(List.of(
                new JobStudentSelection("j1", "s2", "c1"),
                new JobStudentSelection("j1", "s1", "c1")));

        Map<String, Object> result = service.selectStudentsForJob("c1", "j1", List.of("s1", "s2", "outsider"));

        assertEquals(1, result.get("selected"));
        assertEquals(2, result.get("skipped"));
        assertEquals(2, result.get("totalSelected"));
        verify(selectionRepository).save(any(JobStudentSelection.class));
    }

    @Test
    void selectStudentsForJob_rejectsJobNotAssignedToCoordinator() {
        when(placementRepository.findById("j1")).thenReturn(Optional.of(job("j1", "other")));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.selectStudentsForJob("c1", "j1", List.of("s1")));

        assertEquals(403, exception.getStatusCode().value());
        verify(selectionRepository, never()).save(any(JobStudentSelection.class));
    }

    @Test
    void getEligibleStudentsForJob_flagsAlreadySelectedStudents() {
        when(placementRepository.findById("j1")).thenReturn(Optional.of(job("j1", "c1")));
        when(selectionRepository.findByJobId("j1")).thenReturn(List.of(new JobStudentSelection("j1", "s2", "c1")));
        when(studentService.assignedStudents("c1")).thenReturn(List.of(student("s1"), student("s2")));

        List<Map<String, Object>> eligible = service.getEligibleStudentsForJob("c1", "j1");

        assertEquals(2, eligible.size());
        assertEquals(false, eligible.get(0).get("selected"));
        assertEquals(true, eligible.get(1).get("selected"));
    }

    @Test
    void removeStudentFromJob_throwsWhenNotSelected() {
        when(placementRepository.findById("j1")).thenReturn(Optional.of(job("j1", "c1")));
        when(selectionRepository.findByJobIdAndStudentId("j1", "s1")).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> service.removeStudentFromJob("c1", "j1", "s1"));
        verify(selectionRepository, never()).deleteByJobIdAndStudentId(anyString(), anyString());
    }

    @Test
    void assignCoordinatorsToJob_keepsOnlyRealCoordinators() {
        Placement placement = job("j1");
        when(placementRepository.findById("j1")).thenReturn(Optional.of(placement));
        UsersEntries coordinator = new UsersEntries();
        coordinator.setId("c1");
        coordinator.setRole("Coordinator");
        UsersEntries studentUser = new UsersEntries();
        studentUser.setId("c2");
        studentUser.setRole("Student");
        when(usersRepository.findById("c1")).thenReturn(Optional.of(coordinator));
        when(usersRepository.findById("c2")).thenReturn(Optional.of(studentUser));
        when(usersRepository.findById("missing")).thenReturn(Optional.empty());
        when(studentService.isCoordinatorRole(coordinator)).thenReturn(true);
        when(studentService.isCoordinatorRole(studentUser)).thenReturn(false);
        when(placementRepository.save(any(Placement.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Placement saved = service.assignCoordinatorsToJob("j1", List.of("c1", "c2", "missing", "c1"));

        ArgumentCaptor<Placement> captor = ArgumentCaptor.forClass(Placement.class);
        verify(placementRepository).save(captor.capture());
        assertEquals(List.of("c1"), captor.getValue().getCoordinatorIds());
        assertEquals(List.of("c1"), saved.getCoordinatorIds());
    }
}
