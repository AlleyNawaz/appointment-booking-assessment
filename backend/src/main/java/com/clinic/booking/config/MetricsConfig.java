package com.clinic.booking.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * PRD §14 Observability: the four named Micrometer → Prometheus metrics, defined once here
 * (§10's "one named place, not scattered inline" convention, mirroring {@link CorsConfig})
 * so each consumer (BookingService, HoldReaperJob, AvailabilityService, FeatureGateAspect)
 * injects a ready-made bean rather than constructing meters ad hoc inline.
 */
@Configuration
public class MetricsConfig {

    @Bean
    public Counter bookingSuccessCounter(MeterRegistry registry) {
        return Counter.builder("booking.appointments.created")
                .description("Number of appointments successfully created")
                .register(registry);
    }

    @Bean
    public Counter holdExpiryCounter(MeterRegistry registry) {
        return Counter.builder("booking.holds.expired")
                .description("Number of slot holds reaped after expiry")
                .register(registry);
    }

    @Bean
    public Timer availabilityLatencyTimer(MeterRegistry registry) {
        return Timer.builder("booking.availability.latency")
                .description("Latency of GET /booking/availability slot computation")
                .register(registry);
    }

    @Bean
    public Counter flagBlockedCounter(MeterRegistry registry) {
        return Counter.builder("booking.feature_flag.blocked")
                .description("Number of requests blocked by a disabled feature flag")
                .register(registry);
    }
}
