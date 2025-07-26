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
    // spl-token链配置
    private Boolean splEnable;
    private String splRpc;
    private String splAddress;
    private String splSmartContract;

    // BEP20链配置
    private Boolean bep20Enable;
    private String bep20Rpc;
    private String bep20Address;
    private String bep20SmartContract;

    @PostConstruct
    public void validate() {
        // 设置默认启用状态（如果为空）
        if (trc20Enable == null) {
            trc20Enable = false;
        }
        if (splEnable == null) {
            splEnable = false;
        }
        if (bep20Enable == null) {
            bep20Enable = false;
        }
        // 设置默认RPC地址（如果为空）
        if (StringUtils.isBlank(trc20Rpc)) {
            trc20Rpc = "grpc.trongrid.io";
        }
        if (StringUtils.isBlank(splRpc)) {
            splRpc = "https://api.mainnet-beta.solana.com";
        }
        if (StringUtils.isBlank(bep20Rpc)) {
            bep20Rpc = "https://bsc-dataseed.bnbchain.org/";
        }
        // 设置默认智能合约地址（如果为空）
        if (StringUtils.isBlank(trc20SmartContract)) {
            trc20SmartContract = "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t";
        }
        if (StringUtils.isBlank(splSmartContract)) {
            splSmartContract = "Es9vMFrzaCERmJfrF4H2FYD4KCoNkY11McCe8BenwNYB";
        }
        if (StringUtils.isBlank(bep20SmartContract)) {
            bep20SmartContract = "0x55d398326f99059fF775485246999027B3197955"; // USDT主网合约
        }
        if (Boolean.TRUE.equals(trc20Enable)) {
            if (StringUtils.isBlank(trc20Address)) {
                throw new IllegalArgumentException("chain.trc20-address 不能为空");
            }
        }
        if (Boolean.TRUE.equals(splEnable)) {
            if (StringUtils.isBlank(splAddress)) {
                throw new IllegalArgumentException("chain.spl-address 不能为空");
            }
        }
        if (Boolean.TRUE.equals(bep20Enable)) {
            if (StringUtils.isBlank(bep20Address)) {
                throw new IllegalArgumentException("chain.bep20-address 不能为空");
            }
        }
        log.info("[ChainProperties] trc20Enable={}", trc20Enable);
        log.info("[ChainProperties] trc20Rpc={}", trc20Rpc);
        log.info("[ChainProperties] trc20Address={}", trc20Address);
        log.info("[ChainProperties] trc20SmartContract={}", trc20SmartContract);
        log.info("[ChainProperties] splEnable={}", splEnable);
        log.info("[ChainProperties] splRpc={}", splRpc);
        log.info("[ChainProperties] splAddress={}", splAddress);
        log.info("[ChainProperties] splSmartContract={}", splSmartContract);
        log.info("[ChainProperties] bep20Enable={}", bep20Enable);
        log.info("[ChainProperties] bep20Rpc={}", bep20Rpc);
        log.info("[ChainProperties] bep20Address={}", bep20Address);
        log.info("[ChainProperties] bep20SmartContract={}", bep20SmartContract);
    }


} 