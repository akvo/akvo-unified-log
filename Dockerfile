FROM openjdk:11.0.2-jre-slim

RUN set -ex; apt-get update && \
    mkdir -p /usr/share/man/man7/ && \
    apt-get -qq install -y --no-install-recommends --no-install-suggests \
    git openssh-client  postgresql-client && \
    rm -rf /var/cache/apt/* /usr/share/man/*

WORKDIR /app
COPY prod/start-prod-env.sh /app/start-prod-env.sh
RUN chmod u+x /app/start-prod-env.sh
COPY target/akvo-unilog.jar /app/akvo-unilog.jar

CMD ["/app/start-prod-env.sh"]
