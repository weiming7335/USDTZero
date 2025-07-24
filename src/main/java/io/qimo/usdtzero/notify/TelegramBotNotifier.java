package io.qimo.usdtzero.notify;

import io.qimo.usdtzero.config.BotProperties;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
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
    private TelegramClient telegramClient;

    @PostConstruct
    public void init() {
        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        if (StringUtils.isNotBlank(botProperties.getHostname()) && botProperties.getPort() != null) {
            Proxy proxy = new Proxy(Proxy.Type.SOCKS,
                    new InetSocketAddress(botProperties.getHostname(), botProperties.getPort()));
            builder.proxy(proxy);
            if (StringUtils.isNotBlank(botProperties.getUsername()) && StringUtils.isNotBlank(botProperties.getPassword())) {
                builder.proxyAuthenticator((route, response) -> {
                    String credential = Credentials.basic(botProperties.getUsername(), botProperties.getPassword());
                    return response.request().newBuilder()
                            .header("Proxy-Authorization", credential)
                            .build();
                });
            }
        }
        telegramClient = new OkHttpTelegramClient(builder.build(), botProperties.getToken());
        log.info("[TelegramBotNotifier] Bot initialized with proxy: {}:{}", botProperties.getHostname(), botProperties.getPort());
    }

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
} 