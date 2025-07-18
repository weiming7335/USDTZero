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
@ConfigurationProperties(prefix = "pay")
public class PayProperties {
    private String usdtAtom;
    private String usdtRate;
    private Integer expireTime;
    private Boolean tradeIsConfirmed;

    @PostConstruct
    public void validate() {
        if (StringUtils.isBlank(usdtAtom)) {
            usdtAtom = "0.01";
        }
        if (StringUtils.isBlank(usdtRate)) {
            usdtRate = "~1";
        }
        if (expireTime == null) {
            expireTime = 1200;
        }
        if (tradeIsConfirmed == null) {
            tradeIsConfirmed = true;
        }
        // 校验usdtAtom只能为0.1、0.01、0.001
        if (!"0.1".equals(usdtAtom) && !"0.01".equals(usdtAtom) && !"0.001".equals(usdtAtom)) {
            throw new IllegalArgumentException("usdtAtom 只允许为 0.1、0.01 或 0.001");
        }
        log.info("[PayProperties] usdtAtom={}", usdtAtom);
        log.info("[PayProperties] usdtRate={}", usdtRate);
        log.info("[PayProperties] expireTime={}", expireTime);
        log.info("[PayProperties] tradeIsConfirmed={}", tradeIsConfirmed);
    }

    /**
     * 根据usdtAtom返回USDT金额保留的小数位数
     */
    public int getUsdtScale() {
        if ("0.1".equals(usdtAtom)) return 1;
        if ("0.01".equals(usdtAtom)) return 2;
        if ("0.001".equals(usdtAtom)) return 3;
        return 2;
    }
} 