package com.example.placement.service;

import com.example.placement.entity.UsersEntries;
import com.example.placement.repository.UsersRepository;
import org.springframework.stereotype.Service;

@Service
public class AuthenticationService {

    private final UsersRepository usersRepository;

    public AuthenticationService(UsersRepository usersRepository) {
        this.usersRepository = usersRepository;
    }

    public UsersEntries validateCredentials(String enrollmentno, String mobile) {
        return usersRepository.findByEnrollmentnoAndPhoneno(enrollmentno, mobile);
    }
}
