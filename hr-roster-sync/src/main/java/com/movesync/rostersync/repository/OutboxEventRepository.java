package com.movesync.rostersync.repository;

import com.movesync.rostersync.domain.entity.OutboxEvent;
import com.movesync.rostersync.domain.enums.OutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    @Query(value = "SELECT * FROM outbox_event WHERE status = 'PENDING' ORDER BY id ASC LIMIT :batchSize FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<OutboxEvent> pollPendingEventsWithSkipLocked(@Param("batchSize") int batchSize);

    long countByStatus(OutboxStatus status);
}

