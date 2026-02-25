package com.austinhong22.krwstablehub.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class SettlementScheduler {

    private final SettlementProcessor settlementProcessor;

    @Scheduled(fixedDelay = 1000L)
    public void processPendingSettlements() {
        List<Long> instructionIds = settlementProcessor.findPendingInstructionIds(Instant.now());
        for (Long instructionId : instructionIds) {
            try {
                settlementProcessor.processInstruction(instructionId);
            } catch (Exception exception) {
                log.error("Unexpected error processing settlement instructionId={}", instructionId, exception);
            }
        }
    }
}
