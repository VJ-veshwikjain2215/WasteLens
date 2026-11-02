package com.wastelens.security.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Pre-auth limiter filter:
 * 1. Global rate limit (120 req/min) (T-LIM-06)
 * 2. Maximum body size limit 4 KiB (4096 bytes) (T-VAL-05, T-LIM-01)
 * 3. Strict Content-Type check (must be application/json for requests with body) (T-VAL-05)
 */
public class RequestLimitFilter extends OncePerRequestFilter {

    public static final int MAX_BODY_BYTES = 4096;
    public static final int GLOBAL_LIMIT_PER_MINUTE = 120;

    private final AtomicInteger globalCounter = new AtomicInteger(0);
    private volatile long windowStartTime = System.currentTimeMillis();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getRequestURI();

        // Limiter and body rules only apply to API endpoints
        if (path.startsWith("/api/")) {
            // 1. Check Global Rate Limit (120/min)
            if (!checkGlobalRateLimit()) {
                sendErrorResponse(response, 429, "RATE_LIMITED", "Global rate limit exceeded", true);
                return;
            }

            String method = request.getMethod().toUpperCase();
            boolean hasBodyMethod = "POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method);

            if (hasBodyMethod) {
                long contentLength = request.getContentLengthLong();
                String clHeader = request.getHeader("Content-Length");
                if (clHeader != null) {
                    try {
                        long parsed = Long.parseLong(clHeader.trim());
                        if (parsed > contentLength) {
                            contentLength = parsed;
                        }
                    } catch (NumberFormatException ignored) {}
                }

                // Check declared Content-Length
                if (contentLength > MAX_BODY_BYTES) {
                    sendErrorResponse(response, 413, "PAYLOAD_TOO_LARGE", "Request body exceeds maximum size of 4096 bytes", false);
                    return;
                }

                // Check Content-Type if request has body
                String contentType = request.getContentType();
                if (contentLength > 0 || request.getHeader("Transfer-Encoding") != null) {
                    if (contentType == null || !contentType.toLowerCase().startsWith(MediaType.APPLICATION_JSON_VALUE)) {
                        sendErrorResponse(response, 415, "UNSUPPORTED_MEDIA_TYPE", "Content-Type must be application/json", false);
                        return;
                    }
                }

                // Buffer and strictly enforce 4 KiB bound for actual body bytes
                byte[] bodyBytes = readAndCheckBody(request.getInputStream());
                if (bodyBytes == null) {
                    sendErrorResponse(response, 413, "PAYLOAD_TOO_LARGE", "Request body exceeds maximum size of 4096 bytes", false);
                    return;
                }

                HttpServletRequest wrappedRequest = new CachedBodyHttpServletRequest(request, bodyBytes);
                filterChain.doFilter(wrappedRequest, response);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private synchronized boolean checkGlobalRateLimit() {
        long now = System.currentTimeMillis();
        if (now - windowStartTime >= 60_000) {
            windowStartTime = now;
            globalCounter.set(0);
        }
        return globalCounter.incrementAndGet() <= GLOBAL_LIMIT_PER_MINUTE;
    }

    private byte[] readAndCheckBody(InputStream is) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int totalRead = 0;
        int n;
        while ((n = is.read(buffer)) != -1) {
            totalRead += n;
            if (totalRead > MAX_BODY_BYTES) {
                return null; // Exceeded limit
            }
            baos.write(buffer, 0, n);
        }
        return baos.toByteArray();
    }

    private void sendErrorResponse(HttpServletResponse response, int status, String code, String message, boolean retryAfter)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        if (retryAfter) {
            response.setHeader("Retry-After", "60");
        }
        String requestId = UUID.randomUUID().toString();
        String json = "{\"error\":{\"code\":\"" + code + "\",\"message\":\"" + message + "\",\"requestId\":\"" + requestId + "\"}}";
        response.getWriter().write(json);
    }

    // Request wrapper to allow reading the cached request body multiple times downstream
    private static class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {
        private final byte[] body;

        public CachedBodyHttpServletRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream bais = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public boolean isFinished() {
                    return bais.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener readListener) {}

                @Override
                public int read() {
                    return bais.read();
                }
            };
        }
    }
}
