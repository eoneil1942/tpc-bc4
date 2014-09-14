# Usage: runservers2k1.sh tpcb jan16P catalog-cv for example, to use 
#    run_tpcb2.sh with ../jan16P voltdb and use catalog-cv
# or runservers2 tpcc jan16P catalog-cv to run tpcc on 2 hosts
echo about to do, on cv3: bash run_${1}2.sh $2 $3 
DIR=`pwd`
LOGDIR=/mnt/ramdisk
ssh cv3.local << EOF 
# remove any old timing files
rm /mnt/ramdisk/*.txt
cd $DIR
bash run_${1}2k1.sh $2 $3 > $LOGDIR/catalog.log
bash run_${1}2k1.sh $2 server$4 > $LOGDIR/server.log&
EOF
echo cv3 started
rm /mnt/ramdisk/*.txt
bash run_${1}2k1.sh $2 $3 > $LOGDIR/catalog.log
echo starting server locally...this will hang, control-C to end run
bash run_${1}2k1.sh $2 server$4 > $LOGDIR/server.log
