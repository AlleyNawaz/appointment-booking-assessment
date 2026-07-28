package com.clinic.booking.staff.dto;

/** Request body for POST/PUT /staff/providers (PRD §8.13). */
public record ProviderRequest(
        String firstName, String lastName, String specialty, String email, String timezone, Boolean isActive) {
}
