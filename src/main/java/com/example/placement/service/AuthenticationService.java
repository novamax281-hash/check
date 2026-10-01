package com.example.placement.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.example.placement.entity.UsersEntries;
import com.example.placement.repository.UsersRepository;

@Service
public class AuthenticationService {

    private final UsersRepository usersRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthenticationService(UsersRepository usersRepository, PasswordEncoder passwordEncoder) {
        this.usersRepository = usersRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public UsersEntries validateCredentials(String enrollmentno, String mobile) {
        return usersRepository.findByEnrollmentnoAndPhoneno(enrollmentno, mobile);
    }

    public UsersEntries authenticateStudent(String email, String password) {
        return authenticateByEmailPassword(email, password, false);
    }

    public UsersEntries authenticateStaff(String email, String password) {
        return authenticateByEmailPassword(email, password, true);
    }

    private UsersEntries authenticateByEmailPassword(String email, String password, boolean staffOnly) {
        if (email == null || password == null
            || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            return null;
        }
        UsersEntries user = usersRepository.findByEmailIgnoreCase(email.trim().toLowerCase(java.util.Locale.ROOT));
        boolean eligible = staffOnly ? isStaff(user) : isStudent(user);
        if (!eligible || user.getPasswordHash() == null
                || !passwordEncoder.matches(password, user.getPasswordHash())) {
            return null;
        }
        return user;
    }

    public boolean isStudent(UsersEntries user) {
        if (user == null) {
            return false;
        }
        String role = user.getRole();
        return role == null || (!role.equalsIgnoreCase("admin") && !role.equalsIgnoreCase("coordinator"));
    }

    public boolean isStaff(UsersEntries user) {
        return user != null && user.getRole() != null
                && (user.getRole().equalsIgnoreCase("admin") || user.getRole().equalsIgnoreCase("coordinator"));
    }

    public UsersEntries savePassword(UsersEntries user, String password) {
        if (password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("Password must be at most 72 UTF-8 bytes");
        }
        user.setPasswordHash(passwordEncoder.encode(password));
        return usersRepository.save(user);
    }

    public UsersEntries saveStudentPassword(UsersEntries user, String password) {
        return savePassword(user, password);
    }
}
