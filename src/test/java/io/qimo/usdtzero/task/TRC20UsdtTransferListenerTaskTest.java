package io.qimo.usdtzero.task;

import io.qimo.usdtzero.UsdtZeroApplication;
import io.qimo.usdtzero.service.AmountPoolService;
import io.qimo.usdtzero.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tron.trident.proto.Response.BlockExtention;
import org.tron.trident.core.ApiWrapper;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = UsdtZeroApplication.class)
class TRC20UsdtTransferListenerTaskTest {
    private static final Logger log = LoggerFactory.getLogger(TRC20UsdtTransferListenerTaskTest.class);

    @Autowired
    private TRC20UsdtTransferListenerTask listenerTask;

    @Autowired
    private AmountPoolService amountPoolService;

    @Test
    void testParseBlock() {
        // 真实区块号和地址
        long blockNum = 57501162; // 请替换为实际存在的TRC20 USDT转账区块号
        String toAddress = "TLk8T2JePWspuAggW4yx1ZEUdGoYLJb3iH"; // 请替换为实际收款地址
        long expectedAmount = 1000000L; // 1 USDT = 1000000

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