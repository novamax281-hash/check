package com.example.placement.service;

import com.example.placement.entity.UsersEntries;
import com.example.placement.repository.UsersRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

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
        if (email == null || password == null
            || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            return null;
        }
        UsersEntries user = usersRepository.findByEmailIgnoreCase(email.trim().toLowerCase(java.util.Locale.ROOT));
        if (!isStudent(user) || user.getPasswordHash() == null
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

    public UsersEntries saveStudentPassword(UsersEntries user, String password) {
        if (password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("Password must be at most 72 UTF-8 bytes");
        }
        user.setPasswordHash(passwordEncoder.encode(password));
        return usersRepository.save(user);
    }
}
