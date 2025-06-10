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
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
class EmployeeApiIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private static MockWebServer mockWebServer;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add(
                "mock-employee-api.base-url",
                () -> String.format("http://localhost:%s/api/v1/employee", mockWebServer.getPort()));
    }

    @Test
    void getAllEmployees_shouldReturnEmployeesList() throws Exception {
        List<Employee> employees = Arrays.asList(createEmployee("John Doe"), createEmployee("Jane Smith"));
        MockApiResponse<List<Employee>> mockResponse = new MockApiResponse<>(employees, "Success", null);

        mockWebServer.enqueue(new MockResponse()
                .setBody(objectMapper.writeValueAsString(mockResponse))
                .addHeader("Content-Type", "application/json"));

        ResponseEntity<List<Employee>> response = restTemplate.exchange(
                "/api/v1/employee", HttpMethod.GET, null, new ParameterizedTypeReference<List<Employee>>() {});

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().size());
    }

    @Test
    void createEmployee_shouldReturnCreatedEmployee() throws Exception {
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

        ResponseEntity<Employee> response = restTemplate.postForEntity("/api/v1/employee", input, Employee.class);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("New Employee", response.getBody().getName());
    }

    @Test
    void handleRateLimiting_shouldRetryRequest() throws Exception {
        List<Employee> employees = Arrays.asList(createEmployee("John Doe"));
        MockApiResponse<List<Employee>> mockResponse = new MockApiResponse<>(employees, "Success", null);

        mockWebServer.enqueue(new MockResponse().setResponseCode(429));
        mockWebServer.enqueue(new MockResponse()
                .setBody(objectMapper.writeValueAsString(mockResponse))
                .addHeader("Content-Type", "application/json"));

        ResponseEntity<List<Employee>> response = restTemplate.exchange(
                "/api/v1/employee", HttpMethod.GET, null, new ParameterizedTypeReference<List<Employee>>() {});

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(2, mockWebServer.getRequestCount());
    }

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
}
