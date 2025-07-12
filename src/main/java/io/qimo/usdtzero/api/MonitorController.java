package io.qimo.usdtzero.api;

import io.qimo.usdtzero.service.DatabaseMonitorService;
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
} 