package io.qimo.usdtzero.api.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateOrderResponse {
    @JsonProperty("trade_no")
    private String tradeNo;
    @JsonProperty("order_no")
    private String orderNo;
    @JsonProperty("amount")
    private BigDecimal amount;
    @JsonProperty("actual_amount")
    private BigDecimal actualAmount;
    @JsonProperty("address")
    private String address;
    @JsonProperty("timeout")
    private Integer timeout;
    @JsonProperty("payment_url")
    private String paymentUrl;
} 