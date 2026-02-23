package com.austinhong22.krwstablehub.service;

import com.austinhong22.krwstablehub.persistence.model.EpochEntity;
import com.austinhong22.krwstablehub.persistence.model.NetPositionEntity;
import com.austinhong22.krwstablehub.persistence.model.ObligationEntity;
import com.austinhong22.krwstablehub.persistence.model.ObligationStatus;
import com.austinhong22.krwstablehub.persistence.model.ParticipantEntity;
import com.austinhong22.krwstablehub.persistence.repository.NetPositionRepository;
import com.austinhong22.krwstablehub.persistence.repository.ObligationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NettingServiceTest {

    @Mock
    private ObligationRepository obligationRepository;

    @Mock
    private NetPositionRepository netPositionRepository;

    @InjectMocks
    private NettingService nettingService;

    @Test
    void shouldComputeAndPersistNetPositionsFromAcceptedObligations() {
        EpochEntity epoch = epoch(10L, 100L);
        ParticipantEntity a = participant(1L, "A");
        ParticipantEntity b = participant(2L, "B");
        ParticipantEntity c = participant(3L, "C");

        ObligationEntity aToB = obligation(epoch, a, b, 100L);
        ObligationEntity cToA = obligation(epoch, c, a, 70L);
        ObligationEntity cToB = obligation(epoch, c, b, 30L);

        when(obligationRepository.findByEpochIdAndStatus(10L, ObligationStatus.ACCEPTED))
                .thenReturn(List.of(aToB, cToA, cToB));
        when(netPositionRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        List<NetPositionEntity> saved = nettingService.netEpoch(epoch);

        verify(netPositionRepository).deleteByEpochId(10L);
        assertEquals(3, saved.size());

        ArgumentCaptor<List<NetPositionEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(netPositionRepository).saveAll(captor.capture());
        List<NetPositionEntity> positions = captor.getValue();
        assertEquals(-30L, amountForCode(positions, "A"));
        assertEquals(130L, amountForCode(positions, "B"));
        assertEquals(-100L, amountForCode(positions, "C"));
        assertEquals(0L, positions.stream().mapToLong(NetPositionEntity::getNetAmountKrw).sum());
    }

    private long amountForCode(List<NetPositionEntity> positions, String participantCode) {
        return positions.stream()
                .filter(position -> participantCode.equals(position.getParticipant().getParticipantCode()))
                .findFirst()
                .orElseThrow()
                .getNetAmountKrw();
    }

    private ObligationEntity obligation(EpochEntity epoch, ParticipantEntity debtor, ParticipantEntity creditor, long amountKrw) {
        ObligationEntity entity = new ObligationEntity();
        entity.setEpoch(epoch);
        entity.setDebtorParticipant(debtor);
        entity.setCreditorParticipant(creditor);
        entity.setAmountKrw(amountKrw);
        entity.setStatus(ObligationStatus.ACCEPTED);
        return entity;
    }

    private ParticipantEntity participant(Long id, String code) {
        ParticipantEntity entity = new ParticipantEntity();
        entity.setId(id);
        entity.setParticipantCode(code);
        entity.setLedgerAddress("0x1234567890123456789012345678901234567890");
        entity.setNetDebitCapKrw(200_000L);
        return entity;
    }

    private EpochEntity epoch(Long id, long epochNo) {
        EpochEntity entity = new EpochEntity();
        entity.setId(id);
        entity.setEpochNo(epochNo);
        return entity;
    }
}
