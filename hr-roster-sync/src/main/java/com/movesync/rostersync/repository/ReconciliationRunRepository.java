package com.movesync.rostersync.repository;

import com.movesync.rostersync.domain.entity.ReconciliationRun;
import com.movesync.rostersync.domain.enums.ReconciliationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReconciliationRunRepository extends JpaRepository<ReconciliationRun, UUID> {
    Optional<ReconciliationRun> findByIdAndTenantId(UUID id, UUID tenantId);
    Page<ReconciliationRun> findByTenantId(UUID tenantId, Pageable pageable);
    long countByTenantIdAndStatus(UUID tenantId, ReconciliationStatus status);
}

