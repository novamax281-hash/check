package com.example.placement.service;

import com.example.placement.entity.JobStudentSelection;
import com.example.placement.entity.Placement;
import com.example.placement.entity.UsersEntries;
import com.example.placement.repository.JobStudentSelectionRepository;
import com.example.placement.repository.PlacementRepository;
import com.example.placement.repository.UsersRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

// Owns two related but distinct ideas:
//   1. A job being assigned to a coordinator by the admin (Placement.coordinatorIds).
//   2. A coordinator selecting specific students of their section as eligible to apply (JobStudentSelection).
// "Selected for a job" means the student may now SEE and APPLY for the job.
// It is NOT an Application — the student still has to apply after being selected.
@Service
public class JobSelectionService {

    private final JobStudentSelectionRepository selectionRepository;
    private final PlacementRepository placementRepository;
    private final UsersRepository usersRepository;
    private final StudentService studentService;

    public JobSelectionService(JobStudentSelectionRepository selectionRepository,
                               PlacementRepository placementRepository,
                               UsersRepository usersRepository,
                               StudentService studentService) {
        this.selectionRepository = selectionRepository;
        this.placementRepository = placementRepository;
        this.usersRepository = usersRepository;
        this.studentService = studentService;
    }

    public Placement assignCoordinatorsToJob(String jobId, List<String> coordinatorIds) {
        Placement job = requireJob(jobId);
        List<String> validIds = new ArrayList<>();
        for (String coordinatorId : coordinatorIds == null ? List.<String>of() : coordinatorIds) {
            if (coordinatorId == null || coordinatorId.isBlank() || validIds.contains(coordinatorId)) {
                continue;
            }
            UsersEntries coordinator = usersRepository.findById(coordinatorId).orElse(null);
            if (coordinator != null && studentService.isCoordinatorRole(coordinator)) {
                validIds.add(coordinatorId);
            }
        }
        job.setCoordinatorIds(validIds);
        return placementRepository.save(job);
    }

    public List<Map<String, Object>> getJobsForCoordinator(String coordinatorId) {
        List<Placement> jobs = placementRepository.findAllForCoordinator(coordinatorId);
        if (jobs.isEmpty()) {
            return List.of();
        }
        Map<String, Long> selectedCounts = selectionRepository.findByJobIdIn(jobs.stream()
                        .map(Placement::getId)
                        .collect(Collectors.toSet())).stream()
                .collect(Collectors.groupingBy(JobStudentSelection::getJobId, Collectors.counting()));
        return jobs.stream().map(job -> {
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("id", job.getId());
            info.put("company", job.getCompany());
            info.put("jobType", job.getJobType());
            info.put("location", job.getLocation());
            info.put("applicationDeadline", job.getApplicationDeadline());
            info.put("status", job.getStatus());
            info.put("selectedCount", selectedCounts.getOrDefault(job.getId(), 0L));
            return info;
        }).collect(Collectors.toList());
    }

    // Students the coordinator is responsible for, each flagged with whether they are already selected for this job.
    public List<Map<String, Object>> getEligibleStudentsForJob(String coordinatorId, String jobId) {
        requireJobAssignedToCoordinator(coordinatorId, jobId);
        Set<String> selectedIds = selectionRepository.findByJobId(jobId).stream()
                .map(JobStudentSelection::getStudentId)
                .collect(Collectors.toSet());
        return studentService.assignedStudents(coordinatorId).stream()
                .map(student -> toStudentMap(student, selectedIds.contains(student.getId())))
                .collect(Collectors.toList());
    }

    public List<Map<String, Object>> getSelectedStudentsForJob(String coordinatorId, String jobId) {
        requireJobAssignedToCoordinator(coordinatorId, jobId);
        List<JobStudentSelection> selections = selectionRepository.findByJobId(jobId);
        Map<String, UsersEntries> studentById = new HashMap<>();
        usersRepository.findAllById(selections.stream()
                        .map(JobStudentSelection::getStudentId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet()))
                .forEach(student -> studentById.put(student.getId(), student));
        return selections.stream()
                .map(selection -> studentById.get(selection.getStudentId()))
                .filter(Objects::nonNull)
                .map(student -> toStudentMap(student, true))
                .collect(Collectors.toList());
    }

    public Map<String, Object> selectStudentsForJob(String coordinatorId, String jobId, List<String> studentIds) {
        requireJobAssignedToCoordinator(coordinatorId, jobId);
        if (studentIds == null || studentIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select at least one student");
        }
        Set<String> assignedStudentIds = studentService.assignedStudents(coordinatorId).stream()
                .map(UsersEntries::getId)
                .collect(Collectors.toSet());
        Set<String> alreadySelectedIds = selectionRepository.findByJobIdAndStudentIdIn(jobId, studentIds).stream()
                .map(JobStudentSelection::getStudentId)
                .collect(Collectors.toSet());

        int selected = 0;
        int skipped = 0;
        for (String studentId : new LinkedHashSet<>(studentIds)) {
            boolean eligible = studentId != null && !studentId.isBlank()
                    && assignedStudentIds.contains(studentId)
                    && !alreadySelectedIds.contains(studentId);
            if (!eligible) {
                skipped++;
                continue;
            }
            selectionRepository.save(new JobStudentSelection(jobId, studentId, coordinatorId));
            selected++;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("selected", selected);
        result.put("skipped", skipped);
        result.put("totalSelected", selectionRepository.findByJobId(jobId).size());
        result.put("message", selected + " student(s) selected for this job");
        return result;
    }

    public Map<String, Object> removeStudentFromJob(String coordinatorId, String jobId, String studentId) {
        requireJobAssignedToCoordinator(coordinatorId, jobId);
        if (selectionRepository.findByJobIdAndStudentId(jobId, studentId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "This student is not selected for the job");
        }
        selectionRepository.deleteByJobIdAndStudentId(jobId, studentId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "Student removed from this job");
        result.put("totalSelected", selectionRepository.findByJobId(jobId).size());
        return result;
    }

    // Every student sees all open jobs created by any coordinator and can apply to any of them.
    public List<Placement> getJobsVisibleToStudent(String studentId) {
        return placementRepository.findVisibleJobs();
    }

    public void deleteSelectionsForJob(String jobId) {
        selectionRepository.deleteByJobId(jobId);
    }

    private Placement requireJob(String jobId) {
        return placementRepository.findById(jobId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found"));
    }

    private void requireJobAssignedToCoordinator(String coordinatorId, String jobId) {
        Placement job = requireJob(jobId);
        if (job.getCoordinatorIds() == null || !job.getCoordinatorIds().contains(coordinatorId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This job is not assigned to you");
        }
    }

    private Map<String, Object> toStudentMap(UsersEntries student, boolean selected) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("studentId", student.getId());
        info.put("name", student.getName());
        info.put("enrollmentno", student.getEnrollmentno());
        info.put("branch", student.getBranch());
        info.put("section", student.getSection());
        info.put("batch", student.getBatch());
        info.put("selected", selected);
        return info;
    }
}
