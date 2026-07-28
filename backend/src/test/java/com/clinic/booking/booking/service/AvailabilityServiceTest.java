package com.clinic.booking.booking.service;

import com.clinic.booking.booking.domain.AppointmentType;
import com.clinic.booking.booking.domain.Provider;
import com.clinic.booking.booking.domain.ProviderAvailabilityRule;
import com.clinic.booking.booking.domain.ProviderAvailabilityRule.RuleType;
import com.clinic.booking.booking.domain.ProviderUnavailability;
import com.clinic.booking.booking.domain.SlotHold;
import com.clinic.booking.booking.dto.AvailabilityResponse;
import com.clinic.booking.booking.repository.AppointmentTypeRepository;
import com.clinic.booking.booking.repository.ClinicHolidayRepository;
import com.clinic.booking.booking.repository.ProviderAvailabilityRuleRepository;
import com.clinic.booking.booking.repository.ProviderRepository;
import com.clinic.booking.booking.repository.ProviderUnavailabilityRepository;
import com.clinic.booking.booking.repository.AppointmentRepository;
import com.clinic.booking.booking.repository.SlotHoldRepository;
import com.clinic.booking.common.exception.BookingWindowExceededException;
import com.clinic.booking.common.exception.InvalidAppointmentDateException;
import com.clinic.booking.config.BookingProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.time.zone.ZoneOffsetTransition;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AvailabilityService} (PRD §8.4/§11/§12/§19). Domain
 * entities have only JPA-required no-arg constructors (§10 — no production
 * code needs to construct them yet), so fixtures are built via reflection
 * ({@link ReflectionTestUtils}) rather than adding test-only constructors to
 * already-reviewed Milestone 2 entities.
 */
@ExtendWith(MockitoExtension.class)
class AvailabilityServiceTest {

    private static final Long PROVIDER_ID = 5L;
    private static final Long TYPE_ID = 2L;

    @Mock
    private ProviderRepository providerRepository;

    @Mock
    private AppointmentTypeRepository appointmentTypeRepository;

    @Mock
    private ProviderAvailabilityRuleRepository availabilityRuleRepository;

    @Mock
    private ProviderUnavailabilityRepository unavailabilityRepository;

    @Mock
    private SlotHoldRepository slotHoldRepository;

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private ClinicHolidayRepository clinicHolidayRepository;

    private final io.micrometer.core.instrument.Timer availabilityLatencyTimer =
            new SimpleMeterRegistry().timer("test.availability.latency");

    @BeforeEach
    void stubNoActiveHoldsByDefault() {
        // Most tests don't concern active holds/appointments; this default keeps them from
        // having to repeat the same stub. Lenient because early-return tests (bad date,
        // holiday, unknown provider, no working rule) never reach the code path that calls this.
        lenient().when(slotHoldRepository.findActiveOverlapping(any(), any(), any(), any())).thenReturn(List.of());
        lenient().when(appointmentRepository.findActiveOverlapping(any(), any(), any(), any())).thenReturn(List.of());
    }

    private AvailabilityService serviceWith(BookingProperties properties) {
        return new AvailabilityService(providerRepository, appointmentTypeRepository,
                availabilityRuleRepository, unavailabilityRepository, slotHoldRepository, appointmentRepository,
                clinicHolidayRepository, properties, availabilityLatencyTimer);
    }

    private AvailabilityService defaultService() {
        return serviceWith(new BookingProperties());
    }

    @Test
    void dateInThePast_throwsInvalidAppointmentDateException() {
        LocalDate yesterday = LocalDate.now(ZoneId.of("America/New_York")).minusDays(1);

        assertThatThrownBy(() -> defaultService().computeAvailableSlots(PROVIDER_ID, TYPE_ID, yesterday))
                .isInstanceOf(InvalidAppointmentDateException.class);
    }

