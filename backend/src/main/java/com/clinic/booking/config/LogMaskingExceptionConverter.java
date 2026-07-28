package com.clinic.booking.config;

import ch.qos.logback.classic.pattern.ThrowableProxyConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

/**
 * PRD §15.6: masking must also apply "in stack traces from validation exceptions" — this
 * wraps the standard stack-trace rendering ({@link ThrowableProxyConverter}) and masks its
 * output the same way {@link LogMaskingConverter} masks the message. Registered as the
 * {@code %maskedEx} conversion word in {@code logback-spring.xml}.
 */
public class LogMaskingExceptionConverter extends ThrowableProxyConverter {

    @Override
    public String convert(ILoggingEvent event) {
        return LogPiiMasker.mask(super.convert(event));
    }
}
