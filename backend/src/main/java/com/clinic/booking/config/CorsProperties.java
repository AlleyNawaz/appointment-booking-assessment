package com.clinic.booking.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/** §15.8: the explicit CORS origin allowlist — never a wildcard, since credentials are allowed. */
@ConfigurationProperties(prefix = "booking.cors")
public class CorsProperties {

    private List<String> allowedOrigins = List.of("http://localhost:4200");

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }
}
