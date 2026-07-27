package com.clinic.booking.staff.service;

import com.clinic.booking.common.exception.AccountLockedException;
import com.clinic.booking.common.exception.InvalidCredentialsException;
import com.clinic.booking.config.BookingProperties;
import com.clinic.booking.staff.domain.StaffUser;
import com.clinic.booking.staff.dto.StaffSessionResponse;
import com.clinic.booking.staff.repository.StaffUserRepository;
import com.clinic.booking.staff.security.StaffUserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Implements POST/GET /staff/auth/{login,logout,session} (PRD §8.20). Login
 * establishes the Spring Security session context manually (rather than via
 * the framework's form-login filter) so it can share this codebase's
 * controller→service→typed-exception pattern (§10) for the lockout/anti-
 * enumeration rules in §15.9.
 */
@Service
public class StaffAuthService {

    // A real, valid BCrypt hash (cost 12) never matched against any real credential — used
    // solely so an unknown-username attempt performs the same BCrypt work as a known-username
    // wrong-password attempt, keeping response timing comparable (§15.9 anti-enumeration).
    private static final String DECOY_HASH = "$2a$12$VeshfJKteaRrSWdFiq/bOuYV.e73t7.XP8jUKYZtjVamMlHOmxbbG";

    private final StaffUserRepository staffUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final BookingProperties bookingProperties;
    private final SecurityContextRepository securityContextRepository;

    public StaffAuthService(
            StaffUserRepository staffUserRepository,
            PasswordEncoder passwordEncoder,
            BookingProperties bookingProperties,
            SecurityContextRepository securityContextRepository) {
        this.staffUserRepository = staffUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.bookingProperties = bookingProperties;
        this.securityContextRepository = securityContextRepository;
    }

    // recordFailedLogin's mutation must survive the InvalidCredentialsException thrown right
    // after it — @Transactional's default rollback-on-RuntimeException would otherwise discard
    // the incremented counter/lockout on every failed attempt, so the threshold is never reached.
    @Transactional(noRollbackFor = InvalidCredentialsException.class)
    public StaffSessionResponse login(
            String username, String password, HttpServletRequest request, HttpServletResponse response) {
        Optional<StaffUser> maybeUser = staffUserRepository.findByUsername(username);

        if (maybeUser.isEmpty()) {
            passwordEncoder.matches(password, DECOY_HASH);
            throw new InvalidCredentialsException();
        }

        StaffUser staffUser = maybeUser.get();

        // §7.12: a deactivated (is_active = FALSE) account can never authenticate, regardless of
        // credentials — treated identically to an unknown username (same exception, same decoy
        // comparison for comparable timing) so deactivation status isn't itself an oracle.
        if (!staffUser.isActive()) {
            passwordEncoder.matches(password, DECOY_HASH);
            throw new InvalidCredentialsException();
        }

        // §15.9: while locked, every attempt fails this way — even the correct password —
        // and this check happens before verifying the password at all.
        if (staffUser.isCurrentlyLocked()) {
            throw new AccountLockedException();
        }

        if (!passwordEncoder.matches(password, staffUser.getPasswordHash())) {
            staffUser.recordFailedLogin(
                    bookingProperties.getMaxFailedLoginAttempts(), bookingProperties.getLoginLockoutMinutes());
            throw new InvalidCredentialsException();
        }

        staffUser.recordSuccessfulLogin();
        establishSecurityContext(staffUser, request, response);

        Instant expiresAt = Instant.now().plus(bookingProperties.getSessionIdleTimeoutMinutes(), ChronoUnit.MINUTES);
        return toSessionResponse(staffUser, expiresAt);
    }

    public StaffSessionResponse currentSession(StaffUserPrincipal principal, HttpServletRequest request) {
        return new StaffSessionResponse(
                principal.getUsername(),
                principal.getRole().name(),
                principal.getProviderId(),
                computeSessionExpiresAt(request));
    }

    public void logout(HttpServletRequest request, HttpServletResponse response) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        securityContextRepository.saveContext(SecurityContextHolder.createEmptyContext(), request, response);
    }

    private void establishSecurityContext(StaffUser staffUser, HttpServletRequest request, HttpServletResponse response) {
        StaffUserPrincipal principal = new StaffUserPrincipal(staffUser);
        var authentication = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }

    private StaffSessionResponse toSessionResponse(StaffUser staffUser, Instant expiresAt) {
        return new StaffSessionResponse(staffUser.getUsername(), staffUser.getRole().name(),
                staffUser.getProviderId(), expiresAt);
    }

    /** §15.9: whichever of idle-timeout-from-now or absolute-timeout-from-session-creation is sooner. */
    private Instant computeSessionExpiresAt(HttpServletRequest request) {
        Instant idleDeadline = Instant.now().plus(bookingProperties.getSessionIdleTimeoutMinutes(), ChronoUnit.MINUTES);
        HttpSession session = request.getSession(false);
        if (session == null) {
            return idleDeadline;
        }
        Instant absoluteDeadline = Instant.ofEpochMilli(session.getCreationTime())
                .plus(Duration.ofHours(bookingProperties.getSessionAbsoluteTimeoutHours()));
        return idleDeadline.isBefore(absoluteDeadline) ? idleDeadline : absoluteDeadline;
    }
}
