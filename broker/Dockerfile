#FROM maven:3.5-jdk-8
FROM openjdk:8
ADD target/broker.jar broker
EXPOSE 8100
ENTRYPOINT ["java", "-jar", "broker"]