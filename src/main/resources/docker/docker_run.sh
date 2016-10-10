#!/bin/bash -x

NAME=self-training
VERSION=0.0.2-SNAPSHOT

# Passing variables here due to https://github.com/Transmode/gradle-docker/issues/91
docker run --name self-training \
                -p 8080:8080 -d -e BASE_CRM_TOKEN=${BASE_CRM_TOKEN} \
                -e DEVICE_UUID=${DEVICE_UUID} \
                -e workflow_sales_representatives_emails=${workflow_sales_representatives_emails} \
                -e workflow_account_managers_emails=${workflow_sales_representatives_emails} \
                -e workflow_account_manager_on_duty_email=${workflow_account_manager_on_duty_email} \
                self-training:0.0.2-SNAPSHOT
