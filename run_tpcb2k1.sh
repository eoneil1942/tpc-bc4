#!/usr/bin/env bash
# 
# eoneil: TPCB script, 2-host case, Voltdb or CVoltdb
# and here VOLTROOT is a parameter
# Added targets for running with asserts enabled: server-ea, client-ea

APPNAME="tpcb"
VOLTROOT=../$1
MP_PERCENT=$3
VOLTJAR=`ls $VOLTROOT/voltdb/voltdb-[234].*.jar`
CLASSPATH=$({ \
    \ls -1 $VOLTROOT/voltdb/voltdb*-[234].*.jar; \
    \ls -1 "$VOLTROOT"/lib/*.jar; \
    \ls -1 "$VOLTROOT"/lib/extension/*.jar; \
} 2> /dev/null | paste -sd ':' - )
echo CLASSPATH: $CLASSPATH
#CLASSPATH="$VOLTJAR:../../../lib" #:./obj/com:./obj/com/procedures"
VOLTDB="$VOLTROOT/bin/voltdb"
#VOLTCOMPILER="$VOLTROOT/bin/voltcompiler"
LICENSE="$VOLTROOT/voltdb/license.xml"
LEADER="192.168.2.222"
SERVERS="192.168.2.222,192.168.2.223"
BRANCHES=6

# remove build artifacts
function clean() {
    rm -rf obj debugoutput $APPNAME.jar voltdbroot plannerlog.txt
}

# compile the source code for procedures and the client
function srccompile() {
    mkdir -p obj
    javac -classpath $CLASSPATH -d obj \
        src/com/*.java \
        src/com/procedures/*.java \
        src/com/tpcbprocedures/*.java
    # stop if compilation fails
    if [ $? != 0 ]; then exit; fi
}

# build an application catalog, Voltdb case
function catalog-v() {
    srccompile
    $VOLTDB compile --classpath obj -o $APPNAME.jar -p tpcb-project.xml
    # stop if compilation fails
    if [ $? != 0 ]; then exit; fi
}

# CVoltdb build-catalog: use cv version of project.xml
function catalog-cv() {
    srccompile
    $VOLTDB compile --classpath obj -o $APPNAME.jar -p tpcb-cv-project.xml
    # stop if compilation fails
    if [ $? != 0 ]; then exit; fi
}

# run the voltdb server locally
function server() {
    # if a catalog doesn't exist, build one
    if [ ! -f $APPNAME.jar ]; then catalog; fi
    # run the server
    echo running server with $APPNAME.jar
    $VOLTDB create -d mh2_deploymentk1.xml -l $LICENSE -H $LEADER $APPNAME.jar 
}

# added by eoneil--run the voltdb server locally with assertions on
function server-ea() {
    # if a catalog doesn't exist, build one
    if [ ! -f $APPNAME.jar ]; then catalog; fi
    # run the server
    echo running server with $APPNAME.jar
    # add -ea to flags to enable asserts--
    VOLTDB_OPTS=-ea; export VOLTDB_OPTS
    $VOLTDB create -d mh2_deploymentk1.xml -l $LICENSE -H $LEADER $APPNAME.jar 
}

# run the client that drives the tpcb example
# MP-percent=15 is the TPCB standard, provided as 3rd arg
function client() {
    echo MP percent = $MP_PERCENT or $3
    srccompile
    java -classpath obj:$CLASSPATH:obj com.MyTPCB \
        --servers=$SERVERS \
        --duration=60 \
        --branches=$BRANCHES \
        --MP-percent=$MP_PERCENT
}

# eoneil: with assertions on
function client-ea() {
    srccompile
    java -classpath obj:$CLASSPATH:obj -ea com.MyTPCB \
        --servers=$SERVERS \
        --duration=60 \
        --branches=$BRANCHES \
        --MP-percent=$MP_PERCENT
}
function help() {
    echo "Usage: ./run_tpcbx.sh voltrootdir {clean|catalog-v|catalog-cv|server|server-ea|client|client-ea}"
}

# Run the various commands 
# first arg: server to use
# second arg: command to use: clean|catalog|...
# third arg: MP percent to use (client and client-ea only) 
if [ $# -gt 3 ]; then help; exit; fi
if [ $# = 3 ]; then $2; fi
if [ $# = 2 ]; then $2; fi
if [ $# -le 1 ]; then help; fi
