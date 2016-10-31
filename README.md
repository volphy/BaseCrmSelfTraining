#How to run it

##Plain Spring Boot approach:

1. clone repository:

    `git clone https://github.com/volphy/BaseCrmSelfTraining.git`

2. configure sensitive input parameters by setting the following
environment variables in your local environment:

    - `BASE_CRM`

    - `DEVICE_UUID`

    - `workflow_sales_representatives_emails` (specific emails separated by comma ",")

    - `workflow_account_managers_emails` (specific emails separated by comma ",")

    - `workflow_account_manager_on_duty_email`

3. open first terminal and start the application:

    `./gradlew bootRun`

4. open second terminal and run integration tests:

    `./gradlew test`

5. upon successful pass of the tests switch back to the first terminal and stop the application (^C)


##Dockerized approach (with Docker Gradle plugin):

1. clone repository:

    `git clone https://github.com/volphy/BaseCrmSelfTraining.git`

2. configure sensitive input parameters by setting the following
environment variables in your local environment:

    - `BASE_CRM`

    - `DEVICE_UUID`

    - `workflow_sales_representatives_emails` (specific emails separated by comma ",")

    - `workflow_account_managers_emails`  (specific emails separated by comma ",")

    - `workflow_account_manager_on_duty_email`

3. open first terminal and build Docker image:

  `./gradlew buildDocker`

4. build Docker container (and start for the first time) from newly built Docker image:

  `cd src/main/resources/docker`

  `./docker_run.sh`

5. watch logs of newly started Docker container:

  `./docker_logs.sh`

6. open second terminal and start integration tests:

  `./gradlew test`

7. upon successful pass of the tests stop the application:

  `docker stop self-training`


##Dockerized approach (without Docker Gradle plugin):

1. clone repository:

    `git clone https://github.com/volphy/BaseCrmSelfTraining.git`

2. configure sensitive input parameters by setting the following
environment variables in your local environment:

    - `BASE_CRM`

    - `DEVICE_UUID`

    - `workflow_sales_representatives_emails`  (specific emails separated by comma ",")

    - `workflow_account_managers_emails`  (specific emails separated by comma ",")

    - `workflow_account_manager_on_duty_email`

3. open first terminal and build application:

  `./gradlew build`

4. build Docker image:

  `cd src/main/resources/docker`
  
  `./docker_build.sh`

5. build Docker container (and start for the first time) from newly built Docker image:

  `./docker_run.sh`

6. watch logs of newly started Docker container:

  `./docker_logs.sh`

7. open second terminal and start integration tests:

  `./gradlew test`

8. upon successful pass of the tests switch back to the first terminal and stop the application:

  `docker stop self-training`
