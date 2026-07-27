package com.clinic.booking.staff.security;

import com.clinic.booking.config.BookingProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

/**
 * §15.9: SESSION_ABSOLUTE_TIMEOUT_HOURS — a session older than this is
 * invalidated regardless of activity, forcing re-authentication at least
 * once per shift. Deliberately a plain object wired directly into the
 * security filter chain (not {@code @Component}) so Spring Boot's servlet
 * filter auto-registration doesn't ALSO register it as a second, generic
 * filter outside the security chain, running it twice per request.
 */
public class AbsoluteSessionTimeoutFilter extends OncePerRequestFilter {

    private final BookingProperties bookingProperties;

    public AbsoluteSessionTimeoutFilter(BookingProperties bookingProperties) {
        this.bookingProperties = bookingProperties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session != null) {
            long ageMillis = System.currentTimeMillis() - session.getCreationTime();
            long maxAgeMillis = Duration.ofHours(bookingProperties.getSessionAbsoluteTimeoutHours()).toMillis();
            if (ageMillis > maxAgeMillis) {
                session.invalidate();
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }
}
