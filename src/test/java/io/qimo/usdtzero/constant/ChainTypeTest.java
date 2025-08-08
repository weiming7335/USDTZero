package io.qimo.usdtzero.constant;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ChainTypeTest {

    @Test
    void testIsValid() {
        // 测试有效的链类型
        assertTrue(ChainType.isValid("TRC20"));
        assertTrue(ChainType.isValid("SPL"));
        assertTrue(ChainType.isValid("BEP20"));
        
        // 测试无效的链类型
        assertFalse(ChainType.isValid("trc20")); // 小写无效
        assertFalse(ChainType.isValid("spl")); // 小写无效
        assertFalse(ChainType.isValid("bep20")); // 小写无效
        assertFalse(ChainType.isValid("ethereum"));
        assertFalse(ChainType.isValid("bitcoin"));
        assertFalse(ChainType.isValid(""));
        assertFalse(ChainType.isValid(null));
        assertFalse(ChainType.isValid("invalid"));
    }

    @Test
    void testValidate() {
        // 测试有效的链类型（不应该抛出异常）
        assertDoesNotThrow(() -> ChainType.validate("TRC20"));
        assertDoesNotThrow(() -> ChainType.validate("SPL"));
        assertDoesNotThrow(() -> ChainType.validate("BEP20"));
        
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
    void testGetValidChainTypesString() {
        String validTypesString = ChainType.getValidChainTypesString();
        
        assertTrue(validTypesString.contains("TRC20"));
        assertTrue(validTypesString.contains("SPL"));
        assertTrue(validTypesString.contains("BEP20"));
        assertTrue(validTypesString.contains(","));
        
        // 验证格式 - 包含所有三种链类型
        assertTrue(validTypesString.contains("TRC20"));
        assertTrue(validTypesString.contains("SPL"));
        assertTrue(validTypesString.contains("BEP20"));
    }

    @Test
    void testConstants() {
        // 验证常量值
        assertEquals("TRC20", ChainType.TRC20);
        assertEquals("SPL", ChainType.SPL);
        assertEquals("BEP20", ChainType.BEP20);
    }
} 