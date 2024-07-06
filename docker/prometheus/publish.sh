#!/bin/zsh
aws ecr get-login-password --region eu-north-1 | docker login --username AWS --password-stdin 905418066033.dkr.ecr.eu-north-1.amazonaws.com
docker buildx build --platform linux/amd64 --load -t prometheus .
docker tag prometheus:latest 905418066033.dkr.ecr.eu-north-1.amazonaws.com/prometheus:amd64
docker push 905418066033.dkr.ecr.eu-north-1.amazonaws.com/prometheus:amd64