package io.qimo.usdtzero.event;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class OrderMessage {
    private String tradeNo;
    private String toAddress;
    private BigDecimal amount;         // CNY金额（单位元）
    private BigDecimal actualAmount;   // USDT金额
    private LocalDateTime payTime;
    private String chainType;
    private LocalDateTime createTime;
    private String txHash;
} 