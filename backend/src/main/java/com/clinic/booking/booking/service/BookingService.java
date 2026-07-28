package com.clinic.booking.booking.service;

import com.clinic.booking.booking.domain.Appointment;
import com.clinic.booking.booking.domain.AppointmentType;
import com.clinic.booking.booking.domain.Provider;
import com.clinic.booking.booking.domain.SlotHold;
import com.clinic.booking.booking.dto.AppointmentResponse;
import com.clinic.booking.booking.dto.CreateAppointmentRequest;
import com.clinic.booking.booking.repository.AppointmentRepository;
import com.clinic.booking.booking.repository.AppointmentTypeRepository;
import com.clinic.booking.booking.repository.ProviderRepository;
import com.clinic.booking.booking.repository.SlotHoldRepository;
import com.clinic.booking.booking.validation.BookingWindowValidator;
import com.clinic.booking.booking.validation.ClinicClosedDayValidator;
import com.clinic.booking.booking.validation.DuplicateAppointmentValidator;
import com.clinic.booking.booking.validation.LeadTimeValidator;
import com.clinic.booking.common.exception.DuplicateAppointmentException;
import com.clinic.booking.common.exception.IdempotencyKeyReusedMismatchException;
import com.clinic.booking.common.exception.PatientDailyLimitExceededException;
import com.clinic.booking.common.exception.ProviderUnavailableException;
import com.clinic.booking.common.exception.SlotAlreadyBookedException;
import com.clinic.booking.common.exception.SlotHoldExpiredException;
import com.clinic.booking.common.exception.ValidationException;
import com.clinic.booking.common.util.RequestHasher;
import com.clinic.booking.notification.EmailNotificationService;
import io.micrometer.core.instrument.Counter;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Implements POST /booking/appointments (PRD §8.6) — the core transactional
 * booking flow. Idempotency is checked first (§8.6's documented lookup
 * order), before the hold is ever touched, so a replay never consumes it.
 */
@Service
public class BookingService {

