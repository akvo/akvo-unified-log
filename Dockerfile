FROM openjdk:11.0.1-jre-slim

RUN set -ex; apt-get update && \
    apt-get install -y git openssh-client && \
    rm -rf /var/cache/apt/*

WORKDIR /app
COPY prod/start-prod-env.sh /app/start-prod-env.sh
RUN chmod u+x /app/start-prod-env.sh
COPY target/akvo-unilog.jar /app/akvo-unilog.jar

CMD ["/app/start-prod-env.sh"]
