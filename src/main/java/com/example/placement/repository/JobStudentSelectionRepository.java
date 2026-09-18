package com.example.placement.repository;

import com.example.placement.entity.JobStudentSelection;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface JobStudentSelectionRepository extends MongoRepository<JobStudentSelection, String> {

    List<JobStudentSelection> findByJobId(String jobId);

    List<JobStudentSelection> findByJobIdIn(Collection<String> jobIds);

    List<JobStudentSelection> findByStudentId(String studentId);

    List<JobStudentSelection> findByJobIdAndStudentIdIn(String jobId, Collection<String> studentIds);

    Optional<JobStudentSelection> findByJobIdAndStudentId(String jobId, String studentId);

    void deleteByJobId(String jobId);

    void deleteByJobIdAndStudentId(String jobId, String studentId);
}
