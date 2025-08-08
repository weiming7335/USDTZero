package io.qimo.usdtzero.util;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * 签名测试类
 */
public class SignatureTest {
    
    private static final String TEST_TOKEN = "your_test_token_here";
    
    public static void main(String[] args) {
        System.out.println("=== 签名测试 ===");
        
        // 测试创建订单签名
        testCreateOrderSignature();
        
        System.out.println();
        
        // 测试取消订单签名
        testCancelOrderSignature();
        
        System.out.println();
        System.out.println("测试Token: " + TEST_TOKEN);
        System.out.println("请确保application.yml中的app.auth-token配置为: " + TEST_TOKEN);
    }
    
    private static void testCreateOrderSignature() {
        System.out.println("--- 创建订单签名测试 ---");
        
        Map<String, String> params = new HashMap<>();
        params.put("address", "TRC20测试地址");
        params.put("chain_type", "TRC20");
        params.put("order_no", "TEST_ORDER_123");
        params.put("amount", "100.50");
        params.put("notify_url", "https://example.com/notify");
        params.put("redirect_url", "https://example.com/redirect");
        params.put("timeout", "600");
        params.put("usdt_rate", "7.2");
        
        String signature = SignUtils.generateSignature(params, TEST_TOKEN);
        
        System.out.println("参数:");
        for (Map.Entry<String, String> entry : params.entrySet()) {
            System.out.println("  " + entry.getKey() + " = " + entry.getValue());
        }
        System.out.println("生成的签名: " + signature);
        
        // 构建完整的请求JSON
        System.out.println("完整请求JSON:");
        System.out.println("{");
        System.out.println("  \"address\": \"TRC20测试地址\",");
        System.out.println("  \"chain_type\": \"TRC20\",");
        System.out.println("  \"order_no\": \"TEST_ORDER_123\",");
        System.out.println("  \"amount\": 100.50,");
        System.out.println("  \"notify_url\": \"https://example.com/notify\",");
        System.out.println("  \"redirect_url\": \"https://example.com/redirect\",");
        System.out.println("  \"timeout\": 600,");
        System.out.println("  \"usdt_rate\": \"7.2\",");
        System.out.println("  \"signature\": \"" + signature + "\"");
        System.out.println("}");
    }
    
    private static void testCancelOrderSignature() {
        System.out.println("--- 取消订单签名测试 ---");
        
        Map<String, String> params = new HashMap<>();
        params.put("trade_no", "TEST_TRADE_123");
        
        String signature = SignUtils.generateSignature(params, TEST_TOKEN);
        
        System.out.println("参数:");
        for (Map.Entry<String, String> entry : params.entrySet()) {
            System.out.println("  " + entry.getKey() + " = " + entry.getValue());
        }
        System.out.println("生成的签名: " + signature);
        
        // 构建完整的请求JSON
        System.out.println("完整请求JSON:");
        System.out.println("{");
        System.out.println("  \"trade_no\": \"TEST_TRADE_123\",");
        System.out.println("  \"signature\": \"" + signature + "\"");
        System.out.println("}");
    }
} 