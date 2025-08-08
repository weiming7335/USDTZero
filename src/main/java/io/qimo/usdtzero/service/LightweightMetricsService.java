package io.qimo.usdtzero.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.qimo.usdtzero.constant.ChainType;
import io.qimo.usdtzero.util.AmountConvertUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import io.qimo.usdtzero.model.DailyStatistics;
import java.math.BigDecimal;
import java.time.LocalDate;

@Slf4j
@Service
public class LightweightMetricsService {

    private final MeterRegistry meterRegistry;
    
    // 请求计数指标
    private final Counter totalRequestsCounter;
    private final Counter successRequestsCounter;
    private final Counter errorRequestsCounter;
    private final Counter orderCreatedCounter;
    private final Counter orderCreatedFailedCounter;
    private final Counter paymentReceivedCounter;
    private final Counter blockchainSyncCounter;
    private final Counter orderCancelledCounter;
    
    // 请求耗时指标
    private final Timer blockchainQueryTimer;
    private final Timer databaseQueryTimer;
    private final Timer apiResponseTimer;
    
    // 定时任务指标
    private final Timer scheduledTaskTimer;
    private final Counter scheduledTaskSuccessCounter;
    private final Counter scheduledTaskErrorCounter;
    private final AtomicLong lastScheduledTaskTime = new AtomicLong(0);

    // 异常指标
    private final Counter exceptionCounter;
    private final AtomicLong lastExceptionTime = new AtomicLong(0);

    // 扫块成功/失败计数（区分链）
    private final Map<String, AtomicLong> blockScanSuccessMap = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> blockScanFailMap = new ConcurrentHashMap<>();

    private final Map<String, AtomicLong> amountMap = new ConcurrentHashMap<>();
    @Autowired
    public LightweightMetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        
        // 初始化请求计数指标
        this.totalRequestsCounter = Counter.builder("usdtzero.requests.total")
                .description("总请求数")
                .register(meterRegistry);
                
        this.successRequestsCounter = Counter.builder("usdtzero.requests.success")
                .description("成功请求数")
                .register(meterRegistry);
                
        this.errorRequestsCounter = Counter.builder("usdtzero.requests.error")
                .description("错误请求数")
                .register(meterRegistry);
                
        this.orderCreatedCounter = Counter.builder("usdtzero.business.orders.created")
                .description("订单创建数")
                .register(meterRegistry);
                
        this.orderCreatedFailedCounter = Counter.builder("usdtzero.business.orders.created.failed")
                .description("订单创建失败数")
                .register(meterRegistry);
                
        this.paymentReceivedCounter = Counter.builder("usdtzero.business.payments.received")
                .description("支付接收数")
                .register(meterRegistry);
                
        this.blockchainSyncCounter = Counter.builder("usdtzero.business.blockchain.sync")
                .description("区块链同步数")
                .register(meterRegistry);
        
        this.orderCancelledCounter = Counter.builder("usdtzero.business.orders.cancelled")
                .description("订单取消数")
                .register(meterRegistry);

        this.blockchainQueryTimer = Timer.builder("usdtzero.timing.blockchain.query")
                .description("区块链查询耗时")
                .register(meterRegistry);
                
        this.databaseQueryTimer = Timer.builder("usdtzero.timing.database.query")
                .description("数据库查询耗时")
                .register(meterRegistry);
                
        this.apiResponseTimer = Timer.builder("usdtzero.timing.api.response")
                .description("API响应耗时")
                .register(meterRegistry);
        
        // 初始化定时任务指标
        this.scheduledTaskTimer = Timer.builder("usdtzero.timing.scheduled.task")
                .description("定时任务耗时")
                .register(meterRegistry);
                
        this.scheduledTaskSuccessCounter = Counter.builder("usdtzero.scheduled.task.success")
                .description("定时任务成功数")
                .register(meterRegistry);
                
        this.scheduledTaskErrorCounter = Counter.builder("usdtzero.scheduled.task.error")
                .description("定时任务失败数")
                .register(meterRegistry);

