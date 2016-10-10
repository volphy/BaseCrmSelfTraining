#!/bin/bash

NAME=self-training
VERSION=0.0.2-SNAPSHOT

cp -f ../../../../build/libs/${NAME}-${VERSION}.jar .

docker build -f Dockerfile -t ${NAME}:${VERSION} .
