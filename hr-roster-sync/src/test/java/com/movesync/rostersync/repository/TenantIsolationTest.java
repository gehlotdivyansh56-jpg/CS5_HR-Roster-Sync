package com.movesync.rostersync.repository;

import com.movesync.rostersync.domain.entity.Employee;
import com.movesync.rostersync.domain.entity.Office;
import com.movesync.rostersync.domain.entity.Tenant;
import com.movesync.rostersync.domain.enums.EmployeeStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase(replace = org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE)
class TenantIsolationTest {

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private OfficeRepository officeRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Test
    @DisplayName("Tenant A must never access or observe records belonging to Tenant B")
    void shouldEnforceTenantIsolation() {
        // 1. Create two distinct tenants
        Tenant tenantA = tenantRepository.save(new Tenant("google", "Google Inc.", 0.30));
        Tenant tenantB = tenantRepository.save(new Tenant("microsoft", "Microsoft Corp.", 0.30));

        // 2. Create offices for both
        Office officeA = officeRepository.save(new Office(tenantA, "BLR-01", "Google Bangalore", "Bangalore"));
        Office officeB = officeRepository.save(new Office(tenantB, "HYD-01", "Microsoft Hyderabad", "Hyderabad"));

        // 3. Create employees with matching sourceEmployeeId under different tenants
        Employee empA = new Employee(tenantA, "EMP-100", 1L, "Alice Johnson", "alice@google.com", "hashA123");
        empA.setOffice(officeA);
        employeeRepository.save(empA);

        Employee empB = new Employee(tenantB, "EMP-100", 1L, "Bob Smith", "bob@microsoft.com", "hashB456");
        empB.setOffice(officeB);
        employeeRepository.save(empB);

        // 4. Query under Tenant A scope
        Optional<Employee> foundInA = employeeRepository.findByTenantIdAndSourceEmployeeId(tenantA.getId(), "EMP-100");
        assertThat(foundInA).isPresent();
        assertThat(foundInA.get().getName()).isEqualTo("Alice Johnson");
        assertThat(foundInA.get().getTenant().getCode()).isEqualTo("google");

        // 5. Query under Tenant B scope
        Optional<Employee> foundInB = employeeRepository.findByTenantIdAndSourceEmployeeId(tenantB.getId(), "EMP-100");
        assertThat(foundInB).isPresent();
        assertThat(foundInB.get().getName()).isEqualTo("Bob Smith");
        assertThat(foundInB.get().getTenant().getCode()).isEqualTo("microsoft");

        // 6. Active Hashes projection check under Tenant A scope
        List<EmployeeHashView> hashesA = employeeRepository.findAllActiveHashes(tenantA.getId(), EmployeeStatus.ACTIVE);
        assertThat(hashesA).hasSize(1);
        assertThat(hashesA.get(0).getSourceEmployeeId()).isEqualTo("EMP-100");
        assertThat(hashesA.get(0).getRecordHash()).isEqualTo("hashA123");
    }
}
