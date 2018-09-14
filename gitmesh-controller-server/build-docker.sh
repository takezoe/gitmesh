#!/bin/sh
cd `dirname $0`
sbt clean package
export VERSION=`cat build.sbt | grep version | sed -e 's/[^"]*"\([^"]*\)".*/\1/'`
docker build --build-arg VERSION=$VERSION -t gitmesh-controller-server:$VERSION .
