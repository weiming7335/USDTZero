package io.qimo.usdtzero.util;

import java.math.BigDecimal;

/**
 * USDT汇率工具类
 */
public class UsdtRateUtils {
    /**
     * 解析usdtRate字符串，结合最新汇率，计算实际汇率
     * @param usdtRate 汇率策略字符串，如7.4、~1.02、~0.97、+0.3、-0.2
     * @param latestRate 最新汇率
     * @return 实际汇率
     */
    public static BigDecimal calcActualRate(String usdtRate, BigDecimal latestRate) {
        if (usdtRate == null || usdtRate.isEmpty()) return latestRate;
        usdtRate = usdtRate.trim();
        try {
            if (usdtRate.startsWith("~")) {
                BigDecimal factor = new BigDecimal(usdtRate.substring(1));
                return latestRate.multiply(factor);
            } else if (usdtRate.startsWith("+")) {
                BigDecimal add = new BigDecimal(usdtRate.substring(1));
                return latestRate.add(add);
            } else if (usdtRate.startsWith("-")) {
                BigDecimal sub = new BigDecimal(usdtRate.substring(1));
                return latestRate.subtract(sub);
            } else {
                return new BigDecimal(usdtRate);
            }
        } catch (Exception e) {
            // 解析失败，回退为最新汇率
            return latestRate;
        }
    }


} 