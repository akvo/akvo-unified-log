#!/bin/bash

echo "This script will edit /etc/hosts and therefore requires your administrator password."
sudo bash -c 'echo "192.168.50.101 unilog.localdev.akvo.org   # localdev_unilog" >> /etc/hosts'
