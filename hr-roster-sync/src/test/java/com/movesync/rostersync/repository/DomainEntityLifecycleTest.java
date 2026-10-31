package com.movesync.rostersync.repository;

import com.movesync.rostersync.domain.entity.OutboxEvent;
import com.movesync.rostersync.domain.entity.ReconciliationItem;
import com.movesync.rostersync.domain.entity.ReconciliationRun;
import com.movesync.rostersync.domain.entity.Tenant;
import com.movesync.rostersync.domain.enums.OutboxStatus;
import com.movesync.rostersync.domain.enums.ReconciliationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase(replace = org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE)
class DomainEntityLifecycleTest {

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private ReconciliationRunRepository reconciliationRunRepository;

    @Autowired
    private ReconciliationItemRepository reconciliationItemRepository;

    @Test
    @DisplayName("Should persist and retrieve outbox events and reconciliation audit records")
    void shouldPersistOutboxAndReconciliation() {
        Tenant tenant = tenantRepository.save(new Tenant("uber", "Uber Technologies", 0.25));

        // Outbox event test
        OutboxEvent event = new OutboxEvent(tenant.getId(), UUID.randomUUID(), "CREATED", "{\"employee_id\":\"EMP-01\"}");
        OutboxEvent savedEvent = outboxEventRepository.save(event);
        assertThat(savedEvent.getId()).isNotNull();
        assertThat(savedEvent.getStatus()).isEqualTo(OutboxStatus.PENDING);

        // Reconciliation run test
        ReconciliationRun run = new ReconciliationRun(tenant.getId(), 1730000000000L);
        run.setTotalInFile(1500);
        run.setTotalInStore(1520);
        run.setMissingRatio(0.013);
        ReconciliationRun savedRun = reconciliationRunRepository.save(run);
        assertThat(savedRun.getId()).isNotNull();
        assertThat(savedRun.getStatus()).isEqualTo(ReconciliationStatus.RUNNING);

        // Reconciliation item test
        ReconciliationItem item = new ReconciliationItem(savedRun, "EMP-01", "ADD", "New joiner in nightly file");
        ReconciliationItem savedItem = reconciliationItemRepository.save(item);
        assertThat(savedItem.getId()).isNotNull();
        assertThat(savedItem.getRun().getId()).isEqualTo(savedRun.getId());
    }
}
