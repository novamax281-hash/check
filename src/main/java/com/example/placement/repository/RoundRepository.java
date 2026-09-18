package com.example.placement.repository;

import com.example.placement.entity.Round;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface RoundRepository extends MongoRepository<Round, String> {

    List<Round> findByJobIdOrderByRoundNumberAsc(String jobId);

    List<Round> findByJobIdInOrderByRoundNumberAsc(Collection<String> jobIds);

    boolean existsByJobIdAndRoundNumber(String jobId, Integer roundNumber);

    java.util.Optional<Round> findByJobIdAndRoundNumber(String jobId, Integer roundNumber);
}
