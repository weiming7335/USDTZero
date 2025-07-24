package io.qimo.usdtzero.util;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class UsdtRateUtilsTest {
    @Test
    void testParseFixedRate() {
        assertEquals(new BigDecimal("7.4"), UsdtRateUtils.calcActualRate("7.4", new BigDecimal("6.4")));
    }

    @Test
    void testParseRelativeRate() {
        // ~1.02 表示最新汇率上浮2%
        assertEquals(new BigDecimal("6.528"), UsdtRateUtils.calcActualRate("~1.02", new BigDecimal("6.4")));
        // ~0.97 表示最新汇率下浮3%
        assertEquals(new BigDecimal("6.208"), UsdtRateUtils.calcActualRate("~0.97", new BigDecimal("6.4")));
    }

    @Test
    void testParseAddSubRate() {
        // +0.3 表示最新汇率加0.3
        assertEquals(new BigDecimal("6.7"), UsdtRateUtils.calcActualRate("+0.3", new BigDecimal("6.4")));
        // -0.2 表示最新汇率减0.2
        assertEquals(new BigDecimal("6.2"), UsdtRateUtils.calcActualRate("-0.2", new BigDecimal("6.4")));
    }

    @Test
    void testParseEmptyOrInvalid() {
        // 空字符串返回默认
        assertEquals(new BigDecimal("6.4"), UsdtRateUtils.calcActualRate("", new BigDecimal("6.4")));
        // 非法字符串返回默认
        assertEquals(new BigDecimal("6.4"), UsdtRateUtils.calcActualRate("abc", new BigDecimal("6.4")));
    }
} 