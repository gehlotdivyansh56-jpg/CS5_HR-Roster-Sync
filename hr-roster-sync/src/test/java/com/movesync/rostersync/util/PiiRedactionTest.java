package com.movesync.rostersync.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PiiRedactionTest {

    @Test
    @DisplayName("Should mask plain string completely")
    void shouldMaskString() {
        assertThat(PiiRedactor.mask("John Doe")).isEqualTo("***REDACTED***");
        assertThat(PiiRedactor.mask(null)).isNull();
        assertThat(PiiRedactor.mask("")).isEmpty();
    }

    @Test
    @DisplayName("Should mask phone numbers retaining only last 4 digits")
    void shouldMaskPhone() {
        assertThat(PiiRedactor.maskPhone("+919876543210")).isEqualTo("***3210");
        assertThat(PiiRedactor.maskPhone("123")).isEqualTo("***REDACTED***");
        assertThat(PiiRedactor.maskPhone(null)).isEqualTo("***REDACTED***");
    }

    @Test
    @DisplayName("Should mask email preserving first letter and domain")
    void shouldMaskEmail() {
        assertThat(PiiRedactor.maskEmail("rahul.sharma@client.com")).isEqualTo("r***@client.com");
        assertThat(PiiRedactor.maskEmail("a@b.com")).isEqualTo("*@b.com");
        assertThat(PiiRedactor.maskEmail("invalid-email")).isEqualTo("***REDACTED***");
    }
}

