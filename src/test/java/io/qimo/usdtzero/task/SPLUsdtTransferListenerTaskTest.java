package io.qimo.usdtzero.task;

import io.qimo.usdtzero.UsdtZeroApplication;
import io.qimo.usdtzero.service.AmountPoolService;
import io.qimo.usdtzero.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static org.junit.jupiter.api.Assertions.*;


@SpringBootTest(classes = UsdtZeroApplication.class)
@ConditionalOnProperty(value = "chain.spl-enable", havingValue = "true")
class SPLUsdtTransferListenerTaskTest {
    private static final Logger log = LoggerFactory.getLogger(SPLUsdtTransferListenerTaskTest.class);

    @Autowired
    private SolanaUsdtTransferListenerTask listenerTask;

    @Autowired
    private AmountPoolService amountPoolService;

    @Test
    void testRealBlockTransfer() {
        // 使用真实的区块号和地址
        long blockNumber = 397606836;
        String toAddress = "45fpPKXsMY9P7QTmJUvTZL9EUt1Dnugn6puN2oNXv85o";
        long expectedAmount = 1200000L; // 1 USDC = 1000000 micro units

        try {
            // 分配金额
            amountPoolService.allocateAmount(toAddress, expectedAmount);
            
            // 调用监听任务并等待完成
            listenerTask.parseBlockForUsdtTransfers(blockNumber).join();
        } catch (Exception e) {
            log.error("测试执行异常", e);
            fail("测试执行失败: " + e.getMessage());
        } finally {
            // 清理资源
            amountPoolService.releaseAmount(toAddress, expectedAmount);
        }
    }
} 