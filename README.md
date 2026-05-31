# syringe-testbed

Testbed for Syringe – a CDI4.1 compatible framework

This package contains all tests of the CDI 4.1 TCK ported to standalone versions, plus classes needed to run the
official TCK tests under Wildfly. You will need to have Wildfly 31.0.1.Final installed and configured.

export JBOSS_HOME=jakartacdi/wildfly-31.0.1.Final
$JBOSS_HOME/bin/standalone.sh -c standalone.xml

cd cdi-tck-4.1.0/weld/jboss-tck-runner
mvn -Dincontainer=syringe clean test
