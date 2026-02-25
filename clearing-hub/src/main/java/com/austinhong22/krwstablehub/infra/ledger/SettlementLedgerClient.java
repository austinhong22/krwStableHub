package com.austinhong22.krwstablehub.infra.ledger;

import com.example.clearinghub.infra.ledger.LedgerProperties;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.web3j.abi.EventEncoder;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.DynamicArray;
import org.web3j.abi.datatypes.Event;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.generated.Int256;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.EthGasPrice;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.RawTransactionManager;
import org.web3j.tx.response.PollingTransactionReceiptProcessor;
import org.web3j.tx.response.TransactionReceiptProcessor;

import java.io.IOException;
import java.math.BigInteger;
import java.util.List;

@Component
public class SettlementLedgerClient {

    private static final String FUNCTION_SETTLE = "settle";
    private static final Event SETTLED_EVENT = new Event(
            "Settled",
            List.of(new TypeReference<Uint256>(true) {
            })
    );
    private static final String SETTLED_EVENT_TOPIC = EventEncoder.encode(SETTLED_EVENT);
    private static final BigInteger FALLBACK_GAS_PRICE = BigInteger.valueOf(1_000_000_000L);
    private static final int RECEIPT_POLL_INTERVAL_MILLIS = 1000;
    private static final int RECEIPT_ATTEMPTS = 60;

    private final LedgerProperties ledgerProperties;
    private final Web3j web3j;

    public SettlementLedgerClient(LedgerProperties ledgerProperties) {
        this.ledgerProperties = ledgerProperties;
        this.web3j = Web3j.build(new HttpService(ledgerProperties.rpcUrl()));
    }

    public String settle(long epochId, List<String> participantAddresses, List<Long> deltasKrw) {
        if (participantAddresses.size() != deltasKrw.size()) {
            throw new IllegalArgumentException("participants and deltas length mismatch");
        }

        String contractAddress = requireConfigured(ledgerProperties.contractAddress(), "ledger.contract-address");
        String operatorPrivateKey = requireConfigured(ledgerProperties.operatorPrivateKey(), "ledger.operator-private-key");
        Credentials credentials = Credentials.create(operatorPrivateKey);

        Function function = new Function(
                FUNCTION_SETTLE,
                List.of(
                        new Uint256(BigInteger.valueOf(epochId)),
                        new DynamicArray<>(
                                Address.class,
                                participantAddresses.stream().map(Address::new).toList()
                        ),
                        new DynamicArray<>(
                                Int256.class,
                                deltasKrw.stream().map(value -> new Int256(BigInteger.valueOf(value))).toList()
                        )
                ),
                List.of()
        );

        String encodedFunction = FunctionEncoder.encode(function);
        BigInteger gasPrice = resolveGasPrice();
        BigInteger gasLimit = BigInteger.valueOf(ledgerProperties.gasLimit());

        RawTransactionManager transactionManager =
                new RawTransactionManager(web3j, credentials, ledgerProperties.chainId());
        EthSendTransaction sent = sendTransaction(transactionManager, gasPrice, gasLimit, contractAddress, encodedFunction);
        if (sent.hasError()) {
            throw new IllegalStateException("ledger transaction rejected: " + sent.getError().getMessage());
        }

        String txHash = sent.getTransactionHash();
        TransactionReceipt receipt = waitForReceipt(txHash);
        if (!receipt.isStatusOK()) {
            throw new IllegalStateException("ledger transaction failed: " + txHash);
        }
        if (!containsSettledEvent(receipt, contractAddress)) {
            throw new IllegalStateException("ledger transaction missing Settled event: " + txHash);
        }

        return txHash;
    }

    private EthSendTransaction sendTransaction(
            RawTransactionManager transactionManager,
            BigInteger gasPrice,
            BigInteger gasLimit,
            String contractAddress,
            String encodedFunction
    ) {
        try {
            return transactionManager.sendTransaction(gasPrice, gasLimit, contractAddress, encodedFunction, BigInteger.ZERO);
        } catch (IOException exception) {
            throw new IllegalStateException("failed to send ledger transaction", exception);
        }
    }

    private TransactionReceipt waitForReceipt(String txHash) {
        TransactionReceiptProcessor processor = new PollingTransactionReceiptProcessor(
                web3j,
                RECEIPT_POLL_INTERVAL_MILLIS,
                RECEIPT_ATTEMPTS
        );
        try {
            return processor.waitForTransactionReceipt(txHash);
        } catch (IOException exception) {
            throw new IllegalStateException("failed to fetch ledger receipt", exception);
        } catch (org.web3j.protocol.exceptions.TransactionException exception) {
            throw new IllegalStateException("timed out waiting for ledger receipt", exception);
        }
    }

    private BigInteger resolveGasPrice() {
        if (ledgerProperties.gasPrice() > 0) {
            return BigInteger.valueOf(ledgerProperties.gasPrice());
        }
        try {
            EthGasPrice response = web3j.ethGasPrice().send();
            BigInteger gasPrice = response.getGasPrice();
            if (gasPrice != null && gasPrice.signum() > 0) {
                return gasPrice;
            }
            return FALLBACK_GAS_PRICE;
        } catch (IOException exception) {
            throw new IllegalStateException("failed to resolve gas price", exception);
        }
    }

    private boolean containsSettledEvent(TransactionReceipt receipt, String contractAddress) {
        for (Log log : receipt.getLogs()) {
            if (!contractAddress.equalsIgnoreCase(log.getAddress())) {
                continue;
            }
            if (log.getTopics() == null || log.getTopics().isEmpty()) {
                continue;
            }
            if (SETTLED_EVENT_TOPIC.equalsIgnoreCase(log.getTopics().get(0))) {
                return true;
            }
        }
        return false;
    }

    private String requireConfigured(String value, String propertyName) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException("missing required property: " + propertyName);
        }
        return value;
    }

    @PreDestroy
    void shutdownClient() {
        web3j.shutdown();
    }
}
