package com.example.placement.controller;

import com.example.placement.dto.ApplicationDetail;
import com.example.placement.entity.Placement;
import com.example.placement.entity.UsersEntries;
import com.example.placement.repository.PlacementRepository;
import com.example.placement.repository.UsersRepository;
import com.example.placement.service.ApplicationService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/user")
public class UserController {

    private final UsersRepository usersRepository;
    private final ApplicationService applicationService;
    private final PlacementRepository placementRepository;

    public UserController(UsersRepository usersRepository, ApplicationService applicationService,
                          PlacementRepository placementRepository) {
        this.usersRepository = usersRepository;
        this.applicationService = applicationService;
        this.placementRepository = placementRepository;
    }

    private String currentUserId(Authentication authentication) {
        UsersEntries user = usersRepository.findByEnrollmentno(authentication.getName());
        return user != null ? user.getId() : null;
    }

    @GetMapping("/profile")
    public ResponseEntity<?> getProfile(Authentication authentication) {
        UsersEntries user = usersRepository.findByEnrollmentno(authentication.getName());
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "User not found"));
        }
        return ResponseEntity.ok(user);
    }

    @GetMapping("/placements")
    public ResponseEntity<List<Placement>> getAllPlacements(Authentication authentication) {
        UsersEntries user = usersRepository.findByEnrollmentno(authentication.getName());
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(List.of());
        }
        List<Placement> visible = placementRepository.findVisibleJobs();
        return ResponseEntity.ok(visible.stream()
                .filter(job -> applicationService.isEligible(job, user))
                .collect(Collectors.toList()));
    }

    @PostMapping("/apply")
    public ResponseEntity<?> apply(Authentication authentication, @RequestBody Map<String, String> body) {
        UsersEntries user = usersRepository.findByEnrollmentno(authentication.getName());
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "User not found"));
        }
        if (isStaff(user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Only students can apply for placements"));
        }
        if ("Inactive".equalsIgnoreCase(user.getStudentStatus())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Your account is inactive. Contact your coordinator."));
        }
        String jobId = body.get("jobId");
        if (jobId == null || jobId.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Job id is required"));
        }
        String resumeUrl = body.get("resumeUrl");
        if (!isValidResumeUrl(resumeUrl)) {
            return ResponseEntity.badRequest().body(Map.of("message", "A valid resume URL is required"));
        }
        Placement job = placementRepository.findById(jobId).orElse(null);
        if (job == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Placement not found"));
        }
        if ("Closed".equalsIgnoreCase(job.getStatus())) {
            return ResponseEntity.badRequest().body(Map.of("message", "This placement is closed for applications"));
        }
        if (!applicationService.isEligible(job, user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "You do not meet this placement's eligibility criteria"));
        }
        try {
            String result = applicationService.apply(user.getId(), jobId, resumeUrl.trim());
            if ("already".equals(result)) {
                return ResponseEntity.badRequest().body(Map.of("message", "You have already applied for this placement"));
            }
        } catch (DuplicateKeyException duplicate) {
            return ResponseEntity.badRequest().body(Map.of("message", "You have already applied for this placement"));
        }
        return ResponseEntity.ok(Map.of("message", "Application submitted successfully"));
    }

    private boolean isStaff(UsersEntries user) {
        String role = user.getRole() == null ? "" : user.getRole().trim().toLowerCase();
        return "admin".equals(role) || "coordinator".equals(role);
    }

    private boolean isValidResumeUrl(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            URI uri = URI.create(value.trim());
            return uri.getHost() != null && ("http".equalsIgnoreCase(uri.getScheme())
                    || "https".equalsIgnoreCase(uri.getScheme()));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    @GetMapping("/applications")
    public ResponseEntity<List<ApplicationDetail>> myApplications(Authentication authentication) {
        String userId = currentUserId(authentication);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }
        return ResponseEntity.ok(applicationService.getStudentApplications(userId));
    }

    @GetMapping("/dashboard-stats")
    public ResponseEntity<Map<String, Long>> dashboardStats(Authentication authentication) {
        String userId = currentUserId(authentication);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }
        return ResponseEntity.ok(applicationService.studentStats(userId));
    }
}