    private static final Pattern NAME_PATTERN = Pattern.compile("^[\\p{L} '.-]{2,100}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+[1-9]\\d{7,14}$");
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]*>");
    private static final List<Appointment.Status> ACTIVE_STATUSES =
            List.of(Appointment.Status.PENDING, Appointment.Status.CONFIRMED);

    private final AppointmentRepository appointmentRepository;
    private final SlotHoldRepository slotHoldRepository;
    private final ProviderRepository providerRepository;
    private final AppointmentTypeRepository appointmentTypeRepository;
    private final LeadTimeValidator leadTimeValidator;
    private final BookingWindowValidator bookingWindowValidator;
    private final ClinicClosedDayValidator clinicClosedDayValidator;
    private final DuplicateAppointmentValidator duplicateAppointmentValidator;
    private final EmailNotificationService emailNotificationService;
    private final Counter bookingSuccessCounter;

    public BookingService(
            AppointmentRepository appointmentRepository,
            SlotHoldRepository slotHoldRepository,
            ProviderRepository providerRepository,
            AppointmentTypeRepository appointmentTypeRepository,
            LeadTimeValidator leadTimeValidator,
            BookingWindowValidator bookingWindowValidator,
            ClinicClosedDayValidator clinicClosedDayValidator,
            DuplicateAppointmentValidator duplicateAppointmentValidator,
            EmailNotificationService emailNotificationService,
            @Qualifier("bookingSuccessCounter") Counter bookingSuccessCounter) {
        this.appointmentRepository = appointmentRepository;
        this.slotHoldRepository = slotHoldRepository;
        this.providerRepository = providerRepository;
        this.appointmentTypeRepository = appointmentTypeRepository;
        this.leadTimeValidator = leadTimeValidator;
        this.bookingWindowValidator = bookingWindowValidator;
        this.clinicClosedDayValidator = clinicClosedDayValidator;
        this.duplicateAppointmentValidator = duplicateAppointmentValidator;
        this.emailNotificationService = emailNotificationService;
        this.bookingSuccessCounter = bookingSuccessCounter;
    }

    @Transactional
    public AppointmentResponse createAppointment(CreateAppointmentRequest request, String idempotencyKey) {
        String patientFullName = validateName(request.patientFullName());
        String patientEmail = validateEmail(request.patientEmail());
        String patientPhone = validatePhone(request.patientPhone());
        String notes = sanitizeNotes(request.notes());

        String requestBodyHash =
                RequestHasher.hash(request.holdToken(), patientFullName, patientEmail, patientPhone, notes);

        // §8.6 idempotency lookup order: (1) by key, before the hold is ever touched.
        Optional<Appointment> existing = appointmentRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            if (existing.get().getRequestBodyHash().equals(requestBodyHash)) {
                return toResponse(existing.get());
            }
            throw new IdempotencyKeyReusedMismatchException();
        }

        SlotHold hold = slotHoldRepository.findByHoldToken(request.holdToken())
                .filter(h -> h.getExpiresAt().isAfter(Instant.now()))
                .orElseThrow(SlotHoldExpiredException::new);

        // §11.9: referential-active check, read fresh — never cached from hold-creation time.
        Provider provider = providerRepository.findById(hold.getProviderId())
                .filter(p -> p.isActive() && p.getDeletedAt() == null)
                .orElseThrow(ProviderUnavailableException::new);
        AppointmentType appointmentType = appointmentTypeRepository.findById(hold.getAppointmentTypeId())
                .filter(AppointmentType::isActive)
                .orElseThrow(ProviderUnavailableException::new);

        Instant start = hold.getStartDatetime();
        Instant end = hold.getEndDatetime();

        leadTimeValidator.validate(start);
        bookingWindowValidator.validate(start);
        clinicClosedDayValidator.validate(provider.getId(), start, provider.getTimezone());
        checkDailyLimit(patientEmail, patientPhone, provider.getId(), start, provider.getTimezone());
        duplicateAppointmentValidator.validate(patientEmail, patientPhone, provider.getId(), start, end);

        Appointment.Status status =
                appointmentType.isRequiresApproval() ? Appointment.Status.PENDING : Appointment.Status.CONFIRMED;
        String confirmationToken = UUID.randomUUID().toString();

        Appointment appointment = new Appointment(confirmationToken, provider.getId(), appointmentType.getId(),
                patientFullName, patientEmail, patientPhone, notes, start, end, status, idempotencyKey,
                requestBodyHash);

        try {
            appointmentRepository.saveAndFlush(appointment);
        } catch (DataIntegrityViolationException e) {
            // §12.9/§10: insert-and-catch on active_slot_key — the DB unique index is the
            // actual arbiter of double-booking, not a pre-check-then-insert race.
            if (isActiveSlotKeyViolation(e)) {
                throw new SlotAlreadyBookedException();
            }
            throw e;
        }

        slotHoldRepository.delete(hold);
        emailNotificationService.sendBookingNotification(appointment);
        bookingSuccessCounter.increment();

        return toResponse(appointment);
    }

    private void checkDailyLimit(String email, String phone, Long providerId, Instant start, String providerTimezone) {
        ZoneId zone = ZoneId.of(providerTimezone);
        LocalDate day = ZonedDateTime.ofInstant(start, zone).toLocalDate();
        Instant dayStart = day.atStartOfDay(zone).toInstant();
        Instant dayEnd = day.plusDays(1).atStartOfDay(zone).toInstant();

        List<Appointment> sameDay =
                appointmentRepository.findActiveForPatientOnDay(email, phone, ACTIVE_STATUSES, dayStart, dayEnd);
        boolean alreadyHasSameProvider = sameDay.stream().anyMatch(a -> a.getProviderId().equals(providerId));
        if (alreadyHasSameProvider || sameDay.size() >= 3) {
            throw new PatientDailyLimitExceededException();
        }
    }

    private static AppointmentResponse toResponse(Appointment appointment) {
        return new AppointmentResponse(appointment.getConfirmationToken(), appointment.getStatus(),
                appointment.getProviderId(), appointment.getStartDatetime());
    }

    private static boolean isActiveSlotKeyViolation(DataIntegrityViolationException e) {
        if (!(e.getCause() instanceof ConstraintViolationException cve)) {
            return false;
        }
        String constraintName = cve.getConstraintName();
        return constraintName != null && constraintName.toLowerCase().contains("uq_active_slot");
    }

    private static String validateName(String name) {
        if (name == null || !NAME_PATTERN.matcher(name).matches()) {
            throw new ValidationException("patientFullName", "must be 2-100 letters, spaces, hyphens, or periods.");
        }
        return name;
    }

    private static String validateEmail(String email) {
        if (email == null || email.length() > 254 || !EMAIL_PATTERN.matcher(email).matches()) {
            throw new ValidationException("patientEmail", "must be a valid email address.");
        }
        return email;
    }

    private static String validatePhone(String phone) {
        if (phone == null || !PHONE_PATTERN.matcher(phone).matches()) {
            throw new ValidationException("patientPhone", "must be in E.164 format, e.g. +14155551234.");
        }
        return phone;
    }

    /** §15.2/§19 #29: HTML/script tags are silently stripped, never rejected. */
    private static String sanitizeNotes(String notes) {
        if (notes == null) {
            return null;
        }
        String stripped = HTML_TAG_PATTERN.matcher(notes).replaceAll("");
        if (stripped.length() > 500) {
            throw new ValidationException("notes", "must be at most 500 characters.");
        }
        return stripped;
    }
}
