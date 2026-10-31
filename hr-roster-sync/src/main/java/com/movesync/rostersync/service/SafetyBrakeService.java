package com.movesync.rostersync.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SafetyBrakeService {

    private static final Logger log = LoggerFactory.getLogger(SafetyBrakeService.class);

    public record SafetyBrakeResult(
            boolean brakeTriggered,
            double missingRatio,
            int missingCount,
            int totalInStore,
            double threshold,
            String message
    ) {}

    /**
     * Evaluates whether missing records in reconciliation exceed the configured safety brake threshold.
     */
    public SafetyBrakeResult evaluate(int totalInStore, int missingCount, double threshold) {
        if (totalInStore <= 0) {
            return new SafetyBrakeResult(
                    false,
                    0.0,
                    0,
                    0,
                    threshold,
                    "Store is empty; safety brake not applicable"
            );
        }

        double ratio = (double) missingCount / totalInStore;
        boolean triggered = ratio > threshold;

        String formattedPercent = String.format("%.2f%%", ratio * 100);
        String formattedThreshold = String.format("%.2f%%", threshold * 100);

        String message;
        if (triggered) {
            message = String.format(
                    "SAFETY BRAKE TRIGGERED: %d of %d active employees (%s) missing from nightly export, exceeding threshold of %s. Deactivations withheld pending human operator approval.",
                    missingCount, totalInStore, formattedPercent, formattedThreshold
            );
            log.error(message);
        } else {
            message = String.format(
                    "Safety brake check passed: %d of %d active employees (%s) missing, within threshold of %s.",
                    missingCount, totalInStore, formattedPercent, formattedThreshold
            );
            log.info(message);
        }

        return new SafetyBrakeResult(triggered, ratio, missingCount, totalInStore, threshold, message);
    }
}

