mvn package
rm -rf degrees
rm -rf out*
../../hadoop-2.8.0/bin/yarn jar ./target/Cliques-1.0-SNAPSHOT.jar it.QkCount.QkCountDriver computeDegrees -in=graph.txt -out=degrees -workingDir=$(pwd)
