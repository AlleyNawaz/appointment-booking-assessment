package com.clinic.booking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.TimeZone;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableAsync
public class BookingApplication {

    public static void main(String[] args) {
        // The app already assumes UTC everywhere (serverTimezone=UTC, hibernate.jdbc.time_zone=UTC,
        // application.yml) — but those settings don't cover every JDBC temporal type. A JVM whose
        // default timezone isn't UTC (e.g. deployed outside UTC) silently shifts values that go
        // through java.sql.Time/LocalTime JDBC binding, such as provider_availability_rules'
        // start_time/end_time, which have no timezone of their own and must round-trip verbatim.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(BookingApplication.class, args);
    }
}
