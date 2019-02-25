#!/usr/bin/env bash

set -e

if [ ! -d "/tmp/here/akvo-flow-server-config" ]; then
    if [ -d "dev/flow-server-config" ]; then
        echo "Creating fake git repo with Flow config"
        cp -r dev/flow-server-config /akvo-flow-server-config
        current_dir=$(pwd)
        cd /akvo-flow-server-config > /dev/null
        git init
        git config --global user.email "you@example.com"
        git config --global user.name "Your Name"
        git add -A
        git commit -m "Initial commit"
        git clone /akvo-flow-server-config /tmp/here/akvo-flow-server-config
        cd ${current_dir}
    else
        echo "Checking out Github repo"
        ssh-keyscan github.com >> ~/.ssh/known_hosts
        git clone git@github.com:akvo/akvo-flow-server-config.git /tmp/here/akvo-flow-server-config
    fi
fi

if [[ "${WAIT_FOR_DEPS:=false}" = "true" ]]; then
  /app/dev/wait-for-dependencies.sh
else
  mkdir /root/.postgresql
  cp /etc/ssl/certs/ca-certificates.crt /root/.postgresql/root.crt
fi

_term() {
  echo "Caught SIGTERM signal!"
  kill -TERM "$child" 2>/dev/null
}

trap _term SIGTERM

java -jar akvo-unilog.jar /etc/config/akvo-unilog/config.edn &

child=$!
wait "$child"