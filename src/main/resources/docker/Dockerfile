FROM openjdk:8-jre
MAINTAINER Krzysztof Wilk

ENV PATH $PATH:$JAVA_HOME/bin
ENV APPLICATION_FILE self-training-0.0.2-SNAPSHOT.jar
ENV TIMEZONE Europe/Warsaw

COPY $APPLICATION_FILE /app/

WORKDIR /app

EXPOSE 8080

CMD ["/bin/sh", "-c", "java -jar /app/self-training-0.0.2-SNAPSHOT.jar"]