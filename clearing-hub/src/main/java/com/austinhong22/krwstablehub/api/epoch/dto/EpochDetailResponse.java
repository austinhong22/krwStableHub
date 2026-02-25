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
        String settlementStatus,
        String txHash
) {
    public record NetPositionItem(
            String participantCode,
            long netAmountKrw
    ) {
    }
}
