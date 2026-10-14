package com.paynest.common.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "paynest.database")
public final class DatabaseLockProperties {

    private static final Duration MINIMUM_TIMEOUT = Duration.ofMillis(1);
    private static final Duration MAXIMUM_TIMEOUT = Duration.ofMillis(Integer.MAX_VALUE);

    private final Duration lockTimeout;

    public DatabaseLockProperties(Duration lockTimeout) {
        if (lockTimeout == null
                || lockTimeout.compareTo(MINIMUM_TIMEOUT) < 0
                || lockTimeout.compareTo(MAXIMUM_TIMEOUT) > 0
                || !lockTimeout.equals(Duration.ofMillis(lockTimeout.toMillis()))) {
            throw new IllegalArgumentException(
                    "Database lock timeout must be between 1ms and 2147483647ms");
        }
        this.lockTimeout = lockTimeout;
    }

    public Duration lockTimeout() {
        return lockTimeout;
    }
}
