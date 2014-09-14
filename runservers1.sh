# Usage: runservers1 tpcb jan16P catalog-cv for example, to use 
#    run_tpcb2.sh with ../jan16P voltdb and use catalog-cv
# or runservers1 tpcc jan16P catalog-cv to run tpcc on 2 hosts
# or runservers1 tpcc jan16P catalog-cv -ea  to run server-ea
DIR=`pwd`
LOGDIR=/mnt/ramdisk
rm -f /mnt/ramdisk/*.txt
bash run_${1}1.sh $2 $3 > $LOGDIR/catalog.log
echo starting server locally...this will hang, control-C to end run
bash run_${1}1.sh $2 server$4 > $LOGDIR/server.log
