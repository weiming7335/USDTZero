package io.qimo.usdtzero.api.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.math.BigDecimal;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;

@Data
public class CreateOrderRequest {
    private String address;
    @NotBlank(message = "链类型不能为空")
    @JsonProperty("chain_type")
    private String chainType;
    @JsonProperty("order_no")
    private String orderNo;
    @NotNull(message = "金额不能为空")
    @DecimalMin(value = "0.01", message = "金额必须大于等于0.01")
    private BigDecimal amount;
    @NotBlank(message = "签名不能为空")
    private String signature;
    @JsonProperty("notify_url")
    private String notifyUrl;
//    @JsonProperty("redirect_url")
//    private String redirectUrl;
    private Integer timeout;
    @JsonProperty("rate")
    private String rate;
} 