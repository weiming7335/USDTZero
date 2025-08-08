package io.qimo.usdtzero.event;

import io.qimo.usdtzero.notify.TelegramBotNotifier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@SpringBootTest
class OrderPaidEventListenerIntegrationTest {
    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired(required = false)
    private TelegramBotNotifier telegramBotNotifier;

    @Test
    void testOrderPaidEventListener_sendNotification() {
        if (telegramBotNotifier == null) return;
        // 构造 OrderMessage
        OrderMessage msg = new OrderMessage();
        msg.setTradeNo("T123456789");
        msg.setToAddress("1A2B3C4D5E");
        msg.setAmount(new BigDecimal("12345.00"));
        msg.setActualAmount(new BigDecimal("2000000.00"));
        msg.setChainType("TRC20");
        msg.setTxHash("0xabc123");
        msg.setCreateTime(LocalDateTime.now().minusMinutes(5));
        msg.setPayTime(LocalDateTime.now());

        // 发布事件，真实监听器和通知流程
        eventPublisher.publishEvent(new OrderPaidEvent(this, msg));
        // 可通过日志或实际 Telegram 消息验证效果
    }
} 