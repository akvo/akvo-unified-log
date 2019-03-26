#!/usr/bin/env bash

set -eu

function log {
   echo "$(date +"%T") - INFO - $*"
}

export PROJECT_NAME=akvo-lumen

if [[ "${TRAVIS_BRANCH}" != "develop" ]] && [[ ! "${TRAVIS_TAG:-}" =~ promote-.* ]]; then
    exit 0
fi

if [[ "${TRAVIS_PULL_REQUEST}" != "false" ]]; then
    exit 0
fi

log Making sure gcloud and kubectl are installed and up to date
gcloud components install kubectl
gcloud components update
gcloud version
which gcloud kubectl

log Authentication with gcloud and kubectl
gcloud auth activate-service-account --key-file ci/gcloud-service-account.json
gcloud config set project akvo-lumen
gcloud config set container/cluster europe-west1-d
gcloud config set compute/zone europe-west1-d
gcloud config set container/use_client_certificate True

ENVIRONMENT=test
if [[ "${TRAVIS_TAG:-}" =~ promote-.* ]]; then
    log Environment is production
    gcloud container clusters get-credentials production
    ENVIRONMENT=production
    POD_CPU_REQUESTS="400m"
    POD_CPU_LIMITS="2000m"
    POD_MEM_REQUESTS="512Mi"
    POD_MEM_LIMITS="512Mi"
else
    log Environment is test
    gcloud container clusters get-credentials test
    POD_CPU_REQUESTS="200m"
    POD_CPU_LIMITS="400m"
    POD_MEM_REQUESTS="300Mi"
    POD_MEM_LIMITS="300Mi"

    log Pushing images
    gcloud auth configure-docker
    docker push eu.gcr.io/${PROJECT_NAME}/akvo-unilog
fi

log Deploying

sed -e "s/\$TRAVIS_COMMIT/$TRAVIS_COMMIT/" \
  -e "s/\${ENVIRONMENT}/${ENVIRONMENT}/" \
  -e "s/\${POD_CPU_REQUESTS}/${POD_CPU_REQUESTS}/" \
  -e "s/\${POD_MEM_REQUESTS}/${POD_MEM_REQUESTS}/" \
  -e "s/\${POD_CPU_LIMITS}/${POD_CPU_LIMITS}/" \
  -e "s/\${POD_MEM_LIMITS}/${POD_MEM_LIMITS}/" \
  ci/akvo-unilog.yaml.template > akvo-unilog.yaml

kubectl apply -f akvo-unilog.yaml

ci/wait-for-k8s-deployment-to-be-ready.sh

#docker-compose -p akvo-flow-ci -f docker-compose.yml -f docker-compose.ci.yml run --no-deps tests /import-and-run.sh kubernetes-test

log Done