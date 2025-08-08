package io.qimo.usdtzero.api.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class CancelOrderResponse {
    @JsonProperty("trade_no")
    private String tradeNo;
} 