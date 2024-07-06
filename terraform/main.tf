terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.56.1"
    }
  }

  required_version = ">= 1.2.0"
}

provider "aws" {
  region = "eu-north-1"
}

resource "aws_ecs_cluster" "ecs-typelevel-project-cluster" {
  name = "ecs-typelevel-production-cluster-name"
}

resource "aws_iam_role" "ecs_instance_role" {
  name = "ecs_instance_role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "ec2.amazonaws.com"
        }
      },
    ]
  })
}

resource "aws_iam_role_policy_attachment" "ecs_role_policy_attachment" {
  role       = aws_iam_role.ecs_instance_role.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonEC2ContainerServiceforEC2Role"
}

resource "aws_iam_instance_profile" "ecs_instance_profile" {
  name = "ecs_instance_profile"
  role = aws_iam_role.ecs_instance_role.name
}

resource "aws_key_pair" "deployer" {
  key_name   = "forEC2instances"
  public_key = "ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAACAQDP0mqO2iP+HWWPta5AkI4M1662RbRDAwKqpy8VQTuSCSmfmGQErkkve4H449EUn+FKJyBxfOnE50spaEfLBHIBg+ZM12Nk1c4hAv1uo2Chr03jxwC70GNCEM19GZqCW37aMxjLJTHGISvG81FhV0X91qu3fcHQfRPmMVL/1mCHSDh+5ODCFummQ5yQ3zJYng9dLMa59neI3qXVvSMjP+UcP1KQxpD14+HrU0ODViXAhzlI2XMO+RSy4FGZ7V5bEynquIi8Da7w6SKFDKx8l35pxO08QJJzAidlg8h5GQiK+ixz8T0BaWFXcMkXnGl7M+1UEqyP1xCZgtKpp8nmnCFMEQ9tn1zKRwgt6RT0E6au62yRYpSTuCNvatdt0SsuDqS3Fi++sHgV1hZnLERd5nv20C7BbMpCaXR8kD19Mrd0l5d2zkoR/8BG4nRTccxqAT2KTAu0sfsbv2Ru73ouvief/zWiwTL4UYnkuTh09gqNHxnP0Llkjm9taPJ9s0yi0FLguhn6ZmyJP1m3Nxc+roxl9f4Ctvb1YDKJSCVmt6hmgimGPUvSV4THaWR2uOpLkMOSe+zl/A3kW72NgNdxVA7hv1YR/BqtcfqsWeeZujtm5CDZKH7rP746PQJY/HxYg/cEXnIEn9FcHcQF0Yb1VI+CEqKvOAiBule10WD8vLuWEQ== forEC2instances"
}

resource "aws_launch_template" "example" {
  name_prefix   = "example-lt-"
  image_id      = "ami-0711d51b1781bebb9" # al2023-ami-ecs-hvm-2023.0.20240625-kernel-6.1-x86_64
  instance_type = "t3.micro"
  network_interfaces {
    associate_public_ip_address = true
    security_groups             = [aws_security_group.security_group.id]
  }
  key_name = aws_key_pair.deployer.key_name

  iam_instance_profile {
    name = "ecsInstanceRole"
  }

  user_data = filebase64("${path.module}/ecs.sh")
}

resource "aws_autoscaling_group" "my_asg" {
  launch_template {
    id      = aws_launch_template.example.id
    version = "$Latest"
  }
  min_size            = 1
  max_size            = 2
  vpc_zone_identifier = [aws_subnet.public_subnet.id]

  instance_refresh {
    strategy = "Rolling"
  }

  tag {
    key                 = "Name"
    value               = "ECS Instance"
    propagate_at_launch = true
  }

  tag {
    key                 = "AmazonECSManaged"
    value               = true
    propagate_at_launch = true
  }

  target_group_arns = [aws_lb_target_group.example.arn, aws_lb_target_group.backend.arn]
}

resource "aws_ecs_capacity_provider" "my_ec2_provider" {
  name = "myEC2CapacityProvider"

  auto_scaling_group_provider {
    auto_scaling_group_arn = aws_autoscaling_group.my_asg.arn
    # managed_termination_protection = "ENABLED"
  }
}

resource "aws_ecs_cluster_capacity_providers" "example" {
  cluster_name = aws_ecs_cluster.ecs-typelevel-project-cluster.name

  capacity_providers = [aws_ecs_capacity_provider.my_ec2_provider.name]

  default_capacity_provider_strategy {
    base              = 1
    weight            = 100
    capacity_provider = aws_ecs_capacity_provider.my_ec2_provider.name
  }
}

resource "aws_vpc" "my_vpc" {
  cidr_block           = "10.0.0.0/16"
  enable_dns_hostnames = true
}

resource "aws_security_group" "security_group" {
  name   = "ecs-security-group"
  vpc_id = aws_vpc.my_vpc.id
}

