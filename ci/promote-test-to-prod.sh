#!/usr/bin/env bash

set -u

function log {
   echo "$(date +"%T") - INFO - $*"
}

PREVIOUS_CONTEXT=$(kubectl config current-context)

function switch_back () {
    log "Switching k8s context back to ${PREVIOUS_CONTEXT}"
    kubectl config use-context "${PREVIOUS_CONTEXT}"
}

function read_version () {
    CLUSTER=$1
    log "Reading ${CLUSTER} version"
    log "running: gcloud container clusters get-credentials ${CLUSTER} --zone europe-west1-d --project akvo-lumen"
    if ! gcloud container clusters get-credentials "${CLUSTER}" --zone europe-west1-d --project akvo-lumen; then
        log "Could not change context to ${CLUSTER}. Nothing done."
        switch_back
        exit 3
    fi

    VERSION=$(kubectl get deployments akvo-unilog -o jsonpath="{@.spec.template.metadata.labels['akvo-unilog-version']}")
}

read_version "test"
TEST_VERSION=$VERSION

read_version "production"
PROD_VERSION=$VERSION

log "Deployed test version is $TEST_VERSION"
log "Deployed prod version is $PROD_VERSION"
log "See https://github.com/akvo/akvo-unified-log/compare/$PROD_VERSION..$TEST_VERSION"

TAG_NAME="promote-$(date +"%Y%m%d-%H%M%S")"

read -r -e -p "Are you sure you want to promote to production? [yn] " CONFIRM
if [[ "${CONFIRM}" != "y" ]]; then
    log "Nothing done"
    switch_back
    exit 1
else
    log "Tagging with $TAG_NAME and pushing to Github. Travis will deploy."
    git tag $TAG_NAME $TEST_VERSION
    git push origin $TAG_NAME
fi

switch_back