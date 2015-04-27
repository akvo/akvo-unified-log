#!/bin/bash

PWD=$(pwd)

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd $DIR/../../vagrant

CONF=`mktemp vagrant-ssh-conf.XXXXXX`
vagrant ssh-config > $CONF
ssh -t -F $CONF default "sudo -H -u unilog psql"
rm $CONF

cd $PWD
