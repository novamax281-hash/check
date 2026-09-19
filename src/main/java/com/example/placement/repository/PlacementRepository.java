package com.example.placement.repository;

import com.example.placement.entity.Placement;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PlacementRepository extends MongoRepository<Placement, String> {

    // Jobs a student may see: anything not marked "Closed" (case-insensitive, null-safe), pushed to the database.
    @Query("{ 'status': { $not: { $regex: '^Closed$', $options: 'i' } } }")
    List<Placement> findVisibleJobs();
}
