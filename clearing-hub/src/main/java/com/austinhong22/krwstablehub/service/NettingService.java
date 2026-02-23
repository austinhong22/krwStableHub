package com.austinhong22.krwstablehub.service;

import com.austinhong22.krwstablehub.persistence.model.EpochEntity;
import com.austinhong22.krwstablehub.persistence.model.NetPositionEntity;
import com.austinhong22.krwstablehub.persistence.model.ObligationEntity;
import com.austinhong22.krwstablehub.persistence.model.ObligationStatus;
import com.austinhong22.krwstablehub.persistence.model.ParticipantEntity;
import com.austinhong22.krwstablehub.persistence.repository.NetPositionRepository;
import com.austinhong22.krwstablehub.persistence.repository.ObligationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class NettingService {

    private final ObligationRepository obligationRepository;
    private final NetPositionRepository netPositionRepository;

    @Transactional
    public List<NetPositionEntity> netEpoch(EpochEntity epoch) {
        List<ObligationEntity> acceptedObligations = obligationRepository.findByEpochIdAndStatus(
                epoch.getId(),
                ObligationStatus.ACCEPTED
        );

        Map<Long, NetDelta> deltasByParticipantId = new LinkedHashMap<>();
        for (ObligationEntity obligation : acceptedObligations) {
            accumulate(deltasByParticipantId, obligation.getCreditorParticipant(), obligation.getAmountKrw());
            accumulate(deltasByParticipantId, obligation.getDebtorParticipant(), -obligation.getAmountKrw());
        }

        long sum = 0L;
        for (NetDelta delta : deltasByParticipantId.values()) {
            sum = Math.addExact(sum, delta.netAmountKrw());
        }
        if (sum != 0L) {
            throw new IllegalStateException("Netting invariant violated: sum(deltas) must be zero");
        }

        netPositionRepository.deleteByEpochId(epoch.getId());

        List<NetPositionEntity> positions = new ArrayList<>();
        for (NetDelta delta : deltasByParticipantId.values()) {
            if (delta.netAmountKrw() == 0L) {
                continue;
            }
            NetPositionEntity position = new NetPositionEntity();
            position.setEpoch(epoch);
            position.setParticipant(delta.participant());
            position.setNetAmountKrw(delta.netAmountKrw());
            positions.add(position);
        }

        return netPositionRepository.saveAll(positions);
    }

    private void accumulate(Map<Long, NetDelta> deltasByParticipantId, ParticipantEntity participant, long amountKrw) {
        deltasByParticipantId.compute(participant.getId(), (key, existing) -> {
            if (existing == null) {
                return new NetDelta(participant, amountKrw);
            }
            return new NetDelta(participant, Math.addExact(existing.netAmountKrw(), amountKrw));
        });
    }

    private record NetDelta(ParticipantEntity participant, long netAmountKrw) {
    }
}
