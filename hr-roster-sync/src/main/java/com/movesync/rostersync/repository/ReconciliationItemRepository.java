package com.movesync.rostersync.repository;

import com.movesync.rostersync.domain.entity.ReconciliationItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ReconciliationItemRepository extends JpaRepository<ReconciliationItem, Long> {
    Page<ReconciliationItem> findByRunId(UUID runId, Pageable pageable);
}

