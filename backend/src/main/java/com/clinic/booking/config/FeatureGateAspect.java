package com.clinic.booking.config;

import com.clinic.booking.common.exception.FeatureDisabledException;
import io.micrometer.core.instrument.Counter;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * Enforces {@link FeatureGate} before the annotated controller method body runs
 * (PRD §6/§10): "Every gated controller method checks
 * FeatureFlagService.isEnabled(...) as the first statement before any other
 * validation." Blocks are logged at INFO, not WARN/ERROR, since this is
 * expected, intentional behavior, not a fault (§6's Logging row).
 */
@Aspect
@Component
public class FeatureGateAspect {

    private static final Logger log = LoggerFactory.getLogger(FeatureGateAspect.class);

    private final FeatureFlagService featureFlagService;
    private final Counter flagBlockedCounter;

    public FeatureGateAspect(
            FeatureFlagService featureFlagService, @Qualifier("flagBlockedCounter") Counter flagBlockedCounter) {
        this.featureFlagService = featureFlagService;
        this.flagBlockedCounter = flagBlockedCounter;
    }

    @Before("@annotation(featureGate)")
    public void enforce(JoinPoint joinPoint, FeatureGate featureGate) {
        String flagName = featureGate.value();
        if (!featureFlagService.isEnabled(flagName)) {
            log.info("Blocked request to {} - feature flag '{}' is disabled",
                    joinPoint.getSignature().toShortString(), flagName);
            flagBlockedCounter.increment();
            throw new FeatureDisabledException();
        }
    }
}
