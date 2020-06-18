#!/usr/bin/env bash

set -eu

DB_NAME="${1}"
DUMP_FILE="${2}"

export PGPASSWORD="${PASSWORD}"

echo "Creating new DB"
psql_settings=("--username=${DB_USER}" "--host=${DB_HOST}" "--set" "ON_ERROR_STOP=on")

psql "${psql_settings[@]}" --dbname="unilog" --command="CREATE DATABASE \"$DB_NAME\" ENCODING 'UTF8'"

psql_settings=("--username=${DB_USER}" "--host=${DB_HOST}" "--dbname=${DB_NAME}" "--set" "ON_ERROR_STOP=on")

gunzip --stdout "${DUMP_FILE}" \
  | sed -e "/ALTER DEFAULT PRIVILEGES FOR ROLE postgres/d" \
  | sed -e "/COMMENT ON SCHEMA public/d" \
  | sed -e "/CREATE SCHEMA public/d" \
  | psql "${psql_settings[@]}"