package com.evcharging.platform.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fixed-window rate limit on login and registration, per client address and endpoint.
 * In-memory, so it is per application instance. The address is the direct peer address:
 * behind a reverse proxy, configure the proxy to pass the real client address instead.
 */
@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private final int maxRequests;
    private final long windowMillis;
    // key -> {windowStartMillis, count}
    private final ConcurrentHashMap<String, long[]> windows = new ConcurrentHashMap<>();

    public AuthRateLimitFilter(
            @Value("${app.auth.rate-limit.max-requests:10}") int maxRequests,
            @Value("${app.auth.rate-limit.window-seconds:60}") long windowSeconds) {
        this.maxRequests = maxRequests;
        this.windowMillis = windowSeconds * 1000;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return !("POST".equalsIgnoreCase(request.getMethod())
                && ("/api/v1/auth/login".equals(uri) || "/api/v1/auth/register".equals(uri)));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long now = System.currentTimeMillis();
        String key = request.getRemoteAddr() + " " + request.getRequestURI();

        long[] window = windows.compute(key, (k, old) -> {
            if (old == null || now - old[0] >= windowMillis) {
                return new long[] {now, 1};
            }
            old[1]++;
            return old;
        });

        if (windows.size() > 10_000) {
            windows.entrySet().removeIf(e -> now - e.getValue()[0] >= windowMillis);
        }

        if (window[1] > maxRequests) {
            long retryAfter = Math.max(1, (window[0] + windowMillis - now + 999) / 1000);
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(retryAfter));
            response.setContentType("application/problem+json");
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write("{\"status\":429,\"title\":\"Too Many Requests\","
                    + "\"detail\":\"Too many attempts, try again in " + retryAfter + " seconds\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}