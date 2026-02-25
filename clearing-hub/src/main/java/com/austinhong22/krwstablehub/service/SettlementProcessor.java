package com.austinhong22.krwstablehub.service;

import com.austinhong22.krwstablehub.infra.ledger.SettlementLedgerClient;
import com.austinhong22.krwstablehub.persistence.model.EpochEntity;
import com.austinhong22.krwstablehub.persistence.model.EpochStatus;
import com.austinhong22.krwstablehub.persistence.model.NetPositionEntity;
import com.austinhong22.krwstablehub.persistence.model.OutboxEventEntity;
import com.austinhong22.krwstablehub.persistence.model.SettlementInstructionEntity;
import com.austinhong22.krwstablehub.persistence.model.SettlementStatus;
import com.austinhong22.krwstablehub.persistence.repository.EpochRepository;
import com.austinhong22.krwstablehub.persistence.repository.NetPositionRepository;
import com.austinhong22.krwstablehub.persistence.repository.OutboxEventRepository;
import com.austinhong22.krwstablehub.persistence.repository.SettlementInstructionRepository;
import com.example.clearinghub.config.ClearingProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class SettlementProcessor {

    private static final Duration RETRY_DELAY = Duration.ofSeconds(5);
    private static final int MAX_LAST_ERROR_LENGTH = 2000;
    private static final String OUTBOX_STATUS_PENDING = "PENDING";
    private static final String OUTBOX_AGGREGATE_TYPE_EPOCH = "EPOCH";
    private static final String OUTBOX_EVENT_TYPE_SETTLED = "EPOCH_SETTLED";

    private final SettlementInstructionRepository settlementInstructionRepository;
    private final EpochRepository epochRepository;
    private final NetPositionRepository netPositionRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final SettlementLedgerClient settlementLedgerClient;
    private final ClearingProperties clearingProperties;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<Long> findPendingInstructionIds(Instant now) {
        int batchSize = requireBatchSize();
        List<Long> instructionIds = new ArrayList<>(batchSize);

        List<SettlementInstructionEntity> created = settlementInstructionRepository.findByStatusOrderByIdAsc(
                SettlementStatus.CREATED,
                PageRequest.of(0, batchSize)
        );
        for (SettlementInstructionEntity instruction : created) {
            instructionIds.add(instruction.getId());
        }

        int remaining = batchSize - instructionIds.size();
        if (remaining <= 0) {
            return instructionIds;
        }

        List<SettlementInstructionEntity> retrying = settlementInstructionRepository.findByStatusAndNextRetryAtLessThanEqualOrderByIdAsc(
                SettlementStatus.RETRYING,
                now,
                PageRequest.of(0, remaining)
        );
        for (SettlementInstructionEntity instruction : retrying) {
            instructionIds.add(instruction.getId());
        }
        return instructionIds;
    }

    @Transactional
    public void processInstruction(Long instructionId) {
        Instant now = Instant.now();
        SettlementInstructionEntity instruction = settlementInstructionRepository.findByIdForUpdate(instructionId).orElse(null);
        if (instruction == null || !isProcessable(instruction, now)) {
            return;
        }

        EpochEntity epoch = epochRepository.findByIdForUpdate(instruction.getEpoch().getId()).orElseThrow(
                () -> new IllegalStateException("Epoch not found for settlement instruction " + instructionId)
        );
        if (epoch.getStatus() != EpochStatus.NETTED) {
            return;
        }

        int maxAttempts = requireMaxAttempts();
        int nextAttempt = instruction.getAttemptCount() + 1;
        instruction.setAttemptCount(nextAttempt);
        if (nextAttempt > maxAttempts) {
            instruction.setStatus(SettlementStatus.FAILED);
            instruction.setNextRetryAt(null);
            instruction.setLastError("Max settlement attempts exceeded");
            epoch.setStatus(EpochStatus.FAILED);
            return;
        }

        instruction.setStatus(SettlementStatus.RETRYING);
        instruction.setNextRetryAt(null);
        instruction.setLastError(null);
        settlementInstructionRepository.saveAndFlush(instruction);

        List<NetPositionEntity> netPositions = netPositionRepository.findByEpochIdOrderByIdAsc(epoch.getId());
        Map<Long, String> ledgerAddressByParticipantId = mapLedgerAddressByParticipantId(netPositions);
        List<String> participants = new ArrayList<>(netPositions.size());
        List<Long> deltas = new ArrayList<>(netPositions.size());
        for (NetPositionEntity position : netPositions) {
            Long participantId = position.getParticipant().getId();
            String ledgerAddress = ledgerAddressByParticipantId.get(participantId);
            if (!StringUtils.hasText(ledgerAddress)) {
                throw new IllegalStateException("Missing ledger address for participant " + participantId);
            }
            participants.add(ledgerAddress);
            deltas.add(position.getNetAmountKrw());
        }

        try {
            String txHash = settlementLedgerClient.settle(epoch.getId(), participants, deltas);
            instruction.setStatus(SettlementStatus.SETTLED);
            instruction.setTxHash(txHash);
            instruction.setNextRetryAt(null);
            instruction.setLastError(null);

            epoch.setStatus(EpochStatus.SETTLED);
            outboxEventRepository.save(buildSettledOutboxEvent(epoch.getId(), txHash, now));
        } catch (Exception exception) {
            instruction.setLastError(truncateError(exception));
            if (instruction.getAttemptCount() >= maxAttempts) {
                instruction.setStatus(SettlementStatus.FAILED);
                instruction.setNextRetryAt(null);
                epoch.setStatus(EpochStatus.FAILED);
            } else {
                instruction.setStatus(SettlementStatus.RETRYING);
                instruction.setNextRetryAt(now.plus(RETRY_DELAY));
            }
            log.warn("Settlement attempt failed for instructionId={}", instructionId, exception);
        }
    }

    private Map<Long, String> mapLedgerAddressByParticipantId(List<NetPositionEntity> netPositions) {
        Map<Long, String> addresses = new LinkedHashMap<>();
        for (NetPositionEntity netPosition : netPositions) {
            addresses.put(netPosition.getParticipant().getId(), netPosition.getParticipant().getLedgerAddress());
        }
        return addresses;
    }

    private OutboxEventEntity buildSettledOutboxEvent(Long epochId, String txHash, Instant now) {
        OutboxEventEntity outboxEvent = new OutboxEventEntity();
        outboxEvent.setAggregateType(OUTBOX_AGGREGATE_TYPE_EPOCH);
        outboxEvent.setAggregateId(epochId);
        outboxEvent.setEventType(OUTBOX_EVENT_TYPE_SETTLED);
        outboxEvent.setPayloadJson(payloadJson(epochId, txHash, now));
        outboxEvent.setStatus(OUTBOX_STATUS_PENDING);
        outboxEvent.setAvailableAt(now);
        return outboxEvent;
    }

    private String payloadJson(Long epochId, String txHash, Instant now) {
        try {
            return objectMapper.createObjectNode()
                    .put("epochId", epochId)
                    .put("txHash", txHash)
                    .put("settledAt", now.toString())
                    .toString();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to build settlement outbox payload", exception);
        }
    }

    private String truncateError(Exception exception) {
        String message = exception.getMessage();
        if (!StringUtils.hasText(message)) {
            message = exception.getClass().getSimpleName();
        }
        if (message.length() <= MAX_LAST_ERROR_LENGTH) {
            return message;
        }
        return message.substring(0, MAX_LAST_ERROR_LENGTH);
    }

    private boolean isProcessable(SettlementInstructionEntity instruction, Instant now) {
        if (instruction.getStatus() == SettlementStatus.CREATED) {
            return true;
        }
        return instruction.getStatus() == SettlementStatus.RETRYING
                && instruction.getNextRetryAt() != null
                && !instruction.getNextRetryAt().isAfter(now);
    }

    private int requireBatchSize() {
        int batchSize = clearingProperties.settlement().batchSize();
        if (batchSize <= 0) {
            throw new IllegalStateException("clearing.settlement.batch-size must be greater than 0");
        }
        return batchSize;
    }

    private int requireMaxAttempts() {
        int maxAttempts = clearingProperties.settlement().maxAttempts();
        if (maxAttempts <= 0) {
            throw new IllegalStateException("clearing.settlement.max-attempts must be greater than 0");
        }
        return maxAttempts;
    }
}
