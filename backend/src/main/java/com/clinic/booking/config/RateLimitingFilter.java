package com.clinic.booking.config;

import com.clinic.booking.common.exception.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Per-client-IP rate limiting (PRD §15.7/§12.5) — the single source of truth for both
 * "rate limiting" and "booking cooldown", deliberately not duplicated as separate numbers:
 * {@code GET /booking/availability} capped at {@code availabilityRateLimitPerMinute}
 * requests/minute; {@code POST /booking/holds} and {@code POST /booking/appointments}
 * combined capped at {@code bookingRateLimitPerTenMinutes} requests/10 minutes. Exceeding
 * either returns {@code 429 RATE_LIMITED} with a {@code Retry-After} header, using the
 * same {@link ErrorResponse} envelope every other error uses.
 *
 * <p>Fixed-window counters keyed by {@link HttpServletRequest#getRemoteAddr()}, kept
 * in-memory (this app is not yet behind a shared cache per §14's Scalability row, and the
 * PRD names no distributed rate-limit store) — a window resets in place once it expires,
 * so memory use is bounded by the number of distinct IPs seen within the current window,
 * not cumulative "unbounded map" over the process lifetime.
 *
 * <p>Deliberately does not match {@code POST /booking/appointments/{token}/reschedule}
 * (§8.19) — §15.7 names exactly these two endpoints, and no others.
 *
 * <p>{@link HttpServletRequest#getRemoteAddr()} reflects the real client address only if
 * upstream proxies/load balancers are trusted infrastructure that correctly set
 * {@code X-Forwarded-For} — see {@code server.forward-headers-strategy: framework} in
 * application.yml, which makes Spring resolve {@code getRemoteAddr()} from that header.
 * A deployment with an untrusted or missing proxy hop would see all traffic keyed under
 * one address; that is a deployment/infrastructure concern, not something this filter
 * can validate.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final String AVAILABILITY_PATH = "/api/v1/booking/availability";
    private static final String HOLDS_PATH = "/api/v1/booking/holds";
    private static final String APPOINTMENTS_PATH = "/api/v1/booking/appointments";

    private final ObjectMapper objectMapper;
    private final int availabilityLimitPerMinute;
    private final int bookingLimitPerTenMinutes;

    private final ConcurrentHashMap<String, Window> availabilityWindows = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Window> bookingWindows = new ConcurrentHashMap<>();

    public RateLimitingFilter(ObjectMapper objectMapper, BookingProperties bookingProperties) {
        this.objectMapper = objectMapper;
        this.availabilityLimitPerMinute = bookingProperties.getAvailabilityRateLimitPerMinute();
        this.bookingLimitPerTenMinutes = bookingProperties.getBookingRateLimitPerTenMinutes();
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String method = request.getMethod();
        String path = request.getRequestURI();

        ConcurrentHashMap<String, Window> windows;
        int limit;
        Duration windowDuration;
        if ("GET".equals(method) && AVAILABILITY_PATH.equals(path)) {
            windows = availabilityWindows;
            limit = availabilityLimitPerMinute;
            windowDuration = Duration.ofMinutes(1);
        } else if ("POST".equals(method) && (HOLDS_PATH.equals(path) || APPOINTMENTS_PATH.equals(path))) {
            windows = bookingWindows;
            limit = bookingLimitPerTenMinutes;
            windowDuration = Duration.ofMinutes(10);
        } else {
            filterChain.doFilter(request, response);
            return;
        }

        long retryAfterSeconds = tryConsume(windows, request.getRemoteAddr(), limit, windowDuration);
        if (retryAfterSeconds > 0) {
            writeRateLimited(request, response, retryAfterSeconds);
            return;
        }
        filterChain.doFilter(request, response);
    }

    /** Returns 0 if the request is within limit, or the seconds until the caller may retry. */
    private long tryConsume(
            ConcurrentHashMap<String, Window> windows, String clientIp, int limit, Duration windowDuration) {
        Instant now = Instant.now();
        Window window = windows.compute(clientIp, (ip, existing) -> {
            if (existing == null || existing.expiresAt.isBefore(now)) {
                return new Window(now.plus(windowDuration), new AtomicInteger(0));
            }
            return existing;
        });
        int count = window.count.incrementAndGet();
        if (count > limit) {
            return Math.max(1, Duration.between(now, window.expiresAt).getSeconds());
        }
        return 0;
    }

    private void writeRateLimited(HttpServletRequest request, HttpServletResponse response, long retryAfterSeconds)
            throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ErrorResponse body = new ErrorResponse(
                Instant.now(), HttpStatus.TOO_MANY_REQUESTS.value(), "RATE_LIMITED",
                "Too many requests. Please try again later.", request.getRequestURI(), List.of());
        byte[] bytes = objectMapper.writeValueAsBytes(body);
        response.setContentLength(bytes.length);
        response.getOutputStream().write(bytes);
        response.getOutputStream().flush();
    }

    private static final class Window {
        private final Instant expiresAt;
        private final AtomicInteger count;

        private Window(Instant expiresAt, AtomicInteger count) {
            this.expiresAt = expiresAt;
            this.count = count;
        }
    }
}
