# CI/CD Process Documentation - SmartSplit

This document provides a comprehensive explanation of the CI/CD (Continuous Integration/Continuous Deployment) pipeline for the SmartSplit application.

## Table of Contents
1. [Pipeline Overview](#pipeline-overview)
2. [Pipeline Triggers](#pipeline-triggers)
3. [Pipeline Architecture](#pipeline-architecture)
4. [Stage 1: Test](#stage-1-test)
5. [Stage 2: E2E Test](#stage-2-e2e-test)
6. [Stage 3: Build and Push](#stage-3-build-and-push)
7. [Stage 4: Deploy](#stage-4-deploy)
8. [Docker Image Tagging Strategy](#docker-image-tagging-strategy)
9. [Kubernetes Deployment Architecture](#kubernetes-deployment-architecture)
10. [Environment Variables and Secrets](#environment-variables-and-secrets)
11. [Troubleshooting and Monitoring](#troubleshooting-and-monitoring)

---

## Pipeline Overview

The SmartSplit CI/CD pipeline is implemented using **GitHub Actions** and runs on a **self-hosted Windows runner**. The pipeline automates testing, building, publishing Docker images, and deploying to a Kubernetes cluster (Minikube).

**Pipeline File:** [`.github/workflows/ci-cd.yml`](.github/workflows/ci-cd.yml)

**Key Characteristics:**
- **Runner:** Self-hosted Windows machine using PowerShell
- **Container Registry:** Docker Hub
- **Deployment Target:** Kubernetes (Minikube) in the `smartsplit` namespace
- **Pipeline Stages:** 4 sequential stages (Test → E2E Test → Build & Push → Deploy)

---

## Pipeline Triggers

The pipeline is triggered on:

```yaml
on:
  push:
    branches: [ main ]
  pull_request:
    branches: [ main ]
```

- **Push to `main`:** Triggers full pipeline including deployment
- **Pull Request to `main`:** Runs tests and builds but skips deployment (deploy stage only runs on `main`)

---

## Pipeline Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                     GitHub Actions Pipeline                  │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
        ┌───────────────────────────────────────┐
        │     Stage 1: Test (Unit Tests)        │
        │  - Setup Node.js 20                   │
        │  - Install Frontend Dependencies      │
        │  - Install Cypress                    │
        │  - Run Backend Unit Tests (Maven)     │
        └───────────────────────────────────────┘
                            │
                            ▼
        ┌───────────────────────────────────────┐
        │    Stage 2: E2E Test (Integration)    │
        │  - Cleanup Previous Containers        │
        │  - Fix Windows Hyper-V Port Issues    │
        │  - Start MySQL Container (port 3307)  │
        │  - Build & Start Backend (port 16048) │
        │  - Start Frontend (port 3000)         │
        │  - Run Cypress E2E Tests              │
        │  - Upload Test Artifacts              │
        │  - Cleanup Test Environment           │
        └───────────────────────────────────────┘
                            │
                            ▼
        ┌───────────────────────────────────────┐
        │  Stage 3: Build and Push (Docker)     │
        │  - Verify Docker Running              │
        │  - Login to Docker Hub                │
        │  - Build Backend Image (dual tags)    │
        │  - Build Frontend Image (dual tags)   │
        │  - Push to Docker Hub                 │
        │    * :latest tag                      │
        │    * :${git-sha} tag                  │
        └───────────────────────────────────────┘
                            │
                            ▼
        ┌───────────────────────────────────────┐
        │    Stage 4: Deploy (Kubernetes)       │
        │  - Update Backend Deployment          │
        │  - Update Frontend Deployment         │
        │  - Patch with Timestamp Annotation    │
        │  - Wait for Rollout Completion        │
        │  - Verify Deployment Success          │
        └───────────────────────────────────────┘
```

---

## Stage 1: Test

**Job Name:** `test`
**Purpose:** Run backend unit tests and prepare frontend dependencies

### Steps:

1. **Checkout Code**
   - Uses: `actions/checkout@v4`
   - Clones the repository

2. **Setup Node.js**
   - Uses: `actions/setup-node@v3`
   - Node Version: 20

3. **Install Frontend Dependencies**
   ```powershell
   npm install --legacy-peer-deps
   npm install --save-dev --legacy-peer-deps @types/jest @testing-library/jest-dom @testing-library/react @testing-library/user-event jest
   ```
   - Uses `--legacy-peer-deps` to handle dependency conflicts

4. **Install Cypress**
   ```powershell
   npm install cypress --save-dev
   ```

5. **Run Backend Unit Tests**
   ```powershell
   cd backend
   mvn test "-Dapp.jwt.secret=$jwtSecret" -DskipITs=true
   ```
   - Runs unit tests only (skips integration tests with `-DskipITs=true`)
   - Uses `APP_JWT_SECRET` from GitHub Secrets
   - Sets `SPRING_PROFILES_ACTIVE=test`

**Exit Criteria:** All unit tests must pass

---

## Stage 2: E2E Test

**Job Name:** `e2e-test`
**Depends On:** `test`
**Purpose:** Run end-to-end tests using Cypress with a full application stack

### Steps Overview:

#### 2.1 Cleanup Previous Containers
- **Kills all Java and Node processes** from previous test runs
- **Removes `mysql-ci` container** if it exists
- **Removes `smartsplit-ci-network`** Docker network
- **Checks Windows port exclusions** (Hyper-V compatibility)
- **Kills processes** on critical ports (3307, 16048, 3000)
- **Waits for TCP connections to close** (5 seconds)

#### 2.2 Fix Windows Hyper-V Port Reservations
Windows Hyper-V can reserve port ranges dynamically, causing binding conflicts. This step:
- Shows dynamic port range and excluded port ranges
- Checks if port 3307 is in an excluded range
- **Restarts WinNAT service** to clear stale reservations
- Provides diagnostic output for troubleshooting

#### 2.3 Start MySQL Database
Starts MySQL container with **dynamic port allocation**:

```powershell
# Primary port: 3307
# Fallback port: 50307 (outside Hyper-V range)

docker run -d \
  --name mysql-ci \
  --network smartsplit-ci-network \
  -e MYSQL_ROOT_PASSWORD=rootpassword \
  -e MYSQL_DATABASE=smartsplit-db \
  -p "${primaryPort}:3306" \
  mysql:8.0
```

- Attempts primary port (3307) first
- Falls back to port 50307 if blocked by Hyper-V
- Exports chosen port to `$MYSQL_PORT` environment variable
- Waits 30 seconds and verifies MySQL is ready using `mysqladmin ping`

#### 2.4 Build and Start Backend
```powershell
cd backend
mvn clean package -DskipTests
java -jar target/*.jar \
  --spring.datasource.url=jdbc:mysql://localhost:${MYSQL_PORT}/smartsplit-db \
  --spring.datasource.username=root \
  --spring.datasource.password=rootpassword \
  --app.jwt.secret=$env:APP_JWT_SECRET \
  --server.port=16048
```
- Builds backend JAR file
- Starts backend on port **16048**
- Connects to MySQL on dynamic port
- Runs in background (hidden window)
- Waits 20 seconds for startup

#### 2.5 Start Frontend
```powershell
cd frontend
npm install --legacy-peer-deps
npm start  # Starts on port 3000
```
- Installs dependencies
- Starts React dev server on port **3000**
- Runs in background

#### 2.6 Wait for Services
Health check loop with retry logic:
```powershell
# Frontend: http://localhost:3000
# Backend: http://localhost:16048/api/actuator/health
```
- Retries up to 30 times with 5-second intervals
- Uses Spring Boot Actuator for backend health

#### 2.7 Run Cypress E2E Tests
```powershell
npx cypress run --headless --browser chrome
```
- Base URL: `http://localhost:3000`
- Runs in headless Chrome
- Tests located in `cypress/e2e/SmartSplit-E2E.cy.js`

**Test Coverage:**
- User authentication (sign up, sign in, sign out)
- Profile editing
- Group management (CRUD operations)
- Expense management (equal split and manual split)
- Payment processing and verification

#### 2.8 Upload Test Artifacts
- **Screenshots:** Uploaded on failure only
- **Videos:** Always uploaded
- Uses `actions/upload-artifact@v4`

#### 2.9 Cleanup
**Always runs** (even if tests fail):
- Stops all Java and Node processes
- Removes `mysql-ci` container
- Removes Docker network
- Kills remaining processes on ports 3307, 16048, 3000

**Exit Criteria:** All E2E tests must pass

---

## Stage 3: Build and Push

**Job Name:** `build-and-push`
**Depends On:** `test`, `e2e-test`
**Purpose:** Build Docker images and push to Docker Hub

### Environment Variables:
```yaml
REGISTRY: docker.io
BACKEND_IMAGE: ${{ secrets.DOCKERHUB_USERNAME }}/smartsplit-backend
FRONTEND_IMAGE: ${{ secrets.DOCKERHUB_USERNAME }}/smartsplit-frontend
```

### Steps:

#### 3.1 Verify Docker is Running
- Checks `docker info` command
- Retries up to 10 times with 5-second intervals
- Ensures Docker daemon is accessible

#### 3.2 Log in to Docker Hub
```yaml
uses: docker/login-action@v3
with:
  username: ${{ secrets.DOCKERHUB_USERNAME }}
  password: ${{ secrets.DOCKERHUB_TOKEN }}
```

#### 3.3 Build Backend Image
```powershell
$gitSha = "${{ github.sha }}"
docker build -t "${backendImage}:latest" -t "${backendImage}:${gitSha}" ./backend
```

**Backend Dockerfile** ([`backend/Dockerfile`](backend/Dockerfile)):
```dockerfile
# Stage 1: Build with Maven
FROM maven:3.9.8-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src ./src
RUN mvn -DskipTests package

# Stage 2: Runtime with JRE
FROM eclipse-temurin:17-jre-alpine
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75 ..."
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
RUN mkdir /app/uploads && chown -R app:app /app/uploads
USER app
COPY --from=build /app/target/*.jar /app/app.jar
EXPOSE 8080
HEALTHCHECK CMD wget -qO- http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar /app/app.jar"]
```

**Security Features:**
- Multi-stage build (smaller final image)
- Non-root user (`app`)
- JRE-only runtime (no build tools)
- Health check configured

#### 3.4 Build Frontend Image
```powershell
docker build -t "${frontendImage}:latest" -t "${frontendImage}:${gitSha}" ./frontend
```

**Frontend Dockerfile** ([`frontend/Dockerfile`](frontend/Dockerfile)):
```dockerfile
# Stage 1: Build React app
FROM node:18-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm install
COPY . .
RUN npm run build

# Stage 2: Serve with Nginx
FROM nginx:stable-alpine
COPY --from=build /app/build /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
```

**Features:**
- Multi-stage build
- Optimized production build
- Nginx reverse proxy configuration
- Small Alpine-based image

#### 3.5 Push Images to Docker Hub
```powershell
docker push "${backendImage}:latest"
docker push "${backendImage}:${gitSha}"
docker push "${frontendImage}:latest"
docker push "${frontendImage}:${gitSha}"
```

**Images Published:**
- `sakanaisreal/smartsplit-backend:latest`
- `sakanaisreal/smartsplit-backend:${git-sha}`
- `sakanaisreal/smartsplit-frontend:latest`
- `sakanaisreal/smartsplit-frontend:${git-sha}`

---

## Stage 4: Deploy

**Job Name:** `deploy`
**Depends On:** `build-and-push`
**Condition:** Only runs on `main` branch
**Purpose:** Deploy new images to Kubernetes cluster

### Steps:

#### 4.1 Update Kubernetes Deployments
```powershell
$gitSha = "${{ github.sha }}"

# Update images using git SHA (immutable deployment)
kubectl set image deployment/backend backend="${backendImage}:${gitSha}" -n smartsplit
kubectl set image deployment/frontend frontend="${frontendImage}:${gitSha}" -n smartsplit
```

**Why Git SHA instead of `:latest`?**
- **Immutable deployments:** Each deployment tied to specific commit
- **Easy rollbacks:** Can rollback to any previous version
- **Audit trail:** Clear history of what code is deployed
- **Consistency:** Same image across all environments

#### 4.2 Patch with Timestamp Annotation
```powershell
$timestamp = Get-Date -Format "yyyy-MM-ddTHH:mm:ssZ"
kubectl patch deployment backend -n smartsplit -p "{...\"deployment.kubernetes.io/revision-time\":\"$timestamp\"...}"
kubectl patch deployment frontend -n smartsplit -p "{...\"deployment.kubernetes.io/revision-time\":\"$timestamp\"...}"
```
- Forces rollout even if image tag hasn't changed
- Adds deployment timestamp for tracking

#### 4.3 Wait for Rollout Completion
```powershell
kubectl rollout status deployment/backend -n smartsplit --timeout=300s
kubectl rollout status deployment/frontend -n smartsplit --timeout=300s
```
- Waits up to 5 minutes for rollout
- Monitors pod health and readiness

#### 4.4 Verify Deployment
```powershell
kubectl get pods -n smartsplit
```
- Shows final pod status
- Confirms deployment success

---

## Docker Image Tagging Strategy

### Dual-Tag Approach

Every build creates **two tags** for each image:

| Tag Type | Format | Purpose | Used For |
|----------|--------|---------|----------|
| **Latest** | `:latest` | Convenience tag | Local development, quick testing |
| **Git SHA** | `:${git-sha}` | Immutable reference | Production deployments, rollbacks |

### Example:
For git commit `a1b2c3d4e5f6...`:
```
sakanaisreal/smartsplit-backend:latest
sakanaisreal/smartsplit-backend:a1b2c3d4e5f6...
sakanaisreal/smartsplit-frontend:latest
sakanaisreal/smartsplit-frontend:a1b2c3d4e5f6...
```

### Image Pull Policy
Both backend and frontend use `imagePullPolicy: Always`:
```yaml
imagePullPolicy: Always
```
- Ensures fresh images are always pulled
- Prevents stale image issues in Kubernetes

### Checking Deployed Versions
```bash
# View current images
kubectl describe deployment backend -n smartsplit | grep Image:
kubectl describe deployment frontend -n smartsplit | grep Image:

# View deployment history
kubectl rollout history deployment/backend -n smartsplit
kubectl rollout history deployment/frontend -n smartsplit
```

### Rolling Back Deployments
```bash
# Rollback to previous deployment
kubectl rollout undo deployment/backend -n smartsplit

# Rollback to specific revision
kubectl rollout undo deployment/backend -n smartsplit --to-revision=2

# Deploy specific git SHA
kubectl set image deployment/backend backend=sakanaisreal/smartsplit-backend:a1b2c3d -n smartsplit
```

---

## Kubernetes Deployment Architecture

### Namespace
All resources deployed in `smartsplit` namespace:
```yaml
apiVersion: v1
kind: Namespace
metadata:
  name: smartsplit
```

### Components Overview

```
┌─────────────────────────────────────────────────────────────┐
│                    smartsplit namespace                      │
└─────────────────────────────────────────────────────────────┘
                            │
        ┌───────────────────┼───────────────────┐
        │                   │                   │
        ▼                   ▼                   ▼
    ┌───────┐          ┌───────┐          ┌───────┐
    │ MySQL │          │Backend│          │Frontend│
    │  Pod  │          │  Pods │          │  Pods  │
    │   x1  │          │   x2  │          │   x2   │
    └───────┘          └───────┘          └───────┘
        │                   │                   │
        ▼                   ▼                   ▼
   ┌────────┐         ┌────────┐         ┌────────┐
   │MySQL   │         │Backend │         │Frontend│
   │Service │         │Service │         │Service │
   │ClusterIP         │ClusterIP         │ClusterIP
   │Port 3306│        │Port 8081│        │Port 80 │
   └────────┘         └────────┘         └────────┘
        │                   │                   │
        ▼                   ▼                   ▼
   ┌────────┐         ┌─────────────────────────┐
   │MySQL-PV│         │    Ingress Controller   │
   │10Gi    │         │ /api → backend:8081     │
   └────────┘         │ /files → backend:8081   │
                      │ / → frontend:80         │
                      └─────────────────────────┘
                                  │
                                  ▼
                          [External Access]
```

### MySQL Deployment

**File:** [`k8s/mysql/mysql-deployment.yaml`](k8s/mysql/mysql-deployment.yaml)

```yaml
Deployment:
  - Name: mysql
  - Replicas: 1
  - Strategy: Recreate
  - Image: mysql:8.0
  - Resources:
      Limits: 1Gi memory, 1000m CPU
      Requests: 512Mi memory, 500m CPU
  - Probes:
      Liveness: mysqladmin ping (30s initial, 10s period)
      Readiness: mysqladmin ping (10s initial, 5s period)

PersistentVolume:
  - Name: mysql-pv
  - Capacity: 10Gi
  - Access: ReadWriteOnce
  - HostPath: /mnt/data/mysql

Service:
  - Type: ClusterIP
  - Port: 3306
```

### Backend Deployment

**File:** [`k8s/backend/backend-deployment.yaml`](k8s/backend/backend-deployment.yaml)

```yaml
Deployment:
  - Name: backend
  - Replicas: 2
  - Image: sakanaisreal/smartsplit-backend:latest
  - ImagePullPolicy: Always
  - InitContainers:
      - wait-for-mysql (busybox with nc check)
  - Resources:
      Limits: 1Gi memory, 1000m CPU
      Requests: 512Mi memory, 500m CPU
  - Environment:
      - SPRING_DATASOURCE_URL (MySQL connection)
      - APP_JWT_SECRET (from secrets)
      - SPRING_PROFILES_ACTIVE (from configmap)
  - Volumes:
      - uploads-storage (PVC for file uploads)
  - Probes:
      Liveness: /actuator/health (90s initial, 10s period)
      Readiness: /actuator/health (60s initial, 5s period)

Service:
  - Type: ClusterIP
  - Port: 8081
```

**Init Container:**
Ensures MySQL is ready before backend starts:
```bash
until nc -z mysql.smartsplit.svc.cluster.local 3306; do
  echo "MySQL is unavailable - sleeping"
  sleep 2
done
```

### Frontend Deployment

**File:** [`k8s/frontend/frontend-deployment.yaml`](k8s/frontend/frontend-deployment.yaml)

```yaml
Deployment:
  - Name: frontend
  - Replicas: 2
  - Image: sakanaisreal/smartsplit-frontend:latest
  - ImagePullPolicy: Always
  - Resources:
      Limits: 256Mi memory, 500m CPU
      Requests: 128Mi memory, 250m CPU

Service:
  - Type: ClusterIP
  - Port: 80
```

### Ingress Configuration

**File:** [`k8s/ingress.yaml`](k8s/ingress.yaml)

```yaml
Ingress:
  - Name: smartsplit-ingress
  - Annotations:
      proxy-body-size: "200m"  # For file uploads
  - Rules:
      - /api → backend:8081
      - /files → backend:8081
      - / → frontend:80
```

**Routing Logic:**
1. `/api/*` requests → Backend service (REST API)
2. `/files/*` requests → Backend service (file downloads)
3. All other requests → Frontend service (React SPA)

---

## Environment Variables and Secrets

### GitHub Secrets (Required)

| Secret | Description | Used In |
|--------|-------------|---------|
| `DOCKERHUB_USERNAME` | Docker Hub username | Image push |
| `DOCKERHUB_TOKEN` | Docker Hub access token | Image push |
| `APP_JWT_SECRET` | JWT signing secret | Backend authentication |

### Kubernetes ConfigMap

**File:** [`k8s/configmap.yaml`](k8s/configmap.yaml)

```yaml
SPRING_PROFILES_ACTIVE: "prod"
MYSQL_DATABASE: "smartsplit-db"
APP_JWT_EXPIRATION_SECONDS: "86400"
SPRING_SERVLET_MULTIPART_MAX_FILE_SIZE: "200MB"
SPRING_SERVLET_MULTIPART_MAX_REQUEST_SIZE: "200MB"
```

### Kubernetes Secrets

**File:** [`k8s/secrets.yaml`](k8s/secrets.yaml)

```yaml
MYSQL_ROOT_PASSWORD: <base64-encoded>
APP_JWT_SECRET: <base64-encoded>
```

**Note:** Secrets are base64-encoded in the YAML file

---

## Troubleshooting and Monitoring

### Pipeline Failures

#### Test Stage Fails
```bash
# Check test logs in GitHub Actions
# Common issues:
- Missing APP_JWT_SECRET
- Dependency conflicts (use --legacy-peer-deps)
- Test failures in backend
```

#### E2E Test Fails
```bash
# Download Cypress artifacts from GitHub Actions:
- Screenshots (failure cases)
- Videos (all tests)

# Common issues:
- Port binding conflicts (Hyper-V)
- MySQL startup timeout
- Backend/Frontend not ready
- Cypress test flakiness
```

#### Build Stage Fails
```bash
# Common issues:
- Docker daemon not running
- Docker Hub authentication failure
- Dockerfile syntax errors
```

#### Deploy Stage Fails
```bash
# Check Kubernetes cluster:
kubectl get pods -n smartsplit
kubectl describe pod <pod-name> -n smartsplit
kubectl logs <pod-name> -n smartsplit

# Common issues:
- Image pull errors
- Resource limits exceeded
- ConfigMap/Secret missing
- MySQL not ready
```

### Monitoring Deployed Application

#### Check Pod Status
```bash
kubectl get pods -n smartsplit
kubectl get deployments -n smartsplit
kubectl get services -n smartsplit
```

#### View Logs
```bash
# Backend logs
kubectl logs -n smartsplit deployment/backend --tail=100 -f

# Frontend logs
kubectl logs -n smartsplit deployment/frontend --tail=100 -f

# MySQL logs
kubectl logs -n smartsplit deployment/mysql --tail=100 -f
```

#### Check Resource Usage
```bash
kubectl top pods -n smartsplit
kubectl top nodes
```

#### Access Application
```bash
# Get Minikube IP
minikube ip

# Access application:
# Frontend: http://<minikube-ip>
# Backend API: http://<minikube-ip>/api
```

#### Port Forwarding (Alternative Access)
```bash
# Frontend
kubectl port-forward -n smartsplit svc/frontend 3003:80

# Backend
kubectl port-forward -n smartsplit svc/backend 16048:8081

# MySQL
kubectl port-forward -n smartsplit svc/mysql 8082:3306
```

Scripts available in `scripts/` directory:
- `start-port-forward.ps1`: Start all port forwards
- `stop-port-forward.ps1`: Stop all port forwards

### Health Checks

#### Backend Health
```bash
# Via port-forward
curl http://localhost:16048/actuator/health

# Via ingress
curl http://<minikube-ip>/api/actuator/health
```

#### MySQL Health
```bash
kubectl exec -n smartsplit deployment/mysql -- mysqladmin ping -h localhost -u root -p<password>
```

### Rollback Procedure

If deployment fails:

```bash
# Quick rollback to previous version
kubectl rollout undo deployment/backend -n smartsplit
kubectl rollout undo deployment/frontend -n smartsplit

# Check rollout status
kubectl rollout status deployment/backend -n smartsplit

# View rollout history
kubectl rollout history deployment/backend -n smartsplit

# Rollback to specific revision
kubectl rollout undo deployment/backend -n smartsplit --to-revision=2
```

---

## Local Development vs CI/CD

### Local Development (Minikube)

**Deployment Script:** [`deploy-minikube.ps1`](deploy-minikube.ps1)

```powershell
# Deploy with default :latest tag
.\deploy-minikube.ps1

# Deploy with custom tag
.\deploy-minikube.ps1 -ImageTag "v1.2.3"
```

**Process:**
1. Start Minikube cluster
2. Use Minikube's Docker daemon
3. Build images locally with `:latest` tag
4. Apply Kubernetes manifests
5. Enable Ingress addon
6. Wait for pods to be ready

**Key Differences from CI/CD:**
- Uses local Docker daemon (not Docker Hub)
- Uses `:latest` tag (not git SHA)
- No automated testing
- Manual deployment trigger

### CI/CD (GitHub Actions)

**Process:**
1. Automated trigger on push/PR to `main`
2. Run unit tests
3. Run E2E tests with full stack
4. Build images with dual tags
5. Push to Docker Hub
6. Deploy using git SHA tags
7. Automated health checks

---

## Best Practices

### 1. Image Tagging
- Always use git SHA for production deployments
- Use `:latest` only for local development
- Never deploy `:latest` to production

### 2. Secrets Management
- Store secrets in GitHub Secrets (not in code)
- Use Kubernetes Secrets for sensitive data
- Rotate secrets regularly

### 3. Resource Limits
- Always set resource requests and limits
- Monitor resource usage
- Adjust based on actual consumption

### 4. Health Checks
- Configure liveness and readiness probes
- Use appropriate initial delays
- Monitor probe failures

### 5. Testing
- Run tests before deployment
- Use E2E tests to catch integration issues
- Upload test artifacts for debugging

### 6. Rollback Strategy
- Keep deployment history
- Test rollback procedures
- Document rollback steps

### 7. Monitoring
- Monitor application logs
- Track pod status
- Set up alerts for failures

---

## Conclusion

The SmartSplit CI/CD pipeline provides:

- **Automated Testing:** Unit and E2E tests on every change
- **Immutable Deployments:** Git SHA-based versioning
- **Easy Rollbacks:** Clear deployment history
- **Security:** Multi-stage builds, non-root containers, secrets management
- **Reliability:** Health checks, resource limits, automated verification
- **Visibility:** Detailed logging, test artifacts, deployment status

This pipeline ensures that only tested, verified code reaches production, with clear traceability and easy rollback capabilities.
