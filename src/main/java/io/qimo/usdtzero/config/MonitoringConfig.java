package io.qimo.usdtzero.config;

import com.zaxxer.hikari.HikariDataSource;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuator.health.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
public class MonitoringConfig {

    @Autowired
    private DataSource dataSource;

    @Bean
    public HealthIndicator databaseHealthIndicator() {
        return new org.springframework.boot.actuate.health.DataSourceHealthIndicator(
                (HikariDataSource) dataSource, "SELECT 1");
    }
} 