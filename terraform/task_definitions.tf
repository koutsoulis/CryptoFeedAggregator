resource "aws_ecs_task_definition" "backend_prometheus_grafana" {
  family                   = "backend-prometheus-grafana"
  network_mode             = "bridge"
  requires_compatibilities = ["EC2"]
  cpu                      = "1024"
  memory                   = "768"
  runtime_platform {
    cpu_architecture        = "X86_64"
    operating_system_family = "LINUX"
  }
  task_role_arn         = aws_iam_role.ecs_task_role.arn
  execution_role_arn    = aws_iam_role.ecs_task_role.arn
  container_definitions = <<EOF
  [
        {
            "name": "typelevel-backend",
            "image": "905418066033.dkr.ecr.eu-north-1.amazonaws.com/typelevel-project-backend-amd64:0.1.1-SNAPSHOT",
            "cpu": 512,
            "memory": 512,
            "memoryReservation": 128,
            "portMappings": [
                {
                    "name": "4041port",
                    "containerPort": 4041,
                    "hostPort": 4041,
                    "protocol": "tcp",
                    "appProtocol": "http"
                }
            ],
            "essential": true,
            "environment": [],
            "environmentFiles": [],
            "mountPoints": [],
            "volumesFrom": [],
            "ulimits": [],
            "logConfiguration": {
                "logDriver": "awslogs",
                "options": {
                    "awslogs-group": "/ecs/",
                    "awslogs-create-group": "true",
                    "awslogs-region": "eu-north-1",
                    "awslogs-stream-prefix": "ecs"
                },
                "secretOptions": []
            },
            "healthCheck": {
                "command": [
                    "CMD-SHELL",
                    "exit 0"
                ],
                "interval": 30,
                "timeout": 5,
                "retries": 3
            },
            "systemControls": []
        },
        {
            "name": "prometheus",
            "image": "905418066033.dkr.ecr.eu-north-1.amazonaws.com/prometheus:amd64",
            "cpu": 256,
            "memory": 128,
            "memoryReservation": 128,
            "links": [
                "typelevel-backend"
            ],
            "portMappings": [
                {
                    "name": "prom_port",
                    "containerPort": 9090,
                    "hostPort": 9090,
                    "protocol": "tcp"
                }
            ],
            "essential": true,
            "environment": [],
            "environmentFiles": [],
            "mountPoints": [],
            "volumesFrom": [],
            "logConfiguration": {
                "logDriver": "awslogs",
                "options": {
                    "awslogs-group": "/ecs/typelevel-backend",
                    "awslogs-create-group": "true",
                    "awslogs-region": "eu-north-1",
                    "awslogs-stream-prefix": "ecs"
                },
                "secretOptions": []
            },
            "systemControls": []
        },
        {
            "name": "grafana",
            "image": "905418066033.dkr.ecr.eu-north-1.amazonaws.com/grafana:amd64",
            "cpu": 256,
            "memory": 128,
            "memoryReservation": 128,
            "links": [
                "prometheus"
            ],
            "portMappings": [
                {
                    "name": "grafana-3000-tcp",
                    "containerPort": 3000,
                    "hostPort": 3000,
                    "protocol": "tcp"
                }
            ],
            "essential": true,
            "environment": [],
            "environmentFiles": [],
            "mountPoints": [],
            "volumesFrom": [],
            "extraHosts": [],
            "logConfiguration": {
                "logDriver": "awslogs",
                "options": {
                    "awslogs-group": "/ecs/typelevel-backend",
                    "awslogs-create-group": "true",
                    "awslogs-region": "eu-north-1",
                    "awslogs-stream-prefix": "ecs"
                },
                "secretOptions": []
            },
            "systemControls": []
        }
    ]
EOF

}

data "aws_iam_policy_document" "instance_assume_role_policy" {
  statement {
    actions = ["sts:AssumeRole"]

    principals {
      type = "Service"
      identifiers = [
        "ec2.amazonaws.com",
        "ecs-tasks.amazonaws.com"
      ]
    }
  }
}

resource "aws_iam_role" "ecs_task_role" {
  name               = "ecs_task_role"
  assume_role_policy = data.aws_iam_policy_document.instance_assume_role_policy.json

  inline_policy {
    name   = "ecs_task_policy"
    policy = <<EOF
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Effect": "Allow",
            "Action": [
                "ecr:GetAuthorizationToken",
                "ecr:BatchCheckLayerAvailability",
                "ecr:GetDownloadUrlForLayer",
                "ecr:BatchGetImage",
                "logs:CreateLogStream",
                "logs:PutLogEvents"
            ],
            "Resource": "*"
        }
    ]
}
EOF
  }
}