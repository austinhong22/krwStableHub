package com.austinhong22.krwstablehub.service;

import com.austinhong22.krwstablehub.api.error.ApiException;
import com.austinhong22.krwstablehub.persistence.model.EpochEntity;
import com.austinhong22.krwstablehub.persistence.model.EpochStatus;
import com.austinhong22.krwstablehub.persistence.model.SettlementInstructionEntity;
import com.austinhong22.krwstablehub.persistence.model.SettlementStatus;
import com.austinhong22.krwstablehub.persistence.repository.EpochRepository;
import com.austinhong22.krwstablehub.persistence.repository.SettlementInstructionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class EpochCloseService {

    private final EpochRepository epochRepository;
    private final SettlementInstructionRepository settlementInstructionRepository;
    private final NettingService nettingService;

    @Transactional
    public EpochEntity closeEpoch(Long epochId) {
        EpochEntity epoch = epochRepository.findByIdForUpdate(epochId).orElseThrow(
                () -> new ApiException(HttpStatus.NOT_FOUND, "EPOCH_NOT_FOUND", "Epoch not found: " + epochId)
        );

        if (epoch.getStatus() == EpochStatus.OPEN) {
            epoch.setStatus(EpochStatus.NETTING);
            epochRepository.save(epoch);
        }

        if (epoch.getStatus() == EpochStatus.NETTING) {
            nettingService.netEpoch(epoch);
            epoch.setStatus(EpochStatus.NETTED);
            if (epoch.getClosedAt() == null) {
                epoch.setClosedAt(Instant.now());
            }
            epochRepository.save(epoch);
        }

        if (epoch.getStatus() == EpochStatus.NETTED) {
            ensureSettlementInstruction(epoch);
        }

        return epoch;
    }

    private SettlementInstructionEntity ensureSettlementInstruction(EpochEntity epoch) {
        SettlementInstructionEntity existing = settlementInstructionRepository.findByEpochId(epoch.getId()).orElse(null);
        if (existing != null) {
            return existing;
        }

        SettlementInstructionEntity instruction = new SettlementInstructionEntity();
        instruction.setEpoch(epoch);
        instruction.setStatus(SettlementStatus.CREATED);
        instruction.setAttemptCount(0);
        instruction.setTxHash(null);
        instruction.setNextRetryAt(null);
        instruction.setLastError(null);

        try {
            return settlementInstructionRepository.save(instruction);
        } catch (DataIntegrityViolationException exception) {
            return settlementInstructionRepository.findByEpochId(epoch.getId()).orElseThrow(
                    () -> new IllegalStateException("Failed to create settlement instruction", exception)
            );
        }
    }
}
