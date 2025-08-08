package io.qimo.usdtzero.task;

import io.qimo.usdtzero.service.AmountPoolService;
import io.qimo.usdtzero.service.LightweightMetricsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.Map;

/**
 * 资金池过期条目兜底清理任务，防止内存泄漏
 */
@Slf4j
@Component
public class AmountPoolCleanupTask {

    @Autowired
    private AmountPoolService amountPoolService;

    @Autowired
    private LightweightMetricsService metricsService;

    /**
     * 每分钟清理一次过期的资金池条目
     */
    @Scheduled(fixedRate = 60000)
    public void cleanupExpiredAmountPool() {
        io.micrometer.core.instrument.Timer.Sample timer = metricsService.startScheduledTaskTimer();
        int removed = 0;
        LocalDateTime now = LocalDateTime.now();
        try {
            Map<String, AmountPoolService.AmountPoolEntry> pool = amountPoolService.getAmountToOrderMap();
            for (Map.Entry<String, AmountPoolService.AmountPoolEntry> entry : pool.entrySet()) {
                AmountPoolService.AmountPoolEntry value = entry.getValue();
                if (value != null && value.getExpireTime() != null && value.getExpireTime().isBefore(now)) {
                    amountPoolService.releaseAmountByKey(entry.getKey());
                    removed++;
                }
            }
            if (removed > 0) {
                log.info("AmountPoolCleanupTask: 清理过期资金池条目 {} 个", removed);
            }
        } catch (Exception e) {
            metricsService.recordScheduledTaskError("amount_pool_cleanup", e.getMessage());
            log.error("AmountPoolCleanupTask: 清理资金池异常, 已清理 {} 个, 错误: {}", removed, e.getMessage(), e);
        } finally {
            metricsService.stopScheduledTaskTimer(timer, "amount_pool_cleanup");
        }
    }
} 