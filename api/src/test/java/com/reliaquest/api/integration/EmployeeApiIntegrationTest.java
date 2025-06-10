package com.reliaquest.api.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reliaquest.api.model.CreateEmployeeInput;
import com.reliaquest.api.model.Employee;
import com.reliaquest.api.model.MockApiResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EmployeeApiIntegrationTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private ObjectMapper objectMapper;

    private static MockWebServer mockWebServer;

    @BeforeAll
    void setUpAll() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
    }

    @AfterAll
    void tearDownAll() throws IOException {
        if (mockWebServer != null) {
            mockWebServer.shutdown();
        }
    }

    @BeforeEach
    void setUp() {
        // Clear any existing queued responses
        while (mockWebServer.getRequestCount() > 0) {
            try {
                mockWebServer.takeRequest(100, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add(
                "mock-employee-api.base-url",
                () -> mockWebServer != null ? String.format("http://localhost:%s/api/v1/employee", mockWebServer.getPort()) : "http://localhost:8112/api/v1/employee");
    }

    @Test
    void getAllEmployees_shouldReturnEmployeesList() throws Exception {
        // Given
        List<Employee> employees = Arrays.asList(createEmployee("John Doe"), createEmployee("Jane Smith"));
        MockApiResponse<List<Employee>> mockResponse = new MockApiResponse<>(employees, "Success", null);

        mockWebServer.enqueue(new MockResponse()
                .setBody(objectMapper.writeValueAsString(mockResponse))
                .addHeader("Content-Type", "application/json"));

        // When
        ResponseEntity<List<Employee>> response = restTemplate.exchange(
                "/api/v1/employee", HttpMethod.GET, null, new ParameterizedTypeReference<List<Employee>>() {});

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().size());
        assertEquals("John Doe", response.getBody().get(0).getName());
        assertEquals("Jane Smith", response.getBody().get(1).getName());
    }

    @Test
    void getAllEmployees_shouldHandleEmptyResponse() throws Exception {
        // Given
        MockApiResponse<List<Employee>> mockResponse = new MockApiResponse<>(Arrays.asList(), "Success", null);

        mockWebServer.enqueue(new MockResponse()
                .setBody(objectMapper.writeValueAsString(mockResponse))
                .addHeader("Content-Type", "application/json"));

        // When
        ResponseEntity<List<Employee>> response = restTemplate.exchange(
                "/api/v1/employee", HttpMethod.GET, null, new ParameterizedTypeReference<List<Employee>>() {});

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isEmpty());
    }

    @Test
    void getEmployeeById_shouldReturnEmployee() throws Exception {
        // Given
        String employeeId = UUID.randomUUID().toString();
        Employee employee = createEmployee("John Doe");
        MockApiResponse<Employee> mockResponse = new MockApiResponse<>(employee, "Success", null);

        mockWebServer.enqueue(new MockResponse()
                .setBody(objectMapper.writeValueAsString(mockResponse))
                .addHeader("Content-Type", "application/json"));

        // When
        ResponseEntity<Employee> response = 
                restTemplate.getForEntity("/api/v1/employee/" + employeeId, Employee.class);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("John Doe", response.getBody().getName());
    }

    @Test
    void getEmployeeById_shouldReturn404WhenNotFound() throws Exception {
        // Given
        String employeeId = UUID.randomUUID().toString();

        mockWebServer.enqueue(new MockResponse().setResponseCode(404));

        // When
        ResponseEntity<String> response = 
                restTemplate.getForEntity("/api/v1/employee/" + employeeId, String.class);

        // Then
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void searchEmployeesByName_shouldReturnFilteredEmployees() throws Exception {
        // Given
        List<Employee> employees = Arrays.asList(
                createEmployee("John Doe"), 
                createEmployee("John Smith"), 
                createEmployee("Jane Doe"));
        MockApiResponse<List<Employee>> mockResponse = new MockApiResponse<>(employees, "Success", null);

        mockWebServer.enqueue(new MockResponse()
                .setBody(objectMapper.writeValueAsString(mockResponse))
                .addHeader("Content-Type", "application/json"));

        // When
        ResponseEntity<List<Employee>> response = restTemplate.exchange(
                "/api/v1/employee/search/John", 
                HttpMethod.GET, 
                null, 
                new ParameterizedTypeReference<List<Employee>>() {});

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().size()); // Should return both Johns
        assertTrue(response.getBody().stream().allMatch(e -> e.getName().contains("John")));
    }

    @Test
    void getHighestSalary_shouldReturnCorrectValue() throws Exception {
        // Given
        List<Employee> employees = Arrays.asList(
                createEmployeeWithSalary("John Doe", 50000),
                createEmployeeWithSalary("Jane Smith", 75000),
                createEmployeeWithSalary("Bob Johnson", 60000));
        MockApiResponse<List<Employee>> mockResponse = new MockApiResponse<>(employees, "Success", null);

        mockWebServer.enqueue(new MockResponse()
                .setBody(objectMapper.writeValueAsString(mockResponse))
                .addHeader("Content-Type", "application/json"));

        // When
        ResponseEntity<Integer> response = 
                restTemplate.getForEntity("/api/v1/employee/highestSalary", Integer.class);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(75000, response.getBody());
    }

    @Test
    void getTopTenHighestEarningEmployeeNames_shouldReturnCorrectList() throws Exception {
        // Given
        List<Employee> employees = createMultipleEmployeesWithDifferentSalaries();
        MockApiResponse<List<Employee>> mockResponse = new MockApiResponse<>(employees, "Success", null);

        mockWebServer.enqueue(new MockResponse()
                .setBody(objectMapper.writeValueAsString(mockResponse))
                .addHeader("Content-Type", "application/json"));

        // When
        ResponseEntity<List<String>> response = restTemplate.exchange(
                "/api/v1/employee/topTenHighestEarningEmployeeNames",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<String>>() {});

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().size() <= 10);
        // Should be sorted by salary descending
        assertEquals("Employee 12", response.getBody().get(0)); // Highest salary
    }

    @Test
    void createEmployee_shouldReturnCreatedEmployee() throws Exception {
        // Given
        CreateEmployeeInput input = CreateEmployeeInput.builder()
                .name("New Employee")
                .salary(70000)
                .age(30)
                .title("Developer")
                .build();

        Employee createdEmployee = createEmployee("New Employee");
        MockApiResponse<Employee> mockResponse = new MockApiResponse<>(createdEmployee, "Success", null);

        mockWebServer.enqueue(new MockResponse()
                .setBody(objectMapper.writeValueAsString(mockResponse))
                .addHeader("Content-Type", "application/json"));

        // When
        ResponseEntity<Employee> response = restTemplate.postForEntity("/api/v1/employee", input, Employee.class);

        // Then
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("New Employee", response.getBody().getName());

        // Verify the request was made correctly
        RecordedRequest recordedRequest = mockWebServer.takeRequest(1, TimeUnit.SECONDS);
        assertNotNull(recordedRequest);
        assertEquals("POST", recordedRequest.getMethod());
        assertTrue(recordedRequest.getHeader("Content-Type").contains("application/json"));
    }

    @Test
    void createEmployee_shouldHandleValidationErrors() throws Exception {
        // Given
        CreateEmployeeInput input = CreateEmployeeInput.builder()
                .name("") // Invalid name
                .salary(-1000) // Invalid salary
                .age(10) // Invalid age
                .title("")
                .build();

        // When
        ResponseEntity<String> response = restTemplate.postForEntity("/api/v1/employee", input, String.class);

        // Then
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void deleteEmployeeById_shouldReturnEmployeeName() throws Exception {
        // Given
        String employeeId = UUID.randomUUID().toString();
        Employee employee = createEmployee("John Doe");
        MockApiResponse<Employee> getResponse = new MockApiResponse<>(employee, "Success", null);
        MockApiResponse<Boolean> deleteResponse = new MockApiResponse<>(true, "Success", null);

        // Queue responses: first for GET, then for DELETE
        mockWebServer.enqueue(new MockResponse()
                .setBody(objectMapper.writeValueAsString(getResponse))
                .addHeader("Content-Type", "application/json"));
        mockWebServer.enqueue(new MockResponse()
                .setBody(objectMapper.writeValueAsString(deleteResponse))
                .addHeader("Content-Type", "application/json"));

        // When
        restTemplate.delete("/api/v1/employee/" + employeeId);

        // Then
        RecordedRequest getRequest = mockWebServer.takeRequest(1, TimeUnit.SECONDS);
        RecordedRequest deleteRequest = mockWebServer.takeRequest(1, TimeUnit.SECONDS);
        
        assertNotNull(getRequest);
        assertNotNull(deleteRequest);
        assertEquals("GET", getRequest.getMethod());
        assertEquals("DELETE", deleteRequest.getMethod());
    }

    @Test
    void handleRateLimiting_shouldRetryRequest() throws Exception {
        // Given
        List<Employee> employees = Arrays.asList(createEmployee("John Doe"));
        MockApiResponse<List<Employee>> mockResponse = new MockApiResponse<>(employees, "Success", null);

        // First request returns 429 (Too Many Requests), second succeeds
        mockWebServer.enqueue(new MockResponse().setResponseCode(429));
        mockWebServer.enqueue(new MockResponse()
                .setBody(objectMapper.writeValueAsString(mockResponse))
                .addHeader("Content-Type", "application/json"));

        // When
        ResponseEntity<List<Employee>> response = restTemplate.exchange(
                "/api/v1/employee", HttpMethod.GET, null, new ParameterizedTypeReference<List<Employee>>() {});

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        // Should have made 2 requests (original + retry)
        assertEquals(2, mockWebServer.getRequestCount());
    }

    @Test
    void handleServiceUnavailable_shouldRetryRequest() throws Exception {
        // Given
        List<Employee> employees = Arrays.asList(createEmployee("John Doe"));
        MockApiResponse<List<Employee>> mockResponse = new MockApiResponse<>(employees, "Success", null);

        // First request returns 503 (Service Unavailable), second succeeds
        mockWebServer.enqueue(new MockResponse().setResponseCode(503));
        mockWebServer.enqueue(new MockResponse()
                .setBody(objectMapper.writeValueAsString(mockResponse))
                .addHeader("Content-Type", "application/json"));

        // When
        ResponseEntity<List<Employee>> response = restTemplate.exchange(
                "/api/v1/employee", HttpMethod.GET, null, new ParameterizedTypeReference<List<Employee>>() {});

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(2, mockWebServer.getRequestCount());
    }

    @Test
    void handleMalformedJsonResponse_shouldHandleGracefully() throws Exception {
        // Given
        mockWebServer.enqueue(new MockResponse()
                .setBody("{ invalid json }")
                .addHeader("Content-Type", "application/json"));

        // When
        ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/employee", String.class);

        // Then
        // Should handle the error gracefully
        assertTrue(response.getStatusCode().is5xxServerError() || response.getStatusCode().is4xxClientError());
    }

    @Test
    void handleSlowResponse_shouldTimeout() throws Exception {
        // Given
        mockWebServer.enqueue(new MockResponse()
                .setBody("{}")
                .setBodyDelay(10, TimeUnit.SECONDS)); // Longer than our timeout

        // When
        ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/employee", String.class);

        // Then
        // Should timeout and return error status
        assertTrue(response.getStatusCode().is5xxServerError());
    }

    @Test
    void handleLargeResponse_shouldProcessCorrectly() throws Exception {
        // Given
        List<Employee> largeEmployeeList = createLargeEmployeeList(1000);
        MockApiResponse<List<Employee>> mockResponse = new MockApiResponse<>(largeEmployeeList, "Success", null);

        mockWebServer.enqueue(new MockResponse()
                .setBody(objectMapper.writeValueAsString(mockResponse))
                .addHeader("Content-Type", "application/json"));

        // When
        ResponseEntity<List<Employee>> response = restTemplate.exchange(
                "/api/v1/employee", HttpMethod.GET, null, new ParameterizedTypeReference<List<Employee>>() {});

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1000, response.getBody().size());
    }

    @Test
    void handleSpecialCharactersInEmployeeData() throws Exception {
        // Given
        List<Employee> employees = Arrays.asList(
                createEmployeeWithSpecialName("José María García-López"),
                createEmployeeWithSpecialName("田中太郎"),
                createEmployeeWithSpecialName("François O'Connor")
        );
        MockApiResponse<List<Employee>> mockResponse = new MockApiResponse<>(employees, "Success", null);

        mockWebServer.enqueue(new MockResponse()
                .setBody(objectMapper.writeValueAsString(mockResponse))
                .addHeader("Content-Type", "application/json"));

        // When
        ResponseEntity<List<Employee>> response = restTemplate.exchange(
                "/api/v1/employee/search/José", 
                HttpMethod.GET, 
                null, 
                new ParameterizedTypeReference<List<Employee>>() {});

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        assertEquals("José María García-López", response.getBody().get(0).getName());
    }

    // Helper methods
    private Employee createEmployee(String name) {
        return Employee.builder()
                .id(UUID.randomUUID())
                .name(name)
                .salary(50000)
                .age(30)
                .title("Developer")
                .email(name.toLowerCase().replace(" ", "") + "@company.com")
                .build();
    }

    private Employee createEmployeeWithSalary(String name, int salary) {
        return Employee.builder()
                .id(UUID.randomUUID())
                .name(name)
                .salary(salary)
                .age(30)
                .title("Developer")
                .email(name.toLowerCase().replace(" ", "") + "@company.com")
                .build();
    }

    private Employee createEmployeeWithSpecialName(String name) {
        return Employee.builder()
                .id(UUID.randomUUID())
                .name(name)
                .salary(50000)
                .age(30)
                .title("Developer")
                .email("employee@company.com")
                .build();
    }

    private List<Employee> createMultipleEmployeesWithDifferentSalaries() {
        return Arrays.asList(
                createEmployeeWithSalary("Employee 1", 50000),
                createEmployeeWithSalary("Employee 2", 55000),
                createEmployeeWithSalary("Employee 3", 60000),
                createEmployeeWithSalary("Employee 4", 65000),
                createEmployeeWithSalary("Employee 5", 70000),
                createEmployeeWithSalary("Employee 6", 75000),
                createEmployeeWithSalary("Employee 7", 80000),
                createEmployeeWithSalary("Employee 8", 85000),
                createEmployeeWithSalary("Employee 9", 90000),
                createEmployeeWithSalary("Employee 10", 95000),
                createEmployeeWithSalary("Employee 11", 100000),
                createEmployeeWithSalary("Employee 12", 120000)
        );
    }

    private List<Employee> createLargeEmployeeList(int size) {
        return java.util.stream.IntStream.range(0, size)
                .mapToObj(i -> createEmployeeWithSalary("Employee " + i, 50000 + i))
                .toList();
    }
}
