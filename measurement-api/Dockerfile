FROM maven:eclipse-temurin

WORKDIR /app

COPY . .

RUN mvn package -DskipTests 

CMD ["java", "-XX:MaxRAMPercentage=90", "-jar", "/app/target/measurement-api-1.0-SNAPSHOT-shaded.jar"]
