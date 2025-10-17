#!/usr/bin/env pwsh

Write-Host "Starting Minikube deployment process..." -ForegroundColor Green

# Start Minikube if not running
Write-Host "Starting Minikube..." -ForegroundColor Yellow
$minikubeStatus = minikube status
if ($LASTEXITCODE -ne 0) {
    Write-Host "Starting Minikube cluster..." -ForegroundColor Yellow
    minikube start
} else {
    Write-Host "Minikube is already running" -ForegroundColor Green
}

# Set docker env to use minikube's docker daemon
Write-Host "Setting docker environment to Minikube..." -ForegroundColor Yellow
minikube docker-env | Invoke-Expression

# Build Docker images
Write-Host "Building Docker images..." -ForegroundColor Yellow
docker build -t backend:latest ./backend
docker build -t frontend:latest ./frontend

# Create namespace
Write-Host "Creating namespace..." -ForegroundColor Yellow
kubectl apply -f k8s/namespace.yaml

# Apply Kubernetes configurations
Write-Host "Applying Kubernetes configurations..." -ForegroundColor Yellow
kubectl apply -f k8s/configmap.yaml
kubectl apply -f k8s/secrets.yaml
kubectl apply -f k8s/mysql/
kubectl apply -f k8s/backend/
kubectl apply -f k8s/frontend/
kubectl apply -f k8s/ingress.yaml

# Enable ingress addon
Write-Host "Enabling Ingress addon..." -ForegroundColor Yellow
minikube addons enable ingress

# Wait for pods to be ready
Write-Host "Waiting for pods to be ready..." -ForegroundColor Yellow
kubectl wait --namespace smartsplit --for=condition=ready pod --all --timeout=300s

# Get service URLs
Write-Host "`nDeployment completed!" -ForegroundColor Green
Write-Host "Getting Minikube IP..." -ForegroundColor Yellow
$minikubeIp = minikube ip
Write-Host "Minikube IP: $minikubeIp"

Write-Host "`nAccess your application:" -ForegroundColor Green
Write-Host "Frontend: http://$minikubeIp" -ForegroundColor Cyan
Write-Host "Backend API: http://$minikubeIp/api" -ForegroundColor Cyan

# Show pods status
Write-Host "`nPod Status:" -ForegroundColor Green
kubectl get pods -n smartsplit