package com.clinic.booking;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Milestone 1 smoke test: the application context must start, which requires
 * Flyway to apply V1-V4 cleanly against a real MySQL 8 instance (§7.10) and
 * the DataSource/JPA/Actuator auto-configuration to wire up without error.
 */
@SpringBootTest
class BookingApplicationTests {

    @Test
    void contextLoads() {
    }
}
