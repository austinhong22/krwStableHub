package com.example.clearinghub.infra.ledger;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ledger")
public record LedgerProperties(
        String rpcUrl,
        long chainId,
        String contractAddress,
        String operatorPrivateKey,
        long gasPrice,
        long gasLimit
) {
}
