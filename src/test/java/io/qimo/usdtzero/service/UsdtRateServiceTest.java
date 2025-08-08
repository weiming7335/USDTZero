package io.qimo.usdtzero.service;

import io.qimo.usdtzero.model.BizException;
import io.qimo.usdtzero.model.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = {
    "spring.task.scheduling.pool.size=1",
    "spring.task.scheduling.enabled=false"
})
class UsdtRateServiceTest {

    @Autowired
    private UsdtRateService usdtRateService;

    @BeforeEach
    void setUp() {
        // 清空缓存，确保测试环境干净
        usdtRateService.setRate(null);
    }

    @Test
    void testGetCachedRateSuccess() {
        BigDecimal rate = new BigDecimal("7.25");
        usdtRateService.setRate(rate);
        BigDecimal cached = usdtRateService.getCachedRate();
        assertEquals(rate, cached);
    }

    @Test
    void testGetCachedRateWhenNull() {
        // 确保缓存为空
        usdtRateService.setRate(null);
        
        // 由于懒加载，系统会自动尝试获取汇率
        // 如果网络正常，会获取到汇率；如果网络异常，会抛出异常
        try {
            BigDecimal rate = usdtRateService.getCachedRate();
            // 如果获取成功，验证汇率合理性
            assertNotNull(rate);
            assertTrue(rate.compareTo(BigDecimal.ZERO) > 0);
            assertTrue(rate.compareTo(new BigDecimal("10")) < 0);
        } catch (BizException e) {
            // 网络问题导致的失败是可以接受的
            assertEquals(ErrorCode.RATE_CACHE_MISSING, e.getErrorCode());
        }
    }

    @Test
    void testConcurrentRateFetch() throws InterruptedException {
        // 清空缓存
        usdtRateService.setRate(null);
        
        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger exceptionCount = new AtomicInteger(0);
        
        // 启动多个线程同时获取汇率
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    BigDecimal rate = usdtRateService.getCachedRate();
                    if (rate != null) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    exceptionCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await();
        executor.shutdown();
        
        // 验证结果：要么全部成功，要么全部失败（网络问题）
        assertTrue(successCount.get() == threadCount || exceptionCount.get() == threadCount,
                "并发测试结果不一致: 成功=" + successCount.get() + ", 失败=" + exceptionCount.get());
    }

    @Test
    void testLazyLoadingWithCacheExpiration() throws InterruptedException {
        // 设置一个短期汇率
        BigDecimal initialRate = new BigDecimal("7.25");
        usdtRateService.setRate(initialRate);
        
        // 验证缓存工作正常
        BigDecimal cached = usdtRateService.getCachedRate();
        assertEquals(initialRate, cached);
        
        // 等待缓存过期（10秒）
        Thread.sleep(11000);
        
        // 清空缓存，模拟过期
        usdtRateService.setRate(null);
        
        // 尝试获取汇率，应该触发懒加载
        try {
            BigDecimal newRate = usdtRateService.getCachedRate();
            assertNotNull(newRate);
            assertTrue(newRate.compareTo(BigDecimal.ZERO) > 0);
        } catch (BizException e) {
            // 网络问题导致的失败是可以接受的
            assertEquals(ErrorCode.RATE_CACHE_MISSING, e.getErrorCode());
        }
    }

    @Test
    void testRateServiceIntegration() {
        try {
            // 清空缓存，确保测试环境干净
            usdtRateService.setRate(null);
            
            // 测试完整的懒加载流程
            BigDecimal rate = usdtRateService.getCachedRate();
            assertNotNull(rate);
            assertTrue(rate.compareTo(BigDecimal.ZERO) > 0);
            assertTrue(rate.compareTo(new BigDecimal("10")) < 0); // 汇率应该在合理范围内
            
            // 验证缓存是否正常工作
            BigDecimal cachedRate = usdtRateService.getCachedRate();
            assertEquals(rate, cachedRate);
            
            System.out.println("懒加载集成测试成功，汇率: " + rate);
        } catch (BizException e) {
            System.out.println("懒加载集成测试失败: " + e.getMessage());
            // 网络问题导致的失败是可以接受的
            assertTrue(e.getErrorCode() == ErrorCode.RATE_FETCH_FAILED || 
                      e.getErrorCode() == ErrorCode.RATE_CACHE_MISSING);
        }
    }

    @Test
    void testCacheInvalidation() {
        // 设置汇率
        BigDecimal rate1 = new BigDecimal("7.25");
        usdtRateService.setRate(rate1);
        assertEquals(rate1, usdtRateService.getCachedRate());
        
        // 设置为null，应该清除缓存
        usdtRateService.setRate(null);
        
        // 由于懒加载，系统会自动尝试获取汇率
        try {
            BigDecimal rate = usdtRateService.getCachedRate();
            // 如果获取成功，验证汇率合理性
            assertNotNull(rate);
            assertTrue(rate.compareTo(BigDecimal.ZERO) > 0);
            assertTrue(rate.compareTo(new BigDecimal("10")) < 0);
        } catch (BizException e) {
            // 网络问题导致的失败是可以接受的
            assertEquals(ErrorCode.RATE_CACHE_MISSING, e.getErrorCode());
        }
    }
}