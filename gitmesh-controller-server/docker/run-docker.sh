#!/bin/sh
export VERSION=`cat ../build.sbt | grep version | sed -e 's/[^"]*"\([^"]*\)".*/\1/'`
docker run -it -p 8081:8080 --rm gitmesh-controller-server:$VERSION
