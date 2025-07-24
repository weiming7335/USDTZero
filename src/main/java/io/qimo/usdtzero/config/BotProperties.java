package io.qimo.usdtzero.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Slf4j
@Component
@ConfigurationProperties(prefix = "bot")
public class BotProperties {
    private Boolean enable = false;
    private Long adminId;
    private String token;
    private String groupId;
    private String hostname;
    private Integer port;
    private String username;
    private String password;

    @PostConstruct
    public void validate() {
        if (Boolean.TRUE.equals(enable)) {
            if (adminId == null) {
                throw new IllegalArgumentException("bot.admin-id 不能为空");
            }
            if (StringUtils.isBlank(token)) {
                throw new IllegalArgumentException("bot.token 不能为空");
            }
        }
        log.info("[BotProperties] enable={}", enable);
        log.info("[BotProperties] adminId={}", adminId);
        log.info("[BotProperties] token={}", token);
        log.info("[BotProperties] groupId={}", groupId);
        log.info("[BotProperties] hostname={}", hostname);
        log.info("[BotProperties] port={}", port);
        log.info("[BotProperties] username={}", username);
        log.info("[BotProperties] password={}", password);
    }
} 