# Usage: runservers3 tpcb jan16P catalog-cv for example, 
#  to use run_tpcb3.sh with ../jan16P voltdb and use catalog-cv command
# First cd to tpcb-run or whatever run directory you're using on all hosts
DIR=`pwd`
LOGDIR=/mnt/ramdisk

ssh cv3.local << EOF 
rm /mnt/ramdisk/*.txt
cd $DIR
bash run_${1}3.sh $2 $3 > $LOGDIR/catalog.log
bash run_${1}3.sh $2 server$4 > $LOGDIR/server.log&
EOF
echo cv1 started
ssh cv4.local << EOF 
rm /mnt/ramdisk/*.txt
cd $DIR
bash run_${1}3.sh $2 $3 > $LOGDIR/catalog.log
bash run_${1}3.sh $2 server$4 > $LOGDIR/server.log&
EOF
echo cv4 started
rm /mnt/ramdisk/*.txt
bash run_${1}3.sh $2 $3 > $LOGDIR/catalog.log
echo starting server locally...this will hang, control-C to end run
bash run_${1}3.sh $2 server$4 > $LOGDIR/server.log
