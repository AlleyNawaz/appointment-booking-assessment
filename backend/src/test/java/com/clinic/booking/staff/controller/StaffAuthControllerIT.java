package com.clinic.booking.staff.controller;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for staff authentication (PRD §8.20/§15.9) against the
 * real local MySQL schema — same pattern established in Milestones 1-7 (no
 * Testcontainers). Covers the Milestone 8 validation checklist: AC-12,
 * anti-enumeration, lockout self-expiry, the §19 #53 re-lock behavior, CORS
 * preflight independent of the feature flag (§19 #54), and the
 * {@code chk_provider_role_pairing} DB constraint.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class StaffAuthControllerIT {

    private static final String LOGIN_URL = "/api/v1/staff/auth/login";
    private static final String LOGOUT_URL = "/api/v1/staff/auth/logout";
    private static final String SESSION_URL = "/api/v1/staff/auth/session";
    private static final String KNOWN_PASSWORD = "CorrectHorse123";
    private static final String KNOWN_HASH = "$2a$12$iZOajtQvoYA9vG2I/cG0POWBVtiyaczx3rSq7P1M5OlX5njcVqPVq";
    private static final String FLAG_SQL = "UPDATE feature_flags SET is_enabled = ? WHERE flag_name = 'enable_online_booking'";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String username;

    @BeforeEach
    void seedStaffUser() {
        // The JDK's default HttpURLConnection-based request factory fails with
        // "cannot retry due to server authentication, in streaming mode" on a POST
        // that receives a 401 response over a kept-alive connection. Apache
        // HttpClient5 doesn't have that limitation.
        restTemplate.getRestTemplate().setRequestFactory(new HttpComponentsClientHttpRequestFactory());

        username = "staff-it-" + UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO staff_users (username, password_hash, role, is_active) VALUES (?, ?, 'ROLE_STAFF', TRUE)",
                username, KNOWN_HASH);
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM staff_users WHERE username = ?", username);
    }

    @Test
    void unknownUsername_andWrongPassword_returnIdenticalInvalidCredentials() {
        ResponseEntity<Map> unknown =
                restTemplate.postForEntity(LOGIN_URL, loginBody("no-such-user-" + UUID.randomUUID(), "whatever12345"), Map.class);
        ResponseEntity<Map> wrongPassword =
                restTemplate.postForEntity(LOGIN_URL, loginBody(username, "totallyWrong123"), Map.class);

        assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(wrongPassword.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(unknown.getBody().get("errorCode")).isEqualTo("INVALID_CREDENTIALS");
        assertThat(unknown.getBody().get("errorCode")).isEqualTo(wrongPassword.getBody().get("errorCode"));
        assertThat(unknown.getBody().get("message")).isEqualTo(wrongPassword.getBody().get("message"));
    }

    @Test
    void ac12_fiveFailedLogins_thenCorrectPassword_returns403AccountLocked() {
        for (int i = 0; i < 5; i++) {
            ResponseEntity<Map> response =
                    restTemplate.postForEntity(LOGIN_URL, loginBody(username, "wrongAttempt" + i), Map.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        ResponseEntity<Map> sixth = restTemplate.postForEntity(LOGIN_URL, loginBody(username, KNOWN_PASSWORD), Map.class);

        assertThat(sixth.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(sixth.getBody()).containsEntry("errorCode", "ACCOUNT_LOCKED");
    }

    @Test
    void lockout_selfExpiresAtReadTime_noScheduledJobInvolved() {
        jdbcTemplate.update("UPDATE staff_users SET failed_login_attempts = 5, locked_until = ? WHERE username = ?",
                Timestamp.from(Instant.now().minusSeconds(60)), username);

        ResponseEntity<Map> response = restTemplate.postForEntity(LOGIN_URL, loginBody(username, KNOWN_PASSWORD), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void wrongAttemptImmediatelyAfterLockoutExpiry_reLocks_insteadOfFreshCount() {
        // §19 #53: failed_login_attempts only resets on success, never merely because
        // locked_until elapsed — so a single wrong attempt right after expiry re-locks.
        jdbcTemplate.update("UPDATE staff_users SET failed_login_attempts = 5, locked_until = ? WHERE username = ?",
                Timestamp.from(Instant.now().minusSeconds(60)), username);

        ResponseEntity<Map> stillWrong =
                restTemplate.postForEntity(LOGIN_URL, loginBody(username, "stillWrongPassword"), Map.class);
        assertThat(stillWrong.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<Map> nextAttempt = restTemplate.postForEntity(LOGIN_URL, loginBody(username, KNOWN_PASSWORD), Map.class);

        assertThat(nextAttempt.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(nextAttempt.getBody()).containsEntry("errorCode", "ACCOUNT_LOCKED");
    }

    @Test
    void deactivatedAccount_cannotAuthenticate_evenWithCorrectPassword() {
        jdbcTemplate.update("UPDATE staff_users SET is_active = FALSE WHERE username = ?", username);

        ResponseEntity<Map> response = restTemplate.postForEntity(LOGIN_URL, loginBody(username, KNOWN_PASSWORD), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).containsEntry("errorCode", "INVALID_CREDENTIALS");
        assertThat(response.getHeaders().get(HttpHeaders.SET_COOKIE)).isNull();
    }

    @Test
    void successfulLogin_returnsSessionShape_andSessionEndpointWorksWithTheCookie() {
        ResponseEntity<Map> loginResponse =
                restTemplate.postForEntity(LOGIN_URL, loginBody(username, KNOWN_PASSWORD), Map.class);

        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(loginResponse.getBody()).containsEntry("username", username).containsEntry("role", "ROLE_STAFF");
        assertThat(loginResponse.getBody()).containsKey("sessionExpiresAt");

        String sessionCookie = extractCookie(loginResponse, "JSESSIONID");
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, sessionCookie);

        ResponseEntity<Map> sessionResponse =
                restTemplate.exchange(SESSION_URL, HttpMethod.GET, new HttpEntity<>(headers), Map.class);

        assertThat(sessionResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(sessionResponse.getBody()).containsEntry("username", username).containsEntry("role", "ROLE_STAFF");
    }

    @Test
    void login_setsSessionCookieWithRequiredAttributes() {
        // §8.20: "sets an HttpOnly, Secure, SameSite=Strict session cookie".
        ResponseEntity<Map> loginResponse =
                restTemplate.postForEntity(LOGIN_URL, loginBody(username, KNOWN_PASSWORD), Map.class);

        String rawCookieHeader = loginResponse.getHeaders().get(HttpHeaders.SET_COOKIE).stream()
                .filter(c -> c.startsWith("JSESSIONID="))
                .findFirst()
                .orElseThrow(() -> new AssertionError("JSESSIONID cookie not present in response"));

        assertThat(rawCookieHeader).containsIgnoringCase("HttpOnly");
        assertThat(rawCookieHeader).containsIgnoringCase("Secure");
        assertThat(rawCookieHeader).containsIgnoringCase("SameSite=Strict");
    }

    @Test
    void sessionExpiresAt_reflectsTheThirtyMinuteIdleTimeout() {
        // §15.9: SESSION_IDLE_TIMEOUT_MINUTES = 30, sooner than the 8h absolute timeout this
        // early in a session's life, so it's the deadline computeSessionExpiresAt must return.
        ResponseEntity<Map> loginResponse =
                restTemplate.postForEntity(LOGIN_URL, loginBody(username, KNOWN_PASSWORD), Map.class);

        Instant expiresAt = Instant.parse((String) loginResponse.getBody().get("sessionExpiresAt"));

        assertThat(Duration.between(Instant.now(), expiresAt).toMinutes()).isBetween(29L, 30L);
    }

    @Test
    void sessionEndpoint_withoutCookie_returns403WithTheStandardErrorEnvelope() {
        // §8.11: 401 is reserved exclusively for /staff/auth/login credential failures — no
        // other endpoint in this API ever returns it, so a missing session here is 403, not 401.
        // §8 (line 531): "every error response uses the envelope" — including this one, even
        // though it's rejected in the security filter chain before any controller runs.
        ResponseEntity<Map> response = restTemplate.getForEntity(SESSION_URL, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).containsEntry("status", 403).containsEntry("errorCode", "FORBIDDEN");
        assertThat(response.getBody()).containsKeys("timestamp", "message", "path", "fieldErrors");
    }

    @Test
    void logoutEndpoint_withoutSession_returns403WithTheStandardErrorEnvelope() {
        // A valid CSRF token but no session at all — isolates the "not authenticated"
        // (AuthenticationEntryPoint) path from the CSRF-rejection (AccessDeniedHandler) path
        // exercised separately below.
        ResponseEntity<Void> csrfProbe = restTemplate.postForEntity(LOGOUT_URL, null, Void.class);
        String csrfCookie = extractCookie(csrfProbe, "XSRF-TOKEN");
        String csrfValue = csrfCookie.split("=", 2)[1].split(";")[0];

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, csrfCookie);
        headers.add("X-XSRF-TOKEN", csrfValue);
        ResponseEntity<Map> response =
                restTemplate.exchange(LOGOUT_URL, HttpMethod.POST, new HttpEntity<>(headers), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).containsEntry("status", 403).containsEntry("errorCode", "FORBIDDEN");
        assertThat(response.getBody()).containsKeys("timestamp", "message", "path", "fieldErrors");
    }

    @Test
    void csrfRejectedRequest_returns403WithTheStandardErrorEnvelope() {
        ResponseEntity<Map> response = restTemplate.postForEntity(LOGOUT_URL, null, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).containsEntry("status", 403).containsEntry("errorCode", "FORBIDDEN");
        assertThat(response.getBody()).containsKeys("timestamp", "message", "path", "fieldErrors");
    }

    @Test
    void logout_invalidatesTheSession() {
        // The login endpoint is CSRF-ignored (no token exists before authenticating), so it
        // never sets an XSRF-TOKEN cookie, and CsrfFilter only resolves/saves a token for
        // state-changing (non-GET) requests. Provoke a rejected POST first purely to harvest
        // the cookie the CsrfFilter issues on any non-ignored, non-safe request.
        ResponseEntity<Void> csrfProbe = restTemplate.postForEntity(LOGOUT_URL, null, Void.class);
        String csrfCookie = extractCookie(csrfProbe, "XSRF-TOKEN");
        String csrfValue = csrfCookie.split("=", 2)[1].split(";")[0];

        ResponseEntity<Map> loginResponse =
                restTemplate.postForEntity(LOGIN_URL, loginBody(username, KNOWN_PASSWORD), Map.class);
        String sessionCookie = extractCookie(loginResponse, "JSESSIONID");

        HttpHeaders logoutHeaders = new HttpHeaders();
        logoutHeaders.add(HttpHeaders.COOKIE, sessionCookie + "; " + csrfCookie);
        logoutHeaders.add("X-XSRF-TOKEN", csrfValue);
        ResponseEntity<Void> logoutResponse =
                restTemplate.exchange(LOGOUT_URL, HttpMethod.POST, new HttpEntity<>(logoutHeaders), Void.class);
        assertThat(logoutResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        HttpHeaders sessionHeaders = new HttpHeaders();
        sessionHeaders.add(HttpHeaders.COOKIE, sessionCookie);
        ResponseEntity<Map> sessionAfterLogout =
                restTemplate.exchange(SESSION_URL, HttpMethod.GET, new HttpEntity<>(sessionHeaders), Map.class);
        assertThat(sessionAfterLogout.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void corsPreflight_succeedsRegardlessOfFeatureFlagState() {
        jdbcTemplate.update(FLAG_SQL, false);
        HttpHeaders headers = new HttpHeaders();
        headers.add("Origin", "http://localhost:4200");
        headers.add("Access-Control-Request-Method", "POST");

        ResponseEntity<String> response =
                restTemplate.exchange("/api/v1/booking/holds", HttpMethod.OPTIONS, new HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst("Access-Control-Allow-Origin")).isEqualTo("http://localhost:4200");
    }

    @Test
    void chkProviderRolePairing_rejectsRoleProviderRowWithNullProviderId() {
        String rejectedUsername = "provider-no-id-" + UUID.randomUUID();
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO staff_users (username, password_hash, role, provider_id, is_active) "
                        + "VALUES (?, ?, 'ROLE_PROVIDER', NULL, TRUE)",
                rejectedUsername, KNOWN_HASH))
                // MySQL 8.0.16+ CHECK constraint violations (error code 3819) aren't mapped by
                // Spring's default SQLErrorCodeSQLExceptionTranslator to the more specific
                // DataIntegrityViolationException — it falls back to UncategorizedSQLException.
                // Both are DataAccessException, which is what actually matters: the DB rejected it.
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("chk_provider_role_pairing");
    }

    private static Map<String, String> loginBody(String username, String password) {
        return Map.of("username", username, "password", password);
    }

    private static String extractCookie(ResponseEntity<?> response, String cookieName) {
        return response.getHeaders().get(HttpHeaders.SET_COOKIE).stream()
                .filter(c -> c.startsWith(cookieName + "="))
                .findFirst()
                .map(c -> Arrays.stream(c.split(";")).findFirst().orElseThrow())
                .orElseThrow(() -> new AssertionError(cookieName + " cookie not present in response"));
    }
}
