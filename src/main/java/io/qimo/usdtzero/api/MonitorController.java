package io.qimo.usdtzero.api;

import io.qimo.usdtzero.service.DatabaseMonitorService;
import io.qimo.usdtzero.service.LightweightMetricsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/monitor")
public class MonitorController {

    @Autowired
    private DatabaseMonitorService databaseMonitorService;
    
    @Autowired
    private LightweightMetricsService metricsService;

    /**
     * 获取数据库连接池状态
     */
    @GetMapping("/database/pool")
    public Map<String, Object> getDatabasePoolStatus() {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("message", databaseMonitorService.getPoolStatus());
        result.put("timestamp", System.currentTimeMillis());
        return result;
    }

    /**
     * 健康检查
     */
    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "UP");
        result.put("timestamp", System.currentTimeMillis());
        result.put("service", "USDTZero");
        return result;
    }
    
    /**
     * 获取轻量级监控摘要
     */
    @GetMapping("/metrics/summary")
    public Map<String, Object> getMetricsSummary() {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("timestamp", System.currentTimeMillis());
        result.put("summary", metricsService.getMetricsSummary());
        return result;
    }
    
    /**
     * 获取系统概览
     */
    @GetMapping("/overview")
    public Map<String, Object> getSystemOverview() {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("timestamp", System.currentTimeMillis());
        
        // 系统信息
        Map<String, Object> system = new HashMap<>();
        Runtime runtime = Runtime.getRuntime();
        system.put("javaVersion", System.getProperty("java.version"));
        system.put("osName", System.getProperty("os.name"));
        system.put("availableProcessors", runtime.availableProcessors());
        system.put("totalMemory", runtime.totalMemory());
        system.put("freeMemory", runtime.freeMemory());
        system.put("maxMemory", runtime.maxMemory());
        system.put("usedMemory", runtime.totalMemory() - runtime.freeMemory());
        system.put("activeThreads", Thread.activeCount());
        result.put("system", system);
        
        // 数据库状态
        result.put("database", databaseMonitorService.getPoolStatus());
        
        // 监控摘要
        result.put("metrics", metricsService.getMetricsSummary());
        
        return result;
    }
    
    /**
     * 获取定时任务状态
     */
    @GetMapping("/scheduled-tasks")
    public Map<String, Object> getScheduledTasksStatus() {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("timestamp", System.currentTimeMillis());
        
        // 定时任务信息
        Map<String, Object> tasks = new HashMap<>();
        tasks.put("activeThreads", Thread.activeCount());
        tasks.put("scheduledTasks", "5个定时任务正在运行");
        tasks.put("taskFrequency", "1秒、5秒、10秒、30秒、60秒");
        result.put("scheduledTasks", tasks);
        
        return result;
    }
} 