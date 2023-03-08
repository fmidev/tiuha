FROM localstack/localstack:0.12.19

ENV DATA_DIR=/tmp/localstack/data

COPY docker-entrypoint-initaws.d /docker-entrypoint-initaws.d/
COPY supervisord.conf /etc/supervisord.conf
