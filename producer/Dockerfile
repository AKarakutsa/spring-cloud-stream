#FROM maven:3.5-jdk-8
FROM openjdk:8
ADD target/producer.jar producer
EXPOSE 8100
ENTRYPOINT ["java", "-jar", "producer"]