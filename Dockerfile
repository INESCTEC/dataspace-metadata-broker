FROM eclipse-temurin:25-jdk-noble
# Hotspot based on ubuntu 24.04 LTS (noble)

VOLUME /tmp
COPY ./target/broker-*.jar app.jar

CMD ["java", "-jar", "/app.jar"]

