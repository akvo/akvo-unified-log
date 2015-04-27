# Unified Log Vagrant Development Environment 

## System setup

If you have never used Vagrant or the unified log (hereinafter Unilog) Vagrant development environment, follow these steps first.

1. Ensure you have at least Vagrant version 1.2 installed:
    
       ~$ vagrant --version
	   vagrant version 1.2.2

   If you don't have Vagrant installed or if you have an old version installed, head over to [http://vagrantup.com](http://vagrantup.com) to get it.
   
2. Ensure you have Oracle VirtualBox installed. If not, you can get it here: [https://www.virtualbox.org/wiki/Downloads](https://www.virtualbox.org/wiki/Downloads)

## First Start Up

The first time you use the Unilog environment:

1. Run `scripts/devhelpers/setup_etc_hosts.sh` - this will add the necessary entries to your `/etc/hosts` file to be able to access the Unilog server

2. Go into the vagrant directory and start the Vagrant box: `cd vagrant` then `vagrant up`. The first time will take a long time, as it will first download the base Unilog virtual machine. Once this is done, it will update required packages if any have changed, as well as install dependencies of the unified log application (this operation may take some minutes).

3. Try out [http://unilog.localdev.akvo.org/](http://unilog.localdev.akvo.org/)

## Upgrading

Periodically there will be a new base Unilog machine available, which will have updated infrastructure and supporting services. When this is the case, simply `vagrant destroy` to delete your old VM, then `vagrant up` as before; a completely new machine will be created.

You may also want to clean up the old base boxes. They can be found in `$HOME/.vagrant.d/boxes` on UNIXy OSs, and you can simply old ones that you do not use. 


## About the VM

The virtual machine is provisioned with the same [Puppet](http://puppetlabs.com/puppet/what-is-puppet) configuration as the produciton machines. This means that developing locally should take place using exactly the same setup as production.


## FAQ

##### Q: How do I connect to postgreSQL server?
**A**: From your main machine, you can use the script in `scripts/devhelpers/psql.sh` which will connect you as the application user (unilog). You can also `ssh` into the machine (using `vagrant ssh`) and connect by using `sudo -H -u unilog psql`

##### Q: How can I restart the unified log clojure application?
**A**: By default, the unified log application is run by [leiningen](http://leiningen.org/) and controlled by [supervisor](http://supervisord.org/) to match production servers. You can use the script `scripts/devhelpers/supervisorctl.sh` to control the application:

        `scripts/devhelpers/supervisorctl.sh <start|stop|restart> unilog`

##### Q: I get a 502! Why?
**A**: Ensure that unified log application is running. To see if it is running, run `scripts/devhelpers/supervisorctl.sh`. You will see `unilog      RUNNING` or similar if everythig is fine. Chances are, a 502 means that no unified log app is running. In any case, you can use the devhelper script `tail_logs.sh` to check application startup log. 



## Helpful notes

* When you are done developing, run `vagrant halt` to shut down the virtual machine, or it'll just sit there consuming system resources.
 
* Run `vagrant ssh` to ssh into the virtual machine. You will then be logged in as the `vagrant` user, who can use sudo without a password.

* The unified log application root directory is in `/var/akvo/unilog/`.

* The `akvo-unified-log` and `akvo-flow-server-config` repositories from your local machine are synced to the virtual machine at `/var/akvo/unilog/code` and `/var/akvo/unilog/akvo-flow-server-config` respectively. Therefore, you just need to restart the application (by means of `scripts/devhelpers/supervisorctl.sh`) to test local code changes on both of them.

* The `akvo-core-services` repository is cloned at `/var/akvo/unilog/akvo-core-services` in order to have the flow data schema.


## Useful scripts

There are a variety of useful scripts in `scripts/devhelpers`:

* `setup_etc_hosts.sh` will add the necessary IP/address combinations to your `/etc/hosts` file

* `cleanup_etc_hosts.sh` will remove those entries at `/etc/hosts`

* `psql.sh` will connect to the postgreSQL server on the virtual machine as `unilog` user

* `supervisorctl.sh` will connect you to the supervisor service, allowing you to manually start and stop unified log if it is running as a service.

* `tail_logs.sh` will connect you to the supervisor service and tail application startup log file
