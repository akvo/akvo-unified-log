#!/usr/bin/env bash

set -eu
ON_ERROR_STOP=1
export ON_ERROR_STOP

psql -v --username "${POSTGRES_USER}" --dbname "${POSTGRES_DB}" <<-EOSQL
  CREATE USER unilog_username WITH CREATEDB PASSWORD 'unilog_password';
EOSQL

psql -v --username "${POSTGRES_USER}" --dbname "${POSTGRES_DB}" <<-EOSQL
CREATE DATABASE unilogdb
WITH OWNER = unilog_username
     TEMPLATE = template0
     ENCODING = 'UTF8'
     LC_COLLATE = 'en_US.UTF-8'
     LC_CTYPE = 'en_US.UTF-8';
EOSQL

echo "----------"
echo "Done!"