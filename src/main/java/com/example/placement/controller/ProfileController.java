package com.example.placement.controller;

import java.util.List;
import java.util.function.Consumer;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.example.placement.entity.UsersEntries;
import com.example.placement.repository.UsersRepository;
import com.example.placement.service.BatchRanges;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private static final List<String> BTECH_BRANCHES = List.of(
            "CSE", "ECE", "EEE", "ME", "CIVIL", "Cyber Security", "Data Science", "AI / ML");

    private final UsersRepository usersRepository;

    public ProfileController(UsersRepository usersRepository) {
        this.usersRepository = usersRepository;
    }

    @GetMapping
    public UsersEntries getProfile(Authentication authentication) {
        return currentUser(authentication);
    }

    @PutMapping
    public UsersEntries updateProfile(Authentication authentication, @RequestBody UsersEntries update) {
        UsersEntries user = currentUser(authentication);
        String role = user.getRole();

        if (!canEditAdminManagedFields(role) && hasAdminManagedFieldChange(update)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Admin-managed fields cannot be changed by students");
        }
        if (!canEditAdminManagedFields(role) && hasRegistrationFieldChange(user, update)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Course and batch cannot be changed after registration");
        }

        validateAcademicFields(update);
        String profileCourse = update.getCourse() == null || update.getCourse().isBlank()
                ? user.getCourse() : update.getCourse();
        String branch = normalizeBranch(profileCourse, update.getBranch());

        copyIfPresent(update.getName(), user::setName);
        copyIfPresent(update.getDob(), user::setDob);
        copyIfPresent(update.getGender(), user::setGender);
        copyIfPresent(update.getAddress(), user::setAddress);
        copyIfPresent(update.getCity(), user::setCity);
        copyIfPresent(update.getState(), user::setState);
        copyIfPresent(update.getBatch(), user::setBatch);
        if (!isBranchlessCourse(profileCourse)) {
            copyIfPresent(branch, user::setBranch);
        }
        copyIfPresent(update.getSection(), user::setSection);
        copyIfPresent(update.getCourse(), user::setCourse);
        copyIfPresent(update.getCollege(), user::setCollege);
        if (update.getSgpas() != null) {
            List<Double> sgpas = normalizeSgpas(update.getSgpas());
            user.setSgpas(sgpas);
            // Overall CGPA is derived from the semester SGPAs so placement eligibility
            // (which reads cgpa) always reflects the marks a student actually entered.
            user.setCgpa(average(sgpas));
        } else {
            copyIfPresent(update.getCgpa(), user::setCgpa);
        }
        copyIfPresent(update.getGraduationPercentage(), user::setGraduationPercentage);

        if (canEditAdminManagedFields(role)) {
            copyIfPresent(update.getEmail(), user::setEmail);
            copyIfPresent(update.getPhoneno(), user::setPhoneno);
            copyIfPresent(update.getTenthPercentage(), user::setTenthPercentage);
            copyIfPresent(update.getTwelfthPercentage(), user::setTwelfthPercentage);
        }

        return usersRepository.save(user);
    }

    private String normalizeBranch(String course, String branch) {
        if (branch == null || branch.isBlank() || !isBtechCourse(course)) {
            return branch;
        }
        return BTECH_BRANCHES.stream()
                .filter(allowed -> allowed.equalsIgnoreCase(branch.trim()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Branch must be one of: " + String.join(", ", BTECH_BRANCHES)));
    }

    private boolean isBtechCourse(String course) {
        return course != null && course.trim().equalsIgnoreCase("B.Tech");
    }

    private boolean isBranchlessCourse(String course) {
        return course != null && (course.trim().equalsIgnoreCase("MCA")
                || course.trim().equalsIgnoreCase("BCA"));
    }

    private UsersEntries currentUser(Authentication authentication) {
        UsersEntries user = usersRepository.findByEnrollmentno(authentication.getName());
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
        return user;
    }

    private boolean canEditAdminManagedFields(String role) {
        return isCoordinator(role) || "admin".equalsIgnoreCase(role);
    }

    private boolean isCoordinator(String role) {
        return role != null && role.equalsIgnoreCase("coordinator");
    }

    private boolean hasAdminManagedFieldChange(UsersEntries update) {
        return (update.getEmail() != null && !update.getEmail().isBlank())
                || (update.getPhoneno() != null && !update.getPhoneno().isBlank())
                || update.getTenthPercentage() != null
                || update.getTwelfthPercentage() != null;
    }

    private boolean hasRegistrationFieldChange(UsersEntries user, UsersEntries update) {
        return hasChangedValue(user.getBatch(), update.getBatch())
                || hasChangedValue(user.getCourse(), update.getCourse());
    }

    private boolean hasChangedValue(String current, String incoming) {
        return incoming != null && !incoming.isBlank()
                && !incoming.trim().equals(current == null ? "" : current.trim());
    }

    private void copyIfPresent(String incoming, Consumer<String> setter) {
        if (incoming != null && !incoming.isBlank()) {
            setter.accept(incoming.trim());
        }
    }

    private void copyIfPresent(Double incoming, Consumer<Double> setter) {
        if (incoming != null) {
            setter.accept(incoming);
        }
    }

    /**
     * These values decide placement eligibility, so the self-service profile form must not
     * be able to store marks or a batch outside the ranges accepted at registration.
     * Previously a student could post an arbitrary CGPA/percentage and unlock any drive.
     */
    private void validateAcademicFields(UsersEntries update) {
        requireRange("CGPA", update.getCgpa(), 0, 10);
        requireRange("10th percentage", update.getTenthPercentage(), 0, 100);
        requireRange("12th percentage", update.getTwelfthPercentage(), 0, 100);
        requireRange("graduation percentage", update.getGraduationPercentage(), 0, 100);
        if (update.getSgpas() != null) {
            for (Double sgpa : update.getSgpas()) {
                requireRange("Semester SGPA", sgpa, 0, 10);
            }
        }
        String batch = update.getBatch();
        if (batch != null && !batch.isBlank() && !BatchRanges.isValidSpec(batch)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Each eligible batch must be a valid From Year and To Year, for example 2022-2026");
        }
    }

    private void requireRange(String label, Double value, double min, double max) {
        if (value == null) {
            return;
        }
        if (value.isNaN() || value.isInfinite() || value < min || value > max) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    label + " must be between " + (long) min + " and " + (long) max);
        }
    }

    private static List<Double> normalizeSgpas(List<Double> incoming) {
        List<Double> cleaned = new java.util.ArrayList<>();
        for (Double value : incoming) {
            cleaned.add(value == null ? null : Math.round(value * 100.0) / 100.0);
        }
        return cleaned;
    }

    private static Double average(List<Double> values) {
        double sum = 0;
        int counted = 0;
        for (Double value : values) {
            if (value != null) {
                sum += value;
                counted++;
            }
        }
        if (counted == 0) {
            return null;
        }
        return Math.round(sum / counted * 100.0) / 100.0;
    }
}