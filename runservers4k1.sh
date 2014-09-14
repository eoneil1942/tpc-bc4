# Usage: runservers4 tpcb jan16P catalog-ea for example, 
#  to use run_tpcb4.sh with ../jan16P voltdb and use catalog-ea
# or runservers4 tpcc jan16P catalog-cv -ea   to run tpcc w server-ea
DIR=`pwd`
LOGDIR=/mnt/ramdisk
echo about to run, on cv1: bash run_${1}4.sh $2 $3
ssh cv1.local << EOF 
rm /mnt/ramdisk/*.txt
cd $DIR
bash run_${1}4k1.sh $2 $3 > $LOGDIR/catalog.log
bash run_${1}4k1.sh $2 server$4 > $LOGDIR/server.log&
EOF
echo cv1 started
ssh cv3.local << EOF 
rm /mnt/ramdisk/*.txt
cd $DIR
bash run_${1}4k1.sh $2 $3 > $LOGDIR/catalog.log
bash run_${1}4k1.sh $2 server$4 > $LOGDIR/server.log&
EOF
echo cv3 started
ssh cv4.local << EOF 
rm /mnt/ramdisk/*.txt
cd $DIR
bash run_${1}4k1.sh $2 $3 > $LOGDIR/catalog.log
bash run_${1}4k1.sh $2 server$4 > $LOGDIR/server.log&
EOF
echo cv4 started
rm /mnt/ramdisk/*.txt
bash run_${1}4k1.sh $2 $3 > $LOGDIR/catalog.log
echo starting server locally...this will hang, control-C to end run
bash run_${1}4k1.sh $2 server$4 > $LOGDIR/server.log
