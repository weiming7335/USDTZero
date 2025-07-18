package io.qimo.usdtzero.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

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
    
    // 请求耗时指标
    private final Timer orderProcessingTimer;
    private final Timer blockchainQueryTimer;
    private final Timer databaseQueryTimer;
    private final Timer apiResponseTimer;
    
    // 定时任务指标
    private final Timer scheduledTaskTimer;
    private final Counter scheduledTaskSuccessCounter;
    private final Counter scheduledTaskErrorCounter;
    private final AtomicLong lastScheduledTaskTime = new AtomicLong(0);
    
    // 资源指标
    private final AtomicLong currentMemoryUsage = new AtomicLong(0);
    private final AtomicLong currentThreadCount = new AtomicLong(0);
    private final AtomicLong queueBacklogCount = new AtomicLong(0);
    
    // 异常指标
    private final Counter exceptionCounter;
    private final AtomicLong lastExceptionTime = new AtomicLong(0);

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
        
        // 初始化请求耗时指标
        this.orderProcessingTimer = Timer.builder("usdtzero.timing.order.processing")
                .description("订单处理耗时")
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
        
        // 启动资源监控定时任务
        startResourceMonitoring();
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
    public void recordOrderCreated(String chain, String amount) {
        orderCreatedCounter.increment();
        log.info("订单创建 - 链: {}, 金额: {}, 总订单数: {}", 
                chain, amount, orderCreatedCounter.count());
    }

    public void recordOrderCreatedFailed(String chain, String amount) {
        orderCreatedFailedCounter.increment();
        log.warn("订单创建失败 - 链: {}, 金额: {}, 总失败数: {}", 
                chain, amount, orderCreatedFailedCounter.count());
    }

    public void recordPaymentReceived(String chain, String amount) {
        paymentReceivedCounter.increment();
        log.info("支付接收 - 链: {}, 金额: {}, 总支付数: {}", 
                chain, amount, paymentReceivedCounter.count());
    }

    public void recordBlockchainSync(String chain) {
        blockchainSyncCounter.increment();
        log.info("区块链同步 - 链: {}, 总同步次数: {}", 
                chain, blockchainSyncCounter.count());
    }

    /**
     * 记录请求耗时
     */
    public Timer.Sample startOrderProcessingTimer() {
        return Timer.start(meterRegistry);
    }

    public void stopOrderProcessingTimer(Timer.Sample sample) {
        sample.stop(orderProcessingTimer);
        log.info("订单处理耗时: {}ms", orderProcessingTimer.mean(TimeUnit.MILLISECONDS));
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
        sample.stop(scheduledTaskTimer);
        scheduledTaskSuccessCounter.increment();
        lastScheduledTaskTime.set(System.currentTimeMillis());
        
        log.info("定时任务完成 - 任务: {}, 耗时: {}ms, 平均耗时: {}ms, 成功次数: {}", 
                taskName, 
                scheduledTaskTimer.mean(TimeUnit.MILLISECONDS), 
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
     * 更新资源指标
     */
    public void updateResourceMetrics() {
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        
        currentMemoryUsage.set(usedMemory);
        currentThreadCount.set(Thread.activeCount());
        
        // 检查内存使用率
        double memoryUsagePercent = (double) usedMemory / runtime.maxMemory() * 100;
        if (memoryUsagePercent > 80) {
            log.warn("内存使用率过高: {}%, 已用: {}MB, 最大: {}MB", 
                    String.format("%.2f", memoryUsagePercent), usedMemory / 1024 / 1024, runtime.maxMemory() / 1024 / 1024);
        }
        
        // 检查线程数
        if (Thread.activeCount() > 100) {
            log.warn("线程数过多: {}, 当前活跃线程: {}", Thread.activeCount(), Thread.activeCount());
        }
        
        log.info("资源监控 - 内存使用: {}%, 活跃线程: {}, 队列积压: {}",
                String.format("%.2f", memoryUsagePercent),  Thread.activeCount(), queueBacklogCount.get());
    }

    /**
     * 设置队列积压数
     */
    public void setQueueBacklogCount(long count) {
        queueBacklogCount.set(count);
        if (count > 100) {
            log.warn("队列积压严重: {}", count);
        }
    }

    /**
     * 启动资源监控定时任务
     */
    private void startResourceMonitoring() {
        Thread monitorThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    updateResourceMetrics();
                    Thread.sleep(30000); // 每30秒监控一次
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.error("资源监控异常", e);
                }
            }
        }, "resource-monitor");
        monitorThread.setDaemon(true);
        monitorThread.start();
    }

    /**
     * 获取监控摘要
     */
    public String getMetricsSummary() {
        double successRate = totalRequestsCounter.count() > 0 ? 
                (successRequestsCounter.count() / totalRequestsCounter.count()) * 100 : 0;
        
        double scheduledTaskSuccessRate = (scheduledTaskSuccessCounter.count() + scheduledTaskErrorCounter.count()) > 0 ?
                (scheduledTaskSuccessCounter.count() / (scheduledTaskSuccessCounter.count() + scheduledTaskErrorCounter.count())) * 100 : 0;
        
        return String.format(
            "请求成功率: %.2f%%, 订单: %.0f(失败: %.0f), 支付: %.0f, 同步: %.0f, 异常: %.0f, 内存使用: %dMB, 定时任务成功率: %.2f%%",
            successRate,
            orderCreatedCounter.count(),
            orderCreatedFailedCounter.count(),
            paymentReceivedCounter.count(),
            blockchainSyncCounter.count(),
            exceptionCounter.count(),
            currentMemoryUsage.get() / 1024 / 1024,
            scheduledTaskSuccessRate
        );
    }
} 