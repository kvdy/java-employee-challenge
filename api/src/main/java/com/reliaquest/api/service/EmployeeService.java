package com.reliaquest.api.service;

import com.reliaquest.api.exception.EmployeeNotFoundException;
import com.reliaquest.api.exception.ExternalApiException;
import com.reliaquest.api.model.CreateEmployeeInput;
import com.reliaquest.api.model.DeleteEmployeeInput;
import com.reliaquest.api.model.Employee;
import com.reliaquest.api.model.MockApiResponse;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.reactor.ratelimiter.operator.RateLimiterOperator;
import io.github.resilience4j.reactor.retry.RetryOperator;
import io.github.resilience4j.retry.Retry;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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

    public Mono<List<Employee>> searchEmployeesByName(String searchString) {
        log.debug("Searching employees by name: {}", searchString);
        return getAllEmployees().map(employees -> employees.stream()
                .filter(employee -> employee.getName().toLowerCase().contains(searchString.toLowerCase()))
                .collect(Collectors.toList()));
    }

    public Mono<Employee> getEmployeeById(String id) {
        log.debug("Fetching employee by id: {}", id);
        return webClient
                .get()
                .uri("/{id}", id)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<MockApiResponse<Employee>>() {})
                .transformDeferred(RetryOperator.of(retry))
                .transformDeferred(RateLimiterOperator.of(rateLimiter))
                .map(MockApiResponse::getData)
                .doOnSuccess(employee -> log.debug("Successfully fetched employee: {}", employee))
                .doOnError(error -> log.error("Error fetching employee by id: {}", id, error))
                .onErrorMap(this::mapException);
    }

    public Mono<Integer> getHighestSalary() {
        log.debug("Finding highest salary among all employees");
        return getAllEmployees()
                .map(employees ->
                        employees.stream().mapToInt(Employee::getSalary).max().orElse(0))
                .doOnSuccess(salary -> log.debug("Highest salary found: {}", salary));
    }

    public Mono<List<String>> getTopTenHighestEarningEmployeeNames() {
        log.debug("Finding top 10 highest earning employees");
        return getAllEmployees()
                .map(employees -> employees.stream()
                        .sorted(Comparator.comparing(Employee::getSalary).reversed())
                        .limit(10)
                        .map(Employee::getName)
                        .collect(Collectors.toList()))
                .doOnSuccess(names -> log.debug("Top 10 highest earners: {}", names));
    }

    public Mono<Employee> createEmployee(CreateEmployeeInput input) {
        log.debug("Creating new employee: {}", input);
        return webClient
                .post()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(input)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<MockApiResponse<Employee>>() {})
                .transformDeferred(RetryOperator.of(retry))
                .transformDeferred(RateLimiterOperator.of(rateLimiter))
                .map(MockApiResponse::getData)
                .doOnSuccess(employee -> log.info("Successfully created employee: {}", employee))
                .doOnError(error -> log.error("Error creating employee", error))
                .onErrorMap(this::mapException);
    }

    public Mono<String> deleteEmployeeById(String id) {
        log.debug("Deleting employee by id: {}", id);
        return getEmployeeById(id)
                .flatMap(employee -> {
                    DeleteEmployeeInput deleteInput = DeleteEmployeeInput.builder()
                            .name(employee.getName())
                            .build();
                    return webClient
                            .delete()
                            .uri("/{name}", employee.getName())
                            .retrieve()
                            .bodyToMono(new ParameterizedTypeReference<MockApiResponse<Boolean>>() {})
                            .transformDeferred(RetryOperator.of(retry))
                            .transformDeferred(RateLimiterOperator.of(rateLimiter))
                            .map(response -> employee.getName());
                })
                .doOnSuccess(name -> log.info("Successfully deleted employee: {}", name))
                .doOnError(error -> log.error("Error deleting employee by id: {}", id, error))
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
