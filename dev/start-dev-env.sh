#!/usr/bin/env bash

set -e

if [ ! -d "/tmp/here/akvo-flow-server-config" ]; then
    echo "Creating fake git repo with Flow config"
    cp -r dev/flow-server-config /akvo-flow-server-config
    pushd /akvo-flow-server-config > /dev/null
    git init
    git config --global user.email "you@example.com"
    git config --global user.name "Your Name"
    git add -A
    git commit -m "Initial commit"
    git clone /akvo-flow-server-config /tmp/here/akvo-flow-server-config
    popd > /dev/null
fi

if [ ! -d "/tmp/repos-dir" ]; then
    mkdir "/tmp/repos-dir"
fi

lein run dev/dev-config.edn