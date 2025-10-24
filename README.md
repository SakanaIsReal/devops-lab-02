# SmartSplit - Expense Splitting Application

![SmartSplit](https://img.shields.io/badge/version-1.0.0-blue)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.5-green)
![React](https://img.shields.io/badge/React-19-blue)
![Java](https://img.shields.io/badge/Java-17-orange)
![MySQL](https://img.shields.io/badge/MySQL-8-blue)

SmartSplit is a full-stack web application for managing and splitting expenses among groups. Built with modern technologies and designed for easy deployment across multiple environments.

## Table of Contents

- [Features](#features)
- [Tech Stack](#tech-stack)
- [Project Architecture](#project-architecture)
- [Quick Start](#quick-start)
  - [Prerequisites](#prerequisites)
  - [Development Setup Options](#development-setup-options)
- [Development Workflows](#development-workflows)
- [Testing](#testing)
- [Deployment](#deployment)
- [API Documentation](#api-documentation)
- [Environment Variables](#environment-variables)
- [Troubleshooting](#troubleshooting)
- [Contributing](#contributing)

## Features

- User authentication with JWT tokens
- Create and manage groups
- Add expenses with equal or manual split
- Track payments and settlements
- Profile management with avatar upload
- Real-time expense calculations
- Responsive design with mobile support

## Tech Stack

### Backend
- **Framework**: Spring Boot 3.5.5
- **Language**: Java 17
- **Database**: MySQL 8
- **Authentication**: JWT (JSON Web Tokens)
- **ORM**: Spring Data JPA with Hibernate
- **Database Migrations**: Flyway
- **Testing**: JUnit 5, Testcontainers
- **Build Tool**: Maven
- **API Documentation**: OpenAPI/Swagger

### Frontend
- **Framework**: React 19
- **Language**: TypeScript
- **Styling**: TailwindCSS
- **Routing**: React Router v7
- **State Management**: React Context API
- **HTTP Client**: Axios
- **Testing**: Jest, React Testing Library, Cypress

### Infrastructure
- **Containerization**: Docker
- **Orchestration**: Kubernetes (Minikube)
- **CI/CD**: GitHub Actions
- **Container Registry**: Docker Hub

## Project Architecture

### Directory Structure

```
devops-lab-02/
├── backend/                    # Spring Boot application
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/smartsplit/smartsplitback/
│   │   │   │   ├── controller/     # REST API endpoints
│   │   │   │   ├── service/        # Business logic
│   │   │   │   ├── repository/     # JPA repositories
│   │   │   │   ├── model/          # Entities and DTOs
│   │   │   │   ├── security/       # JWT & Spring Security
│   │   │   │   └── config/         # Configuration classes
│   │   │   └── resources/
│   │   │       └── db/migration/   # Flyway SQL migrations
│   │   └── test/                   # Unit and integration tests
│   ├── Dockerfile
│   └── pom.xml
│
├── frontend/                   # React application
│   ├── src/
│   │   ├── pages/              # Page components
│   │   ├── components/         # Reusable UI components
│   │   ├── contexts/           # React Context providers
│   │   ├── utils/              # Utility functions
│   │   └── types/              # TypeScript type definitions
│   ├── Dockerfile
│   ├── nginx.conf              # Nginx configuration for production
│   └── package.json
│
├── k8s/                        # Kubernetes manifests
│   ├── namespace.yaml
│   ├── configmap.yaml
│   ├── secrets.yaml
│   ├── mysql/
│   ├── backend/
│   ├── frontend/
│   └── ingress.yaml
│
├── cypress/                    # E2E tests
│   └── e2e/
│
├── scripts/                    # Utility scripts
│   ├── start-port-forward.ps1
│   └── stop-port-forward.ps1
│
├── .github/workflows/          # CI/CD pipelines
├── docker-compose.yml          # Full stack Docker Compose
├── docker-compose.dev.yml      # Development Docker Compose
├── deploy-minikube.ps1         # Minikube deployment script
└── .env                        # Environment variables
```

### Authentication Flow

1. User signs up or logs in via `/api/auth/signup` or `/api/auth/login`
2. Backend validates credentials and returns JWT token
3. Frontend stores token in localStorage via AuthContext
4. Subsequent requests include token in Authorization header
5. JwtAuthFilter validates token on each protected endpoint
6. Token expires after configured time (default: 24 hours)

## Quick Start

### Prerequisites

Install the following tools based on your chosen deployment method:

**For All Setups:**
- [Git](https://git-scm.com/)
- [Docker Desktop](https://www.docker.com/products/docker-desktop/) (required for Windows/Mac)

**For Hybrid Development (Recommended):**
- [Node.js 18+](https://nodejs.org/) and npm
- Docker Desktop

**For Full Local Development:**
- [Java 17 JDK](https://adoptium.net/)
- [Maven 3.8+](https://maven.apache.org/download.cgi)
- [Node.js 18+](https://nodejs.org/) and npm
- [MySQL 8](https://dev.mysql.com/downloads/) (or Docker)

**For Kubernetes Deployment:**
- [Minikube](https://minikube.sigs.k8s.io/docs/start/)
- [kubectl](https://kubernetes.io/docs/tasks/tools/)
- Docker Desktop

### Clone the Repository

```bash
git clone <repository-url>
cd devops-lab-02
```

### Configure Environment Variables

Ensure your `.env` file exists at the project root:

```env
# Database Configuration
MYSQL_ROOT_PASSWORD=gg
MYSQL_DATABASE=smartsplit-db

# JWT Configuration
APP_JWT_SECRET=<your-long-secure-secret-key>
APP_JWT_EXPIRATION_SECONDS=86400

# Optional: Host Ports
APP_HOST_PORT=8080
MYSQL_HOST_PORT=3306

# Optional: Spring Datasource (for local backend development)
SPRING_DATASOURCE_URL=jdbc:mysql://localhost:8082/smartsplit-db
SPRING_DATASOURCE_USERNAME=root
SPRING_DATASOURCE_PASSWORD=gg
```

**Note:** The `.env` file is already configured in your project.

## Development Setup Options

Choose the setup that best fits your development workflow:

### Option 1: Hybrid Development (Frontend Local + Backend/DB Docker) ⭐ RECOMMENDED

**Best for:** Frontend development with hot-reload capability

**Advantages:**
- Instant hot-reload for frontend changes
- No need to rebuild Docker images for CSS/React changes
- Backend and database run in consistent containerized environment
- Lightweight resource usage
- Easy debugging with browser DevTools

**Setup:**

1. **Start Backend and Database:**
   ```powershell
   docker-compose -f docker-compose.dev.yml up -d
   ```

2. **Verify services are running:**
   ```powershell
   docker-compose -f docker-compose.dev.yml ps
   ```

3. **Start Frontend locally:**
   ```powershell
   cd frontend
   npm install          # First time only
   npm start
   ```

4. **Access the application:**
   - Frontend: http://localhost:3000 (with hot-reload)
   - Backend API: http://localhost:8081/api
   - MySQL: localhost:8082

5. **Stop services:**
   ```powershell
   # Stop frontend: Ctrl+C in terminal

   # Stop backend and database:
   docker-compose -f docker-compose.dev.yml down
   ```

**How it works:**
- Frontend (port 3000) proxies API calls to backend (port 8081)
- Proxy configured in `frontend/package.json`: `"proxy": "http://localhost:8081"`
- CORS is already configured in backend to accept requests from localhost:3000

### Option 2: Full Docker Compose (All Services Containerized)

**Best for:** Testing full stack integration, minimal local setup

**Advantages:**
- Everything runs in containers
- Matches production environment closely
- One command to start/stop everything
- No need for local Java/Node.js installation
- Isolated networking

**Setup:**

1. **Start all services:**
   ```powershell
   docker-compose up -d
   ```
   Or with logs:
   ```powershell
   docker-compose up
   ```

2. **Access the application:**
   - Frontend: http://localhost:8080
   - Backend API: http://localhost:8081/api
   - MySQL: localhost:8082

3. **View logs:**
   ```powershell
   docker-compose logs -f backend
   docker-compose logs -f frontend
   ```

4. **Rebuild after code changes:**
   ```powershell
   docker-compose up --build
   ```

5. **Stop services:**
   ```powershell
   docker-compose down
   ```

6. **Clean up (remove volumes):**
   ```powershell
   docker-compose down -v
   ```

### Option 3: Full Local Development (No Docker)

**Best for:** Backend development, debugging Java code, learning Spring Boot

**Setup:**

1. **Start MySQL** (using Docker or local installation):
   ```powershell
   # Using Docker:
   docker run -d -p 8082:3306 `
     -e MYSQL_ROOT_PASSWORD=gg `
     -e MYSQL_DATABASE=smartsplit-db `
     --name smartsplit-mysql `
     mysql:8
   ```

2. **Start Backend:**
   ```powershell
   cd backend

   # Set environment variables (PowerShell)
   $env:SPRING_DATASOURCE_URL="jdbc:mysql://localhost:8082/smartsplit-db"
   $env:SPRING_DATASOURCE_USERNAME="root"
   $env:SPRING_DATASOURCE_PASSWORD="gg"
   $env:APP_JWT_SECRET="<your-jwt-secret>"
   $env:APP_JWT_EXPIRATION_SECONDS="86400"

   # Run backend
   mvn spring-boot:run
   ```

3. **Start Frontend:**
   ```powershell
   cd frontend
   npm install          # First time only
   npm start
   ```

4. **Access the application:**
   - Frontend: http://localhost:3000
   - Backend API: http://localhost:8081/api

### Option 4: Kubernetes with Minikube

**Best for:** Testing production deployment, learning Kubernetes

**Setup:**

1. **Start Minikube:**
   ```powershell
   minikube start
   ```

2. **Deploy application:**
   ```powershell
   # Windows
   .\deploy-minikube.ps1

   # Linux/Mac
   ./deploy-minikube.sh
   ```

3. **Get Minikube IP:**
   ```powershell
   minikube ip
   ```

4. **Access the application:**
   - Frontend: http://<minikube-ip>
   - Backend API: http://<minikube-ip>/api

5. **Enable port forwarding** (for localhost access):
   ```powershell
   .\scripts\start-port-forward.ps1
   ```
   Then access:
   - Frontend: http://localhost:3003
   - Backend: http://localhost:16048

6. **Stop port forwarding:**
   ```powershell
   .\scripts\stop-port-forward.ps1
   ```

7. **View pod status:**
   ```powershell
   kubectl get pods -n smartsplit
   ```

8. **View logs:**
   ```powershell
   kubectl logs -n smartsplit <pod-name>
   ```

## Development Workflows

### Frontend Development Workflow

1. Start backend and database:
   ```powershell
   docker-compose -f docker-compose.dev.yml up -d
   ```

2. Start frontend with hot-reload:
   ```powershell
   cd frontend
   npm start
   ```

3. Make changes to React components - see changes instantly in browser

4. Run tests:
   ```powershell
   npm test
   ```

### Backend Development Workflow

1. Start MySQL:
   ```powershell
   docker run -d -p 8082:3306 -e MYSQL_ROOT_PASSWORD=gg -e MYSQL_DATABASE=smartsplit-db --name smartsplit-mysql mysql:8
   ```

2. Run backend with Maven:
   ```powershell
   cd backend
   mvn spring-boot:run
   ```

3. Make changes to Java code - Spring Boot DevTools will auto-reload

4. Run tests:
   ```powershell
   # Unit tests only
   mvn test -DskipITs=true

   # Integration tests only
   mvn verify -DskipTests

   # All tests
   mvn verify
   ```

### Making Database Changes

1. Create new Flyway migration in `backend/src/main/resources/db/migration/`
   - Follow naming: `V<version>__<description>.sql`
   - Example: `V5__add_user_avatar_column.sql`

2. Restart backend - Flyway will apply migration automatically

3. Verify migration:
   ```powershell
   # Check flyway_schema_history table
   docker exec -it smartsplit-mysql-dev mysql -uroot -pgg smartsplit-db -e "SELECT * FROM flyway_schema_history;"
   ```

## Testing

### Backend Testing

**Unit Tests:**
```powershell
cd backend
mvn test -DskipITs=true
```

**Integration Tests** (using Testcontainers):
```powershell
mvn verify -DskipTests
```

**All Tests:**
```powershell
mvn verify
```

**Run specific test:**
```powershell
mvn test -Dtest=UserServiceTest
```

**Code Coverage** (JaCoCo):
```powershell
mvn verify
# Report: backend/target/site/jacoco/index.html
```

### Frontend Testing

**Unit Tests** (Jest + React Testing Library):
```powershell
cd frontend
npm test
```

**Run tests in CI mode:**
```powershell
npm test -- --watchAll=false
```

**E2E Tests** (Cypress):

1. Start full application:
   ```powershell
   docker-compose up -d
   ```

2. Run Cypress tests:
   ```powershell
   # Headless mode
   npm run test:e2e

   # Interactive mode
   npm run cypress:open
   ```

**E2E Test Coverage:**
- User authentication (signup, login, logout)
- Profile management and editing
- Group CRUD operations
- Expense creation (equal split and manual split)
- Payment processing and verification

## Deployment

### Docker Image Tagging Strategy

**Production (CI/CD):**
- Images tagged with both `:latest` and `:${git-sha}`
- Deployments use `:${git-sha}` for immutability and audit trail
- Automatic builds and pushes on main branch via GitHub Actions

**Local Development:**
- Uses `:latest` tag by default
- Custom tags supported:
  ```powershell
  .\deploy-minikube.ps1 -ImageTag "v1.2.3"
  ```

### CI/CD Pipeline

The project uses GitHub Actions with four stages:

1. **Test**: Runs backend unit tests and installs frontend dependencies
2. **E2E-Test**: Runs Cypress end-to-end tests with full stack
3. **Build-and-Push**: Builds and pushes Docker images to Docker Hub
4. **Deploy**: Updates Kubernetes deployments with new images

**Required GitHub Secrets:**
- `DOCKERHUB_USERNAME`
- `DOCKERHUB_TOKEN`
- `APP_JWT_SECRET`

### Manual Deployment to Kubernetes

**Apply all manifests:**
```powershell
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/configmap.yaml
kubectl apply -f k8s/secrets.yaml
kubectl apply -f k8s/mysql/
kubectl apply -f k8s/backend/
kubectl apply -f k8s/frontend/
kubectl apply -f k8s/ingress.yaml
```

**Rollback deployment:**
```powershell
kubectl rollout undo deployment/backend -n smartsplit
kubectl rollout undo deployment/frontend -n smartsplit
```

**View deployment history:**
```powershell
kubectl rollout history deployment/backend -n smartsplit
```

## API Documentation

### Accessing Swagger UI

When backend is running, access API documentation at:
- http://localhost:8081/swagger-ui/index.html

### Key API Endpoints

**Authentication:**
- `POST /api/auth/signup` - Register new user
- `POST /api/auth/login` - Login and get JWT token

**Users:**
- `GET /api/users/me` - Get current user profile
- `PUT /api/users/me` - Update current user profile
- `POST /api/users/me/avatar` - Upload avatar image

**Groups:**
- `GET /api/groups` - List user's groups
- `POST /api/groups` - Create new group
- `GET /api/groups/{id}` - Get group details
- `PUT /api/groups/{id}` - Update group
- `DELETE /api/groups/{id}` - Delete group

**Expenses:**
- `GET /api/groups/{groupId}/expenses` - List group expenses
- `POST /api/groups/{groupId}/expenses` - Create expense
- `GET /api/expenses/{id}` - Get expense details
- `PUT /api/expenses/{id}` - Update expense
- `DELETE /api/expenses/{id}` - Delete expense

**Payments:**
- `POST /api/payments` - Record payment
- `GET /api/groups/{groupId}/payments` - List group payments

### Authentication

All protected endpoints require JWT token in header:
```
Authorization: Bearer <token>
```

## Environment Variables

### Backend Environment Variables

| Variable | Description | Default | Required |
|----------|-------------|---------|----------|
| `SPRING_PROFILES_ACTIVE` | Active Spring profile | `prod` | Yes |
| `SPRING_DATASOURCE_URL` | JDBC connection URL | - | Yes |
| `SPRING_DATASOURCE_USERNAME` | Database username | `root` | Yes |
| `SPRING_DATASOURCE_PASSWORD` | Database password | - | Yes |
| `APP_JWT_SECRET` | Secret key for JWT signing | - | Yes |
| `APP_JWT_EXPIRATION_SECONDS` | Token expiration time | `86400` (24h) | No |
| `SPRING_SERVLET_MULTIPART_MAX_FILE_SIZE` | Max file upload size | `200MB` | No |

### Frontend Environment Variables

The frontend uses the proxy configuration in `package.json` to communicate with the backend. No additional environment variables are required for local development.

### Docker Compose Environment Variables

Defined in `.env` file:

| Variable | Description | Example |
|----------|-------------|---------|
| `MYSQL_ROOT_PASSWORD` | MySQL root password | `gg` |
| `MYSQL_DATABASE` | Database name | `smartsplit-db` |
| `APP_JWT_SECRET` | JWT secret key | `<long-random-string>` |
| `APP_JWT_EXPIRATION_SECONDS` | Token expiration | `86400` |

## Troubleshooting

### Common Issues

**1. Port Already in Use**

Error: `Bind for 0.0.0.0:8081 failed: port is already allocated`

Solution:
```powershell
# Find process using the port
netstat -ano | findstr :8081

# Kill the process
taskkill /PID <process-id> /F

# Or change port in docker-compose.yml
```

**2. Frontend Cannot Connect to Backend**

Symptoms: API calls fail, CORS errors, network errors

Solutions:
- Verify backend is running: http://localhost:8081/actuator/health
- Check frontend proxy in `package.json`: should be `"proxy": "http://localhost:8081"`
- Verify CORS configuration in `backend/src/main/java/.../config/CorsConfig.java`
- Check browser console for specific errors

**3. Database Connection Errors**

Symptoms: Backend fails to start, `Communications link failure`

Solutions:
- Verify MySQL is running:
  ```powershell
  docker ps | findstr mysql
  ```
- Check MySQL health:
  ```powershell
  docker logs smartsplit-mysql-dev
  ```
- Verify connection string in `.env` matches MySQL port
- Wait for MySQL healthcheck to pass (can take 30+ seconds)

**4. Docker Build Fails**

Solutions:
- Clear Docker cache:
  ```powershell
  docker builder prune
  docker-compose build --no-cache
  ```
- Ensure Docker Desktop is running and has enough resources
- Check Dockerfile syntax

**5. Minikube Deployment Issues**

Solutions:
- Verify Minikube is running:
  ```powershell
  minikube status
  ```
- Check pod status:
  ```powershell
  kubectl get pods -n smartsplit
  ```
- View pod logs:
  ```powershell
  kubectl logs -n smartsplit <pod-name>
  ```
- Describe pod for events:
  ```powershell
  kubectl describe pod -n smartsplit <pod-name>
  ```

**6. Tests Failing**

Backend tests:
- Ensure Docker is running (for Testcontainers)
- Check test logs: `backend/target/surefire-reports/`
- Run individual test to isolate issue

Frontend tests:
- Clear node_modules: `rm -rf node_modules && npm install`
- Check test configuration in `package.json`

E2E tests:
- Ensure application is running on correct ports
- Check Cypress base URL in `cypress.config.js`
- View screenshots: `cypress/screenshots/`
- View videos: `cypress/videos/`

**7. Hot Reload Not Working**

Solutions:
- Restart development server
- Clear browser cache
- Check for errors in browser console
- Verify no syntax errors in code
- On Windows, ensure file watching is enabled:
  ```powershell
  npm start -- --no-cache
  ```

### Getting Help

- Check [CLAUDE.md](CLAUDE.md) for detailed project instructions
- Review API documentation at http://localhost:8081/swagger-ui/index.html
- Check Docker logs: `docker logs <container-name>`
- Check Kubernetes logs: `kubectl logs -n smartsplit <pod-name>`

## Contributing

### Branch Strategy

- `main` - Production branch (triggers CI/CD)
- `develop` - Development branch (default for PRs)
- Feature branches: `feature/<feature-name>`
- Bug fix branches: `fix/<bug-name>`

### Commit Message Format

Follow conventional commits:
```
type(scope): description

[optional body]

[optional footer]
```

Types: `feat`, `fix`, `docs`, `style`, `refactor`, `test`, `chore`

Example:
```
feat(auth): add password reset functionality

Implemented email-based password reset flow with token expiration.

Closes #123
```

### Pull Request Process

1. Create feature branch from `develop`
2. Make changes and commit with descriptive messages
3. Write tests for new features
4. Ensure all tests pass: `mvn verify` and `npm test`
5. Update documentation if needed
6. Submit PR to `develop` branch
7. Wait for CI/CD pipeline to pass
8. Request review from team members

## License

This project is licensed under the MIT License.

## Authors

- SmartSplit Development Team

## Acknowledgments

- Spring Boot for robust backend framework
- React for modern frontend development
- Docker and Kubernetes for containerization and orchestration
- TailwindCSS for beautiful UI components