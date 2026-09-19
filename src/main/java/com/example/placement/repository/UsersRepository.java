package com.example.placement.repository;

import com.example.placement.entity.UsersEntries;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UsersRepository extends MongoRepository<UsersEntries, String> {

    UsersEntries findByEnrollmentno(String enrollmentno);

    UsersEntries findByEnrollmentnoAndEmail(String enrollmentno, String email);

    UsersEntries findByEnrollmentnoAndPhoneno(String enrollmentno, String phoneno);
}
