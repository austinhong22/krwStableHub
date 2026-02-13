package com.example.clearinghub.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "clearing")
public record ClearingProperties(
        Epoch epoch,
        Settlement settlement
) {
    public record Epoch(
            long seconds
    ) {
    }

    public record Settlement(
            int maxAttempts,
            int batchSize
    ) {
    }
}
