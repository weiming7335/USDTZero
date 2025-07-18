package io.qimo.usdtzero.config;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;

class AppPropertiesTest {

    @Test
    void testUriWithEmptyValue() {
        AppProperties appProperties = new AppProperties();
        appProperties.setUri("");
        appProperties.setAuthToken("test-token");
        // 设置测试端口
        ReflectionTestUtils.setField(appProperties, "serverPort", 23456);
        appProperties.validate();
        
        // uri应该被设置为IP:port格式
        assertNotNull(appProperties.getUri());
        assertTrue(appProperties.getUri().contains(":"));
        assertTrue(appProperties.getUri().endsWith(":23456"));
    }

    @Test
    void testUriWithNullValue() {
        AppProperties appProperties = new AppProperties();
        appProperties.setUri(null);
        appProperties.setAuthToken("test-token");
        // 设置测试端口
        ReflectionTestUtils.setField(appProperties, "serverPort", 8080);
        appProperties.validate();
        
        // uri应该被设置为IP:port格式
        assertNotNull(appProperties.getUri());
        assertTrue(appProperties.getUri().contains(":"));
        assertTrue(appProperties.getUri().endsWith(":8080"));
    }

    @Test
    void testUriWithValidValue() {
        AppProperties appProperties = new AppProperties();
        appProperties.setUri("https://example.com");
        appProperties.setAuthToken("test-token");
        appProperties.validate();
        
        // uri应该保持不变
        assertEquals("https://example.com", appProperties.getUri());
    }

    @Test
    void testAuthTokenValidation() {
        AppProperties appProperties = new AppProperties();
        appProperties.setAuthToken("");
        
        // 应该抛出异常
        assertThrows(IllegalArgumentException.class, () -> appProperties.validate());
    }

    @Test
    void testAuthTokenNullValidation() {
        AppProperties appProperties = new AppProperties();
        appProperties.setAuthToken(null);
        
        // 应该抛出异常
        assertThrows(IllegalArgumentException.class, () -> appProperties.validate());
    }

    @Test
    void testDefaultValues() {
        AppProperties appProperties = new AppProperties();
        appProperties.setAuthToken("test-token");
        appProperties.validate();
        
        assertEquals("static", appProperties.getStaticPath());
        assertNull(appProperties.getSqlitePath());
        assertNotNull(appProperties.getUri());
        assertTrue(appProperties.getUri().contains(":"));
    }
    
    @Test
    void testServerPort() {
        AppProperties appProperties = new AppProperties();
        appProperties.setAuthToken("test-token");
        // 设置测试端口
        ReflectionTestUtils.setField(appProperties, "serverPort", 9999);
        appProperties.validate();
        
        assertEquals(9999, appProperties.getServerPort());
    }
    
    @Test
    void testUriWithCustomPort() {
        AppProperties appProperties = new AppProperties();
        appProperties.setUri("");
        appProperties.setAuthToken("test-token");
        // 设置自定义端口
        ReflectionTestUtils.setField(appProperties, "serverPort", 54321);
        appProperties.validate();
        
        // uri应该使用自定义端口
        assertNotNull(appProperties.getUri());
        assertTrue(appProperties.getUri().endsWith(":54321"));
    }
} 