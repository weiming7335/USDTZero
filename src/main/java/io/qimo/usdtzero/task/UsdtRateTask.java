package io.qimo.usdtzero.task;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URI;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.TimeUnit;
import io.qimo.usdtzero.model.BizException;
import io.qimo.usdtzero.model.ErrorCode;

@Slf4j
@Component
public class UsdtRateTask {
    private static final Cache<String, BigDecimal> RATE_CACHE = Caffeine.newBuilder()
            .expireAfterWrite(15, TimeUnit.SECONDS)
            .build();
    private static final String CACHE_KEY = "USDT_CNY";
    private static final String COINGECKO_API = "https://api.coingecko.com/api/v3/simple/price?ids=tether&vs_currencies=cny";
    private static final String OKX_C2C_API = "https://www.okx.com/v3/c2c/tradingOrders/books?quoteCurrency=cny&baseCurrency=usdt&side=sell&userType=certified&limit=10";
    @Autowired
    private RestTemplate restTemplate;

    public static BigDecimal getCachedRate() {
        BigDecimal rate = RATE_CACHE.getIfPresent(CACHE_KEY);
        if (rate == null) throw new BizException(ErrorCode.RATE_CACHE_MISSING, "USDT/CNY汇率未获取到");
        return rate;
    }

    public static void setRate(BigDecimal rate) {
        if (rate == null) {
            // 如果rate为null，从缓存中移除该键
            RATE_CACHE.invalidate(CACHE_KEY);
        } else {
            // 如果rate不为null，正常存储
            RATE_CACHE.put(CACHE_KEY, rate);
        }
    }

    @Scheduled(fixedRate = 10000)
    public void fetchRate() {
        try {
            BigDecimal okxRate = fetchOkxC2CRateByJdk();
            setRate(okxRate);
            log.info("已更新OKX C2C USDT/CNY汇率(JDK): {}", okxRate);
        } catch (Exception okxEx) {
            log.warn("OKX C2C(JDK)汇率获取失败: {}，尝试CoinGecko", okxEx.getMessage());
            try {
                BigDecimal geckoRate = fetchCoinGeckoRate();
                setRate(geckoRate);
                log.info("已更新CoinGecko USDT/CNY汇率: {}", geckoRate);
            } catch (Exception geckoEx) {
                log.error("CoinGecko汇率也获取失败: {}，本周期无可用汇率", geckoEx.getMessage());
            }
        }
    }

    public BigDecimal fetchCoinGeckoRate() {
        String resp = restTemplate.getForObject(COINGECKO_API, String.class);
        if (resp == null || !resp.contains("\"cny\":")) {
            throw new BizException(ErrorCode.RATE_FETCH_FAILED, "CoinGecko接口返回异常: " + resp);
        }
        // {"tether":{"cny":7.25}}
        String price = resp.split("\"cny\":")[1].split("}")[0].replaceAll("[^0-9.]", "");
        return new BigDecimal(price);
    }

    public BigDecimal fetchOkxC2CRateByJdk() {
        try {
            String resp =restTemplate.getForObject(OKX_C2C_API, String.class);
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