#!/usr/bin/env bash

set -eu
ON_ERROR_STOP=1
export ON_ERROR_STOP

SUPER_USER=postgres
DB_HOST=localhost

## Postgres user password in Google Cloud SQL
export PGPASSWORD=""

## User/Password for the Unilog app
UNILOG_USER=
UNILOG_USER_PASSWORD=
UNILOG_DB_NAME=unilog

psql_settings=("--username=${SUPER_USER}" "--host=${DB_HOST}")


psql "${psql_settings[@]}" --command="CREATE USER ${UNILOG_USER} WITH CREATEDB ENCRYPTED PASSWORD '${UNILOG_USER_PASSWORD}';"
psql "${psql_settings[@]}" --command="GRANT ${UNILOG_USER} TO ${SUPER_USER};"

psql "${psql_settings[@]}" <<-EOSQL
CREATE DATABASE $UNILOG_DB_NAME
WITH OWNER = $UNILOG_USER
     ENCODING = 'UTF8'
     LC_COLLATE = 'en_US.UTF8'
     LC_CTYPE = 'en_US.UTF8';
EOSQL

psql "${psql_settings[@]}" --dbname="$UNILOG_DB_NAME" --command="ALTER SCHEMA public OWNER TO ${UNILOG_USER};"
psql "${psql_settings[@]}" --dbname="$UNILOG_DB_NAME" --command="REVOKE CREATE ON SCHEMA public FROM public;"

echo "----------"
echo "Done!"