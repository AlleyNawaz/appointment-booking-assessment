package com.clinic.booking.booking.dto;

/**
 * Response shape for GET /booking/config (PRD §8.1): {@code { "enabled": true } }.
 */
public record ConfigResponse(boolean enabled) {
}
