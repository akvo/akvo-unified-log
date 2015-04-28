#!/bin/bash
set -e

# for each server configuration in 'akvo-flow-server-config' repository
for server_config in `find /var/akvo/unilog/akvo-flow-server-config/ -not -path '*/\.*' -type d -maxdepth 1`; do
    if [[ -e $server_config/appengine-web.xml ]]; then
      id=`basename $server_config` # take last part of the path
      # create dabatase for an organization if it doesn't exist
      if ! sudo -u postgres psql -lqt | cut -d \| -f 1 | grep -w $id; then
        echo "DB for organization '$id' doesn't exist, thus creating and initializing..."
        sudo -u postgres psql<< EOF
            CREATE DATABASE "$id"
            WITH OWNER "postgres"
            TEMPLATE = template0
            ENCODING 'UTF8'
            LC_COLLATE 'C'
            LC_CTYPE 'C';
            GRANT ALL ON DATABASE "$id" TO unilog;
EOF
        # initialize database for a given organization
        sudo -u postgres psql $id<<EOF 
            CREATE TABLE IF NOT EXISTS event_log (
              id BIGSERIAL PRIMARY KEY,
              payload JSONB
            );

            CREATE OR REPLACE FUNCTION notify_on_event_log_insert()
              RETURNS trigger AS \$\$
              DECLARE BEGIN
                PERFORM pg_notify(TG_TABLE_NAME, NEW.id::text);
                RETURN new;
              END;
              \$\$ LANGUAGE plpgsql;
            
            DROP TRIGGER IF EXISTS event_log_insert_trigger ON event_log;
            
            CREATE TRIGGER event_log_insert_trigger
              AFTER INSERT ON event_log
              FOR EACH ROW EXECUTE PROCEDURE notify_on_event_log_insert();
EOF
      fi
    fi
done