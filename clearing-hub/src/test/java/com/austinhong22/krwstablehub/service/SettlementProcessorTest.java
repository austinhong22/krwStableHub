package com.austinhong22.krwstablehub.service;

import com.austinhong22.krwstablehub.infra.ledger.SettlementLedgerClient;
import com.austinhong22.krwstablehub.persistence.model.EpochEntity;
import com.austinhong22.krwstablehub.persistence.model.EpochStatus;
import com.austinhong22.krwstablehub.persistence.model.NetPositionEntity;
import com.austinhong22.krwstablehub.persistence.model.OutboxEventEntity;
import com.austinhong22.krwstablehub.persistence.model.ParticipantEntity;
import com.austinhong22.krwstablehub.persistence.model.SettlementInstructionEntity;
import com.austinhong22.krwstablehub.persistence.model.SettlementStatus;
import com.austinhong22.krwstablehub.persistence.repository.EpochRepository;
import com.austinhong22.krwstablehub.persistence.repository.NetPositionRepository;
import com.austinhong22.krwstablehub.persistence.repository.OutboxEventRepository;
import com.austinhong22.krwstablehub.persistence.repository.SettlementInstructionRepository;
import com.example.clearinghub.config.ClearingProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SettlementProcessorTest {

    @Mock
    private SettlementInstructionRepository settlementInstructionRepository;

    @Mock
    private EpochRepository epochRepository;

    @Mock
    private NetPositionRepository netPositionRepository;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private SettlementLedgerClient settlementLedgerClient;

    private SettlementProcessor settlementProcessor;

    @BeforeEach
    void setUp() {
        ClearingProperties properties = new ClearingProperties(
                new ClearingProperties.Epoch(60L),
                new ClearingProperties.Settlement(3, 4)
        );
        settlementProcessor = new SettlementProcessor(
                settlementInstructionRepository,
                epochRepository,
                netPositionRepository,
                outboxEventRepository,
                settlementLedgerClient,
                properties,
                new ObjectMapper()
        );
    }

    @Test
    void shouldFindCreatedThenDueRetryingInstructions() {
        SettlementInstructionEntity created = instruction(1L, SettlementStatus.CREATED, 0, null);
        SettlementInstructionEntity retrying = instruction(2L, SettlementStatus.RETRYING, 1, Instant.now().minusSeconds(1));

        when(settlementInstructionRepository.findByStatusOrderByIdAsc(eq(SettlementStatus.CREATED), any(Pageable.class)))
                .thenReturn(List.of(created));
        when(settlementInstructionRepository.findByStatusAndNextRetryAtLessThanEqualOrderByIdAsc(
                eq(SettlementStatus.RETRYING),
                any(Instant.class),
                any(Pageable.class)
        )).thenReturn(List.of(retrying));

        List<Long> ids = settlementProcessor.findPendingInstructionIds(Instant.now());

        assertEquals(List.of(1L, 2L), ids);
    }

    @Test
    void shouldSettleInstructionAndCreateOutbox() {
        EpochEntity epoch = epoch(10L, EpochStatus.NETTED);
        SettlementInstructionEntity instruction = instruction(100L, SettlementStatus.CREATED, 0, null);
        instruction.setEpoch(epoch);

        List<NetPositionEntity> positions = List.of(
                netPosition(epoch, participant(1L, "0x1111111111111111111111111111111111111111"), -70L),
                netPosition(epoch, participant(2L, "0x2222222222222222222222222222222222222222"), 70L)
        );

        when(settlementInstructionRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(instruction));
        when(epochRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(epoch));
        when(netPositionRepository.findByEpochIdOrderByIdAsc(10L)).thenReturn(positions);
        when(settlementLedgerClient.settle(
                eq(10L),
                eq(List.of("0x1111111111111111111111111111111111111111", "0x2222222222222222222222222222222222222222")),
                eq(List.of(-70L, 70L))
        )).thenReturn("0xabc");

        settlementProcessor.processInstruction(100L);

        assertEquals(1, instruction.getAttemptCount());
        assertEquals(SettlementStatus.SETTLED, instruction.getStatus());
        assertEquals("0xabc", instruction.getTxHash());
        assertNull(instruction.getLastError());
        assertNull(instruction.getNextRetryAt());
        assertEquals(EpochStatus.SETTLED, epoch.getStatus());

        ArgumentCaptor<OutboxEventEntity> outboxCaptor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(outboxEventRepository).save(outboxCaptor.capture());
        OutboxEventEntity event = outboxCaptor.getValue();
        assertEquals("EPOCH", event.getAggregateType());
        assertEquals(10L, event.getAggregateId());
        assertEquals("EPOCH_SETTLED", event.getEventType());
        assertEquals("PENDING", event.getStatus());
        assertNotNull(event.getAvailableAt());
        assertTrue(event.getPayloadJson().contains("\"txHash\":\"0xabc\""));

        InOrder order = inOrder(settlementInstructionRepository, settlementLedgerClient);
        order.verify(settlementInstructionRepository).saveAndFlush(instruction);
        order.verify(settlementLedgerClient).settle(
                eq(10L),
                eq(List.of("0x1111111111111111111111111111111111111111", "0x2222222222222222222222222222222222222222")),
                eq(List.of(-70L, 70L))
        );
    }

    @Test
    void shouldKeepRetryingWhenLedgerCallFails() {
        EpochEntity epoch = epoch(11L, EpochStatus.NETTED);
        SettlementInstructionEntity instruction = instruction(101L, SettlementStatus.CREATED, 0, null);
        instruction.setEpoch(epoch);

        when(settlementInstructionRepository.findByIdForUpdate(101L)).thenReturn(Optional.of(instruction));
        when(epochRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(epoch));
        when(netPositionRepository.findByEpochIdOrderByIdAsc(11L)).thenReturn(List.of(
                netPosition(epoch, participant(1L, "0x1111111111111111111111111111111111111111"), 0L)
        ));
        when(settlementLedgerClient.settle(eq(11L), org.mockito.ArgumentMatchers.<List<String>>any(), org.mockito.ArgumentMatchers.<List<Long>>any()))
                .thenThrow(new IllegalStateException("ledger offline"));

        settlementProcessor.processInstruction(101L);

        assertEquals(1, instruction.getAttemptCount());
        assertEquals(SettlementStatus.RETRYING, instruction.getStatus());
        assertNotNull(instruction.getNextRetryAt());
        assertTrue(instruction.getLastError().contains("ledger offline"));
        assertEquals(EpochStatus.NETTED, epoch.getStatus());
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    void shouldFailInstructionWhenMaxAttemptsReached() {
        EpochEntity epoch = epoch(12L, EpochStatus.NETTED);
        SettlementInstructionEntity instruction = instruction(102L, SettlementStatus.RETRYING, 2, Instant.now().minusSeconds(1));
        instruction.setEpoch(epoch);

        when(settlementInstructionRepository.findByIdForUpdate(102L)).thenReturn(Optional.of(instruction));
        when(epochRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(epoch));
        when(netPositionRepository.findByEpochIdOrderByIdAsc(12L)).thenReturn(List.of(
                netPosition(epoch, participant(1L, "0x1111111111111111111111111111111111111111"), 0L)
        ));
        when(settlementLedgerClient.settle(eq(12L), org.mockito.ArgumentMatchers.<List<String>>any(), org.mockito.ArgumentMatchers.<List<Long>>any()))
                .thenThrow(new IllegalStateException("contract revert"));

        settlementProcessor.processInstruction(102L);

        assertEquals(3, instruction.getAttemptCount());
        assertEquals(SettlementStatus.FAILED, instruction.getStatus());
        assertNull(instruction.getNextRetryAt());
        assertTrue(instruction.getLastError().contains("contract revert"));
        assertEquals(EpochStatus.FAILED, epoch.getStatus());
        verify(outboxEventRepository, never()).save(any());
    }

    private SettlementInstructionEntity instruction(Long id, SettlementStatus status, int attempts, Instant nextRetryAt) {
        SettlementInstructionEntity instruction = new SettlementInstructionEntity();
        instruction.setId(id);
        instruction.setStatus(status);
        instruction.setAttemptCount(attempts);
        instruction.setNextRetryAt(nextRetryAt);
        return instruction;
    }

    private EpochEntity epoch(Long id, EpochStatus status) {
        EpochEntity epoch = new EpochEntity();
        epoch.setId(id);
        epoch.setEpochNo(id);
        epoch.setStatus(status);
        return epoch;
    }

    private ParticipantEntity participant(Long id, String ledgerAddress) {
        ParticipantEntity participant = new ParticipantEntity();
        participant.setId(id);
        participant.setParticipantCode("P" + id);
        participant.setLedgerAddress(ledgerAddress);
        participant.setNetDebitCapKrw(200_000L);
        return participant;
    }

    private NetPositionEntity netPosition(EpochEntity epoch, ParticipantEntity participant, long delta) {
        NetPositionEntity position = new NetPositionEntity();
        position.setEpoch(epoch);
        position.setParticipant(participant);
        position.setNetAmountKrw(delta);
        return position;
    }
}
