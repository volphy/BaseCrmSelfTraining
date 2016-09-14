#!/bin/bash -x

NAME=self-training
VERSION=0.0.1

docker run --name ${NAME} -p 8080:8080 -d ${NAME}:${VERSION}
