package com.clinic.booking.staff.service;

import com.clinic.booking.audit.AppointmentAuditLog;
import com.clinic.booking.audit.AppointmentAuditLogRepository;
import com.clinic.booking.common.exception.ValidationException;
import com.clinic.booking.staff.dto.AuditLogEntryResponse;
import com.clinic.booking.staff.dto.AuditLogPageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Implements GET /staff/audit-log (PRD §8.18) — read-only, ROLE_SYSADMIN only. */
@Service
public class AuditLogService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final Map<String, String> SORT_PROPERTIES = Map.of("changedAt", "changedAt", "appointmentId", "appointmentId");

    private final AppointmentAuditLogRepository auditLogRepository;

    public AuditLogService(AppointmentAuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @PreAuthorize("hasRole('SYSADMIN')")
    @Transactional(readOnly = true)
    public AuditLogPageResponse list(Long appointmentId, Instant from, Instant to, int page, int size, String sort) {
        int clampedSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        PageRequest pageRequest = PageRequest.of(page, clampedSize, resolveSort(sort));
        Page<AppointmentAuditLog> result = auditLogRepository.search(appointmentId, from, to, pageRequest);

        List<AuditLogEntryResponse> content = result.getContent().stream().map(AuditLogEntryResponse::from).toList();
        return new AuditLogPageResponse(content, page, clampedSize, result.getTotalElements(), result.getTotalPages());
    }

    private Sort resolveSort(String sort) {
        String[] parts = sort.split(",", 2);
        String property = SORT_PROPERTIES.get(parts[0]);
        if (property == null) {
            throw new ValidationException("sort", "must be one of " + Set.copyOf(SORT_PROPERTIES.keySet()));
        }
        Sort.Direction direction =
                parts.length > 1 && "desc".equalsIgnoreCase(parts[1]) ? Sort.Direction.DESC : Sort.Direction.ASC;
        return Sort.by(direction, property);
    }
}
