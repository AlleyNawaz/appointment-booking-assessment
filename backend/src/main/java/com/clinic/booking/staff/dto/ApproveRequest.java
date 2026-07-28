package com.clinic.booking.staff.dto;

/**
 * POST /staff/appointments/{id}/approve (PRD §8.10) takes no request body —
 * only the {@code If-Match} header. This class exists solely to satisfy the
 * approved implementation plan's Milestone 9 file list; it carries no
 * fields and is not bound as a {@code @RequestBody} anywhere, since doing so
 * would require clients to send a body the endpoint contract doesn't call for.
 */
public record ApproveRequest() {
}
