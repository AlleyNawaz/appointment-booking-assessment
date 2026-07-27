package com.clinic.booking.staff.security;

import com.clinic.booking.common.exception.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

/**
 * Writes the standard PRD §8 {@link ErrorResponse} envelope for both flavors
 * of 403 security rejection on the staff surface — an unauthenticated
 * request (no session at all) and a CSRF-rejected request. §8.11 reserves
 * 401 exclusively for {@code /staff/auth/login} credential failures, so
 * neither case may ever produce one; both fall under the 403 "staff role
 * insufficient" bucket instead. These rejections happen in the security
 * filter chain, before any controller/{@code @RestControllerAdvice} runs, so
 * they need their own envelope-writing here rather than relying on
 * {@code GlobalExceptionHandler} — but PRD line 531 ("every error response
 * uses the envelope") applies to them just the same.
 */
public class StaffSecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public StaffSecurityErrorHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        write(request, response);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
            throws IOException {
        write(request, response);
    }

    private void write(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ErrorResponse body = new ErrorResponse(Instant.now(), HttpStatus.FORBIDDEN.value(), "FORBIDDEN",
                "Access to this resource is forbidden.", request.getRequestURI(), List.of());
        objectMapper.writeValue(response.getWriter(), body);
    }
}
