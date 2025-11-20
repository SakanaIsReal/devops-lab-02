#!/bin/bash

# Default image tag to 'latest', but allow override via environment variable
IMAGE_TAG=${IMAGE_TAG:-latest}

echo -e "\033[32mStarting Minikube deployment process...\033[0m"
echo -e "\033[36mUsing image tag: $IMAGE_TAG\033[0m"

# Start Minikube if not running
echo -e "\033[33mStarting Minikube...\033[0m"
if ! minikube status &>/dev/null; then
    echo -e "\033[33mStarting Minikube cluster...\033[0m"
    minikube start
else
    echo -e "\033[32mMinikube is already running\033[0m"
fi

# Set docker env to use minikube's docker daemon
echo -e "\033[33mSetting docker environment to Minikube...\033[0m"
eval $(minikube docker-env)

# Build Docker images with specified tag (default: latest)
echo -e "\033[33mBuilding Docker images with tag: $IMAGE_TAG...\033[0m"
docker build -t "sakanaisreal/smartsplit-backend:$IMAGE_TAG" ./backend
docker build -t "sakanaisreal/smartsplit-frontend:$IMAGE_TAG" ./frontend

# Tag as :latest as well for local development convenience
if [ "$IMAGE_TAG" != "latest" ]; then
    docker tag "sakanaisreal/smartsplit-backend:$IMAGE_TAG" sakanaisreal/smartsplit-backend:latest
    docker tag "sakanaisreal/smartsplit-frontend:$IMAGE_TAG" sakanaisreal/smartsplit-frontend:latest
fi

# Create namespace
echo -e "\033[33mCreating namespace...\033[0m"
kubectl apply -f k8s/namespace.yaml

# Apply Kubernetes configurations
echo -e "\033[33mApplying Kubernetes configurations...\033[0m"
kubectl apply -f k8s/configmap.yaml
kubectl apply -f k8s/secrets.yaml
kubectl apply -f k8s/mysql/
kubectl apply -f k8s/backend/
kubectl apply -f k8s/frontend/
kubectl apply -f k8s/ingress.yaml

# Enable ingress addon
echo -e "\033[33mEnabling Ingress addon...\033[0m"
minikube addons enable ingress

# Wait for pods to be ready
echo -e "\033[33mWaiting for pods to be ready...\033[0m"
kubectl wait --namespace smartsplit --for=condition=ready pod --all --timeout=300s

# Get service URLs
echo -e "\n\033[32mDeployment completed!\033[0m"
echo -e "\033[33mGetting Minikube IP...\033[0m"
MINIKUBE_IP=$(minikube ip)
echo "Minikube IP: $MINIKUBE_IP"

echo -e "\n\033[32mAccess your application:\033[0m"
echo -e "\033[36mFrontend: http://$MINIKUBE_IP\033[0m"
echo -e "\033[36mBackend API: http://$MINIKUBE_IP/api\033[0m"

# Show pods status
echo -e "\n\033[32mPod Status:\033[0m"
kubectl get pods -n smartsplit