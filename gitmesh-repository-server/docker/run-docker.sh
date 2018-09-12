#!/bin/sh
export VERSION=`cat ../build.sbt | grep version | sed -e 's/[^"]*"\([^"]*\)".*/\1/'`
docker run -it -p 8082:8080 --rm gitmesh-repository-server:$VERSION
