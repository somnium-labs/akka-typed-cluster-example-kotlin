FROM openjdk:11-slim
COPY build/libs/akka-typed-cluster-example-kotlin-all.jar /etc/akka/app.jar
