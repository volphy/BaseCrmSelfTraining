#!/bin/bash

NAME=self-training
VERSION=0.0.1

docker stop ${NAME}
docker rm -f -v ${NAME} && docker rmi -f ${NAME}:${VERSION}