    @Test
    void computeAvailableSlots_recordsLatencyOnTheAvailabilityTimer_evenWhenItThrows() {
        // PRD §14: "availability query latency" — recorded via a `finally`, so a thrown
        // validation exception still counts as a timed invocation, not a skipped one.
        LocalDate yesterday = LocalDate.now(ZoneId.of("America/New_York")).minusDays(1);
        long before = availabilityLatencyTimer.count();

        assertThatThrownBy(() -> defaultService().computeAvailableSlots(PROVIDER_ID, TYPE_ID, yesterday))
                .isInstanceOf(InvalidAppointmentDateException.class);

        assertThat(availabilityLatencyTimer.count()).isEqualTo(before + 1);
    }

    @Test
    void dateBeyondMaxBookingWindow_throwsBookingWindowExceededException() {
        LocalDate tooFarOut = LocalDate.now(ZoneId.of("America/New_York")).plusDays(91);

        assertThatThrownBy(() -> defaultService().computeAvailableSlots(PROVIDER_ID, TYPE_ID, tooFarOut))
                .isInstanceOf(BookingWindowExceededException.class);
    }

    @Test
    void clinicHoliday_returnsEmptySlots_beforeEvenCheckingWorkingRules() {
        LocalDate holiday = LocalDate.now(ZoneId.of("America/New_York")).plusDays(10);
        when(clinicHolidayRepository.existsByHolidayDate(holiday)).thenReturn(true);

        AvailabilityResponse response = defaultService().computeAvailableSlots(PROVIDER_ID, TYPE_ID, holiday);

        assertThat(response.slots()).isEmpty();
    }

    @Test
    void noWorkingRuleForRequestedDay_returnsEmptySlots_notAnError() {
        LocalDate date = LocalDate.now(ZoneId.of("America/New_York")).plusDays(10);
        when(clinicHolidayRepository.existsByHolidayDate(date)).thenReturn(false);
        when(providerRepository.findById(PROVIDER_ID)).thenReturn(Optional.of(provider("America/New_York")));
        when(appointmentTypeRepository.findById(TYPE_ID)).thenReturn(Optional.of(appointmentType(30, 0)));
        when(availabilityRuleRepository.findByProviderIdAndDayOfWeek(eq(PROVIDER_ID), any()))
                .thenReturn(List.of()); // e.g. a Sunday with no override, §19 #35

        AvailabilityResponse response = defaultService().computeAvailableSlots(PROVIDER_ID, TYPE_ID, date);

        assertThat(response.slots()).isEmpty();
    }

    @Test
    void providerSpecificWorkingRule_opensASaturdayThatWouldOtherwiseBeClosed() {
        // §12.1: "A provider or admin may add/remove rows to open Saturdays... the
        // clinic default is a starting point, not a global constraint" — an explicit
        // WORKING rule at day_of_week=6 must produce slots exactly like any other day,
        // the direct counterpart to noWorkingRuleForRequestedDay_returnsEmptySlots above.
        ZoneId zone = ZoneId.of("America/New_York");
        LocalDate saturday = LocalDate.now(zone).with(TemporalAdjusters.next(DayOfWeek.SATURDAY));
        when(clinicHolidayRepository.existsByHolidayDate(saturday)).thenReturn(false);
        when(providerRepository.findById(PROVIDER_ID)).thenReturn(Optional.of(provider("America/New_York")));
        when(appointmentTypeRepository.findById(TYPE_ID)).thenReturn(Optional.of(appointmentType(30, 0)));
        when(availabilityRuleRepository.findByProviderIdAndDayOfWeek(PROVIDER_ID, 6))
                .thenReturn(List.of(rule(LocalTime.of(9, 0), LocalTime.of(13, 0), RuleType.WORKING)));
        lenient().when(unavailabilityRepository.findOverlapping(eq(PROVIDER_ID), any(), any())).thenReturn(List.of());

        AvailabilityResponse response = defaultService().computeAvailableSlots(PROVIDER_ID, TYPE_ID, saturday);

        assertThat(response.slots()).isNotEmpty();
        ZonedDateTime firstSlot = ZonedDateTime.ofInstant(response.slots().get(0), zone);
        assertThat(firstSlot.toLocalTime()).isEqualTo(LocalTime.of(9, 0));
    }

