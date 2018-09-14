#!/bin/sh
cd `dirname $0`
export VERSION=`cat ../gitmesh-controller-server/build.sbt | grep version | sed -e 's/[^"]*"\([^"]*\)".*/\1/'`
docker build -t gitmesh-console:$VERSION .