# ecs agent can reach ECS service endpoint and register EC2 instance to my ECS cluster
resource "aws_vpc_security_group_egress_rule" "https" {
  security_group_id = aws_security_group.security_group.id

  cidr_ipv4   = "0.0.0.0/0"
  from_port   = 443
  ip_protocol = "tcp"
  to_port     = 443
}

resource "aws_vpc_security_group_egress_rule" "typelevel_server_access_binance" {
  security_group_id = aws_security_group.security_group.id

  cidr_ipv4   = "0.0.0.0/0"
  from_port   = 9443
  ip_protocol = "tcp"
  to_port     = 9443
}

resource "aws_vpc_security_group_ingress_rule" "typelevel_server" {
  security_group_id = aws_security_group.security_group.id

  referenced_security_group_id = aws_security_group.alb_sg.id
  from_port                    = 4041
  ip_protocol                  = "tcp"
  to_port                      = 4041
}

resource "aws_vpc_security_group_ingress_rule" "typelevel_server_public" {
  security_group_id = aws_security_group.security_group.id

  cidr_ipv4   = "0.0.0.0/0"
  from_port   = 4041
  ip_protocol = "tcp"
  to_port     = 4041
}

resource "aws_vpc_security_group_ingress_rule" "grafana" {
  security_group_id = aws_security_group.security_group.id

  referenced_security_group_id = aws_security_group.alb_sg.id
  from_port                    = 3000
  ip_protocol                  = "tcp"
  to_port                      = 3000
}

resource "aws_vpc_security_group_ingress_rule" "ssh" {
  security_group_id = aws_security_group.security_group.id

  cidr_ipv4   = "0.0.0.0/0"
  from_port   = 22
  ip_protocol = "tcp"
  to_port     = 22
}

resource "aws_subnet" "public_subnet" {
  vpc_id                  = aws_vpc.my_vpc.id
  cidr_block              = "10.0.1.0/24"
  map_public_ip_on_launch = true
  availability_zone       = "eu-north-1a"

  tags = {
    Name = "PublicSubnet"
  }
}

# forced on us by the application load balancer (requires minimum 2 subnets)
resource "aws_subnet" "public_subne2" {
  vpc_id                  = aws_vpc.my_vpc.id
  cidr_block              = "10.0.3.0/24"
  map_public_ip_on_launch = true
  availability_zone       = "eu-north-1b"

  tags = {
    Name = "PublicSubnet2"
  }
}

resource "aws_internet_gateway" "my_igw" {
  vpc_id = aws_vpc.my_vpc.id

  tags = {
    Name = "my_igw"
  }
}

resource "aws_route_table" "my_route_table" {
  vpc_id = aws_vpc.my_vpc.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.my_igw.id
  }

  tags = {
    Name = "my_route_table"
  }
}

resource "aws_route_table_association" "public_subnet_association" {
  subnet_id      = aws_subnet.public_subnet.id
  route_table_id = aws_route_table.my_route_table.id
}

resource "aws_route_table_association" "public_subnet2_association" {
  subnet_id      = aws_subnet.public_subne2.id
  route_table_id = aws_route_table.my_route_table.id
}

resource "aws_ecs_service" "server-grafana-prometheus" {
  name    = "server-grafana-prometheus-name"
  cluster = aws_ecs_cluster.ecs-typelevel-project-cluster.id
  # task_definition = "arn:aws:ecs:eu-north-1:905418066033:task-definition/typelevel-backend:27"
  task_definition = aws_ecs_task_definition.backend_prometheus_grafana.arn
  desired_count   = 1

  force_new_deployment = true
  placement_constraints {
    type = "distinctInstance"
  }

  capacity_provider_strategy {
    capacity_provider = aws_ecs_capacity_provider.my_ec2_provider.name
    weight            = 100
  }

  depends_on = [aws_autoscaling_group.my_asg]
}

resource "aws_lb_target_group" "example" {
  name        = "example-tg"
  port        = 3000
  protocol    = "HTTP"
  vpc_id      = aws_vpc.my_vpc.id
  target_type = "instance"

  health_check {
    enabled             = true
    path                = "/"
    protocol            = "HTTP"
    port                = 4041
    healthy_threshold   = 3
    unhealthy_threshold = 3
    timeout             = 5
    interval            = 30
    matcher             = "200"
  }
}

resource "aws_lb_target_group" "backend" {
  name        = "backend-target-group"
  port        = 4041
  protocol    = "HTTP"
  vpc_id      = aws_vpc.my_vpc.id
  target_type = "instance"

  health_check {
    enabled             = true
    path                = "/"
    protocol            = "HTTP"
    port                = 4041
    healthy_threshold   = 3
    unhealthy_threshold = 3
    timeout             = 5
    interval            = 30
    matcher             = "200"
  }
}
