#!/bin/bash

psql -c "CREATE ROLE unilog WITH PASSWORD 'password' CREATEDB LOGIN;"

psql <<EOF
  CREATE DATABASE unilog
  WITH OWNER = unilog
  TEMPLATE = template0
  ENCODING = 'UTF8'
  LC_COLLATE = 'en_US.UTF-8' \
  LC_CTYPE = 'en_US.UTF-8';
EOF
