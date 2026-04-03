package com.fm.be.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Async thread pool configuration for background jobs.
 *
 * <p>Defines named executors used with {@code @Async("executorName")}:
 * <ul>
 *   <li>{@code payrollExecutor} — payroll processing (core=2, max=4, queue=10)</li>
 *   <li>{@code mediaExecutor} — thumbnail generation (core=1, max=2, queue=20)</li>
 * </ul>
 */
@Configuration
public class AsyncConfig {

    @Value("${app.async.core-pool-size:2}")
    private int corePoolSize;

    @Value("${app.async.max-pool-size:4}")
    private int maxPoolSize;

    @Value("${app.async.queue-capacity:50}")
    private int queueCapacity;

    /**
     * Thread pool for async payroll processing jobs.
     *
     * @return configured executor for payroll background work
     */
    @Bean(name = "payrollExecutor")
    public Executor payrollExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(10);
        executor.setThreadNamePrefix("payroll-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }

    /**
     * Thread pool for async media processing (thumbnails, etc.).
     *
     * @return configured executor for media background work
     */
    @Bean(name = "mediaExecutor")
    public Executor mediaExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("media-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
