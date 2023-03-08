FROM localstack/localstack:1.4

ENV DATA_DIR=/tmp/localstack/data

COPY docker-entrypoint-initaws.d /docker-entrypoint-initaws.d/
COPY supervisord.conf /etc/supervisord.conf
