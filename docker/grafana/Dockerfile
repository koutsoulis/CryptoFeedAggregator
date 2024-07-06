FROM public.ecr.aws/ubuntu/grafana:latest
COPY './grafana.ini' '/etc/grafana/grafana.ini'
COPY './dashboard.json' '/etc/grafana/dashboards/'
COPY './dashboard_config.yaml' '/etc/grafana/provisioning/dashboards/'
COPY './datasource.yaml' '/etc/grafana/provisioning/datasources/'

EXPOSE 3000