        // 初始化异常指标
        this.exceptionCounter = Counter.builder("usdtzero.exceptions.total")
                .description("异常总数")
                .register(meterRegistry);

    }

    /**
     * 记录请求计数
     */
    public void recordRequest(String endpoint, boolean success) {
        totalRequestsCounter.increment();
        
        if (success) {
            successRequestsCounter.increment();
            log.info("请求成功 - 端点: {}, 总请求: {}, 成功请求: {}", 
                    endpoint, totalRequestsCounter.count(), successRequestsCounter.count());
        } else {
            errorRequestsCounter.increment();
            log.warn("请求失败 - 端点: {}, 总请求: {}, 错误请求: {}", 
                    endpoint, totalRequestsCounter.count(), errorRequestsCounter.count());
        }
    }

    /**
     * 记录业务指标
     */
    public void recordOrderCreated(String tradeNo) {
        orderCreatedCounter.increment();
        log.info("订单创建 - tradeNo: {}, 总订单数: {}", tradeNo, orderCreatedCounter.count());
    }

    public void recordOrderCreatedFailed(String tradeNo) {
        orderCreatedFailedCounter.increment();
        log.warn("订单创建失败 - tradeNo: {}, 总失败数: {}", tradeNo, orderCreatedFailedCounter.count());
    }

    public void recordPaymentReceived(String chain, Long amount, Long actualAmount, String tradeNo) {
        paymentReceivedCounter.increment();
        amountMap.computeIfAbsent("CNY", k -> new AtomicLong()).addAndGet(amount);
        amountMap.computeIfAbsent(chain, k -> new AtomicLong()).addAndGet(actualAmount);

        log.info("支付接收 - 链: {}, 金额: {}, 实际交易金额: {},交易编号:{}, 总支付数: {}",
                chain, amount,actualAmount,tradeNo, paymentReceivedCounter.count());
    }

    public void recordBlockchainSync(String chain) {
        blockchainSyncCounter.increment();
        log.info("区块链同步 - 链: {}, 总同步次数: {}", 
                chain, blockchainSyncCounter.count());
    }

    /**
     * 记录订单取消
     */
    public void recordOrderCancelled(String tradeNo) {
        orderCancelledCounter.increment();
        log.info("订单取消 - tradeNo: {}, 总取消数: {}", tradeNo, orderCancelledCounter.count());
    }



    public void recordBlockchainQueryTime(long timeMs) {
        blockchainQueryTimer.record(timeMs, TimeUnit.MILLISECONDS);
        log.info("区块链查询耗时: {}ms, 平均耗时: {}ms",
                timeMs, blockchainQueryTimer.mean(TimeUnit.MILLISECONDS));
    }

    public void recordDatabaseQueryTime(long timeMs) {
        databaseQueryTimer.record(timeMs, TimeUnit.MILLISECONDS);
        log.info("数据库查询耗时: {}ms, 平均耗时: {}ms",
                timeMs, databaseQueryTimer.mean(TimeUnit.MILLISECONDS));
    }

    public void recordApiResponseTime(long timeMs) {
        apiResponseTimer.record(timeMs, TimeUnit.MILLISECONDS);
        if (timeMs > 1000) { // 超过1秒记录警告
            log.warn("API响应缓慢 - 耗时: {}ms, 平均耗时: {}ms", 
                    timeMs, apiResponseTimer.mean(TimeUnit.MILLISECONDS));
        }
    }

    /**
     * 记录异常
     */
    public void recordException(String exceptionType, String message) {
        exceptionCounter.increment();
        lastExceptionTime.set(System.currentTimeMillis());
        log.error("异常记录 - 类型: {}, 消息: {}, 总异常数: {}", 
                exceptionType, message, exceptionCounter.count());
    }

    /**
     * 开始定时任务计时
     */
    public Timer.Sample startScheduledTaskTimer() {
        return Timer.start(meterRegistry);
    }

    /**
     * 停止定时任务计时并记录成功
     */
    public void stopScheduledTaskTimer(Timer.Sample sample, String taskName) {
        long actualTimeNanos = sample.stop(scheduledTaskTimer);
        long actualTimeMs = TimeUnit.NANOSECONDS.toMillis(actualTimeNanos);
        scheduledTaskSuccessCounter.increment();
        lastScheduledTaskTime.set(System.currentTimeMillis());
        
        log.info("定时任务完成 - 任务: {}, 实际耗时: {}ms, 平均耗时: {}ms, 成功次数: {}", 
                taskName, 
                actualTimeMs,
                scheduledTaskTimer.mean(TimeUnit.MILLISECONDS),
                scheduledTaskSuccessCounter.count());
    }

    /**
     * 记录定时任务异常
     */
    public void recordScheduledTaskError(String taskName, String errorMessage) {
        scheduledTaskErrorCounter.increment();
        exceptionCounter.increment();
        lastExceptionTime.set(System.currentTimeMillis());
        
        log.error("定时任务异常 - 任务: {}, 错误: {}, 失败次数: {}", 
                taskName, errorMessage, scheduledTaskErrorCounter.count());
    }

    /**
     * 记录定时任务耗时（直接记录）
     */
    public void recordScheduledTaskTime(long timeMs, String taskName, boolean success) {
        scheduledTaskTimer.record(timeMs, TimeUnit.MILLISECONDS);
        
        if (success) {
            scheduledTaskSuccessCounter.increment();
            log.info("定时任务完成 - 任务: {}, 耗时: {}ms, 平均耗时: {}ms", 
                    taskName, timeMs, scheduledTaskTimer.mean(TimeUnit.MILLISECONDS));
        } else {
            scheduledTaskErrorCounter.increment();
            log.warn("定时任务失败 - 任务: {}, 耗时: {}ms", taskName, timeMs);
        }
    }

    /**
     * 记录扫块成功（区分链）
     */
    public void incBlockScanSuccess(String chainType) {
        blockScanSuccessMap.computeIfAbsent(chainType, k -> new AtomicLong()).incrementAndGet();
    }
    /**
     * 记录扫块失败（区分链）
     */
    public void incBlockScanFail(String chainType) {
        blockScanFailMap.computeIfAbsent(chainType, k -> new AtomicLong()).incrementAndGet();
    }

    /**
     * 生成当日统计对象
     */
    public DailyStatistics generateDailyStatistics() {
        return DailyStatistics.builder()
                .date(LocalDate.now())
                .successOrderCount((long) paymentReceivedCounter.count())
                .totalOrderCount((long) (orderCreatedCounter.count() + orderCreatedFailedCounter.count()))
                .totalCnyAmount(AmountConvertUtils.calculateCnyFromMinUnit(getPaymentAmount("CNY")))
                .totalTrc20Amount(AmountConvertUtils.calculateUsdtFromMinUnit(getPaymentAmount(ChainType.TRC20), ChainType.getUsdtUnit(ChainType.TRC20), 2))
                .totalSplAmount(AmountConvertUtils.calculateUsdtFromMinUnit(getPaymentAmount(ChainType.SPL), ChainType.getUsdtUnit(ChainType.SPL), 2))
                .totalBep20Amount(AmountConvertUtils.calculateUsdtFromMinUnit(getPaymentAmount(ChainType.BEP20), ChainType.getUsdtUnit(ChainType.BEP20), 2))
                .trc20FailCount(blockScanFailMap.getOrDefault(ChainType.TRC20, new AtomicLong(0)).get())
                .trc20SuccessCount(blockScanSuccessMap.getOrDefault(ChainType.TRC20, new AtomicLong(0)).get())
                .splFailCount(blockScanFailMap.getOrDefault(ChainType.SPL, new AtomicLong(0)).get())
                .splSuccessCount(blockScanSuccessMap.getOrDefault(ChainType.SPL, new AtomicLong(0)).get())
                .bep20FailCount(blockScanFailMap.getOrDefault(ChainType.BEP20, new AtomicLong(0)).get())
                .bep20SuccessCount(blockScanSuccessMap.getOrDefault(ChainType.BEP20, new AtomicLong(0)).get())
                .currentUsdtRate(BigDecimal.valueOf(7.2)) // 这里可以从汇率服务获取
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * 获取指定链的当日收款金额
     */
    private long getPaymentAmount(String type) {
        return amountMap.getOrDefault(type, new AtomicLong(0)).get();
    }

    @Scheduled(cron = "0 0 0 * * ?") // 每天零点执行
    public void resetDailyStatistics() {
        // 清零收款金额统计
        amountMap.clear();
        
        // 清零扫块成功/失败计数
        blockScanSuccessMap.clear();
        blockScanFailMap.clear();
        
        // 清零Counter相关指标（通过重置MeterRegistry实现）
        meterRegistry.clear();
        
        log.info("每日统计已清零");
    }

} 