#
# Building the UNICORE Gateway
#

You need Java 7 and Apache Maven.

** NOTE: 
for packaging, you'll need maven2, while for other 
tasks both 2 and 3 should work.


The Java code is built and unit tested using

  mvn install

To skip unit testing

  mvn install -DskipTests

#
# Creating documentation
#

To build the docs:

  mvn site

You can check them by pointing a web browser at 
"target/site/index.html"

To upload the docs to the unicore-dev documentation server:

  mvn site:deploy


#
# Creating distribution packages
#

The following commands create the distribution packages
in tgz, deb and rpm formats

The package versions are defined in the pom.xml file!

#tgz
  mvn package -DskipTests -Ppackman -Dpackage.type=bin.tar.gz

#deb
  mvn package -DskipTests -Ppackman -Dpackage.type=deb -Ddistribution=Debian

#rpm
  mvn package -DskipTests -Ppackman -Dpackage.type=rpm -Ddistribution=RedHat



