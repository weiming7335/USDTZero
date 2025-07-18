package io.qimo.usdtzero.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("trade_order")
public class Order {
    @TableId(type = IdType.NONE)
    private Long id;
    private String tradeNo;         // 业务主键
    private String orderNo;         // 平台订单号
    private Long amount;            // CNY金额（单位分/元，建议分）
    private Long actualAmount;      // USDT金额（最小单位）
    private String address;
    private String chainType;
    private String status;
    private String signature;
    private String usdtRate;        // 汇率策略
    private String usdtAtom;
    private Boolean tradeIsConfirmed;
    private String notifyUrl;
    private String redirectUrl;
    private Integer timeout;
    private String paymentUrl;
    private Integer notifyCount;
    private LocalDateTime createTime;
    private LocalDateTime expireTime;
    private LocalDateTime payTime;
    private String txHash;
    private String notifyStatus;
    private LocalDateTime updateTime;

} 