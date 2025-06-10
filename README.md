# Employee Management API

A Spring Boot REST API that provides employee management capabilities with integration to an external mock employee service.

## Prerequisites

- Java 17 (Amazon Corretto recommended)
- Gradle 7.6+
- Port 8111 (API) and 8112 (Mock Server) available

## Getting Started

### 1. Start the Mock Employee Server
```bash
cd server
./gradlew bootRun
```
The mock server will start on http://localhost:8112

### 2. Start the API Application
```bash
cd api
./gradlew bootRun
```
The API will be available at http://localhost:8111

### 3. Run Tests
```bash
./gradlew test
```

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/employee` | Get all employees |
| GET | `/api/v1/employee/{id}` | Get employee by ID |
| GET | `/api/v1/employee/search/{searchString}` | Search employees by name |
| GET | `/api/v1/employee/highestSalary` | Get highest salary |
| GET | `/api/v1/employee/topTenHighestEarningEmployeeNames` | Get top 10 earners |
| POST | `/api/v1/employee` | Create new employee |
| DELETE | `/api/v1/employee/{id}` | Delete employee |

## Example Requests

### Get All Employees
```bash
curl http://localhost:8111/api/v1/employee
```

### Create Employee
```bash
curl -X POST http://localhost:8111/api/v1/employee \
  -H "Content-Type: application/json" \
  -d '{
    "name": "John Doe",
    "salary": 75000,
    "age": 30,
    "title": "Software Engineer"
  }'
```

### Search by Name
```bash
curl http://localhost:8111/api/v1/employee/search/John
```

## Request/Response Models

### Create Employee Request
```json
{
  "name": "string",     // Required, non-empty
  "salary": 0,          // Required, positive number
  "age": 0,             // Required, 16-75
  "title": "string"     // Required, non-empty
}
```

### Employee Response
```json
{
  "id": "uuid",
  "name": "string",
  "salary": 0,
  "age": 0,
  "title": "string",
  "email": "string"
}
```

## Error Responses

| Status Code | Description |
|-------------|-------------|
| 400 | Bad Request - Validation error |
| 404 | Not Found - Employee not found |
| 503 | Service Unavailable - External API error |

## Configuration

Key configurations in `application.yml`:

```yaml
mock-employee-api:
  base-url: http://localhost:8112/api/v1/employee
  
resilience:
  retry:
    max-attempts: 3
    wait-duration: 1s
```

## Features

- **Reactive Programming**: Built with Spring WebFlux for non-blocking I/O
- **Resilience**: Retry mechanism with exponential backoff for external API calls
- **Validation**: Input validation using Jakarta Bean Validation
- **Error Handling**: Global exception handler with meaningful error messages

## Postman Collection

Import the `Employee_API.postman_collection.json` file into Postman to test all endpoints.

## Notes

- The mock employee server has rate limiting, so the API includes retry logic
- All monetary values are in cents (integer representation)
- Employee IDs are UUIDs
- Search is case-insensitive and matches partial names

## Future Enhancements

### 1. Containerization
- **Docker Support**: Add Dockerfile and docker-compose.yml for easy deployment
- **Kubernetes Ready**: Create Helm charts for K8s deployment
- **Container Registry**: Push images to Docker Hub or private registry

### 2. Database Integration
- **Caching Layer**: Add Redis for caching frequently accessed data
- **Persistent Storage**: Integrate PostgreSQL/MongoDB for data persistence
- **Event Sourcing**: Implement event-driven architecture with Kafka

### 3. API Enhancements
- **Pagination**: Add pagination support for large datasets
- **Filtering & Sorting**: Advanced query parameters for data filtering
- **GraphQL**: Alternative GraphQL endpoint for flexible queries
- **API Versioning**: Implement proper versioning strategy (v1, v2)

### 4. Security Improvements
- **Authentication**: Add JWT-based authentication
- **OAuth2**: Integrate with OAuth2 providers
- **API Rate Limiting**: Implement rate limiting per client
- **CORS Configuration**: Configurable CORS policies

### 5. Observability
- **Metrics**: Integrate Prometheus/Grafana for monitoring
- **Distributed Tracing**: Add Zipkin/Jaeger for request tracing
- **Health Checks**: Enhanced health check endpoints
- **Logging**: Centralized logging with ELK stack

### 6. Performance Optimization
- **Connection Pooling**: Optimize WebClient connection pools
- **Response Compression**: Enable GZIP compression
- **Reactive Streams**: Full reactive pipeline end-to-end
- **Circuit Breaker Dashboard**: Hystrix dashboard for monitoring

### 7. Development Experience
- **OpenAPI/Swagger**: Auto-generated API documentation
- **Hot Reload**: Spring Boot DevTools for development
- **API Client SDK**: Generate client libraries for different languages
- **Integration Tests**: Testcontainers for integration testing

## Scalability Options

### Horizontal Scaling
- **Load Balancing**: Deploy multiple instances behind a load balancer
- **Service Mesh**: Integrate with Istio/Linkerd for microservices
- **Auto-scaling**: Configure HPA (Horizontal Pod Autoscaler) in K8s

### Vertical Scaling
- **JVM Tuning**: Optimize heap size and GC settings
- **Resource Limits**: Configure CPU/Memory limits appropriately
- **Thread Pool Tuning**: Optimize Netty event loop threads

### Data Scaling
- **Read Replicas**: Distribute read load across replicas
- **Sharding**: Partition data for large-scale deployments
- **CDN Integration**: Cache static responses at edge locations

## Quick Start - Docker (Future)

```bash
# Build Docker image
docker build -t employee-api:latest .

# Run with Docker Compose
docker-compose up -d

# Scale horizontally
docker-compose up -d --scale api=3
```