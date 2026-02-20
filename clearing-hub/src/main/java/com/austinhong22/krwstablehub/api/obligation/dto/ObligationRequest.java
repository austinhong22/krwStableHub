package com.austinhong22.krwstablehub.api.obligation.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.Locale;

public record ObligationRequest(
        @NotBlank
        @Size(max = 128)
        String txId,
        @NotBlank
        @Size(max = 32)
        String payer,
        @NotBlank
        @Size(max = 32)
        String payee,
        @NotBlank
        @Pattern(regexp = "(?i)KRW", message = "payAsset must be KRW")
        String payAsset,
        @NotNull
        @Positive
        Long amount
) {

    @AssertTrue(message = "payer and payee must be different")
    public boolean hasDistinctParticipants() {
        if (payer == null || payee == null) {
            return true;
        }
        return !normalizedPayer().equals(normalizedPayee());
    }

    public String normalizedTxId() {
        return txId.trim();
    }

    public String normalizedPayer() {
        return payer.trim().toUpperCase(Locale.ROOT);
    }

    public String normalizedPayee() {
        return payee.trim().toUpperCase(Locale.ROOT);
    }

    public String normalizedPayAsset() {
        return payAsset.trim().toUpperCase(Locale.ROOT);
    }
}
