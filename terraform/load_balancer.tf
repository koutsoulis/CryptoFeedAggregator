resource "aws_security_group" "alb_sg" {
  name   = "alb-example-sg"
  vpc_id = aws_vpc.my_vpc.id
}

resource "aws_lb" "my_alb" {
  name               = "my-application-lb"
  internal           = false
  load_balancer_type = "application"
  security_groups    = [aws_security_group.alb_sg.id]
  subnets            = [aws_subnet.public_subnet.id, aws_subnet.public_subne2.id]
}

# listeners

resource "aws_lb_listener" "redirect_http_to_https" {
  load_balancer_arn = aws_lb.my_alb.arn
  port              = "80"
  protocol          = "HTTP"

  default_action {
    type = "redirect"

    redirect {
      port        = "443"
      protocol    = "HTTPS"
      status_code = "HTTP_301"
    }
  }
}

resource "aws_lb_listener" "backend" {
  load_balancer_arn = aws_lb.my_alb.arn
  port              = 443
  protocol          = "HTTPS"
  ssl_policy        = "ELBSecurityPolicy-2016-08"
  certificate_arn   = "arn:aws:acm:eu-north-1:905418066033:certificate/b942ea00-5430-4584-a7cf-2d59b7d03950"

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.example.arn
  }
}

resource "aws_lb_listener_rule" "rule_4041" {
  listener_arn = aws_lb_listener.backend.arn

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.backend.arn
  }

  condition {
    host_header {
      values = ["typelevel-server-new.kotopoulion.xyz"]
    }
  }
}

resource "aws_lb_listener_rule" "rule_3000" {
  listener_arn = aws_lb_listener.backend.arn

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.example.arn
  }

  condition {
    host_header {
      values = ["grafana.kotopoulion.xyz"]
    }
  }
}

# security group rules

resource "aws_vpc_security_group_ingress_rule" "alb-reachable-from-public-http" {
  security_group_id = aws_security_group.alb_sg.id

  cidr_ipv4   = "0.0.0.0/0"
  from_port   = 80
  ip_protocol = "tcp"
  to_port     = 80
}

resource "aws_vpc_security_group_ingress_rule" "alb-reachable-from-public-https" {
  security_group_id = aws_security_group.alb_sg.id

  cidr_ipv4   = "0.0.0.0/0"
  from_port   = 443
  ip_protocol = "tcp"
  to_port     = 443
}

resource "aws_vpc_security_group_egress_rule" "alb-reach-typelevel-server" {
  security_group_id = aws_security_group.alb_sg.id
  referenced_security_group_id = aws_security_group.security_group.id

  from_port   = 4041
  to_port     = 4041
  ip_protocol = "tcp"
}

resource "aws_vpc_security_group_egress_rule" "alb-reach-grafana" {
  security_group_id = aws_security_group.alb_sg.id
  referenced_security_group_id = aws_security_group.security_group.id

  from_port   = 3000
  to_port     = 3000
  ip_protocol = "tcp"
}