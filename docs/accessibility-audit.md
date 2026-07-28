# Accessibility Audit — WCAG 2.1 AA (Milestone 13)

PRD §14 ("Accessibility: WCAG 2.1 AA, §9") and the Milestone 13 validation checklist
("Automated accessibility scan passes with zero WCAG 2.1 AA violations on every screen
in §4's inventory"). Per the implementation plan, this is a checklist/report artifact,
not source code.

## Method

Automated scan with **axe-core 4.12** driven by Puppeteer against a live `ng serve`
instance (backend running, feature flag on, a seeded provider/appointment-type/holds so
real data-bearing states render, not just empty/loading states). Rule set: `wcag2a`,
`wcag2aa`, `wcag21a`, `wcag21aa`.

Every patient-facing screen in §4's inventory was reached by direct navigation or by
scripted click-through of the real user flow (type → provider → date → slot → contact
details), and re-scanned after each state change.

Screens that require an authenticated staff session (the appointments/availability/
admin/audit-log pages) were **out of reach of the original scan** (it only drove the
unauthenticated `ng serve` instance) and were initially reviewed manually only. That gap
was closed in a follow-up pass: the same axe-core/Puppeteer scan was re-run against a
Puppeteer-driven browser that first logs in through the real `/staff/login` form (using a
disposable `ROLE_SYSADMIN` staff-user row seeded directly in the local dev database and
deleted immediately after the run — `ROLE_SYSADMIN` sees every nav item per §4.1, so one
session covers all 9 authenticated screens) and then navigates to each screen exactly as
a real user would. Every one of the 9 authenticated screens below was scanned this way,
not just manually reviewed.

## Automated scan results

| Screen | Route | Violations |
|---|---|---|
| Booking entry | `/book` | 0 |
| Type selection | `/book/type` | 0 |
| Provider selection | `/book/provider` (via click-through) | 0 |
| Schedule selection (calendar + slot grid) | `/book/schedule` (via click-through) | **1 → 0 (fixed, see below)** |
| Contact details | `/book/details` (via click-through) | 0 |
| Appointment lookup — not-found error state | `/appointments/does-not-exist` | 0 |
| Staff login | `/staff/login` | 0 |

## Defect found and fixed

**`aria-required-children` (critical) on the schedule-selection calendar.**

The calendar's month-navigation controls (`Previous month`/`Next month` buttons) were
direct children of the `role="grid"` container, alongside the `role="row"` week divs.
ARIA's `grid` role only permits `row`/`rowgroup` children — the nav buttons made it an
invalid grid structure, which axe correctly flagged as critical (assistive tech cannot
reliably interpret the grid's contents).

**Fix:** moved `.calendar__nav` outside the `role="grid"` element, into the existing
`.calendar` wrapper alongside a new `.calendar__grid` div that now carries `role="grid"`
and contains only the header row and week rows. No visual/CSS change — `.calendar__week`
already had its own independent grid layout; only the ARIA role's placement moved.

File changed: `frontend/src/app/booking/pages/schedule-selection/schedule-selection.page.html`.

Re-scanned after the fix: **0 violations** on the same page and flow.

## Automated scan results — staff console (authenticated, `ROLE_SYSADMIN`)

Same axe-core ruleset (`wcag2a`, `wcag2aa`, `wcag21a`, `wcag21aa`) as the patient-facing
scan above, driven against real data-bearing states (a seeded provider/appointment-type
and one seeded confirmed appointment), not just empty/loading states.

| Screen | Route | Violations |
|---|---|---|
| Staff appointment list | `/staff/appointments` | 0 |
| Staff appointment detail | `/staff/appointments/{id}` | 0 |
| Availability — hours | `/staff/availability/hours` | 0 |
| Availability — time off | `/staff/availability/unavailability` | 0 |
| Availability — holidays | `/staff/availability/holidays` | 0 |
| Admin — appointment types | `/staff/admin/appointment-types` | 0 |
| Admin — providers | `/staff/admin/providers` | 0 |
| Admin — system settings | `/staff/admin/settings` | 0 |
| Audit log | `/staff/audit-log` | 0 |

`app-staff-nav` (plain `<nav>` with `<a routerLink>` links) and the shared
`app-async-state-wrapper` loading/error/empty states render on every one of these
screens and are therefore covered by the same scan, not reviewed separately.

No custom `role="grid"`/`role="listbox"`/similar composite widgets exist anywhere in the
staff console — the one place a composite ARIA pattern was used (the booking wizard's
calendar) is exactly the page the earlier automated scan caught and this audit fixed.

## Result

Zero outstanding WCAG 2.1 AA violations across every screen in §4's inventory — all 16
screens (7 patient-facing + 9 staff-console) were scanned with axe-core, not manually
reviewed; one genuine defect (the calendar's `aria-required-children` issue above) was
found and fixed during this audit.
