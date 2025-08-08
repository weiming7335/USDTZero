package io.qimo.usdtzero.service;

import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;

@Slf4j
@Service
public class DatabaseMonitorService {

    @Autowired
    private DataSource dataSource;

    /**
     * 每30秒监控一次数据库连接池状态
     */
    @Scheduled(fixedRate = 30000)
    public void monitorDatabasePool() {
        if (dataSource instanceof HikariDataSource) {
            HikariDataSource hikariDataSource = (HikariDataSource) dataSource;
            
            // 检查连接池是否已经初始化
            if (hikariDataSource.getHikariPoolMXBean() == null) {
                log.info("=== 数据库连接池监控 ===");
                log.info("连接池正在初始化中...");
                log.info("最大连接数: {}", hikariDataSource.getMaximumPoolSize());
                log.info("=========================");
                return;
            }
            
            log.info("=== 数据库连接池监控 ===");
            log.info("活跃连接数: {}", hikariDataSource.getHikariPoolMXBean().getActiveConnections());
            log.info("空闲连接数: {}", hikariDataSource.getHikariPoolMXBean().getIdleConnections());
            log.info("总连接数: {}", hikariDataSource.getHikariPoolMXBean().getTotalConnections());
            log.info("等待连接数: {}", hikariDataSource.getHikariPoolMXBean().getThreadsAwaitingConnection());
            log.info("最大连接数: {}", hikariDataSource.getMaximumPoolSize());
            log.info("=========================");
        }
    }

    /**
     * 获取连接池状态信息
     */
    public String getPoolStatus() {
        if (dataSource instanceof HikariDataSource) {
            HikariDataSource hikariDataSource = (HikariDataSource) dataSource;
            
            // 检查连接池是否已经初始化
            if (hikariDataSource.getHikariPoolMXBean() == null) {
                return "连接池正在初始化中...";
            }
            
            return String.format(
                "活跃:%d, 空闲:%d, 总数:%d, 等待:%d",
                hikariDataSource.getHikariPoolMXBean().getActiveConnections(),
                hikariDataSource.getHikariPoolMXBean().getIdleConnections(),
                hikariDataSource.getHikariPoolMXBean().getTotalConnections(),
                hikariDataSource.getHikariPoolMXBean().getThreadsAwaitingConnection()
            );
        }
        return "数据源类型不支持";
    }
} 