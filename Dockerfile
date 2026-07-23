FROM eclipse-temurin:21-jdk-jammy
# Hotspot based on ubuntu 22.04 LTS (jammy)

VOLUME /tmp
COPY ./target/broker-*.jar app.jar

CMD ["java", "-jar", "/app.jar"]

