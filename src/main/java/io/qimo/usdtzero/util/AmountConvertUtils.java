package io.qimo.usdtzero.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class AmountConvertUtils {
    /**
     * 从最小单位计算USDT金额，确保精度一致性
     */
    public static BigDecimal calculateUsdtFromMinUnit(long actualAmountMinUnit, long usdtUnit, int usdtScale) {
        // 使用与分配时相同的计算逻辑
        return BigDecimal.valueOf(actualAmountMinUnit)
                .divide(BigDecimal.valueOf(usdtUnit), usdtScale, RoundingMode.HALF_UP);
    }

    /**
     * 从CNY分计算CNY元，确保精度一致性
     */
    public static BigDecimal calculateCnyFromMinUnit(Long amountInCents) {
        // 使用与创建时相同的计算逻辑（HALF_UP）
        return BigDecimal.valueOf(amountInCents)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    /**
     * 从CNY元计算CNY分，确保精度一致性
     */
    public static Long calculateCnyToMinUnit(BigDecimal amountInYuan) {
        // 使用与转换时相同的计算逻辑（HALF_UP）
        return amountInYuan.multiply(BigDecimal.valueOf(100)).longValue();
    }
} 