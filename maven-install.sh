
#
# Install autumo libs in local maven repository
#

# Vars
COMMONS_VERSION=1.0.0
IFACEX_VERSION=1.3.0
BEETROOT_VERSION=2.2.0

# Install
mvn install:install-file -Dfile=lib/autumo-commons-${COMMONS_VERSION}.jar -DgroupId=ch.autumo.commons -DartifactId=autumo-commons -Dversion=${COMMONS_VERSION} -Dpackaging=jar
mvn install:install-file -Dfile=lib/autumo-ifacex-${IFACEX_VERSION}.jar -DgroupId=ch.autumo.ifacex -DartifactId=autumo-ifacex -Dversion=${IFACEX_VERSION} -Dpackaging=jar
mvn install:install-file -Dfile=lib/autumo-beetroot-${BEETROOT_VERSION}.jar -DgroupId=ch.autumo.beetroot -DartifactId=autumo-beetroot -Dversion=${BEETROOT_VERSION} -Dpackaging=jar

