package com.clinic.booking.config;

import com.github.benmanes.caffeine.cache.Ticker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link FeatureFlagService} (PRD §6/§10): "cached in-application
 * with a 10-second TTL." A fake {@link Ticker} is injected so the TTL can be
 * proven deterministically without a real 10-second sleep.
 */
@ExtendWith(MockitoExtension.class)
class FeatureFlagServiceTest {

    private static final String FLAG_NAME = FeatureFlagService.ENABLE_ONLINE_BOOKING;

    @Mock
    private FeatureFlagRepository featureFlagRepository;

    private static final class MutableTicker implements Ticker {
        private long nanos = 0L;

        @Override
        public long read() {
            return nanos;
        }

        void advance(long duration, TimeUnit unit) {
            nanos += unit.toNanos(duration);
        }
    }

    @Test
    void isEnabled_cachesResultAndDoesNotHitTheRepositoryOnEverySecondCall() {
        MutableTicker ticker = new MutableTicker();
        FeatureFlagService service = new FeatureFlagService(featureFlagRepository, ticker);
        when(featureFlagRepository.findById(FLAG_NAME)).thenReturn(Optional.of(flag(false)));

        assertThat(service.isEnabled(FLAG_NAME)).isFalse();
        assertThat(service.isEnabled(FLAG_NAME)).isFalse();

        verify(featureFlagRepository, times(1)).findById(FLAG_NAME);
    }

    @Test
    void isEnabled_refetchesAfterTenSecondTtlElapses() {
        MutableTicker ticker = new MutableTicker();
        FeatureFlagService service = new FeatureFlagService(featureFlagRepository, ticker);
        when(featureFlagRepository.findById(FLAG_NAME))
                .thenReturn(Optional.of(flag(false)))
                .thenReturn(Optional.of(flag(true)));

        assertThat(service.isEnabled(FLAG_NAME)).isFalse();

        ticker.advance(11, TimeUnit.SECONDS);

        assertThat(service.isEnabled(FLAG_NAME)).isTrue();
        verify(featureFlagRepository, times(2)).findById(FLAG_NAME);
    }

    @Test
    void isEnabled_doesNotRefetchBeforeTenSecondTtlElapses() {
        MutableTicker ticker = new MutableTicker();
        FeatureFlagService service = new FeatureFlagService(featureFlagRepository, ticker);
        when(featureFlagRepository.findById(FLAG_NAME)).thenReturn(Optional.of(flag(false)));

        assertThat(service.isEnabled(FLAG_NAME)).isFalse();

        ticker.advance(9, TimeUnit.SECONDS);

        assertThat(service.isEnabled(FLAG_NAME)).isFalse();
        verify(featureFlagRepository, times(1)).findById(FLAG_NAME);
    }

    @Test
    void isEnabled_defaultsToFalse_whenFlagRowIsMissing() {
        when(featureFlagRepository.findById("unknown_flag")).thenReturn(Optional.empty());
        FeatureFlagService service = new FeatureFlagService(featureFlagRepository, Ticker.systemTicker());

        assertThat(service.isEnabled("unknown_flag")).isFalse();
    }

    private FeatureFlag flag(boolean enabled) {
        return new FeatureFlag(FLAG_NAME, enabled, "SYSTEM_SEED", Instant.now());
    }
}
