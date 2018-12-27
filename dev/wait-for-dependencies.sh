#!/usr/bin/env bash

set -eu

MAX_ATTEMPTS=120

echo "Waiting for PostgreSQL ..."

if [[ ! -d "/root/.postgresql" ]]; then
    mkdir /root/.postgresql
    cp "${PGSSLROOTCERT}" /root/.postgresql/root.crt
fi

ATTEMPTS=0
PG=""
SQL="SELECT 1"

while [[ -z "${PG}" && "${ATTEMPTS}" -lt "${MAX_ATTEMPTS}" ]]; do
    export PGPASSWORD=unilog_password
    PG=$( (psql --username=unilog_username --host=postgres --dbname=unilogdb --command "${SQL}" 2>&1 | grep "(1 row)") || echo "")
    let ATTEMPTS+=1
    sleep 1
done

if [[ -z "${PG}" ]]; then
    echo "PostgreSQL is not available"
    exit 1
fi

echo "PostgreSQL is ready!"
