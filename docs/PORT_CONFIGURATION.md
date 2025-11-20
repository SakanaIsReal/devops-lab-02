# SmartSplit Network Port Configuration Guide

**Last Updated:** 2025-11-20
**Purpose:** Comprehensive documentation of all network ports used across local development and Kubernetes deployment environments.

---

## Table of Contents

1. [Quick Reference](#quick-reference)
2. [Local Development (Docker Compose)](#local-development-docker-compose)
3. [Minikube Kubernetes Setup](#minikube-kubernetes-setup)
4. [Frontend Configuration](#frontend-configuration)
5. [Backend Configuration](#backend-configuration)
6. [CI/CD E2E Testing](#cicd-e2e-testing)
7. [Port-by-Port Breakdown](#port-by-port-breakdown)
8. [Deployment Scenario Flows](#deployment-scenario-flows)
9. [Known Issues & Mismatches](#known-issues--mismatches)
10. [Configuration File Reference](#configuration-file-reference)
11. [Troubleshooting](#troubleshooting)

---

## Quick Reference

### Port Summary Table

| Port | Environment | Service | Access URL | Notes |
|------|-------------|---------|------------|-------|
| **3000** | Local Dev, CI/CD | Frontend Dev Server | http://localhost:3000 | `npm start` in frontend/ |
| **3003** | Minikube | Frontend (Port Forward) | http://localhost:3003 | Via `kubectl port-forward` |
| **8080** | Docker Compose | Frontend (nginx) | http://localhost:8080 | Production-like frontend |
| **8081** | Docker Compose | Backend (Spring Boot) | http://localhost:8081/api | Direct backend access |
| **8082** | Docker Compose, Minikube | MySQL | localhost:8082 | Database access for tools |
| **16048** | Minikube, CI/CD | Backend (Port Forward) | http://localhost:16048/api | Standard backend port |
| **3306** | Internal Only | MySQL (Container) | - | Internal container port |
| **3307/50307** | CI/CD Only | MySQL (Test) | localhost:3307 | E2E test database |

### Which Port Should I Use?

**For Local Development:**
- Frontend: `npm start` → http://localhost:3000
- Backend (Docker): http://localhost:8081/api
- Backend (Minikube): http://localhost:16048/api (after port-forward)
- MySQL: localhost:8082

**For Docker Compose:**
- Access frontend: http://localhost:8080
- Access backend API: http://localhost:8081/api
- Access MySQL: localhost:8082

**For Minikube (with port forwarding):**
- Access frontend: http://localhost:3003
- Access backend API: http://localhost:16048/api
- Access MySQL: localhost:8082

**For Minikube (via Ingress):**
- Access frontend: http://MINIKUBE_IP/
- Access backend API: http://MINIKUBE_IP/api

---

## Local Development (Docker Compose)

### Port Mapping Table

| Service | Container Port | Host Port | URL Access | Configuration |
|---------|---------------|-----------|------------|---------------|
| **MySQL** | 3306 | 8082 | localhost:8082 | [docker-compose.yml:9](../docker-compose.yml#L9) |
| **Backend** | 8081 | 8081 | localhost:8081 | [docker-compose.yml:51](../docker-compose.yml#L51) |
| **Frontend** | 80 | 8080 | localhost:8080 | [docker-compose.yml:60](../docker-compose.yml#L60) |

### Configuration Details

#### docker-compose.yml Ports

```yaml
services:
  db:
    ports:
      - "8082:3306"  # Host:Container
    # Accessible at localhost:8082 from host
    # Accessible at db:3306 from other containers

  backend:
    ports:
      - "8081:8081"
    environment:
      SPRING_DATASOURCE_URL: jdbc:mysql://db:3306/${MYSQL_DATABASE}
      # Uses internal docker network, port 3306
    # Accessible at localhost:8081 from host
    # Accessible at backend:8081 from other containers

  frontend:
    ports:
      - "8080:80"
    # Accessible at localhost:8080 from host
```

### Network Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    Docker Compose Network                │
│                                                           │
│  ┌──────────────┐      ┌──────────────┐      ┌────────┐│
│  │   Frontend   │─────▶│   Backend    │─────▶│  MySQL ││
│  │  (nginx:80)  │      │ (spring:8081)│      │ (:3306)││
│  └──────────────┘      └──────────────┘      └────────┘│
│         │                      │                    │    │
└─────────┼──────────────────────┼────────────────────┼───┘
          │                      │                    │
    Host:8080              Host:8081            Host:8082
          │                      │                    │
    ┌─────▼──────────────────────▼────────────────────▼───┐
    │              User's Local Machine (Host)            │
    └─────────────────────────────────────────────────────┘
```

### Starting Docker Compose

```bash
# Start all services
docker-compose up

# Start in detached mode
docker-compose up -d

# Access services
# Frontend: http://localhost:8080
# Backend API: http://localhost:8081/api/health
# MySQL: mysql -h 127.0.0.1 -P 8082 -u root -p
```

### Frontend-Backend Communication (Docker Compose)

The frontend container uses **nginx.conf** to proxy API requests:

```nginx
# frontend/nginx.conf
location /api {
    proxy_pass http://backend:8081;  # Uses Docker service name
    # ...
}
```

When user visits http://localhost:8080/api/users:
1. Browser → nginx container (port 80)
2. nginx → backend container (port 8081) via Docker network
3. Backend returns response
4. nginx → Browser

---

## Minikube Kubernetes Setup

### Kubernetes Service Configuration

| Service | Type | Service Port | Target Port | Pod Port | Configuration |
|---------|------|--------------|-------------|----------|---------------|
| **MySQL** | ClusterIP | 3306 | 3306 | 3306 | [mysql-deployment.yaml:103](../k8s/mysql/mysql-deployment.yaml#L103) |
| **Backend** | ClusterIP | 8081 | 8081 | 8081 | [backend-deployment.yaml:110](../k8s/backend/backend-deployment.yaml#L110) |
| **Frontend** | ClusterIP | 80 | 80 | 80 | [frontend-deployment.yaml:42](../k8s/frontend/frontend-deployment.yaml#L42) |

### Port Forwarding Configuration

Port forwarding makes cluster services accessible on localhost:

| Script | Local Port | Target Service | Service Port | Namespace |
|--------|-----------|----------------|--------------|-----------|
| start-port-forward.ps1 | 3003 | frontend | 80 | smartsplit |
| start-port-forward.ps1 | 16048 | backend | 8081 | smartsplit |
| start-port-forward.ps1 | 8082 | mysql | 3306 | smartsplit |

#### Port Forward Commands

```powershell
# Windows PowerShell
.\scripts\start-port-forward.ps1

# Individual port forwards
kubectl port-forward svc/frontend 3003:80 -n smartsplit
kubectl port-forward svc/backend 16048:8081 -n smartsplit
kubectl port-forward svc/mysql 8082:3306 -n smartsplit
```

```bash
# Linux/Mac
./scripts/start-port-forward.sh

# Individual port forwards (same commands as Windows)
kubectl port-forward svc/frontend 3003:80 -n smartsplit
kubectl port-forward svc/backend 16048:8081 -n smartsplit
kubectl port-forward svc/mysql 8082:3306 -n smartsplit
```

### Ingress Configuration

The ingress controller routes traffic based on URL paths:

```yaml
# k8s/ingress.yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
spec:
  rules:
    - http:
        paths:
          - path: /api
            pathType: Prefix
            backend:
              service:
                name: backend
                port:
                  number: 8081
          - path: /
            pathType: Prefix
            backend:
              service:
                name: frontend
                port:
                  number: 80
```

**Access via Ingress:**
- Frontend: http://MINIKUBE_IP/
- Backend API: http://MINIKUBE_IP/api

**Get Minikube IP:**
```bash
minikube ip
```

### Kubernetes Network Architecture

#### Without Port Forwarding (via Ingress)

```
┌────────────────────────────────────────────────────────┐
│                  Kubernetes Cluster                     │
│                                                          │
│  ┌─────────────┐      ┌──────────────┐      ┌────────┐│
│  │  Ingress    │      │              │      │        ││
│  │ Controller  │      │              │      │        ││
│  └──────┬──────┘      │              │      │        ││
│         │             │              │      │        ││
│    /api │ /           │              │      │        ││
│         │             │              │      │        ││
│  ┌──────▼──────┐   ┌──▼──────────┐   ┌─────▼──────┐ │
│  │  Frontend   │   │   Backend   │   │   MySQL    │ │
│  │  Service    │   │   Service   │   │  Service   │ │
│  │   (:80)     │   │   (:8081)   │   │  (:3306)   │ │
│  └──────┬──────┘   └──────┬──────┘   └─────┬──────┘ │
│         │                 │                 │        │
│  ┌──────▼──────┐   ┌──────▼──────┐   ┌─────▼──────┐ │
│  │ Frontend    │   │  Backend    │   │   MySQL    │ │
│  │   Pod       │──▶│   Pod       │──▶│    Pod     │ │
│  │ (nginx:80)  │   │(spring:8081)│   │  (:3306)   │ │
│  └─────────────┘   └─────────────┘   └────────────┘ │
│                                                        │
└────────────────────────────────────────────────────────┘
           │
           ▼
    http://MINIKUBE_IP
```

#### With Port Forwarding

```
┌─────────────────────────────────────────────────────────┐
│                  Kubernetes Cluster                      │
│                                                           │
│  ┌──────────────┐      ┌──────────────┐      ┌────────┐ │
│  │  Frontend    │      │   Backend    │      │  MySQL │ │
│  │  Service     │      │   Service    │      │Service │ │
│  │   (:80)      │      │   (:8081)    │      │(:3306) │ │
│  └──────┬───────┘      └──────┬───────┘      └────┬───┘ │
│         │                     │                   │     │
│  ┌──────▼───────┐      ┌──────▼───────┐      ┌───▼────┐│
│  │ Frontend Pod │─────▶│ Backend Pod  │─────▶│MySQL   ││
│  │  (nginx:80)  │      │(spring:8081) │      │Pod     ││
│  └──────────────┘      └──────────────┘      │(:3306) ││
│         │                     │               └────────┘│
└─────────┼─────────────────────┼──────────────────┼──────┘
          │                     │                  │
   Port Forward           Port Forward      Port Forward
   3003→80                16048→8081         8082→3306
          │                     │                  │
    ┌─────▼─────────────────────▼──────────────────▼──────┐
    │         User's Local Machine (Host)                 │
    │  localhost:3003  localhost:16048  localhost:8082    │
    └─────────────────────────────────────────────────────┘
```

### Kubernetes Environment Variables

The backend pod receives configuration via ConfigMap and Secrets:

```yaml
# Backend environment in k8s/backend/backend-deployment.yaml
env:
  - name: SPRING_PROFILES_ACTIVE
    value: "prod"
  - name: SPRING_DATASOURCE_URL
    value: "jdbc:mysql://mysql.smartsplit.svc.cluster.local:3306/smartsplit-db"
```

**Key Points:**
- Backend connects to MySQL via Kubernetes DNS: `mysql.smartsplit.svc.cluster.local:3306`
- Uses internal cluster networking (no host ports)
- Port 3306 is the service port (not exposed outside cluster without port-forward)

---

## Frontend Configuration

### API Connection Strategy

The frontend uses **relative paths** for API calls, relying on proxies to route to the backend.

#### Frontend Source Code

```typescript
// frontend/src/utils/api.ts:13
const API_BASE_URL = '/api';  // Relative path

const api = axios.create({
  baseURL: API_BASE_URL,
});
```

All API requests (e.g., `api.get('/users')`) become `GET /api/users`.

### Proxy Configuration (Development)

#### package.json Proxy

```json
// frontend/package.json:60
{
  "proxy": "http://localhost:16048"
}
```

**Purpose:** Used ONLY when running `npm start` in the frontend directory.

**How it works:**
1. User visits http://localhost:3000
2. Frontend makes request to `/api/users`
3. Webpack dev server proxies to `http://localhost:16048/api/users`
4. Backend responds

**When this is used:**
- Local frontend development with `npm start`
- Frontend developer testing without Docker

**When this is NOT used:**
- Docker Compose (uses nginx.conf instead)
- Kubernetes (uses nginx.conf instead)
- Production builds (static files served by nginx)

### Nginx Configuration (Production)

```nginx
# frontend/nginx.conf:14-15
location /api {
    proxy_pass http://backend.smartsplit.svc.cluster.local:8081;
    proxy_http_version 1.1;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection 'upgrade';
    proxy_set_header Host $host;
    proxy_cache_bypass $http_upgrade;
}
```

**Purpose:** Used in Docker containers and Kubernetes pods.

**How it works:**
1. User's browser sends request to `/api/users`
2. Nginx receives request
3. Nginx proxies to backend service
4. Backend responds through nginx to browser

**Environment-specific backends:**
- **Docker Compose:** `http://backend:8081` (Docker service name)
- **Kubernetes:** `http://backend.smartsplit.svc.cluster.local:8081` (K8s DNS)

### Frontend Port Summary

| Environment | Port | Server | Proxy Target | Configuration |
|-------------|------|--------|--------------|---------------|
| **npm start** | 3000 | Webpack Dev Server | localhost:16048 | package.json:60 |
| **Docker Compose** | 8080 (host) / 80 (container) | nginx | backend:8081 | nginx.conf:14 |
| **Kubernetes** | 80 (service) | nginx | backend.smartsplit...:8081 | nginx.conf:14 |
| **K8s Port Forward** | 3003 (host) / 80 (service) | nginx | backend.smartsplit...:8081 | nginx.conf:14 |

---

## Backend Configuration

### Server Port

The backend consistently runs on **port 8081** inside its container/pod across all environments.

### Configuration Files

#### application-dev.properties

```properties
# backend/src/main/resources/application-dev.properties:2
server.port=8081

# backend/src/main/resources/application-dev.properties:5
spring.datasource.url=jdbc:mysql://127.0.0.1:8082/smartsplit-db
```

**Usage:** Local development with IDE or `mvn spring-boot:run`

**Database:** Connects to `127.0.0.1:8082` (assumes MySQL running via Docker with port 8082 exposed)

#### application-prod.properties

```properties
# backend/src/main/resources/application-prod.properties:2
server.port=8081

# backend/src/main/resources/application-prod.properties:15
spring.datasource.url=${SPRING_DATASOURCE_URL:jdbc:mysql://db:3306/smartsplit-db}
```

**Usage:** Docker Compose and Kubernetes deployments

**Database:** Uses environment variable `SPRING_DATASOURCE_URL` or defaults to `db:3306`

#### application-test.properties

```properties
# backend/src/main/resources/application-test.properties
# No server.port specified (Testcontainers assigns random port)
```

**Usage:** Running tests with `mvn test` or `mvn verify`

**Database:** Testcontainers spins up temporary MySQL on random port

### Database Connection by Environment

| Environment | Database URL | Port | Notes |
|------------|-------------|------|-------|
| **Dev (local)** | `jdbc:mysql://127.0.0.1:8082/smartsplit-db` | 8082 | Via application-dev.properties |
| **Docker Compose** | `jdbc:mysql://db:3306/smartsplit-db` | 3306 | Internal network, service name 'db' |
| **Kubernetes** | `jdbc:mysql://mysql.smartsplit.svc.cluster.local:3306/smartsplit-db` | 3306 | K8s DNS, internal cluster network |
| **Test (Maven)** | Testcontainers (dynamic) | Random | Ephemeral container |
| **CI/CD E2E** | `jdbc:mysql://localhost:3307/smartsplit-db` | 3307 or 50307 | GitHub Actions runner |

### Backend Port Exposure

| Environment | Internal Port | External Port | Access URL |
|-------------|--------------|---------------|------------|
| **Local (mvn spring-boot:run)** | 8081 | 8081 | http://localhost:8081/api |
| **Docker Compose** | 8081 | 8081 | http://localhost:8081/api |
| **Kubernetes Service** | 8081 | 8081 (ClusterIP) | Internal only |
| **K8s Port Forward** | 8081 | 16048 | http://localhost:16048/api |
| **K8s Ingress** | 8081 | 80 (ingress) | http://MINIKUBE_IP/api |

### Health Check Endpoint

```
# Check backend health
curl http://localhost:8081/actuator/health  # Docker Compose
curl http://localhost:16048/actuator/health  # Kubernetes port-forward
curl http://MINIKUBE_IP/api/actuator/health  # Kubernetes ingress
```

---

## CI/CD E2E Testing

The GitHub Actions pipeline runs end-to-end tests with a specific port configuration to avoid conflicts.

### E2E Test Port Configuration

| Service | Port | Purpose | Configuration |
|---------|------|---------|---------------|
| **MySQL** | 3307 (or 50307 if conflict) | Test database | [ci-cd.yml:234-236](../.github/workflows/ci-cd.yml#L234) |
| **Backend** | 16048 | Test backend API | [ci-cd.yml:378](../.github/workflows/ci-cd.yml#L378) |
| **Frontend** | 3000 | React dev server | [ci-cd.yml:391](../.github/workflows/ci-cd.yml#L391) |
| **Cypress** | N/A | Test runner | Targets http://localhost:3000 |

### CI/CD Port Selection Strategy

```yaml
# .github/workflows/ci-cd.yml:234-236
# Try port 3307 first, fallback to 50307 if in use
$mysqlPort = 3307
if (Test-NetConnection -ComputerName localhost -Port $mysqlPort -InformationLevel Quiet) {
    $mysqlPort = 50307
}
```

**Reason:** GitHub Actions runners may have MySQL already running on 3307.

### E2E Test Network Flow

```
┌─────────────────────────────────────────────────────────┐
│              GitHub Actions Runner (Windows)            │
│                                                          │
│  ┌──────────────┐      ┌──────────────┐      ┌────────┐│
│  │   Cypress    │─────▶│   Frontend   │─────▶│Backend ││
│  │  Test Runner │      │npm start:3000│      │:16048  ││
│  └──────────────┘      └──────────────┘      └────┬───┘│
│         │                      │                   │    │
│    Visits                 Proxies to              Uses  │
│  localhost:3000          localhost:16048     localhost: │
│                                               3307/50307│
│                                                   │     │
│                                            ┌──────▼────┐│
│                                            │   MySQL   ││
│                                            │ Container ││
│                                            │:3307/50307││
│                                            └───────────┘│
└─────────────────────────────────────────────────────────┘
```

### Cypress Configuration

```javascript
// cypress.config.js:5
export default defineConfig({
  e2e: {
    baseUrl: process.env.CYPRESS_BASE_URL || 'http://localhost:3000/',
  },
});
```

**CI/CD sets:** `CYPRESS_BASE_URL: http://localhost:3000`

### Starting E2E Test Stack (Manual)

```powershell
# Start MySQL
docker run -d --name smartsplit-mysql-test `
  -e MYSQL_ROOT_PASSWORD=rootpassword `
  -e MYSQL_DATABASE=smartsplit-db `
  -p 3307:3306 mysql:8

# Start Backend
cd backend
$env:SPRING_PROFILES_ACTIVE="test"
$env:SPRING_DATASOURCE_URL="jdbc:mysql://localhost:3307/smartsplit-db"
mvn spring-boot:run

# Start Frontend (new terminal)
cd frontend
npm start  # Runs on port 3000

# Run Cypress (new terminal)
$env:CYPRESS_BASE_URL="http://localhost:3000"
npx cypress run
```

---

## Port-by-Port Breakdown

### Port 3000

**Primary Use:** Frontend development server

**Environments:**
- Local development: `npm start` in `frontend/` directory
- CI/CD E2E tests: GitHub Actions runs frontend

**Server:** Webpack Dev Server (React development)

**Access:**
- http://localhost:3000

**Proxy Behavior:**
- Requests to `/api` are proxied to `http://localhost:16048` (from package.json)

**Started By:**
```bash
cd frontend
npm start
```

---

### Port 3003

**Primary Use:** Minikube frontend access via port forwarding

**Environments:**
- Minikube local development

**Server:** kubectl port-forward to frontend service (nginx)

**Access:**
- http://localhost:3003

**Started By:**
```powershell
# Windows
.\scripts\start-port-forward.ps1

# Or manually
kubectl port-forward svc/frontend 3003:80 -n smartsplit
```

**Purpose:** Allows accessing Kubernetes frontend service from localhost without using Minikube IP

---

### Port 8080

**Primary Use:** Docker Compose frontend access

**Environments:**
- Docker Compose local development

**Server:** nginx container

**Access:**
- http://localhost:8080

**Port Mapping:** Host 8080 → Container 80

**Started By:**
```bash
docker-compose up
```

**Purpose:** Production-like frontend access using Docker Compose

---

### Port 8081

**Primary Use:** Backend Spring Boot application

**Environments:**
- Docker Compose
- Local Maven/IDE development
- Kubernetes (internal service port)

**Server:** Spring Boot embedded Tomcat

**Access:**
- http://localhost:8081/api (Docker Compose)
- http://localhost:8081/api (local development)
- Internal only (Kubernetes, access via port-forward or ingress)

**Configuration:**
- `server.port=8081` in application*.properties

**Started By:**
```bash
# Local development
cd backend
mvn spring-boot:run

# Docker Compose
docker-compose up

# Kubernetes
kubectl apply -f k8s/
```

---

### Port 8082

**Primary Use:** MySQL database external access

**Environments:**
- Docker Compose
- Minikube (via port forward)

**Server:** MySQL 8

**Access:**
- `mysql -h 127.0.0.1 -P 8082 -u root -p`
- Database tools (DBeaver, MySQL Workbench, etc.)

**Port Mapping:**
- **Docker Compose:** Host 8082 → Container 3306
- **Kubernetes:** Host 8082 → Service 3306 (via port-forward)

**Purpose:** Allow developers to access database with external tools for debugging and inspection

**Started By:**
```bash
# Docker Compose
docker-compose up

# Kubernetes port forward
kubectl port-forward svc/mysql 8082:3306 -n smartsplit
```

---

### Port 16048

**Primary Use:** Backend access for Minikube and E2E testing

**Environments:**
- Minikube (via port forward)
- CI/CD E2E tests
- Local frontend development (`npm start`)

**Server:** kubectl port-forward to backend service or Spring Boot process

**Access:**
- http://localhost:16048/api

**Purpose:**
- Consistent backend port across Minikube and testing environments
- Matches frontend package.json proxy configuration

**Started By:**
```powershell
# Kubernetes port forward
.\scripts\start-port-forward.ps1
# Or manually
kubectl port-forward svc/backend 16048:8081 -n smartsplit

# CI/CD E2E (GitHub Actions)
mvn spring-boot:run  # Configured to run on 16048
```

---

### Port 3306

**Primary Use:** MySQL internal container/pod port

**Environments:**
- Docker Compose (internal network)
- Kubernetes (internal cluster network)

**Server:** MySQL 8

**Access:** Internal only (not directly accessible from host)

**Accessed By:**
- Backend containers/pods via Docker network or Kubernetes DNS
- Docker: `jdbc:mysql://db:3306/smartsplit-db`
- Kubernetes: `jdbc:mysql://mysql.smartsplit.svc.cluster.local:3306/smartsplit-db`

**Configuration:**
- Default MySQL port, not changed

---

### Port 3307 / 50307

**Primary Use:** CI/CD E2E test MySQL container

**Environments:**
- GitHub Actions E2E test stage only

**Server:** MySQL 8 Docker container

**Port Selection Logic:**
```powershell
# Try 3307 first
$mysqlPort = 3307
# If in use, fallback to 50307
if (Test-NetConnection -ComputerName localhost -Port $mysqlPort -InformationLevel Quiet) {
    $mysqlPort = 50307
}
```

**Purpose:**
- Isolated test database for E2E tests
- Avoids conflicts with runner's MySQL (often on 3306) or other services on 3307

**Access:**
- Backend test: `jdbc:mysql://localhost:3307/smartsplit-db` (or 50307)

**Started By:**
```powershell
# GitHub Actions workflow
docker run -d --name smartsplit-mysql-test `
  -e MYSQL_ROOT_PASSWORD=$env:MYSQL_ROOT_PASSWORD `
  -e MYSQL_DATABASE=$env:MYSQL_DATABASE `
  -p ${mysqlPort}:3306 mysql:8
```

---

## Deployment Scenario Flows

### Scenario 1: Pure Docker Compose Development

**Use Case:** Full stack development with production-like setup

```
┌──────────────────────────────────────────────────────┐
│                   User's Browser                     │
│              http://localhost:8080                   │
└──────────────────────┬───────────────────────────────┘
                       │
                       ▼
┌──────────────────────────────────────────────────────┐
│                Docker Compose Network                │
│                                                       │
│  ┌──────────────┐      ┌──────────────┐  ┌────────┐ │
│  │  Frontend    │      │  Backend     │  │ MySQL  │ │
│  │  nginx:80    │─────▶│ spring:8081  │─▶│ :3306  │ │
│  │              │ /api │              │  │        │ │
│  └──────────────┘      └──────────────┘  └────────┘ │
│         │                     │                │     │
│    Host:8080            Host:8081        Host:8082  │
└─────────┼─────────────────────┼────────────────┼────┘
          │                     │                │
          ▼                     ▼                ▼
      localhost:8080      localhost:8081   localhost:8082
```

**Access Points:**
- Frontend: http://localhost:8080
- Backend API: http://localhost:8081/api
- MySQL: localhost:8082

**How Requests Flow:**
1. User visits `http://localhost:8080`
2. nginx serves React static files
3. User action triggers API call to `/api/users`
4. nginx proxies to `http://backend:8081/api/users` (Docker network)
5. Backend queries MySQL at `db:3306` (Docker network)
6. Response flows back through nginx to browser

**Start Commands:**
```bash
docker-compose up
```

---

### Scenario 2: Frontend Dev + Docker Backend (BROKEN ⚠️)

**Use Case:** Frontend developer iterating on React code, using Docker backend

```
┌──────────────────────────────────────────────────────┐
│                User's Browser                        │
│              http://localhost:3000                   │
└──────────────────────┬───────────────────────────────┘
                       │
                       ▼
┌──────────────────────────────────────────────────────┐
│             Webpack Dev Server :3000                 │
│          (frontend/ directory, npm start)            │
│                                                       │
│  Proxy configured: http://localhost:16048            │
└──────────────────────┬───────────────────────────────┘
                       │ /api requests
                       ▼
                  localhost:16048 ❌ NOTHING LISTENING

┌──────────────────────────────────────────────────────┐
│           Docker Compose Backend (RUNNING)           │
│              Listening on: localhost:8081            │
│                    ❌ MISMATCH                        │
└──────────────────────────────────────────────────────┘
```

**Problem:**
- package.json proxy: `http://localhost:16048`
- Docker backend: `localhost:8081`
- **Frontend cannot reach backend**

**Workaround:**
```json
// Temporarily change frontend/package.json
"proxy": "http://localhost:8081"
```

**Recommended Fix:** See [Known Issues](#known-issues--mismatches) section

**Start Commands:**
```bash
# Terminal 1: Start Docker backend
docker-compose up backend db

# Terminal 2: Start frontend dev server
cd frontend
npm start

# Access: http://localhost:3000
```

---

### Scenario 3: Minikube with Port Forwarding

**Use Case:** Kubernetes development with local access

```
┌──────────────────────────────────────────────────────┐
│                  User's Browser                      │
│              http://localhost:3003                   │
└──────────────────────┬───────────────────────────────┘
                       │
                       ▼
┌──────────────────────────────────────────────────────┐
│              kubectl port-forward                    │
│                                                       │
│  localhost:3003 ──┐  localhost:16048 ──┐  localhost: │
│                   │                     │  8082 ──┐   │
└───────────────────┼─────────────────────┼─────────┼───┘
                    │                     │         │
                    ▼                     ▼         ▼
┌──────────────────────────────────────────────────────┐
│               Kubernetes Cluster                     │
│                                                       │
│  ┌─────────────┐    ┌──────────────┐    ┌────────┐  │
│  │  Frontend   │    │  Backend     │    │ MySQL  │  │
│  │  Service    │    │  Service     │    │Service │  │
│  │   :80       │    │   :8081      │    │ :3306  │  │
│  └──────┬──────┘    └───────┬──────┘    └────┬───┘  │
│         │                   │                 │      │
│  ┌──────▼──────┐    ┌───────▼──────┐    ┌────▼───┐  │
│  │ Frontend    │    │  Backend     │    │ MySQL  │  │
│  │ Pod         │───▶│  Pod         │───▶│  Pod   │  │
│  │ nginx:80    │/api│ spring:8081  │    │ :3306  │  │
│  └─────────────┘    └──────────────┘    └────────┘  │
│                                                       │
└───────────────────────────────────────────────────────┘
```

**Access Points:**
- Frontend: http://localhost:3003
- Backend API: http://localhost:16048/api
- MySQL: localhost:8082

**How Requests Flow:**
1. User visits `http://localhost:3003`
2. Port-forward tunnels to frontend service (port 80)
3. Frontend pod's nginx serves React static files
4. User action triggers API call to `/api/users`
5. nginx proxies to `http://backend.smartsplit.svc.cluster.local:8081/api/users` (K8s DNS)
6. Backend pod queries MySQL at `mysql.smartsplit.svc.cluster.local:3306` (K8s DNS)
7. Response flows back through nginx, port-forward, to browser

**Start Commands:**
```powershell
# Windows
.\deploy-minikube.ps1
.\scripts\start-port-forward.ps1

# Access: http://localhost:3003
```

```bash
# Linux/Mac
./deploy-minikube.sh
./scripts/start-port-forward.sh

# Access: http://localhost:3003
```

---

### Scenario 4: Minikube via Ingress (No Port Forward)

**Use Case:** Production-like Kubernetes access via ingress controller

```
┌──────────────────────────────────────────────────────┐
│                 User's Browser                       │
│           http://192.168.49.2 (Minikube IP)         │
└──────────────────────┬───────────────────────────────┘
                       │
                       ▼
┌──────────────────────────────────────────────────────┐
│              Ingress Controller                      │
│                                                       │
│  Route /api  ──────┐       Route /  ──────┐          │
│                    │                       │          │
└────────────────────┼───────────────────────┼──────────┘
                     │                       │
                     ▼                       ▼
┌──────────────────────────────────────────────────────┐
│               Kubernetes Cluster                     │
│                                                       │
│         ┌──────────────┐       ┌─────────────┐       │
│         │  Backend     │       │  Frontend   │       │
│         │  Service     │       │  Service    │       │
│         │   :8081      │       │   :80       │       │
│         └──────┬───────┘       └──────┬──────┘       │
│                │                      │              │
│         ┌──────▼───────┐       ┌──────▼──────┐       │
│         │  Backend     │       │ Frontend    │       │
│         │  Pod         │◀──────│ Pod         │       │
│         │ spring:8081  │ /api  │ nginx:80    │       │
│         └──────┬───────┘       └─────────────┘       │
│                │                                     │
│         ┌──────▼───────┐                             │
│         │   MySQL      │                             │
│         │   Service    │                             │
│         │   :3306      │                             │
│         └──────┬───────┘                             │
│                │                                     │
│         ┌──────▼───────┐                             │
│         │   MySQL      │                             │
│         │   Pod        │                             │
│         │   :3306      │                             │
│         └──────────────┘                             │
│                                                       │
└───────────────────────────────────────────────────────┘
```

**Access Points:**
- Get Minikube IP: `minikube ip` (e.g., 192.168.49.2)
- Frontend: http://192.168.49.2/
- Backend API: http://192.168.49.2/api

**How Requests Flow:**
1. User visits `http://192.168.49.2/`
2. Ingress controller routes to frontend service (port 80)
3. Frontend pod's nginx serves React static files
4. User action triggers API call to `/api/users`
5. Browser sends request to `http://192.168.49.2/api/users`
6. Ingress controller routes `/api` to backend service (port 8081)
7. Backend pod queries MySQL via K8s DNS
8. Response flows back through ingress to browser

**Start Commands:**
```powershell
# Windows
.\deploy-minikube.ps1

# Get Minikube IP
minikube ip

# Start Minikube tunnel (enables ingress)
minikube tunnel
```

```bash
# Linux/Mac
./deploy-minikube.sh

# Get Minikube IP
minikube ip

# Start Minikube tunnel
minikube tunnel
```

---

### Scenario 5: CI/CD E2E Testing

**Use Case:** Automated end-to-end testing in GitHub Actions

```
┌──────────────────────────────────────────────────────┐
│         GitHub Actions Runner (Windows)              │
│                                                       │
│  ┌───────────────┐                                   │
│  │   Cypress     │                                   │
│  │ Test Runner   │                                   │
│  │               │                                   │
│  └───────┬───────┘                                   │
│          │ Visits localhost:3000                     │
│          ▼                                           │
│  ┌───────────────────────────────┐                   │
│  │   Frontend Dev Server         │                   │
│  │   npm start                   │                   │
│  │   Port: 3000                  │                   │
│  │                               │                   │
│  │   Proxy: localhost:16048      │                   │
│  └───────────────┬───────────────┘                   │
│                  │ /api requests                     │
│                  ▼                                   │
│  ┌───────────────────────────────┐                   │
│  │   Backend Process             │                   │
│  │   mvn spring-boot:run         │                   │
│  │   Port: 16048                 │                   │
│  └───────────────┬───────────────┘                   │
│                  │ jdbc:mysql://localhost:3307       │
│                  ▼                                   │
│  ┌───────────────────────────────┐                   │
│  │   MySQL Docker Container      │                   │
│  │   Port: 3307 (or 50307)       │                   │
│  └───────────────────────────────┘                   │
│                                                       │
└───────────────────────────────────────────────────────┘
```

**Port Configuration:**
- Cypress targets: `http://localhost:3000`
- Frontend dev server: `3000`
- Backend: `16048`
- MySQL: `3307` (primary) or `50307` (fallback)

**How Tests Run:**
1. MySQL container starts on port 3307 (or 50307 if conflict)
2. Backend starts with `SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3307/...`
3. Frontend dev server starts on port 3000
4. Wait for all services to be healthy (actuator checks)
5. Cypress runs tests against `http://localhost:3000`
6. Frontend proxies API calls to `localhost:16048`
7. Backend queries MySQL on `localhost:3307`

**Configuration:**
```yaml
# .github/workflows/ci-cd.yml
env:
  MYSQL_PORT: 3307  # (or 50307)
  BACKEND_PORT: 16048
  FRONTEND_PORT: 3000
  CYPRESS_BASE_URL: http://localhost:3000
```

**Cleanup:**
- Cypress artifacts uploaded (screenshots, videos)
- All processes killed
- MySQL container removed

---

## Known Issues & Mismatches

### Issue #1: Frontend Proxy Mismatch ⚠️ CRITICAL

**Status:** Known Issue
**Impact:** HIGH - Breaks local frontend development with Docker Compose backend

#### Problem

The frontend `package.json` proxy configuration does not match the Docker Compose backend port.

```json
// frontend/package.json:60
"proxy": "http://localhost:16048"
```

But Docker Compose exposes backend on:

```yaml
# docker-compose.yml:51
backend:
  ports:
    - "8081:8081"
```

#### Impact

**Broken Scenario:**
1. Developer starts Docker Compose: `docker-compose up`
   - Backend runs on `localhost:8081`
2. Developer starts frontend: `cd frontend && npm start`
   - Frontend runs on `localhost:3000`
   - Proxy configured to `localhost:16048`
3. Developer makes API request
   - Frontend tries to proxy to `localhost:16048`
   - ❌ Nothing listening on 16048
   - **API calls fail**

**Working Scenarios:**
- ✅ Minikube with port forwarding (backend forwarded to 16048)
- ✅ Frontend dev + backend via K8s port-forward
- ✅ CI/CD E2E tests (backend runs on 16048)
- ❌ Frontend dev + Docker Compose backend

#### Root Cause

Port 16048 is used consistently for:
- Minikube port forwarding (`scripts/start-port-forward.ps1`)
- CI/CD E2E tests (`.github/workflows/ci-cd.yml`)

But Docker Compose was configured to use 8081 to match the backend's internal port.

#### Workarounds

**Temporary Fix (Frontend):**
```json
// frontend/package.json
"proxy": "http://localhost:8081"
```

**Temporary Fix (Docker Compose):**
```yaml
# docker-compose.yml
backend:
  ports:
    - "16048:8081"  # Changed from 8081:8081
```

#### Recommendations

**Option A: Standardize on Port 16048 (RECOMMENDED)**

1. Update `docker-compose.yml`:
   ```yaml
   backend:
     ports:
       - "16048:8081"
   ```

2. Keep `frontend/package.json` as-is:
   ```json
   "proxy": "http://localhost:16048"
   ```

3. Update documentation to consistently refer to 16048 as the backend port

**Benefits:**
- ✅ Consistent backend port across all environments (Docker, K8s, CI/CD)
- ✅ No code changes needed to frontend
- ✅ Matches existing port-forward scripts
- ✅ Matches CI/CD configuration

**Option B: Standardize on Port 8081**

1. Keep `docker-compose.yml` as-is
2. Update `frontend/package.json`:
   ```json
   "proxy": "http://localhost:8081"
   ```
3. Update port-forward scripts to use 8081:
   ```powershell
   kubectl port-forward svc/backend 8081:8081 -n smartsplit
   ```
4. Update CI/CD to run backend on 8081

**Drawbacks:**
- ❌ Requires changes to frontend, scripts, and CI/CD
- ❌ Port 8081 less distinctive (could conflict with other apps)
- ❌ More files to update

**Option C: Use Environment Variable**

1. Update `frontend/package.json`:
   ```json
   "proxy": "${REACT_APP_BACKEND_URL:-http://localhost:16048}"
   ```

2. For Docker Compose development, set environment variable:
   ```bash
   export REACT_APP_BACKEND_URL=http://localhost:8081
   npm start
   ```

**Drawbacks:**
- ❌ Requires environment variable management
- ❌ Easy to forget setting the variable
- ❌ More complex developer setup

---

### Issue #2: Inconsistent Backend Port Exposure

**Status:** Known Issue
**Impact:** MEDIUM - Confusing for developers

#### Problem

The backend is exposed on different ports depending on the environment:
- Docker Compose: `8081`
- Minikube port-forward: `16048`
- CI/CD: `16048`

#### Impact

- Developers must remember which port to use in each context
- Documentation must explain multiple scenarios
- Copy-pasting commands may fail if wrong port used

#### Example Confusion

```bash
# Works in Docker Compose
curl http://localhost:8081/api/health

# Fails in Minikube (without changing port)
curl http://localhost:8081/api/health  # ❌ Connection refused

# Must use different port
curl http://localhost:16048/api/health  # ✅ Works
```

#### Recommendation

Standardize on port **16048** for all local backend access. See [Issue #1 Recommendations](#recommendations) for implementation details.

---

### Issue #3: Database URL Configuration Ambiguity

**Status:** Known Issue
**Impact:** LOW - Documentation issue, technically correct

#### Problem

The `.env` file contains:

```properties
SPRING_DATASOURCE_URL=jdbc:mysql://localhost:8082/smartsplit-db
```

This could be interpreted as:
1. Backend should connect to MySQL on `localhost:8082` (WRONG - backend is in Docker)
2. Environment variable for Docker Compose (CORRECT - but overridden)

#### Context

- The `.env` file is used by Docker Compose for environment variables
- Docker Compose backend ignores this value (uses `application-prod.properties` default: `db:3306`)
- Local backend development uses `application-dev.properties`: `127.0.0.1:8082`

#### Current Behavior (Correct)

**Docker Compose Backend:**
```yaml
# docker-compose.yml
backend:
  environment:
    SPRING_DATASOURCE_URL: jdbc:mysql://db:3306/${MYSQL_DATABASE}
```
- Uses Docker service name `db` at port `3306`
- .env value is not used

**Local Backend Development:**
```properties
# application-dev.properties
spring.datasource.url=jdbc:mysql://127.0.0.1:8082/smartsplit-db
```
- Connects to `127.0.0.1:8082` (Docker MySQL with port forwarding)

#### Recommendation

**Add Clarifying Comments:**

```properties
# .env

# NOTE: This SPRING_DATASOURCE_URL is NOT used by Docker Compose backend
# Docker Compose backend uses: jdbc:mysql://db:3306/${MYSQL_DATABASE}
# This value is here as a reference/template only
SPRING_DATASOURCE_URL=jdbc:mysql://localhost:8082/smartsplit-db

# For local backend development (outside Docker), use application-dev.properties
```

**Create .env.local.example:**

```properties
# .env.local.example
# Copy this to .env.local for standalone backend development

SPRING_PROFILES_ACTIVE=dev
SPRING_DATASOURCE_URL=jdbc:mysql://127.0.0.1:8082/smartsplit-db
APP_JWT_SECRET=your-jwt-secret-here
APP_JWT_EXPIRATION_SECONDS=86400
```

---

### Issue #4: Missing Centralized Port Documentation

**Status:** RESOLVED by this document
**Impact:** MEDIUM - Developers had to hunt through multiple files

#### Problem

Port configuration was scattered across:
- `docker-compose.yml`
- Multiple Kubernetes YAML files
- PowerShell and bash scripts
- Backend properties files
- Frontend package.json
- Frontend nginx.conf
- CI/CD workflow file

No single source of truth existed.

#### Resolution

This document (`docs/PORT_CONFIGURATION.md`) provides:
- ✅ Complete port mapping tables
- ✅ Configuration file references with line numbers
- ✅ Environment-specific flows
- ✅ Port-by-port breakdowns
- ✅ Troubleshooting guidance

#### Recommendation

- Link to this document from `README.md` and `CLAUDE.md`
- Update this document when port configurations change
- Add comments in configuration files pointing to this document

---

## Configuration File Reference

Quick reference for all port-related configurations with exact file locations.

### Docker Compose

| Configuration | File | Line | Value |
|--------------|------|------|-------|
| MySQL host port | docker-compose.yml | 9 | 8082:3306 |
| Backend host port | docker-compose.yml | 51 | 8081:8081 |
| Frontend host port | docker-compose.yml | 60 | 8080:80 |
| Backend datasource URL | docker-compose.yml | 49 | jdbc:mysql://db:3306/${MYSQL_DATABASE} |

### Kubernetes

| Configuration | File | Line | Value |
|--------------|------|------|-------|
| MySQL service port | k8s/mysql/mysql-deployment.yaml | 103 | 3306 |
| MySQL container port | k8s/mysql/mysql-deployment.yaml | 58 | 3306 |
| Backend service port | k8s/backend/backend-deployment.yaml | 110 | 8081 |
| Backend container port | k8s/backend/backend-deployment.yaml | 35 | 8081 |
| Backend datasource URL | k8s/backend/backend-deployment.yaml | 44-45 | jdbc:mysql://mysql.smartsplit.svc.cluster.local:3306/smartsplit-db |
| Frontend service port | k8s/frontend/frontend-deployment.yaml | 42 | 80 |
| Frontend container port | k8s/frontend/frontend-deployment.yaml | 23 | 80 |
| Ingress backend port | k8s/ingress.yaml | 18 | 8081 |
| Ingress frontend port | k8s/ingress.yaml | 25 | 80 |

### Frontend

| Configuration | File | Line | Value |
|--------------|------|------|-------|
| Dev server proxy | frontend/package.json | 60 | http://localhost:16048 |
| API base URL | frontend/src/utils/api.ts | 13 | /api |
| nginx proxy pass | frontend/nginx.conf | 14 | http://backend.smartsplit.svc.cluster.local:8081 |

### Backend

| Configuration | File | Line | Value |
|--------------|------|------|-------|
| Server port (dev) | backend/src/main/resources/application-dev.properties | 2 | 8081 |
| Datasource URL (dev) | backend/src/main/resources/application-dev.properties | 5 | jdbc:mysql://127.0.0.1:8082/smartsplit-db |
| Server port (prod) | backend/src/main/resources/application-prod.properties | 2 | 8081 |
| Datasource URL (prod) | backend/src/main/resources/application-prod.properties | 15 | jdbc:mysql://db:3306/smartsplit-db (default) |

### Port Forwarding Scripts

| Configuration | File | Line | Value |
|--------------|------|------|-------|
| Frontend port forward | scripts/start-port-forward.ps1 | 10 | 3003:80 |
| Backend port forward | scripts/start-port-forward.ps1 | 15 | 16048:8081 |
| MySQL port forward | scripts/start-port-forward.ps1 | 20 | 8082:3306 |
| Port availability check | scripts/port-forward.ps1 | 45 | Test-NetConnection |

### CI/CD

| Configuration | File | Line | Value |
|--------------|------|------|-------|
| MySQL port selection | .github/workflows/ci-cd.yml | 234-236 | 3307 or 50307 |
| MySQL container mapping | .github/workflows/ci-cd.yml | 276 | ${mysqlPort}:3306 |
| Backend datasource URL | .github/workflows/ci-cd.yml | 286 | jdbc:mysql://localhost:${mysqlPort}/smartsplit-db |
| Backend server port | .github/workflows/ci-cd.yml | 378 | (default 8080, but logs show 16048) |
| Frontend dev server | .github/workflows/ci-cd.yml | 391 | npm start (port 3000) |
| Cypress base URL | .github/workflows/ci-cd.yml | 430 | http://localhost:3000 |
| Cypress config | cypress.config.js | 5 | process.env.CYPRESS_BASE_URL \|\| 'http://localhost:3000/' |

### Environment Variables

| Configuration | File | Line | Value |
|--------------|------|------|-------|
| Datasource URL | .env | 3 | jdbc:mysql://localhost:8082/smartsplit-db |
| JWT Secret | .env | 4 | ${APP_JWT_SECRET} |
| JWT Expiration | .env | 5 | 86400 |

---

## Troubleshooting

### Port Already in Use

**Symptoms:**
```
Error: bind EADDRINUSE :::3000
Error: Port 8081 is already in use
```

**Solution:**

**Windows (PowerShell):**
```powershell
# Find what's using a port
Get-NetTCPConnection -LocalPort 3000 | Select-Object -Property LocalPort, State, OwningProcess
Get-Process -Id <ProcessID>

# Kill process
Stop-Process -Id <ProcessID> -Force

# Or use the provided script
.\scripts\check-ports.ps1
```

**Linux/Mac:**
```bash
# Find what's using a port
lsof -i :3000

# Kill process
kill -9 <PID>

# Or use the provided script
./scripts/check-ports.sh
```

### Frontend Cannot Reach Backend

**Symptoms:**
- Network errors in browser console
- `ERR_CONNECTION_REFUSED` or `ECONNREFUSED`
- 404 errors on `/api` requests

**Diagnosis:**

1. Check which environment you're running:
   ```bash
   # Check if backend is running and on which port
   curl http://localhost:8081/actuator/health
   curl http://localhost:16048/actuator/health
   ```

2. Check frontend proxy configuration:
   ```bash
   # Check package.json proxy
   grep -A 1 "proxy" frontend/package.json
   ```

3. Verify ports match:
   - If backend is on 8081, frontend proxy should be `localhost:8081`
   - If backend is on 16048 (K8s port-forward), frontend proxy should be `localhost:16048`

**Solutions:**

**Quick Fix (Temporary):**
```json
// frontend/package.json
// Change proxy to match your backend port
"proxy": "http://localhost:8081"  // For Docker Compose
// OR
"proxy": "http://localhost:16048"  // For Minikube
```

**Permanent Fix:**
See [Issue #1 Recommendations](#recommendations)

### Database Connection Failed

**Symptoms:**
```
Communications link failure
The last packet sent successfully to the server was 0 milliseconds ago
```

**Diagnosis:**

1. Check MySQL is running:
   ```bash
   # Docker Compose
   docker-compose ps

   # Kubernetes
   kubectl get pods -n smartsplit
   ```

2. Check port forwarding (if using Minikube):
   ```powershell
   # Windows
   Get-Process | Where-Object { $_.ProcessName -eq "kubectl" }

   # Check if port-forward is active
   netstat -ano | findstr :8082
   ```

3. Test MySQL connection:
   ```bash
   # Docker Compose or Minikube port-forward
   mysql -h 127.0.0.1 -P 8082 -u root -p
   ```

**Solutions:**

**Docker Compose:**
```bash
# Restart MySQL
docker-compose restart db

# Check logs
docker-compose logs db
```

**Kubernetes:**
```bash
# Check pod status
kubectl get pod -n smartsplit -l app=mysql

# Check logs
kubectl logs -n smartsplit -l app=mysql

# Restart port forwarding
.\scripts\stop-port-forward.ps1
.\scripts\start-port-forward.ps1
```

### Minikube Ingress Not Working

**Symptoms:**
- Cannot access `http://MINIKUBE_IP`
- Connection timeout or refused

**Diagnosis:**

1. Check Minikube is running:
   ```bash
   minikube status
   ```

2. Check ingress addon:
   ```bash
   minikube addons list | grep ingress
   ```

3. Check ingress controller:
   ```bash
   kubectl get pods -n ingress-nginx
   ```

**Solutions:**

```bash
# Enable ingress addon
minikube addons enable ingress

# Restart Minikube tunnel
minikube tunnel

# Verify ingress
kubectl get ingress -n smartsplit

# Check ingress controller logs
kubectl logs -n ingress-nginx -l app.kubernetes.io/name=ingress-nginx
```

### Port Forwarding Keeps Dying

**Symptoms:**
- Port forwarding stops after a few minutes
- `kubectl port-forward` process exits

**Diagnosis:**

1. Check port-forward process:
   ```powershell
   # Windows
   Get-Process | Where-Object { $_.ProcessName -eq "kubectl" }
   ```

2. Check for error messages:
   ```bash
   kubectl port-forward svc/backend 16048:8081 -n smartsplit
   # Look for error output
   ```

**Solutions:**

**Use Background Scripts:**
```powershell
# Windows - scripts automatically restart
.\scripts\start-port-forward.ps1
```

**Manual Restart:**
```bash
# Add --address flag for stability
kubectl port-forward --address 0.0.0.0 svc/backend 16048:8081 -n smartsplit
```

**Use `kubectl` keep-alive:**
```bash
# Run in loop (Linux/Mac)
while true; do
  kubectl port-forward svc/backend 16048:8081 -n smartsplit
  sleep 2
done
```

### CI/CD E2E Tests Failing - Port Conflicts

**Symptoms:**
- E2E test stage fails with "Port already in use"
- MySQL container won't start

**Diagnosis:**

Check GitHub Actions logs for:
```
Error: Port 3307 is already in use
Error: Port 3000 is already in use
```

**Solutions:**

The CI/CD workflow includes automatic port conflict resolution:

```yaml
# Automatically tries 50307 if 3307 is in use
$mysqlPort = 3307
if (Test-NetConnection -ComputerName localhost -Port $mysqlPort -InformationLevel Quiet) {
    $mysqlPort = 50307
}
```

If tests continue to fail:

1. Check if previous test containers weren't cleaned up:
   ```bash
   docker ps -a | grep smartsplit-mysql-test
   docker rm -f smartsplit-mysql-test
   ```

2. Check runner cleanup scripts

3. Manually specify fallback ports in workflow

### Browser Shows 502 Bad Gateway (Kubernetes)

**Symptoms:**
- Frontend loads but shows "502 Bad Gateway" on API calls
- Only happens in Kubernetes environment

**Diagnosis:**

1. Check backend pod is running:
   ```bash
   kubectl get pods -n smartsplit -l app=backend
   ```

2. Check backend logs:
   ```bash
   kubectl logs -n smartsplit -l app=backend
   ```

3. Test backend directly:
   ```bash
   kubectl port-forward svc/backend 16048:8081 -n smartsplit
   curl http://localhost:16048/actuator/health
   ```

**Solutions:**

**Backend Pod Crashed:**
```bash
# Check why pod crashed
kubectl describe pod -n smartsplit -l app=backend

# Common issues:
# - Database connection failed (check MySQL pod)
# - Missing environment variables (check ConfigMap/Secrets)
# - Image pull error (check image tag)
```

**Service Configuration Mismatch:**
```bash
# Verify service selector matches pod labels
kubectl get svc backend -n smartsplit -o yaml
kubectl get pod -n smartsplit -l app=backend --show-labels

# Selector and labels must match
```

**Nginx Configuration Error:**
```bash
# Check frontend nginx logs
kubectl logs -n smartsplit -l app=frontend

# Verify nginx.conf proxy_pass URL
kubectl exec -n smartsplit -l app=frontend -- cat /etc/nginx/conf.d/default.conf
```

---

## Quick Reference Commands

### Check What's Running on Ports

**Windows:**
```powershell
# Use provided script
.\scripts\check-ports.ps1

# Manual check
netstat -ano | findstr ":3000 :3003 :8080 :8081 :8082 :16048"
```

**Linux/Mac:**
```bash
# Use provided script
./scripts/check-ports.sh

# Manual check
lsof -i :3000 -i :3003 -i :8080 -i :8081 -i :8082 -i :16048
```

### Start All Services

**Docker Compose:**
```bash
docker-compose up
```

**Kubernetes:**
```powershell
# Windows
.\deploy-minikube.ps1
.\scripts\start-port-forward.ps1
```

```bash
# Linux/Mac
./deploy-minikube.sh
./scripts/start-port-forward.sh
```

### Test Service Availability

```bash
# Frontend (Docker Compose)
curl http://localhost:8080

# Frontend (Minikube)
curl http://localhost:3003

# Backend (Docker Compose)
curl http://localhost:8081/actuator/health

# Backend (Minikube)
curl http://localhost:16048/actuator/health

# MySQL (requires mysql client)
mysql -h 127.0.0.1 -P 8082 -u root -p
```

### Stop All Services

**Docker Compose:**
```bash
docker-compose down
```

**Kubernetes:**
```powershell
# Windows
.\scripts\stop-port-forward.ps1
kubectl delete namespace smartsplit
```

```bash
# Linux/Mac
./scripts/stop-port-forward.sh
kubectl delete namespace smartsplit
```

---

## Appendix: Port Selection Rationale

### Why These Specific Ports?

**Port 3000:** Standard React development server port (Create React App default)

**Port 3003:** Avoids conflict with 3000, memorable (3000 + 3), distinctive for Kubernetes frontend

**Port 8080:** Common HTTP alternative port, standard for web applications

**Port 8081:** Common for backend APIs, avoids conflict with 8080, standard Spring Boot alternative

**Port 8082:** Sequential after 8081, avoids conflict with common MySQL port 3306

**Port 16048:** Distinctive 5-digit port, unlikely to conflict, chosen for consistency

**Port 3306:** MySQL default, unchanged for standard tools compatibility

**Port 3307:** Common MySQL alternative, next sequential port after 3306

**Port 50307:** High port number, very unlikely to conflict, obvious fallback from 3307

### Port Assignment Philosophy

1. **Use standard ports when possible** (3000 for React, 3306 for MySQL, 8080 for HTTP)
2. **Avoid conflicts with system services** (no ports <1024)
3. **Sequential numbering for related services** (8081, 8082)
4. **Memorable mappings** (3003 = 3000 + 3, frontend-related)
5. **High ports for fallbacks** (50307 very unlikely to conflict)
6. **Consistency across environments** (16048 used for backend in K8s and CI/CD)

---

**Document Version:** 1.0
**Last Updated:** 2025-11-20
**Maintained By:** DevOps Team
**Related Documents:** [CLAUDE.md](../CLAUDE.md), [README.md](../README.md)
