#!/bin/bash
 
JMX_PORT=9011
 
RMI_PORT=9010
 
RMI_HOST="euca-128-111-84-201.eucalyptus.cloud.eci.ucsb.edu"
 
JAVA_OPTS="-Dcom.sun.management.jmxremote "
JAVA_OPTS="${JAVA_OPTS} -Dcom.sun.management.jmxremote.local.only=false "
JAVA_OPTS="${JAVA_OPTS} -Dcom.sun.management.jmxremote.port=${JMX_PORT} "
JAVA_OPTS="${JAVA_OPTS} -Dcom.sun.management.jmxremote.authenticate=false"
JAVA_OPTS="${JAVA_OPTS} -Dcom.sun.management.jmxremote.ssl=false"
JAVA_OPTS="${JAVA_OPTS} -Djava.rmi.server.hostname=${RMI_HOST}"
JAVA_OPTS="${JAVA_OPTS} -Djava.net.preferIPv4Stack=true "
JAVA_OPTS="${JAVA_OPTS} -Dcom.sun.management.jmxremote.rmi.port=${RMI_PORT}"
java $JAVA_OPTS -cp target/RoyCrab-2.0-jar-with-dependencies.jar search10.RemoteBloomFilterImpl
