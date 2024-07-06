#!/bin/zsh
aws ecr get-login-password --region eu-north-1 | docker login --username AWS --password-stdin 905418066033.dkr.ecr.eu-north-1.amazonaws.com
gpg -d grafana.ini.gpg > grafana.ini
docker buildx build --platform linux/amd64 --load -t grafana .
docker tag grafana:latest 905418066033.dkr.ecr.eu-north-1.amazonaws.com/grafana:amd64
docker push 905418066033.dkr.ecr.eu-north-1.amazonaws.com/grafana:amd64