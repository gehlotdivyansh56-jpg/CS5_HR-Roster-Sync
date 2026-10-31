package com.movesync.rostersync.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HashUtilTest {

    @Test
    @DisplayName("Should produce deterministic 64-character hex hash")
    void shouldProduceDeterministicHash() {
        String hash1 = HashUtil.computeHrRecordHash(
                "Alice Smith", "alice@corp.com", "+12025550143", "EMP-001", "BLR-01", "Engineering", "DAY", "ACTIVE"
        );
        String hash2 = HashUtil.computeHrRecordHash(
                "Alice Smith", "alice@corp.com", "+12025550143", "EMP-001", "BLR-01", "Engineering", "DAY", "ACTIVE"
        );

        assertThat(hash1).isNotNull();
        assertThat(hash1).hasSize(64);
        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    @DisplayName("Should distinguish strictly between null and empty string (ADR-005 collision resistance)")
    void shouldDistinguishNullFromEmptyString() {
        // Record A has department as null
        String hashNullDept = HashUtil.computeHrRecordHash(
                "Alice", "alice@corp.com", null, null, "BLR-01", null, "DAY", "ACTIVE"
        );
        // Record B has department as empty string
        String hashEmptyDept = HashUtil.computeHrRecordHash(
                "Alice", "alice@corp.com", null, null, "BLR-01", "", "DAY", "ACTIVE"
        );

        assertThat(hashNullDept).isNotEqualTo(hashEmptyDept);
    }

    @Test
    @DisplayName("Should normalize email casing and trim whitespace")
    void shouldNormalizeEmailAndWhitespace() {
        String hashUpper = HashUtil.computeHrRecordHash(
                "  Alice Smith  ", "ALICE@CORP.COM ", null, null, "blr-01", "IT", "DAY", "ACTIVE"
        );
        String hashLower = HashUtil.computeHrRecordHash(
                "Alice Smith", "alice@corp.com", null, null, "BLR-01", "IT", "DAY", "ACTIVE"
        );

        assertThat(hashUpper).isEqualTo(hashLower);
    }
}

