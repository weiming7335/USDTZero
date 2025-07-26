package io.qimo.usdtzero.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.qimo.usdtzero.api.response.CreateOrderResponse;
import io.qimo.usdtzero.constant.ChainType;
import io.qimo.usdtzero.model.ApiResponse;
import io.qimo.usdtzero.model.Order;
import io.qimo.usdtzero.util.SignUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class OrderApiIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @LocalServerPort
    private int port;

    private String baseUrl;

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port;
    }

    private String generateValidSignature(Map<String, Object> requestData) {
        Map<String, String> paramsForSignature = new HashMap<>();
        for (Map.Entry<String, Object> entry : requestData.entrySet()) {
            if (!"signature".equals(entry.getKey()) && entry.getValue() != null) {
                paramsForSignature.put(entry.getKey(), entry.getValue().toString());
            }
        }
        return SignUtils.generateSignature(paramsForSignature, "your_test_token_here");
    }

    @Test
    void testCreateOrderSuccess() {
        // 准备请求数据
        Map<String, Object> requestData = new HashMap<>();
        requestData.put("address", "TTrtQRMxRdr3fNBCz9xQwYQNZNFmcu4srM");
        requestData.put("chain_type", ChainType.TRC20);
        requestData.put("order_no", "TEST_ORDER_" + System.currentTimeMillis());
        requestData.put("amount", BigDecimal.valueOf(100));
        requestData.put("timeout", 60);
        requestData.put("signature", generateValidSignature(requestData));

        // 设置请求头
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestData, headers);

        // 发送请求
        ResponseEntity<ApiResponse> response = restTemplate.postForEntity(
            baseUrl + "/api/v1/order/create",
            request,
            ApiResponse.class
        );

        // 验证响应
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(0, response.getBody().getCode());
        assertEquals("成功", response.getBody().getMessage());
        
        // 验证返回的订单数据
        CreateOrderResponse order = objectMapper.convertValue(response.getBody().getData(), CreateOrderResponse.class);
        assertNotNull(order);
        assertEquals(requestData.get("order_no"), order.getOrderNo());
        assertEquals(requestData.get("address"), order.getAddress());
        assertEquals(100, order.getAmount().longValue());
    }

    @Test
    void testCreateOrderWithInvalidSignature() {
        // 准备请求数据（无效签名）
        Map<String, Object> requestData = new HashMap<>();
        requestData.put("address", "TRC20测试地址");
        requestData.put("chain_type", "TRC20");
        requestData.put("order_no", "TEST_ORDER_" + System.currentTimeMillis());
        requestData.put("amount", "100.50");
        requestData.put("signature", "invalid_signature"); // 专门测试签名异常

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestData, headers);

        // 发送请求
        ResponseEntity<ApiResponse> response = restTemplate.postForEntity(
            baseUrl + "/api/v1/order/create",
            request,
            ApiResponse.class
        );

        // 验证响应
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1102, response.getBody().getCode()); // SIGNATURE_INVALID
        assertEquals("签名验证失败", response.getBody().getMessage());
    }

    @Test
    void testCreateOrderWithMissingSignature() {
        // 准备请求数据（缺失签名）
        Map<String, Object> requestData = new HashMap<>();
        requestData.put("address", "TRC20测试地址");
        requestData.put("chain_type", "TRC20");
        requestData.put("order_no", "TEST_ORDER_" + System.currentTimeMillis());
        requestData.put("amount", "100.50");
        // 不加 signature 字段，专门测试缺失签名

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestData, headers);

        // 发送请求
        ResponseEntity<ApiResponse> response = restTemplate.postForEntity(
            baseUrl + "/api/v1/order/create",
            request,
            ApiResponse.class
        );

        // 验证响应
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1003, response.getBody().getCode()); // SIGNATURE_MISSING
        assertEquals("签名不能为空", response.getBody().getMessage());
    }

    @Test
    void testCreateOrderWithEmptyRequestBody() {
        // 只传空签名
        Map<String, Object> requestData = new HashMap<>();
        requestData.put("signature", "");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestData, headers);

        // 发送请求
        ResponseEntity<ApiResponse> response = restTemplate.postForEntity(
            baseUrl + "/api/v1/order/create",
            request,
            ApiResponse.class
        );

        // 验证响应
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1003, response.getBody().getCode());
    }

    @Test
    void testCancelOrderSuccess() {
        // 先创建一个订单
        String tradeNo = createTestOrder();
        
        // 准备取消订单请求
        Map<String, Object> requestData = new HashMap<>();
        requestData.put("trade_no", tradeNo);
        requestData.put("signature", generateValidSignature(requestData));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestData, headers);

        // 发送取消订单请求
        ResponseEntity<ApiResponse> response = restTemplate.postForEntity(
            baseUrl + "/api/v1/order/cancel",
            request,
            ApiResponse.class
        );

        // 验证响应
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(0, response.getBody().getCode());
        assertEquals("成功", response.getBody().getMessage());
    }

    @Test
    void testCancelOrderWithInvalidOrderNo() {
        // 准备取消订单请求（无效订单号）
        Map<String, Object> requestData = new HashMap<>();
        requestData.put("trade_no", "INVALID_ORDER_NO");
        
        // 生成有效签名
        Map<String, String> paramsForSignature = new HashMap<>();
        paramsForSignature.put("trade_no", "INVALID_ORDER_NO");
        
        String validSignature = SignUtils.generateSignature(paramsForSignature, "your_test_token_here");
        requestData.put("signature", validSignature);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestData, headers);

        // 发送取消订单请求
        ResponseEntity<ApiResponse> response = restTemplate.postForEntity(
            baseUrl + "/api/v1/order/cancel",
            request,
            ApiResponse.class
        );

        // 验证响应
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(2001, response.getBody().getCode()); // ORDER_NOT_FOUND
        assertEquals("订单不存在", response.getBody().getMessage());
    }

    @Test
    void testHealthCheck() {
        // 测试健康检查接口
        ResponseEntity<String> response = restTemplate.getForEntity(
            baseUrl + "/actuator/health",
            String.class
        );

        // 验证响应
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("UP"));
    }

    @Test
    void testNonExistentEndpoint() {
        // 测试不存在的接口
        ResponseEntity<ApiResponse> response = restTemplate.getForEntity(
            baseUrl + "/api/v1/nonexistent",
            ApiResponse.class
        );

        // 验证响应
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    /**
     * 辅助方法：创建测试订单
     */
    private String createTestOrder() {
        Map<String, Object> requestData = new HashMap<>();
        String orderNo = "TEST_ORDER_" + System.currentTimeMillis();
        requestData.put("address", "TRC20测试地址");
        requestData.put("chain_type", "TRC20");
        requestData.put("order_no", orderNo);
        requestData.put("amount", "100.50");
        requestData.put("signature", generateValidSignature(requestData));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestData, headers);

        ResponseEntity<ApiResponse> response = restTemplate.postForEntity(
            baseUrl + "/api/v1/order/create",
            request,
            ApiResponse.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        // 从响应体中获取tradeNo
        Map<String, Object> data = objectMapper.convertValue(response.getBody().getData(), Map.class);
        return data.get("trade_no") != null ? data.get("trade_no").toString() : null;
    }
} 