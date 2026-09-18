package com.example.placement.repository;

import com.example.placement.entity.Application;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ApplicationRepository extends MongoRepository<Application, String> {

    Optional<Application> findByStudentIdAndPlacementId(String studentId, String placementId);

    List<Application> findByStudentId(String studentId);

    List<Application> findByPlacementId(String placementId);

    List<Application> findByPlacementIdIn(Collection<String> placementIds);

    List<Application> findByStudentIdIn(Collection<String> studentIds);

    void deleteByPlacementId(String placementId);

}
