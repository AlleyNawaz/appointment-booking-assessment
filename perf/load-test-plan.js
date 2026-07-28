// Milestone 14 — load/performance test plan (PRD §14 Non-Functional Requirements).
//
// Targets, measured at the load balancer per §14:
//   - GET  /api/v1/booking/availability   P95 < 300ms
//   - POST /api/v1/booking/appointments   P95 < 500ms
//
// Tool: k6 (https://k6.io) — chosen because it is a single static binary (no JVM/Node runtime
// dependency on the load-generating host) and expresses pass/fail thresholds declaratively,
// which is what this plan needs: a checked assertion against §14's two P95 numbers, not just a
// report to eyeball.
//
// Usage:
//   k6 run perf/load-test-plan.js
//   k6 run -e BASE_URL=https://staging.example.com perf/load-test-plan.js
//
// Prerequisites before running against a real target:
//   - enable_online_booking must be TRUE (§6) — otherwise every request in this script gets
//     403 FEATURE_DISABLED and the timings measured are of the flag-check path, not the real one.
//   - At least one active provider with an active appointment type and working hours must exist,
//     with PROVIDER_ID/APPOINTMENT_TYPE_ID below pointing at them.
//   - Run against a multi-instance deployment behind the real load balancer topology (§14
//     Scalability) for a result that reflects the target architecture — a single local instance
//     under-reports connection-pool contention (§14: 20 connections/instance) that only appears
//     with concurrent instances sharing one MySQL server.
//
// This script has not been executed against a live multi-instance deployment as part of this
// change — no such environment exists in this repository/sandbox. It is a ready-to-run artifact;
// the "baseline results" half of this milestone's deliverable is the staging dry run named in
// docs/flag-rollout-runbook.md, not a number fabricated here.

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const PROVIDER_ID = __ENV.PROVIDER_ID || '1';
const APPOINTMENT_TYPE_ID = __ENV.APPOINTMENT_TYPE_ID || '2';

const availabilityLatency = new Trend('availability_latency_ms', true);
const appointmentCreationLatency = new Trend('appointment_creation_latency_ms', true);

export const options = {
  scenarios: {
    // §14: "read-heavy on availability, not connection-heavy" — availability traffic
    // dominates realistic load, so it gets the larger share of virtual users.
    availability_reads: {
      executor: 'constant-vus',
      exec: 'checkAvailability',
      vus: 30,
      duration: '2m',
    },
    appointment_creation: {
      executor: 'constant-vus',
      exec: 'createAppointment',
      vus: 5,
      duration: '2m',
    },
  },
  thresholds: {
    // §14's two P95 targets, enforced as k6 pass/fail thresholds, not just observed.
    'availability_latency_ms': ['p(95)<300'],
    'appointment_creation_latency_ms': ['p(95)<500'],
    // A load test that is quietly erroring on every request is not a load test.
    http_req_failed: ['rate<0.05'],
  },
};

function futureDate(daysOut) {
  const d = new Date();
  d.setUTCDate(d.getUTCDate() + daysOut);
  return d.toISOString().slice(0, 10);
}

export function checkAvailability() {
  const date = futureDate(3 + (__VU % 30)); // spread requests across ~30 distinct dates
  const url = `${BASE_URL}/api/v1/booking/availability?providerId=${PROVIDER_ID}&appointmentTypeId=${APPOINTMENT_TYPE_ID}&date=${date}`;
  const res = http.get(url);
  availabilityLatency.add(res.timings.duration);
  check(res, {
    'availability status is 200 or 403 (flag off)': (r) => r.status === 200 || r.status === 403,
  });
  sleep(1);
}

export function createAppointment() {
  const date = futureDate(10 + __VU); // stay well clear of the availability scenario's dates
  const holdRes = http.post(
    `${BASE_URL}/api/v1/booking/holds`,
    JSON.stringify({
      providerId: Number(PROVIDER_ID),
      appointmentTypeId: Number(APPOINTMENT_TYPE_ID),
      startDateTime: `${date}T14:00:00Z`,
    }),
    { headers: { 'Content-Type': 'application/json' } }
  );
  if (holdRes.status !== 201) {
    sleep(1);
    return; // flag off, slot already held this run, etc. — not what this scenario measures.
  }
  const holdToken = JSON.parse(holdRes.body).holdToken;

  const start = Date.now();
  const res = http.post(
    `${BASE_URL}/api/v1/booking/appointments`,
    JSON.stringify({
      holdToken,
      patientFullName: 'Load Test Patient',
      patientEmail: `load-test-${__VU}-${__ITER}@example.com`,
      patientPhone: '+14155550100',
    }),
    {
      headers: {
        'Content-Type': 'application/json',
        'Idempotency-Key': `${__VU}-${__ITER}-${Date.now()}`,
      },
    }
  );
  appointmentCreationLatency.add(Date.now() - start);
  check(res, {
    'appointment creation status is 201, 409, or 410': (r) =>
      [201, 409, 410].includes(r.status),
  });
  sleep(1);
}
