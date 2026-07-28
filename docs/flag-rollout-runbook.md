# Feature Flag Rollout Runbook — `enable_online_booking`

Milestone 14 deliverable (PRD §6/§14). Covers the staged rollout of the one flag in this
system, `enable_online_booking`, seeded `FALSE` (§7.9), and its rollback.

## Background

`enable_online_booking` gates exactly six endpoints (§6): `GET /booking/appointment-types`,
`GET /booking/providers`, `GET /booking/availability`, `POST /booking/holds`,
`POST /booking/appointments`, `POST /booking/appointments/{token}/reschedule`. Every other
endpoint — including `GET`/`DELETE /booking/appointments/{token}` and the entire staff
console — is **never gated** (§6), so flipping the flag off never blocks staff from managing
existing appointments, nor patients from looking up or cancelling one they already hold.

`FeatureFlagService` caches the flag's value for 10 seconds (§19 #12) — every instance in a
multi-instance deployment converges on a flip within that window with no manual cache-bust or
restart required.

## Step 1 — Enable in staging

1. Confirm the staging database has at least one active provider, with active appointment
   types assigned and working-hours rules configured (§7.4) — otherwise every availability
   query correctly but unhelpfully returns an empty slot list (§19 #35), which looks identical
   to a broken deployment during smoke testing.
2. Flip the flag: `PUT /api/v1/staff/feature-flags/enable_online_booking` with
   `{"isEnabled": true}`, authenticated as `ROLE_ADMIN` or `ROLE_SYSADMIN` (§8.17 — the one
   mutating action `ROLE_SYSADMIN` is granted, §2).
3. Wait ≥10 seconds for the cache TTL to guarantee every instance has converged (§19 #12).

## Step 2 — Smoke test in staging

Run through the full patient journey once, end-to-end, against staging:

- [ ] `GET /booking/appointment-types` → `200`, non-empty list.
- [ ] `GET /booking/providers?appointmentTypeId={id}` → `200`.
- [ ] `GET /booking/availability?providerId={id}&appointmentTypeId={id}&date={tomorrow+2}` →
      `200`, at least one slot (AC-1's scenario: a slot ≥24h and ≤90 days out, on a day with
      working hours).
- [ ] `POST /booking/holds` for that slot → `201` with a `holdToken`.
- [ ] `POST /booking/appointments` with that hold → `201`, `status: "CONFIRMED"` (or
      `"PENDING"` if the type requires approval), and the confirmation email is triggered
      (§8.6/AC-1).
- [ ] `GET /booking/appointments/{confirmationToken}` → `200`, matches what was just created.
- [ ] `DELETE /booking/appointments/{confirmationToken}` (well outside the 4h cutoff) → `204`
      or `200` per §8.8, and a second `GET` reflects `CANCELLED`.
- [ ] Staff console: the new appointment (before cancelling it, or a second one) is visible in
      `/staff/appointments` and, if `PENDING`, can be approved (§8.10).
- [ ] Run `perf/load-test-plan.js` against staging (`k6 run -e BASE_URL=<staging-url>
      perf/load-test-plan.js`) and confirm both P95 thresholds pass under the real staging
      topology — this is the load-test dry run named in the production-readiness checklist.

If any step fails: do not proceed to Step 3. Flip the flag back to `false`, file the defect,
and restart this runbook from Step 1 once it's fixed.

## Step 3 — Enable in production

1. Repeat Step 1's pre-flight check (active provider/types/hours) against the **production**
   database specifically — staging and production data are independent.
2. Flip the flag the same way as Step 1, against the production `/staff/feature-flags`
   endpoint.
3. Wait ≥10 seconds for cache convergence across all production instances.

## Step 4 — Monitor the first rollout window

Watch these two Micrometer/Prometheus metrics (§14) continuously for at least the first hour
after the production flip, and periodically for the following 24 hours:

- **`booking.feature_flag.blocked`** (flag-blocked-request counter) — should drop to
  (near-)zero immediately after the flip. A sustained non-zero rate means some client is still
  hitting a gated endpoint expecting the old `403`, or the flag did not actually propagate to
  every instance (check `GET /booking/config` directly against each instance if load-balancer
  routing allows it).
- **`booking.appointments.created`** (booking success counter, renders as
  `booking_appointments_total` in Prometheus) — should start incrementing at a rate consistent
  with real traffic. A flat line with real traffic present suggests bookings are failing
  silently upstream of this counter (e.g. every request 500ing before reaching
  `BookingService.createAppointment`'s success path).

Also watch `booking.availability.latency` (the P95 timer) against §14's 300ms target under
real production load, not just the staging load-test numbers from Step 2.

## Rollback procedure

Flipping the flag back to `false` is the entire rollback — no other action is needed or
sufficient:

1. `PUT /api/v1/staff/feature-flags/enable_online_booking` with `{"isEnabled": false}`.
2. Wait ≥10 seconds for cache convergence.
3. Confirm: the six gated endpoints now return `403 FEATURE_DISABLED`; `GET`/`DELETE
   /booking/appointments/{token}` and the full staff console continue to work exactly as
   before (§19 #11/#13 — the flag only gates the six endpoints, never existing state).
4. **Existing bookings are never affected by the flag** (§6's Database row, §19 #13) —
   appointments already `CONFIRMED`/`PENDING` before the rollback remain exactly as they were;
   no data migration, backfill, or cleanup step is needed or should be attempted.

This procedure must be executed once in staging as a dry run — flip off, confirm the four
checks in step 3 above, flip back on — before the production go-live in Step 3, per this
milestone's validation checklist.
