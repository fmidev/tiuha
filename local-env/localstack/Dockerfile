FROM localstack/localstack:0.12.19

ENV DATA_DIR=/tmp/localstack/data

RUN chmod 664 /opt/code/localstack/localstack/infra/dynamodb/log4j2.xml

COPY docker-entrypoint-initaws.d /docker-entrypoint-initaws.d/
COPY supervisord.conf /etc/supervisord.conf
