package com.reliaquest.api.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.reliaquest.api.exception.EmployeeNotFoundException;
import com.reliaquest.api.model.CreateEmployeeInput;
import com.reliaquest.api.model.Employee;
import com.reliaquest.api.service.EmployeeService;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class EmployeeApiIntegrationTest {

    @Autowired private TestRestTemplate restTemplate;
    @MockBean private EmployeeService employeeService;

    @Test
    void getAllEmployees_shouldReturnEmployeesList() {
        // Given
        List<Employee> employees = Arrays.asList(
            createEmployee("John Doe"), 
            createEmployee("Jane Smith")
        );
        when(employeeService.getAllEmployees()).thenReturn(Mono.just(employees));

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
    void getAllEmployees_shouldHandleEmptyResponse() {
        // Given
        when(employeeService.getAllEmployees()).thenReturn(Mono.just(Collections.emptyList()));

        // When
        ResponseEntity<List<Employee>> response = restTemplate.exchange(
                "/api/v1/employee", HttpMethod.GET, null, new ParameterizedTypeReference<List<Employee>>() {});

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isEmpty());
    }

    @Test
    void getEmployeeById_shouldReturnEmployee() {
        // Given
        String employeeId = UUID.randomUUID().toString();
        Employee employee = createEmployee("John Doe");
        when(employeeService.getEmployeeById(employeeId)).thenReturn(Mono.just(employee));

        // When
        ResponseEntity<Employee> response = 
                restTemplate.getForEntity("/api/v1/employee/" + employeeId, Employee.class);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("John Doe", response.getBody().getName());
    }

    @Test
    void getEmployeeById_shouldReturn404WhenNotFound() {
        // Given
        String employeeId = UUID.randomUUID().toString();
        when(employeeService.getEmployeeById(employeeId))
            .thenReturn(Mono.error(new EmployeeNotFoundException("Employee not found with id: " + employeeId)));

        // When
        ResponseEntity<String> response = 
                restTemplate.getForEntity("/api/v1/employee/" + employeeId, String.class);

        // Then
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void searchEmployeesByName_shouldReturnFilteredEmployees() {
        // Given
        List<Employee> employees = Arrays.asList(
                createEmployee("John Doe"), 
                createEmployee("John Smith")
        );
        when(employeeService.searchEmployeesByName("John")).thenReturn(Mono.just(employees));

        // When
        ResponseEntity<List<Employee>> response = restTemplate.exchange(
                "/api/v1/employee/search/John", 
                HttpMethod.GET, 
                null, 
                new ParameterizedTypeReference<List<Employee>>() {});

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().size());
        assertTrue(response.getBody().stream().allMatch(e -> e.getName().contains("John")));
    }

    @Test
    void getHighestSalary_shouldReturnCorrectValue() {
        // Given
        when(employeeService.getHighestSalary()).thenReturn(Mono.just(75000));

        // When
        ResponseEntity<Integer> response = 
                restTemplate.getForEntity("/api/v1/employee/highestSalary", Integer.class);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(75000, response.getBody());
    }

    @Test
    void getTopTenHighestEarningEmployeeNames_shouldReturnCorrectList() {
        // Given
        List<String> topEarners = Arrays.asList("Employee 12", "Employee 11", "Employee 10");
        when(employeeService.getTopTenHighestEarningEmployeeNames()).thenReturn(Mono.just(topEarners));

        // When
        ResponseEntity<List<String>> response = restTemplate.exchange(
                "/api/v1/employee/topTenHighestEarningEmployeeNames",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<String>>() {});

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(3, response.getBody().size());
        assertEquals("Employee 12", response.getBody().get(0));
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

        Employee createdEmployee = createEmployee("New Employee");
        when(employeeService.createEmployee(any(CreateEmployeeInput.class))).thenReturn(Mono.just(createdEmployee));

        // When
        ResponseEntity<Employee> response = restTemplate.postForEntity("/api/v1/employee", input, Employee.class);

        // Then
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("New Employee", response.getBody().getName());
        
        verify(employeeService).createEmployee(any(CreateEmployeeInput.class));
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

        // When
        ResponseEntity<String> response = restTemplate.postForEntity("/api/v1/employee", input, String.class);

        // Then
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        
        // Verify service was not called due to validation failure
        verify(employeeService, never()).createEmployee(any(CreateEmployeeInput.class));
    }

    @Test
    void deleteEmployeeById_shouldReturnSuccess() {
        // Given
        String employeeId = UUID.randomUUID().toString();
        when(employeeService.deleteEmployeeById(employeeId)).thenReturn(Mono.just("John Doe"));

        // When
        restTemplate.delete("/api/v1/employee/" + employeeId);

        // Then
        verify(employeeService).deleteEmployeeById(employeeId);
    }

    @Test
    void handleLargeResponse_shouldProcessCorrectly() {
        // Given
        List<Employee> largeEmployeeList = createLargeEmployeeList(1000);
        when(employeeService.getAllEmployees()).thenReturn(Mono.just(largeEmployeeList));

        // When
        ResponseEntity<List<Employee>> response = restTemplate.exchange(
                "/api/v1/employee", HttpMethod.GET, null, new ParameterizedTypeReference<List<Employee>>() {});

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1000, response.getBody().size());
    }

    @Test
    void handleSpecialCharactersInEmployeeData() {
        // Given
        List<Employee> employees = Arrays.asList(
                createEmployeeWithSpecialName("José María García-López"),
                createEmployeeWithSpecialName("田中太郎"),
                createEmployeeWithSpecialName("François O'Connor")
        );
        when(employeeService.searchEmployeesByName("José")).thenReturn(Mono.just(employees.subList(0, 1)));

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

    private List<Employee> createLargeEmployeeList(int size) {
        return java.util.stream.IntStream.range(0, size)
                .mapToObj(i -> createEmployee("Employee " + i))
                .toList();
    }
}