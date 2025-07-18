package io.qimo.usdtzero.api.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CancelOrderRequest {
    @NotNull(message = "交易编号不能为空")
    @JsonProperty("trade_no")
    private String tradeNo;
    @NotNull(message = "签名不能为空")
    private String signature;
} 