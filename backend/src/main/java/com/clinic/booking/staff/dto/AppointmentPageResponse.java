package com.clinic.booking.staff.dto;

import java.util.List;

/** The standard page envelope (PRD §8.9): {@code { content, page, size, totalElements, totalPages } }. */
public record AppointmentPageResponse(
        List<StaffAppointmentResponse> content, int page, int size, long totalElements, int totalPages) {
}
