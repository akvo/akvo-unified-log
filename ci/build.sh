#!/usr/bin/env bash

set -e

function log {
   echo "$(date +"%T") - INFO - $*"
}

export PROJECT_NAME=akvo-lumen
BRANCH_NAME="${TRAVIS_BRANCH:=unknown}"

if [ -z "$TRAVIS_COMMIT" ]; then
    export TRAVIS_COMMIT=local
fi

log Building backend dev container
docker build --rm=false -t akvo-unilog-dev:develop . -f Dockerfile-dev
log Running backend test and uberjar
docker run -v $HOME/.m2:/root/.m2 -v `pwd`:/app akvo-unilog-dev:develop lein do test, uberjar

log Building production container
docker build --rm=false -t eu.gcr.io/${PROJECT_NAME}/akvo-unilog:$TRAVIS_COMMIT .
docker tag eu.gcr.io/${PROJECT_NAME}/akvo-unilog:$TRAVIS_COMMIT eu.gcr.io/${PROJECT_NAME}/akvo-unilog:develop

log Starting docker compose env
docker-compose -p akvo-unilog-ci -f docker-compose.yml -f docker-compose.ci.yml up -d --build
log Running integration tests
docker-compose -p akvo-unilog-ci -f docker-compose.yml -f docker-compose.ci.yml run --no-deps tests dev/start-dev-env.sh integration-test

log Done