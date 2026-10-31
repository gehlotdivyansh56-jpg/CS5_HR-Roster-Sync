package com.movesync.rostersync.repository;

import com.movesync.rostersync.domain.entity.Employee;
import com.movesync.rostersync.domain.enums.EmployeeStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmployeeRepository extends JpaRepository<Employee, UUID> {

    Optional<Employee> findByTenantIdAndSourceEmployeeId(UUID tenantId, String sourceEmployeeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM Employee e WHERE e.tenant.id = :tenantId AND e.sourceEmployeeId = :sourceEmployeeId")
    Optional<Employee> findForUpdate(@Param("tenantId") UUID tenantId, @Param("sourceEmployeeId") String sourceEmployeeId);

    Page<Employee> findByTenantIdAndStatus(UUID tenantId, EmployeeStatus status, Pageable pageable);

    Page<Employee> findByTenantId(UUID tenantId, Pageable pageable);

    long countByTenantIdAndStatus(UUID tenantId, EmployeeStatus status);

    @Query("SELECT e.sourceEmployeeId AS sourceEmployeeId, e.recordHash AS recordHash FROM Employee e WHERE e.tenant.id = :tenantId AND e.status = :status")
    List<EmployeeHashView> findAllActiveHashes(@Param("tenantId") UUID tenantId, @Param("status") EmployeeStatus status);
}