    @Test
    void unknownProviderOrType_returnsEmptySlots_notAnError() {
        LocalDate date = LocalDate.now(ZoneId.of("America/New_York")).plusDays(10);
        when(clinicHolidayRepository.existsByHolidayDate(date)).thenReturn(false);
        when(providerRepository.findById(PROVIDER_ID)).thenReturn(Optional.empty());

        AvailabilityResponse response = defaultService().computeAvailableSlots(PROVIDER_ID, TYPE_ID, date);

        assertThat(response.slots()).isEmpty();
    }

    @Test
    void onlyOffersSlotsWhereDurationPlusBufferFitsBeforeTheNextBlockingInterval() {
        LocalDate date = LocalDate.now(ZoneId.of("America/New_York")).plusDays(10);
        when(clinicHolidayRepository.existsByHolidayDate(date)).thenReturn(false);
        when(providerRepository.findById(PROVIDER_ID)).thenReturn(Optional.of(provider("America/New_York")));
        // duration 45 + buffer 15 = 60 minutes needed
        when(appointmentTypeRepository.findById(TYPE_ID)).thenReturn(Optional.of(appointmentType(45, 15)));
        when(availabilityRuleRepository.findByProviderIdAndDayOfWeek(eq(PROVIDER_ID), any()))
                .thenReturn(List.of(rule(LocalTime.of(9, 0), LocalTime.of(10, 0), RuleType.WORKING)));
        lenient().when(unavailabilityRepository.findOverlapping(eq(PROVIDER_ID), any(), any())).thenReturn(List.of());

        AvailabilityResponse response = defaultService().computeAvailableSlots(PROVIDER_ID, TYPE_ID, date);

        // A one-hour window can fit exactly one 60-minute (duration+buffer) slot at 09:00;
        // 09:15 would need until 10:15, which doesn't fit (§19 #36/#37).
        assertThat(response.slots()).hasSize(1);
        ZonedDateTime onlySlot = ZonedDateTime.ofInstant(response.slots().get(0), ZoneId.of("America/New_York"));
        assertThat(onlySlot.toLocalTime()).isEqualTo(LocalTime.of(9, 0));
    }

    @Test
    void breakInterval_splitsWorkingHoursIntoTwoOpenRanges() {
        LocalDate date = LocalDate.now(ZoneId.of("America/New_York")).plusDays(10);
        when(clinicHolidayRepository.existsByHolidayDate(date)).thenReturn(false);
        when(providerRepository.findById(PROVIDER_ID)).thenReturn(Optional.of(provider("America/New_York")));
        when(appointmentTypeRepository.findById(TYPE_ID)).thenReturn(Optional.of(appointmentType(15, 0)));
        when(availabilityRuleRepository.findByProviderIdAndDayOfWeek(eq(PROVIDER_ID), any())).thenReturn(List.of(
                rule(LocalTime.of(9, 0), LocalTime.of(17, 0), RuleType.WORKING),
                rule(LocalTime.of(12, 0), LocalTime.of(13, 0), RuleType.BREAK)));
        lenient().when(unavailabilityRepository.findOverlapping(eq(PROVIDER_ID), any(), any())).thenReturn(List.of());

        AvailabilityResponse response = defaultService().computeAvailableSlots(PROVIDER_ID, TYPE_ID, date);

        List<LocalTime> localTimes = response.slots().stream()
                .map(instant -> ZonedDateTime.ofInstant(instant, ZoneId.of("America/New_York")).toLocalTime())
                .toList();
        assertThat(localTimes).contains(LocalTime.of(11, 45)); // last slot before the break
        assertThat(localTimes).doesNotContain(LocalTime.of(12, 0), LocalTime.of(12, 15), LocalTime.of(12, 45));
        assertThat(localTimes).contains(LocalTime.of(13, 0)); // first slot after the break
    }

