package io.qimo.usdtzero.task;

import io.qimo.usdtzero.UsdtZeroApplication;
import io.qimo.usdtzero.service.AmountPoolService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = UsdtZeroApplication.class)
@ConditionalOnProperty(value = "chain.bep20-enable", havingValue = "true")
class BEP20UsdtTransferListenerTaskTest {
    private static final Logger log = LoggerFactory.getLogger(BEP20UsdtTransferListenerTaskTest.class);

    @Autowired
    private BEP20UsdtTransferListenerTask listenerTask;

    @Autowired
    private AmountPoolService amountPoolService;

    @Test
    void testParseBlock() {
        // 真实区块号和地址（请替换为实际存在的BEP20 USDT转账区块号和收款地址）
        long blockNum = 59868958L; // 示例区块号
        String toAddress = "0x72d2989b76d3038f1d15e5fa811fa958aebfebaf"; // 示例收款地址
        long expectedAmount = 160000L;

        try {
            // 资金池分配金额
            amountPoolService.allocateAmount(toAddress, expectedAmount);
            // 调用监听任务并等待完成
            CompletableFuture<Void> future = listenerTask.parseBlock(blockNum);
            future.join();
        } catch (Exception e) {
            log.error("测试执行异常", e);
            fail("测试执行失败: " + e.getMessage());
        } finally {
            // 清理资源
            amountPoolService.releaseAmount(toAddress, expectedAmount);
        }
    }
} 