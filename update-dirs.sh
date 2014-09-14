# copy this dir on cv2 to 4 places 
DIR=`pwd`
scp -r * eoneil@cv1.local:$DIR
scp -r * eoneil@cv3.local:$DIR
scp -r * eoneil@cv4.local:$DIR
scp -r * eoneil@cv0.local:$DIR
