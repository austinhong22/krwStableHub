package com.austinhong22.krwstablehub.api.obligation.dto;

import java.time.Instant;

public record ObligationResponse(
        Long obligationId,
        String txId,
        String payer,
        String payee,
        String payAsset,
        long amount,
        String status,
        Long epochNo,
        Instant createdAt
) {
}
