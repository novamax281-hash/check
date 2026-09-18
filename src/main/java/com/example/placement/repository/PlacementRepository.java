package com.example.placement.repository;

import com.example.placement.entity.Placement;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PlacementRepository extends MongoRepository<Placement, String> {

    List<Placement> findByCoordinatorIdsContaining(String coordinatorId);

    List<Placement> findByCoordinatorId(String coordinatorId);

    // Jobs a student may see: anything not marked "Closed" (case-insensitive, null-safe), pushed to the database.
    @Query("{ 'status': { $not: { $regex: '^Closed$', $options: 'i' } } }")
    List<Placement> findVisibleJobs();

    // Every job a coordinator can manage, across the fields ownership has been tracked in over time.
    @Query("{ $or: [ { 'coordinatorId': ?0 }, { 'createdBy': ?0 }, { 'coordinatorIds': ?0 } ] }")
    List<Placement> findAllForCoordinator(String coordinatorId);
}
