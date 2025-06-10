package com.reliaquest.api.controller;

import com.reliaquest.api.model.CreateEmployeeInput;
import com.reliaquest.api.model.Employee;
import com.reliaquest.api.service.EmployeeService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/employee")
@RequiredArgsConstructor
public class EmployeeController implements IEmployeeController<Employee, CreateEmployeeInput> {

    private final EmployeeService employeeService;

    @Override
    public ResponseEntity<List<Employee>> getAllEmployees() {
        log.info("GET request to fetch all employees");
        return employeeService.getAllEmployees().map(ResponseEntity::ok).block();
    }

    @Override
    public ResponseEntity<List<Employee>> getEmployeesByNameSearch(String searchString) {
        log.info("GET request to search employees by name: {}", searchString);
        return employeeService
                .searchEmployeesByName(searchString)
                .map(ResponseEntity::ok)
                .block();
    }

    @Override
    public ResponseEntity<Employee> getEmployeeById(String id) {
        log.info("GET request to fetch employee by id: {}", id);
        return employeeService.getEmployeeById(id).map(ResponseEntity::ok).block();
    }

    @Override
    public ResponseEntity<Integer> getHighestSalaryOfEmployees() {
        log.info("GET request to fetch highest salary");
        return employeeService.getHighestSalary().map(ResponseEntity::ok).block();
    }

    @Override
    public ResponseEntity<List<String>> getTopTenHighestEarningEmployeeNames() {
        log.info("GET request to fetch top 10 highest earning employees");
        return employeeService
                .getTopTenHighestEarningEmployeeNames()
                .map(ResponseEntity::ok)
                .block();
    }

    @Override
    public ResponseEntity<Employee> createEmployee(@Valid CreateEmployeeInput employeeInput) {
        log.info("POST request to create employee: {}", employeeInput);
        return employeeService
                .createEmployee(employeeInput)
                .map(employee -> ResponseEntity.status(HttpStatus.CREATED).body(employee))
                .block();
    }

    @Override
    public ResponseEntity<String> deleteEmployeeById(String id) {
        log.info("DELETE request to delete employee by id: {}", id);
        return employeeService.deleteEmployeeById(id).map(ResponseEntity::ok).block();
    }
}
