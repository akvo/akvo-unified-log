#!/usr/bin/env bash

set -eu

## Fill ElephantSQL details
export DB_HOST=""
export DB_NAME=""
export DB_USER=""
export PASSWORD=""

dbs=$(PGPASSWORD="${PASSWORD}" psql -t --host="${DB_HOST}" --username="${DB_USER}" --dbname="${DB_NAME}"  -c "SELECT datname FROM pg_database WHERE datistemplate = false and datname like 'u\_%'")

for i in $dbs; do
  echo $i
  ./dump-one-db.sh $i $i.gz
done
