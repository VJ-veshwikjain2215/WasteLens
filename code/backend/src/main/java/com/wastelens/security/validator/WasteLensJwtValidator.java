package com.wastelens.security.validator;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class WasteLensJwtValidator implements OAuth2TokenValidator<Jwt> {

    private final String expectedIssuer;
    private final String expectedAudience;
    private final Duration clockSkew;
    private final Clock clock;

    public WasteLensJwtValidator(String expectedIssuer, String expectedAudience, Duration clockSkew, Clock clock) {
        this.expectedIssuer = expectedIssuer;
        this.expectedAudience = expectedAudience;
        this.clockSkew = clockSkew != null ? clockSkew : Duration.ofSeconds(30);
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        Instant now = Instant.now(clock);

        // 1. Required 'exp' claim check (T-AUTH-06)
        Instant exp = jwt.getExpiresAt();
        if (exp == null) {
            return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                "invalid_token", "Token missing required 'exp' claim", null
            ));
        }

        // 2. Expiry check with clock skew (T-AUTH-02)
        if (now.isAfter(exp.plus(clockSkew))) {
            return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                "invalid_token", "Token has expired", null
            ));
        }

        // 3. 'nbf' check if present (T-AUTH-03)
        Instant nbf = jwt.getNotBefore();
        if (nbf != null && now.isBefore(nbf.minus(clockSkew))) {
            return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                "invalid_token", "Token is not yet valid ('nbf' claim in future)", null
            ));
        }

        // 4. Exact issuer check (T-AUTH-05)
        if (expectedIssuer != null && !expectedIssuer.isBlank()) {
            String issuer = jwt.getIssuer() != null ? jwt.getIssuer().toString() : null;
            if (!expectedIssuer.equals(issuer)) {
                return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                    "invalid_token", "Token issuer does not match expected value", null
                ));
            }
        }

        // 5. Audience check (T-AUTH-07)
        if (expectedAudience != null && !expectedAudience.isBlank()) {
            List<String> audience = jwt.getAudience();
            if (audience == null || !audience.contains(expectedAudience)) {
                return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                    "invalid_token", "Token audience does not contain expected value", null
                ));
            }
        }

        // 6. Strict UUID subject check (T-AUTH-08)
        String sub = jwt.getSubject();
        if (sub == null || sub.isBlank()) {
            return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                "invalid_token", "Token subject ('sub') claim is missing or empty", null
            ));
        }
        try {
            UUID.fromString(sub);
        } catch (IllegalArgumentException e) {
            return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                "invalid_token", "Token subject ('sub') must be a canonical UUID", null
            ));
        }

        // 7. Not anonymous check (T-AUTH-08)
        Object isAnonymousClaim = jwt.getClaims().get("is_anonymous");
        if (Boolean.TRUE.equals(isAnonymousClaim) || "true".equalsIgnoreCase(String.valueOf(isAnonymousClaim))) {
            return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                "invalid_token", "Anonymous sessions are not permitted", null
            ));
        }

        // 8. Role check if present (must not be unauthenticated)
        String role = jwt.getClaimAsString("role");
        if (role != null && !role.isBlank() && !"authenticated".equalsIgnoreCase(role)) {
            return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                "invalid_token", "User role '" + role + "' is not permitted", null
            ));
        }

        return OAuth2TokenValidatorResult.success();
    }
}
