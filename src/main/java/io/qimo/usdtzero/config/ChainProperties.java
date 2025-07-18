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
@ConfigurationProperties(prefix = "chain")
public class ChainProperties {
    // TRC20链配置
    private Boolean trc20Enable;
    private String trc20Rpc;
    private String trc20Address;
    private String trc20SmartContract;
    private Boolean solEnable;
    private String solRpc;
    private String solAddress;
    private String solSmartContract;

    @PostConstruct
    public void validate() {
        // 设置默认启用状态（如果为空）
        if (trc20Enable == null) {
            trc20Enable = false;
        }
        if (solEnable == null) {
            solEnable = false;
        }
        
        // 设置默认RPC地址（如果为空）
        if (StringUtils.isBlank(trc20Rpc)) {
            trc20Rpc = "https://api.trongrid.io";
        }
        if (StringUtils.isBlank(solRpc)) {
            solRpc = "https://api.mainnet-beta.solana.com";
        }
        
        // 设置默认智能合约地址（如果为空）
        if (StringUtils.isBlank(trc20SmartContract)) {
            trc20SmartContract = "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t";
        }
        if (StringUtils.isBlank(solSmartContract)) {
            solSmartContract = "Es9vMFrzaCERmJfrF4H2FYD4KCoNkY11McCe8BenwNYB";
        }
        
        if (Boolean.TRUE.equals(trc20Enable)) {
            if (StringUtils.isBlank(trc20Address)) {
                throw new IllegalArgumentException("chain.trc20-address 不能为空");
            }
        }
        if (Boolean.TRUE.equals(solEnable)) {
            if (StringUtils.isBlank(solAddress)) {
                throw new IllegalArgumentException("chain.sol-address 不能为空");
            }
        }
        log.info("[ChainProperties] trc20Enable={}", trc20Enable);
        log.info("[ChainProperties] trc20Rpc={}", trc20Rpc);
        log.info("[ChainProperties] trc20Address={}", trc20Address);
        log.info("[ChainProperties] trc20SmartContract={}", trc20SmartContract);
        log.info("[ChainProperties] solEnable={}", solEnable);
        log.info("[ChainProperties] solRpc={}", solRpc);
        log.info("[ChainProperties] solAddress={}", solAddress);
        log.info("[ChainProperties] solSmartContract={}", solSmartContract);
    }
} 