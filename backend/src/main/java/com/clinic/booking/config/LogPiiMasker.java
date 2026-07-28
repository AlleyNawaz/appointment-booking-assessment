package com.clinic.booking.config;

import java.util.regex.Pattern;

/**
 * PRD §15.6: masks a full email/phone number wherever one appears in already-formatted log
 * text — {@code jordan@example.com} becomes {@code j***@example.com}, {@code +14155551234}
 * becomes {@code +1415***1234} — regardless of which log statement produced the text. Shared
 * by both {@link LogMaskingConverter} (the formatted message) and
 * {@link LogMaskingExceptionConverter} (stack traces), so masking logic lives in exactly one
 * place rather than being duplicated per converter.
 */
final class LogPiiMasker {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("[A-Za-z0-9][A-Za-z0-9._%+-]*@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern PHONE_PATTERN = Pattern.compile("\\+[1-9]\\d{7,14}");

    private LogPiiMasker() {
    }

    static String mask(String input) {
        if (input == null) {
            return null;
        }
        String withMaskedEmails = EMAIL_PATTERN.matcher(input).replaceAll(match -> maskEmail(match.group()));
        return PHONE_PATTERN.matcher(withMaskedEmails).replaceAll(match -> maskPhone(match.group()));
    }

    private static String maskEmail(String email) {
        int at = email.indexOf('@');
        return email.charAt(0) + "***" + email.substring(at);
    }

    private static String maskPhone(String phone) {
        if (phone.length() <= 9) {
            return phone.substring(0, 5) + "***";
        }
        return phone.substring(0, 5) + "***" + phone.substring(phone.length() - 4);
    }
}
