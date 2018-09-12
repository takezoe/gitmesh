#!/bin/sh
cd `dirname $0`
cd ../
sbt clean package
export VERSION=`cat build.sbt | grep version | sed -e 's/[^"]*"\([^"]*\)".*/\1/'`
cd -
cp ../target/scala-2.12/gitmesh-controller-server_2.12-$VERSION.war ./ROOT.war
docker build -t gitmesh-controller-server:$VERSION .
rm ./ROOT.war