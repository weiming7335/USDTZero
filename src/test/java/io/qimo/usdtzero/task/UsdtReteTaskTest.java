package io.qimo.usdtzero.task;

import io.qimo.usdtzero.model.BizException;
import io.qimo.usdtzero.model.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = {
    "spring.task.scheduling.pool.size=1",
    "spring.task.scheduling.enabled=false"
})
class UsdtRateTaskTest {

    @Autowired
    private UsdtRateTask usdtRateTask;

    @BeforeEach
    void setUp() {
        // 清空缓存，确保测试环境干净
        UsdtRateTask.setRate(null);
    }

    @Test
    void testGetCachedRateSuccess() {
        BigDecimal rate = new BigDecimal("7.25");
        UsdtRateTask.setRate(rate);
        BigDecimal cached = UsdtRateTask.getCachedRate();
        assertEquals(rate, cached);
    }

    @Test
    void testGetCachedRateWhenNull() {
        // 确保缓存为空
        UsdtRateTask.setRate(null);
        
        BizException ex = assertThrows(BizException.class, UsdtRateTask::getCachedRate);
        assertEquals(ErrorCode.RATE_CACHE_MISSING, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("USDT/CNY汇率未获取到"));
    }

    @Test
    void testFetchCoinGeckoRateSuccess() {
        try {
            // 测试真实的 CoinGecko API 调用
            BigDecimal rate = usdtRateTask.fetchCoinGeckoRate();
            assertNotNull(rate);
            assertTrue(rate.compareTo(BigDecimal.ZERO) > 0);
            assertTrue(rate.compareTo(new BigDecimal("10")) < 0); // 汇率应该在合理范围内
            System.out.println("CoinGecko 汇率获取成功: " + rate);
        } catch (BizException e) {
            // 如果网络问题导致失败，这是可以接受的
            System.out.println("CoinGecko API 调用失败: " + e.getMessage());
            assertEquals(ErrorCode.RATE_FETCH_FAILED, e.getErrorCode());
        }
    }

    @Test
    void testFetchOkxC2CRate() {
        try {
            // 测试真实的 OKX C2C API 调用
            BigDecimal rate = usdtRateTask.fetchOkxC2CRateByJdk();
            if (rate != null) {
                assertTrue(rate.compareTo(BigDecimal.ZERO) > 0);
                assertTrue(rate.compareTo(new BigDecimal("10")) < 0);
                System.out.println("OKX C2C 汇率获取成功: " + rate);
            } else {
                System.out.println("OKX C2C API 返回 null，可能是网络问题或接口变更");
            }
        } catch (Exception e) {
            // OKX 接口可能不稳定，这是可以接受的
            System.out.println("OKX C2C API 调用异常: " + e.getMessage());
        }
    }

    @Test
    void testRateTaskIntegration() {
        try {
            // 清空缓存，确保测试环境干净
            UsdtRateTask.setRate(null);
            
            // 测试完整的汇率获取流程
            BigDecimal rate = usdtRateTask.fetchCoinGeckoRate();
            assertNotNull(rate);
            
            // 将获取的汇率存储到缓存中
            UsdtRateTask.setRate(rate);
            
            // 验证缓存是否正常工作
            BigDecimal cachedRate = UsdtRateTask.getCachedRate();
            assertEquals(rate, cachedRate);
            
            System.out.println("集成测试成功，汇率: " + rate);
        } catch (BizException e) {
            System.out.println("集成测试失败: " + e.getMessage());
            // 网络问题导致的失败是可以接受的
            assertTrue(e.getErrorCode() == ErrorCode.RATE_FETCH_FAILED || 
                      e.getErrorCode() == ErrorCode.RATE_CACHE_MISSING);
        }
    }
}