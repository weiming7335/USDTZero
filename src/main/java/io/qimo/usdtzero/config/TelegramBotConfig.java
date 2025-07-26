package io.qimo.usdtzero.config;

import io.qimo.usdtzero.notify.TelegramCommandBot;
import io.qimo.usdtzero.service.LightweightMetricsService;
import io.qimo.usdtzero.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Credentials;
import okhttp3.OkHttpClient;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.net.InetSocketAddress;
import java.net.Proxy;
import jakarta.annotation.PreDestroy;

@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "bot", name = "enable", havingValue = "true")
public class TelegramBotConfig {

    @Autowired
    private BotProperties botProperties;

    @Autowired
    private LightweightMetricsService metricsService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private TelegramBotsLongPollingApplication telegramBotsApplication;

    private TelegramCommandBot telegramCommandBot;

    @Bean
    public TelegramClient telegramClient() {
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
        TelegramClient telegramClient = new OkHttpTelegramClient(builder.build(), botProperties.getToken());
        log.info("[TelegramBotConfig] Bot initialized with proxy: {}:{}", botProperties.getHostname(), botProperties.getPort());
        return telegramClient;
    }

    @Bean
    public TelegramCommandBot telegramCommandBot() {
        log.info("初始化 TelegramCommandBot");
        this.telegramCommandBot = new TelegramCommandBot(telegramClient(), botProperties, metricsService, orderService);
        
        // 注册Bot到TelegramBotsLongPollingApplication
        try {
            this.telegramCommandBot.onRegister();
            telegramBotsApplication.registerBot(botProperties.getToken(), this.telegramCommandBot);
            log.info("TelegramCommandBot 注册成功");
        } catch (Exception e) {
            log.error("TelegramCommandBot 注册失败", e);
            throw new RuntimeException("TelegramCommandBot 注册失败", e);
        }
        
        return this.telegramCommandBot;
    }

    /**
     * 进程关闭时注销机器人
     */
    @PreDestroy
    public void destroy() {
        if (telegramCommandBot != null) {
            try {
                log.info("正在注销 TelegramCommandBot...");
                // 停止TelegramBotsLongPollingApplication
                telegramBotsApplication.stop();
                log.info("TelegramCommandBot 注销成功");
            } catch (Exception e) {
                log.error("TelegramCommandBot 注销失败", e);
            }
        }
    }
} 