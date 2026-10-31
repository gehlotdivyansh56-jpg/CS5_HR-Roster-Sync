package com.movesync.rostersync.auth;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.UUID;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;

    public JwtAuthFilter(JwtTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String bearerToken = request.getHeader("Authorization");
        String correlationId = request.getHeader("X-Correlation-Id");
        if (!StringUtils.hasText(correlationId)) {
            correlationId = "req-" + UUID.randomUUID().toString().substring(0, 8);
        }
        org.slf4j.MDC.put("correlation_id", correlationId);
        response.setHeader("X-Correlation-Id", correlationId);

        try {
            if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
                String token = bearerToken.substring(7);
                Claims claims = tokenProvider.parseAndValidate(token);

                String tenantIdStr = claims.get("tenant_id", String.class);
                String role = claims.get("role", String.class);
                String subject = claims.getSubject();

                if (tenantIdStr != null && role != null) {
                    UUID tenantId = UUID.fromString(tenantIdStr);
                    TenantContext.setTenantId(tenantId);
                    org.slf4j.MDC.put("tenant_id", tenantId.toString());

                    String roleWithPrefix = role.startsWith("ROLE_") ? role : "ROLE_" + role;
                    SimpleGrantedAuthority authority = new SimpleGrantedAuthority(roleWithPrefix);

                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(subject, null, Collections.singletonList(authority));
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }

            filterChain.doFilter(request, response);

        } finally {
            // Guaranteed cleanup to prevent thread pool reuse pollution
            TenantContext.clear();
            org.slf4j.MDC.clear();
        }
    }
}

