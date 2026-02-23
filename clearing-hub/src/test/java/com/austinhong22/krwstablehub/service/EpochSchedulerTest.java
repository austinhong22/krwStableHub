package com.austinhong22.krwstablehub.service;

import com.austinhong22.krwstablehub.persistence.model.EpochEntity;
import com.austinhong22.krwstablehub.persistence.model.EpochStatus;
import com.example.clearinghub.config.ClearingProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EpochSchedulerTest {

    @Mock
    private EpochService epochService;

    @Mock
    private EpochCloseService epochCloseService;

    @Test
    void shouldCloseDueEpochAndCreateNextOpenEpoch() {
        EpochScheduler epochScheduler = new EpochScheduler(epochService, epochCloseService, clearingProperties());
        EpochEntity due = epoch(10L, Instant.now().minusSeconds(61));
        EpochEntity active = epoch(11L, Instant.now().minusSeconds(10));
        when(epochService.getOrCreateOpenEpoch()).thenReturn(due, active);

        epochScheduler.closeDueEpochs();

        verify(epochCloseService).closeEpoch(10L);
        verify(epochService).createNextOpenEpoch(due);
    }

    @Test
    void shouldSkipWhenOpenEpochWindowHasNotEnded() {
        EpochScheduler epochScheduler = new EpochScheduler(epochService, epochCloseService, clearingProperties());
        EpochEntity active = epoch(22L, Instant.now().minusSeconds(5));
        when(epochService.getOrCreateOpenEpoch()).thenReturn(active);

        epochScheduler.closeDueEpochs();

        verify(epochCloseService, never()).closeEpoch(active.getId());
        verify(epochService, never()).createNextOpenEpoch(active);
    }

    private EpochEntity epoch(Long id, Instant openedAt) {
        EpochEntity epoch = new EpochEntity();
        epoch.setId(id);
        epoch.setEpochNo(id);
        epoch.setStatus(EpochStatus.OPEN);
        epoch.setOpenedAt(openedAt);
        return epoch;
    }

    private ClearingProperties clearingProperties() {
        return new ClearingProperties(
                new ClearingProperties.Epoch(60L),
                new ClearingProperties.Settlement(5, 100)
        );
    }
}
