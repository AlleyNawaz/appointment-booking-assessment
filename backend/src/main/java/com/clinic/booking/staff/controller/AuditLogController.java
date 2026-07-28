package com.clinic.booking.staff.controller;

import com.clinic.booking.staff.dto.AuditLogPageResponse;
import com.clinic.booking.staff.service.AuditLogService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/** GET /api/v1/staff/audit-log (PRD §8.18) — ROLE_SYSADMIN only. */
@RestController
@RequestMapping("/api/v1/staff/audit-log")
public class AuditLogController {

    private final AuditLogService auditLogService;

    public AuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @GetMapping
    public AuditLogPageResponse list(
            @RequestParam(required = false) Long appointmentId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "changedAt,asc") String sort) {
        return auditLogService.list(appointmentId, from, to, page, size, sort);
    }
}
