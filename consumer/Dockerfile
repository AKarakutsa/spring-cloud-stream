#FROM maven:3.5-jdk-8
FROM openjdk:8
ADD target/consumer.jar consumer
EXPOSE 8100
ENTRYPOINT ["java", "-jar", "consumer"]