package com.wastelens.security.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Rejects requests to /api/** that attempt to provide or spoof an owner selector
 * via query parameters or headers (T-ISO-02).
 * Ownership must strictly be derived from the verified JWT subject.
 */
public class OwnerParamSecurityFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        if (path.startsWith("/api/")) {
            // Check query parameters
            if (request.getParameter("ownerId") != null 
                    || request.getParameter("owner_id") != null 
                    || request.getParameter("sub") != null) {
                sendErrorResponse(response);
                return;
            }

            // Check headers
            if (request.getHeader("X-Owner-Id") != null 
                    || request.getHeader("ownerId") != null 
                    || request.getHeader("owner_id") != null) {
                sendErrorResponse(response);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private void sendErrorResponse(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        String requestId = UUID.randomUUID().toString();
        String json = "{\"error\":{\"code\":\"INVALID_INPUT\",\"message\":\"Owner specification is forbidden; ownership is strictly derived from token\",\"requestId\":\"" + requestId + "\"}}";
        response.getWriter().write(json);
    }
}
