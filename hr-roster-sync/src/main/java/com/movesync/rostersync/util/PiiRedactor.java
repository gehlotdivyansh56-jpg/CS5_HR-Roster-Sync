package com.movesync.rostersync.util;

public final class PiiRedactor {

    private static final String REDACTED_PLACEHOLDER = "***REDACTED***";

    private PiiRedactor() {}

    public static String mask(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        return REDACTED_PLACEHOLDER;
    }

    public static String maskEmail(String email) {
        if (email == null || email.isBlank() || !email.contains("@")) {
            return REDACTED_PLACEHOLDER;
        }
        int atIndex = email.indexOf('@');
        if (atIndex <= 1) {
            return "*@" + email.substring(atIndex + 1);
        }
        return email.charAt(0) + "***" + email.substring(atIndex);
    }

    public static String maskPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return REDACTED_PLACEHOLDER;
        }
        if (phone.length() <= 4) {
            return REDACTED_PLACEHOLDER;
        }
        return "***" + phone.substring(phone.length() - 4);
    }
}

