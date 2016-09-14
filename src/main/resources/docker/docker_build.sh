#!/bin/bash

NAME=self-training
VERSION=0.0.1

cp -f ../../../../build/libs/${NAME}-${VERSION}.jar .

docker build --build-arg BASE_CRM_TOKEN=${BASE_CRM_TOKEN} \
        --build-arg DEVICE_UUID=${DEVICE_UUID} \
        -f Dockerfile \
        -t ${NAME}:${VERSION} .
