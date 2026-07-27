package com.clinic.booking.staff.security;

import com.clinic.booking.config.BookingProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link AbsoluteSessionTimeoutFilter} (PRD §15.9:
 * SESSION_ABSOLUTE_TIMEOUT_HOURS = 8) — verifies the 8-hour enforcement logic
 * directly against a session's {@code creationTime}, without needing to wait
 * real wall-clock time.
 */
@ExtendWith(MockitoExtension.class)
class AbsoluteSessionTimeoutFilterTest {

    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private FilterChain filterChain;
    @Mock
    private HttpSession session;

    private final BookingProperties bookingProperties = new BookingProperties();
    private final AbsoluteSessionTimeoutFilter filter = new AbsoluteSessionTimeoutFilter(bookingProperties);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void invalidatesAndClearsContext_whenSessionIsOlderThanTheAbsoluteTimeout() throws Exception {
        when(request.getSession(false)).thenReturn(session);
        when(session.getCreationTime())
                .thenReturn(System.currentTimeMillis() - Duration.ofHours(8).plusMinutes(1).toMillis());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("staff", null));

        filter.doFilter(request, response, filterChain);

        verify(session).invalidate();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void leavesSessionIntact_whenYoungerThanTheAbsoluteTimeout() throws Exception {
        when(request.getSession(false)).thenReturn(session);
        when(session.getCreationTime()).thenReturn(System.currentTimeMillis() - Duration.ofMinutes(10).toMillis());

        filter.doFilter(request, response, filterChain);

        verify(session, never()).invalidate();
        verify(filterChain).doFilter(request, response);
    }
}
