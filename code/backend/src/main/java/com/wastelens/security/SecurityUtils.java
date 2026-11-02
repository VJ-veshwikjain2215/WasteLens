package com.wastelens.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.UUID;

public final class SecurityUtils {

    private SecurityUtils() {}

    public static UUID getCurrentOwnerId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            String sub = jwtAuth.getToken().getSubject();
            if (sub != null && !sub.isBlank()) {
                return UUID.fromString(sub);
            }
        }
        throw new IllegalStateException("No authenticated JWT subject found in security context");
    }

    public static AuthenticatedUser getAuthenticatedUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            Jwt jwt = jwtAuth.getToken();
            UUID ownerId = UUID.fromString(jwt.getSubject());
            String email = jwt.getClaimAsString("email");
            String role = jwt.getClaimAsString("role");
            return new AuthenticatedUser(ownerId, email != null ? email : "", role != null ? role : "authenticated");
        }
        throw new IllegalStateException("No authenticated JWT in security context");
    }
}
