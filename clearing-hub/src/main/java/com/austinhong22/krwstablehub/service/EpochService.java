package com.austinhong22.krwstablehub.service;

import com.austinhong22.krwstablehub.persistence.model.EpochEntity;
import com.austinhong22.krwstablehub.persistence.model.EpochStatus;
import com.austinhong22.krwstablehub.persistence.repository.EpochRepository;
import com.example.clearinghub.config.ClearingProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class EpochService {

    private final EpochRepository epochRepository;
    private final ClearingProperties clearingProperties;

    @Transactional
    public EpochEntity getOrCreateOpenEpoch() {
        return epochRepository.findFirstByStatusOrderByEpochNoAsc(EpochStatus.OPEN)
                .orElseGet(this::createOpenEpoch);
    }

    @Transactional
    public EpochEntity createOpenEpoch() {
        long epochSeconds = clearingProperties.epoch().seconds();
        if (epochSeconds <= 0) {
            throw new IllegalStateException("clearing.epoch.seconds must be greater than 0");
        }

        Instant now = Instant.now();
        long epochNo = now.getEpochSecond() / epochSeconds;
        Instant openedAt = Instant.ofEpochSecond(epochNo * epochSeconds);

        EpochEntity epoch = new EpochEntity();
        epoch.setEpochNo(epochNo);
        epoch.setStatus(EpochStatus.OPEN);
        epoch.setOpenedAt(openedAt);

        try {
            return epochRepository.save(epoch);
        } catch (DataIntegrityViolationException exception) {
            return epochRepository.findByEpochNo(epochNo)
                    .orElseThrow(() -> new IllegalStateException("Failed to create OPEN epoch", exception));
        }
    }
}
