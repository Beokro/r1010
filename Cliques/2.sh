#./1.sh
rm -rf cliques-3
rm -rf out*/
mvn package
../../hadoop-2.8.0/bin/yarn jar ./target/Cliques-1.0-SNAPSHOT.jar it.QkCount.QkCountDriver countCliques -in=haha.txt -out=cliques -logCpuTime=False -cliqueSize=3 -workingDir=$(pwd)
cat cliques-3/*
