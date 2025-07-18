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
        
        assertEquals("0.01", payProperties.getUsdtAtom());
        assertEquals("~1", payProperties.getUsdtRate());
        assertEquals(1200, payProperties.getExpireTime());
        assertTrue(payProperties.getTradeIsConfirmed());  // 默认应该为true
    }

    @Test
    void testUsdtRateWithBlank() {
        // 测试usdtRate为空的情况
        PayProperties payProperties = new PayProperties();
        payProperties.setUsdtRate("");
        payProperties.validate();
        
        assertEquals("~1", payProperties.getUsdtRate());
    }

    @Test
    void testUsdtRateWithNull() {
        // 测试usdtRate为null的情况
        PayProperties payProperties = new PayProperties();
        payProperties.setUsdtRate(null);
        payProperties.validate();
        
        assertEquals("~1", payProperties.getUsdtRate());
    }
} 