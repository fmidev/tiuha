FROM localstack/localstack:1.4

ENV DATA_DIR=/tmp/localstack/data
ENV SERVICES=s3

COPY docker-entrypoint-initaws.d /docker-entrypoint-initaws.d/
COPY supervisord.conf /etc/supervisord.conf
