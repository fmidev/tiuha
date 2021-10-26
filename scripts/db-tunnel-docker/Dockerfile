FROM amazon/aws-cli:2.2.35

RUN curl "https://s3.amazonaws.com/session-manager-downloads/plugin/latest/linux_64bit/session-manager-plugin.rpm" -o "session-manager-plugin.rpm"
RUN yum install -y session-manager-plugin.rpm
RUN yum install -y openssh-clients
RUN yum install -y nc

COPY session-manager-forward-psql.sh .
COPY known_hosts /root/.ssh/

EXPOSE 1111
HEALTHCHECK --interval=1s --start-period=30s \
  CMD ["nc", "-z", "localhost", "1111"]
ENTRYPOINT ["./session-manager-forward-psql.sh"]