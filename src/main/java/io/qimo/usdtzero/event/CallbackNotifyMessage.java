package io.qimo.usdtzero.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CallbackNotifyMessage {
    private String tradeNo;
    private String status; // 订单状态
} 