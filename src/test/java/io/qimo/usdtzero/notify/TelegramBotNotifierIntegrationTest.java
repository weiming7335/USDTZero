package io.qimo.usdtzero.notify;

import io.qimo.usdtzero.UsdtZeroApplication;
import io.qimo.usdtzero.config.BotProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = UsdtZeroApplication.class)
class TelegramBotNotifierIntegrationTest {

    @Autowired(required = false)
    private TelegramBotNotifier notifier;
    @Test
    void testSendToAdmin_real() {
        if (notifier == null) {
            System.out.println("[SKIP] TelegramBotNotifier 未启用");
            return;
        }
        notifier.sendToAdmin("[集成测试] 你好，管理员！ " + System.currentTimeMillis());
    }

    @Test
    void testSendToGroup_real() {
        if (notifier == null) {
            System.out.println("[SKIP] TelegramBotNotifier 未启用");
            return;
        }
        notifier.sendToGroup("[集成测试] 你好，群组！ " + System.currentTimeMillis());
    }
} 