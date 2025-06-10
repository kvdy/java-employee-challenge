package com.reliaquest.api.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.reliaquest.api.exception.EmployeeNotFoundException;
import com.reliaquest.api.model.CreateEmployeeInput;
import com.reliaquest.api.model.Employee;
import com.reliaquest.api.model.MockApiResponse;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.retry.Retry;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
public class EmployeeServiceTest {

    @Mock
    private WebClient webClient;

    @Mock
    private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;

    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpec;

    @Mock
    private WebClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private WebClient.RequestBodySpec requestBodySpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    @Mock
    private Retry retry;

    @Mock
    private RateLimiter rateLimiter;

    private EmployeeService employeeService;

    @BeforeEach
    void setUp() {
        when(retry.getName()).thenReturn("test-retry");
        when(rateLimiter.getName()).thenReturn("test-rate-limiter");
        employeeService = new EmployeeService(webClient, retry, rateLimiter);
    }

    @Test
    void getAllEmployees_shouldReturnListOfEmployees() {
        List<Employee> employees =
                Arrays.asList(createEmployee("John Doe", 50000), createEmployee("Jane Smith", 60000));
        MockApiResponse<List<Employee>> response = new MockApiResponse<>(employees, "Success", null);

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class))).thenReturn(Mono.just(response));

        StepVerifier.create(employeeService.getAllEmployees())
                .expectNext(employees)
                .verifyComplete();
    }

    @Test
    void getEmployeeById_shouldReturnEmployee() {
        String id = UUID.randomUUID().toString();
        Employee employee = createEmployee("John Doe", 50000);
        MockApiResponse<Employee> response = new MockApiResponse<>(employee, "Success", null);

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri("/{id}", id)).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class))).thenReturn(Mono.just(response));

        StepVerifier.create(employeeService.getEmployeeById(id))
                .expectNext(employee)
                .verifyComplete();
    }

    @Test
    void getEmployeeById_shouldThrowNotFoundException_whenEmployeeNotFound() {
        String id = UUID.randomUUID().toString();
        WebClientResponseException notFoundException =
                WebClientResponseException.create(HttpStatus.NOT_FOUND.value(), "Not Found", null, null, null);

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri("/{id}", id)).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class))).thenReturn(Mono.error(notFoundException));

        StepVerifier.create(employeeService.getEmployeeById(id))
                .expectError(EmployeeNotFoundException.class)
                .verify();
    }

    @Test
    void searchEmployeesByName_shouldReturnFilteredEmployees() {
        List<Employee> employees = Arrays.asList(
                createEmployee("John Doe", 50000),
                createEmployee("Jane Smith", 60000),
                createEmployee("John Smith", 55000));
        MockApiResponse<List<Employee>> response = new MockApiResponse<>(employees, "Success", null);

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class))).thenReturn(Mono.just(response));

        StepVerifier.create(employeeService.searchEmployeesByName("John"))
                .expectNextMatches(list -> list.size() == 2
                        && list.stream().allMatch(e -> e.getName().contains("John")))
                .verifyComplete();
    }

    @Test
    void getHighestSalary_shouldReturnHighestSalary() {
        List<Employee> employees = Arrays.asList(
                createEmployee("John Doe", 50000),
                createEmployee("Jane Smith", 60000),
                createEmployee("Bob Jones", 55000));
        MockApiResponse<List<Employee>> response = new MockApiResponse<>(employees, "Success", null);

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class))).thenReturn(Mono.just(response));

        StepVerifier.create(employeeService.getHighestSalary())
                .expectNext(60000)
                .verifyComplete();
    }

    @Test
    void getTopTenHighestEarningEmployeeNames_shouldReturnTop10Names() {
        List<Employee> employees = createEmployeeList();
        MockApiResponse<List<Employee>> response = new MockApiResponse<>(employees, "Success", null);

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class))).thenReturn(Mono.just(response));

        StepVerifier.create(employeeService.getTopTenHighestEarningEmployeeNames())
                .expectNextMatches(names -> names.size() == 10 && names.get(0).equals("Employee 11"))
                .verifyComplete();
    }

    @Test
    void createEmployee_shouldReturnCreatedEmployee() {
        CreateEmployeeInput input = CreateEmployeeInput.builder()
                .name("New Employee")
                .salary(70000)
                .age(30)
                .title("Developer")
                .build();

        Employee createdEmployee = Employee.builder()
                .id(UUID.randomUUID())
                .name("New Employee")
                .salary(70000)
                .age(30)
                .title("Developer")
                .email("newemployee@company.com")
                .build();

        MockApiResponse<Employee> response = new MockApiResponse<>(createdEmployee, "Success", null);

        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.contentType(any())).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class))).thenReturn(Mono.just(response));

        StepVerifier.create(employeeService.createEmployee(input))
                .expectNext(createdEmployee)
                .verifyComplete();
    }

    private Employee createEmployee(String name, int salary) {
        return Employee.builder()
                .id(UUID.randomUUID())
                .name(name)
                .salary(salary)
                .age(30)
                .title("Developer")
                .email(name.toLowerCase().replace(" ", "") + "@company.com")
                .build();
    }

    private List<Employee> createEmployeeList() {
        return Arrays.asList(
                createEmployee("Employee 1", 50000),
                createEmployee("Employee 2", 55000),
                createEmployee("Employee 3", 60000),
                createEmployee("Employee 4", 65000),
                createEmployee("Employee 5", 70000),
                createEmployee("Employee 6", 75000),
                createEmployee("Employee 7", 80000),
                createEmployee("Employee 8", 85000),
                createEmployee("Employee 9", 90000),
                createEmployee("Employee 10", 95000),
                createEmployee("Employee 11", 100000));
    }
}
