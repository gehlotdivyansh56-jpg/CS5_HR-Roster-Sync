package com.movesync.rostersync.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SafetyBrakeServiceTest {

    private final SafetyBrakeService safetyBrakeService = new SafetyBrakeService();

    @Test
    @DisplayName("Should not trigger safety brake when store is empty")
    void shouldNotTriggerWhenStoreEmpty() {
        SafetyBrakeService.SafetyBrakeResult result = safetyBrakeService.evaluate(0, 0, 0.30);
        assertThat(result.brakeTriggered()).isFalse();
        assertThat(result.missingRatio()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Should pass when missing ratio is below threshold (e.g. 5% missing with 30% threshold)")
    void shouldPassWhenWithinThreshold() {
        // 100 active in store, 5 missing -> 5% missing <= 30%
        SafetyBrakeService.SafetyBrakeResult result = safetyBrakeService.evaluate(100, 5, 0.30);
        assertThat(result.brakeTriggered()).isFalse();
        assertThat(result.missingRatio()).isEqualTo(0.05);
        assertThat(result.message()).contains("Safety brake check passed");
    }

    @Test
    @DisplayName("Should trigger safety brake when missing ratio exceeds threshold (e.g. 40% missing with 30% threshold)")
    void shouldTriggerBrakeWhenExceedingThreshold() {
        // 200,000 active in store, 80,000 missing -> 40% missing > 30%
        SafetyBrakeService.SafetyBrakeResult result = safetyBrakeService.evaluate(200000, 80000, 0.30);
        assertThat(result.brakeTriggered()).isTrue();
        assertThat(result.missingRatio()).isEqualTo(0.40);
        assertThat(result.message()).contains("SAFETY BRAKE TRIGGERED");
        assertThat(result.message()).contains("40.00%");
    }

    @Test
    @DisplayName("Should support custom configurable threshold per tenant (e.g. 15%)")
    void shouldSupportCustomThreshold() {
        // 1,000 active, 200 missing -> 20% missing > 15% custom threshold
        SafetyBrakeService.SafetyBrakeResult result = safetyBrakeService.evaluate(1000, 200, 0.15);
        assertThat(result.brakeTriggered()).isTrue();
        assertThat(result.threshold()).isEqualTo(0.15);
    }
}

