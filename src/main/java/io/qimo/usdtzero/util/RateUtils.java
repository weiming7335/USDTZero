package io.qimo.usdtzero.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * USDT汇率工具类
 */
public class RateUtils {
    /**
     * 解析usdtRate字符串，结合最新汇率，计算实际汇率
     * @param rate 汇率策略字符串，如~1.02、~0.97、+0.3、-0.2
     * @param latestRate 最新汇率
     * @return 实际汇率
     */
    public static BigDecimal calcActualRate(String rate, BigDecimal latestRate) {
        if (rate == null || rate.isEmpty()) return latestRate;
        rate = rate.trim();
        try {
            if (rate.startsWith("~")) {
                BigDecimal factor = new BigDecimal(rate.substring(1));
                return latestRate.multiply(factor);
            } else if (rate.startsWith("+")) {
                BigDecimal add = new BigDecimal(rate.substring(1));
                return latestRate.add(add);
            } else if (rate.startsWith("-")) {
                BigDecimal sub = new BigDecimal(rate.substring(1));
                return latestRate.subtract(sub);
            } else {
                return latestRate;
            }
        } catch (Exception e) {
            // 解析失败，回退为最新汇率
            return latestRate;
        }
    }
} 