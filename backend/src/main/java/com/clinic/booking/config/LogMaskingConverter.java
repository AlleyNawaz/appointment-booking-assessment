package com.clinic.booking.config;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

/**
 * PRD §15.6: a logging {@code PatternLayout} converter masking email/phone in the formatted
 * log message before any line is emitted. Registered as the {@code %maskedMsg} conversion
 * word in {@code logback-spring.xml}, replacing {@code %msg} everywhere in the pattern —
 * this is what makes masking apply to every existing and future log statement uniformly,
 * without auditing individual call sites.
 */
public class LogMaskingConverter extends ClassicConverter {

    @Override
    public String convert(ILoggingEvent event) {
        return LogPiiMasker.mask(event.getFormattedMessage());
    }
}
