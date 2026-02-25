package com.austinhong22.krwstablehub.service;

import com.austinhong22.krwstablehub.api.epoch.dto.EpochDetailResponse;
import com.austinhong22.krwstablehub.api.error.ApiException;
import com.austinhong22.krwstablehub.persistence.model.EpochEntity;
import com.austinhong22.krwstablehub.persistence.model.NetPositionEntity;
import com.austinhong22.krwstablehub.persistence.model.SettlementInstructionEntity;
import com.austinhong22.krwstablehub.persistence.repository.EpochRepository;
import com.austinhong22.krwstablehub.persistence.repository.NetPositionRepository;
import com.austinhong22.krwstablehub.persistence.repository.SettlementInstructionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EpochQueryService {

    private final EpochRepository epochRepository;
    private final NetPositionRepository netPositionRepository;
    private final SettlementInstructionRepository settlementInstructionRepository;

    @Transactional(readOnly = true)
    public EpochDetailResponse getEpoch(Long epochId) {
        EpochEntity epoch = epochRepository.findById(epochId).orElseThrow(
                () -> new ApiException(HttpStatus.NOT_FOUND, "EPOCH_NOT_FOUND", "Epoch not found: " + epochId)
        );
        List<NetPositionEntity> positions = netPositionRepository.findByEpochIdOrderByIdAsc(epochId);
        SettlementInstructionEntity settlementInstruction = settlementInstructionRepository.findByEpochId(epochId).orElse(null);

        return new EpochDetailResponse(
                epoch.getId(),
                epoch.getEpochNo(),
                epoch.getStatus().name(),
                epoch.getOpenedAt(),
                epoch.getClosedAt(),
                mapPositions(positions),
                settlementInstruction == null ? null : settlementInstruction.getStatus().name(),
                settlementInstruction == null ? null : settlementInstruction.getTxHash()
        );
    }

    private List<EpochDetailResponse.NetPositionItem> mapPositions(List<NetPositionEntity> positions) {
        return positions.stream()
                .map(position -> new EpochDetailResponse.NetPositionItem(
                        position.getParticipant().getParticipantCode(),
                        position.getNetAmountKrw()
                ))
                .toList();
    }

}
