package com.austinhong22.krwstablehub.service;

import com.austinhong22.krwstablehub.persistence.model.EpochEntity;
import com.austinhong22.krwstablehub.persistence.model.EpochStatus;
import com.austinhong22.krwstablehub.persistence.model.SettlementInstructionEntity;
import com.austinhong22.krwstablehub.persistence.model.SettlementStatus;
import com.austinhong22.krwstablehub.persistence.repository.EpochRepository;
import com.austinhong22.krwstablehub.persistence.repository.SettlementInstructionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EpochCloseServiceTest {

    @Mock
    private EpochRepository epochRepository;

    @Mock
    private SettlementInstructionRepository settlementInstructionRepository;

    @Mock
    private NettingService nettingService;

    @InjectMocks
    private EpochCloseService epochCloseService;

    @Test
    void shouldCloseOpenEpochAndCreateSettlementInstruction() {
        EpochEntity epoch = epoch(10L, EpochStatus.OPEN);
        when(epochRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(epoch));
        when(settlementInstructionRepository.findByEpochId(10L)).thenReturn(Optional.empty());

        EpochEntity closed = epochCloseService.closeEpoch(10L);

        assertSame(epoch, closed);
        assertEquals(EpochStatus.NETTED, closed.getStatus());
        assertNotNull(closed.getClosedAt());
        verify(nettingService).netEpoch(epoch);

        ArgumentCaptor<SettlementInstructionEntity> instructionCaptor = ArgumentCaptor.forClass(SettlementInstructionEntity.class);
        verify(settlementInstructionRepository).save(instructionCaptor.capture());
        SettlementInstructionEntity instruction = instructionCaptor.getValue();
        assertSame(epoch, instruction.getEpoch());
        assertEquals(SettlementStatus.CREATED, instruction.getStatus());
        assertEquals(0, instruction.getAttemptCount());
    }

    @Test
    void shouldKeepExistingSettlementInstructionForNettedEpoch() {
        EpochEntity epoch = epoch(20L, EpochStatus.NETTED);
        SettlementInstructionEntity existing = new SettlementInstructionEntity();
        existing.setEpoch(epoch);
        existing.setStatus(SettlementStatus.CREATED);

        when(epochRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(epoch));
        when(settlementInstructionRepository.findByEpochId(20L)).thenReturn(Optional.of(existing));

        epochCloseService.closeEpoch(20L);

        verify(nettingService, never()).netEpoch(any());
        verify(settlementInstructionRepository, never()).save(any());
    }

    private EpochEntity epoch(Long id, EpochStatus status) {
        EpochEntity epoch = new EpochEntity();
        epoch.setId(id);
        epoch.setEpochNo(id);
        epoch.setStatus(status);
        return epoch;
    }
}
