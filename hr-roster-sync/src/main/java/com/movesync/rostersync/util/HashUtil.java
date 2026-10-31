package com.movesync.rostersync.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Deterministic, null-safe canonical hashing utility.
 * Implements ADR-005: Length-Prefixed Canonical Encoding with Null Sentinels.
 *
 * Prevents delimiter collisions and treats null distinctly from empty strings.
 * Example canonical form: "4:John|14:john@corp.com|\u0000NULL\u0000|0:|..."
 */
public final class HashUtil {

    private static final String NULL_SENTINEL = "\u0000NULL\u0000";
    private static final char FIELD_DELIMITER = '|';

    private HashUtil() {}

    /**
     * Computes deterministic SHA-256 hash for HR-owned fields.
     */
    public static String computeHrRecordHash(
            String name,
            String email,
            String phone,
            String managerSourceId,
            String officeCode,
            String department,
            String shift,
            String status) {

        StringBuilder canonical = new StringBuilder(256);
        appendField(canonical, name != null ? name.trim() : null);
        appendField(canonical, email != null ? email.trim().toLowerCase() : null);
        appendField(canonical, phone != null ? phone.trim() : null);
        appendField(canonical, managerSourceId != null ? managerSourceId.trim() : null);
        appendField(canonical, officeCode != null ? officeCode.trim().toUpperCase() : null);
        appendField(canonical, department != null ? department.trim() : null);
        appendField(canonical, shift != null ? shift.trim() : null);
        appendField(canonical, status != null ? status.trim().toUpperCase() : null);

        return sha256Hex(canonical.toString());
    }

    private static void appendField(StringBuilder sb, String value) {
        if (!sb.isEmpty()) {
            sb.append(FIELD_DELIMITER);
        }
        if (value == null) {
            sb.append(NULL_SENTINEL);
        } else {
            sb.append(value.length()).append(':').append(value);
        }
    }

    public static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedhash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder(64);
            for (byte b : encodedhash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}

