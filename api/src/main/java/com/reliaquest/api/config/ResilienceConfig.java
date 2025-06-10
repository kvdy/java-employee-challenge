package com.reliaquest.api.config;

import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Configuration
public class ResilienceConfig {

    @Value("${mock-employee-api.retry.max-attempts}")
    private int maxAttempts;

    @Value("${mock-employee-api.retry.wait-duration}")
    private Duration waitDuration;

    @Value("${mock-employee-api.retry.max-wait-duration}")
    private Duration maxWaitDuration;

    @Value("${mock-employee-api.rate-limiter.limit-for-period}")
    private int limitForPeriod;

    @Value("${mock-employee-api.rate-limiter.limit-refresh-period}")
    private Duration limitRefreshPeriod;

    @Value("${mock-employee-api.rate-limiter.timeout-duration}")
    private Duration timeoutDuration;

    @Bean
    public Retry retry() {
        RetryConfig config = RetryConfig.custom()
                .maxAttempts(maxAttempts)
                .retryOnException(throwable -> throwable instanceof WebClientResponseException.TooManyRequests
                        || throwable instanceof WebClientResponseException.ServiceUnavailable)
                .intervalFunction(
                        IntervalFunction.ofExponentialBackoff(waitDuration.toMillis(), 2, maxWaitDuration.toMillis()))
                .build();
        return Retry.of("employeeApiRetry", config);
    }

    @Bean
    public RateLimiter rateLimiter() {
        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitForPeriod(limitForPeriod)
                .limitRefreshPeriod(limitRefreshPeriod)
                .timeoutDuration(timeoutDuration)
                .build();
        return RateLimiter.of("employeeApiRateLimiter", config);
    }
}
