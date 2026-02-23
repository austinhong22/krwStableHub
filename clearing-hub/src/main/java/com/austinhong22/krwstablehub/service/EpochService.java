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
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class EpochService {

    private final EpochRepository epochRepository;
    private final ClearingProperties clearingProperties;

    @Transactional
    public EpochEntity getOrCreateOpenEpoch() {
        return epochRepository.findFirstByStatusOrderByEpochNoAsc(EpochStatus.OPEN)
                .orElseGet(this::createOpenEpochAlignedToCurrentWindow);
    }

    @Transactional
    public EpochEntity createOpenEpoch() {
        return createOpenEpochAlignedToCurrentWindow();
    }

    @Transactional(readOnly = true)
    public Optional<EpochEntity> findOldestOpenEpoch() {
        return epochRepository.findFirstByStatusOrderByEpochNoAsc(EpochStatus.OPEN);
    }

    @Transactional
    public EpochEntity createNextOpenEpoch(EpochEntity epoch) {
        long epochSeconds = requireEpochSeconds();
        long nextEpochNo = epoch.getEpochNo() + 1;
        Instant nextOpenedAt = epoch.getOpenedAt().plusSeconds(epochSeconds);
        return createOrGetOpenEpoch(nextEpochNo, nextOpenedAt, epochSeconds);
    }

    private EpochEntity createOpenEpochAlignedToCurrentWindow() {
        long epochSeconds = requireEpochSeconds();
        Instant now = Instant.now();
        long epochNo = now.getEpochSecond() / epochSeconds;
        Instant openedAt = Instant.ofEpochSecond(epochNo * epochSeconds);
        return createOrGetOpenEpoch(epochNo, openedAt, epochSeconds);
    }

    private EpochEntity createOrGetOpenEpoch(long initialEpochNo, Instant initialOpenedAt, long epochSeconds) {
        long epochNo = initialEpochNo;
        Instant openedAt = initialOpenedAt;

        while (true) {
            EpochEntity existing = epochRepository.findByEpochNo(epochNo).orElse(null);
            if (existing != null) {
                if (existing.getStatus() == EpochStatus.OPEN) {
                    return existing;
                }
                epochNo = epochNo + 1;
                openedAt = openedAt.plusSeconds(epochSeconds);
                continue;
            }

            EpochEntity epoch = new EpochEntity();
            epoch.setEpochNo(epochNo);
            epoch.setStatus(EpochStatus.OPEN);
            epoch.setOpenedAt(openedAt);

            try {
                return epochRepository.save(epoch);
            } catch (DataIntegrityViolationException exception) {
                // Another transaction inserted the same row; retry with fresh reads.
            }
        }
    }

    private long requireEpochSeconds() {
        long epochSeconds = clearingProperties.epoch().seconds();
        if (epochSeconds <= 0) {
            throw new IllegalStateException("clearing.epoch.seconds must be greater than 0");
        }
        return epochSeconds;
    }
}
