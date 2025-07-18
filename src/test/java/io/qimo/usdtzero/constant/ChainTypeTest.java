package io.qimo.usdtzero.constant;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ChainTypeTest {

    @Test
    void testIsValid() {
        // 测试有效的链类型
        assertTrue(ChainType.isValid("trc20"));
        assertTrue(ChainType.isValid("TRC20"));
        assertTrue(ChainType.isValid("Trc20"));
        assertTrue(ChainType.isValid("sol"));
        assertTrue(ChainType.isValid("SOL"));
        assertTrue(ChainType.isValid("Sol"));
        
        // 测试无效的链类型
        assertFalse(ChainType.isValid("ethereum"));
        assertFalse(ChainType.isValid("bitcoin"));
        assertFalse(ChainType.isValid(""));
        assertFalse(ChainType.isValid(null));
        assertFalse(ChainType.isValid("invalid"));
    }

    @Test
    void testValidate() {
        // 测试有效的链类型（不应该抛出异常）
        assertDoesNotThrow(() -> ChainType.validate("trc20"));
        assertDoesNotThrow(() -> ChainType.validate("TRC20"));
        assertDoesNotThrow(() -> ChainType.validate("sol"));
        assertDoesNotThrow(() -> ChainType.validate("SOL"));
        
        // 测试无效的链类型（应该抛出异常）
        IllegalArgumentException exception1 = assertThrows(IllegalArgumentException.class, 
                () -> ChainType.validate("ethereum"));
        assertTrue(exception1.getMessage().contains("无效的链类型: ethereum"));
        assertTrue(exception1.getMessage().contains("支持的链类型"));
        
        IllegalArgumentException exception2 = assertThrows(IllegalArgumentException.class, 
                () -> ChainType.validate(""));
        assertTrue(exception2.getMessage().contains("无效的链类型: "));
        
        IllegalArgumentException exception3 = assertThrows(IllegalArgumentException.class, 
                () -> ChainType.validate(null));
        assertTrue(exception3.getMessage().contains("无效的链类型: null"));
    }

    @Test
    void testGetValidChainTypes() {
        Set<String> validTypes = ChainType.getValidChainTypes();
        
        assertEquals(2, validTypes.size());
        assertTrue(validTypes.contains("trc20"));
        assertTrue(validTypes.contains("sol"));
        
        // 验证返回的是副本，不是原始集合
        validTypes.add("test");
        Set<String> originalTypes = ChainType.getValidChainTypes();
        assertEquals(2, originalTypes.size());
        assertFalse(originalTypes.contains("test"));
    }

    @Test
    void testGetValidChainTypesString() {
        String validTypesString = ChainType.getValidChainTypesString();
        
        assertTrue(validTypesString.contains("trc20"));
        assertTrue(validTypesString.contains("sol"));
        assertTrue(validTypesString.contains(","));
        
        // 验证格式
        assertTrue(validTypesString.matches(".*trc20.*sol.*") || validTypesString.matches(".*sol.*trc20.*"));
    }

    @Test
    void testConstants() {
        // 验证常量值
        assertEquals("trc20", ChainType.TRC20);
        assertEquals("sol", ChainType.SOL);
    }
} 