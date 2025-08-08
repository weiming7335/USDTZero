package io.qimo.usdtzero.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class AmountPoolServiceTest {

    private AmountPoolService amountPoolService;

    @BeforeEach
    void setUp() {
        amountPoolService = new AmountPoolService();
    }

    @Test
    void testAllocateAmount_Success() {
        boolean result = amountPoolService.allocateAmount("TRC20_ADDRESS", 1000000L);
        assertTrue(result);
        assertFalse(amountPoolService.isAmountAvailable("TRC20_ADDRESS", 1000000L));
    }

    @Test
    void testAllocateAmount_Duplicate() {
        boolean result1 = amountPoolService.allocateAmount("TRC20_ADDRESS", 1000000L);
        assertTrue(result1);
        boolean result2 = amountPoolService.allocateAmount("TRC20_ADDRESS", 1000000L);
        assertFalse(result2);
    }

    @Test
    void testAllocateAmount_WithOrderTradeNoAndExpireTime() {
        LocalDateTime expireTime = LocalDateTime.of(2025, 7, 21, 12, 0);
        boolean result = amountPoolService.allocateAmount("TRC20_ADDRESS", 1000000L, "ORDER_001", expireTime);
        assertTrue(result);
        Map<String, AmountPoolService.AmountPoolEntry> amountToOrderMap = amountPoolService.getAmountToOrderMap();
        assertEquals("ORDER_001", amountToOrderMap.get("TRC20_ADDRESS_1000000").getOrderTradeNo());
        assertEquals(expireTime, amountToOrderMap.get("TRC20_ADDRESS_1000000").getExpireTime());
    }

    @Test
    void testAllocateAmount_WithOrderTradeNoAndExpireTime_Duplicate() {
        LocalDateTime expireTime = LocalDateTime.of(2025, 7, 21, 12, 0);
        boolean result1 = amountPoolService.allocateAmount("TRC20_ADDRESS", 1000000L, "ORDER_001", expireTime);
        assertTrue(result1);
        boolean result2 = amountPoolService.allocateAmount("TRC20_ADDRESS", 1000000L, "ORDER_002", expireTime.plusDays(1));
        assertFalse(result2);
        Map<String, AmountPoolService.AmountPoolEntry> amountToOrderMap = amountPoolService.getAmountToOrderMap();
        assertEquals("ORDER_001", amountToOrderMap.get("TRC20_ADDRESS_1000000").getOrderTradeNo());
        assertEquals(expireTime, amountToOrderMap.get("TRC20_ADDRESS_1000000").getExpireTime());
    }

    @Test
    void testReleaseAmount_Success() {
        amountPoolService.allocateAmount("TRC20_ADDRESS", 1000000L);
        assertFalse(amountPoolService.isAmountAvailable("TRC20_ADDRESS", 1000000L));
        amountPoolService.releaseAmount("TRC20_ADDRESS", 1000000L);
        assertTrue(amountPoolService.isAmountAvailable("TRC20_ADDRESS", 1000000L));
    }

    @Test
    void testReleaseAmount_NotExists() {
        assertDoesNotThrow(() -> {
            amountPoolService.releaseAmount("TRC20_ADDRESS", 1000000L);
        });
        assertTrue(amountPoolService.isAmountAvailable("TRC20_ADDRESS", 1000000L));
    }

    @Test
    void testIsAmountAvailable_True() {
        assertTrue(amountPoolService.isAmountAvailable("TRC20_ADDRESS", 1000000L));
    }

    @Test
    void testIsAmountAvailable_False() {
        amountPoolService.allocateAmount("TRC20_ADDRESS", 1000000L);
        assertFalse(amountPoolService.isAmountAvailable("TRC20_ADDRESS", 1000000L));
    }

    @Test
    void testGetAllLockedAmounts() {
        amountPoolService.allocateAmount("TRC20_ADDRESS", 1000000L);
        amountPoolService.allocateAmount("SOL_ADDRESS", 2000000L);
        Set<String> lockedAmounts = amountPoolService.getAllLockedAmounts();
        assertEquals(2, lockedAmounts.size());
        assertTrue(lockedAmounts.contains("TRC20_ADDRESS_1000000"));
        assertTrue(lockedAmounts.contains("SOL_ADDRESS_2000000"));
    }

    @Test
    void testGetAmountToOrderMap() {
        amountPoolService.allocateAmount("TRC20_ADDRESS", 1000000L);
        amountPoolService.updateOrderTradeNoAndExpireTime("TRC20_ADDRESS", 1000000L, "ORDER_001", LocalDateTime.of(2025, 7, 21, 12, 0));
        amountPoolService.allocateAmount("SOL_ADDRESS", 2000000L);
        amountPoolService.updateOrderTradeNoAndExpireTime("SOL_ADDRESS", 2000000L, "ORDER_002", LocalDateTime.of(2025, 7, 22, 12, 0));
        Map<String, AmountPoolService.AmountPoolEntry> amountToOrderMap = amountPoolService.getAmountToOrderMap();
        assertEquals(2, amountToOrderMap.size());
        assertEquals("ORDER_001", amountToOrderMap.get("TRC20_ADDRESS_1000000").getOrderTradeNo());
        assertEquals(LocalDateTime.of(2025, 7, 21, 12, 0), amountToOrderMap.get("TRC20_ADDRESS_1000000").getExpireTime());
        assertEquals("ORDER_002", amountToOrderMap.get("SOL_ADDRESS_2000000").getOrderTradeNo());
        assertEquals(LocalDateTime.of(2025, 7, 22, 12, 0), amountToOrderMap.get("SOL_ADDRESS_2000000").getExpireTime());
    }

    @Test
    void testUpdateOrderTradeNoAndExpireTime_Success() {
        amountPoolService.allocateAmount("TRC20_ADDRESS", 1000000L);
        boolean result = amountPoolService.updateOrderTradeNoAndExpireTime("TRC20_ADDRESS", 1000000L, "ORDER_001", LocalDateTime.of(2025, 7, 21, 12, 0));
        assertTrue(result);
        Map<String, AmountPoolService.AmountPoolEntry> amountToOrderMap = amountPoolService.getAmountToOrderMap();
        assertEquals("ORDER_001", amountToOrderMap.get("TRC20_ADDRESS_1000000").getOrderTradeNo());
        assertEquals(LocalDateTime.of(2025, 7, 21, 12, 0), amountToOrderMap.get("TRC20_ADDRESS_1000000").getExpireTime());
    }

    @Test
    void testUpdateOrderTradeNoAndExpireTime_NotExists() {
        boolean result = amountPoolService.updateOrderTradeNoAndExpireTime("TRC20_ADDRESS", 1000000L, "ORDER_001", LocalDateTime.of(2025, 7, 21, 12, 0));
        assertTrue(result); // 现在直接put覆盖
        Map<String, AmountPoolService.AmountPoolEntry> amountToOrderMap = amountPoolService.getAmountToOrderMap();
        assertEquals("ORDER_001", amountToOrderMap.get("TRC20_ADDRESS_1000000").getOrderTradeNo());
        assertEquals(LocalDateTime.of(2025, 7, 21, 12, 0), amountToOrderMap.get("TRC20_ADDRESS_1000000").getExpireTime());
    }
} 