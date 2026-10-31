package com.movesync.rostersync;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayMigrationScriptTest {

    @Test
    @DisplayName("Verify all 4 Flyway migration scripts exist and contain required DDL")
    void shouldValidateFlywayMigrationFiles() throws IOException {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("classpath:db/migration/V*__*.sql");

        assertThat(resources).hasSize(4);

        List<String> filenames = Arrays.stream(resources).map(Resource::getFilename).toList();
        assertThat(filenames).containsExactlyInAnyOrder(
                "V1__create_tenant_office.sql",
                "V2__create_employee.sql",
                "V3__create_outbox.sql",
                "V4__create_reconciliation.sql"
        );

        // Check content of V1
        String v1 = readResource(resources, "V1__create_tenant_office.sql");
        assertThat(v1).contains("CREATE TABLE tenant", "CREATE TABLE office", "idx_tenant_code", "uq_tenant_office_code");

        // Check content of V2
        String v2 = readResource(resources, "V2__create_employee.sql");
        assertThat(v2).contains("CREATE TABLE employee", "uq_tenant_source_emp", "idx_employee_tenant_source", "idx_employee_tenant_hash");

        // Check content of V3
        String v3 = readResource(resources, "V3__create_outbox.sql");
        assertThat(v3).contains("CREATE TABLE outbox_event", "idx_outbox_pending_poll");

        // Check content of V4
        String v4 = readResource(resources, "V4__create_reconciliation.sql");
        assertThat(v4).contains("CREATE TABLE reconciliation_run", "CREATE TABLE reconciliation_item", "idx_recon_tenant_status");
    }

    private String readResource(Resource[] resources, String filename) throws IOException {
        Resource r = Arrays.stream(resources)
                .filter(res -> filename.equals(res.getFilename()))
                .findFirst()
                .orElseThrow();
        return new String(r.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }
}

