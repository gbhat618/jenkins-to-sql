#!/bin/bash
set -e

# Set these variables or export them before running
: "${DOCKERHUB_USER:?Set DOCKERHUB_USER}"
: "${DOCKERHUB_REPO:?Set DOCKERHUB_REPO}"
: "${TAG:=latest}"

IMAGE="$DOCKERHUB_USER/$DOCKERHUB_REPO:$TAG"

echo "Building and Push Docker image: $IMAGE"
docker buildx build --platform=linux/amd64 -t gbhat618/jenkins-to-sql:amd64 --push .

echo "Done."