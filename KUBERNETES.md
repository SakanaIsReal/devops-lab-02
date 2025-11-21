# Kubernetes Deployment Guide - SmartSplit

## Table of Contents
- [Overview](#overview)
- [Architecture](#architecture)
- [Prerequisites](#prerequisites)
- [Quick Start](#quick-start)
- [Kubernetes Resources](#kubernetes-resources)
- [Deployment Process](#deployment-process)
- [Accessing the Application](#accessing-the-application)
- [Monitoring and Management](#monitoring-and-management)
- [Troubleshooting](#troubleshooting)
- [Advanced Operations](#advanced-operations)

---

## Overview

SmartSplit uses Kubernetes for container orchestration, providing:
- **High Availability**: Multiple replicas for frontend and backend services
- **Scalability**: Easy horizontal scaling of application components
- **Service Discovery**: Internal DNS-based service communication
- **Load Balancing**: Automatic traffic distribution across pod replicas
- **Self-Healing**: Automatic pod restarts on failures
- **Rolling Updates**: Zero-downtime deployments

### Deployment Environment

- **Local Development**: Minikube (single-node cluster)
- **Production**: Kubernetes cluster with Ingress controller
- **Namespace**: `smartsplit` (isolated environment)

---

## Architecture

### Component Overview

```
┌─────────────────────────────────────────────────────────────┐
│                    Kubernetes Cluster                        │
│                                                              │
│  ┌────────────────────────────────────────────────────┐    │
│  │               Ingress Controller                    │    │
│  │  - Routes /api → Backend Service                    │    │
│  │  - Routes /files → Backend Service                  │    │
│  │  - Routes / → Frontend Service                      │    │
│  └─────────────┬──────────────────────┬────────────────┘    │
│                │                      │                      │
│     ┌──────────▼─────────┐ ┌─────────▼──────────┐          │
│     │  Frontend Service  │ │  Backend Service   │          │
│     │   (ClusterIP)      │ │   (ClusterIP)      │          │
│     │   Port: 80         │ │   Port: 8081       │          │
│     └──────────┬─────────┘ └─────────┬──────────┘          │
│                │                      │                      │
│     ┌──────────▼─────────┐ ┌─────────▼──────────┐          │
│     │ Frontend Pods (x2) │ │ Backend Pods (x2)  │          │
│     │  - Nginx           │ │  - Spring Boot     │          │
│     │  - React App       │ │  - JWT Auth        │          │
│     │                    │ │  - REST API        │          │
│     └────────────────────┘ └─────────┬──────────┘          │
│                                       │                      │
│                            ┌──────────▼──────────┐          │
│                            │   MySQL Service     │          │
│                            │   (ClusterIP)       │          │
│                            │   Port: 3306        │          │
│                            └──────────┬──────────┘          │
│                                       │                      │
│                            ┌──────────▼──────────┐          │
│                            │    MySQL Pod        │          │
│                            │  - MySQL 8.0        │          │
│                            │  - PV Storage       │          │
│                            └─────────────────────┘          │
│                                                              │
│  ┌────────────────────────────────────────────────────┐    │
│  │              Persistent Volumes                     │    │
│  │  - mysql-pv (10Gi) → /mnt/data/mysql               │    │
│  │  - uploads-pv (20Gi) → /mnt/data/uploads           │    │
│  └────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────┘
```

### Resource Map

| Component | Type | Replicas | CPU Request | Memory Request | Storage |
|-----------|------|----------|-------------|----------------|---------|
| Frontend | Deployment | 2 | 250m | 128Mi | - |
| Backend | Deployment | 2 | 500m | 512Mi | 20Gi (uploads) |
| MySQL | Deployment | 1 | 500m | 512Mi | 10Gi (data) |

---

## Prerequisites

### Required Tools

1. **Minikube** (v1.25+)
   ```bash
   # Installation
   # Windows (using Chocolatey)
   choco install minikube

   # Linux
   curl -LO https://storage.googleapis.com/minikube/releases/latest/minikube-linux-amd64
   sudo install minikube-linux-amd64 /usr/local/bin/minikube

   # macOS
   brew install minikube
   ```

2. **kubectl** (v1.23+)
   ```bash
   # Windows (using Chocolatey)
   choco install kubernetes-cli

   # Linux
   curl -LO "https://dl.k8s.io/release/$(curl -L -s https://dl.k8s.io/release/stable.txt)/bin/linux/amd64/kubectl"
   sudo install -o root -g root -m 0755 kubectl /usr/local/bin/kubectl

   # macOS
   brew install kubectl
   ```

3. **Docker** (v20.10+)
   - Required for building images and Minikube driver
   - [Download Docker Desktop](https://www.docker.com/products/docker-desktop)

### System Requirements

- **CPU**: 4 cores minimum (6+ recommended)
- **Memory**: 8GB minimum (16GB recommended)
- **Disk Space**: 40GB free space
- **OS**: Windows 10/11, macOS 10.14+, or Linux

### Verify Installation

```bash
# Check Minikube version
minikube version

# Check kubectl version
kubectl version --client

# Check Docker version
docker --version
```

---

## Quick Start

### Option 1: Automated Deployment (Recommended)

**Windows:**
```powershell
# Deploy with default settings (uses :latest tag)
.\deploy-minikube.ps1

# Deploy with specific version
.\deploy-minikube.ps1 -ImageTag "v1.2.3"
```

**Linux/macOS:**
```bash
# Deploy with default settings (uses :latest tag)
./deploy-minikube.sh

# Deploy with specific version
IMAGE_TAG=v1.2.3 ./deploy-minikube.sh
```

### Option 2: Manual Deployment

```bash
# 1. Start Minikube
minikube start --cpus=4 --memory=8192 --driver=docker

# 2. Set Docker environment to use Minikube's daemon
eval $(minikube docker-env)  # Linux/macOS
minikube docker-env | Invoke-Expression  # Windows PowerShell

# 3. Build Docker images
docker build -t backend:latest ./backend
docker build -t frontend:latest ./frontend

# 4. Apply Kubernetes configurations
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/configmap.yaml
kubectl apply -f k8s/secrets.yaml
kubectl apply -f k8s/mysql/
kubectl apply -f k8s/backend/
kubectl apply -f k8s/frontend/
kubectl apply -f k8s/ingress.yaml

# 5. Enable Ingress addon
minikube addons enable ingress

# 6. Wait for all pods to be ready
kubectl wait --namespace smartsplit --for=condition=ready pod --all --timeout=300s

# 7. Get Minikube IP
minikube ip
```

---

## Kubernetes Resources

### Namespace

**File:** [k8s/namespace.yaml](k8s/namespace.yaml)

Creates an isolated namespace `smartsplit` for all application resources.

```yaml
apiVersion: v1
kind: Namespace
metadata:
  name: smartsplit
```

**Purpose:**
- Resource isolation
- Access control boundaries
- Easier resource management and cleanup

### ConfigMap

**File:** [k8s/configmap.yaml](k8s/configmap.yaml)

Stores non-sensitive configuration data:

| Key | Value | Purpose |
|-----|-------|---------|
| `MYSQL_DATABASE` | `smartsplit-db` | Database name |
| `SPRING_PROFILES_ACTIVE` | `prod` | Spring Boot profile |
| `SPRING_SERVLET_MULTIPART_MAX_FILE_SIZE` | `200MB` | Max file upload size |
| `SPRING_SERVLET_MULTIPART_MAX_REQUEST_SIZE` | `200MB` | Max request size |
| `APP_JWT_EXPIRATION_SECONDS` | `86400` | JWT token expiration (24 hours) |

**Usage:**
```bash
# View ConfigMap
kubectl get configmap app-config -n smartsplit -o yaml

# Edit ConfigMap
kubectl edit configmap app-config -n smartsplit

# After editing, restart deployments to apply changes
kubectl rollout restart deployment/backend -n smartsplit
```

### Secrets

**File:** [k8s/secrets.yaml](k8s/secrets.yaml)

Stores sensitive data (base64 encoded):

| Key | Purpose |
|-----|---------|
| `MYSQL_ROOT_PASSWORD` | MySQL root password |
| `SPRING_DATASOURCE_PASSWORD` | Backend database password |
| `APP_JWT_SECRET` | JWT signing secret key |

**Security Notes:**
- In production, use external secret management (e.g., HashiCorp Vault, AWS Secrets Manager)
- Never commit real secrets to version control
- Use `stringData` for plain text (auto-encoded) or `data` for base64

**Managing Secrets:**
```bash
# View secret keys (values are hidden)
kubectl get secret app-secrets -n smartsplit

# View decoded secret (USE WITH CAUTION)
kubectl get secret app-secrets -n smartsplit -o jsonpath='{.data.MYSQL_ROOT_PASSWORD}' | base64 --decode

# Create/update secret from file
kubectl create secret generic app-secrets \
  --from-literal=MYSQL_ROOT_PASSWORD=your-password \
  --from-literal=APP_JWT_SECRET=your-jwt-secret \
  --namespace smartsplit \
  --dry-run=client -o yaml | kubectl apply -f -
```

### MySQL Database

**Files:**
- [k8s/mysql/mysql-pv.yaml](k8s/mysql/mysql-pv.yaml) - Persistent storage
- [k8s/mysql/mysql-deployment.yaml](k8s/mysql/mysql-deployment.yaml) - Database deployment

**Architecture:**
- **Replicas:** 1 (single instance)
- **Strategy:** Recreate (ensures data consistency)
- **Storage:** 10Gi PersistentVolume at `/mnt/data/mysql`
- **Image:** `mysql:8.0`

**Resource Limits:**
```yaml
resources:
  limits:
    memory: 1Gi
    cpu: 1000m
  requests:
    memory: 512Mi
    cpu: 500m
```

**Health Checks:**
- **Liveness Probe:** Runs `mysqladmin ping` every 10s (starts after 30s)
- **Readiness Probe:** Runs `mysqladmin ping` every 5s (starts after 10s)

**Service:**
- **Type:** ClusterIP (internal only)
- **Port:** 3306
- **DNS:** `mysql.smartsplit.svc.cluster.local`

**Data Persistence:**
```bash
# Check PV and PVC status
kubectl get pv mysql-pv -n smartsplit
kubectl get pvc mysql-pvc -n smartsplit

# View storage usage
kubectl exec -n smartsplit deployment/mysql -- df -h /var/lib/mysql

# Backup database
kubectl exec -n smartsplit deployment/mysql -- mysqldump -uroot -p$MYSQL_ROOT_PASSWORD smartsplit-db > backup.sql

# Restore database
kubectl exec -i -n smartsplit deployment/mysql -- mysql -uroot -p$MYSQL_ROOT_PASSWORD smartsplit-db < backup.sql
```

### Backend Service

**Files:**
- [k8s/backend/backend-deployment.yaml](k8s/backend/backend-deployment.yaml)
- [k8s/backend/uploads-pv.yaml](k8s/backend/uploads-pv.yaml)

**Architecture:**
- **Replicas:** 2 (high availability)
- **Strategy:** RollingUpdate (default, zero-downtime)
- **Storage:** 20Gi shared volume for file uploads (ReadWriteMany)
- **Image:** `sakanaisreal/smartsplit-backend:latest`

**Init Container:**
```yaml
initContainers:
- name: wait-for-mysql
  image: busybox:1.36
  # Waits for MySQL to be ready before starting backend
```

**Environment Variables:**
- Database connection via ConfigMap and Secrets
- JWT configuration from Secrets
- Spring Boot profile and multipart settings

**Resource Limits:**
```yaml
resources:
  limits:
    memory: 1Gi
    cpu: 1000m
  requests:
    memory: 512Mi
    cpu: 500m
```

**Health Checks:**
- **Liveness Probe:** HTTP GET `/actuator/health` every 10s (starts after 90s)
- **Readiness Probe:** HTTP GET `/actuator/health` every 5s (starts after 60s)

**Service:**
- **Type:** ClusterIP
- **Port:** 8081
- **DNS:** `backend.smartsplit.svc.cluster.local`

**File Uploads:**
```bash
# Check uploads PV/PVC
kubectl get pv uploads-pv -n smartsplit
kubectl get pvc uploads-pvc -n smartsplit

# View uploaded files
kubectl exec -n smartsplit deployment/backend -- ls -lh /app/uploads

# Clean up old uploads (if needed)
kubectl exec -n smartsplit deployment/backend -- find /app/uploads -type f -mtime +30 -delete
```

### Frontend Service

**File:** [k8s/frontend/frontend-deployment.yaml](k8s/frontend/frontend-deployment.yaml)

**Architecture:**
- **Replicas:** 2 (load balanced)
- **Image:** `sakanaisreal/smartsplit-frontend:latest`
- **Server:** Nginx serving React build

**Resource Limits:**
```yaml
resources:
  limits:
    memory: 256Mi
    cpu: 500m
  requests:
    memory: 128Mi
    cpu: 250m
```

**Service:**
- **Type:** ClusterIP
- **Port:** 80
- **DNS:** `frontend.smartsplit.svc.cluster.local`

### Ingress

**File:** [k8s/ingress.yaml](k8s/ingress.yaml)

Routes external traffic to internal services:

| Path | Service | Port | Purpose |
|------|---------|------|---------|
| `/api` | backend | 8081 | REST API endpoints |
| `/files` | backend | 8081 | File downloads/uploads |
| `/` | frontend | 80 | React application |

**Annotations:**
```yaml
annotations:
  nginx.ingress.kubernetes.io/proxy-body-size: "200m"  # Allow large file uploads
```

**Path Priority:**
1. `/api` - Highest priority (Prefix match)
2. `/files` - High priority (Prefix match)
3. `/` - Catch-all (Prefix match)

**Accessing Ingress:**
```bash
# Get Ingress status
kubectl get ingress smartsplit-ingress -n smartsplit

# View Ingress details
kubectl describe ingress smartsplit-ingress -n smartsplit

# Test backend API through Ingress
curl http://$(minikube ip)/api/actuator/health

# Test frontend through Ingress
curl http://$(minikube ip)/
```

---

## Deployment Process

### Image Tagging Strategy

**Local Development:**
- Uses `:latest` tag by default
- Built locally in Minikube's Docker daemon
- No registry push required

**CI/CD Production:**
- Dual tagging: `:latest` and `:${git-sha}`
- Deployments use `:${git-sha}` for immutability
- Images pushed to Docker Hub registry

### Deployment Scripts

#### Windows: deploy-minikube.ps1

```powershell
# Basic deployment
.\deploy-minikube.ps1

# Custom image tag
.\deploy-minikube.ps1 -ImageTag "v1.0.0"
```

**What it does:**
1. Starts/verifies Minikube is running
2. Sets Docker environment to Minikube
3. Builds Docker images with specified tag
4. Tags as `:latest` for convenience
5. Applies all Kubernetes manifests
6. Enables Ingress addon
7. Waits for pods to be ready
8. Displays access URLs and pod status

#### Linux/macOS: deploy-minikube.sh

```bash
# Basic deployment
./deploy-minikube.sh

# Custom image tag
IMAGE_TAG=v1.0.0 ./deploy-minikube.sh
```

### Manual Deployment Steps

```bash
# Step 1: Start Minikube
minikube start --cpus=4 --memory=8192

# Step 2: Use Minikube's Docker daemon
eval $(minikube docker-env)  # Linux/macOS
minikube docker-env | Invoke-Expression  # Windows

# Step 3: Build images
docker build -t backend:latest ./backend
docker build -t frontend:latest ./frontend

# Step 4: Create namespace
kubectl apply -f k8s/namespace.yaml

# Step 5: Apply configuration
kubectl apply -f k8s/configmap.yaml
kubectl apply -f k8s/secrets.yaml

# Step 6: Deploy MySQL (with storage)
kubectl apply -f k8s/mysql/mysql-pv.yaml
kubectl apply -f k8s/mysql/mysql-deployment.yaml

# Step 7: Deploy Backend (with uploads storage)
kubectl apply -f k8s/backend/uploads-pv.yaml
kubectl apply -f k8s/backend/backend-deployment.yaml

# Step 8: Deploy Frontend
kubectl apply -f k8s/frontend/frontend-deployment.yaml

# Step 9: Configure Ingress
kubectl apply -f k8s/ingress.yaml
minikube addons enable ingress

# Step 10: Wait for readiness
kubectl wait --namespace smartsplit \
  --for=condition=ready pod \
  --all \
  --timeout=300s
```

### Updating Deployments

**Update ConfigMap/Secrets:**
```bash
# Edit ConfigMap
kubectl edit configmap app-config -n smartsplit

# Restart deployments to apply changes
kubectl rollout restart deployment/backend -n smartsplit
kubectl rollout restart deployment/frontend -n smartsplit
```

**Update Image Version:**
```bash
# Option 1: Using kubectl set image
kubectl set image deployment/backend \
  backend=sakanaisreal/smartsplit-backend:new-tag \
  -n smartsplit

kubectl set image deployment/frontend \
  frontend=sakanaisreal/smartsplit-frontend:new-tag \
  -n smartsplit

# Option 2: Edit deployment directly
kubectl edit deployment backend -n smartsplit

# Option 3: Apply updated manifest
# (Update image tag in YAML file, then:)
kubectl apply -f k8s/backend/backend-deployment.yaml
kubectl apply -f k8s/frontend/frontend-deployment.yaml
```

**Rebuild and Deploy:**
```bash
# Use Minikube's Docker
eval $(minikube docker-env)

# Rebuild with new tag
docker build -t backend:v2.0.0 ./backend
docker build -t frontend:v2.0.0 ./frontend

# Update deployment
kubectl set image deployment/backend backend=backend:v2.0.0 -n smartsplit
kubectl set image deployment/frontend frontend=frontend:v2.0.0 -n smartsplit

# Watch rollout
kubectl rollout status deployment/backend -n smartsplit
kubectl rollout status deployment/frontend -n smartsplit
```

---

## Accessing the Application

### Via Minikube IP (Direct Access)

```bash
# Get Minikube IP
minikube ip

# Access application
# Frontend: http://<minikube-ip>/
# Backend API: http://<minikube-ip>/api
# API Health: http://<minikube-ip>/api/actuator/health
```

**Example:**
```bash
MINIKUBE_IP=$(minikube ip)
echo "Frontend: http://$MINIKUBE_IP"
echo "Backend: http://$MINIKUBE_IP/api"

# Test backend
curl http://$MINIKUBE_IP/api/actuator/health
```

### Via Port Forwarding (Local Development)

Port forwarding allows accessing services via `localhost`.

**Quick Start Scripts:**

```powershell
# Windows - Start port forwarding
.\scripts\start-port-forward.ps1

# Access services
# Frontend: http://localhost:3000
# Backend: http://localhost:8081

# Stop port forwarding
.\scripts\stop-port-forward.ps1
```

**Manual Port Forwarding:**

```bash
# Frontend (port 3000)
kubectl port-forward -n smartsplit svc/frontend 3000:80

# Backend (port 8081 or 16048)
kubectl port-forward -n smartsplit svc/backend 8081:8081

# MySQL (port 3306 or 8082)
kubectl port-forward -n smartsplit svc/mysql 3306:3306
```

**Background Port Forwarding:**

```bash
# Linux/macOS
kubectl port-forward -n smartsplit svc/frontend 3000:80 &
kubectl port-forward -n smartsplit svc/backend 8081:8081 &

# Stop background processes
killall kubectl
```

### Service Types Comparison

| Method | URL | Use Case | Requires |
|--------|-----|----------|----------|
| Minikube IP | `http://<minikube-ip>` | Testing Ingress | Ingress addon |
| Port Forward | `http://localhost:<port>` | Local debugging | kubectl access |
| NodePort | `http://<node-ip>:<nodeport>` | External access | Change service type |
| LoadBalancer | External IP | Cloud only | Cloud provider |

---

## Monitoring and Management

### Pod Management

**View Pods:**
```bash
# List all pods in namespace
kubectl get pods -n smartsplit

# Wide output (shows node, IP)
kubectl get pods -n smartsplit -o wide

# Watch pods in real-time
kubectl get pods -n smartsplit --watch

# Filter by label
kubectl get pods -n smartsplit -l app=backend
```

**Pod Details:**
```bash
# Describe pod (events, conditions)
kubectl describe pod <pod-name> -n smartsplit

# Get pod YAML
kubectl get pod <pod-name> -n smartsplit -o yaml

# Get pod logs
kubectl logs <pod-name> -n smartsplit

# Follow logs (tail -f)
kubectl logs <pod-name> -n smartsplit -f

# Previous container logs (after crash)
kubectl logs <pod-name> -n smartsplit --previous

# Logs from specific container
kubectl logs <pod-name> -n smartsplit -c backend
```

**Pod Execution:**
```bash
# Execute command in pod
kubectl exec -n smartsplit <pod-name> -- ls -la

# Interactive shell
kubectl exec -it -n smartsplit <pod-name> -- /bin/sh
kubectl exec -it -n smartsplit <pod-name> -- /bin/bash

# Execute in specific container
kubectl exec -it -n smartsplit <pod-name> -c backend -- /bin/bash
```

### Deployment Management

**View Deployments:**
```bash
# List deployments
kubectl get deployments -n smartsplit

# Describe deployment
kubectl describe deployment backend -n smartsplit

# View deployment history
kubectl rollout history deployment/backend -n smartsplit
```

**Scaling:**
```bash
# Scale deployment
kubectl scale deployment backend --replicas=3 -n smartsplit

# Autoscaling (HPA)
kubectl autoscale deployment backend \
  --cpu-percent=80 \
  --min=2 \
  --max=5 \
  -n smartsplit

# View autoscaler
kubectl get hpa -n smartsplit
```

**Rollout Management:**
```bash
# Check rollout status
kubectl rollout status deployment/backend -n smartsplit

# Pause rollout
kubectl rollout pause deployment/backend -n smartsplit

# Resume rollout
kubectl rollout resume deployment/backend -n smartsplit

# Rollback to previous version
kubectl rollout undo deployment/backend -n smartsplit

# Rollback to specific revision
kubectl rollout undo deployment/backend -n smartsplit --to-revision=2

# Restart deployment (recreate pods)
kubectl rollout restart deployment/backend -n smartsplit
```

### Service Management

**View Services:**
```bash
# List services
kubectl get svc -n smartsplit

# Describe service
kubectl describe svc backend -n smartsplit

# View endpoints
kubectl get endpoints -n smartsplit
```

**Service Testing:**
```bash
# Test service from within cluster
kubectl run -it --rm debug \
  --image=busybox \
  --restart=Never \
  -n smartsplit \
  -- wget -qO- http://backend.smartsplit.svc.cluster.local:8081/actuator/health

# Test MySQL connection
kubectl run -it --rm mysql-client \
  --image=mysql:8.0 \
  --restart=Never \
  -n smartsplit \
  -- mysql -h mysql.smartsplit.svc.cluster.local -uroot -p
```

### Resource Monitoring

**Resource Usage:**
```bash
# Enable metrics-server addon
minikube addons enable metrics-server

# View node resources
kubectl top nodes

# View pod resources
kubectl top pods -n smartsplit

# Sort by CPU
kubectl top pods -n smartsplit --sort-by=cpu

# Sort by memory
kubectl top pods -n smartsplit --sort-by=memory
```

**Events:**
```bash
# View namespace events
kubectl get events -n smartsplit

# Sort by timestamp
kubectl get events -n smartsplit --sort-by='.lastTimestamp'

# Watch events
kubectl get events -n smartsplit --watch
```

### Storage Management

**Persistent Volumes:**
```bash
# List PVs
kubectl get pv

# Describe PV
kubectl describe pv mysql-pv

# List PVCs
kubectl get pvc -n smartsplit

# Describe PVC
kubectl describe pvc mysql-pvc -n smartsplit

# Check storage usage
kubectl exec -n smartsplit deployment/mysql -- df -h /var/lib/mysql
kubectl exec -n smartsplit deployment/backend -- df -h /app/uploads
```

---

## Troubleshooting

### Common Issues

#### 1. Pods Not Starting

**Symptoms:**
```bash
kubectl get pods -n smartsplit
# NAME                        READY   STATUS             RESTARTS
# backend-xxxxx               0/2     ImagePullBackOff   0
```

**Solutions:**

**A. Image Pull Errors:**
```bash
# Check if using Minikube's Docker daemon
eval $(minikube docker-env)

# List images
docker images | grep backend

# Rebuild image
docker build -t backend:latest ./backend

# Check imagePullPolicy
kubectl describe pod <pod-name> -n smartsplit
# Should be: imagePullPolicy: Never (for local) or Always (for registry)
```

**B. Init Container Waiting:**
```bash
# Check init container logs
kubectl logs <pod-name> -n smartsplit -c wait-for-mysql

# Verify MySQL is running
kubectl get pods -n smartsplit -l app=mysql

# Check MySQL service
kubectl get svc mysql -n smartsplit
```

**C. Resource Constraints:**
```bash
# Check node resources
kubectl describe node

# Reduce resource requests if needed
kubectl edit deployment backend -n smartsplit
```

#### 2. MySQL Connection Failures

**Symptoms:**
```
Cannot connect to database
Access denied for user 'root'
```

**Solutions:**

```bash
# A. Check MySQL pod status
kubectl get pods -n smartsplit -l app=mysql

# B. Check MySQL logs
kubectl logs deployment/mysql -n smartsplit

# C. Verify secret
kubectl get secret app-secrets -n smartsplit -o jsonpath='{.data.MYSQL_ROOT_PASSWORD}' | base64 --decode

# D. Test connection from backend pod
kubectl exec -it deployment/backend -n smartsplit -- /bin/sh
# Inside pod:
nc -zv mysql.smartsplit.svc.cluster.local 3306

# E. Restart MySQL (recreates pod)
kubectl rollout restart deployment/mysql -n smartsplit

# F. Check persistent volume
kubectl get pvc mysql-pvc -n smartsplit
kubectl describe pvc mysql-pvc -n smartsplit
```

#### 3. Ingress Not Working

**Symptoms:**
- Cannot access via Minikube IP
- 404 errors
- Connection refused

**Solutions:**

```bash
# A. Enable Ingress addon
minikube addons enable ingress

# B. Check Ingress status
kubectl get ingress -n smartsplit
kubectl describe ingress smartsplit-ingress -n smartsplit

# C. Verify Ingress controller
kubectl get pods -n ingress-nginx

# D. Check service endpoints
kubectl get endpoints -n smartsplit

# E. Test services directly
kubectl port-forward -n smartsplit svc/frontend 3000:80
curl http://localhost:3000

# F. Check Ingress logs
kubectl logs -n ingress-nginx <ingress-controller-pod>

# G. Verify Minikube tunnel (macOS/Linux)
minikube tunnel
```

#### 4. Backend Health Check Failures

**Symptoms:**
```
Liveness probe failed: Get http://...:8081/actuator/health
Readiness probe failed
```

**Solutions:**

```bash
# A. Check backend logs
kubectl logs deployment/backend -n smartsplit -f

# B. Increase probe delays
kubectl edit deployment backend -n smartsplit
# Increase initialDelaySeconds (e.g., 90 → 120)

# C. Test actuator endpoint
kubectl exec -it deployment/backend -n smartsplit -- curl localhost:8081/actuator/health

# D. Check environment variables
kubectl exec deployment/backend -n smartsplit -- env | grep -E "SPRING|MYSQL|JWT"

# E. Verify database migration
kubectl logs deployment/backend -n smartsplit | grep -i flyway
```

#### 5. Persistent Volume Issues

**Symptoms:**
```
PersistentVolumeClaim is not bound
Pod has unbound PersistentVolumeClaims
```

**Solutions:**

```bash
# A. Check PV status
kubectl get pv
kubectl get pvc -n smartsplit

# B. Describe PVC for events
kubectl describe pvc mysql-pvc -n smartsplit

# C. Verify storage class
kubectl get storageclass

# D. For Minikube, ensure correct path
kubectl describe pv mysql-pv

# E. Recreate PV/PVC if needed
kubectl delete pvc mysql-pvc -n smartsplit
kubectl delete pv mysql-pv
kubectl apply -f k8s/mysql/mysql-pv.yaml
```

#### 6. Out of Memory Errors

**Symptoms:**
```
OOMKilled
Container killed
```

**Solutions:**

```bash
# A. Check resource usage
kubectl top pods -n smartsplit

# B. Increase memory limits
kubectl edit deployment backend -n smartsplit
# Increase resources.limits.memory

# C. Check for memory leaks in logs
kubectl logs <pod-name> -n smartsplit | grep -i memory

# D. Restart deployment
kubectl rollout restart deployment/backend -n smartsplit
```

### Debugging Commands

**Complete Cluster State:**
```bash
# All resources in namespace
kubectl get all -n smartsplit

# Detailed cluster info
kubectl cluster-info
kubectl cluster-info dump

# Node information
kubectl get nodes
kubectl describe node minikube
```

**Pod Debugging:**
```bash
# Debug pod with shell access
kubectl debug <pod-name> -n smartsplit -it --image=busybox

# Create debug container in existing pod (Kubernetes 1.23+)
kubectl debug -it <pod-name> -n smartsplit --image=ubuntu --target=backend

# Copy files from pod
kubectl cp smartsplit/<pod-name>:/app/logs ./local-logs

# Copy files to pod
kubectl cp ./local-file smartsplit/<pod-name>:/app/
```

**Network Debugging:**
```bash
# Test DNS resolution
kubectl run -it --rm debug \
  --image=busybox \
  --restart=Never \
  -n smartsplit \
  -- nslookup backend.smartsplit.svc.cluster.local

# Test network connectivity
kubectl run -it --rm debug \
  --image=nicolaka/netshoot \
  --restart=Never \
  -n smartsplit \
  -- /bin/bash
# Inside pod:
curl http://backend.smartsplit.svc.cluster.local:8081/actuator/health
```

### Logging Best Practices

**Centralized Logging:**
```bash
# Stream all backend logs
kubectl logs -n smartsplit -l app=backend -f

# Last 100 lines
kubectl logs -n smartsplit deployment/backend --tail=100

# Since timestamp
kubectl logs -n smartsplit deployment/backend --since=1h

# Export logs to file
kubectl logs -n smartsplit deployment/backend > backend-logs.txt
```

---

## Advanced Operations

### Cluster Management

**Minikube Operations:**
```bash
# Start with custom resources
minikube start --cpus=6 --memory=16384 --disk-size=50g

# Stop cluster (preserves state)
minikube stop

# Delete cluster (removes all data)
minikube delete

# Pause cluster
minikube pause

# Unpause cluster
minikube unpause

# SSH into Minikube node
minikube ssh

# View Minikube dashboard
minikube dashboard

# Get Minikube IP
minikube ip

# Enable/disable addons
minikube addons list
minikube addons enable metrics-server
minikube addons enable dashboard
minikube addons disable ingress
```

**Cluster Context:**
```bash
# View contexts
kubectl config get-contexts

# Switch context
kubectl config use-context minikube

# View current context
kubectl config current-context

# Set default namespace
kubectl config set-context --current --namespace=smartsplit
```

### Backup and Restore

**Backup Kubernetes Resources:**
```bash
# Export all resources
kubectl get all -n smartsplit -o yaml > smartsplit-backup.yaml

# Export specific resources
kubectl get configmap app-config -n smartsplit -o yaml > configmap-backup.yaml
kubectl get secret app-secrets -n smartsplit -o yaml > secrets-backup.yaml
kubectl get deployment -n smartsplit -o yaml > deployments-backup.yaml

# Backup PVCs (data must be backed up separately)
kubectl get pvc -n smartsplit -o yaml > pvc-backup.yaml
```

**Backup Database:**
```bash
# MySQL dump
kubectl exec -n smartsplit deployment/mysql -- \
  mysqldump -uroot -p$MYSQL_ROOT_PASSWORD smartsplit-db \
  > smartsplit-db-backup.sql

# Backup with timestamp
kubectl exec -n smartsplit deployment/mysql -- \
  mysqldump -uroot -p$MYSQL_ROOT_PASSWORD smartsplit-db \
  > smartsplit-db-backup-$(date +%Y%m%d-%H%M%S).sql

# Backup all databases
kubectl exec -n smartsplit deployment/mysql -- \
  mysqldump -uroot -p$MYSQL_ROOT_PASSWORD --all-databases \
  > mysql-all-backup.sql
```

**Restore Database:**
```bash
# Restore from backup
kubectl exec -i -n smartsplit deployment/mysql -- \
  mysql -uroot -p$MYSQL_ROOT_PASSWORD smartsplit-db \
  < smartsplit-db-backup.sql

# Restore with progress
cat smartsplit-db-backup.sql | \
  kubectl exec -i -n smartsplit deployment/mysql -- \
  mysql -uroot -p$MYSQL_ROOT_PASSWORD smartsplit-db
```

**Backup Persistent Volumes:**
```bash
# Copy data from PV
kubectl exec -n smartsplit deployment/mysql -- \
  tar czf - /var/lib/mysql \
  > mysql-data-backup.tar.gz

# Restore data to PV
kubectl exec -i -n smartsplit deployment/mysql -- \
  tar xzf - -C / \
  < mysql-data-backup.tar.gz
```

### Disaster Recovery

**Complete Application Restore:**
```bash
# 1. Recreate namespace
kubectl apply -f k8s/namespace.yaml

# 2. Restore configs and secrets
kubectl apply -f configmap-backup.yaml
kubectl apply -f secrets-backup.yaml

# 3. Restore storage
kubectl apply -f k8s/mysql/mysql-pv.yaml
kubectl apply -f k8s/backend/uploads-pv.yaml

# 4. Restore deployments
kubectl apply -f k8s/mysql/mysql-deployment.yaml
kubectl apply -f k8s/backend/backend-deployment.yaml
kubectl apply -f k8s/frontend/frontend-deployment.yaml
kubectl apply -f k8s/ingress.yaml

# 5. Wait for MySQL to be ready
kubectl wait --namespace smartsplit \
  --for=condition=ready pod \
  -l app=mysql \
  --timeout=120s

# 6. Restore database
kubectl exec -i -n smartsplit deployment/mysql -- \
  mysql -uroot -p$MYSQL_ROOT_PASSWORD smartsplit-db \
  < smartsplit-db-backup.sql

# 7. Verify all pods
kubectl get pods -n smartsplit
```

### Performance Optimization

**Resource Tuning:**
```bash
# View resource usage trends
kubectl top pods -n smartsplit --containers

# Adjust resource limits based on usage
kubectl set resources deployment backend -n smartsplit \
  --limits=cpu=1500m,memory=1.5Gi \
  --requests=cpu=750m,memory=768Mi

# Adjust replicas for load
kubectl scale deployment backend --replicas=3 -n smartsplit
kubectl scale deployment frontend --replicas=3 -n smartsplit
```

**Horizontal Pod Autoscaling:**
```yaml
# Create HPA manifest: k8s/hpa.yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: backend-hpa
  namespace: smartsplit
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: backend
  minReplicas: 2
  maxReplicas: 10
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
  - type: Resource
    resource:
      name: memory
      target:
        type: Utilization
        averageUtilization: 80
```

```bash
# Apply HPA
kubectl apply -f k8s/hpa.yaml

# View HPA status
kubectl get hpa -n smartsplit

# Describe HPA
kubectl describe hpa backend-hpa -n smartsplit
```

**Database Performance:**
```bash
# Check MySQL performance
kubectl exec -it -n smartsplit deployment/mysql -- mysql -uroot -p$MYSQL_ROOT_PASSWORD -e "SHOW PROCESSLIST;"

# View slow queries
kubectl exec -it -n smartsplit deployment/mysql -- mysql -uroot -p$MYSQL_ROOT_PASSWORD -e "SELECT * FROM information_schema.PROCESSLIST WHERE TIME > 5;"

# Optimize tables
kubectl exec -it -n smartsplit deployment/mysql -- mysql -uroot -p$MYSQL_ROOT_PASSWORD -e "OPTIMIZE TABLE smartsplit-db.*;"
```

### Security Hardening

**Network Policies:**
```yaml
# Create network policy: k8s/network-policy.yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: backend-network-policy
  namespace: smartsplit
spec:
  podSelector:
    matchLabels:
      app: backend
  policyTypes:
  - Ingress
  - Egress
  ingress:
  - from:
    - podSelector:
        matchLabels:
          app: frontend
    - namespaceSelector:
        matchLabels:
          name: ingress-nginx
    ports:
    - protocol: TCP
      port: 8081
  egress:
  - to:
    - podSelector:
        matchLabels:
          app: mysql
    ports:
    - protocol: TCP
      port: 3306
```

**Security Context:**
```yaml
# Add to deployment:
securityContext:
  runAsNonRoot: true
  runAsUser: 1000
  fsGroup: 1000
  capabilities:
    drop:
    - ALL
  readOnlyRootFilesystem: true
```

**Secret Management:**
```bash
# Encrypt secrets at rest (cluster-level)
# For production, use external secrets manager

# Rotate secrets
kubectl create secret generic app-secrets-new \
  --from-literal=MYSQL_ROOT_PASSWORD=new-password \
  --from-literal=APP_JWT_SECRET=new-jwt-secret \
  --namespace smartsplit \
  --dry-run=client -o yaml | kubectl apply -f -

# Update deployments to use new secret
kubectl set env deployment/backend --from=secret/app-secrets-new -n smartsplit
kubectl set env deployment/mysql --from=secret/app-secrets-new -n smartsplit
```

### Multi-Environment Setup

**Environment-Specific Configs:**
```bash
# Directory structure:
# k8s/
# ├── base/           # Common resources
# ├── dev/            # Development overlays
# ├── staging/        # Staging overlays
# └── production/     # Production overlays

# Use Kustomize for overlays
kubectl apply -k k8s/dev/
kubectl apply -k k8s/staging/
kubectl apply -k k8s/production/
```

### CI/CD Integration

**Automated Deployment (GitHub Actions):**
```yaml
# .github/workflows/deploy.yml
- name: Update Kubernetes Deployment
  run: |
    kubectl set image deployment/backend \
      backend=sakanaisreal/smartsplit-backend:${{ github.sha }} \
      -n smartsplit

    kubectl set image deployment/frontend \
      frontend=sakanaisreal/smartsplit-frontend:${{ github.sha }} \
      -n smartsplit

    kubectl rollout status deployment/backend -n smartsplit
    kubectl rollout status deployment/frontend -n smartsplit
```

**Rollback Strategy:**
```bash
# View deployment history
kubectl rollout history deployment/backend -n smartsplit

# Rollback to previous version
kubectl rollout undo deployment/backend -n smartsplit

# Rollback to specific revision
kubectl rollout undo deployment/backend -n smartsplit --to-revision=3

# Verify rollback
kubectl rollout status deployment/backend -n smartsplit
```

### Cleanup Operations

**Delete Specific Resources:**
```bash
# Delete deployment
kubectl delete deployment backend -n smartsplit

# Delete service
kubectl delete svc backend -n smartsplit

# Delete all deployments
kubectl delete deployments --all -n smartsplit

# Delete by label
kubectl delete pods -l app=backend -n smartsplit
```

**Complete Cleanup:**
```bash
# Delete entire namespace (removes all resources)
kubectl delete namespace smartsplit

# Or delete all resources individually
kubectl delete -f k8s/ingress.yaml
kubectl delete -f k8s/frontend/
kubectl delete -f k8s/backend/
kubectl delete -f k8s/mysql/
kubectl delete -f k8s/secrets.yaml
kubectl delete -f k8s/configmap.yaml
kubectl delete -f k8s/namespace.yaml

# Delete persistent volumes (data will be lost!)
kubectl delete pv mysql-pv uploads-pv

# Stop Minikube
minikube stop

# Delete Minikube cluster
minikube delete
```

---

## Reference

### Quick Command Reference

| Task | Command |
|------|---------|
| Deploy to Minikube | `./deploy-minikube.sh` or `.\deploy-minikube.ps1` |
| Get all resources | `kubectl get all -n smartsplit` |
| View pods | `kubectl get pods -n smartsplit` |
| View logs | `kubectl logs <pod-name> -n smartsplit -f` |
| Execute in pod | `kubectl exec -it <pod-name> -n smartsplit -- /bin/bash` |
| Port forward | `kubectl port-forward svc/backend 8081:8081 -n smartsplit` |
| Scale deployment | `kubectl scale deployment backend --replicas=3 -n smartsplit` |
| Update image | `kubectl set image deployment/backend backend=backend:v2 -n smartsplit` |
| Rollback | `kubectl rollout undo deployment/backend -n smartsplit` |
| Delete namespace | `kubectl delete namespace smartsplit` |

### Resource Limits Reference

| Resource | CPU Request | CPU Limit | Memory Request | Memory Limit |
|----------|-------------|-----------|----------------|--------------|
| Frontend | 250m | 500m | 128Mi | 256Mi |
| Backend | 500m | 1000m | 512Mi | 1Gi |
| MySQL | 500m | 1000m | 512Mi | 1Gi |

### Port Reference

| Service | Internal Port | External Access |
|---------|---------------|-----------------|
| Frontend | 80 | Minikube IP / localhost:3000 |
| Backend | 8081 | Minikube IP/api / localhost:8081 |
| MySQL | 3306 | localhost:3306 (port-forward only) |

### Environment Variables Reference

See [ConfigMap](#configmap) and [Secrets](#secrets) sections.

---

## Additional Resources

- **Kubernetes Official Docs**: https://kubernetes.io/docs/
- **Minikube Docs**: https://minikube.sigs.k8s.io/docs/
- **kubectl Cheat Sheet**: https://kubernetes.io/docs/reference/kubectl/cheatsheet/
- **Ingress NGINX**: https://kubernetes.github.io/ingress-nginx/
- **Project README**: [README.md](README.md)
- **CI/CD Documentation**: [CICD_PROCESS.md](CICD_PROCESS.md)
