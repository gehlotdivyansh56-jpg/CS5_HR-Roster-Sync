package com.movesync.rostersync.auth;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequiresRoleAspectTest {

    private RequiresRoleAspect aspect;

    @BeforeEach
    void setUp() {
        aspect = new RequiresRoleAspect();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Should permit invocation when user has the exact required role")
    void shouldPermitWhenRoleMatches() {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "user", null, Collections.singletonList(new SimpleGrantedAuthority("ROLE_HR_SYSTEM"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        RequiresRole annotation = createAnnotation("HR_SYSTEM");
        assertThatCode(() -> aspect.checkRole(null, annotation)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should throw AccessDeniedException when user lacks the required role")
    void shouldDenyWhenRoleMismatch() {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "user", null, Collections.singletonList(new SimpleGrantedAuthority("ROLE_TRANSPORT_ADMIN"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        RequiresRole annotation = createAnnotation("HR_SYSTEM");
        assertThatThrownBy(() -> aspect.checkRole(null, annotation))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("missing required role HR_SYSTEM");
    }

    @Test
    @DisplayName("Should throw AccessDeniedException when unauthenticated")
    void shouldDenyWhenUnauthenticated() {
        SecurityContextHolder.clearContext();

        RequiresRole annotation = createAnnotation("HR_SYSTEM");
        assertThatThrownBy(() -> aspect.checkRole(null, annotation))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Unauthenticated");
    }

    private RequiresRole createAnnotation(String role) {
        return new RequiresRole() {
            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() {
                return RequiresRole.class;
            }

            @Override
            public String value() {
                return role;
            }
        };
    }
}

