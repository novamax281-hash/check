package com.example.placement.repository;

import com.example.placement.entity.Notice;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface NoticeRepository extends MongoRepository<Notice, String> {

    List<Notice> findAllByOrderByUpdatedAtDesc();

    /**
     * Notices a student may see: enabled, and either never expiring or not yet expired.
     * Filtering expiry in the query keeps expired rows off the wire instead of relying
     * on the client to hide them.
     */
    @Query("{ 'active': true, $or: [ { 'expiresAt': null }, { 'expiresAt': { $gt: ?0 } } ] }")
    List<Notice> findVisible(LocalDateTime now);
}
