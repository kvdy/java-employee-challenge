package com.reliaquest.api.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.reliaquest.api.exception.EmployeeNotFoundException;
import com.reliaquest.api.exception.ExternalApiException;
import com.reliaquest.api.model.CreateEmployeeInput;
import com.reliaquest.api.model.Employee;
import com.reliaquest.api.model.MockApiResponse;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.retry.Retry;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class EmployeeServiceTest {

    @Mock private WebClient webClient;
    @Mock private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;
    @Mock private WebClient.RequestHeadersSpec requestHeadersSpec;
    @Mock private WebClient.RequestBodyUriSpec requestBodyUriSpec;
    @Mock private WebClient.RequestBodySpec requestBodySpec;
    @Mock private WebClient.ResponseSpec responseSpec;

    private EmployeeService employeeService;
    private Retry retry;
    private RateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        // Create real instances with test-friendly configurations
        retry = Retry.ofDefaults("test-retry");
        rateLimiter = RateLimiter.ofDefaults("test-rate-limiter");
        employeeService = new EmployeeService(webClient, retry, rateLimiter);
    }

    @Test
    void getAllEmployees_shouldReturnListOfEmployees() {
        // Given
        List<Employee> employees = Arrays.asList(
            createEmployee("John Doe", 50000), 
            createEmployee("Jane Smith", 60000)
        );
        MockApiResponse<List<Employee>> response = new MockApiResponse<>(employees, "Success", null);

        setupWebClientMock(Mono.just(response));

        // When & Then
        StepVerifier.create(employeeService.getAllEmployees())
                .expectNext(employees)
                .verifyComplete();
    }

    @Test
    void getAllEmployees_shouldHandleEmptyList() {
        // Given
        List<Employee> emptyList = Collections.emptyList();
        MockApiResponse<List<Employee>> response = new MockApiResponse<>(emptyList, "Success", null);

        setupWebClientMock(Mono.just(response));

        // When & Then
        StepVerifier.create(employeeService.getAllEmployees())
                .expectNext(emptyList)
                .verifyComplete();
    }

    @Test
    void getAllEmployees_shouldHandleLargeDataset() {
        // Given - Large dataset with 1000+ employees
        List<Employee> largeEmployeeList = createLargeEmployeeList(1500);
        MockApiResponse<List<Employee>> response = new MockApiResponse<>(largeEmployeeList, "Success", null);

        setupWebClientMock(Mono.just(response));

        // When & Then
        StepVerifier.create(employeeService.getAllEmployees())
                .expectNextMatches(list -> list.size() == 1500)
                .verifyComplete();
    }

    @Test
    void getEmployeeById_shouldReturnEmployee() {
        // Given
        String id = UUID.randomUUID().toString();
        Employee employee = createEmployee("John Doe", 50000);
        MockApiResponse<Employee> response = new MockApiResponse<>(employee, "Success", null);

        setupWebClientMockWithUri(id, Mono.just(response));

        // When & Then
        StepVerifier.create(employeeService.getEmployeeById(id))
                .expectNext(employee)
                .verifyComplete();
    }

    @Test
    void getEmployeeById_shouldThrowNotFoundException_whenEmployeeNotFound() {
        // Given
        String id = UUID.randomUUID().toString();
        WebClientResponseException notFoundException = 
            WebClientResponseException.create(HttpStatus.NOT_FOUND.value(), "Not Found", null, null, null);

        setupWebClientMockWithUri(id, Mono.error(notFoundException));

        // When & Then
        StepVerifier.create(employeeService.getEmployeeById(id))
                .expectError(EmployeeNotFoundException.class)
                .verify();
    }

    @Test
    void getEmployeeById_shouldHandleInvalidUUIDs() {
        // Given
        String invalidId = "invalid-uuid-format";
        WebClientResponseException badRequestException = 
            WebClientResponseException.create(HttpStatus.BAD_REQUEST.value(), "Bad Request", null, null, null);

        setupWebClientMockWithUri(invalidId, Mono.error(badRequestException));

        // When & Then
        StepVerifier.create(employeeService.getEmployeeById(invalidId))
                .expectError(ExternalApiException.class)
                .verify();
    }

    @Test
    void searchEmployeesByName_shouldReturnFilteredEmployees() {
        // Given
        List<Employee> employees = Arrays.asList(
                createEmployee("John Doe", 50000),
                createEmployee("Jane Smith", 60000),
                createEmployee("John Smith", 55000)
        );
        MockApiResponse<List<Employee>> response = new MockApiResponse<>(employees, "Success", null);

        setupWebClientMock(Mono.just(response));

        // When & Then
        StepVerifier.create(employeeService.searchEmployeesByName("John"))
                .expectNextMatches(list -> list.size() == 2 
                    && list.stream().allMatch(e -> e.getName().contains("John")))
                .verifyComplete();
    }

    @Test
    void searchEmployeesByName_shouldHandleCaseInsensitiveSearch() {
        // Given
        List<Employee> employees = Arrays.asList(
                createEmployee("JOHN DOE", 50000),
                createEmployee("jane smith", 60000),
                createEmployee("John Smith", 55000)
        );
        MockApiResponse<List<Employee>> response = new MockApiResponse<>(employees, "Success", null);

        setupWebClientMock(Mono.just(response));

        // When & Then
        StepVerifier.create(employeeService.searchEmployeesByName("john"))
                .expectNextMatches(list -> list.size() == 2)
                .verifyComplete();
    }

    @Test
    void searchEmployeesByName_shouldHandleSpecialCharacters() {
        // Given
        List<Employee> employees = Arrays.asList(
                createEmployee("José María García-López", 50000),
                createEmployee("田中太郎", 60000),
                createEmployee("François O'Connor", 55000)
        );
        MockApiResponse<List<Employee>> response = new MockApiResponse<>(employees, "Success", null);

        setupWebClientMock(Mono.just(response));

        // When & Then
        StepVerifier.create(employeeService.searchEmployeesByName("José"))
                .expectNextMatches(list -> list.size() == 1)
                .verifyComplete();
    }

    @Test
    void getHighestSalary_shouldReturnHighestSalary() {
        // Given
        List<Employee> employees = Arrays.asList(
                createEmployee("John Doe", 50000),
                createEmployee("Jane Smith", 60000),
                createEmployee("Bob Jones", 55000)
        );
        MockApiResponse<List<Employee>> response = new MockApiResponse<>(employees, "Success", null);

        setupWebClientMock(Mono.just(response));

        // When & Then
        StepVerifier.create(employeeService.getHighestSalary())
                .expectNext(60000)
                .verifyComplete();
    }

    @Test
    void getHighestSalary_shouldHandleEmptyList() {
        // Given
        List<Employee> emptyList = Collections.emptyList();
        MockApiResponse<List<Employee>> response = new MockApiResponse<>(emptyList, "Success", null);

        setupWebClientMock(Mono.just(response));

        // When & Then
        StepVerifier.create(employeeService.getHighestSalary())
                .expectNext(0)
                .verifyComplete();
    }

    @Test
    void getHighestSalary_shouldHandleMaxIntegerValue() {
        // Given
        List<Employee> employees = Arrays.asList(
                createEmployee("Rich Person", Integer.MAX_VALUE),
                createEmployee("Regular Person", 50000)
        );
        MockApiResponse<List<Employee>> response = new MockApiResponse<>(employees, "Success", null);

        setupWebClientMock(Mono.just(response));

        // When & Then
        StepVerifier.create(employeeService.getHighestSalary())
                .expectNext(Integer.MAX_VALUE)
                .verifyComplete();
    }

    @Test
    void getTopTenHighestEarningEmployeeNames_shouldReturnTop10Names() {
        // Given
        List<Employee> employees = createEmployeeList();
        MockApiResponse<List<Employee>> response = new MockApiResponse<>(employees, "Success", null);

        setupWebClientMock(Mono.just(response));

        // When & Then
        StepVerifier.create(employeeService.getTopTenHighestEarningEmployeeNames())
                .expectNextMatches(names -> names.size() == 10 && names.get(0).equals("Employee 11"))
                .verifyComplete();
    }

    @Test
    void getTopTenHighestEarningEmployeeNames_shouldHandleFewerThan10Employees() {
        // Given
        List<Employee> employees = Arrays.asList(
                createEmployee("Employee 1", 50000),
                createEmployee("Employee 2", 60000),
                createEmployee("Employee 3", 55000)
        );
        MockApiResponse<List<Employee>> response = new MockApiResponse<>(employees, "Success", null);

        setupWebClientMock(Mono.just(response));

        // When & Then
        StepVerifier.create(employeeService.getTopTenHighestEarningEmployeeNames())
                .expectNextMatches(names -> names.size() == 3)
                .verifyComplete();
    }

    @Test
    void createEmployee_shouldReturnCreatedEmployee() {
        // Given
        CreateEmployeeInput input = CreateEmployeeInput.builder()
                .name("New Employee")
                .salary(70000)
                .age(30)
                .title("Developer")
                .build();

        Employee createdEmployee = createEmployee("New Employee", 70000);
        MockApiResponse<Employee> response = new MockApiResponse<>(createdEmployee, "Success", null);

        setupCreateEmployeeMock(Mono.just(response));

        // When & Then
        StepVerifier.create(employeeService.createEmployee(input))
                .expectNext(createdEmployee)
                .verifyComplete();
    }

    @Test
    void createEmployee_shouldHandleValidationErrors() {
        // Given
        CreateEmployeeInput input = CreateEmployeeInput.builder()
                .name("") // Invalid name
                .salary(-1000) // Invalid salary
                .age(10) // Invalid age
                .title("")
                .build();

        WebClientResponseException badRequestException = 
            WebClientResponseException.create(HttpStatus.BAD_REQUEST.value(), "Bad Request", null, null, null);

        setupCreateEmployeeMock(Mono.error(badRequestException));

        // When & Then
        StepVerifier.create(employeeService.createEmployee(input))
                .expectError(ExternalApiException.class)
                .verify();
    }

    @Test
    void deleteEmployeeById_shouldReturnEmployeeName() {
        // Given
        String id = UUID.randomUUID().toString();
        Employee employee = createEmployee("John Doe", 50000);
        MockApiResponse<Employee> getResponse = new MockApiResponse<>(employee, "Success", null);
        MockApiResponse<Boolean> deleteResponse = new MockApiResponse<>(true, "Success", null);

        // Setup get employee mock
        setupWebClientMockWithUri(id, Mono.just(getResponse));
        
        // Setup delete employee mock - Need to use separate requestHeadersUriSpec for delete
        WebClient.RequestHeadersUriSpec deleteHeadersUriSpec = mock(WebClient.RequestHeadersUriSpec.class);
        WebClient.RequestHeadersSpec deleteHeadersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec deleteResponseSpec = mock(WebClient.ResponseSpec.class);
        
        when(webClient.delete()).thenReturn(deleteHeadersUriSpec);
        when(deleteHeadersUriSpec.uri("/{name}", employee.getName())).thenReturn(deleteHeadersSpec);
        when(deleteHeadersSpec.retrieve()).thenReturn(deleteResponseSpec);
        when(deleteResponseSpec.bodyToMono(any(ParameterizedTypeReference.class))).thenReturn(Mono.just(deleteResponse));

        // When & Then
        StepVerifier.create(employeeService.deleteEmployeeById(id))
                .expectNext("John Doe")
                .verifyComplete();
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void getAllEmployees_shouldHandleConcurrentRequests() throws InterruptedException {
        // Given
        List<Employee> employees = Arrays.asList(createEmployee("Test Employee", 50000));
        MockApiResponse<List<Employee>> response = new MockApiResponse<>(employees, "Success", null);

        setupWebClientMock(Mono.just(response));

        // When - Multiple concurrent requests
        int numberOfThreads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch latch = new CountDownLatch(numberOfThreads);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < numberOfThreads; i++) {
            executor.submit(() -> {
                try {
                    StepVerifier.create(employeeService.getAllEmployees())
                            .expectNext(employees)
                            .verifyComplete();
                    successCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        // Then
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertEquals(numberOfThreads, successCount.get());
        executor.shutdown();
    }

    @Test
    void getAllEmployees_shouldHandleServiceUnavailable() {
        // Given
        WebClientResponseException serviceUnavailableException = 
            WebClientResponseException.create(HttpStatus.SERVICE_UNAVAILABLE.value(), "Service Unavailable", null, null, null);

        setupWebClientMock(Mono.error(serviceUnavailableException));

        // When & Then
        StepVerifier.create(employeeService.getAllEmployees())
                .expectError(ExternalApiException.class)
                .verify();
    }

    @Test
    void getAllEmployees_shouldHandleTooManyRequests() {
        // Given
        WebClientResponseException tooManyRequestsException = 
            WebClientResponseException.create(HttpStatus.TOO_MANY_REQUESTS.value(), "Too Many Requests", null, null, null);

        setupWebClientMock(Mono.error(tooManyRequestsException));

        // When & Then
        StepVerifier.create(employeeService.getAllEmployees())
                .expectError(ExternalApiException.class)
                .verify();
    }

    @Test
    void searchEmployeesByName_shouldHandleNullAndEmptySearchString() {
        // Given
        List<Employee> employees = Arrays.asList(createEmployee("John Doe", 50000));
        MockApiResponse<List<Employee>> response = new MockApiResponse<>(employees, "Success", null);

        setupWebClientMock(Mono.just(response));

        // When & Then - Empty string should return all employees
        StepVerifier.create(employeeService.searchEmployeesByName(""))
                .expectNextMatches(list -> list.size() == 1)
                .verifyComplete();
    }

    @Test
    void createEmployee_shouldHandleExtremeValues() {
        // Given
        CreateEmployeeInput input = CreateEmployeeInput.builder()
                .name("Employee with very long name that exceeds normal limits and contains special characters éñüñç")
                .salary(Integer.MAX_VALUE)
                .age(16) // Minimum age
                .title("Title with émojis 😀 and special characters")
                .build();

        Employee createdEmployee = createEmployeeWithExtremeValues();
        MockApiResponse<Employee> response = new MockApiResponse<>(createdEmployee, "Success", null);

        setupCreateEmployeeMock(Mono.just(response));

        // When & Then
        StepVerifier.create(employeeService.createEmployee(input))
                .expectNext(createdEmployee)
                .verifyComplete();
    }

    @Test
    void createEmployee_shouldHandleMaximumAge() {
        // Given
        CreateEmployeeInput input = CreateEmployeeInput.builder()
                .name("Senior Employee")
                .salary(80000)
                .age(75) // Maximum age
                .title("Senior Consultant")
                .build();

        Employee createdEmployee = createEmployee("Senior Employee", 80000);
        MockApiResponse<Employee> response = new MockApiResponse<>(createdEmployee, "Success", null);

        setupCreateEmployeeMock(Mono.just(response));

        // When & Then
        StepVerifier.create(employeeService.createEmployee(input))
                .expectNext(createdEmployee)
                .verifyComplete();
    }

    // Security test scenarios
    @Test
    void searchEmployeesByName_shouldHandleSqlInjectionAttempt() {
        // Given
        String sqlInjectionAttempt = "'; DROP TABLE employees; --";
        List<Employee> employees = Arrays.asList(createEmployee("Safe Employee", 50000));
        MockApiResponse<List<Employee>> response = new MockApiResponse<>(employees, "Success", null);

        setupWebClientMock(Mono.just(response));

        // When & Then - Should not find any matches (safe handling)
        StepVerifier.create(employeeService.searchEmployeesByName(sqlInjectionAttempt))
                .expectNextMatches(list -> list.isEmpty())
                .verifyComplete();
    }

    @Test
    void createEmployee_shouldHandleXssAttempt() {
        // Given
        CreateEmployeeInput input = CreateEmployeeInput.builder()
                .name("<script>alert('xss')</script>")
                .salary(50000)
                .age(30)
                .title("<img src=x onerror=alert('xss')>")
                .build();

        // This should be handled by validation or sanitization
        WebClientResponseException badRequestException = 
            WebClientResponseException.create(HttpStatus.BAD_REQUEST.value(), "Invalid input", null, null, null);

        setupCreateEmployeeMock(Mono.error(badRequestException));

        // When & Then
        StepVerifier.create(employeeService.createEmployee(input))
                .expectError(ExternalApiException.class)
                .verify();
    }

    // Memory and performance edge cases
    @Test
    void getAllEmployees_shouldHandleVeryLargeResponse() {
        // Given - Simulate very large response
        List<Employee> largeEmployeeList = createLargeEmployeeList(10000);
        MockApiResponse<List<Employee>> response = new MockApiResponse<>(largeEmployeeList, "Success", null);

        setupWebClientMock(Mono.just(response));

        // When & Then
        StepVerifier.create(employeeService.getAllEmployees())
                .expectNextMatches(list -> list.size() == 10000)
                .verifyComplete();
    }

    @Test
    void getAllEmployees_shouldHandleTimeout() {
        // Given
        setupWebClientMock(Mono.delay(Duration.ofSeconds(10)).then(Mono.just(new MockApiResponse<>(Collections.emptyList(), "Success", null))));

        // When & Then
        StepVerifier.create(employeeService.getAllEmployees())
                .expectTimeout(Duration.ofSeconds(5))
                .verify();
    }

    // Helper methods
    private void setupWebClientMock(Mono<MockApiResponse<List<Employee>>> response) {
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class))).thenReturn(response);
    }

    private void setupWebClientMockWithUri(String uri, Mono<MockApiResponse<Employee>> response) {
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri("/{id}", uri)).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class))).thenReturn(response);
    }

    private void setupCreateEmployeeMock(Mono<MockApiResponse<Employee>> response) {
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.contentType(MediaType.APPLICATION_JSON)).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class))).thenReturn(response);
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

    private Employee createEmployeeWithExtremeValues() {
        return Employee.builder()
                .id(UUID.randomUUID())
                .name("Employee with very long name that exceeds normal limits and contains special characters éñüñç")
                .salary(Integer.MAX_VALUE)
                .age(16)
                .title("Title with émojis 😀 and special characters")
                .email("extremeemployee@company.com")
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
                createEmployee("Employee 11", 100000)
        );
    }

    private List<Employee> createLargeEmployeeList(int size) {
        return java.util.stream.IntStream.range(0, size)
                .mapToObj(i -> createEmployee("Employee " + i, 50000 + i))
                .toList();
    }
}