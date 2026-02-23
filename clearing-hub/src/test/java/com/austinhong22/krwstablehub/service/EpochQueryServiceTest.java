package com.austinhong22.krwstablehub.service;

import com.austinhong22.krwstablehub.api.epoch.dto.EpochDetailResponse;
import com.austinhong22.krwstablehub.persistence.model.EpochEntity;
import com.austinhong22.krwstablehub.persistence.model.EpochStatus;
import com.austinhong22.krwstablehub.persistence.model.NetPositionEntity;
import com.austinhong22.krwstablehub.persistence.model.ParticipantEntity;
import com.austinhong22.krwstablehub.persistence.model.SettlementInstructionEntity;
import com.austinhong22.krwstablehub.persistence.model.SettlementStatus;
import com.austinhong22.krwstablehub.persistence.repository.EpochRepository;
import com.austinhong22.krwstablehub.persistence.repository.NetPositionRepository;
import com.austinhong22.krwstablehub.persistence.repository.SettlementInstructionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EpochQueryServiceTest {

    @Mock
    private EpochRepository epochRepository;

    @Mock
    private NetPositionRepository netPositionRepository;

    @Mock
    private SettlementInstructionRepository settlementInstructionRepository;

    @InjectMocks
    private EpochQueryService epochQueryService;

    @Test
    void shouldReturnEpochWithNetPositionsAndSettlementInstruction() {
        EpochEntity epoch = new EpochEntity();
        epoch.setId(10L);
        epoch.setEpochNo(100L);
        epoch.setStatus(EpochStatus.NETTED);
        epoch.setOpenedAt(Instant.parse("2026-02-23T10:00:00Z"));
        epoch.setClosedAt(Instant.parse("2026-02-23T10:01:00Z"));

        NetPositionEntity a = netPosition(epoch, participant(1L, "A"), -70L);
        NetPositionEntity b = netPosition(epoch, participant(2L, "B"), 70L);

        SettlementInstructionEntity instruction = new SettlementInstructionEntity();
        instruction.setEpoch(epoch);
        instruction.setStatus(SettlementStatus.CREATED);
        instruction.setTxHash(null);

        when(epochRepository.findById(10L)).thenReturn(Optional.of(epoch));
        when(netPositionRepository.findByEpochIdOrderByIdAsc(10L)).thenReturn(List.of(a, b));
        when(settlementInstructionRepository.findByEpochId(10L)).thenReturn(Optional.of(instruction));

        EpochDetailResponse response = epochQueryService.getEpoch(10L);

        assertEquals(10L, response.epochId());
        assertEquals("NETTED", response.status());
        assertEquals(2, response.netPositions().size());
        assertEquals("A", response.netPositions().get(0).participantCode());
        assertEquals(-70L, response.netPositions().get(0).netAmountKrw());
        assertNotNull(response.settlementInstruction());
        assertEquals("CREATED", response.settlementInstruction().status());
    }

    private NetPositionEntity netPosition(EpochEntity epoch, ParticipantEntity participant, long amountKrw) {
        NetPositionEntity position = new NetPositionEntity();
        position.setEpoch(epoch);
        position.setParticipant(participant);
        position.setNetAmountKrw(amountKrw);
        return position;
    }

    private ParticipantEntity participant(Long id, String code) {
        ParticipantEntity participant = new ParticipantEntity();
        participant.setId(id);
        participant.setParticipantCode(code);
        participant.setLedgerAddress("0x1234567890123456789012345678901234567890");
        participant.setNetDebitCapKrw(200_000L);
        return participant;
    }
}
