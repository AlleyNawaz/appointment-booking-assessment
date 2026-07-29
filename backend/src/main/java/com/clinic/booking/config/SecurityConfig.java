package com.clinic.booking.config;

import com.clinic.booking.staff.security.AbsoluteSessionTimeoutFilter;
import com.clinic.booking.staff.security.StaffSecurityErrorHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * Spring Security foundation (PRD §10/§15.4/§15.8/§15.9): session cookies +
 * CSRF for the staff console, BCrypt password hashing, a read-only
 * {@link RoleHierarchy} for future {@code @PreAuthorize} read checks
 * (Milestone 9+ — no method is annotated with it yet), and CORS. The
 * anonymous {@code /api/v1/booking/**} surface stays fully public and
 * CSRF-exempt, exactly as it was before this milestone (§6's "never gated"
 * staff-console note doesn't apply the other way — patient booking was never
 * gated by authentication either).
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder(BookingProperties bookingProperties) {
        return new BCryptPasswordEncoder(bookingProperties.getBcryptStrength());
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    /** §10: "RoleHierarchy bean is configured for read authorities only (ROLE_SYSADMIN > ROLE_ADMIN > ROLE_STAFF)". */
    @Bean
    public RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.fromHierarchy("ROLE_SYSADMIN > ROLE_ADMIN > ROLE_STAFF");
    }

    @Bean
    static DefaultMethodSecurityExpressionHandler methodSecurityExpressionHandler(RoleHierarchy roleHierarchy) {
        DefaultMethodSecurityExpressionHandler handler = new DefaultMethodSecurityExpressionHandler();
        handler.setRoleHierarchy(roleHierarchy);
        return handler;
    }

    @Bean
    public AbsoluteSessionTimeoutFilter absoluteSessionTimeoutFilter(BookingProperties bookingProperties) {
        return new AbsoluteSessionTimeoutFilter(bookingProperties);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            CorsConfigurationSource corsConfigurationSource,
            SecurityContextRepository securityContextRepository,
            AbsoluteSessionTimeoutFilter absoluteSessionTimeoutFilter,
            ObjectMapper objectMapper) throws Exception {
        StaffSecurityErrorHandler staffSecurityErrorHandler = new StaffSecurityErrorHandler(objectMapper);
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf((CsrfConfigurer<HttpSecurity> csrf) -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                        // §15.4: the anonymous booking API is stateless (no cookies) — CSRF doesn't apply to it.
                        .ignoringRequestMatchers("/api/v1/booking/**", "/api/v1/staff/auth/login"))
                .securityContext(sc -> sc.securityContextRepository(securityContextRepository))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/").permitAll()
                        .requestMatchers("/api/v1/booking/**").permitAll()
                        .requestMatchers("/api/v1/staff/auth/login").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness").permitAll()
                        .requestMatchers("/actuator/**").hasRole("SYSADMIN")
                        .anyRequest().authenticated())
                // §8.11: 401 is reserved exclusively for /staff/auth/login credential failures —
                // no other endpoint in this API ever returns it. An unauthenticated request to a
                // protected staff endpoint (no session at all), and a CSRF-rejected request, both
                // fall under the 403 "staff role insufficient" bucket instead — and per §8's
                // "every error response uses the envelope" rule (line 531), both still need the
                // standard ErrorResponse body, not a bare status code.
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(staffSecurityErrorHandler)
                        .accessDeniedHandler(staffSecurityErrorHandler))
                .addFilterBefore(absoluteSessionTimeoutFilter, SecurityContextHolderFilter.class)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable);
        return http.build();
    }
}
