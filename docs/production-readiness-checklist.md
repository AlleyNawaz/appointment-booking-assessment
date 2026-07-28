# Production Readiness Checklist — Milestone 14 (Go-Live Gate)

This is a verification artifact, not source code — Milestone 14's charter is "no new
features," so every item below is either a check against what prior milestones already built,
or an explicitly flagged gap for a product/architecture decision. Nothing in this document
was implemented as part of closing it out.

## Infrastructure (deployment-level, not application code — PRD §15.5/§14)

- [ ] **TLS in transit** — mandatory at the infrastructure layer for every environment,
      including local development proxying (§15.5). Not application-configurable; confirm at
      the load balancer/reverse-proxy layer before go-live.
- [ ] **MySQL InnoDB tablespace encryption-at-rest** — a deployment-level requirement (§15.5),
      not an application concern. Confirm the target MySQL instance has this enabled before
      go-live; the application makes no assumption about it either way.
- [x] **Connection pool sized at 20 per instance** — `spring.datasource.hikari.maximum-pool-size:
      20` in `application.yml` (§14 Scalability: "read-heavy on availability, not
      connection-heavy," tuned down from Spring Boot's HikariCP default of 100).
- [ ] **Horizontal scalability verified across the real multi-instance topology** — the
      application is stateless for the patient flow (§14) and every scheduled job is
      ShedLock-guarded (§12.14/§7.13) so exactly one instance executes a given tick, but this
      has only been exercised against a single local instance in this repository/sandbox — see
      `perf/load-test-plan.js` and `docs/flag-rollout-runbook.md` for the staging dry run this
      needs before the production flag flip.
- [x] **`server.forward-headers-strategy: framework`** — configured (Milestone 13) so
      `RateLimitingFilter` and request logging see the real client address behind a load
      balancer; the LB/reverse proxy is trusted infrastructure responsible for setting
      `X-Forwarded-For` correctly.

## Observability (§14)

- [x] Structured JSON logs (`logback-spring.xml`).
- [x] `X-Request-Id` generated/echoed and propagated to every log line via MDC
      (`RequestIdFilter`).
- [x] PII masking in logs — email/phone masked in both `%msg` and stack traces
      (`LogPiiMasker`/`LogMaskingConverter`/`LogMaskingExceptionConverter`).
- [x] Micrometer → Prometheus metrics: booking success rate (`booking.appointments.created`),
      hold-expiry rate (`booking.holds.expired`), availability query latency
      (`booking.availability.latency`), flag-blocked-request count
      (`booking.feature_flag.blocked`) — `management.prometheus.metrics.export.enabled: true`.
- [ ] `X-Request-Id` → `appointment_audit_log` propagation — **known, documented gap**, see
      below.

## §18 Acceptance Criteria / §19 Edge Cases / §13 Error Catalog / §12.7 Lifecycle — automated coverage

- [x] All 12 Acceptance Criteria (§18) have a dedicated automated test — see
      `backend/src/test/java/com/clinic/booking/AcceptanceCriteriaSuiteIT.java`'s traceability
      matrix (11 pre-existed across Milestones 5/6/8/11/12; AC-6 was added by this milestone).
- [x] All 54 edge cases (§19) have an identifiable automated test or an explicit N/A/manual
      justification — see `backend/src/test/java/com/clinic/booking/EdgeCaseSuiteIT.java`'s
      traceability matrix (14 net-new tests closed genuine test-only gaps; the rest were already
      covered elsewhere or are not backend-testable, e.g. §19 #5/#6/#10 are frontend/JS-disabled
      concerns).
- [x] Every §13 error-catalog row with an implemented code path has at least one test confirming
      the correct typed exception is thrown — the eight rows found untested during this pass
      (`CLINIC_CLOSED_DAY`, `APPOINTMENT_TYPE_CODE_EXISTS`, `PROVIDER_EMAIL_EXISTS`,
      `INVALID_TIMEZONE`, `INVALID_TIME_RANGE`, `HOLIDAY_DATE_EXISTS`, `FEATURE_FLAG_NOT_FOUND`,
      `DUPLICATE_APPOINTMENT`) were closed in `EdgeCaseSuiteIT.java`. These, and several
      pre-existing rows (`LEAD_TIME_VIOLATION`, `PROVIDER_UNAVAILABLE`, `SLOT_ALREADY_BOOKED`,
      `PATIENT_DAILY_LIMIT_EXCEEDED`, `IDEMPOTENCY_KEY_REUSED_MISMATCH`, `STALE_VERSION`,
      `APPOINTMENT_NOT_RESCHEDULABLE`, `APPOINTMENT_STATE_CHANGED`), are verified at the
      service/validator layer only (`assertThatThrownBy(...).isInstanceOf(...)`), not by a real
      HTTP call asserting the JSON `errorCode` field or numeric status — no HTTP-level IT class
      exists for `POST /booking/appointments`/`.../reschedule`. `GlobalExceptionHandler`'s
      exception→status/errorCode mapping is a deterministic 1:1 table, verified by code review,
      which is what completes the chain from typed exception to the exact HTTP response. Three
      rows have **no implemented code path at all** — see "Known gaps" below.
- [x] Every §12.7 lifecycle transition with an implementation is exercised, including the
      invalid-transition rejection (§19 #24) — `PENDING → REJECTED` and plain
      `CONFIRMED → COMPLETED` (the two transitions with no direct happy-path test before this
      milestone) were closed in `EdgeCaseSuiteIT.java`. The one exception is staff-initiated
      `CONFIRMED → CANCELLED` "any time" (§12.6), which has no service method or endpoint at all
      and therefore cannot be exercised by any test — see "Known gaps" below.
- [ ] Load test confirms P95 targets under realistic concurrent load — plan ready
      (`perf/load-test-plan.js`), not yet run against the real multi-instance topology (no such
      environment exists in this repository/sandbox). Run as part of the staging step in
      `docs/flag-rollout-runbook.md`.

## §17 AI Agent Execution Constraints — sign-off

- [x] **Rule 5 (controllers free of business logic)** — spot-checked every controller class
      under `backend/src/main/java/com/clinic/booking/**/controller/`; the only `if` statements
      found are DTO-shape/null checks (e.g. `StaffAppointmentController`'s "reason required" on
      reject), never a business-rule evaluation. No new controller code was added by this
      milestone.
- [x] **Rule 7 (tests covering every §13 row and every §12.7 transition)** — see the section
      above; closed by `AcceptanceCriteriaSuiteIT`/`EdgeCaseSuiteIT`, except the three §13 rows with
      no implementation (below).
- [x] **Rule 2 (never invent an endpoint/field/status/error code not listed in §7/§8/§11/§13)**
      — no new endpoints, fields, or error codes were introduced by this milestone; every gap
      found below is reported per rule 2's own instruction ("stop and flag the gap rather than
      inventing a resolution"), not resolved unilaterally.

## Known gaps found during this verification pass (not fixed — require a product/architecture decision)

Milestone 14 is a verification gate, not a feature milestone. Four of these (#2-#5) were
discovered while building the traceability suites above; #1 (`X-Request-Id`) was originally
discovered and documented during Milestone 13 and is carried forward here, not newly found by
this pass. None is addressed by this milestone, per its own charter ("No new features") and
§17 rule 2.

1. **`X-Request-Id` is not propagated into `appointment_audit_log`.** §14's Observability row
   names this, but §7.9 defines the table's columns exhaustively with no request-id column, and
   §17 rule 2 forbids adding one without a migration. Documented in
   `RequestIdFilter.java`'s class javadoc (Milestone 13). Requires: approve a migration adding a
   `request_id` column, or amend §14 to drop this clause.
2. **§19 #33 (Idempotency-Key must be a valid UUID) is not implemented.**
   `AppointmentController`/`RescheduleController` only null-check the header; no format
   validation exists. Requires: a decision on whether to add the validation (a small,
   contained change) or amend §19 #33.
3. **`SERVICE_UNAVAILABLE` (503) and `REQUEST_TIMEOUT` (504) have no `GlobalExceptionHandler`
   mapping and no code path that would produce them**, despite being named in §13's error
   catalog. Requires: a decision on which specific exceptions (e.g. connection-pool exhaustion,
   a downstream timeout) should map to these, since §13 names the codes but not their triggers.
4. **A generic `Exception → 500 INTERNAL_SERVER_ERROR` fallback handler does not exist** in
   `GlobalExceptionHandler` — an unclassified exception currently falls through to Spring Boot's
   default error response shape, not the PRD §8 error envelope. Requires: a decision on whether
   to add a catch-all handler (uncontroversial in isolation, but is a change to a completed
   milestone's cross-cutting file, so flagged rather than done silently here).
5. **Staff-initiated `CONFIRMED → CANCELLED` "any time" (§12.6) has no service method or
   endpoint.** Only patient self-service cancellation (§8.8, with its 4-hour cutoff) exists;
   §12.6's second sentence ("staff, who can cancel via the staff console with no cutoff") names
   a capability that was never built. Requires: a decision on whether this is in scope for a
   future milestone or was a documentation-only aspiration.

## Staged flag-rollout

See `docs/flag-rollout-runbook.md`.
