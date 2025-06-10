package com.reliaquest.api.service;

import com.reliaquest.api.exception.EmployeeNotFoundException;
import com.reliaquest.api.exception.ExternalApiException;
import com.reliaquest.api.model.Employee;
import com.reliaquest.api.model.MockApiResponse;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.reactor.ratelimiter.operator.RateLimiterOperator;
import io.github.resilience4j.reactor.retry.RetryOperator;
import io.github.resilience4j.retry.Retry;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmployeeService {

    private final WebClient webClient;
    private final Retry retry;
    private final RateLimiter rateLimiter;

    public Mono<List<Employee>> getAllEmployees() {
        log.debug("Fetching all employees");
        return webClient
                .get()
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<MockApiResponse<List<Employee>>>() {})
                .transformDeferred(RetryOperator.of(retry))
                .transformDeferred(RateLimiterOperator.of(rateLimiter))
                .map(MockApiResponse::getData)
                .doOnSuccess(employees -> log.debug("Successfully fetched {} employees", employees.size()))
                .doOnError(error -> log.error("Error fetching all employees", error))
                .onErrorMap(this::mapException);
    }

    private Throwable mapException(Throwable throwable) {
        if (throwable instanceof WebClientResponseException webClientException) {
            if (webClientException.getStatusCode() == HttpStatus.NOT_FOUND) {
                return new EmployeeNotFoundException("Employee not found");
            }
            return new ExternalApiException(
                    "External API error: " + webClientException.getMessage(), webClientException);
        }
        return new ExternalApiException("Unexpected error occurred", throwable);
    }
}
