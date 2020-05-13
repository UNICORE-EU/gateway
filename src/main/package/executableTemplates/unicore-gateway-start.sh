#!/bin/bash

#
# Startup script for UNICORE gateway
#
@cdInstall@
#
# Read basic settings 
#
. @etc@/startup.properties

#
# check whether the server might be already running
#
if [ -e $PID ] 
 then 
  if [ -d /proc/$(cat $PID) ]
   then
     echo "A Gateway instance may be already running with process id "$(cat $PID)
     echo "If this is not the case, delete the file $INST/$PID and re-run this script"
     exit 1
   fi
fi

#
# put all jars in lib/ on the classpath
#
CP=.$(@cdRoot@find "$LIB" -name "*.jar" -exec printf ":{}" \;)


#
# go
#
CLASSPATH=$CP; export CLASSPATH

nohup $JAVA ${MEM} ${OPTS} ${DEFS} eu.unicore.gateway.Gateway ${PARAM} > $STARTLOG 2>&1  &
echo $! > $PID
