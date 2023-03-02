FROM openjdk:21

COPY target/measurement-api-1.0-SNAPSHOT-shaded.jar /app/server.jar

CMD ["java", "-XX:MaxRAMPercentage=90", "-jar", "/app/server.jar"]
