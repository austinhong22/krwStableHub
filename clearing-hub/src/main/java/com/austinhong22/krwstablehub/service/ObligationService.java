package com.austinhong22.krwstablehub.service;

import com.austinhong22.krwstablehub.api.error.ApiException;
import com.austinhong22.krwstablehub.api.obligation.dto.ObligationRequest;
import com.austinhong22.krwstablehub.api.obligation.dto.ObligationResponse;
import com.austinhong22.krwstablehub.common.Hashing;
import com.austinhong22.krwstablehub.persistence.model.EpochEntity;
import com.austinhong22.krwstablehub.persistence.model.ObligationEntity;
import com.austinhong22.krwstablehub.persistence.model.ObligationStatus;
import com.austinhong22.krwstablehub.persistence.model.ParticipantEntity;
import com.austinhong22.krwstablehub.persistence.repository.ObligationRepository;
import com.austinhong22.krwstablehub.persistence.repository.ParticipantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ObligationService {

    private final ObligationRepository obligationRepository;
    private final ParticipantRepository participantRepository;
    private final EpochService epochService;

    @Transactional
    public ObligationResponse receive(ObligationRequest request) {
        String requestHash = computeRequestHash(request);

        ObligationEntity existing = obligationRepository.findByTxId(request.normalizedTxId()).orElse(null);
        if (existing != null) {
            return validateExistingAndRespond(existing, requestHash, request.normalizedPayAsset());
        }

        ParticipantEntity payer = findParticipant(request.normalizedPayer(), "payer");
        ParticipantEntity payee = findParticipant(request.normalizedPayee(), "payee");
        EpochEntity epoch = epochService.getOrCreateOpenEpoch();

        ObligationEntity obligation = new ObligationEntity();
        obligation.setTxId(request.normalizedTxId());
        obligation.setRequestHash(requestHash);
        obligation.setDebtorParticipant(payer);
        obligation.setCreditorParticipant(payee);
        obligation.setAmountKrw(request.amount());
        obligation.setStatus(determineStatus(payer, epoch, request.amount()));
        obligation.setEpoch(epoch);

        try {
            ObligationEntity saved = obligationRepository.save(obligation);
            return toResponse(saved, request.normalizedPayAsset());
        } catch (DataIntegrityViolationException exception) {
            return resolveConcurrentInsert(request.normalizedTxId(), requestHash, request.normalizedPayAsset(), exception);
        }
    }

    private ObligationResponse validateExistingAndRespond(ObligationEntity existing, String requestHash, String payAsset) {
        if (!existing.getRequestHash().equals(requestHash)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "IDEMPOTENCY_CONFLICT",
                    "txId already exists with a different request payload"
            );
        }
        return toResponse(existing, payAsset);
    }

    private ParticipantEntity findParticipant(String participantCode, String role) {
        return participantRepository.findByParticipantCode(participantCode).orElseThrow(
                () -> new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "PARTICIPANT_NOT_FOUND",
                        "Unknown " + role + " participant code: " + participantCode
                )
        );
    }

    private ObligationStatus determineStatus(ParticipantEntity payer, EpochEntity epoch, long amountKrw) {
        long currentDelta = obligationRepository.sumNetDeltaByEpochAndParticipantAndStatus(
                epoch.getId(),
                payer.getId(),
                ObligationStatus.ACCEPTED
        );
        long projectedDelta = currentDelta - amountKrw;
        long projectedNetDebit = Math.max(0L, -projectedDelta);
        return projectedNetDebit > payer.getNetDebitCapKrw() ? ObligationStatus.HELD : ObligationStatus.ACCEPTED;
    }

    private ObligationResponse resolveConcurrentInsert(
            String txId,
            String requestHash,
            String payAsset,
            DataIntegrityViolationException exception
    ) {
        ObligationEntity existing = obligationRepository.findByTxId(txId).orElseThrow(
                () -> new ApiException(
                        HttpStatus.CONFLICT,
                        "OBLIGATION_CONFLICT",
                        "Failed to create obligation due to concurrent update",
                        exception
                )
        );
        return validateExistingAndRespond(existing, requestHash, payAsset);
    }

    private String computeRequestHash(ObligationRequest request) {
        return Hashing.sha256Hex(
                String.join(
                        "|",
                        request.normalizedTxId(),
                        request.normalizedPayer(),
                        request.normalizedPayee(),
                        request.normalizedPayAsset(),
                        request.amount().toString()
                )
        );
    }

    private ObligationResponse toResponse(ObligationEntity obligation, String payAsset) {
        Long epochNo = obligation.getEpoch() == null ? null : obligation.getEpoch().getEpochNo();
        return new ObligationResponse(
                obligation.getId(),
                obligation.getTxId(),
                obligation.getDebtorParticipant().getParticipantCode(),
                obligation.getCreditorParticipant().getParticipantCode(),
                payAsset,
                obligation.getAmountKrw(),
                obligation.getStatus().name(),
                epochNo,
                obligation.getCreatedAt()
        );
    }
}