    @Test
    void unavailabilityRange_blocksTheOverlappingPortionOfTheDay() {
        ZoneId zone = ZoneId.of("America/New_York");
        LocalDate date = LocalDate.now(zone).plusDays(10);
        when(clinicHolidayRepository.existsByHolidayDate(date)).thenReturn(false);
        when(providerRepository.findById(PROVIDER_ID)).thenReturn(Optional.of(provider("America/New_York")));
        when(appointmentTypeRepository.findById(TYPE_ID)).thenReturn(Optional.of(appointmentType(15, 0)));
        when(availabilityRuleRepository.findByProviderIdAndDayOfWeek(eq(PROVIDER_ID), any()))
                .thenReturn(List.of(rule(LocalTime.of(9, 0), LocalTime.of(12, 0), RuleType.WORKING)));

        Instant vacationStart = ZonedDateTime.of(date, LocalTime.of(10, 0), zone).toInstant();
        Instant vacationEnd = ZonedDateTime.of(date, LocalTime.of(11, 0), zone).toInstant();
        when(unavailabilityRepository.findOverlapping(eq(PROVIDER_ID), any(), any()))
                .thenReturn(List.of(unavailability(vacationStart, vacationEnd)));

        AvailabilityResponse response = defaultService().computeAvailableSlots(PROVIDER_ID, TYPE_ID, date);

        List<LocalTime> localTimes = response.slots().stream()
                .map(instant -> ZonedDateTime.ofInstant(instant, zone).toLocalTime())
                .toList();
        assertThat(localTimes).contains(LocalTime.of(9, 0), LocalTime.of(9, 45));
        assertThat(localTimes).doesNotContain(LocalTime.of(10, 0), LocalTime.of(10, 30));
        assertThat(localTimes).contains(LocalTime.of(11, 0), LocalTime.of(11, 45));
    }

    @Test
    void activeSlotHold_blocksTheOverlappingPortionOfTheDay() {
        // §8.4: "subtracting ... the union of ... active slot_holds ..." (Milestone 4).
        ZoneId zone = ZoneId.of("America/New_York");
        LocalDate date = LocalDate.now(zone).plusDays(10);
        when(clinicHolidayRepository.existsByHolidayDate(date)).thenReturn(false);
        when(providerRepository.findById(PROVIDER_ID)).thenReturn(Optional.of(provider("America/New_York")));
        when(appointmentTypeRepository.findById(TYPE_ID)).thenReturn(Optional.of(appointmentType(15, 0)));
        when(availabilityRuleRepository.findByProviderIdAndDayOfWeek(eq(PROVIDER_ID), any()))
                .thenReturn(List.of(rule(LocalTime.of(9, 0), LocalTime.of(12, 0), RuleType.WORKING)));
        lenient().when(unavailabilityRepository.findOverlapping(eq(PROVIDER_ID), any(), any())).thenReturn(List.of());

        Instant holdStart = ZonedDateTime.of(date, LocalTime.of(10, 0), zone).toInstant();
        Instant holdEnd = ZonedDateTime.of(date, LocalTime.of(11, 0), zone).toInstant();
        SlotHold hold = new SlotHold(PROVIDER_ID, TYPE_ID, holdStart, holdEnd, "hold-token", Instant.now().plusSeconds(300));
        when(slotHoldRepository.findActiveOverlapping(eq(PROVIDER_ID), any(), any(), any()))
                .thenReturn(List.of(hold));

        AvailabilityResponse response = defaultService().computeAvailableSlots(PROVIDER_ID, TYPE_ID, date);

        List<LocalTime> localTimes = response.slots().stream()
                .map(instant -> ZonedDateTime.ofInstant(instant, zone).toLocalTime())
                .toList();
        assertThat(localTimes).contains(LocalTime.of(9, 0), LocalTime.of(9, 45));
        assertThat(localTimes).doesNotContain(LocalTime.of(10, 0), LocalTime.of(10, 30));
        assertThat(localTimes).contains(LocalTime.of(11, 0), LocalTime.of(11, 45));
    }

