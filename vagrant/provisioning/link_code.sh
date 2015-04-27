#!/bin/bash
set -e

# linking leiningen app into `/var/akvo/unilog/code`
rm -rf /var/akvo/unilog/code
sudo -H -u unilog ln -s /vagrant/unilog/checkout /var/akvo/unilog/code

# linking akvo-flow-server-config into `/var/akvo/unilog/akvo-flow-server-config`
rm -rf /var/akvo/unilog/akvo-flow-server-config
sudo -H -u unilog ln -s /vagrant/flow-server-config/checkout /var/akvo/unilog/akvo-flow-server-config

# cloning akvo-core-services to have the flow data schema
if [ ! -d /var/akvo/unilog/akvo-core-services ]; then
  git clone -b unified-log https://github.com/akvo/akvo-core-services.git /var/akvo/unilog/akvo-core-services
fi
