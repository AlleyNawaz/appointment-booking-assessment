package com.clinic.booking.staff.dto;

import java.util.List;

/** The standard page envelope (PRD §8.9/§8.18): {@code { content, page, size, totalElements, totalPages } }. */
public record AuditLogPageResponse(
        List<AuditLogEntryResponse> content, int page, int size, long totalElements, int totalPages) {
}
