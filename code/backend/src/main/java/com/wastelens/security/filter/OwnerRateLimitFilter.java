package com.wastelens.security.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Post-auth limiter filter:
 * Enforces per-owner rate limit (60 req/min) based on verified JWT subject (T-LIM-02).
 * Evicts idle entries to guarantee bounded memory (T-LIM-07).
 */
public class OwnerRateLimitFilter extends OncePerRequestFilter {

    public static final int OWNER_LIMIT_PER_MINUTE = 60;
    private static final long EVICTION_INTERVAL_MS = 5 * 60 * 1000L; // 5 minutes

    private final ConcurrentHashMap<UUID, OwnerWindow> limitMap = new ConcurrentHashMap<>();
    private volatile long lastEvictionTime = System.currentTimeMillis();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getRequestURI();

        if (path.startsWith("/api/")) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth instanceof JwtAuthenticationToken jwtAuth) {
                String sub = jwtAuth.getToken().getSubject();
                if (sub != null) {
                    try {
                        UUID ownerId = UUID.fromString(sub);
                        if (!checkOwnerRateLimit(ownerId)) {
                            sendRateLimitResponse(response);
                            return;
                        }
                    } catch (IllegalArgumentException ignored) {
                        // Handled by JWT validator
                    }
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean checkOwnerRateLimit(UUID ownerId) {
        long now = System.currentTimeMillis();
        evictStaleEntriesIfNeeded(now);

        OwnerWindow window = limitMap.compute(ownerId, (id, current) -> {
            if (current == null || (now - current.windowStart) >= 60_000) {
                return new OwnerWindow(now, new AtomicInteger(1));
            }
            current.counter.incrementAndGet();
            return current;
        });

        return window.counter.get() <= OWNER_LIMIT_PER_MINUTE;
    }

    private void evictStaleEntriesIfNeeded(long now) {
        if (now - lastEvictionTime > EVICTION_INTERVAL_MS) {
            lastEvictionTime = now;
            limitMap.entrySet().removeIf(entry -> (now - entry.getValue().windowStart) > EVICTION_INTERVAL_MS);
        }
    }

    private void sendRateLimitResponse(HttpServletResponse response) throws IOException {
        response.setStatus(429);
        response.setHeader("Retry-After", "60");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        String requestId = UUID.randomUUID().toString();
        String json = "{\"error\":{\"code\":\"RATE_LIMITED\",\"message\":\"Owner rate limit exceeded (60 req/min)\",\"requestId\":\"" + requestId + "\"}}";
        response.getWriter().write(json);
    }

    private static class OwnerWindow {
        final long windowStart;
        final AtomicInteger counter;

        OwnerWindow(long windowStart, AtomicInteger counter) {
            this.windowStart = windowStart;
            this.counter = counter;
        }
    }
}
