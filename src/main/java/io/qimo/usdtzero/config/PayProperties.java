package io.qimo.usdtzero.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.util.regex.Pattern;

@Data
@Slf4j
@Component
@ConfigurationProperties(prefix = "pay")
public class PayProperties {
    private String atom;
    private String rate;
    private Integer timeout;
    private Boolean tradeIsConfirmed;

    // 汇率格式校验正则表达式
    private static final Pattern RATE_PATTERN = Pattern.compile("^(~[0-9]+(\\.[0-9]+)?|[+-][0-9]+(\\.[0-9]+)?)$");

    @PostConstruct
    public void validate() {
        if (StringUtils.isBlank(atom)) {
            atom = "0.01";
        }
        if (StringUtils.isBlank(rate)) {
            rate = "~1";
        }
        if (timeout == null) {
            timeout = 1200;
        }
        if (tradeIsConfirmed == null) {
            tradeIsConfirmed = true;
        }
        // 校验atom只能为0.1、0.01、0.001
        if (!"0.1".equals(atom) && !"0.01".equals(atom) && !"0.001".equals(atom)) {
            throw new IllegalArgumentException("atom 只允许为 0.1、0.01 或 0.001");
        }
        // 校验rate格式
        validateRate();
        log.info("[PayProperties] atom={}", atom);
        log.info("[PayProperties] rate={}", rate);
        log.info("[PayProperties] timeout={}", timeout);
        log.info("[PayProperties] tradeIsConfirmed={}", tradeIsConfirmed);
    }

    /**
     * 校验rate格式
     * 支持格式：~1.02、~0.97、+0.3、-0.2
     */
    private void validateRate() {
        if (StringUtils.isNotBlank(rate) && !RATE_PATTERN.matcher(rate).matches()) {
            throw new IllegalArgumentException("rate 格式不正确，支持格式：~1.02、~0.97、+0.3、-0.2");
        }
    }

    /**
     * 根据atom返回金额保留的小数位数
     */
    public int getScale() {
        if ("0.1".equals(atom)) return 1;
        if ("0.01".equals(atom)) return 2;
        if ("0.001".equals(atom)) return 3;
        return 2;
    }
} 