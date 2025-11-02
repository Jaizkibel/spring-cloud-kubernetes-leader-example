#!/bin/bash

# Check if minikube is running
if ! minikube status >/dev/null 2>&1; then
  echo "Minikube is not running. Starting minikube..."
  minikube start
  if [ $? -ne 0 ]; then
    echo "Failed to start minikube"
    exit 1
  fi
  echo "Minikube started successfully"
else
  echo "Minikube is already running"
fi

eval "$(minikube docker-env)"
./gradlew bootBuildImage --imageName=leader_example
kubectl apply -f deployment.yml
