package com.clinic.booking.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Reads {@code feature_flags} (§7.9) through a 10-second Caffeine cache so a
 * toggle takes effect within 10 seconds cluster-wide without hitting the
 * database on every request (§6/§10).
 */
@Service
public class FeatureFlagService {

    /** The only flag defined so far (§7.9 seed row); used by the gated endpoints in §6. */
    public static final String ENABLE_ONLINE_BOOKING = "enable_online_booking";

    private final FeatureFlagRepository featureFlagRepository;
    private final Cache<String, Boolean> cache;

    @Autowired
    public FeatureFlagService(FeatureFlagRepository featureFlagRepository) {
        this(featureFlagRepository, Ticker.systemTicker());
    }

    /** Package-private: lets tests supply a fake ticker to assert TTL behavior without a real 10s wait. */
    FeatureFlagService(FeatureFlagRepository featureFlagRepository, Ticker ticker) {
        this.featureFlagRepository = featureFlagRepository;
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(10))
                .ticker(ticker)
                .build();
    }

    public boolean isEnabled(String flagName) {
        return Boolean.TRUE.equals(cache.get(flagName, this::loadFromDatabase));
    }

    /**
     * §8.17: a flag write evicts this instance's cache entry synchronously so its own next
     * request sees the change immediately, rather than waiting on the passive 10s TTL that
     * still governs convergence for *other* instances in the cluster.
     */
    public void evict(String flagName) {
        cache.invalidate(flagName);
    }

    private Boolean loadFromDatabase(String flagName) {
        return featureFlagRepository.findById(flagName)
                .map(FeatureFlag::isEnabled)
                .orElse(false);
    }
}
