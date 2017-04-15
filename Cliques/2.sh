./1.sh
rm -rf cliques-3
../../hadoop-2.8.0/bin/yarn jar ./target/Cliques-1.0-SNAPSHOT.jar it.QkCount.QkCountDriver countCliques -in=degrees -out=cliques -cliqueSize=3 -workingDir=$(pwd)