    @Test
    void dstTransition_shiftsUtcOffsetAutomatically_withoutSchemaChange() {
        // §19 #4: start_time/end_time are wall-clock values interpreted in the
        // provider's IANA zone at computation time. A wide test-only booking
        // window is used so the test can always reach the real next DST
        // transition, whenever the suite happens to run, rather than assuming
        // a hardcoded calendar date.
        ZoneId zone = ZoneId.of("America/New_York");
        ZoneOffsetTransition transition = zone.getRules().nextTransition(Instant.now());
        LocalDate dayBefore = ZonedDateTime.ofInstant(transition.getInstant(), zone).toLocalDate().minusDays(1);
        LocalDate dayAfter = ZonedDateTime.ofInstant(transition.getInstant(), zone).toLocalDate().plusDays(1);

        BookingProperties wideWindow = new BookingProperties();
        wideWindow.setMaxBookingWindowDays(400);

        when(clinicHolidayRepository.existsByHolidayDate(any())).thenReturn(false);
        when(providerRepository.findById(PROVIDER_ID)).thenReturn(Optional.of(provider("America/New_York")));
        when(appointmentTypeRepository.findById(TYPE_ID)).thenReturn(Optional.of(appointmentType(15, 0)));
        when(availabilityRuleRepository.findByProviderIdAndDayOfWeek(eq(PROVIDER_ID), any()))
                .thenReturn(List.of(rule(LocalTime.of(9, 0), LocalTime.of(10, 0), RuleType.WORKING)));
        when(unavailabilityRepository.findOverlapping(eq(PROVIDER_ID), any(), any())).thenReturn(List.of());

        AvailabilityService service = serviceWith(wideWindow);
        Instant slotBefore = service.computeAvailableSlots(PROVIDER_ID, TYPE_ID, dayBefore).slots().get(0);
        Instant slotAfter = service.computeAvailableSlots(PROVIDER_ID, TYPE_ID, dayAfter).slots().get(0);

        ZoneOffset offsetBefore = zone.getRules().getOffset(slotBefore);
        ZoneOffset offsetAfter = zone.getRules().getOffset(slotAfter);
        assertThat(offsetBefore).isNotEqualTo(offsetAfter);

        assertThat(ZonedDateTime.ofInstant(slotBefore, zone).toLocalTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(ZonedDateTime.ofInstant(slotAfter, zone).toLocalTime()).isEqualTo(LocalTime.of(9, 0));
    }

    // --- fixture construction (reflection: entities have only a JPA no-arg constructor) ---

    private static Provider provider(String timezone) {
        Provider provider = construct(Provider.class);
        ReflectionTestUtils.setField(provider, "timezone", timezone);
        return provider;
    }

    private static AppointmentType appointmentType(int durationMinutes, int bufferMinutes) {
        AppointmentType type = construct(AppointmentType.class);
        ReflectionTestUtils.setField(type, "durationMinutes", durationMinutes);
        ReflectionTestUtils.setField(type, "bufferMinutes", bufferMinutes);
        return type;
    }

    private static ProviderAvailabilityRule rule(LocalTime start, LocalTime end, RuleType ruleType) {
        ProviderAvailabilityRule rule = construct(ProviderAvailabilityRule.class);
        ReflectionTestUtils.setField(rule, "startTime", start);
        ReflectionTestUtils.setField(rule, "endTime", end);
        ReflectionTestUtils.setField(rule, "ruleType", ruleType);
        return rule;
    }

    private static ProviderUnavailability unavailability(Instant start, Instant end) {
        ProviderUnavailability unavailability = construct(ProviderUnavailability.class);
        ReflectionTestUtils.setField(unavailability, "startDatetime", start);
        ReflectionTestUtils.setField(unavailability, "endDatetime", end);
        return unavailability;
    }

    private static <T> T construct(Class<T> type) {
        try {
            Constructor<T> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not construct test fixture for " + type, e);
        }
    }
}
