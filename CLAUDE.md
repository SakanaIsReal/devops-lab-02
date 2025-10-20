# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**SmartSplit** is a full-stack expense splitting application with:
- **Backend**: Spring Boot 3.5.5 (Java 17) REST API with JWT authentication, MySQL database, and Flyway migrations
- **Frontend**: React 19 with TypeScript, TailwindCSS, and React Router
- **Infrastructure**: Kubernetes deployment on Minikube with Docker containerization

## Core Architecture

### Backend Structure (Spring Boot)
Located in `backend/src/main/java/com/smartsplit/smartsplitback/`:
- **controller/**: REST API endpoints
- **service/**: Business logic layer
- **repository/**: JPA repository interfaces
- **model/**: JPA entities and DTOs (in `model/dto/`)
- **security/**: JWT authentication and Spring Security configuration
- **config/**: Application configuration classes
- **Database**: Flyway migrations in `backend/src/main/resources/db/migration/`

The backend uses:
- Spring Security with JWT tokens (configured via `APP_JWT_SECRET` and `APP_JWT_EXPIRATION_SECONDS`)
- MySQL 8 database with JPA/Hibernate
- Testcontainers for integration tests (files ending in `*IT.java`)
- Actuator for health checks and monitoring
- OpenAPI/Swagger UI for API documentation

### Frontend Structure (React)
Located in `frontend/src/`:
- **pages/**: Full page components (Login, SignUp, HomePage, GroupPage, BillDetail, etc.)
- **components/**: Reusable UI components (Navbar, BottomNav, FAB, TransactionCard, etc.)
- **contexts/**: React context providers (AuthContext for authentication state)
- **utils/**: Utility functions and helpers
- **types/**: TypeScript type definitions

The frontend communicates with the backend through a proxy configuration (set in `frontend/package.json` to `http://127.0.0.1:16048`).

### Authentication Flow
- JWT-based authentication managed via AuthContext
- Protected routes use `ProtectedRoute` component
- Public routes (login/signup) use `PublicRoute` component
- Tokens stored and managed client-side

## Development Commands

### Backend (from `backend/` directory)
```bash
# Run unit tests only (skip integration tests)
mvn test -DskipITs=true

# Run integration tests only (files matching *IT.java)
mvn verify -DskipTests

# Run all tests
mvn verify

# Build the application
mvn clean package

# Run locally (requires MySQL running)
mvn spring-boot:run

# Run with specific profile
SPRING_PROFILES_ACTIVE=prod mvn spring-boot:run
```

### Frontend (from `frontend/` directory)
```bash
# Install dependencies
npm install

# Start development server (port 3000)
npm start

# Build for production
npm build

# Run unit tests with Jest
npm test

# Run Cypress E2E tests (headless)
npm run test:e2e

# Open Cypress test runner
npm run cypress:open
```

### E2E Testing (from project root)
```bash
# Run Cypress tests
npx cypress run

# Open Cypress interactive mode
npx cypress open
```

Cypress is configured in `cypress.config.js` with base URL `http://localhost:3003/` (override with `CYPRESS_BASE_URL` env var).

### Docker Compose (Local Development)
```bash
# Start all services (MySQL, backend, frontend)
docker-compose up

# Start in detached mode
docker-compose up -d

# Stop all services
docker-compose down

# Rebuild and start
docker-compose up --build
```

Services exposed:
- Frontend: http://localhost:8080
- Backend API: http://localhost:8081
- MySQL: localhost:8082

### Kubernetes Deployment (Minikube)

**Linux/Mac:**
```bash
# Deploy to Minikube
./deploy-minikube.sh
```

**Windows:**
```powershell
# Deploy to Minikube
.\deploy-minikube.ps1
```

**Manual K8s operations:**
```bash
# Apply configurations
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/configmap.yaml
kubectl apply -f k8s/secrets.yaml
kubectl apply -f k8s/mysql/
kubectl apply -f k8s/backend/
kubectl apply -f k8s/frontend/
kubectl apply -f k8s/ingress.yaml

# Check pod status
kubectl get pods -n smartsplit

# View logs
kubectl logs -n smartsplit <pod-name>

# Port forwarding (scripts available in scripts/)
kubectl port-forward -n smartsplit svc/frontend 3003:80
kubectl port-forward -n smartsplit svc/backend 16048:8081
kubectl port-forward -n smartsplit svc/mysql 8082:3306
```

Port forwarding scripts in `scripts/` directory:
- `start-port-forward.ps1`: Start all port forwards
- `stop-port-forward.ps1`: Stop all port forwards
- `port-forward.ps1`: Main port forwarding script

## Environment Variables

Required environment variables (see `.env` file):
```
MYSQL_ROOT_PASSWORD=<password>
MYSQL_DATABASE=smartsplit-db
APP_JWT_SECRET=<long-secure-secret>
APP_JWT_EXPIRATION_SECONDS=86400
```

For backend tests, set:
```
SPRING_PROFILES_ACTIVE=test
APP_JWT_SECRET=<test-secret>
```

## CI/CD Pipeline

The project uses GitHub Actions (`.github/workflows/ci-cd.yml`) with three stages:

1. **Test**: Runs backend unit tests with Maven and installs frontend dependencies
2. **Build-and-Push**: Builds Docker images and pushes to Docker Hub (tagged with `:latest` and `:${git-sha}`)
3. **Deploy**: Updates Kubernetes deployments in the `smartsplit` namespace

Pipeline runs on `self-hosted` runner using PowerShell on Windows.

**Required GitHub Secrets:**
- `DOCKERHUB_USERNAME`
- `DOCKERHUB_TOKEN`
- `APP_JWT_SECRET`

## Key Technical Details

### Backend Testing Strategy
- **Unit tests**: Standard test files in `src/test/java` (run with `mvn test -DskipITs=true`)
- **Integration tests**: Files ending in `*IT.java` using Testcontainers with MySQL (run with `mvn verify`)
- **Code coverage**: JaCoCo plugin generates reports after tests

### API Base Path
The backend API uses `/api` as the base path for all endpoints (configured in Kubernetes ingress and mentioned in recent commits).

### Database Migrations
Flyway handles database schema versioning. Migration files are in `backend/src/main/resources/db/migration/` following the pattern `V<version>__<description>.sql`.

### Known Issues
- API routing may have errors when using separated ports (fixed by using reverse proxy per commit be3123c)
- Frontend proxy set to specific port (16048) for backend communication
- Cypress tests are disabled in CI pipeline (removed in commit 2a447af)

## Branch Strategy

- **main**: Production branch (triggers CI/CD deployment)
- **develop**: Development branch (current working branch)

When creating pull requests, target the `develop` branch.
