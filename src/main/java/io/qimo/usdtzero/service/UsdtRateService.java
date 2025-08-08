package io.qimo.usdtzero.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import io.qimo.usdtzero.model.BizException;
import io.qimo.usdtzero.model.ErrorCode;

@Slf4j
@Service
public class UsdtRateService {
    private final Cache<String, BigDecimal> rateCache = Caffeine.newBuilder()
            .expireAfterWrite(10, TimeUnit.SECONDS) // 缓存10秒
            .build();
    private static final String CACHE_KEY = "USDT_CNY";
    private static final String COINGECKO_API = "https://api.coingecko.com/api/v3/simple/price?ids=tether&vs_currencies=cny";
    private static final String OKX_C2C_API = "https://www.okx.com/v3/c2c/tradingOrders/books?quoteCurrency=cny&baseCurrency=usdt&side=sell&userType=certified&limit=10";
    
    // 线程同步锁，防止并发调用外部API
    private final ReentrantLock rateLock = new ReentrantLock();
    
    @Autowired
    private RestTemplate restTemplate;

    /**
     * 获取USDT汇率（懒加载模式）
     * 1. 先检查缓存
     * 2. 缓存过期时，加锁调用外部API
     * 3. 线程同步，避免并发调用
     */
    public BigDecimal getCachedRate() {
        BigDecimal rate = rateCache.getIfPresent(CACHE_KEY);
        if (rate != null) {
            return rate;
        }
        
        // 缓存过期，需要重新获取
        rateLock.lock();
        try {
            // 双重检查，防止其他线程已经获取了汇率
            rate = rateCache.getIfPresent(CACHE_KEY);
            if (rate != null) {
                return rate;
            }
            
            // 调用外部API获取汇率
            rate = fetchRateWithFallback();
            
            if (rate != null) {
                rateCache.put(CACHE_KEY, rate);
                log.info("已获取并缓存USDT/CNY汇率: {}", rate);
                return rate;
            } else {
                throw new BizException(ErrorCode.RATE_CACHE_MISSING, "USDT/CNY汇率未获取到");
            }
        } finally {
            rateLock.unlock();
        }
    }

    /**
     * 设置汇率到缓存
     */
    public void setRate(BigDecimal rate) {
        if (rate == null) {
            rateCache.invalidate(CACHE_KEY);
        } else {
            rateCache.put(CACHE_KEY, rate);
        }
    }

    /**
     * 获取汇率（带降级策略）
     * 1. 优先使用OKX C2C
     * 2. 失败时降级到CoinGecko
     */
    private BigDecimal fetchRateWithFallback() {
        try {
            BigDecimal okxRate = fetchOkxC2CRateByJdk();
            if (okxRate != null) {
                log.info("成功获取OKX C2C USDT/CNY汇率: {}", okxRate);
                return okxRate;
            }
        } catch (Exception okxEx) {
            log.warn("OKX C2C汇率获取失败: {}，尝试CoinGecko", okxEx.getMessage());
        }
        
        try {
            BigDecimal geckoRate = fetchCoinGeckoRate();
            log.info("成功获取CoinGecko USDT/CNY汇率: {}", geckoRate);
            return geckoRate;
        } catch (Exception geckoEx) {
            log.error("CoinGecko汇率也获取失败: {}", geckoEx.getMessage());
            return null;
        }
    }

    /**
     * 获取CoinGecko汇率
     */
    private BigDecimal fetchCoinGeckoRate() {
        String resp = restTemplate.getForObject(COINGECKO_API, String.class);
        if (resp == null || !resp.contains("\"cny\":")) {
            throw new BizException(ErrorCode.RATE_FETCH_FAILED, "CoinGecko接口返回异常: " + resp);
        }
        // {"tether":{"cny":7.25}}
        String price = resp.split("\"cny\":")[1].split("}")[0].replaceAll("[^0-9.]", "");
        return new BigDecimal(price);
    }

    /**
     * 获取OKX C2C汇率
     */
    private BigDecimal fetchOkxC2CRateByJdk() {
        try {
            String resp = restTemplate.getForObject(OKX_C2C_API, String.class);
            if (resp == null || !resp.contains("\"price\":\"")) {
                return null;
            }
            String[] arr = resp.split("\"price\":\"");
            int count = Math.min(arr.length - 1, 10);
            if (count < 1) return null;
            BigDecimal sum = BigDecimal.ZERO;
            for (int i = 1; i <= count; i++) {
                String priceStr = arr[i].split("\"")[0];
                sum = sum.add(new BigDecimal(priceStr));
            }
            return sum.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
        } catch (Exception e) {
            return null;
        }
    }
} 