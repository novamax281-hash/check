package com.example.placement.controller;

import com.example.placement.dto.AssignmentRequest;
import com.example.placement.entity.UsersEntries;
import com.example.placement.service.StudentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin")
public class CoordinatorManagementController {

    private final StudentService studentService;

    public CoordinatorManagementController(StudentService studentService) {
        this.studentService = studentService;
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleStatusException(ResponseStatusException exception) {
        String message = exception.getReason() != null ? exception.getReason() : "Request failed";
        return ResponseEntity.status(exception.getStatusCode()).body(Map.of("message", message));
    }

    @GetMapping("/coordinators")
    public List<Map<String, Object>> coordinators() {
        return studentService.listCoordinators();
    }

    @PostMapping("/coordinators")
    public UsersEntries addCoordinator(@RequestBody UsersEntries coordinator) {
        return studentService.addCoordinator(coordinator);
    }

    @GetMapping("/coordinators/{id}")
    public Map<String, Object> coordinator(@PathVariable String id) {
        return studentService.getCoordinator(id);
    }

    @PutMapping("/coordinators/{id}")
    public Map<String, Object> updateCoordinator(@PathVariable String id, @RequestBody UsersEntries update) {
        return studentService.updateCoordinator(id, update);
    }

    @GetMapping("/coordinators/{id}/progress")
    public Map<String, Object> coordinatorProgress(@PathVariable String id) {
        return studentService.coordinatorProgress(id);
    }

    @DeleteMapping("/coordinators/{id}")
    public Map<String, Object> removeCoordinator(@PathVariable String id) {
        return studentService.removeCoordinator(id);
    }

    @GetMapping("/assignment-options")
    public Map<String, Object> assignmentOptions() {
        return studentService.assignmentOptions();
    }

    @GetMapping("/assignments")
    public List<Map<String, Object>> assignments() {
        return studentService.listAssignments();
    }

    @PostMapping("/assign")
    public Map<String, Object> assign(@RequestBody AssignmentRequest request) {
        if (request.getCoordinatorId() == null || request.getCoordinatorId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A coordinator is required");
        }
        return studentService.assignToCoordinator(request.getCoordinatorId(),
                request.getBranch(), request.getSection(), request.getBatch(),
                request.getStudentIds());
    }

    @PostMapping("/assignments/remove")
    public Map<String, Object> unassign(@RequestBody AssignmentRequest request) {
        if (request.getCoordinatorId() == null || request.getCoordinatorId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A coordinator is required");
        }
        return studentService.unassignStudents(request.getCoordinatorId(), request.getStudentIds());
    }

    @DeleteMapping("/assignments/{coordinatorId}/{studentId}")
    public Map<String, Object> unassignSingle(@PathVariable String coordinatorId,
                                              @PathVariable String studentId) {
        return studentService.unassignStudents(coordinatorId, List.of(studentId));
    }
}