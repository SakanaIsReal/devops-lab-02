# CI/CD Flow Guide for Beginners

**A comprehensive guide to understanding SmartSplit's automated deployment pipeline**

---

## Table of Contents

1. [What is CI/CD?](#what-is-cicd)
2. [SmartSplit Pipeline Overview](#smartsplit-pipeline-overview)
3. [Prerequisites & Setup](#prerequisites--setup)
4. [Pipeline Stages Explained](#pipeline-stages-explained)
5. [Key Concepts for Beginners](#key-concepts-for-beginners)
6. [Hands-On: Triggering the Pipeline](#hands-on-triggering-the-pipeline)
7. [Troubleshooting Guide](#troubleshooting-guide)
8. [Advanced Topics](#advanced-topics)
9. [Quick Reference](#quick-reference)

---

## What is CI/CD?

### The Problem CI/CD Solves

Imagine you're working on a team project. Every time someone makes a change:
- You need to manually test everything
- You need to build the application
- You need to deploy it to the server
- You might forget a step or make a mistake

**CI/CD automates all of this!**

### Breaking Down the Acronym

**CI = Continuous Integration**
- Automatically test code changes when developers push to the repository
- Catch bugs early before they reach production
- Ensure new code works with existing code

**CD = Continuous Deployment**
- Automatically deploy tested code to production
- No manual steps required
- Fast, reliable, repeatable deployments

### Why It Matters

✅ **Faster releases** - Deploy multiple times per day instead of once per week
✅ **Fewer bugs** - Automated tests catch issues before users see them
✅ **Less stress** - No more manual deployment procedures
✅ **Better quality** - Consistent process every time
✅ **Quick rollbacks** - Easy to revert if something goes wrong

---

## SmartSplit Pipeline Overview

### The Big Picture

When you push code to the `main` branch, here's what happens automatically:

```
┌──────────────────────────────────────────────────────────────────┐
│                    GITHUB ACTIONS PIPELINE                        │
│                  (Self-Hosted Windows Runner)                     │
└──────────────────────────────────────────────────────────────────┘

  Push to 'main' branch
         │
         ▼
    ┌─────────┐
    │  STAGE  │  ⏱️ ~2-3 minutes
    │    1    │  Run backend unit tests
    │  TEST   │  Install frontend dependencies
    └────┬────┘
         │ ✅ Tests pass
         ▼
    ┌─────────┐
    │  STAGE  │  ⏱️ ~5-7 minutes
    │    2    │  Start MySQL database
    │ E2E-TEST│  Build & start backend + frontend
    └────┬────┘  Run Cypress end-to-end tests
         │ ✅ E2E tests pass
         ▼
    ┌─────────┐
    │  STAGE  │  ⏱️ ~3-5 minutes
    │    3    │  Build Docker images
    │  BUILD  │  Push to Docker Hub
    └────┬────┘  Tag with :latest and :git-sha
         │ ✅ Images built and pushed
         ▼
    ┌─────────┐
    │  STAGE  │  ⏱️ ~2-3 minutes
    │    4    │  Update Kubernetes deployment
    │ DEPLOY  │  Wait for rollout to complete
    └────┬────┘  Verify pods are running
         │ ✅ Deployment successful
         ▼
   🎉 Live in Production!
```

### Total Pipeline Time

**~12-18 minutes** from code push to production deployment

### What Happens on Failure?

If any stage fails, the pipeline **stops immediately**:
- ❌ Test fails → No E2E tests run
- ❌ E2E test fails → No Docker build
- ❌ Build fails → No deployment
- ❌ Deploy fails → Previous version keeps running (safe!)

This prevents broken code from reaching production.

---

## Prerequisites & Setup

### Required GitHub Secrets

Before the pipeline can run, you need to configure these secrets in your GitHub repository:

**Settings → Secrets and variables → Actions → New repository secret**

| Secret Name | Description | Example |
|-------------|-------------|---------|
| `DOCKERHUB_USERNAME` | Your Docker Hub username | `sakanaisreal` |
| `DOCKERHUB_TOKEN` | Docker Hub access token | `dckr_pat_xxxxx...` |
| `APP_JWT_SECRET` | JWT secret for authentication | `your-256-bit-secret-key-here` |

> **💡 Tip:** Generate a strong JWT secret with: `openssl rand -base64 64`

### Self-Hosted Runner Requirements

SmartSplit uses a **self-hosted Windows runner** instead of GitHub's cloud servers. This means you need a Windows computer configured as a runner.

**Hardware Requirements:**
- Windows 10/11 or Windows Server
- 8GB+ RAM (16GB recommended)
- 50GB+ free disk space
- Reliable internet connection

**Software Requirements:**
- Docker Desktop with Hyper-V or WSL2
- PowerShell 5.1+ or PowerShell Core 7+
- Git
- kubectl (configured to access your Kubernetes cluster)

**Setup Guide:** See [`.github/RUNNER_SETUP.md`](../.github/RUNNER_SETUP.md) for complete runner setup instructions.

### Local Development Setup

To run the pipeline locally or understand what it does:

**Backend:**
```bash
cd backend
mvn test -DskipITs=true    # Run unit tests
mvn verify                  # Run all tests including integration tests
mvn clean package          # Build JAR file
```

**Frontend:**
```bash
cd frontend
npm install --legacy-peer-deps  # Install dependencies
npm start                       # Start dev server (port 3000)
npm test                        # Run unit tests
npm run test:e2e               # Run Cypress E2E tests
```

**E2E Tests:**
```bash
# From project root
npx cypress run         # Run headless
npx cypress open        # Open interactive test runner
```

---

## Pipeline Stages Explained

### Stage 1: Test (Unit Testing)

**Purpose:** Verify that individual components work correctly in isolation.

#### What Happens Step-by-Step

1. **Checkout Code** 📥
   - GitHub Actions downloads your repository code
   - Tool: `actions/checkout@v4`

2. **Setup Node.js** ⚙️
   - Installs Node.js version 20
   - Required for frontend dependencies and Cypress

3. **Install Frontend Dependencies** 📦
   ```bash
   cd frontend
   Remove-Item -Recurse -Force node_modules, package-lock.json
   npm install --legacy-peer-deps
   npm install --save-dev jest @testing-library/react @testing-library/jest-dom
   npm install cypress --save-dev
   ```
   - Cleans previous installations (prevents conflicts)
   - `--legacy-peer-deps` handles dependency version mismatches
   - Installs Jest for unit tests and Cypress for E2E tests

4. **Run Backend Unit Tests** 🧪
   ```bash
   cd backend
   mvn test -DskipITs=true
   ```
   - `-DskipITs=true` skips integration tests (faster, no database needed)
   - Tests use in-memory H2 database instead of MySQL
   - Environment: `SPRING_PROFILES_ACTIVE=test`

#### Technologies Used

- **Maven** - Backend build tool and dependency management
- **JUnit** - Java testing framework
- **Spring Boot Test** - Testing utilities for Spring applications
- **Jest** - JavaScript testing framework (installed but not run in this stage)
- **npm** - Node.js package manager

#### Why This Stage Matters

Unit tests catch:
- Logic errors in individual functions
- Null pointer exceptions
- Type mismatches
- Edge cases

If unit tests fail, there's no point in running expensive E2E tests.

#### Common Failures & Solutions

**❌ "Tests failed: Expected 5 but got 6"**
- **Cause:** Logic error in code
- **Solution:** Review the failing test output, fix the code logic

**❌ "BUILD FAILURE: Compilation error"**
- **Cause:** Syntax error or missing import
- **Solution:** Fix compilation errors in the code

**❌ "npm install failed"**
- **Cause:** Network issues or corrupted cache
- **Solution:** Runner will retry, or manually clear npm cache

---

### Stage 2: E2E-Test (End-to-End Testing)

**Purpose:** Test the complete application stack as users would experience it.

This is the **most complex stage** because it runs the entire application (database, backend, frontend) and simulates real user interactions.

#### What Happens Step-by-Step

##### 1. Cleanup Previous Test Runs 🧹

```powershell
# Stop all Java and Node processes
Get-Process java, node -ErrorAction SilentlyContinue | Stop-Process -Force

# Remove previous MySQL container
docker rm -f mysql-ci

# Remove previous network
docker network rm smartsplit-ci-network

# Kill processes using test ports
Stop-Process -Id (Get-NetTCPConnection -LocalPort 3307,16048,3003).OwningProcess -Force
```

**Why?** Previous test runs might have left processes or containers running, causing port conflicts.

##### 2. Handle Windows Hyper-V Port Issues 🔧

**The Problem:** Windows Hyper-V reserves random port ranges that can block Docker from binding to specific ports.

```powershell
# Check if port 3307 is in a reserved range
netsh interface ipv4 show excludedportrange protocol=tcp

# If blocked, restart WinNAT service to clear stale reservations
Restart-Service WinNat -Force
Start-Sleep -Seconds 5

# If still blocked, use fallback port 50307
```

**Why This Matters:** Without this workaround, MySQL container fails to start with "port already in use" error.

> **📖 Learn More:** See [`.github/RUNNER_SETUP.md`](../.github/RUNNER_SETUP.md) for detailed explanation of Hyper-V port conflicts.

##### 3. Start MySQL Database 🗄️

```powershell
# Create isolated Docker network
docker network create smartsplit-ci-network

# Start MySQL container
docker run -d `
  --name mysql-ci `
  --network smartsplit-ci-network `
  -e MYSQL_ROOT_PASSWORD=rootpassword `
  -e MYSQL_DATABASE=smartsplit-db `
  -p 3307:3306 `
  --health-cmd="mysqladmin ping -h localhost -u root -prootpassword" `
  --health-interval=10s `
  --health-timeout=5s `
  --health-retries=5 `
  mysql:8.0

# Wait for MySQL to be healthy
while ((docker inspect --format='{{.State.Health.Status}}' mysql-ci) -ne 'healthy') {
    Start-Sleep -Seconds 2
}
```

**Key Points:**
- Uses port **3307** (not default 3306) to avoid conflicts
- Health check ensures MySQL is truly ready before proceeding
- Isolated network prevents interference with other containers

##### 4. Build and Start Backend ⚙️

```powershell
cd backend

# Build the application
mvn clean package -DskipTests

# Verify JAR file was created
if (-not (Test-Path "target/smartsplit-back-0.0.1-SNAPSHOT.jar")) {
    throw "Backend JAR file not found after build!"
}

# Start the backend
$env:SPRING_PROFILES_ACTIVE="test"
$env:APP_JWT_SECRET="${{ secrets.APP_JWT_SECRET }}"
Start-Process java -ArgumentList `
  "-jar", "target/smartsplit-back-0.0.1-SNAPSHOT.jar", `
  "--server.port=16048", `
  "--spring.datasource.url=jdbc:mysql://localhost:3307/smartsplit-db" `
  -RedirectStandardOutput "backend-output.log" `
  -RedirectStandardError "backend-error.log"

# Wait for backend to initialize
Start-Sleep -Seconds 40
```

**Key Points:**
- Port **16048** (not default 8080) matches Cypress test configuration
- Connects to MySQL on port 3307
- 40-second wait ensures Spring Boot fully initializes
- Logs redirected to files for debugging

##### 5. Install Frontend Dependencies & Start Frontend 🎨

```powershell
cd frontend

# Install dependencies
npm install --legacy-peer-deps

# Start frontend dev server on port 3003
$env:PORT="3003"
$env:BROWSER="none"  # Don't auto-open browser
Start-Process npm -ArgumentList "start" `
  -RedirectStandardOutput "frontend-output.log" `
  -RedirectStandardError "frontend-error.log"

# Wait for frontend to build and start
Start-Sleep -Seconds 60
```

**Key Points:**
- Port **3003** (not default 3000) ensures no conflicts
- `BROWSER=none` prevents automatic browser opening
- 60-second wait ensures Webpack build completes

##### 6. Verify Services Started ✅

```powershell
# Check if processes are running
Get-Process java
Get-Process node

# Verify ports are listening
Get-NetTCPConnection -LocalPort 3003,16048

# Test HTTP accessibility
Invoke-WebRequest -Uri "http://localhost:3003" -Method GET
Invoke-WebRequest -Uri "http://localhost:16048/api/actuator/health" -Method GET
```

**Why?** Ensures services actually started before running expensive Cypress tests.

##### 7. Wait for Services to Be Ready ⏳

```powershell
$maxAttempts = 30
$attempt = 0

while ($attempt -lt $maxAttempts) {
    try {
        # Check frontend
        $frontendResponse = Invoke-WebRequest -Uri "http://localhost:3003" -UseBasicParsing

        # Check backend health endpoint
        $backendResponse = Invoke-WebRequest -Uri "http://localhost:16048/api/actuator/health" -UseBasicParsing

        if ($frontendResponse.StatusCode -eq 200 -and $backendResponse.StatusCode -eq 200) {
            Write-Host "✅ All services ready!"
            break
        }
    } catch {
        Write-Host "⏳ Waiting for services... (Attempt $attempt/$maxAttempts)"
        Start-Sleep -Seconds 5
        $attempt++
    }
}
```

**Key Points:**
- Polls services every 5 seconds
- Max 30 attempts = 150 seconds timeout
- Uses Spring Boot Actuator health check for backend

##### 8. Run Cypress E2E Tests 🧪

```powershell
cd ..  # Back to project root
npx cypress run --headless --browser chrome
```

**What Cypress Tests:**

The E2E test suite ([`cypress/e2e/SmartSplit-E2E.cy.js`](../cypress/e2e/SmartSplit-E2E.cy.js)) simulates a real user:

1. **Sign Up Flow**
   - Navigate to signup page
   - Fill in username, email, password
   - Submit form
   - Verify redirect to login page

2. **Sign In Flow**
   - Enter credentials
   - Click sign in button
   - Verify redirect to home page
   - Verify JWT token stored

3. **Profile Management**
   - Navigate to profile page
   - Edit profile information
   - Save changes
   - Verify changes persisted

4. **Group Management**
   - Create a new group
   - Add members to group
   - Edit group details
   - Delete group

5. **Expense Management**
   - Create expense with equal split
   - Create expense with manual split
   - View expense details
   - Verify calculations

6. **Payment Processing**
   - Record payment
   - Verify balances updated
   - Check transaction history

7. **Sign Out Flow**
   - Click sign out button
   - Verify redirect to login page
   - Verify token cleared

**Total Test Duration:** ~3-5 minutes

##### 9. Upload Test Artifacts 📤

```yaml
- name: Upload Cypress screenshots
  if: failure()  # Only on test failure
  uses: actions/upload-artifact@v3
  with:
    name: cypress-screenshots
    path: cypress/screenshots

- name: Upload Cypress videos
  if: always()  # Always upload videos
  uses: actions/upload-artifact@v3
  with:
    name: cypress-videos
    path: cypress/videos
```

**Where to Find Artifacts:**
1. Go to GitHub Actions run
2. Scroll to bottom of page
3. Download "Artifacts" section

**Artifact Types:**
- **Screenshots** - Only saved when tests fail (shows exact failure point)
- **Videos** - Always saved (shows full test execution)

##### 10. Cleanup (Always Runs) 🧹

```powershell
# Stop all Java and Node processes
Get-Process java, node -ErrorAction SilentlyContinue | Stop-Process -Force

# Remove MySQL container and network
docker rm -f mysql-ci
docker network rm smartsplit-ci-network

# Kill processes on test ports
Stop-Process -Id (Get-NetTCPConnection -LocalPort 3307,16048,3003).OwningProcess -Force
```

**Why `if: always()`?** Cleanup runs even if tests fail, preventing port conflicts on next run.

#### Technologies Used

- **Docker** - Containerizes MySQL for isolated testing
- **Cypress** - Modern E2E testing framework
- **Spring Boot Actuator** - Provides health check endpoints
- **MySQL 8.0** - Database for test data
- **PowerShell** - Orchestrates the entire test process

#### Why This Stage Matters

E2E tests catch:
- Integration issues between frontend and backend
- API contract violations
- UI/UX bugs
- Authentication/authorization issues
- Database schema mismatches
- Timing/race conditions

**Real Example:** A unit test might pass, but E2E test reveals that the "Create Group" button doesn't actually save to the database.

#### Common Failures & Solutions

**❌ "Port 3307 already in use"**
- **Cause:** Previous test run didn't cleanup, or Hyper-V reserved the port
- **Solution:** Pipeline automatically uses fallback port 50307

**❌ "Backend health check timeout"**
- **Cause:** Backend failed to start (check logs)
- **Solution:** Download `backend-output.log` artifact to debug

**❌ "Cypress test failed: Element not found"**
- **Cause:** UI changed but test wasn't updated
- **Solution:** Update Cypress selectors to match new UI

**❌ "MySQL connection refused"**
- **Cause:** MySQL container not fully initialized
- **Solution:** Pipeline automatically waits for health check

---

### Stage 3: Build-and-Push (Docker Image Creation)

**Purpose:** Package the application into Docker images and push to Docker Hub.

#### What Are Docker Images?

Think of Docker images like **shipping containers**:
- Standard format that works anywhere
- Contains everything the app needs (code, dependencies, runtime)
- Can be deployed to any server with Docker

#### What Happens Step-by-Step

##### 1. Verify Docker is Running ✅

```powershell
$attempts = 0
while ($attempts -lt 10) {
    try {
        docker info | Out-Null
        Write-Host "✅ Docker is running"
        break
    } catch {
        Write-Host "⏳ Waiting for Docker..."
        Start-Sleep -Seconds 5
        $attempts++
    }
}
```

**Why?** Docker Desktop can take time to start, especially after runner reboot.

##### 2. Log in to Docker Hub 🔐

```yaml
- name: Log in to Docker Hub
  uses: docker/login-action@v3
  with:
    username: ${{ secrets.DOCKERHUB_USERNAME }}
    password: ${{ secrets.DOCKERHUB_TOKEN }}
```

**Why?** You need authentication to push images to your Docker Hub repository.

##### 3. Build Backend Image 🏗️

```powershell
cd backend
docker build -t sakanaisreal/smartsplit-backend:latest `
             -t sakanaisreal/smartsplit-backend:${{ github.sha }} `
             .
```

**Dockerfile Breakdown:** [`backend/Dockerfile`](../backend/Dockerfile)

```dockerfile
# ===== STAGE 1: Build =====
FROM maven:3.9.8-eclipse-temurin-17 AS build

WORKDIR /app
COPY pom.xml .
# Download dependencies first (cached layer)
RUN mvn dependency:go-offline

COPY src ./src
# Build the application
RUN mvn clean package -DskipTests

# ===== STAGE 2: Runtime =====
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Create non-root user for security
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Copy only the JAR file from build stage
COPY --from=build /app/target/*.jar app.jar

# Configure JVM for container environment
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

# Health check configuration
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/api/actuator/health || exit 1

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

**Multi-Stage Build Explained:**

**Why Two Stages?**
- **Stage 1 (build):** Uses full Maven + JDK (large, ~800MB)
- **Stage 2 (runtime):** Uses only JRE (small, ~200MB)
- **Result:** Final image is 75% smaller!

**Security Best Practices:**
- ✅ Runs as non-root user (`spring:spring`)
- ✅ Uses Alpine Linux (minimal attack surface)
- ✅ Only includes runtime dependencies

**Performance Optimizations:**
- ✅ Dependency layer cached separately (faster rebuilds)
- ✅ JVM configured for containers (`UseContainerSupport`)
- ✅ Memory limit set (`MaxRAMPercentage=75%`)

##### 4. Build Frontend Image 🏗️

```powershell
cd frontend
docker build -t sakanaisreal/smartsplit-frontend:latest `
             -t sakanaisreal/smartsplit-frontend:${{ github.sha }} `
             .
```

**Dockerfile Breakdown:** [`frontend/Dockerfile`](../frontend/Dockerfile)

```dockerfile
# ===== STAGE 1: Build =====
FROM node:18-alpine AS build

WORKDIR /app

# Copy dependency files first (cached layer)
COPY package*.json ./
RUN npm install --legacy-peer-deps

# Copy source code and build
COPY . .
RUN npm run build

# ===== STAGE 2: Serve =====
FROM nginx:stable-alpine

# Copy built files to nginx
COPY --from=build /app/build /usr/share/nginx/html

# Copy custom nginx configuration
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
```

**Multi-Stage Build Explained:**

**Stage 1 (build):**
- Uses Node.js to compile React app
- Runs `npm run build` (creates optimized production bundle)
- Includes Webpack, Babel, etc. (large, ~500MB)

**Stage 2 (serve):**
- Uses Nginx to serve static files
- Only includes built HTML/JS/CSS files
- No Node.js needed at runtime (small, ~50MB)

**Result:** Final image is 90% smaller!

**Nginx Configuration:** [`frontend/nginx.conf`](../frontend/nginx.conf)

```nginx
server {
    listen 80;
    root /usr/share/nginx/html;
    index index.html;

    # Serve React app for all routes (client-side routing)
    location / {
        try_files $uri $uri/ /index.html;
    }

    # Cache static assets
    location /static/ {
        expires 1y;
        add_header Cache-Control "public, immutable";
    }
}
```

##### 5. Push Images to Docker Hub 📤

```powershell
# Push backend images (both tags)
docker push sakanaisreal/smartsplit-backend:latest
docker push sakanaisreal/smartsplit-backend:${{ github.sha }}

# Push frontend images (both tags)
docker push sakanaisreal/smartsplit-frontend:latest
docker push sakanaisreal/smartsplit-frontend:${{ github.sha }}
```

**Dual Tagging Strategy Explained:**

| Tag | Example | Usage |
|-----|---------|-------|
| `:latest` | `smartsplit-backend:latest` | Local development, quick testing |
| `:git-sha` | `smartsplit-backend:0bec405` | Production deployments |

**Why Two Tags?**

**`:latest` Tag:**
- ✅ Convenient for local development
- ✅ Always points to most recent build
- ❌ Not immutable (changes over time)
- ❌ Hard to track what's deployed

**`:git-sha` Tag:**
- ✅ Immutable (never changes)
- ✅ Clear audit trail (tied to git commit)
- ✅ Easy rollbacks (just use previous SHA)
- ✅ Guaranteed consistency across environments
- ⚠️ Requires tracking SHA values

**Production Deployment Rule:** **ALWAYS use git SHA tags, NEVER use `:latest`**

**Example:**
```bash
# ❌ BAD (not auditable, hard to rollback)
kubectl set image deployment/backend backend=sakanaisreal/smartsplit-backend:latest

# ✅ GOOD (immutable, traceable, easy to rollback)
kubectl set image deployment/backend backend=sakanaisreal/smartsplit-backend:0bec405
```

#### Technologies Used

- **Docker** - Containerization platform
- **Docker Hub** - Image registry (like GitHub for Docker images)
- **Multi-stage builds** - Optimization technique
- **Maven** - Backend build tool
- **npm** - Frontend build tool
- **Nginx** - Web server for serving React app

#### Why This Stage Matters

Docker images enable:
- **Consistency:** Same image runs identically everywhere (dev, staging, production)
- **Portability:** Works on any system with Docker
- **Isolation:** Each service has its own dependencies
- **Scalability:** Easy to run multiple instances
- **Rollbacks:** Can deploy previous versions instantly

#### Common Failures & Solutions

**❌ "Docker login failed: unauthorized"**
- **Cause:** Invalid Docker Hub credentials
- **Solution:** Update `DOCKERHUB_TOKEN` secret with new access token

**❌ "docker build failed: no space left on device"**
- **Cause:** Runner disk full with old images
- **Solution:** Run `docker system prune -a -f` on runner

**❌ "docker push failed: denied"**
- **Cause:** Don't have permission to push to repository
- **Solution:** Verify Docker Hub repository exists and you have write access

**❌ "Build failed: npm ERR! code ELIFECYCLE"**
- **Cause:** Frontend build error (TypeScript/ESLint error)
- **Solution:** Fix the error shown in logs (TypeScript error, missing import, etc.)

---

### Stage 4: Deploy (Kubernetes Deployment)

**Purpose:** Update the production Kubernetes cluster with new Docker images.

#### What is Kubernetes?

**Kubernetes** (K8s) is like an **orchestrator for Docker containers**:
- Automatically runs containers across multiple servers
- Restarts containers if they crash
- Routes traffic to healthy containers
- Scales up/down based on demand
- Performs zero-downtime deployments

**SmartSplit's Kubernetes Setup:**
- **Minikube:** Local single-node Kubernetes cluster (for development/testing)
- **Namespace:** `smartsplit` (isolates our app from other apps)
- **Deployments:** Backend (2 replicas), Frontend (2 replicas), MySQL (1 replica)

#### What Happens Step-by-Step

##### 1. Update Deployment Images 🔄

```powershell
# Update backend deployment with new image (using git SHA tag)
kubectl set image deployment/backend `
  backend=sakanaisreal/smartsplit-backend:${{ github.sha }} `
  -n smartsplit

# Update frontend deployment with new image (using git SHA tag)
kubectl set image deployment/frontend `
  frontend=sakanaisreal/smartsplit-frontend:${{ github.sha }} `
  -n smartsplit
```

**What This Does:**
- Tells Kubernetes to use the new image with the specific git SHA
- Kubernetes creates new pods with the new image
- Old pods keep running until new pods are ready
- **Zero-downtime deployment!**

**Key Point:** Uses **git SHA tag** (not `:latest`) for immutable deployments.

##### 2. Force Rollout (Ensure New Pods Created) 🚀

```powershell
# Annotate backend deployment to force rollout
kubectl patch deployment backend -n smartsplit `
  -p "{\"spec\":{\"template\":{\"metadata\":{\"annotations\":{\"deployment.kubernetes.io/revision-time\":\"$(Get-Date -Format 'yyyy-MM-ddTHH:mm:ssZ')\"}}}}}"

# Annotate frontend deployment to force rollout
kubectl patch deployment frontend -n smartsplit `
  -p "{\"spec\":{\"template\":{\"metadata\":{\"annotations\":{\"deployment.kubernetes.io/revision-time\":\"$(Get-Date -Format 'yyyy-MM-ddTHH:mm:ssZ')\"}}}}}"
```

**Why This Step?**

**The Problem:** If you push an image with the same tag (e.g., `:latest`), Kubernetes might not detect the change because the tag hasn't changed.

**The Solution:** Add a timestamp annotation to force Kubernetes to see the deployment as "changed" and trigger a rollout.

**Note:** With git SHA tags, this is mostly redundant (SHA is always unique), but it's kept for safety.

##### 3. Wait for Rollout to Complete ⏳

```powershell
# Wait for backend rollout (5-minute timeout)
kubectl rollout status deployment/backend -n smartsplit --timeout=300s

# Wait for frontend rollout (5-minute timeout)
kubectl rollout status deployment/frontend -n smartsplit --timeout=300s
```

**What Happens During Rollout:**

```
Initial State:
  Backend: [Pod-Old-1] [Pod-Old-2]  ✅ Running

Rollout Starts:
  Backend: [Pod-Old-1] [Pod-Old-2] [Pod-New-1]  ⏳ Starting

Waiting for New Pod:
  Backend: [Pod-Old-1] [Pod-Old-2] [Pod-New-1]  ⏳ Container Creating

Health Check:
  Backend: [Pod-Old-1] [Pod-Old-2] [Pod-New-1]  🔍 Running health checks

New Pod Ready:
  Backend: [Pod-Old-1] [Pod-New-1] [Pod-New-2]  ⏳ Rolling update

Old Pods Terminating:
  Backend: [Pod-New-1] [Pod-New-2]  ✅ Rollout complete!
```

**Kubernetes Rolling Update Strategy:**
- Creates new pod
- Waits for health checks to pass
- Terminates one old pod
- Repeats until all pods updated
- **Zero downtime!**

**Health Check Configuration:** (from [`k8s/backend/backend-deployment.yaml`](../k8s/backend/backend-deployment.yaml))

```yaml
livenessProbe:
  httpGet:
    path: /api/actuator/health
    port: 8081
  initialDelaySeconds: 90  # Wait 90s before first check
  periodSeconds: 10

readinessProbe:
  httpGet:
    path: /api/actuator/health
    port: 8081
  initialDelaySeconds: 60  # Wait 60s before first check
  periodSeconds: 5
```

**Liveness vs Readiness:**
- **Liveness:** Is the app alive? (If no, restart the pod)
- **Readiness:** Is the app ready for traffic? (If no, don't send requests)

**Why Long Initial Delays?**
- Spring Boot takes ~30-60 seconds to start
- Premature health checks cause unnecessary restarts

##### 4. Verify Deployment ✅

```powershell
# List all pods in smartsplit namespace
kubectl get pods -n smartsplit

# Expected output:
# NAME                        READY   STATUS    RESTARTS   AGE
# backend-7b8c9d5f6-abc12     1/1     Running   0          2m
# backend-7b8c9d5f6-def34     1/1     Running   0          1m
# frontend-6f4a8b2c9-ghi56    1/1     Running   0          2m
# frontend-6f4a8b2c9-jkl78    1/1     Running   0          1m
# mysql-5c7d9e4f3-mno90       1/1     Running   0          15m

Write-Host "✅ Deployment successful! 🎉"
```

#### Kubernetes Architecture Overview

**Deployment Hierarchy:**

```
Namespace: smartsplit
│
├── Deployment: backend (2 replicas)
│   ├── Pod: backend-xxx-1
│   │   └── Container: backend (port 8081)
│   └── Pod: backend-xxx-2
│       └── Container: backend (port 8081)
│
├── Deployment: frontend (2 replicas)
│   ├── Pod: frontend-xxx-1
│   │   └── Container: frontend (port 80)
│   └── Pod: frontend-xxx-2
│       └── Container: frontend (port 80)
│
├── Deployment: mysql (1 replica)
│   └── Pod: mysql-xxx-1
│       └── Container: mysql (port 3306)
│
├── Service: backend (ClusterIP)
│   └── Exposes backend pods on port 8081
│
├── Service: frontend (ClusterIP)
│   └── Exposes frontend pods on port 80
│
├── Service: mysql (ClusterIP)
│   └── Exposes mysql pod on port 3306
│
└── Ingress: smartsplit-ingress
    ├── Route: /api → backend:8081
    └── Route: / → frontend:80
```

**Key Kubernetes Concepts:**

**Pod:**
- Smallest deployable unit
- Contains one or more containers
- Has its own IP address
- Ephemeral (can be replaced anytime)

**Deployment:**
- Manages a set of identical pods
- Ensures desired number of replicas are running
- Handles rolling updates

**Service:**
- Stable network endpoint for pods
- Load balances traffic across pod replicas
- DNS name: `<service-name>.<namespace>.svc.cluster.local`

**Ingress:**
- Routes external traffic to services
- Acts as API gateway / reverse proxy
- Handles path-based routing

**ConfigMap:**
- Stores non-sensitive configuration
- Example: database name, environment settings

**Secret:**
- Stores sensitive configuration (base64 encoded)
- Example: passwords, API keys

#### Resource Configuration

**Backend Resources:** (from [`k8s/backend/backend-deployment.yaml`](../k8s/backend/backend-deployment.yaml))

```yaml
resources:
  limits:
    memory: "1Gi"      # Maximum memory
    cpu: "1000m"       # Maximum CPU (1 core)
  requests:
    memory: "512Mi"    # Guaranteed memory
    cpu: "500m"        # Guaranteed CPU (0.5 cores)
```

**Why Resource Limits?**
- Prevents one pod from consuming all cluster resources
- Kubernetes can efficiently schedule pods
- Protects against memory leaks

**Frontend Resources:** (from [`k8s/frontend/frontend-deployment.yaml`](../k8s/frontend/frontend-deployment.yaml))

```yaml
resources:
  limits:
    memory: "256Mi"
    cpu: "500m"
  requests:
    memory: "128Mi"
    cpu: "250m"
```

**Why Less Than Backend?**
- Frontend just serves static files (lightweight)
- Backend processes business logic (heavier)

#### Persistent Storage

**MySQL Persistent Volume:** (from [`k8s/mysql/mysql-pv.yaml`](../k8s/mysql/mysql-pv.yaml))

```yaml
apiVersion: v1
kind: PersistentVolume
metadata:
  name: mysql-pv
spec:
  capacity:
    storage: 10Gi
  accessModes:
    - ReadWriteOnce  # Single node can mount
  hostPath:
    path: /data/mysql  # Stored on host machine
```

**Why Persistent Storage?**
- Database data survives pod restarts
- Without it, all data lost when pod dies

**Backend Upload Storage:** (from [`k8s/backend/backend-pv.yaml`](../k8s/backend/backend-pv.yaml))

```yaml
apiVersion: v1
kind: PersistentVolume
metadata:
  name: backend-upload-pv
spec:
  capacity:
    storage: 5Gi
  accessModes:
    - ReadWriteMany  # Multiple pods can mount
  hostPath:
    path: /data/backend-uploads
```

**Why ReadWriteMany?**
- Multiple backend replicas need access to same uploads
- User uploads a receipt → must be visible from all backend pods

#### Technologies Used

- **Kubernetes** - Container orchestration platform
- **kubectl** - Kubernetes command-line tool
- **Minikube** - Local Kubernetes cluster
- **Nginx Ingress** - Ingress controller for routing
- **Persistent Volumes** - Durable storage for stateful apps

#### Why This Stage Matters

Kubernetes deployment provides:
- **High Availability:** Multiple replicas ensure uptime
- **Zero Downtime:** Rolling updates keep app available
- **Self-Healing:** Automatically restarts failed pods
- **Scalability:** Easy to add more replicas
- **Resource Isolation:** Each pod has guaranteed resources
- **Easy Rollbacks:** Can revert to previous version instantly

#### Common Failures & Solutions

**❌ "kubectl: command not found"**
- **Cause:** kubectl not installed on runner
- **Solution:** Install kubectl and add to PATH

**❌ "Error from server (Forbidden): deployments.apps is forbidden"**
- **Cause:** kubectl not authenticated to cluster
- **Solution:** Configure kubectl with correct credentials (`kubectl config`)

**❌ "Waiting for rollout to finish: 0 of 2 updated replicas are available..."**
- **Cause:** New pods failing health checks
- **Solution:** Check pod logs (`kubectl logs -n smartsplit <pod-name>`)

**❌ "error: unable to recognize 'k8s/backend/backend-deployment.yaml': no matches for kind 'Deployment'"**
- **Cause:** Kubernetes API version mismatch
- **Solution:** Update YAML files to match cluster API version

**❌ "ImagePullBackOff"**
- **Cause:** Kubernetes can't pull Docker image from registry
- **Possible Issues:**
  - Image doesn't exist (check Docker Hub)
  - Registry credentials not configured
  - Network connectivity issues
- **Solution:** Check events (`kubectl describe pod <pod-name> -n smartsplit`)

**❌ "CrashLoopBackOff"**
- **Cause:** Pod starts then immediately crashes
- **Possible Issues:**
  - Application error on startup
  - Missing environment variables
  - Database connection failed
- **Solution:** Check logs (`kubectl logs <pod-name> -n smartsplit`)

---

## Key Concepts for Beginners

### 1. Continuous Integration (CI)

**Definition:** Automatically test and build code every time changes are pushed.

**In SmartSplit:**
- Stage 1 (Test) + Stage 2 (E2E-Test) = CI
- Ensures new code doesn't break existing functionality
- Catches bugs before they reach production

**Benefits:**
- ✅ Early bug detection (cheaper to fix)
- ✅ Confidence in code quality
- ✅ Faster development (automated testing)

### 2. Continuous Deployment (CD)

**Definition:** Automatically deploy tested code to production.

**In SmartSplit:**
- Stage 3 (Build-and-Push) + Stage 4 (Deploy) = CD
- No manual deployment steps
- Code goes from developer's laptop to production in ~15 minutes

**Benefits:**
- ✅ Faster feature delivery
- ✅ Reduced human error
- ✅ Consistent deployment process

### 3. Docker Containers

**Analogy:** Docker is like a shipping container for software.

**Traditional Deployment:**
```
Developer's laptop: Works fine ✅
Staging server: Breaks ❌ (different Node version)
Production server: Breaks ❌ (missing dependency)
```

**Docker Deployment:**
```
Developer's laptop: Works fine ✅
Staging server: Works fine ✅ (same container)
Production server: Works fine ✅ (same container)
```

**Key Points:**
- Container includes everything: code, dependencies, runtime
- Runs identically everywhere
- Isolated from other containers

### 4. Docker Images vs Containers

**Docker Image:**
- **Blueprint** for creating containers
- Stored in registry (Docker Hub)
- Immutable (doesn't change)
- Example: `sakanaisreal/smartsplit-backend:0bec405`

**Docker Container:**
- **Running instance** of an image
- Has its own filesystem, network, processes
- Can be started, stopped, deleted
- Example: Backend pod in Kubernetes

**Analogy:**
- Image = Recipe
- Container = Cooked meal

### 5. Multi-Stage Docker Builds

**Problem:** Docker images can be huge (1GB+) because they include build tools.

**Solution:** Use multi-stage builds to separate build and runtime.

**Example:**

```dockerfile
# Stage 1: Build (includes build tools)
FROM node:18 AS build
RUN npm install
RUN npm run build
# Result: 500MB image with Node, npm, Webpack, etc.

# Stage 2: Serve (only runtime)
FROM nginx:alpine
COPY --from=build /app/build /usr/share/nginx/html
# Result: 50MB image with only static files
```

**Benefits:**
- ✅ 90% smaller images
- ✅ Faster downloads
- ✅ Smaller attack surface (no unnecessary tools)

### 6. Image Tagging Strategy

**`:latest` Tag:**
```bash
docker pull sakanaisreal/smartsplit-backend:latest
```
- Always points to most recent build
- **Not immutable** (changes over time)
- Good for: Local development
- Bad for: Production deployments

**`:git-sha` Tag:**
```bash
docker pull sakanaisreal/smartsplit-backend:0bec405
```
- Points to specific git commit
- **Immutable** (never changes)
- Good for: Production deployments
- Bad for: Nothing (always use this!)

**Why Git SHA Tags Matter:**

**Scenario 1: Bug Found in Production**
```bash
# What version is deployed?
kubectl describe deployment backend -n smartsplit | grep Image
# Output: sakanaisreal/smartsplit-backend:0bec405

# Check that commit in Git
git log 0bec405
# Found the bug!
```

**Scenario 2: Rollback Needed**
```bash
# Rollback to version from yesterday
kubectl set image deployment/backend \
  backend=sakanaisreal/smartsplit-backend:ffa6f23 \
  -n smartsplit
```

With `:latest` tag, you wouldn't know what version was deployed or how to rollback!

### 7. Health Checks

**What Are Health Checks?**

Health checks are HTTP endpoints that tell Kubernetes if your app is working.

**SmartSplit Backend Health Check:**
```bash
curl http://localhost:16048/api/actuator/health

# Response if healthy:
{"status":"UP"}

# Response if unhealthy:
{"status":"DOWN","details":{...}}
```

**Two Types:**

**Liveness Probe:**
- **Question:** "Is the app alive?"
- **Action if fails:** Restart the pod
- **Use case:** Detect deadlocks, infinite loops

**Readiness Probe:**
- **Question:** "Is the app ready for traffic?"
- **Action if fails:** Stop sending requests (but don't restart)
- **Use case:** Detect slow startup, temporary issues

**Why Initial Delays?**

```yaml
livenessProbe:
  initialDelaySeconds: 90  # Wait 90 seconds before first check
```

Spring Boot takes ~30-60 seconds to start. If you check immediately, it will always fail and Kubernetes will keep restarting the pod in an endless loop!

### 8. Kubernetes Rolling Updates

**Zero-Downtime Deployment Strategy:**

```
Step 1: Old version running (2 pods)
  [Pod-Old-1] [Pod-Old-2]  ← Receiving traffic

Step 2: Create new pod
  [Pod-Old-1] [Pod-Old-2] [Pod-New-1]
                           └─ Starting...

Step 3: Wait for health checks
  [Pod-Old-1] [Pod-Old-2] [Pod-New-1]
                           └─ Health checks passing ✅

Step 4: Start sending traffic to new pod
  [Pod-Old-1] [Pod-Old-2] [Pod-New-1]
   └─ Traffic  └─ Traffic  └─ Traffic

Step 5: Create second new pod
  [Pod-Old-1] [Pod-Old-2] [Pod-New-1] [Pod-New-2]
                                       └─ Starting...

Step 6: Terminate old pods
  [Pod-New-1] [Pod-New-2]  ← All traffic

✅ Update complete, no downtime!
```

**Key Points:**
- New pods created before old pods deleted
- Traffic only sent to healthy pods
- Gradual rollout (not all at once)
- Can rollback if new version has issues

### 9. Kubernetes Namespaces

**What Are Namespaces?**

Namespaces are like **folders** for Kubernetes resources.

```
Kubernetes Cluster
│
├── Namespace: default
│   └── (system resources)
│
├── Namespace: smartsplit  ← Our app!
│   ├── backend pods
│   ├── frontend pods
│   └── mysql pod
│
└── Namespace: another-app
    └── (someone else's app)
```

**Why Use Namespaces?**
- ✅ Isolation (apps don't interfere)
- ✅ Access control (permissions per namespace)
- ✅ Resource quotas (limit CPU/memory per namespace)
- ✅ Organization (easy to find resources)

**Working with Namespaces:**
```bash
# List resources in smartsplit namespace
kubectl get pods -n smartsplit

# Without -n, uses 'default' namespace
kubectl get pods  # Won't see smartsplit pods!
```

### 10. ConfigMaps vs Secrets

**ConfigMap:** Non-sensitive configuration
```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: smartsplit-config
data:
  MYSQL_DATABASE: "smartsplit-db"
  SPRING_PROFILES_ACTIVE: "prod"
```

**Secret:** Sensitive configuration (base64 encoded)
```yaml
apiVersion: v1
kind: Secret
metadata:
  name: smartsplit-secrets
data:
  MYSQL_ROOT_PASSWORD: "Z2c="  # base64("gg")
  APP_JWT_SECRET: "eW91ci1zZWNyZXQta2V5"
```

**Usage in Pod:**
```yaml
env:
  - name: MYSQL_DATABASE
    valueFrom:
      configMapKeyRef:
        name: smartsplit-config
        key: MYSQL_DATABASE

  - name: MYSQL_ROOT_PASSWORD
    valueFrom:
      secretKeyRef:
        name: smartsplit-secrets
        key: MYSQL_ROOT_PASSWORD
```

**Why Separate?**
- ✅ Can commit ConfigMaps to Git (not secrets!)
- ✅ Different access control (secrets more restricted)
- ✅ Secrets encrypted at rest (ConfigMaps not)

### 11. Self-Hosted vs Cloud Runners

**GitHub Actions Runners:**

**Cloud Runners (GitHub-hosted):**
- ✅ No setup required
- ✅ Fresh environment every run
- ✅ Automatically maintained by GitHub
- ❌ Limited resources
- ❌ Slower (cold start)
- ❌ Pay per minute

**Self-Hosted Runners (SmartSplit):**
- ✅ Full control over environment
- ✅ Can access local resources (Kubernetes cluster)
- ✅ Faster (warm start)
- ✅ Free compute
- ❌ Requires setup and maintenance
- ❌ Need to manage updates
- ❌ Security considerations

**Why SmartSplit Uses Self-Hosted:**
- Needs access to local Minikube cluster
- Can deploy to local Kubernetes without exposing to internet
- Windows-specific requirements (PowerShell, Hyper-V workarounds)

### 12. Flyway Database Migrations

**What Are Migrations?**

Database migrations are **version control for your database schema**.

**Problem Without Migrations:**
```
Developer 1: Adds "profile_picture" column (manual SQL)
Developer 2: Doesn't know, old schema
Production: Which schema version? 🤷
```

**Solution With Migrations:**
```
V1__create_users_table.sql      ← Applied
V2__add_groups_table.sql        ← Applied
V3__add_profile_picture.sql     ← Applied
V4__add_expenses_table.sql      ← Not yet applied
```

**How Flyway Works:**
1. Checks `flyway_schema_history` table
2. Sees which migrations already applied
3. Applies new migrations in order
4. Records in history table

**Migration File Example:** [`backend/src/main/resources/db/migration/V1__create_users_table.sql`](../backend/src/main/resources/db/migration/)

```sql
CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

**Naming Convention:**
- `V<version>__<description>.sql`
- Version must be unique and sequential
- Double underscore before description

**Why Migrations Matter:**
- ✅ Database changes tracked in Git
- ✅ Repeatable (same migrations on all environments)
- ✅ Safe (can't accidentally skip a migration)
- ✅ Rollback support (reversible migrations)

---

## Hands-On: Triggering the Pipeline

### Method 1: Push to Main Branch

**Step-by-Step:**

1. **Make a code change:**
   ```bash
   # Example: Update README
   echo "Updated documentation" >> README.md
   ```

2. **Commit the change:**
   ```bash
   git add README.md
   git commit -m "docs: Update README"
   ```

3. **Push to main:**
   ```bash
   git push origin main
   ```

4. **Watch the pipeline:**
   - Go to GitHub repository
   - Click "Actions" tab
   - See your workflow running

### Method 2: Merge Pull Request

**Step-by-Step:**

1. **Create a feature branch:**
   ```bash
   git checkout -b feature/my-feature
   ```

2. **Make changes and commit:**
   ```bash
   # Make your changes
   git add .
   git commit -m "feat: Add new feature"
   ```

3. **Push feature branch:**
   ```bash
   git push origin feature/my-feature
   ```

4. **Create Pull Request:**
   - Go to GitHub repository
   - Click "Pull requests" → "New pull request"
   - Base: `main`, Compare: `feature/my-feature`
   - Click "Create pull request"

5. **Pipeline runs on PR:**
   - Tests run automatically (Stages 1-3)
   - Deploy stage skipped (only runs on `main`)

6. **Merge PR:**
   - Once tests pass, click "Merge pull request"
   - Full pipeline runs (Stages 1-4)
   - Code deployed to production!

### Watching Pipeline Progress

**GitHub Actions UI:**

1. **Repository → Actions tab**
   - Lists all workflow runs
   - Green ✅ = Success, Red ❌ = Failure, Yellow 🟡 = Running

2. **Click on a workflow run**
   - Shows all 4 stages
   - Click stage to see detailed logs

3. **Reading logs:**
   ```
   Run mvn test -DskipITs=true
   [INFO] Scanning for projects...
   [INFO] Building smartsplit-back 0.0.1-SNAPSHOT
   [INFO] Tests run: 45, Failures: 0, Errors: 0, Skipped: 0
   [INFO] BUILD SUCCESS
   ```

4. **Downloading artifacts:**
   - Scroll to bottom of workflow run page
   - "Artifacts" section
   - Download `cypress-videos` or `cypress-screenshots`

### Understanding Success vs Failure

**✅ Successful Run:**
```
✅ Test             (2m 34s)
✅ E2E-Test         (6m 12s)
✅ Build-and-Push   (4m 18s)
✅ Deploy           (2m 47s)
────────────────────────────
Total: 15m 51s
```

**❌ Failed Run:**
```
✅ Test             (2m 34s)
❌ E2E-Test         (3m 22s)
⊘  Build-and-Push   (skipped)
⊘  Deploy           (skipped)
────────────────────────────
Failed at: E2E-Test
```

**What to Do on Failure:**
1. Click the failed stage (red ❌)
2. Read the error message
3. Download artifacts if available
4. Fix the issue
5. Push again

---

## Troubleshooting Guide

### Stage 1: Test Failures

#### Problem: Backend Tests Fail

**Error Message:**
```
[ERROR] Tests run: 45, Failures: 1, Errors: 0, Skipped: 0
[ERROR] testCreateGroup(com.smartsplit.GroupServiceTest)
Expected: 200, Actual: 500
```

**What It Means:**
- A unit test failed
- Expected HTTP 200 (OK), got 500 (Internal Server Error)
- Code has a bug

**How to Fix:**
1. Run tests locally:
   ```bash
   cd backend
   mvn test -DskipITs=true
   ```

2. Find the failing test:
   ```bash
   # Look in target/surefire-reports/
   cat target/surefire-reports/TEST-com.smartsplit.GroupServiceTest.xml
   ```

3. Debug the test:
   ```java
   @Test
   public void testCreateGroup() {
       // Add debugging
       System.out.println("Creating group: " + group);
       // ... rest of test
   }
   ```

4. Fix the bug in the actual code

5. Verify fix locally:
   ```bash
   mvn test -DskipITs=true
   ```

6. Push the fix

#### Problem: npm install Fails

**Error Message:**
```
npm ERR! code ERESOLVE
npm ERR! ERESOLVE unable to resolve dependency tree
```

**What It Means:**
- Dependency version conflict
- Two packages need different versions of the same dependency

**How to Fix:**

**Option 1: Use legacy peer deps (current solution)**
```bash
npm install --legacy-peer-deps
```

**Option 2: Update dependencies**
```bash
npm update
npm install
```

**Option 3: Clear cache**
```bash
npm cache clean --force
rm -rf node_modules package-lock.json
npm install --legacy-peer-deps
```

### Stage 2: E2E Test Failures

#### Problem: Port Already in Use

**Error Message:**
```
Error starting userland proxy: listen tcp 0.0.0.0:3307:
bind: An attempt was made to access a socket in a way forbidden by its access permissions.
```

**What It Means:**
- Windows Hyper-V has reserved port 3307
- MySQL container can't bind to the port

**How to Fix:**

**Automatic (handled by pipeline):**
- Pipeline detects the issue
- Automatically uses fallback port 50307
- No action needed!

**Manual (if running locally):**
```powershell
# Check reserved ports
netsh interface ipv4 show excludedportrange protocol=tcp

# Restart WinNAT service
Restart-Service WinNAT

# Or use different port
docker run -p 50307:3306 mysql:8.0
```

**Learn More:** See [`.github/RUNNER_SETUP.md`](../.github/RUNNER_SETUP.md)

#### Problem: Backend Won't Start

**Error Message:**
```
Waiting for backend... (Attempt 30/30)
Error: Backend not responding after 150 seconds
```

**What It Means:**
- Backend started but never became healthy
- Could be database connection issue, startup error, etc.

**How to Fix:**

1. **Download backend logs:**
   - Go to GitHub Actions run
   - Download `backend-output.log` artifact

2. **Check for common issues:**

   **Issue: Database connection failed**
   ```
   Caused by: java.net.ConnectException: Connection refused
   ```
   - MySQL not ready yet (increase wait time)
   - Wrong MySQL port (check 3307 vs 50307)

   **Issue: Missing environment variable**
   ```
   Caused by: java.lang.IllegalArgumentException: JWT secret cannot be null
   ```
   - Check GitHub secrets are configured

   **Issue: Port already in use**
   ```
   Caused by: java.net.BindException: Address already in use
   ```
   - Another process using port 16048
   - Cleanup step failed

3. **Fix and retry:**
   ```bash
   # If it's a code issue, fix and push
   git add .
   git commit -m "fix: Backend startup issue"
   git push
   ```

#### Problem: Cypress Test Fails

**Error Message:**
```
CypressError: Timed out retrying after 4000ms:
Expected to find element: [data-testid="login-button"], but never found it.
```

**What It Means:**
- Cypress couldn't find an element on the page
- UI changed but test wasn't updated, OR
- Frontend has a bug

**How to Fix:**

1. **Download artifacts:**
   - Screenshots show exact failure point
   - Videos show full test execution

2. **Check if UI changed:**
   ```jsx
   // Old code:
   <button data-testid="login-button">Login</button>

   // New code (broke test):
   <button data-testid="signin-button">Login</button>
   ```

3. **Update Cypress test:**
   ```javascript
   // Old test:
   cy.get('[data-testid="login-button"]').click()

   // Updated test:
   cy.get('[data-testid="signin-button"]').click()
   ```

4. **Run locally to verify:**
   ```bash
   # Start all services
   docker-compose up -d

   # Run Cypress
   npx cypress open

   # Click the failing test to debug
   ```

5. **Push the fix**

**Pro Tip:** Always use `data-testid` attributes for Cypress selectors (more stable than CSS classes).

### Stage 3: Build-and-Push Failures

#### Problem: Docker Build Fails

**Error Message:**
```
ERROR [build 5/5] RUN mvn clean package -DskipTests
npm ERR! Failed at the smartsplit-front@0.1.0 build script.
```

**What It Means:**
- Build failed inside Docker container
- Usually a compilation error (TypeScript, ESLint, etc.)

**How to Fix:**

1. **Build locally to see full error:**
   ```bash
   cd frontend
   npm run build
   ```

2. **Common issues:**

   **Issue: TypeScript error**
   ```
   error TS2304: Cannot find name 'useState'
   ```
   - Missing import: `import { useState } from 'react'`

   **Issue: ESLint error**
   ```
   error  'username' is assigned a value but never used  no-unused-vars
   ```
   - Remove unused variable or use it

   **Issue: Environment variable missing**
   ```
   error  'REACT_APP_API_URL' is not defined  no-undef
   ```
   - Add to `.env` file

3. **Fix the error and test:**
   ```bash
   npm run build  # Must succeed
   ```

4. **Push the fix**

#### Problem: Docker Push Fails

**Error Message:**
```
denied: requested access to the resource is denied
```

**What It Means:**
- Don't have permission to push to Docker Hub repository
- Invalid credentials

**How to Fix:**

**Check Docker Hub:**
1. Log in to hub.docker.com
2. Verify repository exists: `sakanaisreal/smartsplit-backend`
3. Check you have write access

**Update GitHub Secrets:**
1. Generate new Docker Hub access token:
   - hub.docker.com → Account Settings → Security → New Access Token
   - Copy the token (shown once!)

2. Update GitHub secret:
   - GitHub repo → Settings → Secrets → Actions
   - Edit `DOCKERHUB_TOKEN`
   - Paste new token

3. Retry the pipeline

#### Problem: Out of Disk Space

**Error Message:**
```
no space left on device
```

**What It Means:**
- Runner's disk is full
- Old Docker images taking up space

**How to Fix:**

**On the runner machine:**
```powershell
# Check disk usage
docker system df

# Clean up old images
docker system prune -a -f

# Remove all unused volumes
docker volume prune -f

# Check disk space freed
docker system df
```

**Prevent future issues:**
- Add automated cleanup to runner
- Increase runner disk size
- Enable Docker automatic cleanup

### Stage 4: Deploy Failures

#### Problem: kubectl Not Found

**Error Message:**
```
kubectl: The term 'kubectl' is not recognized
```

**What It Means:**
- kubectl not installed on runner
- kubectl not in PATH

**How to Fix:**

**Install kubectl:**
```powershell
# Windows (using Chocolatey)
choco install kubernetes-cli

# Or download manually from:
# https://kubernetes.io/docs/tasks/tools/install-kubectl-windows/
```

**Add to PATH:**
```powershell
$env:Path += ";C:\path\to\kubectl"
```

**Verify:**
```powershell
kubectl version --client
```

#### Problem: Unauthorized Access

**Error Message:**
```
Error from server (Forbidden): deployments.apps "backend" is forbidden:
User "system:anonymous" cannot update resource "deployments" in API group "apps"
```

**What It Means:**
- kubectl not authenticated to Kubernetes cluster
- Wrong credentials

**How to Fix:**

**Check current context:**
```bash
kubectl config current-context
# Should show: minikube
```

**If wrong context:**
```bash
kubectl config use-context minikube
```

**If authentication expired:**
```bash
# Get new credentials
minikube update-context

# Or restart Minikube
minikube stop
minikube start
```

**Verify access:**
```bash
kubectl get pods -n smartsplit
```

#### Problem: Rollout Timeout

**Error Message:**
```
error: timed out waiting for the condition
```

**What It Means:**
- New pods never became ready
- Health checks failing for 5 minutes

**How to Fix:**

**Check pod status:**
```bash
kubectl get pods -n smartsplit

# Example output:
# NAME                        READY   STATUS             RESTARTS   AGE
# backend-7b8c9d5f6-abc12     0/1     CrashLoopBackOff   5          3m
```

**Common statuses:**

**`CrashLoopBackOff`:**
- Pod starts then crashes immediately
- Check logs:
  ```bash
  kubectl logs backend-7b8c9d5f6-abc12 -n smartsplit
  ```

**`ImagePullBackOff`:**
- Can't pull Docker image
- Check image exists:
  ```bash
  docker pull sakanaisreal/smartsplit-backend:0bec405
  ```

**`RunContainerError`:**
- Container configuration error
- Check events:
  ```bash
  kubectl describe pod backend-7b8c9d5f6-abc12 -n smartsplit
  ```

**Debug steps:**

1. **Check pod details:**
   ```bash
   kubectl describe pod <pod-name> -n smartsplit
   ```

2. **Check logs:**
   ```bash
   kubectl logs <pod-name> -n smartsplit

   # If pod crashed, check previous logs:
   kubectl logs <pod-name> -n smartsplit --previous
   ```

3. **Common issues:**

   **Issue: Database connection failed**
   ```
   Communications link failure
   ```
   - MySQL service not ready
   - Wrong database URL in ConfigMap

   **Issue: Missing secret**
   ```
   JWT secret cannot be null
   ```
   - Check secret exists:
     ```bash
     kubectl get secret smartsplit-secrets -n smartsplit
     ```

   **Issue: Health check failing**
   ```
   Liveness probe failed: HTTP probe failed with statuscode: 503
   ```
   - Application starting but not healthy
   - Increase `initialDelaySeconds` in deployment YAML

4. **Fix and redeploy:**
   ```bash
   # Edit deployment
   kubectl edit deployment backend -n smartsplit

   # Or apply updated YAML
   kubectl apply -f k8s/backend/backend-deployment.yaml
   ```

#### Problem: Old Pods Not Terminating

**Error Message:**
```
Waiting for deployment "backend" rollout to finish: 1 old replicas are pending termination...
```

**What It Means:**
- Old pods stuck in "Terminating" state
- Blocks new pods from starting

**How to Fix:**

**Check pod status:**
```bash
kubectl get pods -n smartsplit

# Output:
# NAME                        READY   STATUS        RESTARTS   AGE
# backend-7b8c9d5f6-abc12     1/1     Terminating   0          10m
```

**Force delete pod:**
```bash
kubectl delete pod backend-7b8c9d5f6-abc12 -n smartsplit --force --grace-period=0
```

**If many pods stuck:**
```bash
# Delete all terminating pods
kubectl get pods -n smartsplit | grep Terminating | awk '{print $1}' | xargs kubectl delete pod --force --grace-period=0 -n smartsplit
```

**Prevent future issues:**
- Add `terminationGracePeriodSeconds: 30` to deployment
- Ensure application handles SIGTERM signal gracefully

---

## Advanced Topics

### Viewing Deployment History

**List all revisions:**
```bash
kubectl rollout history deployment/backend -n smartsplit

# Output:
# REVISION  CHANGE-CAUSE
# 1         <none>
# 2         <none>
# 3         <none>
```

**View specific revision:**
```bash
kubectl rollout history deployment/backend -n smartsplit --revision=2

# Output shows:
# - Image version
# - Environment variables
# - Resource limits
```

**See current revision:**
```bash
kubectl describe deployment backend -n smartsplit | grep Image:

# Output:
# Image: sakanaisreal/smartsplit-backend:0bec405
```

**Add change-cause annotations (recommended):**
```bash
# When deploying
kubectl set image deployment/backend \
  backend=sakanaisreal/smartsplit-backend:0bec405 \
  -n smartsplit \
  --record

# Now history shows meaningful messages
```

### Rolling Back Deployments

**Scenario 1: Rollback to Previous Version**

```bash
# Undo last deployment
kubectl rollout undo deployment/backend -n smartsplit

# This reverts to revision N-1
```

**Scenario 2: Rollback to Specific Version**

```bash
# View history
kubectl rollout history deployment/backend -n smartsplit

# Rollback to revision 2
kubectl rollout undo deployment/backend -n smartsplit --to-revision=2
```

**Scenario 3: Deploy Specific Git SHA**

```bash
# Deploy known-good version
kubectl set image deployment/backend \
  backend=sakanaisreal/smartsplit-backend:ffa6f23 \
  -n smartsplit

# Wait for rollout
kubectl rollout status deployment/backend -n smartsplit
```

**Verify rollback:**
```bash
# Check image version
kubectl describe deployment backend -n smartsplit | grep Image:

# Check pods running
kubectl get pods -n smartsplit

# Test application
curl http://localhost:16048/api/actuator/health
```

### Monitoring Production

#### Check Application Health

**Backend health:**
```bash
kubectl exec -it <backend-pod> -n smartsplit -- \
  wget -qO- http://localhost:8081/api/actuator/health

# Output:
# {"status":"UP"}
```

**Frontend health:**
```bash
kubectl exec -it <frontend-pod> -n smartsplit -- \
  wget -qO- http://localhost/

# Should return HTML
```

#### View Application Logs

**Tail logs (live):**
```bash
kubectl logs -f <pod-name> -n smartsplit

# For backend with Spring Boot:
kubectl logs -f backend-7b8c9d5f6-abc12 -n smartsplit | grep ERROR
```

**View recent logs:**
```bash
kubectl logs --tail=100 <pod-name> -n smartsplit
```

**View logs from all replicas:**
```bash
kubectl logs -l app=backend -n smartsplit --all-containers=true
```

**Save logs to file:**
```bash
kubectl logs <pod-name> -n smartsplit > backend-logs.txt
```

#### Monitor Resource Usage

**Pod CPU/Memory:**
```bash
kubectl top pods -n smartsplit

# Output:
# NAME                        CPU(cores)   MEMORY(bytes)
# backend-xxx                 250m         512Mi
# frontend-xxx                50m          128Mi
# mysql-xxx                   100m         800Mi
```

**Node resources:**
```bash
kubectl top nodes

# Output:
# NAME       CPU(cores)   CPU%   MEMORY(bytes)   MEMORY%
# minikube   1500m        37%    4Gi             66%
```

#### Watch Pod Status

**Real-time updates:**
```bash
kubectl get pods -n smartsplit --watch

# Updates live as pods change status
```

**Detailed view:**
```bash
kubectl get pods -n smartsplit -o wide

# Shows:
# - Node running on
# - IP address
# - Restarts
# - Age
```

### Scaling Deployments

**Scale backend to 3 replicas:**
```bash
kubectl scale deployment/backend --replicas=3 -n smartsplit
```

**Scale frontend to 1 replica:**
```bash
kubectl scale deployment/frontend --replicas=1 -n smartsplit
```

**Auto-scaling (Horizontal Pod Autoscaler):**
```bash
# Scale based on CPU usage
kubectl autoscale deployment backend \
  --min=2 --max=5 \
  --cpu-percent=80 \
  -n smartsplit

# View autoscaler status
kubectl get hpa -n smartsplit
```

### Debugging Techniques

#### Execute Commands in Pod

**Interactive shell:**
```bash
kubectl exec -it <pod-name> -n smartsplit -- /bin/sh

# Now you're inside the container!
# Check files, run commands, etc.
```

**One-off command:**
```bash
# Check Java version
kubectl exec <backend-pod> -n smartsplit -- java -version

# Check disk space
kubectl exec <pod-name> -n smartsplit -- df -h

# Test database connection
kubectl exec <backend-pod> -n smartsplit -- \
  mysql -h mysql.smartsplit.svc.cluster.local -u root -p
```

#### Port Forwarding for Local Access

**Backend:**
```bash
kubectl port-forward svc/backend 16048:8081 -n smartsplit

# Now accessible at: http://localhost:16048
```

**Frontend:**
```bash
kubectl port-forward svc/frontend 3003:80 -n smartsplit

# Now accessible at: http://localhost:3003
```

**MySQL:**
```bash
kubectl port-forward svc/mysql 8082:3306 -n smartsplit

# Connect with MySQL client:
mysql -h 127.0.0.1 -P 8082 -u root -p
```

**SmartSplit Scripts:** (Windows)
```powershell
# Start all port forwards
.\scripts\start-port-forward.ps1

# Stop all port forwards
.\scripts\stop-port-forward.ps1
```

#### Copy Files To/From Pod

**Copy from pod:**
```bash
kubectl cp <pod-name>:/path/to/file ./local-file -n smartsplit

# Example: Download backend logs
kubectl cp backend-xxx:/app/logs/spring.log ./backend-spring.log -n smartsplit
```

**Copy to pod:**
```bash
kubectl cp ./local-file <pod-name>:/path/to/file -n smartsplit

# Example: Upload test data
kubectl cp ./test-data.json backend-xxx:/app/data.json -n smartsplit
```

---

## Quick Reference

### Pipeline Commands

```bash
# View workflow runs
gh workflow view ci-cd.yml

# List recent runs
gh run list --workflow=ci-cd.yml

# View specific run
gh run view <run-id>

# Download artifacts
gh run download <run-id>

# Re-run failed jobs
gh run rerun <run-id>
```

### Docker Commands

```bash
# Build image
docker build -t smartsplit-backend:test .

# Run container
docker run -p 8080:8080 smartsplit-backend:test

# View logs
docker logs <container-id>

# Stop container
docker stop <container-id>

# Remove container
docker rm <container-id>

# List images
docker images | grep smartsplit

# Remove image
docker rmi sakanaisreal/smartsplit-backend:latest

# Clean up everything
docker system prune -a -f
```

### Kubernetes Commands

```bash
# View all resources
kubectl get all -n smartsplit

# Describe resource
kubectl describe <resource-type> <name> -n smartsplit

# View logs
kubectl logs <pod-name> -n smartsplit

# Follow logs
kubectl logs -f <pod-name> -n smartsplit

# Execute command
kubectl exec <pod-name> -n smartsplit -- <command>

# Port forward
kubectl port-forward svc/<service> <local-port>:<remote-port> -n smartsplit

# Scale deployment
kubectl scale deployment/<name> --replicas=<count> -n smartsplit

# Rollback deployment
kubectl rollout undo deployment/<name> -n smartsplit

# View deployment history
kubectl rollout history deployment/<name> -n smartsplit

# Restart deployment
kubectl rollout restart deployment/<name> -n smartsplit

# Delete resource
kubectl delete <resource-type> <name> -n smartsplit

# Apply configuration
kubectl apply -f <file>.yaml

# Edit resource
kubectl edit <resource-type> <name> -n smartsplit
```

### Git Commands

```bash
# View recent commits
git log --oneline -10

# View commit details
git show <commit-sha>

# Find when a file changed
git log --follow <file-path>

# View file at specific commit
git show <commit-sha>:<file-path>

# Tag a commit
git tag -a v1.0.0 -m "Release v1.0.0"
git push origin v1.0.0
```

### Maven Commands

```bash
# Run unit tests
mvn test -DskipITs=true

# Run integration tests
mvn verify -DskipTests

# Run all tests
mvn verify

# Build JAR
mvn clean package

# Run locally
mvn spring-boot:run

# Skip tests
mvn clean package -DskipTests

# Generate test coverage report
mvn verify
# Open: target/site/jacoco/index.html
```

### npm Commands

```bash
# Install dependencies
npm install --legacy-peer-deps

# Start dev server
npm start

# Build for production
npm run build

# Run unit tests
npm test

# Run E2E tests
npm run test:e2e

# Open Cypress
npm run cypress:open

# Update dependencies
npm update

# Check for outdated packages
npm outdated

# Audit vulnerabilities
npm audit
npm audit fix
```

### Port Configuration Reference

| Port | Service | Environment | Access |
|------|---------|-------------|--------|
| 3000 | Frontend Dev Server | Local, CI/CD | http://localhost:3000 |
| 3003 | Frontend (Port Forward) | Minikube | http://localhost:3003 |
| 8080 | Backend (Container) | Docker Compose | Internal |
| 8081 | Backend API | Docker Compose | http://localhost:8081/api |
| 16048 | Backend (Port Forward) | Minikube, CI/CD | http://localhost:16048/api |
| 3306 | MySQL (Container) | Internal | mysql://mysql:3306 |
| 3307 | MySQL (CI) | E2E Tests | mysql://localhost:3307 |
| 8082 | MySQL (Port Forward) | Minikube | mysql://localhost:8082 |
| 50307 | MySQL (Fallback) | E2E Tests | mysql://localhost:50307 |

### File Locations Reference

**CI/CD Configuration:**
- Workflow: `.github/workflows/ci-cd.yml`
- Runner setup: `.github/RUNNER_SETUP.md`

**Docker:**
- Backend Dockerfile: `backend/Dockerfile`
- Frontend Dockerfile: `frontend/Dockerfile`
- Compose: `docker-compose.yml`
- Frontend Nginx: `frontend/nginx.conf`

**Kubernetes:**
- Namespace: `k8s/namespace.yaml`
- ConfigMap: `k8s/configmap.yaml`
- Secrets: `k8s/secrets.yaml`
- Backend: `k8s/backend/`
- Frontend: `k8s/frontend/`
- MySQL: `k8s/mysql/`
- Ingress: `k8s/ingress.yaml`

**Testing:**
- Cypress config: `cypress.config.js`
- E2E tests: `cypress/e2e/SmartSplit-E2E.cy.js`
- Backend tests: `backend/src/test/java/`

**Deployment:**
- Minikube deploy: `deploy-minikube.ps1` (Windows)
- Port forwarding: `scripts/start-port-forward.ps1`

**Documentation:**
- Project guide: `CLAUDE.md`
- Port configuration: `docs/PORT_CONFIGURATION.md`
- CI/CD guide: `docs/CICD_FLOW.md` (this file)

### Environment Variables Reference

**GitHub Secrets (Required):**
```
DOCKERHUB_USERNAME
DOCKERHUB_TOKEN
APP_JWT_SECRET
```

**ConfigMap (Kubernetes):**
```
MYSQL_DATABASE=smartsplit-db
SPRING_PROFILES_ACTIVE=prod
SPRING_SERVLET_MULTIPART_MAX_FILE_SIZE=200MB
SPRING_SERVLET_MULTIPART_MAX_REQUEST_SIZE=200MB
APP_JWT_EXPIRATION_SECONDS=86400
```

**Secrets (Kubernetes):**
```
MYSQL_ROOT_PASSWORD
APP_JWT_SECRET
```

**Local Development (.env):**
```
MYSQL_ROOT_PASSWORD=gg
MYSQL_DATABASE=smartsplit-db
APP_JWT_SECRET=<your-secret>
APP_JWT_EXPIRATION_SECONDS=86400
```

---

## Conclusion

You now have a comprehensive understanding of SmartSplit's CI/CD pipeline!

**Key Takeaways:**
- ✅ CI/CD automates testing, building, and deployment
- ✅ Four stages: Test → E2E-Test → Build-and-Push → Deploy
- ✅ Git SHA tags enable immutable, auditable deployments
- ✅ Kubernetes provides zero-downtime deployments and self-healing
- ✅ Health checks ensure only healthy pods receive traffic
- ✅ Multi-stage Docker builds create small, secure images

**Next Steps:**
1. **Experiment locally:** Run `deploy-minikube.ps1` to deploy to local Kubernetes
2. **Make a change:** Update code, push to `main`, watch the pipeline
3. **Practice debugging:** Intentionally break something, learn to fix it
4. **Explore Kubernetes:** Use `kubectl` to inspect running pods
5. **Improve the pipeline:** Add more tests, optimize build times, etc.

**Resources:**
- [GitHub Actions Documentation](https://docs.github.com/en/actions)
- [Docker Documentation](https://docs.docker.com/)
- [Kubernetes Documentation](https://kubernetes.io/docs/)
- [Cypress Documentation](https://docs.cypress.io/)
- [Spring Boot Documentation](https://spring.io/projects/spring-boot)

**Need Help?**
- Check [CLAUDE.md](../CLAUDE.md) for project overview
- Check [PORT_CONFIGURATION.md](PORT_CONFIGURATION.md) for networking details
- Check [`.github/RUNNER_SETUP.md`](../.github/RUNNER_SETUP.md) for runner setup

Happy deploying! 🚀