package com.movesync.rostersync.config;

import com.movesync.rostersync.auth.JwtTokenProvider;
import com.movesync.rostersync.domain.entity.Office;
import com.movesync.rostersync.domain.entity.Tenant;
import com.movesync.rostersync.repository.OfficeRepository;
import com.movesync.rostersync.repository.TenantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@Profile("!test")
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    public static final UUID GOOGLE_TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    public static final UUID MICROSOFT_TENANT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private final TenantRepository tenantRepository;
    private final OfficeRepository officeRepository;
    private final JwtTokenProvider tokenProvider;
    private final JdbcTemplate jdbcTemplate;

    public DataSeeder(TenantRepository tenantRepository,
                      OfficeRepository officeRepository,
                      JwtTokenProvider tokenProvider,
                      JdbcTemplate jdbcTemplate) {
        this.tenantRepository = tenantRepository;
        this.officeRepository = officeRepository;
        this.tokenProvider = tokenProvider;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        log.info("Bootstrapping deterministic seed data for development...");

        try {
            // Clean up any non-deterministic demo records if present
            jdbcTemplate.execute("DELETE FROM tenant WHERE code IN ('google', 'microsoft') AND id NOT IN ('11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222')");

            // Seed Demo Tenant 1: Google (11111111-1111-1111-1111-111111111111)
            jdbcTemplate.execute("""
                INSERT INTO tenant (id, code, name, safety_brake_threshold) 
                VALUES ('11111111-1111-1111-1111-111111111111', 'google', 'Google Inc.', 0.30)
                ON CONFLICT (id) DO NOTHING;
            """);

            jdbcTemplate.execute("""
                INSERT INTO office (tenant_id, code, name, city, active)
                VALUES ('11111111-1111-1111-1111-111111111111', 'BLR-01', 'Google Bangalore Hub', 'Bangalore', true)
                ON CONFLICT (tenant_id, code) DO NOTHING;
            """);

            jdbcTemplate.execute("""
                INSERT INTO office (tenant_id, code, name, city, active)
                VALUES ('11111111-1111-1111-1111-111111111111', 'HYD-01', 'Google Hyderabad Hub', 'Hyderabad', true)
                ON CONFLICT (tenant_id, code) DO NOTHING;
            """);

            // Seed Demo Tenant 2: Microsoft (22222222-2222-2222-2222-222222222222)
            jdbcTemplate.execute("""
                INSERT INTO tenant (id, code, name, safety_brake_threshold) 
                VALUES ('22222222-2222-2222-2222-222222222222', 'microsoft', 'Microsoft Corp.', 0.30)
                ON CONFLICT (id) DO NOTHING;
            """);

            jdbcTemplate.execute("""
                INSERT INTO office (tenant_id, code, name, city, active)
                VALUES ('22222222-2222-2222-2222-222222222222', 'BLR-02', 'Microsoft Bangalore', 'Bangalore', true)
                ON CONFLICT (tenant_id, code) DO NOTHING;
            """);

            // Generate Sample Tokens for Postman / Testing
            String hrTokenGoogle = tokenProvider.generateToken("google-hr-service", GOOGLE_TENANT_ID, "HR_SYSTEM");
            String transportAdminGoogle = tokenProvider.generateToken("google-transport-admin", GOOGLE_TENANT_ID, "TRANSPORT_ADMIN");
            String operatorTokenGoogle = tokenProvider.generateToken("google-operator", GOOGLE_TENANT_ID, "OPERATOR");

            log.info("==================================================================================");
            log.info("Demo Seed Data Initialized:");
            log.info("Tenant Google ID: {}", GOOGLE_TENANT_ID);
            log.info("Tenant Microsoft ID: {}", MICROSOFT_TENANT_ID);
            log.info("Sample JWT Token (Google HR_SYSTEM): Bearer {}", hrTokenGoogle);
            log.info("Sample JWT Token (Google TRANSPORT_ADMIN): Bearer {}", transportAdminGoogle);
            log.info("Sample JWT Token (Google OPERATOR): Bearer {}", operatorTokenGoogle);
            log.info("==================================================================================");
        } catch (Exception ex) {
            log.warn("Seed data initialization warning: {}", ex.getMessage());
        }
    }
}

