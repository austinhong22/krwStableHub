package com.austinhong22.krwstablehub.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SettlementSchedulerTest {

    @Mock
    private SettlementProcessor settlementProcessor;

    @Test
    void shouldProcessPendingInstructionIds() {
        SettlementScheduler scheduler = new SettlementScheduler(settlementProcessor);
        when(settlementProcessor.findPendingInstructionIds(any(Instant.class))).thenReturn(List.of(10L, 11L));

        scheduler.processPendingSettlements();

        verify(settlementProcessor).processInstruction(10L);
        verify(settlementProcessor).processInstruction(11L);
    }
}
