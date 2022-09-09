# UNICORE Gateway

This repository contains the source code for the UNICORE Gateway.

The UNICORE Gateway is an (optional) server component that
provides a reverse https proxy, allowing you to run several backend
servers (UNICORE/X, Registery, ...) behind a single address.
This helps with firewall configuration, requiring only a single open ports.

(a similar effect can be achieved using other http servers that can
act as a reverse proxy, such as Apache httpd or nginx)


## Download


The Gateway is distributed as part of the "Core Server" bundle,
which can be downloaded from SourceForge:
https://sourceforge.net/projects/unicore/files/Servers/Core/

## Documentation

See the [Gateway manual] 
(https://unicore-docs.readthedocs.io/en/latest/admin-docs/gateway/index.html)

## Building from source

You need Java and Apache Maven.

The Java code is built and unit tested using

    mvn install

To skip unit testing

    mvn install -DskipTests

The following commands create distribution packages
in tgz, deb and rpm formats


 * tgz

    mvn package -DskipTests -Ppackman -Dpackage.type=bin.tar.gz

 * deb

    mvn package -DskipTests -Ppackman -Dpackage.type=deb -Ddistribution=Debian

 * rpm

    mvn package -DskipTests -Ppackman -Dpackage.type=rpm -Ddistribution=RedHat

