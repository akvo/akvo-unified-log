#!/usr/bin/env bash

set -eu

## Fill ElephantSQL details. Could just use the existing dump files to do the for loop.
export DB_HOST=""
export DB_NAME=""
export DB_USER=""
export PASSWORD=""

dbs=$(PGPASSWORD="${PASSWORD}" psql -t --host="${DB_HOST}" --username="${DB_USER}" --dbname="${DB_NAME}"  -c "SELECT datname FROM pg_database WHERE datistemplate = false and datname like 'u\_%'")

## The Cloud SQL details. User/password is the unilog user, not the postgres one.
export DB_HOST=""
export DB_USER=""
export PASSWORD=""

for i in $dbs; do
  echo $i
  ./restore-one-db.sh $i $i.gz
done
