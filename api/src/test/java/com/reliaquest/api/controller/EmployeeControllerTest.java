package com.reliaquest.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reliaquest.api.exception.EmployeeNotFoundException;
import com.reliaquest.api.model.CreateEmployeeInput;
import com.reliaquest.api.model.Employee;
import com.reliaquest.api.service.EmployeeService;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import reactor.core.publisher.Mono;

@WebMvcTest(EmployeeController.class)
class EmployeeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private EmployeeService employeeService;

    @Test
    void getAllEmployees_shouldReturnListOfEmployees() throws Exception {
        List<Employee> employees = Arrays.asList(createEmployee("John Doe"), createEmployee("Jane Smith"));

        when(employeeService.getAllEmployees()).thenReturn(Mono.just(employees));

        mockMvc.perform(get("/api/v1/employee"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].employee_name").value("John Doe"))
                .andExpect(jsonPath("$[1].employee_name").value("Jane Smith"));
    }

    @Test
    void getEmployeeById_shouldReturnEmployee() throws Exception {
        String id = UUID.randomUUID().toString();
        Employee employee = createEmployee("John Doe");

        when(employeeService.getEmployeeById(id)).thenReturn(Mono.just(employee));

        mockMvc.perform(get("/api/v1/employee/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employee_name").value("John Doe"));
    }

    @Test
    void getEmployeeById_shouldReturn404_whenNotFound() throws Exception {
        String id = UUID.randomUUID().toString();

        when(employeeService.getEmployeeById(id))
                .thenReturn(Mono.error(new EmployeeNotFoundException("Employee not found")));

        mockMvc.perform(get("/api/v1/employee/{id}", id)).andExpect(status().isNotFound());
    }

    @Test
    void searchEmployeesByName_shouldReturnFilteredEmployees() throws Exception {
        List<Employee> employees = Arrays.asList(createEmployee("John Doe"), createEmployee("John Smith"));

        when(employeeService.searchEmployeesByName("John")).thenReturn(Mono.just(employees));

        mockMvc.perform(get("/api/v1/employee/search/{searchString}", "John"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].employee_name").value("John Doe"))
                .andExpect(jsonPath("$[1].employee_name").value("John Smith"));
    }

    @Test
    void getHighestSalary_shouldReturnHighestSalary() throws Exception {
        when(employeeService.getHighestSalary()).thenReturn(Mono.just(100000));

        mockMvc.perform(get("/api/v1/employee/highestSalary"))
                .andExpect(status().isOk())
                .andExpect(content().string("100000"));
    }

    @Test
    void getTopTenHighestEarningEmployeeNames_shouldReturnNamesList() throws Exception {
        List<String> names = Arrays.asList("Employee 1", "Employee 2", "Employee 3");

        when(employeeService.getTopTenHighestEarningEmployeeNames()).thenReturn(Mono.just(names));

        mockMvc.perform(get("/api/v1/employee/topTenHighestEarningEmployeeNames"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("Employee 1"))
                .andExpect(jsonPath("$[1]").value("Employee 2"))
                .andExpect(jsonPath("$[2]").value("Employee 3"));
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

        when(employeeService.createEmployee(any(CreateEmployeeInput.class))).thenReturn(Mono.just(createdEmployee));

        mockMvc.perform(post("/api/v1/employee")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(input)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.employee_name").value("New Employee"));
    }

    @Test
    void createEmployee_shouldReturnBadRequest_whenValidationFails() throws Exception {
        CreateEmployeeInput input = CreateEmployeeInput.builder()
                .name("")
                .salary(-1000)
                .age(10)
                .title("")
                .build();

        mockMvc.perform(post("/api/v1/employee")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(input)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteEmployeeById_shouldReturnEmployeeName() throws Exception {
        String id = UUID.randomUUID().toString();

        when(employeeService.deleteEmployeeById(id)).thenReturn(Mono.just("John Doe"));

        mockMvc.perform(delete("/api/v1/employee/{id}", id))
                .andExpect(status().isOk())
                .andExpect(content().string("John Doe"));
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
