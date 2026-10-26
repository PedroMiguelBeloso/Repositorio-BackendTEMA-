package com.temaconsulting.agente_iso.verification.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.slf4j.MDC;
import java.util.Map;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
public class VerificationAsyncConfig {
    @Bean("verificationTaskExecutor")
    public Executor verificationTaskExecutor(
            @Value("${tema.verification.queue-capacity:10}") int queueCapacity
    ) {
        if (queueCapacity < 1) {
            throw new IllegalArgumentException("Verification queue capacity must be positive");
        }
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("verification-");
        executor.setTaskDecorator(task -> {
            Map<String, String> context = MDC.getCopyOfContextMap();
            return () -> {
                Map<String, String> previous = MDC.getCopyOfContextMap();
                try {
                    if (context == null) MDC.clear(); else MDC.setContextMap(context);
                    task.run();
                } finally {
                    if (previous == null) MDC.clear(); else MDC.setContextMap(previous);
                }
            };
        });
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        return executor;
    }
}
