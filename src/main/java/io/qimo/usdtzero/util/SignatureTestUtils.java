package io.qimo.usdtzero.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.qimo.usdtzero.api.request.CreateOrderRequest;
import io.qimo.usdtzero.api.request.CancelOrderRequest;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * 签名测试工具类
 * 用于生成测试用的签名，方便接口调试
 */
@Slf4j
public class SignatureTestUtils {
    
    private static final String TEST_TOKEN = "your_test_token_here";
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * 为CreateOrderDTO生成签名
     */
    public static String generateCreateOrderSignature(CreateOrderRequest dto) {
        Map<String, String> params = new HashMap<>();
        
        if (dto.getAddress() != null) {
            params.put("address", dto.getAddress());
        }
        if (dto.getChainType() != null) {
            params.put("chain_type", dto.getChainType());
        }
        if (dto.getOrderNo() != null) {
            params.put("order_no", dto.getOrderNo());
        }
        if (dto.getAmount() != null) {
            params.put("amount", dto.getAmount().toPlainString());
        }
        if (dto.getNotifyUrl() != null) {
            params.put("notify_url", dto.getNotifyUrl());
        }
        if (dto.getTimeout() != null) {
            params.put("timeout", dto.getTimeout().toString());
        }
        if (dto.getRate() != null) {
            params.put("rate", dto.getRate());
        }
        
        return SignUtils.generateSignature(params, TEST_TOKEN);
    }
    
    /**
     * 为CancelOrderDTO生成签名
     */
    public static String generateCancelOrderSignature(CancelOrderRequest dto) {
        Map<String, String> params = new HashMap<>();
        
        if (dto.getTradeNo() != null) {
            params.put("trade_no", dto.getTradeNo());
        }
        
        return SignUtils.generateSignature(params, TEST_TOKEN);
    }
    
    /**
     * 生成测试用的CreateOrderDTO
     */
    public static CreateOrderRequest createTestCreateOrderDTO() {
        CreateOrderRequest dto = new CreateOrderRequest();
        dto.setAddress("TRC20测试地址");
        dto.setChainType("TRC20");
        dto.setOrderNo("TEST_ORDER_" + System.currentTimeMillis());
        dto.setAmount(new BigDecimal("100.50"));
        dto.setNotifyUrl("https://example.com/notify");
        dto.setTimeout(600);
        dto.setRate("7.2");
        
        // 生成签名
        String signature = generateCreateOrderSignature(dto);
        dto.setSignature(signature);
        
        return dto;
    }
    
    /**
     * 生成测试用的CancelOrderDTO
     */
    public static CancelOrderRequest createTestCancelOrderDTO() {
        CancelOrderRequest dto = new CancelOrderRequest();
        dto.setTradeNo("TEST_TRADE_" + System.currentTimeMillis());
        
        // 生成签名
        String signature = generateCancelOrderSignature(dto);
        dto.setSignature(signature);
        
        return dto;
    }
    
    /**
     * 打印测试用的JSON请求体
     */
    public static void printTestRequest() {
        try {
            CreateOrderRequest createDto = createTestCreateOrderDTO();
            CancelOrderRequest cancelDto = createTestCancelOrderDTO();
            
            log.info("=== 测试签名生成 ===");
            log.info("CreateOrder请求体:");
            log.info(objectMapper.writeValueAsString(createDto));
            log.info("");
            log.info("CancelOrder请求体:");
            log.info(objectMapper.writeValueAsString(cancelDto));
            log.info("");
            log.info("测试Token: {}", TEST_TOKEN);
            log.info("请确保application.yml中的app.auth-token配置为: {}", TEST_TOKEN);
            
        } catch (Exception e) {
            log.error("生成测试请求失败", e);
        }
    }
    
    public static void main(String[] args) {
        printTestRequest();
    }
} 