#!/bin/bash

NAME=self-training
VERSION=0.0.1

cp -f ../../../../build/libs/${NAME}-${VERSION}.jar .

docker build --build-arg BASE_CRM_TOKEN=${BASE_CRM_TOKEN} \
        --build-arg DEVICE_UUID=${DEVICE_UUID} \
        --build-arg workflow_sales_representatives_emails=${workflow_sales_representatives_emails} \
        --build-arg workflow.account_managers_email=${workflow_account_managers_emails} \
        --build-arg workflow_account_manager_on_duty_email=${workflow_account_manager_on_duty_email} \
        -f Dockerfile \
        -t ${NAME}:${VERSION} .
