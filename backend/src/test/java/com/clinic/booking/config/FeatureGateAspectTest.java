package com.clinic.booking.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.clinic.booking.common.exception.FeatureDisabledException;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link FeatureGateAspect} (PRD §6/§10): the flag check must
 * run before the gated method body, and a block must be logged at INFO — not
 * WARN/ERROR — since it is expected, intentional behavior, not a fault.
 *
 * <p>Uses a real {@link FeatureFlagService} backed by a mocked
 * {@link FeatureFlagRepository} (an interface, mocked via a JDK proxy) rather
 * than mocking {@link FeatureFlagService} itself — {@code FeatureFlagService}
 * is already covered by {@link FeatureFlagServiceTest}, and this avoids
 * depending on Mockito's inline mock maker being able to subclass a concrete
 * class on every JDK this project might run on.
 */
@ExtendWith(MockitoExtension.class)
class FeatureGateAspectTest {

    @Mock
    private FeatureFlagRepository featureFlagRepository;

    @Mock
    private JoinPoint joinPoint;

    @Mock
    private Signature signature;

    private Logger aspectLogger;
    private ListAppender<ILoggingEvent> logAppender;

    private static class DummyGatedTarget {
        @FeatureGate(FeatureFlagService.ENABLE_ONLINE_BOOKING)
        void gatedMethod() {
        }
    }

    @BeforeEach
    void attachLogAppender() {
        aspectLogger = (Logger) LoggerFactory.getLogger(FeatureGateAspect.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        aspectLogger.addAppender(logAppender);
    }

    @AfterEach
    void detachLogAppender() {
        aspectLogger.detachAppender(logAppender);
    }

    @Test
    void enforce_throwsFeatureDisabled_andLogsAtInfo_whenFlagIsOff() throws NoSuchMethodException {
        lenient().when(joinPoint.getSignature()).thenReturn(signature);
        lenient().when(signature.toShortString()).thenReturn("AppointmentTypeController.getAppointmentTypes()");
        when(featureFlagRepository.findById(FeatureFlagService.ENABLE_ONLINE_BOOKING)).thenReturn(Optional.empty());

        FeatureGateAspect aspect = new FeatureGateAspect(new FeatureFlagService(featureFlagRepository));
        FeatureGate annotation = gatedAnnotation();

        assertThatThrownBy(() -> aspect.enforce(joinPoint, annotation))
                .isInstanceOf(FeatureDisabledException.class);

        assertThat(logAppender.list).hasSize(1);
        assertThat(logAppender.list.get(0).getLevel()).isEqualTo(Level.INFO);
    }

    @Test
    void enforce_doesNotThrow_andLogsNothing_whenFlagIsOn() throws NoSuchMethodException {
        when(featureFlagRepository.findById(FeatureFlagService.ENABLE_ONLINE_BOOKING))
                .thenReturn(Optional.of(new FeatureFlag(
                        FeatureFlagService.ENABLE_ONLINE_BOOKING, true, "SYSTEM_SEED", Instant.now())));

        FeatureGateAspect aspect = new FeatureGateAspect(new FeatureFlagService(featureFlagRepository));
        FeatureGate annotation = gatedAnnotation();

        aspect.enforce(joinPoint, annotation);

        assertThat(logAppender.list).isEmpty();
    }

    private FeatureGate gatedAnnotation() throws NoSuchMethodException {
        return DummyGatedTarget.class.getDeclaredMethod("gatedMethod").getAnnotation(FeatureGate.class);
    }
}
