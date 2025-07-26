package io.qimo.usdtzero.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 当日统计对象
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DailyStatistics {
    
    /**
     * 统计日期
     */
    private LocalDate date;
    
    /**
     * 今日成功订单数
     */
    private Long successOrderCount;
    
    /**
     * 今日总订单数
     */
    private Long totalOrderCount;
    
    /**
     * 今日收款汇总 - CNY
     */
    private BigDecimal totalCnyAmount;
    
    /**
     * 今日收款汇总 - TRC20 USDT
     */
    private BigDecimal totalTrc20Amount;
    
    /**
     * 今日收款汇总 - SPL USDT
     */
    private BigDecimal totalSplAmount;
    
    /**
     * 今日收款汇总 - BEP20 USDT
     */
    private BigDecimal totalBep20Amount;
    
    /**
     * 扫块成功数据 - TRC20失败数
     */
    private Long trc20FailCount;
    
    /**
     * 扫块成功数据 - TRC20成功数
     */
    private Long trc20SuccessCount;
    
    /**
     * 扫块成功数据 - SPL失败数
     */
    private Long splFailCount;
    
    /**
     * 扫块成功数据 - SPL成功数
     */
    private Long splSuccessCount;
    
    /**
     * 扫块成功数据 - BEP20失败数
     */
    private Long bep20FailCount;
    
    /**
     * 扫块成功数据 - BEP20成功数
     */
    private Long bep20SuccessCount;

    /**
     * 当前基准汇率 (USDT/CNY)
     */
    private BigDecimal currentUsdtRate;

    /**
     * 统计时间戳
     */
    private Long timestamp;

    /**
     * 获取TRC20成功率
     */
    public BigDecimal getTrc20SuccessRate() {
        if (trc20SuccessCount == null || trc20FailCount == null) {
            return BigDecimal.ZERO;
        }
        long total = trc20SuccessCount + trc20FailCount;
        if (total == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(trc20SuccessCount)
                .divide(BigDecimal.valueOf(total), 4, BigDecimal.ROUND_HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }
    
    /**
     * 获取SPL成功率
     */
    public BigDecimal getSplSuccessRate() {
        if (splSuccessCount == null || splFailCount == null) {
            return BigDecimal.ZERO;
        }
        long total = splSuccessCount + splFailCount;
        if (total == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(splSuccessCount)
                .divide(BigDecimal.valueOf(total), 4, BigDecimal.ROUND_HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }
    
    /**
     * 获取BEP20成功率
     */
    public BigDecimal getBep20SuccessRate() {
        if (bep20SuccessCount == null || bep20FailCount == null) {
            return BigDecimal.ZERO;
        }
        long total = bep20SuccessCount + bep20FailCount;
        if (total == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(bep20SuccessCount)
                .divide(BigDecimal.valueOf(total), 4, BigDecimal.ROUND_HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }
    
    /**
     * 获取订单成功率
     */
    public BigDecimal getOrderSuccessRate() {
        if (successOrderCount == null || totalOrderCount == null || totalOrderCount == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(successOrderCount)
                .divide(BigDecimal.valueOf(totalOrderCount), 4, BigDecimal.ROUND_HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }
    
    /**
     * 获取今日总USDT收款
     */
    public BigDecimal getTotalUsdtAmount() {
        BigDecimal total = BigDecimal.ZERO;
        if (totalTrc20Amount != null) total = total.add(totalTrc20Amount);
        if (totalSplAmount != null) total = total.add(totalSplAmount);
        if (totalBep20Amount != null) total = total.add(totalBep20Amount);
        return total;
    }
} 