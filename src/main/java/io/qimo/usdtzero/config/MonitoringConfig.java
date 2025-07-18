package io.qimo.usdtzero.config;

import org.springframework.context.annotation.Configuration;

/**
 * 监控配置类
 * 目前使用默认的Spring Boot Actuator配置
 * 可以通过application.yml中的management配置进行自定义
 */
@Configuration
public class MonitoringConfig {
    // 使用Spring Boot默认的监控配置
    // 可以通过 /actuator/health 访问健康检查
    // 可以通过 /actuator/metrics 访问系统指标
} 