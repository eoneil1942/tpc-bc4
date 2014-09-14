#!/usr/bin/env bash
# eoneil: TPCC script, derived simply from older TPCC version
# and here VOLTROOT is a parameter
# Added targets for running with asserts enabled: server-ea, client-ea

APPNAME="tpcc"
VOLTROOT=../$1
CLASSPATH=$({ \
    \ls -1 $VOLTROOT/voltdb/voltdb*-[234].*.jar; \
    \ls -1 "$VOLTROOT"/lib/*.jar; \
    \ls -1 "$VOLTROOT"/lib/extension/*.jar; \
} 2> /dev/null | paste -sd ':' - )
echo CLASSPATH: $CLASSPATH
VOLTDB="$VOLTROOT/bin/voltdb"
CSVLOADER="$VOLTROOT/bin/csvloader"
LICENSE="$VOLTROOT/voltdb/license.xml"
LEADER="localhost"
SERVERS="localhost"
WAREHOUSES=6
# remove build artifacts
function clean() {
    rm -rf obj debugoutput $APPNAME.jar  plannerlog.txt
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

# build an application catalog
function catalog-cv() {
    srccompile
    $VOLTDB compile --classpath obj -o $APPNAME.jar -p tpcc-cv-project.xml
    # stop if compilation fails
    if [ $? != 0 ]; then exit; fi
}
# no escrow-named columns
function catalog-v() {
    srccompile
    $VOLTDB compile --classpath obj -o $APPNAME.jar -p tpcc-project.xml
    # stop if compilation fails
    if [ $? != 0 ]; then exit; fi
}

# run the voltdb server locally
function server() {
	echo running server with tpcc app jar
    # if a catalog doesn't exist, build one
    if [ ! -f $APPNAME.jar ]; then catalog; fi
    # run the server
    $VOLTDB create -d deployment.xml -l $LICENSE -H $LEADER $APPNAME.jar 
}

# run the voltdb server locally, with asserts enabled
function server-ea() {
	echo running server with tpcc app jar
    # if a catalog doesn't exist, build one
    if [ ! -f $APPNAME.jar ]; then catalog; fi
    # add -ea to flags to enable asserts--
    VOLTDB_OPTS=-ea; export VOLTDB_OPTS
    # run the server
    $VOLTDB create -d deployment.xml -l $LICENSE -H $LEADER $APPNAME.jar 
}
# run the client help
function client-help() {
    srccompile
    java -classpath obj:$CLASSPATH:obj com.MyTPCC \
        --servers=$SERVERS \
        --duration=60 \
        --warehouses=$WAREHOUSES \
        --help
}

# load the replicated table using cvsloader
# do this from one client, before running "client"
function load() {
	echo "running " + $CSVLOADER
	$CSVLOADER item -f items.csv -r logs
}


# run the client that drives the tpcc example
function client() {
    srccompile
    java -classpath obj:$CLASSPATH:obj com.MyTPCC \
        --statsfile=stats.txt \
        --servers=$SERVERS \
        --duration=60 \
        --warehouses=$WAREHOUSES 

}

# run the client that drives the tpcc example, with asserts on
function client-ea() {
	$CVSLOADER -f items.csv -r logs
    srccompile
    java -classpath obj:$CLASSPATH:obj -ea com.MyTPCC \
        --servers=localhost \
        --duration=60 \
        --warehouses=$WAREHOUSES
}

function help() {
    echo "Usage: ./run_tpcc.sh voltrootdir {clean|catalog-v|catalog-cv|server|server-ea|client|client-ea}"
}

# Run the target passed as the last arg on the command line
# for targets needing voltroot spec, use that before the command
if [ $# -gt 2 ]; then help; exit; fi
if [ $# = 2 ]; then $2; fi
if [ $# = 1 ]; then $1; fi
if [ $# = 0 ]; then help; fi
