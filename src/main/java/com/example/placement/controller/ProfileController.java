package com.example.placement.controller;

import com.example.placement.entity.UsersEntries;
import com.example.placement.repository.UsersRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.function.Consumer;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {

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

        copyIfPresent(update.getName(), user::setName);
        copyIfPresent(update.getDob(), user::setDob);
        copyIfPresent(update.getGender(), user::setGender);
        copyIfPresent(update.getAddress(), user::setAddress);
        copyIfPresent(update.getCity(), user::setCity);
        copyIfPresent(update.getState(), user::setState);
        copyIfPresent(update.getBatch(), user::setBatch);
        copyIfPresent(update.getBranch(), user::setBranch);
        copyIfPresent(update.getSection(), user::setSection);
        copyIfPresent(update.getDepartment(), user::setDepartment);
        copyIfPresent(update.getCourse(), user::setCourse);
        copyIfPresent(update.getCollege(), user::setCollege);
        copyIfPresent(update.getCgpa(), user::setCgpa);
        copyIfPresent(update.getTenthPercentage(), user::setTenthPercentage);
        copyIfPresent(update.getTwelfthPercentage(), user::setTwelfthPercentage);
        copyIfPresent(update.getGraduationPercentage(), user::setGraduationPercentage);

        boolean canEditContact = isCoordinator(role) || "admin".equalsIgnoreCase(role);
        if (canEditContact) {
            copyIfPresent(update.getEmail(), user::setEmail);
            copyIfPresent(update.getPhoneno(), user::setPhoneno);
        }

        return usersRepository.save(user);
    }

    private UsersEntries currentUser(Authentication authentication) {
        UsersEntries user = usersRepository.findByEnrollmentno(authentication.getName());
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
        return user;
    }

    private boolean isCoordinator(String role) {
        return role != null && role.equalsIgnoreCase("coordinator");
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
}