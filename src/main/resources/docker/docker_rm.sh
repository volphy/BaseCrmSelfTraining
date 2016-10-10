#!/bin/bash

NAME=self-training
VERSION=0.0.2-SNAPSHOT

docker stop ${NAME}
docker rm -f -v ${NAME} && docker rmi -f ${NAME}:${VERSION}

