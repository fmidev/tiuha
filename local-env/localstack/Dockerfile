FROM localstack/localstack:1.4

ENV DATA_DIR=/tmp/localstack/data
ENV SERVICES=s3

RUN chmod 664 /tmp/localstack/server.test.pem /tmp/localstack/server.test.pem.key /tmp/localstack/server.test.pem.crt

COPY docker-entrypoint-initaws.d /docker-entrypoint-initaws.d/
COPY supervisord.conf /etc/supervisord.conf
