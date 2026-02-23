package com.austinhong22.krwstablehub.service;

import com.austinhong22.krwstablehub.persistence.model.EpochEntity;
import com.example.clearinghub.config.ClearingProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class EpochScheduler {

    private static final int MAX_EPOCHS_PER_TICK = 120;

    private final EpochService epochService;
    private final EpochCloseService epochCloseService;
    private final ClearingProperties clearingProperties;

    @Scheduled(fixedDelay = 1000L)
    public void closeDueEpochs() {
        for (int i = 0; i < MAX_EPOCHS_PER_TICK; i++) {
            EpochEntity openEpoch = epochService.getOrCreateOpenEpoch();
            if (!isDue(openEpoch, Instant.now())) {
                return;
            }
            epochCloseService.closeEpoch(openEpoch.getId());
            epochService.createNextOpenEpoch(openEpoch);
        }
    }

    private boolean isDue(EpochEntity epoch, Instant now) {
        return !epoch.getOpenedAt().plusSeconds(clearingProperties.epoch().seconds()).isAfter(now);
    }
}
