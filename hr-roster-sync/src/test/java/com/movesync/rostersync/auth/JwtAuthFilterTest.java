package com.movesync.rostersync.auth;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtAuthFilterTest {

    private JwtTokenProvider tokenProvider;
    private JwtAuthFilter filter;

    @BeforeEach
    void setUp() {
        tokenProvider = new JwtTokenProvider(
                "MoveInSyncRosterSyncSuperSecretKeyForJwtAuthenticationMustBeAtLeast256BitsLong",
                60
        );
        filter = new JwtAuthFilter(tokenProvider);
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test
    @DisplayName("Should extract TenantContext and SecurityContext when valid Bearer token provided")
    void shouldAuthenticateValidToken() throws ServletException, IOException {
        UUID expectedTenantId = UUID.randomUUID();
        String token = tokenProvider.generateToken("hr-service", expectedTenantId, "HR_SYSTEM");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean[] filterChainExecuted = {false};
        FilterChain filterChain = (req, res) -> {
            filterChainExecuted[0] = true;
            // Inside the filter chain, TenantContext and SecurityContext must be present
            assertThat(TenantContext.getTenantId()).isEqualTo(expectedTenantId);
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
            assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("hr-service");
            assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                    .anyMatch(a -> a.getAuthority().equals("ROLE_HR_SYSTEM"));
        };

        filter.doFilter(request, response, filterChain);

        assertThat(filterChainExecuted[0]).isTrue();
        // After filter completion, ThreadLocal TenantContext must be cleanly wiped
        assertThat(TenantContext.getTenantId()).isNull();
    }

    @Test
    @DisplayName("Should proceed without authentication when no Authorization header provided")
    void shouldProceedWhenNoHeader() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean[] filterChainExecuted = {false};
        FilterChain filterChain = (req, res) -> {
            filterChainExecuted[0] = true;
            assertThat(TenantContext.getTenantId()).isNull();
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        };

        filter.doFilter(request, response, filterChain);
        assertThat(filterChainExecuted[0]).isTrue();
    }
}

