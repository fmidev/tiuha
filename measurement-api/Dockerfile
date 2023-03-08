FROM maven:eclipse-temurin

COPY src pom.xml /app/

RUN mvn package -DskipTests -f /app/pom.xml

CMD ["java", "-XX:MaxRAMPercentage=90", "-jar", "/app/target/measurement-api-1.0-SNAPSHOT-shaded.jar"]
