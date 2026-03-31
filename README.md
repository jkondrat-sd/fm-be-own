# Facility Management Backend (Spring Boot)

This is the backend service for the Facility Management Platform, built using **Spring Boot 3.4.0** and **Java 17 LTS**.

## Technologies
- **Java 17 (LTS)**
- **Spring Boot 3.4.0**
- **Maven** (Building)
- **PostgreSQL 16 + PostGIS 3.4** (Database)
- **Redis 7** (Caching & Real-time storage)

## Prerequisites
- Java 17+
- Maven 3.9+
- Docker & Docker Compose (for local development)

## Getting Started

### Local Setup
1. Configure your database and Redis in `src/main/resources/application.yml` or via environment variables.
2. Build the project:
   ```bash
   mvn clean install
   ```
3. Run the application:
   ```bash
   mvn spring-boot:run
   ```

### Using Docker
A `docker-compose.yml` will be provided for infrastructure (PostgreSQL, PostGIS, Redis).

## Project Structure
- `com.fm.be.common`: Shared controllers, utilities, and configurations.
- `com.fm.be.modules`: Feature modules (Auth, Attendance, Payroll, etc.)
- `com.fm.be.jobs`: Background task processors.

## API Endpoints
- **Health Check**: `GET /v1/health`
- See `context/BACKEND_CONTEXT.md` for full API reference.
