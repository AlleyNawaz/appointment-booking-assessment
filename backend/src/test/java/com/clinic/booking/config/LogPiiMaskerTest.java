package com.clinic.booking.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.LoggingEvent;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PRD §15.6: "Application logs never contain a full email or phone number... before any
 * log line is emitted, including in stack traces from validation exceptions." Covers the
 * masking rule itself ({@link LogPiiMasker}) and its wiring into the two Logback converters
 * {@code logback-spring.xml} registers as the {@code %maskedMsg}/{@code %maskedEx}
 * conversion words.
 */
class LogPiiMaskerTest {

    private static final Logger LOGGER = (Logger) LoggerFactory.getLogger("test.logger");

    @Test
    void mask_replacesAFullEmailAddress_keepingFirstLetterAndDomain() {
        assertThat(LogPiiMasker.mask("Sending notification to jordan@example.com for appointment X"))
                .isEqualTo("Sending notification to j***@example.com for appointment X");
    }

    @Test
    void mask_replacesAFullE164PhoneNumber_keepingPrefixAndLastFourDigits() {
        assertThat(LogPiiMasker.mask("Contact phone: +14155551234"))
                .isEqualTo("Contact phone: +1415***1234");
    }

    @Test
    void mask_replacesMultiplePiiValuesInTheSameLine() {
        assertThat(LogPiiMasker.mask("jordan@example.com / +14155551234"))
                .isEqualTo("j***@example.com / +1415***1234");
    }

    @Test
    void mask_leavesNonPiiTextUnchanged() {
        assertThat(LogPiiMasker.mask("Sending CONFIRMED notification email for appointment abc-123"))
                .isEqualTo("Sending CONFIRMED notification email for appointment abc-123");
    }

    @Test
    void logMaskingConverter_masksTheFormattedMessage() {
        LoggingEvent event = new LoggingEvent(
                Logger.class.getName(), LOGGER, Level.INFO,
                "Sending {} notification email to {} for appointment {}",
                null, new Object[] { "CONFIRMED", "jordan@example.com", "abc-123" });

        String converted = new LogMaskingConverter().convert(event);

        assertThat(converted)
                .isEqualTo("Sending CONFIRMED notification email to j***@example.com for appointment abc-123")
                .doesNotContain("jordan@example.com");
    }

    @Test
    void logMaskingExceptionConverter_masksPiiEmbeddedInAThrowablesMessage() {
        Throwable failure = new IllegalStateException("Failed to notify jordan@example.com at +14155551234");
        LoggingEvent event = new LoggingEvent(
                Logger.class.getName(), LOGGER, Level.ERROR, "Notification failed", failure, null);

        LogMaskingExceptionConverter converter = new LogMaskingExceptionConverter();
        converter.start();
        String converted = converter.convert(event);

        assertThat(converted)
                .contains("j***@example.com")
                .contains("+1415***1234")
                .doesNotContain("jordan@example.com")
                .doesNotContain("+14155551234");
    }
}
