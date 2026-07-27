Implementation Plan — Online Appointment Booking System

Basis: PRD v2.2.0 (Technical Review Board approved) · Method: Trunk-Based Development — every milestone is a short-lived branch merged to main, every merge produces deployable software, enable_online_booking stays FALSE until Milestone 7 so incomplete patient-facing work is never user-visible in production.

Milestone index: 1 Foundation & Core Schema · 2 Feature Flag & Reference Reads · 3 Availability Engine · 4 Slot Holds & Locking Infra · 5 Booking Creation (Core) · 6 Patient Self-Service (Lookup/Cancel) · 7 Booking Wizard Frontend · 8 Staff Authentication · 9 Staff Appointment Console · 10 Staff Scheduling & Admin Console · 11 Audit Log & Scheduled Jobs · 12 Reschedule (Atomic) · 13 Security & Cross-Cutting Hardening · 14 Final Verification & Go-Live

---
Milestone 1 — Project Foundation & Core Reference Schema

Objective. Stand up both codebases and the database migration pipeline; land the reference tables (providers, appointment_types, provider_appointment_types, feature_flags) that almost every later milestone depends on. Nothing patient-facing exists yet — this milestone is deployable because it's inert (empty apps + a schema with no traffic against it).

Deliverables.
- Spring Boot 3.x project skeleton (Java 17), package structure per §10 layering, Flyway wired to run on startup.
- Angular 17+ project skeleton (standalone components), folder structure per §9.
- CI pipeline: build + test on every PR, Flyway migration dry-run against a disposable MySQL instance.
- First four Flyway migrations with seed data (§7.1–7.3, §7.9's feature_flags half).
- Health-check endpoint (/actuator/health or equivalent) for deploy verification.

Files expected.
- backend/pom.xml (or build.gradle), backend/src/main/resources/application.yml
- backend/src/main/resources/db/migration/V1__create_providers.sql
- backend/src/main/resources/db/migration/V2__create_appointment_types.sql
- backend/src/main/resources/db/migration/V3__create_provider_appointment_types.sql
- backend/src/main/resources/db/migration/V4__create_feature_flags.sql
- backend/src/main/java/com/clinic/booking/config/BookingProperties.java (@ConfigurationProperties(prefix="booking") shell, §10)
- backend/src/main/java/com/clinic/booking/BookingApplication.java
- frontend/angular.json, frontend/src/app/app.config.ts, frontend/src/app/core/, frontend/src/app/shared/ (empty scaffolds)
- .github/workflows/ci.yml (or equivalent)

Dependencies. None — this is the root milestone.

Validation checklist.
- [ ] mvn/gradle build and ng build both succeed in CI.
- [ ] Flyway migrations apply cleanly against a fresh MySQL 8 instance with zero manual DDL.
- [ ] Seed data matches §7.2's table exactly (4 appointment types, correct duration/buffer/requires_approval values).
- [ ] feature_flags seeded with enable_online_booking = FALSE (§7.9).
- [ ] Health endpoint returns 200 with a live DB connection.
- [ ] No business logic anywhere yet (nothing to violate §10's controller-purity rule).

Suggested Git commit messages.
- chore: scaffold Spring Boot backend project structure
- chore: scaffold Angular frontend project structure
- feat(db): add providers and appointment_types migrations with seed data
- feat(db): add provider_appointment_types and feature_flags migrations
- chore(ci): add build and migration-dry-run pipeline

---
Milestone 2 — Feature Flag Service & Reference-Data Reads

Objective. Implement the feature-flag mechanism exactly per §6/§10 and expose the first three gated/non-gated read endpoints. This is the first milestone with real HTTP surface, and it's safe to deploy because the flag defaults OFF.

Deliverables.
- FeatureFlagService backed by Caffeine (expireAfterWrite = 10s, §10).
- @FeatureGate annotation + AOP aspect (§6/§10) — structural flag enforcement, not inline ifs.
- GET /api/v1/booking/config (never gated, §8.1).
- GET /api/v1/booking/appointment-types (gated, §8.2).
- GET /api/v1/booking/providers?appointmentTypeId= (gated, §8.3).
- GlobalExceptionHandler (@RestControllerAdvice) with the error envelope (§8) and FeatureDisabledException → 403 mapping.

Files expected.
- backend/src/main/java/com/clinic/booking/config/FeatureFlagService.java
- backend/src/main/java/com/clinic/booking/config/FeatureGate.java (annotation)
- backend/src/main/java/com/clinic/booking/config/FeatureGateAspect.java
- backend/src/main/java/com/clinic/booking/booking/controller/BookingConfigController.java
- backend/src/main/java/com/clinic/booking/booking/controller/AppointmentTypeController.java
- backend/src/main/java/com/clinic/booking/booking/controller/ProviderController.java
- backend/src/main/java/com/clinic/booking/booking/service/{AppointmentTypeService,ProviderService}.java
- backend/src/main/java/com/clinic/booking/booking/repository/{AppointmentTypeRepository,ProviderRepository}.java
- backend/src/main/java/com/clinic/booking/booking/dto/{AppointmentTypeResponse,ProviderResponse}.java
- backend/src/main/java/com/clinic/booking/common/exception/{GlobalExceptionHandler,FeatureDisabledException}.java
- backend/src/test/java/.../FeatureFlagServiceTest.java
- backend/src/test/java/.../BookingConfigControllerIT.java

Dependencies. Milestone 1 (schema + seed data).

Validation checklist.
- [ ] GET /config returns {"enabled": false} with the seeded flag, never gated (AC-5 half).
- [ ] GET /appointment-types and GET /providers return 403 FEATURE_DISABLED while the flag is off — verifies gating ordering (flag check first, per §6's "Backend" row).
- [ ] Flipping the DB row and waiting >10s flips the cached result (cache TTL test).
- [ ] GET /providers without appointmentTypeId returns 400 VALIDATION_ERROR (§8.3).
- [ ] Flag-blocked requests log at INFO, not WARN/ERROR (§6).
- [ ] Entities never serialize directly — only DTOs cross the controller boundary (§10).

Suggested Git commit messages.
- feat(flag): implement FeatureFlagService with Caffeine cache
- feat(flag): add @FeatureGate AOP aspect for endpoint gating
- feat(api): add booking config and reference-data read endpoints
- feat(error): add GlobalExceptionHandler with error envelope
- test(flag): cover cache TTL and flag-gating ordering

---
Milestone 3 — Availability Computation Engine

Objective. Implement the slot-computation engine and its supporting schema (working hours, unavailability, holidays) behind GET /booking/availability.

Deliverables.
- provider_availability_rules, provider_unavailability, clinic_holidays migrations + default seed (§7.4/§12.1).
- AvailabilityService.computeAvailableSlots(...) — 15-minute grid, subtracting WORKING∖BREAK, existing appointments (±buffer), active holds (holds table doesn't exist until M4, so this milestone computes without hold-subtraction and M4 adds it), unavailability,
holidays.
- GET /api/v1/booking/availability (gated, §8.4).

Files expected.
- backend/src/main/resources/db/migration/V5__create_provider_availability_rules.sql
- backend/src/main/resources/db/migration/V6__create_provider_unavailability.sql
- backend/src/main/resources/db/migration/V7__create_clinic_holidays.sql
- backend/src/main/java/com/clinic/booking/booking/service/AvailabilityService.java
- backend/src/main/java/com/clinic/booking/booking/controller/AvailabilityController.java
- backend/src/main/java/com/clinic/booking/booking/repository/{ProviderAvailabilityRuleRepository,ProviderUnavailabilityRepository,ClinicHolidayRepository}.java
- backend/src/main/java/com/clinic/booking/booking/dto/AvailabilityResponse.java
- backend/src/main/java/com/clinic/booking/common/exception/{InvalidAppointmentDateException,BookingWindowExceededException,ClinicClosedDayException}.java
- backend/src/test/java/.../AvailabilityServiceTest.java (unit — DST, weekend, holiday, partial-fit slot cases)

Dependencies. Milestones 1–2.

Validation checklist.
- [ ] Sunday with no override returns 200 {slots: []}, not an error (§19 #35).
- [ ] A slot is only offered if duration+buffer fits before the next blocking interval (§19 #36/#37).
- [ ] date in the past → 400 INVALID_APPOINTMENT_DATE; >90 days out → 400 BOOKING_WINDOW_EXCEEDED (§8.4).
- [ ] Clinic holiday blocks every provider unconditionally (§11.5); provider-specific Saturday override works (§12.1).
- [ ] DST transition day shifts UTC offset automatically with no schema change (§19 #4).
- [ ] Non-overlapping WORKING/BREAK validation exists at the service layer (staff-console enforcement lands in Milestone k function is written here for reuse, §19 #38).

Suggested Git commit messages.
- feat(db): add availability rules, unavailability, and holiday migrations
- feat(availability): implement slot-computation engine
- feat(api): add GET availability endpoint
- test(availability): cover DST, holiday, and partial-fit edge cases

---
Milestone 4 — Slot Holds & Distributed Locking Infrastructure

Objective. Implement the soft, UX-layer lock (§12.10) and the ShedLock infrastructure that later scheduled jobs (M11) will depend on, so it's proven out early on the simplest job (the hold reaper).

Deliverables.
- slot_holds migration including appointment_type_id (§7.8 — Round-3 fix).
- shedlock migration (§7.13).
- POST /api/v1/booking/holds (§8.5), including feature-flag gating.
- HoldService, hold-reaper @Scheduled job with @SchedulerLock (§12.14).
- AvailabilityService updated to subtract active holds.

Files expected.
- backend/src/main/resources/db/migration/V8__create_slot_holds.sql
- backend/src/main/resources/db/migration/V9__create_shedlock.sql
- backend/src/main/java/com/clinic/booking/booking/service/HoldService.java
- backend/src/main/java/com/clinic/booking/booking/controller/HoldController.java
- backend/src/main/java/com/clinic/booking/booking/repository/SlotHoldRepository.java
- backend/src/main/java/com/clinic/booking/booking/dto/{HoldRequest,HoldResponse}.java
- backend/src/main/java/com/clinic/booking/booking/job/HoldReaperJob.java
- backend/src/main/java/com/clinic/booking/common/exception/SlotAlreadyBookedException.java
- backend/src/test/java/.../HoldServiceIT.java (Testcontainers — concurrent hold acquisition)
- backend/src/test/java/.../HoldReaperJobIT.java

Dependencies. Milestones 1–3.

Validation checklist.
- [ ] Concurrent hold requests for the same slot: exactly one 201, the other 409 SLOT_ALREADY_BOOKED (insert-and-catch on uq_hold_slot, not check-then-act, §10).
- [ ] Hold TTL is exactly HOLD_DURATION_MINUTES = 5 (§8.5).
- [ ] Reaper job deletes only rows where expires_at < NOW(); re-running it twice in a row is a no-op the second time (idempotent, §14).
- [ ] With two app instances running locally (or simulated), only one executes a given reaper tick (ShedLock proof-of-con
- [ ] Availability query correctly excludes actively-held slots (§8.4 union).

Suggested Git commit messages.
- feat(db): add slot_holds and shedlock migrations
- feat(holds): implement POST /booking/holds with insert-and-catch locking
- feat(jobs): add ShedLock-protected hold-reaper scheduled job
- feat(availability): subtract active holds from computed slots
- test(holds): verify concurrent hold race resolves to exactly one winner

---
Milestone 5 — Booking Creation (Core Transactional Flow)

Objective. Implement the single most important endpoint in the system: POST /booking/appointments, with idempotency, the active_slot_key hard invariant, and every §11 validation rule. This is the largest milestone.

Deliverables.
- appointments migration (§7.7, including request_body_hash and appointment_type_id-from-hold resolution).
- BookingService.createAppointment(...): hold lookup (resolves appointmentTypeId, §7.8/§8.6), §11 validation chain, idempotency check/replay, insert-and-catch on active_slot_key, requires_approval branch to CONFIRMED/PENDING.
- POST /api/v1/booking/appointments (§8.6).
- All §11 validators as distinct typed exceptions (§10).
- Async, best-effort confirmation/pending email side-effect (logged on failure, never a transaction participant, §19 #41)

Files expected.
- backend/src/main/resources/db/migration/V10__create_appointments.sql
- backend/src/main/java/com/clinic/booking/booking/service/BookingService.java
- backend/src/main/java/com/clinic/booking/booking/controller/AppointmentController.java (POST only in this milestone)
- backend/src/main/java/com/clinic/booking/booking/repository/AppointmentRepository.java
- backend/src/main/java/com/clinic/booking/booking/dto/{CreateAppointmentRequest,AppointmentResponse}.java
- backend/src/main/java/com/clinic/booking/booking/validation/{LeadTimeValidator,BookingWindowValidator,ClinicClosedDayVar,DuplicateAppointmentValidator}.java
- backend/src/main/java/com/clinic/booking/common/exception/{LeadTimeViolationException,DuplicateAppointmentException,PatientDailyLimitExceededException,IdempotencyKeyReusedMismatchException,SlotHoldExpiredException,ProviderUnavailableException}.java
- backend/src/main/java/com/clinic/booking/common/util/RequestHasher.java (§8.6 canonicalization)
- backend/src/main/java/com/clinic/booking/notification/EmailNotificationService.java (async, best-effort)
- backend/src/test/java/.../BookingServiceIT.java (Testcontainers — AC-1, AC-2, AC-3, AC-4, AC-8, AC-9)

Dependencies. Milestones 1–4.

Validation checklist.
- [ ] AC-1: GENERAL_CONSULT 48h out → 201 CONFIRMED.
- [ ] AC-2: NEW_PATIENT → 201 PENDING, and the slot is excluded from availability while PENDING (§8.4's CONFIRMED/PENDING
- [ ] AC-3: same-day 10h-lead booking → 400 LEAD_TIME_VIOLATION.
- [ ] AC-4: two concurrent submissions for the same slot with two valid holds → exactly one 201, other 409 SLOT_ALREADY_Bunique index, not app-level pre-check.
- [ ] AC-8: 4th same-day booking beyond the 3-across-providers limit → 409 PATIENT_DAILY_LIMIT_EXCEEDED.
- [ ] AC-9: replayed Idempotency-Key with identical body → original 201 returned, no second row; mismatched body → 409 IDTCH (§8.6 hash algorithm exactly as specified).
- [ ] appointmentTypeId is never accepted in the request body — confirmed resolved from the hold row only.
- [ ] notes HTML-stripped server-side regardless of client input (§15.2).
- [ ] Email failure never rolls back the booking (§19 #41).

Suggested Git commit messages.
- feat(db): add appointments core table migration
- feat(booking): implement request-body-hash idempotency mechanism
- feat(booking): implement §11 validation chain as typed exceptions
- feat(booking): implement POST /booking/appointments with insert-and-catch active_slot_key
- feat(notification): add async best-effort confirmation email
- test(booking): cover AC-1, AC-2, AC-3, AC-4, AC-8, AC-9

---
Milestone 6 — Patient Self-Service (Lookup & Cancellation)

Objective. Implement the never-gated token-based endpoints so a patient can manage a booking that already exists, indepen

Deliverables.
- GET /api/v1/booking/appointments/{confirmationToken} (§8.7).
- DELETE /api/v1/booking/appointments/{confirmationToken} (§8.8), writing changed_by = 'PATIENT_SELF_SERVICE' (§7.9).
- Identical not-found/malformed-token response (§15.3 anti-oracle).

Files expected.
- backend/src/main/java/com/clinic/booking/booking/controller/AppointmentLookupController.java
- backend/src/main/java/com/clinic/booking/booking/service/AppointmentLookupService.java (extends AppointmentController/BookingService as appropriate)
- backend/src/main/java/com/clinic/booking/booking/dto/AppointmentDetailResponse.java
- backend/src/main/java/com/clinic/booking/common/exception/{AppointmentNotFoundException,CancellationWindowExpiredException}.java
- backend/src/main/java/com/clinic/booking/audit/AuditLogWriter.java (first use of appointment_audit_log — table itself l targets a temporary lightweight log or is stubbed until M9's migration; note: re-sequence if audit table is needed earlier — see Dependencies)
- backend/src/test/java/.../AppointmentLookupControllerIT.java (AC-5, AC-7)

Dependencies. Milestone 5. Schema note: if appointment_audit_log (originally slated for M9) is needed to satisfy §12.7's dit row" for this milestone's CANCELLED transition, pull its migration forward into this milestone instead of M9 — theaudit table has no dependency on staff_users and can safely land here.

Validation checklist.
- [ ] AC-5: flag OFF → GET /availability is 403, but GET /appointments/{validToken} is still 200 (never gated).
- [ ] AC-7: appointment 2h out → DELETE returns 409 CANCELLATION_WINDOW_EXPIRED with clinic phone number in the message.
- [ ] Malformed token and valid-but-unknown token return byte-identical 404 APPOINTMENT_NOT_FOUND responses (§8.7/§15.3).
- [ ] Successful cancellation frees active_slot_key immediately — rebooking the identical slot is permitted right after (§19 #23).
- [ ] Cancellation writes one audit row with changed_by = 'PATIENT_SELF_SERVICE', reason = patient-supplied text or NULL.

Suggested Git commit messages.
- feat(db): add appointment_audit_log migration
- feat(booking): implement GET appointment-by-token lookup
- feat(booking): implement DELETE cancellation with cutoff enforcement
- feat(audit): write PATIENT_SELF_SERVICE audit rows on cancellation
- test(booking): cover AC-5 and AC-7

---
Milestone 7 — Booking Wizard Frontend (First End-to-End Patient Journey)

Objective. Build the complete Angular patient flow against Milestones 2–6's API surface. This is the first milestone whera staging environment produces a fully usable product — the natural point to validate the whole patient journey end-to-endbefore touching staff features.

Deliverables.
- BookingStateService (signals) + BookingApiService.
- Five wizard pages (type/provider/schedule/contact/review) + Appointment Lookup page.
- feature-flag.guard.ts, http-error.interceptor.ts, error-messages.const.ts.
- Shared LoadingSpinnerComponent/ErrorBannerComponent/EmptyStateComponent/AsyncStateWrapperComponent.
- Accessibility pass on the wizard (grid roles, aria-live, aria-disabled reasons per §9).

Files expected.
- frontend/src/app/booking/booking.routes.ts
- frontend/src/app/booking/state/booking-state.service.ts
- frontend/src/app/booking/services/booking-api.service.ts
- frontend/src/app/booking/models/{appointment,provider,appointment-type,slot-hold}.model.ts
- frontend/src/app/booking/pages/{type-selection,provider-selection,schedule-selection,contact-details,review-confirm}/*.ts
- frontend/src/app/appointment-lookup/appointment-lookup.page.ts
- frontend/src/app/core/guards/feature-flag.guard.ts
- frontend/src/app/core/interceptors/http-error.interceptor.ts
- frontend/src/app/core/error-messages.const.ts
- frontend/src/app/shared/components/{loading-spinner,error-banner,empty-state,async-state-wrapper}/*.ts
- frontend/e2e/booking-flow.spec.ts (E2E, if Cypress/Playwright is in the stack)

Dependencies. Milestones 1–6.

Validation checklist.
- [ ] Full manual/E2E run of the golden path: type → provider → date/slot → hold → contact → submit → confirmation, both d =true branches.
- [ ] Flag-off state renders the "unavailable" page with clinic phone number, and no booking components exist in the DOM (§6).
- [ ] Hold-expiry mid-flow surfaces the exact copy from §3 and returns the user to slot selection with contact info retai
- [ ] Browser refresh at any wizard step restores state from sessionStorage if the hold hasn't expired (§19 #5).
- [ ] Calendar grid is keyboard-navigable with aria-label stating why a date is disabled (§9).
- [ ] 360px viewport usable; slot grid reflows to single column below 480px (§14).
- [ ] Staging flag flipped ON → a real booking can be completed by a human tester end-to-end.

Suggested Git commit messages.
- feat(frontend): add BookingStateService and BookingApiService
- feat(frontend): implement type, provider, and schedule selection pages
- feat(frontend): implement contact details and review/confirm pages
- feat(frontend): implement appointment lookup and cancellation UI
- feat(frontend): add feature-flag guard and global error interceptor
- test(e2e): add end-to-end booking flow coverage

---
Milestone 8 — Staff Authentication

Objective. Stand up the authenticated staff surface's foundation: staff_users, Spring Security, BCrypt, lockout, sessions, CORS. Nothing in the staff console works yet beyond login — but login itself is a complete, deployable, testable feature.

Deliverables.
- staff_users migration (§7.12, including lockout columns and the chk_provider_role_pairing constraint).
- Spring Security config: session cookies, CSRF, BCryptPasswordEncoder (cost 12), read-only RoleHierarchy bean (§10's resolved model).
- POST /staff/auth/login, POST /staff/auth/logout, GET /staff/auth/session (§8.20).
- Lockout logic (MAX_FAILED_LOGIN_ATTEMPTS=5, LOGIN_LOCKOUT_MINUTES=15), session timeouts (30 min idle / 8h absolute).
- Centralized CorsConfigurationSource bean (§15.8).
- Angular staff login page + session.guard.ts/role.guard.ts + StaffSessionService.

Files expected.
- backend/src/main/resources/db/migration/V11__create_staff_users.sql
- backend/src/main/java/com/clinic/booking/config/SecurityConfig.java
- backend/src/main/java/com/clinic/booking/config/CorsConfig.java
- backend/src/main/java/com/clinic/booking/staff/service/StaffAuthService.java
- backend/src/main/java/com/clinic/booking/staff/controller/StaffAuthController.java
- backend/src/main/java/com/clinic/booking/staff/repository/StaffUserRepository.java
- backend/src/main/java/com/clinic/booking/staff/security/StaffUserDetailsService.java
- backend/src/main/java/com/clinic/booking/common/exception/{InvalidCredentialsException,AccountLockedException}.java
- frontend/src/app/staff/auth/{staff-auth.service.ts,staff-session.service.ts,session.guard.ts,role.guard.ts}
- frontend/src/app/staff/auth/login/login.page.ts
- backend/src/test/java/.../StaffAuthControllerIT.java (AC-12)

Dependencies. Milestone 1 (needs providers for the provider_id FK); independent of Milestones 2–7.

Validation checklist.
- [ ] AC-12: 5 failed logins then a 6th correct-password attempt within the window → 403 ACCOUNT_LOCKED.
- [ ] Unknown username and wrong password return byte-identical 401 INVALID_CREDENTIALS (§15.9 anti-enumeration).
- [ ] Lockout self-expires at read time via locked_until, no scheduled job involved.
- [ ] A wrong attempt immediately after lockout expiry re-locks (§19 #53), not a fresh 5-attempt count.
- [ ] Idle session expires at 30 minutes, absolute at 8 hours.
- [ ] CORS preflight succeeds regardless of flag state (§19 #54) — verify with a cross-origin dev-server request.
- [ ] chk_provider_role_pairing rejects a ROLE_PROVIDER row with provider_id = NULL at the DB layer.

Suggested Git commit messages.
- feat(db): add staff_users migration with lockout columns
- feat(security): configure Spring Security with BCrypt and read-only RoleHierarchy
- feat(security): add CORS configuration bean
- feat(staff): implement login/logout/session endpoints with lockout policy
- feat(frontend): add staff login page and session/role guards
- test(staff): cover AC-12 and credential-enumeration resistance

---
Milestone 9 — Staff Appointment Console

Objective. Let Receptionist/Provider/Admin manage the appointment lifecycle: list, approve, reject, complete — the core j authenticated surface.

Deliverables.
- GET /staff/appointments (§8.9, pagination/filter/sort contract).
- POST /staff/appointments/{id}/{approve,reject,complete} (§8.10) with If-Match optimistic locking, roles per §2's resolvADMIN/owning ROLE_PROVIDER, never ROLE_SYSADMIN).
- Audit-row writes for every transition (staff username as changed_by).
- Angular staff appointments list + detail/action page.

Files expected.
- backend/src/main/java/com/clinic/booking/staff/controller/StaffAppointmentController.java
- backend/src/main/java/com/clinic/booking/staff/service/AppointmentLifecycleService.java
- backend/src/main/java/com/clinic/booking/staff/dto/{AppointmentPageResponse,ApproveRequest,RejectRequest}.java
- backend/src/main/java/com/clinic/booking/common/exception/StaleVersionException.java
- frontend/src/app/staff/appointments/{appointment-list.page.ts,appointment-detail.page.ts}
- frontend/src/app/staff/appointments/appointment-api.service.ts
- backend/src/test/java/.../AppointmentLifecycleServiceIT.java (AC-10 groundwork, §19 #15/#24/#25/#49)

Dependencies. Milestones 5, 6, 8.

Validation checklist.
- [ ] page=999 beyond data → 200 empty content, not 404 (§19 #42); size=10000 clamped to 100 (§19 #43).
- [ ] Two staff members transitioning the same appointment concurrently → second gets 409 STALE_VERSION (§19 #15).
- [ ] A ROLE_PROVIDER cannot approve another provider's appointment → 403 (§19 #49).
- [ ] ROLE_SYSADMIN attempting approve/reject/complete → 403 (Round-3 role-hierarchy fix verification).
- [ ] Invalid transition (e.g., CANCELLED → COMPLETED) rejected with a conflict error, no diagram edge honored (§19 #24).
- [ ] Every transition writes exactly one audit row with the staff's username.

Suggested Git commit messages.
- feat(staff): implement paginated appointment list endpoint
- feat(staff): implement approve/reject/complete with optimistic locking
- feat(audit): write staff-username audit rows on lifecycle transitions
- feat(frontend): add staff appointment list and detail pages
- test(staff): cover STALE_VERSION, provider scoping, and invalid transitions

---
Milestone 10 — Staff Scheduling & Admin Console

Objective. Deliver every Admin/Receptionist configuration capability from §2 that wasn't yet built: availability rules, unavailability, holidays, appointment-type/provider CRUD, and the feature-flag toggle itself — the endpoint that ultimately turns the whole product on
in production.

Deliverables.
- CRUD endpoints §8.12–§8.17, with the Round-3 role model applied literally (ROLE_ADMIN only on writes, except §8.17's named ROLE_SYSADMIN exception).
- Non-overlapping availability-rule validation at the service layer (§19 #38).
- affectedAppointments read-back on unavailability creation (§7.5 needs_attention surface).
- Angular admin/availability pages + nav visibility per §4.1's matrix.

Files expected.
- backend/src/main/java/com/clinic/booking/staff/controller/{AppointmentTypeAdminController,ProviderAdminController,AvailabilityRuleController,UnavailabilityController,ClinicHolidayController,FeatureFlagAdminController}.java
- backend/src/main/java/com/clinic/booking/staff/service/{AppointmentTypeAdminService,ProviderAdminService,AvailabilityRurvice,ClinicHolidayService}.java
- backend/src/main/java/com/clinic/booking/common/exception/{AppointmentTypeCodeExistsException,ProviderEmailExistsException,InvalidTimezoneException,InvalidAppointmentTypeReferenceException,AvailabilityRuleOverlapException,InvalidTimeRangeException,HolidayDateExistsExc
eption,FeatureFlagNotFoundException}.java
- frontend/src/app/staff/availability/{hours.page.ts,unavailability.page.ts,holidays.page.ts}
- frontend/src/app/staff/admin/{appointment-types.page.ts,providers.page.ts,settings.page.ts}
- backend/src/test/java/.../{AvailabilityRuleServiceIT,ProviderAdminControllerIT,FeatureFlagAdminControllerIT}.java

Dependencies. Milestones 3, 5, 8, 9.

Validation checklist.
- [ ] Overlapping WORKING/BREAK rule save attempt → 409 AVAILABILITY_RULE_OVERLAP, rejected before persistence (§19 #38).
- [ ] ROLE_ADMIN can CRUD types/providers/rules/holidays; ROLE_SYSADMIN gets 403 on every one of those writes but 200 on the matching reads.
- [ ] PUT /feature-flags/enable_online_booking succeeds for both ROLE_ADMIN and ROLE_SYSADMIN — the one deliberate except
- [ ] Flag toggle evicts the writing instance's cache synchronously (own next request sees the change immediately, §8.17).
- [ ] Creating provider_unavailability overlapping an existing CONFIRMED appointment returns it in affectedAppointments a (§19 #20/§12.3).
- [ ] Nav items hidden/shown exactly per §4.1's matrix for each of the four roles (manual QA pass, one login per role).

Suggested Git commit messages.
- feat(staff): implement appointment-type and provider CRUD endpoints
- feat(staff): implement availability-rule CRUD with overlap validation
- feat(staff): implement unavailability CRUD with affected-appointments read-back
- feat(staff): implement clinic-holiday CRUD and feature-flag toggle endpoint
- feat(frontend): add staff availability and admin console pages
- test(staff): cover role-write restrictions and availability overlap rejection

---
Milestone 11 — Audit Log & Scheduled Jobs

Objective. Close the operational loop: SysAdmin's read-only audit visibility, and the two remaining automated lifecycle tD), both ShedLock-protected per §12.14.

Deliverables.
- GET /staff/audit-log (§8.18, ROLE_SYSADMIN only).
- Approval-timeout expiry job (PENDING → EXPIRED after 24h, §12.11).
- Nightly missed-marker job (CONFIRMED → MISSED, WHERE status='CONFIRMED' guard against the completed-just-before race, §19 #25).
- Angular audit-log viewer page.

Files expected.
- backend/src/main/java/com/clinic/booking/staff/controller/AuditLogController.java
- backend/src/main/java/com/clinic/booking/staff/service/AuditLogService.java
- backend/src/main/java/com/clinic/booking/booking/job/{ApprovalTimeoutJob,MissedAppointmentJob}.java
- frontend/src/app/staff/audit-log/audit-log.page.ts
- backend/src/test/java/.../{ApprovalTimeoutJobIT,MissedAppointmentJobIT,AuditLogControllerIT}.java (AC-10)

Dependencies. Milestones 4 (ShedLock), 5, 8, 9.

Validation checklist.
- [ ] AC-10: PENDING with no staff action for 24h → nightly job flips it to EXPIRED, audit row changed_by='SYSTEM'.
- [ ] CONFIRMED past end_datetime + 24h never manually completed → MISSED; already-COMPLETED rows are excluded from the sweep (§19 #25).
- [ ] Running either job twice back-to-back is a no-op the second time (idempotent).
- [ ] Only ROLE_SYSADMIN can call GET /audit-log; every other role gets 403.
- [ ] Correction window: staff can flip MISSED → COMPLETED within 7 days but not beyond (§12.7/§19 #26).

Suggested Git commit messages.
- feat(jobs): implement approval-timeout expiry job with ShedLock
- feat(jobs): implement nightly missed-appointment job with ShedLock
- feat(staff): implement read-only audit-log retrieval endpoint
- feat(frontend): add audit-log viewer page
- test(jobs): cover AC-10 and idempotent re-run safety

---
Milestone 12 — Reschedule (Atomic Endpoint)

Objective. Implement the single most concurrency-sensitive endpoint added in the Round-2/Round-3 revisions: atomic cancelllback guarantees.

Deliverables.
- POST /booking/appointments/{token}/reschedule (§8.19), added as the sixth gated endpoint (§6).
- AppointmentService.reschedule(...) implementing the exact 10-step transaction from §12.13, including the daily-limit/du9 #52) and APPOINTMENT_STATE_CHANGED handling.
- Angular reschedule action on the Appointment Lookup page.

Files expected.
- backend/src/main/java/com/clinic/booking/booking/service/RescheduleService.java
- backend/src/main/java/com/clinic/booking/booking/controller/RescheduleController.java (or added to AppointmentController)
- backend/src/main/java/com/clinic/booking/booking/dto/{RescheduleRequest,RescheduleResponse}.java
- backend/src/main/java/com/clinic/booking/common/exception/{AppointmentNotReschedulableException,AppointmentStateChangedException}.java
- frontend/src/app/appointment-lookup/reschedule-action.component.ts
- backend/src/test/java/.../RescheduleServiceIT.java (AC-11, §19 #51/#52, §19 #39/#40)

Dependencies. Milestones 4, 5, 6, 9 (needs the version-based optimistic-lock path proven in M9).

Validation checklist.
- [ ] AC-11: two patients race for the same new slot → loser gets 409 SLOT_ALREADY_BOOKED, and their original appointmentED and untouched (full-transaction rollback, not partial).
- [ ] A staff cancellation racing the reschedule transaction → 409 APPOINTMENT_STATE_CHANGED, not STALE_VERSION (§19 #51).
- [ ] Rescheduling one's only appointment of the day doesn't trip the patient's own daily-limit/duplicate check (§19 #52)
- [ ] Reschedule into a <24h slot → LEAD_TIME_VIOLATION; reschedule of an appointment inside the 4h cutoff → CANCELLATION_WINDOW_EXPIRED (§19 #39/#40).
- [ ] PENDING appointments correctly rejected with 409 APPOINTMENT_NOT_RESCHEDULABLE.
- [ ] Two audit rows written per successful reschedule, both changed_by='PATIENT_SELF_SERVICE', distinguishable via reason.
- [ ] Endpoint returns 403 FEATURE_DISABLED when the flag is off (confirms the §6 six-endpoint gating list).

Suggested Git commit messages.
- feat(booking): implement atomic reschedule transaction
- feat(booking): add self-exclusion to daily-limit/duplicate checks for reschedule
- feat(api): add reschedule endpoint to feature-flag gating list
- feat(frontend): add reschedule action to appointment lookup page
- test(booking): cover AC-11 and reschedule race/rollback scenarios

---
Milestone 13 — Security & Cross-Cutting Hardening

Objective. Close out every non-functional requirement that spans the whole system rather than one endpoint: rate limitingadiness, accessibility completeness, observability.

Deliverables.
- Per-IP rate limiting on /availability (10/min) and /holds+/appointments combined (5/10min), 429 RATE_LIMITED + Retry-After (§15.7).
- Logging PatternLayout converter masking email/phone in all log output, including stack traces (§15.6).
- i18n resource-file externalization of every UI string, even though only en-US ships (§14).
- Micrometer → Prometheus metrics: booking success rate, hold-expiry rate, availability latency, flag-blocked count (§14)
- X-Request-Id propagation from SPA through every log line and into appointment_audit_log where relevant.
- Full accessibility audit against WCAG 2.1 AA across all screens (not just the wizard from M7).

Files expected.
- backend/src/main/java/com/clinic/booking/config/RateLimitingFilter.java (or Bucket4j config)
- backend/src/main/java/com/clinic/booking/config/LogMaskingConverter.java
- backend/src/main/java/com/clinic/booking/config/MetricsConfig.java
- backend/src/main/java/com/clinic/booking/config/RequestIdFilter.java
- frontend/src/assets/i18n/en-US.json
- frontend/src/app/core/interceptors/request-id.interceptor.ts
- backend/src/test/java/.../RateLimitingFilterIT.java
- Accessibility audit report (manual/automated, e.g. axe-core run) — not a source file, a checklist artifact

Dependencies. All prior milestones (this is a horizontal pass across the whole surface).

Validation checklist.
- [ ] 11th /availability request within a minute from one IP → 429 with Retry-After (§19 #32).
- [ ] Log output for a validation failure never contains a full email/phone, including in the stack trace (§15.6).
- [ ] Every string on every screen (patient + staff) is sourced from an i18n resource file, none hardcoded.
- [ ] X-Request-Id appears on a request, its logs, and — where the request caused a status transition — the corresponding
- [ ] Prometheus scrape endpoint exposes all four named metrics.
- [ ] Automated accessibility scan passes with zero WCAG 2.1 AA violations on every screen in §4's inventory.

Suggested Git commit messages.
- feat(security): implement per-IP rate limiting on availability and booking endpoints
- feat(logging): add PII-masking log pattern converter
- feat(i18n): externalize all UI strings to en-US resource file
- feat(observability): add Micrometer metrics and request-id propagation
- test(security): cover rate-limit thresholds and PII masking
- chore(a11y): remediate WCAG 2.1 AA findings across staff console

---
Milestone 14 — Final Verification & Go-Live

Objective. Full-system regression against every acceptance criterion and edge case in the PRD, performance validation, and the production flag-rollout runbook. No new features — this milestone is the release gate.

Deliverables.
- Complete automated test suite covering all 12 ACs (§18) and all 54 edge cases (§19) with explicit test-to-row traceabil
- Load/performance test report against §14's P95 targets (<300ms availability, <500ms appointment creation, measured at the load balancer).
- Production readiness checklist: TLS enforced, MySQL tablespace encryption-at-rest confirmed, connection pool sized at 2ied across the real multi-instance topology.
- Staged flag-rollout runbook: enable in staging → smoke test → enable in production → monitor flag-blocked-request and booking-success-rate metrics for the first rollout window → documented rollback (flip flag off) procedure.
- Final sign-off checklist mapped 1:1 to §17's AI Agent Execution Constraints (controller purity, no invented endpoints/c, every §12.7 transition tested).

Files expected.
- backend/src/test/java/.../AcceptanceCriteriaSuite.java (or a suite tagged/organized by AC-1…AC-12)
- backend/src/test/java/.../EdgeCaseSuite.java (organized by §19 category, referencing edge-case numbers in test names/co
- perf/load-test-plan.{jmx,js} (k6/Gatling/JMeter — whichever tool the team standardizes on)
- docs/production-readiness-checklist.md
- docs/flag-rollout-runbook.md

Dependencies. All prior milestones.

Validation checklist.
- [ ] All 12 acceptance criteria (§18) pass as automated tests.
- [ ] All 54 edge cases (§19) have an identifiable corresponding test or manual-verification record.
- [ ] Every row in §13's error catalog has at least one test asserting its exact HTTP status + errorCode.
- [ ] Every transition in §12.7's state diagram is exercised by a test, including the invalid-transition rejections (§19 #24).
- [ ] Load test confirms P95 targets under realistic concurrent load.
- [ ] Code review confirms zero business-logic if statements in any controller class (§17 rule 5).
- [ ] Staging flag-flip rollout completed and monitored before the production flip.
- [ ] Rollback runbook executed once in staging as a dry run (flip off, confirm existing bookings unaffected per §6's Database row).

Suggested Git commit messages.
- test: add acceptance-criteria suite covering AC-1 through AC-12
- test: add edge-case suite covering §19 categories 1-54
- chore(perf): add load-test plan and baseline results
- docs: add production readiness checklist and flag-rollout runbook
- chore(release): tag v1.0.0 for production flag rollout
