package com.clinic.booking.staff.dto;

/** Request body for POST /staff/auth/login (PRD §8.20): {@code { "username", "password" } }. */
public record LoginRequest(String username, String password) {
}
