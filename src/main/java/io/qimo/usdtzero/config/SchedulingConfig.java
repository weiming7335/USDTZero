package io.qimo.usdtzero.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
@EnableScheduling
public class SchedulingConfig implements SchedulingConfigurer {

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.setScheduler(taskExecutor());
    }

    @Bean
    public Executor taskExecutor() {
        // 创建自定义线程工厂
        ThreadFactory threadFactory = new ThreadFactory() {
            private final AtomicInteger threadNumber = new AtomicInteger(1);
            
            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r, "scheduled-task-" + threadNumber.getAndIncrement());
                thread.setDaemon(false); // 非守护线程
                thread.setPriority(Thread.NORM_PRIORITY);
                return thread;
            }
        };
        
        // 创建线程池，最小2个线程，多核服务器使用核心数
        int corePoolSize = Math.max(2, Runtime.getRuntime().availableProcessors());
        return Executors.newScheduledThreadPool(corePoolSize, threadFactory);
    }
} 