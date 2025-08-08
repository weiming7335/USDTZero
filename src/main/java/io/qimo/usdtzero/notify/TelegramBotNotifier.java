package io.qimo.usdtzero.notify;

import io.qimo.usdtzero.config.BotProperties;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.checkerframework.checker.units.qual.A;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import jakarta.annotation.PostConstruct;
import java.net.InetSocketAddress;
import java.net.Proxy;
import okhttp3.Credentials;
import okhttp3.OkHttpClient;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "bot", name = "enable", havingValue = "true")
public class TelegramBotNotifier {
    @Autowired
    private BotProperties botProperties;
    @Autowired
    private TelegramClient telegramClient;
    public void sendToAdmin(String text) {
        SendMessage message = SendMessage.builder()
                .chatId(botProperties.getAdminId())
                .text(text)
                .build();
        try {
            telegramClient.execute(message);
        } catch (TelegramApiException e) {
            log.error("发送Telegram消息失败: {}", e.getMessage(), e);
        }
    }

    public void sendToGroup(String text) {
        if (StringUtils.isBlank(botProperties.getGroupId())) return;
        SendMessage message = SendMessage.builder()
                .chatId(botProperties.getGroupId())
                .text(text)
                .build();
        try {
            telegramClient.execute(message);
        } catch (TelegramApiException e) {
            log.error("发送Telegram群组消息失败: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 发送项目启动成功通知
     */
    @PostConstruct
    public void sendStartupNotification() {
        String message = "🚀 USDTZero 项目启动成功！";
        sendToAdmin(message);
        sendToGroup(message);
    }
} 