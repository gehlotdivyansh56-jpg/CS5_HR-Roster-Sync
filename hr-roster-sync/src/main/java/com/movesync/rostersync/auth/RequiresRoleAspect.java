package com.movesync.rostersync.auth;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

@Aspect
@Component
public class RequiresRoleAspect {

    private final com.movesync.rostersync.metrics.SyncMetricsService syncMetricsService;

    public RequiresRoleAspect(com.movesync.rostersync.metrics.SyncMetricsService syncMetricsService) {
        this.syncMetricsService = syncMetricsService;
    }

    public RequiresRoleAspect() {
        this.syncMetricsService = null;
    }

    @Before("@annotation(com.movesync.rostersync.auth.RequiresRole) || @within(com.movesync.rostersync.auth.RequiresRole)")
    public void enforceRole(JoinPoint joinPoint) {
        RequiresRole annotation = null;
        if (joinPoint.getSignature() instanceof MethodSignature methodSignature) {
            Method method = methodSignature.getMethod();
            annotation = method.getAnnotation(RequiresRole.class);
        }
        if (annotation == null && joinPoint.getTarget() != null) {
            annotation = joinPoint.getTarget().getClass().getAnnotation(RequiresRole.class);
        }
        if (annotation != null) {
            checkRole(annotation);
        }
    }

    public void checkRole(JoinPoint joinPoint, RequiresRole requiresRole) {
        checkRole(requiresRole);
    }

    public void checkRole(RequiresRole requiresRole) {
        if (requiresRole == null) {
            return;
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            if (syncMetricsService != null) syncMetricsService.recordAuthRejected();
            throw new AccessDeniedException("Unauthenticated access attempt");
        }

        String required = requiresRole.value().toUpperCase();
        String expectedAuthority = required.startsWith("ROLE_") ? required : "ROLE_" + required;

        boolean hasRole = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equalsIgnoreCase(expectedAuthority));

        if (!hasRole) {
            if (syncMetricsService != null) syncMetricsService.recordAuthRejected();
            throw new AccessDeniedException("Access denied: missing required role " + required);
        }
    }
}
