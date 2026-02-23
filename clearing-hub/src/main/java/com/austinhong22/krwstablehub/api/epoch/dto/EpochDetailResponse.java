package com.austinhong22.krwstablehub.api.epoch.dto;

import java.time.Instant;
import java.util.List;

public record EpochDetailResponse(
        Long epochId,
        long epochNo,
        String status,
        Instant openedAt,
        Instant closedAt,
        List<NetPositionItem> netPositions,
        SettlementInstructionItem settlementInstruction
) {
    public record NetPositionItem(
            String participantCode,
            long netAmountKrw
    ) {
    }

    public record SettlementInstructionItem(
            String status,
            String txHash
    ) {
    }
}
