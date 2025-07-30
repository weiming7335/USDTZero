package io.qimo.usdtzero.task;

import io.qimo.usdtzero.service.AmountPoolService;
import io.qimo.usdtzero.service.LightweightMetricsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AmountPoolCleanupTaskTest {
    @Mock
    private AmountPoolService amountPoolService;
    @Mock
    private LightweightMetricsService metricsService;
    @InjectMocks
    private AmountPoolCleanupTask cleanupTask;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testCleanupExpiredAmountPool_removesExpiredEntriesAndRecordsMetrics() {
        // 构造过期和未过期条目
        String expiredKey = "addr_1_100";
        String validKey = "addr_2_200";
        LocalDateTime now = LocalDateTime.now();
        AmountPoolService.AmountPoolEntry expired = new AmountPoolService.AmountPoolEntry("order1", now.minusMinutes(1));
        AmountPoolService.AmountPoolEntry valid = new AmountPoolService.AmountPoolEntry("order2", now.plusMinutes(10));
        Map<String, AmountPoolService.AmountPoolEntry> pool = Map.of(
                expiredKey, expired,
                validKey, valid
        );
        when(amountPoolService.getAmountToOrderMap()).thenReturn(pool);

        // 执行
        cleanupTask.cleanupExpiredAmountPool();

        // 验证只清理过期条目
        verify(amountPoolService, times(1)).releaseAmountByKey(expiredKey);
        verify(amountPoolService, never()).releaseAmountByKey(validKey);
       }

    @Test
    void testCleanupExpiredAmountPool_handlesExceptionAndRecordsError() {
        when(amountPoolService.getAmountToOrderMap()).thenThrow(new RuntimeException("mock error"));
        cleanupTask.cleanupExpiredAmountPool();
        verify(metricsService, atLeastOnce()).recordScheduledTaskError(eq("amount_pool_cleanup"), anyString());
    }
} 