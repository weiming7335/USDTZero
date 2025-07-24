package io.qimo.usdtzero.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = {
    "pay.trade-is-confirmed=true"
})
class PayPropertiesTest {

    @Test
    void testTradeIsConfirmedWithTrue() {
        // 测试配置为true的情况
        PayProperties payProperties = new PayProperties();
        payProperties.setTradeIsConfirmed(true);
        payProperties.validate();
        
        assertTrue(payProperties.getTradeIsConfirmed());
    }

    @Test
    void testTradeIsConfirmedWithFalse() {
        // 测试配置为false的情况
        PayProperties payProperties = new PayProperties();
        payProperties.setTradeIsConfirmed(false);
        payProperties.validate();
        
        assertFalse(payProperties.getTradeIsConfirmed());
    }

    @Test
    void testTradeIsConfirmedWithNull() {
        // 测试配置为null的情况，应该默认为true
        PayProperties payProperties = new PayProperties();
        payProperties.setTradeIsConfirmed(null);
        payProperties.validate();
        
        assertTrue(payProperties.getTradeIsConfirmed());
    }

    @Test
    void testDefaultValues() {
        // 测试默认值
        PayProperties payProperties = new PayProperties();
        payProperties.validate();
        
        assertEquals("0.01", payProperties.getAtom());
        assertEquals("~1", payProperties.getRate());
        assertEquals(1200, payProperties.getTimeout());
        assertTrue(payProperties.getTradeIsConfirmed());  // 默认应该为true
    }

    @Test
    void testRateWithBlank() {
        // 测试rate为空的情况
        PayProperties payProperties = new PayProperties();
        payProperties.setRate("");
        payProperties.validate();
        
        assertEquals("~1", payProperties.getRate());
    }

    @Test
    void testRateWithNull() {
        // 测试rate为null的情况
        PayProperties payProperties = new PayProperties();
        payProperties.setRate(null);
        payProperties.validate();
        
        assertEquals("~1", payProperties.getRate());
    }

    @Test
    void testRateWithValidFormats() {
        // 测试有效的rate格式
        String[] validRates = {"~1.02", "~0.97", "+0.3", "-0.2", "~1", "+1", "-1", "~1.5", "+0.05", "-0.05"};
        
        for (String validRate : validRates) {
            PayProperties payProperties = new PayProperties();
            payProperties.setRate(validRate);
            assertDoesNotThrow(() -> payProperties.validate(), 
                "rate格式 " + validRate + " 应该有效");
        }
    }

    @Test
    void testRateWithInvalidFormats() {
        // 测试无效的rate格式
        String[] invalidRates = {"1.02", "0.97", "0.3", "0.2", "abc", "~", "+", "-", "~abc", "+abc", "-abc", "1.02~", "0.3+"};
        
        for (String invalidRate : invalidRates) {
            PayProperties payProperties = new PayProperties();
            payProperties.setRate(invalidRate);
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, 
                () -> payProperties.validate(), 
                "rate格式 " + invalidRate + " 应该无效");
            assertTrue(exception.getMessage().contains("rate 格式不正确"));
        }
    }

    @Test
    void testRateWithEmptyString() {
        // 测试rate为空字符串的情况，应该使用默认值
        PayProperties payProperties = new PayProperties();
        payProperties.setRate("");
        payProperties.validate();
        
        assertEquals("~1", payProperties.getRate());
    }
} 