package com.temaconsulting.agente_iso.integration.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
public class IntegrationProbeConfiguration {
    @Bean(destroyMethod = "shutdownNow")
    public ExecutorService integrationProbeExecutor() {
        AtomicInteger sequence = new AtomicInteger();
        return new ThreadPoolExecutor(
                2, 2, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(4),
                task -> {
                    Thread thread = new Thread(task, "integration-probe-" + sequence.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy()
        );
    }
}
