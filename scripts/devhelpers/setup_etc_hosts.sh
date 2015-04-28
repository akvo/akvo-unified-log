#!/bin/bash

echo "This script will edit /etc/hosts and therefore requires your administrator password."

# check if /etc/hosts last character is a newline or not
lastline=$(tail -c 1 /etc/hosts)
if [ "$lastline" != "" ]; then
    sudo bash -c 'echo   \ \ >> /etc/hosts'
fi
sudo bash -c 'echo "192.168.50.101 unilog.localdev.akvo.org   # localdev_unilog" >> /etc/hosts'
