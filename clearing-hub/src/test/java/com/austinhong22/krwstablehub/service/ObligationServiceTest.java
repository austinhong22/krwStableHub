package com.austinhong22.krwstablehub.service;

import com.austinhong22.krwstablehub.api.error.ApiException;
import com.austinhong22.krwstablehub.api.obligation.dto.ObligationRequest;
import com.austinhong22.krwstablehub.api.obligation.dto.ObligationResponse;
import com.austinhong22.krwstablehub.persistence.model.EpochEntity;
import com.austinhong22.krwstablehub.persistence.model.EpochStatus;
import com.austinhong22.krwstablehub.persistence.model.ObligationEntity;
import com.austinhong22.krwstablehub.persistence.model.ObligationStatus;
import com.austinhong22.krwstablehub.persistence.model.ParticipantEntity;
import com.austinhong22.krwstablehub.persistence.repository.ObligationRepository;
import com.austinhong22.krwstablehub.persistence.repository.ParticipantRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ObligationServiceTest {

    @Mock
    private ObligationRepository obligationRepository;

    @Mock
    private ParticipantRepository participantRepository;

    @Mock
    private EpochService epochService;

    @InjectMocks
    private ObligationService obligationService;

    @Test
    void shouldReturnExistingObligationWhenTxIdMatchesAndHashMatches() {
        ObligationRequest request = new ObligationRequest("tx-1", "A", "B", "KRW", 1_000L);
        ObligationEntity existing = obligation(10L, "tx-1", "4514eb7746f944254d89a9f1b73f840d3f7e901ad0387405fb1b727244ec60c4", ObligationStatus.ACCEPTED);
        when(obligationRepository.findByTxId("tx-1")).thenReturn(Optional.of(existing));

        ObligationResponse response = obligationService.receive(request);

        assertEquals(10L, response.obligationId());
        assertEquals("ACCEPTED", response.status());
        verify(obligationRepository, never()).save(any(ObligationEntity.class));
        verify(participantRepository, never()).findByParticipantCode(any());
    }

    @Test
    void shouldThrowConflictWhenTxIdExistsButHashMismatches() {
        ObligationRequest request = new ObligationRequest("tx-1", "A", "B", "KRW", 1_000L);
        ObligationEntity existing = obligation(10L, "tx-1", "different-hash", ObligationStatus.ACCEPTED);
        when(obligationRepository.findByTxId("tx-1")).thenReturn(Optional.of(existing));

        ApiException exception = assertThrows(ApiException.class, () -> obligationService.receive(request));

        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        assertEquals("IDEMPOTENCY_CONFLICT", exception.getCode());
    }

    @Test
    void shouldStoreAcceptedObligationWhenProjectedNetDebitIsWithinCap() {
        ObligationRequest request = new ObligationRequest("tx-accepted", "A", "B", "KRW", 100_000L);
        ParticipantEntity payer = participant(1L, "A", 200_000L);
        ParticipantEntity payee = participant(2L, "B", 200_000L);
        EpochEntity epoch = epoch(50L, 100L);

        when(obligationRepository.findByTxId("tx-accepted")).thenReturn(Optional.empty());
        when(participantRepository.findByParticipantCode("A")).thenReturn(Optional.of(payer));
        when(participantRepository.findByParticipantCode("B")).thenReturn(Optional.of(payee));
        when(epochService.getOrCreateOpenEpoch()).thenReturn(epoch);
        when(obligationRepository.sumNetDeltaByEpochAndParticipantAndStatus(50L, 1L, ObligationStatus.ACCEPTED)).thenReturn(0L);
        when(obligationRepository.save(any(ObligationEntity.class))).thenAnswer(invocation -> {
            ObligationEntity saved = invocation.getArgument(0);
            saved.setId(11L);
            saved.setCreatedAt(Instant.parse("2026-02-20T00:00:00Z"));
            return saved;
        });

        ObligationResponse response = obligationService.receive(request);

        assertEquals("ACCEPTED", response.status());
        assertEquals(100L, response.epochNo());
    }

    @Test
    void shouldStoreHeldObligationWhenProjectedNetDebitExceedsCap() {
        ObligationRequest request = new ObligationRequest("tx-held", "A", "B", "KRW", 100_000L);
        ParticipantEntity payer = participant(1L, "A", 200_000L);
        ParticipantEntity payee = participant(2L, "B", 200_000L);
        EpochEntity epoch = epoch(50L, 101L);

        when(obligationRepository.findByTxId("tx-held")).thenReturn(Optional.empty());
        when(participantRepository.findByParticipantCode("A")).thenReturn(Optional.of(payer));
        when(participantRepository.findByParticipantCode("B")).thenReturn(Optional.of(payee));
        when(epochService.getOrCreateOpenEpoch()).thenReturn(epoch);
        when(obligationRepository.sumNetDeltaByEpochAndParticipantAndStatus(50L, 1L, ObligationStatus.ACCEPTED)).thenReturn(-150_000L);
        when(obligationRepository.save(any(ObligationEntity.class))).thenAnswer(invocation -> {
            ObligationEntity saved = invocation.getArgument(0);
            saved.setId(12L);
            saved.setCreatedAt(Instant.parse("2026-02-20T00:00:00Z"));
            return saved;
        });

        ObligationResponse response = obligationService.receive(request);

        assertEquals("HELD", response.status());
        assertEquals(100_000L, response.amount());
    }

    private ObligationEntity obligation(Long id, String txId, String requestHash, ObligationStatus status) {
        ObligationEntity entity = new ObligationEntity();
        entity.setId(id);
        entity.setTxId(txId);
        entity.setRequestHash(requestHash);
        entity.setDebtorParticipant(participant(1L, "A", 200_000L));
        entity.setCreditorParticipant(participant(2L, "B", 200_000L));
        entity.setAmountKrw(1_000L);
        entity.setStatus(status);
        entity.setEpoch(epoch(10L, 9L));
        entity.setCreatedAt(Instant.parse("2026-02-20T00:00:00Z"));
        return entity;
    }

    private ParticipantEntity participant(Long id, String code, long capKrw) {
        ParticipantEntity entity = new ParticipantEntity();
        entity.setId(id);
        entity.setParticipantCode(code);
        entity.setLedgerAddress("0x1234567890123456789012345678901234567890");
        entity.setNetDebitCapKrw(capKrw);
        return entity;
    }

    private EpochEntity epoch(Long id, long epochNo) {
        EpochEntity entity = new EpochEntity();
        entity.setId(id);
        entity.setEpochNo(epochNo);
        entity.setStatus(EpochStatus.OPEN);
        entity.setOpenedAt(Instant.parse("2026-02-20T00:00:00Z"));
        return entity;
    }
}
